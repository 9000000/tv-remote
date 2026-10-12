package dev.tvremote.phone;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;

/**
 * A small ADB client over TCP: authenticates with this app's own RSA key and multiplexes several streams (shell
 * commands, sockets on the TV) over the one connection. The first connection makes the TV ask "Allow USB debugging?".
 */
final class Adb implements Closeable {
    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f, CLSE = 0x45534c43, WRTE = 0x45545257;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    private static final byte[] EMPTY = new byte[0], EOF = new byte[0];
    /** ASN.1 DigestInfo header for SHA-1: adbd treats the 20-byte token as a SHA-1 digest to be PKCS#1 v1.5 signed. */
    private static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    interface Prompt { void waitingForUser(); }

    private static final class Msg { int cmd, arg0, arg1; byte[] data; }

    /** One ADB stream. Reads block until data, end of stream or the timeout; writes block until the TV acknowledges. */
    final class Stream implements Closeable {
        private final int local;
        private volatile int remote;
        private volatile boolean closed;
        private final LinkedBlockingQueue<byte[]> in = new LinkedBlockingQueue<>();
        private final Semaphore opened = new Semaphore(0), acked = new Semaphore(0);
        private byte[] cur = EMPTY;
        private int pos;

        Stream(int local) { this.local = local; }

        /** Reads up to len bytes; -1 at end of stream; 0 if nothing arrived within timeoutMs (0 = wait for ever). */
        int read(byte[] buf, int off, int len, long timeoutMs) throws IOException {
            if (pos == cur.length) {
                if (cur == EOF) return -1;
                try {
                    byte[] next = timeoutMs > 0 ? in.poll(timeoutMs, TimeUnit.MILLISECONDS) : in.take();
                    if (next == null) return 0;
                    cur = next; pos = 0;
                    if (cur == EOF) return -1;
                } catch (InterruptedException e) {
                    throw new IOException("interrupted");
                }
            }
            int n = Math.min(len, cur.length - pos);
            System.arraycopy(cur, pos, buf, off, n);
            pos += n;
            return n;
        }

        void readFully(byte[] buf, int off, int len) throws IOException {
            while (len > 0) {
                int n = read(buf, off, len, 0);
                if (n < 0) throw new EOFException("stream closed");
                off += n; len -= n;
            }
        }

        String readAll() throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = read(buf, 0, buf.length, 0)) >= 0; ) out.write(buf, 0, n);
            return out.toString("UTF-8").replace("\r\n", "\n").trim();
        }

        synchronized void write(byte[] data, int off, int len) throws IOException {
            while (len > 0) {
                if (closed) throw new IOException("stream closed");
                int n = Math.min(len, maxData);
                byte[] part = new byte[n];
                System.arraycopy(data, off, part, 0, n);
                send(WRTE, local, remote, part);
                try {
                    while (!acked.tryAcquire(1, TimeUnit.SECONDS)) if (closed) throw new IOException("stream closed");
                } catch (InterruptedException e) {
                    throw new IOException("interrupted");
                }
                off += n; len -= n;
            }
        }

        void write(byte[] data) throws IOException { write(data, 0, data.length); }

        @Override public void close() {
            if (closed) return;
            end();
            try { send(CLSE, local, remote, EMPTY); } catch (IOException ignored) { }
        }

        private void end() {
            closed = true;
            streams.remove(local);
            in.add(EOF);
            opened.release();
        }
    }

    private final KeyPair key;
    private final ConcurrentHashMap<Integer, Stream> streams = new ConcurrentHashMap<>();
    private Socket sock;
    private DataInputStream in;
    private OutputStream out;
    private int nextId = 1, maxData = 4096;
    private volatile boolean dead;

    Adb(File dir) throws Exception {
        File priv = new File(dir, "adbkey.pk8"), pub = new File(dir, "adbkey.x509");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (priv.exists() && pub.exists()) {
            key = new KeyPair(kf.generatePublic(new X509EncodedKeySpec(readFile(pub))), kf.generatePrivate(new PKCS8EncodedKeySpec(readFile(priv))));
        } else {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            key = g.generateKeyPair();
            try (FileOutputStream a = new FileOutputStream(priv); FileOutputStream b = new FileOutputStream(pub)) {
                a.write(key.getPrivate().getEncoded());
                b.write(key.getPublic().getEncoded());
            }
        }
    }

    private static byte[] readFile(File f) throws IOException {
        byte[] b = new byte[(int) f.length()];
        try (DataInputStream d = new DataInputStream(new java.io.FileInputStream(f))) { d.readFully(b); }
        return b;
    }

    /** Connects and authenticates. If the TV does not know this app's key yet, waits (3 minutes) for the person to allow it. */
    void connect(String host, int port, Prompt prompt) throws Exception {
        dial(host, port);
        byte[] token = authenticate();
        if (token != null) {
            send(AUTH, AUTH_RSAPUBLICKEY, 0, (publicKey() + "\0").getBytes("UTF-8"));   // unknown key: the TV asks the person
            prompt.waitingForUser();
            awaitApproval(host, port);
        }
        sock.setSoTimeout(0);
        sock.setKeepAlive(true);
        Thread t = new Thread(this::pump, "adb-reader");
        t.setDaemon(true);
        t.start();
    }

    private void awaitApproval(String host, int port) throws Exception {
        Socket asking = sock;
        DataInputStream askingIn = in;
        long end = System.currentTimeMillis() + 180000;
        boolean askingAlive = true;
        while (System.currentTimeMillis() < end) {
            if (askingAlive) {
                sock = asking; in = askingIn; out = asking.getOutputStream();
                try {
                    sock.setSoTimeout(3000);
                    while (true) {
                        Msg m = read();
                        if (m.cmd == CNXN) { maxData = Math.min(Math.max(m.arg1, 4096), 1 << 18); return; }
                    }
                } catch (java.net.SocketTimeoutException waiting) {
                    // still undecided on that connection
                } catch (IOException closedByTv) {
                    askingAlive = false;
                }
            } else {
                Thread.sleep(2000);
            }
            // Some TVs never upgrade the connection that asked; once "Always allow" is ticked a fresh one just works.
            try {
                dial(host, port);
                if (authenticate() == null) { asking.close(); return; }
                sock.close();
            } catch (IOException retry) {
                // adbd busy, try again
            }
        }
        asking.close();
        throw new IOException("timeout");
    }

    private void dial(String host, int port) throws IOException {
        sock = new Socket();
        sock.connect(new InetSocketAddress(host, port), 6000);
        sock.setTcpNoDelay(true);
        sock.setSoTimeout(15000);
        in = new DataInputStream(sock.getInputStream());
        out = sock.getOutputStream();
    }

    /** Signs the TV's challenge with our key. Returns null when connected, or the next challenge if the key is not trusted yet. */
    private byte[] authenticate() throws Exception {
        send(CNXN, 0x01000000, 1 << 18, "host::\0".getBytes("UTF-8"));
        boolean signed = false;
        while (true) {
            Msg m = read();
            if (m.cmd == CNXN) {
                maxData = Math.min(Math.max(m.arg1, 4096), 1 << 18);
                return null;
            }
            if (m.cmd != AUTH || m.arg0 != AUTH_TOKEN) continue;
            if (signed) return m.data;
            signed = true;
            Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            c.init(Cipher.ENCRYPT_MODE, key.getPrivate());
            c.update(SHA1_PREFIX);
            send(AUTH, AUTH_SIGNATURE, 0, c.doFinal(m.data));
        }
    }

    /** Reader thread: routes every incoming packet to its stream. */
    private void pump() {
        try {
            while (true) {
                Msg m = read();
                Stream s = streams.get(m.arg1);
                if (s == null) continue;
                if (m.cmd == OKAY) {
                    if (s.remote == 0) { s.remote = m.arg0; s.opened.release(); } else s.acked.release();
                } else if (m.cmd == WRTE) {
                    s.in.add(m.data);
                    send(OKAY, s.local, s.remote, EMPTY);
                } else if (m.cmd == CLSE) {
                    s.end();
                }
            }
        } catch (IOException e) {
            dead = true;
            for (Stream s : streams.values()) s.end();
        }
    }

    /** Opens an ADB service, e.g. "shell:ls" or "localabstract:name". Throws if the TV refuses it. */
    Stream open(String service) throws IOException {
        if (dead) throw new IOException("adb connection lost");
        Stream s;
        synchronized (this) { s = new Stream(nextId++); }
        streams.put(s.local, s);
        send(OPEN, s.local, 0, (service + "\0").getBytes("UTF-8"));
        try {
            if (!s.opened.tryAcquire(10, TimeUnit.SECONDS)) { s.end(); throw new IOException("no answer from the TV"); }
        } catch (InterruptedException e) {
            throw new IOException("interrupted");
        }
        if (s.closed || s.remote == 0) throw new IOException("refused: " + service);
        return s;
    }

    /** Runs a command in the TV's shell (as the `shell` user) and returns everything it printed. */
    String shell(String cmd) throws IOException {
        try (Stream s = open("shell:" + cmd)) { return s.readAll(); }
    }

    boolean isDead() { return dead; }

    @Override public void close() {
        dead = true;
        try { if (sock != null) sock.close(); } catch (IOException ignored) { }
    }

    private synchronized void send(int cmd, int arg0, int arg1, byte[] data) throws IOException {
        int sum = 0;
        for (byte b : data) sum += b & 0xff;
        ByteBuffer b = ByteBuffer.allocate(24 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(~cmd).put(data);
        out.write(b.array());
        out.flush();
    }

    private Msg read() throws IOException {
        byte[] head = new byte[24];
        in.readFully(head);
        ByteBuffer b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = b.getInt(); m.arg0 = b.getInt(); m.arg1 = b.getInt();
        int len = b.getInt();
        if (len < 0 || len > (1 << 20)) throw new IOException("bad adb packet");
        m.data = new byte[len];
        in.readFully(m.data);
        return m;
    }

    /** The public key in adb's own format: little-endian words of n, R^2 mod n and -1/n mod 2^32, base64 encoded. */
    private String publicKey() {
        RSAPublicKey k = (RSAPublicKey) key.getPublic();
        BigInteger n = k.getModulus(), r32 = BigInteger.ONE.shiftLeft(32), r = BigInteger.ONE.shiftLeft(2048);
        ByteBuffer b = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(64).putInt(n.mod(r32).modInverse(r32).negate().mod(r32).intValue());
        putWords(b, n);
        putWords(b, r.multiply(r).mod(n));
        b.putInt(k.getPublicExponent().intValue());
        return Base64.encodeToString(b.array(), Base64.NO_WRAP) + " tvremote@phone";
    }

    private static void putWords(ByteBuffer b, BigInteger v) {
        for (int i = 0; i < 64; i++) b.putInt(v.shiftRight(32 * i).intValue());
    }
}
