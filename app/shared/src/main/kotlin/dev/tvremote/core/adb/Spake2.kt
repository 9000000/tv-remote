package dev.tvremote.core.adb
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * SPAKE2 over edwards25519 the way BoringSSL does it (the one adb's pairing uses), in plain BigInteger arithmetic: the
 * pairing is a few messages, so speed does not matter, and nothing native has to ship with the apps. The points M and N
 * are BoringSSL's own (recovered from the real adb: not the RFC 9382 ones). The private scalar is multiplied by the
 * cofactor 8 (the password's is not: BoringSSL only pads it with multiples of L, which changes nothing on prime-order points),
 * and the transcript is hashed with SHA-512, every part prefixed by its length as 8 bytes little-endian.
 *
 * [alice] is the side that starts (adb's client), the other is bob. [random] is for tests: 64 bytes standing for the secret.
 */
class Spake2(private val alice: Boolean, private val myName: ByteArray, private val theirName: ByteArray, random: ByteArray? = null) {
    private val secret: BigInteger = (random ?: ByteArray(64).also { SecureRandom().nextBytes(it) }).let { le(it).mod(L).shiftLeft(3) }
    private var myMsg: ByteArray? = null
    private var passwordHash: ByteArray? = null
    private var passwordScalar: BigInteger? = null

    /** The 32 bytes to send to the other side, made from the shared [password]. */
    fun generate(password: ByteArray): ByteArray {
        check(myMsg == null) { "already generated" }
        val hash = MessageDigest.getInstance("SHA-512").digest(password)
        passwordHash = hash
        val w = le(hash).mod(L)
        passwordScalar = w
        val mask = mul(if (alice) M else N, w)
        val msg = encode(add(mul(BASE, secret), mask))
        myMsg = msg
        return msg
    }

    /** The other side's 32 bytes in, the 64 bytes of shared key out (equal on both sides only when the passwords were). */
    fun process(theirMsg: ByteArray): ByteArray {
        val mine = myMsg ?: throw IllegalStateException("generate first")
        if (theirMsg.size != 32) throw IllegalArgumentException("bad pairing message")
        val q = decode(theirMsg) ?: throw IllegalArgumentException("bad pairing message")
        val unmasked = add(q, neg(mul(if (alice) N else M, passwordScalar!!)))
        val shared = encode(mul(unmasked, secret))
        val sha = MessageDigest.getInstance("SHA-512")
        fun part(b: ByteArray) {
            val n = ByteArray(8) { (b.size.toLong() shr (8 * it)).toByte() }
            sha.update(n)
            sha.update(b)
        }
        if (alice) { part(myName); part(theirName); part(mine); part(theirMsg) }
        else { part(theirName); part(myName); part(theirMsg); part(mine) }
        part(shared)
        part(passwordHash!!)
        return sha.digest()
    }

    /** A point in extended coordinates (x = X/Z, y = Y/Z, xy = T/Z). */
    private class Pt(val x: BigInteger, val y: BigInteger, val z: BigInteger, val t: BigInteger)

    companion object {
        private val P = BigInteger.ONE.shiftLeft(255) - BigInteger.valueOf(19)
        private val L = BigInteger.ONE.shiftLeft(252) + BigInteger("27742317777372353535851937790883648493")
        private val D = BigInteger.valueOf(-121665).multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P)
        private val D2 = D.shiftLeft(1).mod(P)
        private val SQRT_M1 = BigInteger.valueOf(2).modPow((P - BigInteger.ONE).shiftRight(2), P)

        private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        private val M: Pt by lazy { decode(hex("932581b409222652499d92cdece394a3ae5e1cb85cb870ac3030bbe477112ed1"))!! }
        private val N: Pt by lazy { decode(hex("2d803ae3e80adc51cab57648e5becb4bef79f45fd130ca4f497ddb623b76ce84"))!! }
        private val BASE: Pt by lazy {
            val y = BigInteger.valueOf(4).multiply(BigInteger.valueOf(5).modInverse(P)).mod(P)
            decode(encodeY(y, false))!!
        }
        private val ZERO = Pt(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)

        private fun le(b: ByteArray): BigInteger = BigInteger(1, b.reversedArray())

        private fun encodeY(y: BigInteger, odd: Boolean): ByteArray {
            val out = ByteArray(32)
            val raw = y.toByteArray().reversedArray()
            for (i in 0 until minOf(32, raw.size)) out[i] = raw[i]
            if (odd) out[31] = (out[31].toInt() or 0x80).toByte()
            return out
        }

        private fun add(a: Pt, b: Pt): Pt {
            val aa = (a.y - a.x).multiply(b.y - b.x).mod(P)
            val bb = (a.y + a.x).multiply(b.y + b.x).mod(P)
            val cc = a.t.multiply(D2).multiply(b.t).mod(P)
            val dd = a.z.shiftLeft(1).multiply(b.z).mod(P)
            val e = bb - aa
            val f = dd - cc
            val g = dd + cc
            val h = bb + aa
            return Pt(e.multiply(f).mod(P), g.multiply(h).mod(P), f.multiply(g).mod(P), e.multiply(h).mod(P))
        }

        private fun neg(a: Pt) = Pt(a.x.negate().mod(P), a.y, a.z, a.t.negate().mod(P))

        private fun mul(a: Pt, k: BigInteger): Pt {
            var r = ZERO
            var q = a
            for (i in 0 until k.bitLength()) {
                if (k.testBit(i)) r = add(r, q)
                q = add(q, q)
            }
            return r
        }

        private fun encode(a: Pt): ByteArray {
            val zi = a.z.modInverse(P)
            val x = a.x.multiply(zi).mod(P)
            val y = a.y.multiply(zi).mod(P)
            return encodeY(y, x.testBit(0))
        }

        private fun decode(b: ByteArray): Pt? {
            if (b.size != 32) return null
            val odd = b[31].toInt() and 0x80 != 0
            val c = b.copyOf().also { it[31] = (it[31].toInt() and 0x7f).toByte() }
            val y = le(c)
            if (y >= P) return null
            val yy = y.multiply(y).mod(P)
            val u = yy - BigInteger.ONE
            val v = D.multiply(yy).add(BigInteger.ONE).mod(P)
            var x = u.multiply(v.modPow(P - BigInteger.valueOf(2), P)).mod(P).modPow((P + BigInteger.valueOf(3)).shiftRight(3), P)
            if (v.multiply(x).multiply(x).subtract(u).mod(P).signum() != 0) {
                x = x.multiply(SQRT_M1).mod(P)
                if (v.multiply(x).multiply(x).subtract(u).mod(P).signum() != 0) return null
            }
            if (x.testBit(0) != odd) x = x.negate().mod(P)
            return Pt(x, y, BigInteger.ONE, x.multiply(y).mod(P))
        }

        /** For tests: a point's encoding, to compare with BoringSSL's. */
        internal fun roundTrip(b: ByteArray): ByteArray? = decode(b)?.let { encode(it) }
        internal fun baseTimes(k: BigInteger): ByteArray = encode(mul(BASE, k))
    }
}
