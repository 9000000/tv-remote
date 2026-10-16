// The release's signature: Ed25519 over SHA256SUMS (the SHA-256 of every file of the release), checked by every app before
// it installs an update (update/Release.kt, update/Ed25519.kt). Run with the JDK, no build:
//   java dev/Sign.java new  <key>          a new key (PKCS#8 PEM, only this user can read it); prints the public key for Release.KEYS
//   java dev/Sign.java pub  <key>          prints the public key again
//   java dev/Sign.java sign <key> <file>   writes <file>.sig: the signature, base64, one line
// The private key never goes to GitHub (a secret there is usable by whoever can change a workflow): it stays on the
// maintainer's computer, and a second one (the spare, its public key also in Release.KEYS) is kept away from it.
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public class Sign {
    public static void main(String[] a) throws Exception {
        switch (a.length > 0 ? a[0] : "") {
            case "new" -> {
                Path p = Path.of(a[1]);
                if (Files.exists(p)) throw new IllegalStateException(p + " exists: not overwriting a release key");
                Files.createDirectories(p.toAbsolutePath().getParent());
                KeyPair k = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
                String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(k.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
                Files.writeString(p, pem);
                try { Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rw-------")); } catch (UnsupportedOperationException ignored) { }
                System.out.println(hex(pub(k.getPublic())));
            }
            case "pub" -> System.out.println(hex(pub(KeyFactory.getInstance("Ed25519").generatePublic(publicOf(load(a[1]))))));
            case "sign" -> {
                Signature s = Signature.getInstance("Ed25519");
                s.initSign(load(a[1]));
                s.update(Files.readAllBytes(Path.of(a[2])));
                Files.writeString(Path.of(a[2] + ".sig"), Base64.getEncoder().encodeToString(s.sign()) + "\n");
            }
            default -> { System.err.println("java dev/Sign.java new|pub|sign <key> [file]"); System.exit(2); }
        }
    }

    static PrivateKey load(String path) throws Exception {
        String b = Files.readString(Path.of(path)).replaceAll("-----[A-Z ]+-----|\\s", "");
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(b)));
    }

    // the JDK has no call that derives an Ed25519 public key from the private one: a KeyPairGenerator fed the same seed does
    static java.security.spec.EncodedKeySpec publicOf(PrivateKey k) throws Exception {
        byte[] enc = k.getEncoded();
        byte[] seed = java.util.Arrays.copyOfRange(enc, enc.length - 32, enc.length);
        KeyPairGenerator g = KeyPairGenerator.getInstance("Ed25519");
        g.initialize(255, new SecureRandom() { @Override public void nextBytes(byte[] b) { System.arraycopy(seed, 0, b, 0, b.length); } });
        return new java.security.spec.X509EncodedKeySpec(g.generateKeyPair().getPublic().getEncoded());
    }

    static byte[] pub(PublicKey k) { byte[] e = k.getEncoded(); return java.util.Arrays.copyOfRange(e, e.length - 32, e.length); }

    static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); }
}
