package dev.tvremote.home.manage

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Who may use [ManageServer]: the one-time key in the QR's address, then a session. Plain JVM (a clock and a source of randomness are
 * handed in), so the rules are tested without a TV.
 *  - [issue] makes a key and cuts every session of the last one.
 *  - [redeem] takes the key once: a session id for the cookie, or null (wrong, used, or the key was voided by too many wrong guesses).
 *  - [check] says whether a cookie is a live session: not past [sessionMs] since it was made, and the server not idle for [idleMs].
 *  - [idle] is true when nothing authorised came for [idleMs]: the server stops itself then.
 * Time is the monotonic clock, as every wait in the project: the TV setting its clock from the network must not end or extend a session.
 */
class Access(
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val random: (Int) -> String = ::randomText,
    val idleMs: Long = IDLE_MS,
    val sessionMs: Long = SESSION_MS,
) {
    private var key: String? = null
    private val sessions = HashMap<String, Long>()                     // session id -> when it was made
    private var last = clock()
    private var wrong = 0

    /** A new key (the old one and all sessions stop working); the idle time starts again. */
    @Synchronized fun issue(): String {
        val k = random(KEY_LEN)
        key = k; sessions.clear(); wrong = 0; last = clock()
        return k
    }

    /** The key used once: a session id, and the key is gone. Null for anything else; [MAX_WRONG] wrong tries void the key. */
    @Synchronized fun redeem(given: String?): String? {
        val k = key ?: return null
        if (given != null && given.length == k.length && MessageDigest.isEqual(given.toByteArray(), k.toByteArray())) {   // constant time
            key = null
            val id = random(SESSION_LEN)
            sessions[id] = clock(); last = clock()
            return id
        }
        if (++wrong >= MAX_WRONG) key = null                            // a guesser gets nowhere: the owner opens a new code
        return null
    }

    /** True for a live session; the request counts as use (it keeps the server from going idle). */
    @Synchronized fun check(session: String?): Boolean {
        if (session == null) return false
        val now = clock()
        val made = sessions.entries.firstOrNull { it.key.length == session.length && MessageDigest.isEqual(it.key.toByteArray(), session.toByteArray()) } ?: return false
        if (now - made.value >= sessionMs || now - last >= idleMs) { sessions.remove(made.key); return false }
        last = now
        return true
    }

    @Synchronized fun idle() = clock() - last >= idleMs

    /** Sessions alive now (phones that opened the page and have not run out). */
    @Synchronized fun connected(): Int { val now = clock(); sessions.values.removeAll { now - it >= sessionMs }; return sessions.size }

    @Synchronized fun keyOpen() = key != null

    companion object {
        const val IDLE_MS = 10 * 60 * 1000L                             // no request for 10 minutes: the server stops
        const val SESSION_MS = 60 * 60 * 1000L                          // a phone is signed in for an hour at most
        const val KEY_LEN = 12
        const val SESSION_LEN = 32
        const val MAX_WRONG = 30
        private val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"         // no 0/o/1/l/i: it may be read aloud or typed
        private val rng = SecureRandom()
        fun randomText(n: Int) = String(CharArray(n) { ALPHABET[rng.nextInt(ALPHABET.length)] })
    }
}
