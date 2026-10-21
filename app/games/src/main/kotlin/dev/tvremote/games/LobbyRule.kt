package dev.tvremote.games

/** When the lobby starts the game by itself: at least one phone is in, every phone has pressed Ready and the game has the players it needs. */
object LobbyRule {
    /** One player in the lobby. A physical pad ([phone] false) has no Ready button, so it never holds the others up (nor starts the game alone). */
    data class P(val slot: Int, val phone: Boolean, val ready: Boolean)

    fun allReady(ps: List<P>, min: Int = 1): Boolean = ps.filter { it.phone }.let { ph -> ph.isNotEmpty() && ph.all { it.ready } && ps.size >= min }

    /** OK on the remote with too few players asks once before it starts anyway. */
    fun tooFew(ps: List<P>, min: Int): Boolean = ps.size < min
}
