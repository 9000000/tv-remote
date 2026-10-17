package dev.tvremote.core

import dev.tvremote.core.picture.Tv
/**
 * One TV saved twice: picked once on the home Wi-Fi and once over Tailscale, it would be two rows, and an action on
 * several TVs would run twice on it. Both apps fold such rows into one with this rule.
 */
object SameTv {
    /** A saved TV: the address it is saved under, and what was learnt about it once connected (any may be ""). */
    data class Tv(val host: String, val lan: String = "", val ts: String = "", val mac: String = "") {
        val addresses: Set<String> get() = setOf(host, lan, ts).filter { it.isNotEmpty() }.toSet()
    }

    fun same(a: Tv, b: Tv): Boolean {
        if (a.host.isEmpty() || b.host.isEmpty()) return false
        if (a.mac.isNotEmpty() && b.mac.isNotEmpty()) return a.mac.equals(b.mac, true)
        // by address only through the one a row is saved under: two homes can both have a TV at 192.168.1.5
        return a.host in b.addresses || b.host in a.addresses
    }

    /** [items] in their order, each with the later ones that are the same TV: the first is kept, the others fold into it. */
    fun <T> groups(items: List<T>, tv: (T) -> Tv): List<Pair<T, List<T>>> {
        val left = items.toMutableList()
        val out = ArrayList<Pair<T, List<T>>>()
        while (left.isNotEmpty()) {
            val keep = left.removeAt(0)
            val dup = left.filter { same(tv(keep), tv(it)) }
            left.removeAll(dup)
            out.add(keep to dup)
        }
        return out
    }
}
