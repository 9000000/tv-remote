// Every key a release needs, in one file locked with a password, to keep somewhere else (a Drive, a USB stick): the two
// Ed25519 keys that sign SHA256SUMS (dev/Sign.java) and the two signing.jks that sign the APKs. Losing them means no
// installed app takes an update again, so this is the way back. Run through dev/keys.sh (it finds the files):
//   java dev/Keys.java pack    <out> <name=path>...   asks a password twice, writes <out>
//   java dev/Keys.java unpack  <file> <name=path>...  asks the password, writes each name to its path (never over a different file)
//   java dev/Keys.java list    <file>                 asks the password, prints the names and sizes
// The file: "TVRKEYS1", salt (16), PBKDF2-SHA256 rounds (4), IV (12), then AES-256-GCM of a zip. GCM fails on a wrong password
// or a changed byte, so a damaged copy is told apart from a good one; plain JDK, nothing to install.
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.*;

public class Keys {
    static final byte[] MAGIC = "TVRKEYS1".getBytes();
    static final int ROUNDS = 600_000;                                // OWASP's figure for PBKDF2-SHA256: about a second here

    public static void main(String[] a) throws Exception {
        try { run(a); } catch (IllegalStateException e) { System.err.println(e.getMessage()); System.exit(1); }   // said in words, no trace
    }

    static void run(String[] a) throws Exception {
        switch (a.length > 0 ? a[0] : "") {
            case "pack" -> pack(Path.of(a[1]), pairs(a, 2));
            case "unpack" -> unpack(Path.of(a[1]), pairs(a, 2));
            case "list" -> read(Path.of(a[1]), ask("Password: ")).forEach((k, v) -> System.out.println("  " + k + "  " + v.length + " bytes"));
            default -> { System.err.println("pack <out> name=path... | unpack <file> name=path... | list <file>"); System.exit(2); }
        }
    }

    static LinkedHashMap<String, Path> pairs(String[] a, int from) {
        LinkedHashMap<String, Path> m = new LinkedHashMap<>();
        for (int i = from; i < a.length; i++) { int e = a[i].indexOf('='); m.put(a[i].substring(0, e), Path.of(a[i].substring(e + 1))); }
        return m;
    }

    static char[] ask(String prompt) {
        Console c = System.console();
        if (c == null) throw new IllegalStateException("run it in a terminal: the password is typed, never passed as an argument");
        return c.readPassword(prompt);
    }

    static SecretKey key(char[] pw, byte[] salt, int rounds) throws Exception {
        byte[] k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(pw, salt, rounds, 256)).getEncoded();
        return new SecretKeySpec(k, "AES");
    }

    static void pack(Path out, Map<String, Path> files) throws Exception {
        if (Files.exists(out)) throw new IllegalStateException(out + " exists: not overwriting");
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(zip)) {
            for (var f : files.entrySet()) {
                if (!Files.exists(f.getValue())) { System.out.println("  - " + f.getKey() + ": not here (" + f.getValue() + "), left out"); continue; }
                z.putNextEntry(new ZipEntry(f.getKey()));
                z.write(Files.readAllBytes(f.getValue()));
                z.closeEntry();
                System.out.println("  + " + f.getKey());
            }
        }
        char[] pw = ask("Password (12+ characters, keep it apart from the file): ");
        if (pw.length < 12) throw new IllegalStateException("too short: 12 characters at least");
        if (!Arrays.equals(pw, ask("Again: "))) throw new IllegalStateException("the two did not match");
        SecureRandom r = new SecureRandom();
        byte[] salt = new byte[16], iv = new byte[12];
        r.nextBytes(salt); r.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key(pw, salt, ROUNDS), new GCMParameterSpec(128, iv));
        c.updateAAD(MAGIC);
        byte[] body = c.doFinal(zip.toByteArray());
        try (DataOutputStream d = new DataOutputStream(Files.newOutputStream(out, StandardOpenOption.CREATE_NEW))) {
            d.write(MAGIC); d.write(salt); d.writeInt(ROUNDS); d.write(iv); d.write(body);
        }
        System.out.println("Wrote " + out + " (" + Files.size(out) + " bytes). Check it opens: dev/keys.sh list " + out);
    }

    static LinkedHashMap<String, byte[]> read(Path file, char[] pw) throws Exception {
        DataInputStream d = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(file)));
        byte[] magic = new byte[8], salt = new byte[16], iv = new byte[12];
        d.readFully(magic);
        if (!Arrays.equals(magic, MAGIC)) throw new IllegalStateException(file + " is not a TV Remote keys file");
        d.readFully(salt);
        int rounds = d.readInt();
        d.readFully(iv);
        byte[] body = d.readAllBytes();
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(pw, salt, rounds), new GCMParameterSpec(128, iv));
        c.updateAAD(MAGIC);
        byte[] zip;
        try { zip = c.doFinal(body); } catch (AEADBadTagException e) { throw new IllegalStateException("wrong password, or the file was changed"); }
        LinkedHashMap<String, byte[]> m = new LinkedHashMap<>();
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) m.put(e.getName(), z.readAllBytes());
        }
        return m;
    }

    static void unpack(Path file, Map<String, Path> to) throws Exception {
        var got = read(file, ask("Password: "));
        for (var e : got.entrySet()) {
            Path p = to.get(e.getKey());
            if (p == null) { System.out.println("  ? " + e.getKey() + ": no place for it, skipped"); continue; }
            if (Files.exists(p)) {
                if (Arrays.equals(Files.readAllBytes(p), e.getValue())) { System.out.println("  = " + e.getKey() + ": already there"); continue; }
                p = p.resolveSibling(p.getFileName() + ".restored");      // a different key is there: both kept, the person chooses
                System.out.println("  ! " + e.getKey() + ": a different file is in place, this one written beside it as " + p);
            }
            Files.createDirectories(p.toAbsolutePath().getParent());
            Files.write(p, e.getValue(), StandardOpenOption.CREATE_NEW);
            try { Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rw-------")); } catch (UnsupportedOperationException ignored) { }
            System.out.println("  + " + e.getKey() + " -> " + p);
        }
    }
}
