package dev.tvremote.phone

import org.json.JSONObject

// The infrared code sets (assets/ir/codes.json, made by dev/ir_codes.py from Flipper-IRDB): one profile per remote, its keys named
// as in Scenes.KEYS. Plain Kotlin + org.json, so IrCodesTest reads the real file.

internal class IrProfile(val id: String, val brand: String, val name: String, val keys: Map<String, String>) {
    val title: String get() = "$brand $name"
}

internal object IrCodes {
    /** Every key a profile may hold, in the order the screen and the file list them. */
    val KEYS = listOf("POWER", "HOME", "BACK", "UP", "DOWN", "LEFT", "RIGHT", "OK", "MENU", "SETTINGS", "VOL_UP", "VOL_DOWN", "MUTE", "CH_UP", "CH_DOWN",
        "INPUT", "PLAY_PAUSE", "STOP", "REWIND", "FFWD", "NEXT", "PREV", "INFO", "GUIDE", "SUBTITLE") + (0..9).map { it.toString() }

    /** The keys "Dò mã" can try: one the box surely answers, whatever remote it came with. */
    val PROBES = listOf("POWER", "HOME", "VOL_UP", "MUTE")

    fun parse(text: String): List<IrProfile> {
        val arr = JSONObject(text).getJSONArray("profiles")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val k = o.getJSONObject("keys")
            val keys = LinkedHashMap<String, String>()
            for (n in k.keys()) keys[n] = k.getString(n)
            IrProfile(o.getString("id"), o.getString("brand"), o.getString("name"), keys)
        }
    }

    /** The profiles that answer to [key] differently: the first of each distinct code, with how many share it. Dò mã steps through these, not every model. */
    fun distinct(all: List<IrProfile>, key: String): List<Pair<IrProfile, Int>> {
        val groups = LinkedHashMap<String, MutableList<IrProfile>>()
        for (p in all) p.keys[key]?.let { groups.getOrPut(it) { ArrayList() }.add(p) }
        return groups.values.map { Pair(it.first(), it.size) }
    }
}
