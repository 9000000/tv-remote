package dev.tvremote.home.apps

import android.content.Context
import org.json.JSONArray

/**
 * TV Home's OWN lock, for a TV that has no TV Setup (or one too old to keep rules): a PIN made on the TV and a list of locked apps, both
 * kept in this app's prefs (the PIN only as a salted hash). It blocks opening the app from TV Home (grid, dock, menu, a key assigned
 * in TV Home); an app opened another way (the remote's own app button, the Assistant, recents) is NOT blocked, only TV Setup's lock
 * (which watches every open) does that. The same lockout as TV Setup's PIN screen: 5 wrong codes in a row = a wait of 1, 5, 15, then 60 min.
 * Forgetting the code: Settings → Apps → TV Home → Clear data. When TV Setup is there its rules win: [AppRules] moves this list to it
 * the first time the parent opens Lock & limits ([clear] after).
 */
object HomeLock {
    const val TRIES = 5
    val WAITS_MIN = longArrayOf(1, 5, 15, 60)
    const val LEN = 4

    /** [wrong] codes in a row, [level] waits so far (which one is next), [until] when the last wait ends. */
    data class Tries(val wrong: Int = 0, val level: Int = 0, val until: Long = 0L)

    sealed class Verdict {
        object Ok : Verdict()
        data class Wrong(val left: Int) : Verdict()
        data class Wait(val seconds: Long) : Verdict()
    }

    fun valid(pin: String) = pin.length == LEN && pin.all { it in '0'..'9' }

    /** The salted hash kept instead of the PIN (iterated: a stolen prefs file is slow to guess). */
    fun hash(salt: String, pin: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        var d = "$salt:$pin".toByteArray()
        repeat(10_000) { d = md.digest(d) }
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun seconds(ms: Long) = (ms + 999) / 1000

    /** The try [pin] against what is kept: the verdict and the new count. A try made during a wait is never allowed (and not counted). */
    fun attempt(salt: String, stored: String, pin: String, t: Tries, now: Long): Pair<Verdict, Tries> {
        if (now < t.until) return Verdict.Wait(seconds(t.until - now)) to t
        if (stored.isNotEmpty() && hash(salt, pin) == stored) return Verdict.Ok to Tries()
        val w = t.wrong + 1
        val n = if (w < TRIES) Tries(w, t.level, t.until)
                else Tries(0, minOf(t.level + 1, WAITS_MIN.size), now + WAITS_MIN[minOf(t.level, WAITS_MIN.size - 1)] * 60_000L)
        return (if (n.until > now) Verdict.Wait(seconds(n.until - now)) else Verdict.Wrong(TRIES - n.wrong)) to n
    }

    /** Whether opening [pkg] must ask the PIN: it is locked and has not just been cleared. */
    fun needsPin(locked: Set<String>, pkg: String, cleared: Boolean) = !cleared && pkg in locked

    // ---- prefs ----

    private fun prefs(c: Context) = c.getSharedPreferences("homelock", Context.MODE_PRIVATE)

    fun has(c: Context) = prefs(c).getString("hash", "").orEmpty().isNotEmpty()

    fun locked(c: Context): Set<String> = try {
        JSONArray(prefs(c).getString("apps", "[]")).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
    } catch (e: Exception) { emptySet() }

    fun isLocked(c: Context, pkg: String) = pkg in locked(c)

    @Synchronized fun set(c: Context, pkg: String, on: Boolean) {
        prefs(c).edit().putString("apps", JSONArray(if (on) locked(c) + pkg else locked(c) - pkg).toString()).apply()
    }

    @Synchronized fun create(c: Context, pin: String) {
        require(valid(pin)) { "bad PIN" }
        val salt = java.util.UUID.randomUUID().toString()
        prefs(c).edit().putString("salt", salt).putString("hash", hash(salt, pin)).putInt("wrong", 0).putInt("level", 0).putLong("until", 0L).commit()
    }

    /** Wrong or right: asks the PIN with the lockout counted and kept. */
    @Synchronized fun check(c: Context, pin: String, now: Long = System.currentTimeMillis()): Verdict {
        val p = prefs(c)
        val (v, t) = attempt(p.getString("salt", "").orEmpty(), p.getString("hash", "").orEmpty(), pin, Tries(p.getInt("wrong", 0), p.getInt("level", 0), p.getLong("until", 0L)), now)
        p.edit().putInt("wrong", t.wrong).putInt("level", t.level).putLong("until", t.until).commit()
        return v
    }

    /** Seconds left of a wait, 0 when not waiting. */
    fun waiting(c: Context, now: Long = System.currentTimeMillis()): Long = prefs(c).getLong("until", 0L).let { if (it > now) seconds(it - now) else 0L }

    /** The list and the PIN both go (TV Setup has them now). */
    @Synchronized fun clear(c: Context) { prefs(c).edit().clear().commit() }
}
