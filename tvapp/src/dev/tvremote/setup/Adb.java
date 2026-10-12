package dev.tvremote.setup;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * Just enough of the ADB wire protocol to open a shell on the TV this app runs on (127.0.0.1:5555) and read its output.
 * One stream at a time, synchronous. The first connection makes the TV ask "Allow USB debugging?" for this app's key.
 */
final class Adb implements Closeable {
    private static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f, CLSE = 0x45534c43, WRTE = 0x45545257;
    private static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;
    private static final byte[] EMPTY = new byte[0];
    /** ASN.1 DigestInfo header for SHA-1: adbd treats the 20-byte token as a SHA-1 digest to be PKCS#1 v1.5 signed. */
    private static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    interface Prompt { void waitingForUser(); }

    private static final class Msg { int cmd, arg0, arg1; byte[] data; }

    private final KeyPair key;
    private Socket sock;
    private DataInputStream in;
    private OutputStream out;
    private int nextId = 1;

    Adb(File dir) throws Exception {
        File priv = new File(dir, "adbkey.pk8"), pub = new File(dir, "adbkey.x509");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (priv.exists() && pub.exists()) {
            key = new KeyPair(kf.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(pub.toPath()))),
                    kf.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(priv.toPath()))));
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

    void connect(String host, int port, Prompt prompt) throws Exception {
        open(host, port);
        byte[] token = authenticate();
        if (token == null) return;                                    // this app's key is already trusted
        send(AUTH, AUTH_RSAPUBLICKEY, 0, (publicKey() + "\0").getBytes("UTF-8"));   // unknown key: the TV asks the person
        prompt.waitingForUser();
        Socket asking = sock;
        DataInputStream askingIn = in;
        long end = System.currentTimeMillis() + 180000;
        boolean askingAlive = true;
        while (System.currentTimeMillis() < end) {
            if (askingAlive) {
                sock = asking; in = askingIn; out = asking.getOutputStream();
                try {
                    sock.setSoTimeout(3000);
                    while (true) if (read().cmd == CNXN) { sock.setSoTimeout(120000); return; }
                } catch (java.net.SocketTimeoutException waiting) {
                    // still undecided on that connection
                } catch (IOException closed) {
                    askingAlive = false;
                }
            } else {
                Thread.sleep(2000);
            }
            // Some TVs never upgrade the connection that asked; once "Always allow" is ticked a fresh one just works.
            try {
                open(host, port);
                if (authenticate() == null) { asking.close(); return; }
                sock.close();
            } catch (IOException retry) {
                // adbd busy, try again
            }
        }
        asking.close();
        throw new IOException("hết thời gian chờ cho phép trên TV");
    }

    private void open(String host, int port) throws IOException {
        sock = new Socket();
        sock.connect(new InetSocketAddress(host, port), 5000);
        sock.setTcpNoDelay(true);
        sock.setSoTimeout(15000);
        in = new DataInputStream(sock.getInputStream());
        out = sock.getOutputStream();
        nextId = 1;
    }

    /** Signs the TV's challenge with our key. Returns null when connected, or the next challenge if the key is not trusted yet. */
    private byte[] authenticate() throws Exception {
        send(CNXN, 0x01000000, 4096, "host::\0".getBytes("UTF-8"));
        boolean signed = false;
        while (true) {
            Msg m = read();
            if (m.cmd == CNXN) {
                sock.setSoTimeout(120000);
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

    /** Runs a command in the TV's shell (as the `shell` user) and returns everything it printed. */
    String shell(String cmd) throws IOException {
        int local = nextId++;
        send(OPEN, local, 0, ("shell:" + cmd + "\0").getBytes("UTF-8"));
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (true) {
            Msg m = read();
            if (m.arg1 != local) continue;
            if (m.cmd == WRTE) {
                buf.write(m.data);
                send(OKAY, local, m.arg0, EMPTY);
            } else if (m.cmd == CLSE) {
                send(CLSE, local, m.arg0, EMPTY);
                break;
            }
        }
        return buf.toString("UTF-8").replace("\r\n", "\n").trim();
    }

    @Override public void close() {
        try { if (sock != null) sock.close(); } catch (IOException ignored) { }
    }

    private void send(int cmd, int arg0, int arg1, byte[] data) throws IOException {
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
        return Base64.encodeToString(b.array(), Base64.NO_WRAP) + " tvsetup@tv";
    }

    private static void putWords(ByteBuffer b, BigInteger v) {
        for (int i = 0; i < 64; i++) b.putInt(v.shiftRight(32 * i).intValue());
    }
}
