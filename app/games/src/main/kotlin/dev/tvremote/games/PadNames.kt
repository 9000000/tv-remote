package dev.tvremote.games

/** The pure parts of the phones' slots: a player's typed name, and which slot a phone gets. */
object PadNames {
    const val MAX = 16                                     // characters (code points: an emoji counts one)

    /** What a phone typed as its name: no control or bidi characters, spaces collapsed, trimmed, at most [MAX] code points. null = empty (the default "Player N"). */
    fun clean(raw: String?): String? {
        if (raw == null) return null
        val s = raw.replace(Regex("[\\p{Cc}\\u2028\\u2029\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]"), " ").replace(Regex("\\s+"), " ").trim()
        if (s.isEmpty()) return null
        val cps = s.codePoints().limit(MAX.toLong()).toArray()
        return String(cps, 0, cps.size).trim().ifEmpty { null }
    }

    /** One slot as [pick] sees it. */
    data class S(val cid: String?, val connected: Boolean, val physical: Boolean, val reserved: Boolean)

    /**
     * The slot for a phone: its own again (same client id, even after the app was restarted), else the first free one nobody is keeping,
     * else a free one whose keeping time is running. -1 = full.
     */
    fun pick(slots: List<S>, cid: String): Int {
        val own = slots.indexOfFirst { it.cid == cid }
        if (own >= 0) return own
        return slots.indexOfFirst { !it.connected && !it.physical && !it.reserved }.takeIf { it >= 0 }
            ?: slots.indexOfFirst { !it.connected && !it.physical }
    }
}
