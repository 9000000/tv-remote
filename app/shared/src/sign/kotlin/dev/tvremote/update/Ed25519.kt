package dev.tvremote.update

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Ed25519 signature checking (RFC 8032, 5.1.7), for the signed list of a release's files (SHA256SUMS.sig). Android has
 * Ed25519 only from 13 (the apps start at 5), so it is written out here: checking only, no secret is ever held, so plain
 * BigInteger arithmetic (not constant time) is fine. EdSignTest checks it against the JDK's own Ed25519.
 */
object Ed25519 {
    private val P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val L = BigInteger.ONE.shiftLeft(252).add(BigInteger("27742317777372353535851937790883648493"))
    private val D = BigInteger.valueOf(-121665).multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P)
    private val SQRT_M1 = BigInteger.valueOf(2).modPow(P.subtract(BigInteger.ONE).shiftRight(2), P)
    private val TWO = BigInteger.valueOf(2)

    private class Pt(val x: BigInteger, val y: BigInteger, val z: BigInteger, val t: BigInteger)   // extended coordinates

    private val ZERO_PT = Pt(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)
    private val B: Pt = run {
        val y = BigInteger.valueOf(4).multiply(BigInteger.valueOf(5).modInverse(P)).mod(P)
        val x = recoverX(y, 0)!!
        Pt(x, y, BigInteger.ONE, x.multiply(y).mod(P))
    }

    private fun add(p: Pt, q: Pt): Pt {
        val a = p.y.subtract(p.x).multiply(q.y.subtract(q.x)).mod(P)
        val b = p.y.add(p.x).multiply(q.y.add(q.x)).mod(P)
        val c = TWO.multiply(p.t).multiply(q.t).multiply(D).mod(P)
        val d = TWO.multiply(p.z).multiply(q.z).mod(P)
        val e = b.subtract(a); val f = d.subtract(c); val g = d.add(c); val h = b.add(a)
        return Pt(e.multiply(f).mod(P), g.multiply(h).mod(P), f.multiply(g).mod(P), e.multiply(h).mod(P))
    }

    private fun mul(s: BigInteger, p: Pt): Pt {
        var q = ZERO_PT; var r = p; var k = s
        while (k.signum() > 0) {
            if (k.testBit(0)) q = add(q, r)
            r = add(r, r); k = k.shiftRight(1)
        }
        return q
    }

    private fun same(p: Pt, q: Pt): Boolean =
        p.x.multiply(q.z).subtract(q.x.multiply(p.z)).mod(P).signum() == 0 &&
            p.y.multiply(q.z).subtract(q.y.multiply(p.z)).mod(P).signum() == 0

    private fun recoverX(y: BigInteger, sign: Int): BigInteger? {
        if (y >= P) return null
        val x2 = y.multiply(y).subtract(BigInteger.ONE).multiply(D.multiply(y).multiply(y).add(BigInteger.ONE).modInverse(P)).mod(P)
        if (x2.signum() == 0) return if (sign == 1) null else BigInteger.ZERO
        var x = x2.modPow(P.add(BigInteger.valueOf(3)).shiftRight(3), P)
        if (x.multiply(x).subtract(x2).mod(P).signum() != 0) x = x.multiply(SQRT_M1).mod(P)
        if (x.multiply(x).subtract(x2).mod(P).signum() != 0) return null
        if (x.testBit(0) != (sign == 1)) x = P.subtract(x)
        return x
    }

    private fun le(b: ByteArray): BigInteger = BigInteger(1, b.reversedArray())

    private fun point(b: ByteArray): Pt? {
        if (b.size != 32) return null
        val sign = (b[31].toInt() shr 7) and 1
        val y = le(b.copyOf().also { it[31] = (it[31].toInt() and 0x7f).toByte() })
        val x = recoverX(y, sign) ?: return null
        return Pt(x, y, BigInteger.ONE, x.multiply(y).mod(P))
    }

    /** True when [sig] (64 bytes) is [key]'s (32 bytes, the raw public key) signature of [msg]. */
    fun verify(key: ByteArray, msg: ByteArray, sig: ByteArray): Boolean {
        if (key.size != 32 || sig.size != 64) return false
        val a = point(key) ?: return false
        val rs = sig.copyOfRange(0, 32)
        val r = point(rs) ?: return false
        val s = le(sig.copyOfRange(32, 64))
        if (s >= L) return false
        val h = le(MessageDigest.getInstance("SHA-512").apply { update(rs); update(key); update(msg) }.digest()).mod(L)
        return same(mul(s, B), add(r, mul(h, a)))
    }
}
