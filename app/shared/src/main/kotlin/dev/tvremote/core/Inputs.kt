package dev.tvremote.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * The TV's real inputs (HDMI 1 / 2 / 3, AV, tuner...) with the names the TV gives them, and switching to one with a tap.
 *
 * Android TV / Google TV, over ADB: the inputs come from `dumpsys tv_input` (every TV input service the system knows, hardware
 * ones are named `<service>/HW<n>`, with TvInputHardwareInfo lines giving each one's type and HDMI port) and the devices behind
 * them from `dumpsys hdmi_control` (HDMI-CEC: "PlayStation 5" on port 1). Both are read in [READ] and understood by [parse], a pure
 * function: dumpsys is not a stable format, so [parse] takes what it can find and never throws. The switch is the Live TV app's
 * own link, `content://android.media.tv/passthrough/<input id>`; where no app takes it, the TV_INPUT_HDMI_n key codes (243..246).
 * LG: `ssap://tv/getExternalInputList` and `switchInput` ([parseLg], in [Lg]). Samsung only has its source key, so nothing here.
 */
object Inputs {
    /** [kind]: hdmi, av (composite), component, vga, tuner or other. [port]: the HDMI port number, else 0. [device]: the box behind it (CEC). */
    class Input(val id: String, val kind: String, val port: Int, val device: String, val label: String, val connected: Boolean? = null, val key: Int = 0) {
        fun json(): JSONObject = JSONObject().put("id", id).put("kind", kind).put("port", port).put("device", device).put("label", label)
            .apply { if (connected != null) put("connected", connected) }
    }

    fun json(list: List<Input>): JSONObject = JSONObject().put("inputs", JSONArray(list.map { it.json() }))

    /** The id that stands for the tuner (the TV's own channels): switching to it is the Live TV key. */
    const val TUNER = "tuner"

    private const val SEP = "#--tvr-inputs--"
    const val READ = "dumpsys tv_input 2>/dev/null; echo '$SEP'; dumpsys hdmi_control 2>/dev/null"

    // TvInputHardwareInfo types (hardware/interfaces tv.input) and TvInputInfo types (TvInputInfo.TYPE_*): the dump prints either
    private val TYPES = mapOf(2 to "tuner", 3 to "av", 6 to "component", 7 to "vga", 8 to "other", 9 to "hdmi", 10 to "other",
        1001 to "av", 1004 to "component", 1005 to "vga", 1006 to "other", 1007 to "hdmi", 1008 to "other", 0 to "tuner")

    private val ID = Regex("""TvInputInfo\{id=([^,}\s]+)""")
    private val HW = Regex("""(?:/|\bHW)(?:HW)?(\d+)$""")
    private val HW_INFO = Regex("""TvInputHardwareInfo\s*\{([^}]*)\}""")
    private val INFO_TYPE = Regex("""\btype=(\d+)""")
    private val INFO_ID = Regex("""\bid=(\d+)""")
    private val INFO_PORT = Regex("""hdmi_port(?:_id)?=(\d+)""")
    private val INFO_CABLE = Regex("""cable_connection_status=(\d+)""")

    private class Hw(val kind: String, val port: Int, val connected: Boolean?)

    /** What [READ] printed → the inputs in the order the TV lists them (HDMI by port, then the rest). Empty: this TV has none to switch (boxes, emulators). */
    fun parse(out: String): List<Input> {
        return try { parseUnsafe(out) } catch (e: Exception) { emptyList() }
    }

    private fun parseUnsafe(out: String): List<Input> {
        val at = out.indexOf(SEP)
        val tvInput = if (at < 0) out else out.substring(0, at)
        val cec = if (at < 0) "" else out.substring(at + SEP.length)

        // hardware ids ("HW5") → type and port
        val hw = HashMap<Int, Hw>()
        for (m in HW_INFO.findAll(tvInput)) {
            val body = m.groupValues[1]
            val id = INFO_ID.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val kind = TYPES[INFO_TYPE.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: continue] ?: "other"
            val port = INFO_PORT.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val cable = INFO_CABLE.find(body)?.groupValues?.get(1)?.toIntOrNull()
            hw[id] = Hw(kind, port, when (cable) { 1 -> true; 2 -> false; else -> null })   // CABLE_CONNECTION_STATUS_*: 0 unknown, 1 connected, 2 disconnected
        }
        val boxes = cecDevices(cec)

        val ids = LinkedHashSet<String>()
        for (m in ID.findAll(tvInput)) ids.add(m.groupValues[1])
        // the inputMap lines also name an input on its own ("com.x/.Y/HW5: info: ...") when the info prints in another shape
        for (line in tvInput.lineSequence()) {
            val k = line.trim().substringBefore(':').trim()
            if (k.contains('/') && !k.contains(' ') && HW.containsMatchIn(k)) ids.add(k)
        }

        data class Raw(val id: String, val kind: String, val port: Int, val connected: Boolean?)
        val raw = ArrayList<Raw>()
        var tuner = false
        for (id in ids) {
            val n = HW.find(id)?.groupValues?.get(1)?.toIntOrNull()
            val info = if (n != null) hw[n] else null
            val lower = id.lowercase()
            val kind = info?.kind ?: when {
                "hdmi" in lower -> "hdmi"
                "composite" in lower || lower.endsWith("av") || "/av" in lower -> "av"
                "component" in lower -> "component"
                "vga" in lower -> "vga"
                n != null -> "other"                                  // a hardware input (HW<n>) of a type the dump did not print
                else -> null
            }
            if (kind == null) {                                       // an app's input service (a channel app, a tuner): not a thing to switch to by id
                if (lower.contains("tuner") || lower.contains("dtv") || lower.contains("atsc") || lower.contains("dvb")) tuner = true
                continue
            }
            if (kind == "tuner") { tuner = true; continue }
            val port = info?.port?.takeIf { it > 0 } ?: Regex("hdmi_?(\\d)").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 0   // "Hdmi1InputService"
            raw.add(Raw(id, kind, port, info?.connected))
        }
        if (raw.isEmpty()) return emptyList()                         // a tuner alone is the Live TV key, which the remote already has

        // the same kind, in the order printed, are numbered 1, 2, 3 when the dump gave no port
        val seen = HashMap<String, Int>()
        val used = raw.filter { it.kind == "hdmi" && it.port > 0 }.map { it.port }.toMutableSet()
        val list = ArrayList<Input>()
        for (r in raw.sortedWith(compareBy({ it.kind != "hdmi" }, { it.port == 0 }, { it.port }))) {
            val nth = (seen[r.kind] ?: 0) + 1
            seen[r.kind] = nth
            var port = r.port
            if (r.kind == "hdmi" && port == 0) { port = generateSequence(nth) { it + 1 }.first { it !in used }; used.add(port) }
            val name = if (r.kind == "hdmi") boxes.forPort(port) else ""
            val base = when (r.kind) {
                "hdmi" -> "HDMI $port"
                "av" -> if (raw.count { it.kind == "av" } > 1) "AV $nth" else "AV"
                "component" -> if (raw.count { it.kind == "component" } > 1) "Component $nth" else "Component"
                "vga" -> "VGA"
                else -> "Input $nth"
            }
            list.add(Input(r.id, r.kind, if (r.kind == "hdmi") port else 0, name, if (name.isEmpty()) base else "$base · $name", r.connected, fallbackKey(r.kind, if (r.kind == "hdmi") port else nth)))
        }
        if (tuner) list.add(Input(TUNER, "tuner", 0, "", "TV", null, 170))
        return list
    }

    /** KEYCODE_TV_INPUT_HDMI_1..4 = 243..246, COMPOSITE_1..2 = 247..248, COMPONENT_1..2 = 249..250, VGA_1 = 251; 0 where there is none. */
    fun fallbackKey(kind: String, n: Int): Int = when (kind) {
        "hdmi" -> if (n in 1..4) 242 + n else 0
        "av" -> if (n in 1..2) 246 + n else 0
        "component" -> if (n in 1..2) 248 + n else 0
        "vga" -> 251
        else -> 0
    }

    // ------------------------------------------------------------ HDMI-CEC: the boxes behind the ports
    private class Box(val name: String, val port: Int, val physical: Int)
    private class Boxes(val list: List<Box>) {
        /** The box on [port]: the one plugged straight in (physical address N000) before one behind a receiver (N100...). */
        fun forPort(port: Int): String {
            val on = list.filter { it.port == port || it.physical > 0 && it.physical shr 12 == port }
            return (on.firstOrNull { it.physical and 0x0FFF == 0 } ?: on.firstOrNull())?.name ?: ""
        }
    }

    private val NAME = Regex("""display_?name[:=]\s*(.*?)(?=\s+(?:power_?status|physical_?address|port_?id|device_?type|vendor_?id|logical_?address|cec_?version|status|device_?power_?status|adopt)\b|\s*[,}]|\s*$)""", RegexOption.IGNORE_CASE)
    private val PHYS = Regex("""physical_?address[:=]\s*(?:0x)?([0-9a-fA-F]{4})""", RegexOption.IGNORE_CASE)
    private val PORT = Regex("""\bport_?id[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val LOGICAL = Regex("""logical_?address[:=]\s*(?:0x)?([0-9a-fA-F]{1,2})""", RegexOption.IGNORE_CASE)

    private fun cecDevices(text: String): Boxes {
        val list = ArrayList<Box>()
        val seen = HashSet<String>()
        for (line in text.lineSequence()) {
            if (!line.contains("display_name", true) && !line.contains("displayname", true)) continue
            val name = NAME.find(line)?.groupValues?.get(1)?.trim()?.trim('"', '\'') ?: continue
            if (name.isEmpty() || name.equals("null", true)) continue
            val logical = LOGICAL.find(line)?.groupValues?.get(1)?.toIntOrNull(16)
            if (logical == 0 || logical == 15) continue               // the TV itself / the broadcast address
            val phys = PHYS.find(line)?.groupValues?.get(1)?.toIntOrNull(16) ?: 0
            val port = PORT.find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            if (port == 0 && phys == 0) continue
            if (seen.add("$name/$phys/$port")) list.add(Box(name, port, phys))
        }
        return Boxes(list)
    }

    // ------------------------------------------------------------ LG
    /** `ssap://tv/getExternalInputList` → inputs. The id is LG's own ("HDMI_1"); a custom label or the connected device's model name is added. */
    fun parseLg(payload: JSONObject?): List<Input> {
        val devices = payload?.optJSONArray("devices") ?: return emptyList()
        val list = ArrayList<Input>()
        for (i in 0 until devices.length()) {
            val d = devices.optJSONObject(i) ?: continue
            val id = d.optString("id"); if (id.isEmpty()) continue
            val lower = id.lowercase()
            val kind = when { lower.startsWith("hdmi") -> "hdmi"; lower.startsWith("av") || lower.startsWith("comp") && !lower.startsWith("component") -> "av"; lower.startsWith("component") -> "component"; else -> "other" }
            val port = if (kind == "hdmi") d.optInt("port", Regex("(\\d+)").find(id)?.value?.toIntOrNull() ?: 0) else 0
            val base = if (kind == "hdmi") "HDMI $port" else d.optString("label").ifEmpty { id.replace('_', ' ') }
            val label = d.optString("label").trim()
            val model = d.optString("modelName").trim()
            val name = listOf(label, model).firstOrNull { it.isNotEmpty() && !it.replace(" ", "").equals(base.replace(" ", ""), true) && !it.replace(" ", "").equals(id.replace("_", ""), true) } ?: ""
            list.add(Input(id, kind, port, name, if (name.isEmpty()) base else "$base · $name", if (d.has("connected")) d.optBoolean("connected") else null))
        }
        return list
    }

    // ------------------------------------------------------------ switching (ADB)
    private val SAFE_ID = Regex("""[A-Za-z0-9_.:/$-]{1,200}""")

    fun valid(id: String) = SAFE_ID.matches(id)

    /** The link the Live TV app turns into "show this input": the id is one path segment, so its slashes are percent-encoded. */
    fun link(id: String): String = "content://android.media.tv/passthrough/" + id.replace("%", "%25").replace("/", "%2F").replace("$", "%24")

    /**
     * Switches to [id] over ADB. The Live TV link first; when no app takes it (or it errors), the input's key code from [known]
     * (the list, if it was read) or worked out from the id. Returns "intent" or "key"; throws when neither is possible.
     */
    fun switch(sh: (String) -> String, id: String, known: List<Input>? = null): String {
        if (!valid(id)) throw IllegalArgumentException("bad input")
        if (id == TUNER) { sh("input keyevent 170"); return "key" }
        val out = sh("am start -a android.intent.action.VIEW -d " + Adb.q(link(id)) + " 2>&1")
        if (!failed(out)) return "intent"
        val key = (known ?: parse(sh(READ))).firstOrNull { it.id == id }?.key ?: 0
        if (key == 0) throw IllegalArgumentException("this TV did not take the switch")
        sh("input keyevent $key")
        return "key"
    }

    internal fun failed(out: String): Boolean {
        val s = out.lowercase()
        return s.contains("error") || s.contains("exception") || s.contains("unable to resolve") || s.contains("no activity found")
    }

    /** A value kept for a minute, per TV (each [Manage] / [Lg] holds its own). Failures are not kept. */
    class Cache(private val ttlMs: Long = 60_000, private val now: () -> Long = System::currentTimeMillis) {
        private var at = 0L
        private var value: List<Input>? = null
        @Synchronized fun get(fresh: Boolean = false, load: () -> List<Input>): List<Input> {
            val v = value
            if (!fresh && v != null && now() - at < ttlMs) return v
            return load().also { value = it; at = now() }
        }
        /** The list as it was last read, however old; null before the first read. */
        @Synchronized fun peek(): List<Input>? = value
        @Synchronized fun clear() { value = null }
    }
}
