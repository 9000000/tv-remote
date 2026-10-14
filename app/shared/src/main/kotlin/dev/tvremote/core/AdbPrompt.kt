package dev.tvremote.core

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The TV's "Allow USB debugging?" question, answered from an ADB session that the TV already trusts (TV Setup's own, or
 * the phone's or computer's). Only ever for a device the person already trusts: one paired with TV Setup (its code is shown
 * on the TV, so someone was there) or one with ADB already. And only for the key the question shows: the answer is given
 * after its fingerprint matches the one asked for, so a stranger asking at the same moment is never let in.
 */
object AdbPrompt {
    /** What the question on screen shows, and where its parts are. */
    class Seen(val fingerprint: String, val nodes: List<Node>) {
        val always get() = nodes.firstOrNull { it.id.endsWith("alwaysUse") || it.cls.endsWith("CheckBox") }
        val allow get() = nodes.firstOrNull { it.id == "android:id/button1" }
        val cancel get() = nodes.firstOrNull { it.id == "android:id/button2" }
        val focused get() = nodes.firstOrNull { it.focused }
    }

    class Node(val id: String, val cls: String, val checked: Boolean, val focused: Boolean, val x: Int, val y: Int)

    private val FP = Regex("(?:[0-9A-F]{2}:){15}[0-9A-F]{2}")
    private const val DUMP = "/data/local/tmp/tvr-ui.xml"

    /** The question if it is on screen now, null otherwise. */
    @Throws(IOException::class)
    fun read(adb: Adb): Seen? {
        if (!adb.shell("dumpsys window | grep -E 'mCurrentFocus|mFocusedWindow'", 15000).contains("UsbDebuggingActivity")) return null
        for (attempt in 0..2) {                                       // uiautomator sometimes finds the screen busy: try again
            val xml = adb.shell("uiautomator dump $DUMP >/dev/null 2>&1; cat $DUMP; rm -f $DUMP", 30000)
            val nodes = ArrayList<Node>()
            var fp = ""
            for (m in Regex("<node ([^>]*)").findAll(xml)) {
                val a = Regex("([\\w-]+)=\"([^\"]*)\"").findAll(m.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
                FP.find(a["text"] ?: "")?.let { fp = it.value }
                val b = Regex("\\d+").findAll(a["bounds"] ?: "").map { it.value.toInt() }.toList()
                if (b.size == 4) nodes.add(Node(a["resource-id"] ?: "", a["class"] ?: "", a["checked"] == "true", a["focused"] == "true", (b[0] + b[2]) / 2, (b[1] + b[3]) / 2))
            }
            if (fp.isNotEmpty()) return Seen(fp, nodes)
            Thread.sleep(600)
        }
        return null
    }

    /**
     * Waits up to [waitMs] for the question about [fingerprint] and answers it with "Always allow". With [skipOthers], questions
     * about other keys that come first (often left over from connections long gone; the TV shows one at a time) are declined,
     * which never lets anyone in; their device just asks again. Returns allowed, none (no question about it came) or
     * other:<fingerprint> (another key's question is on screen and was left alone).
     */
    @Throws(IOException::class)
    fun allow(adb: Adb, fingerprint: String, waitMs: Long, skipOthers: Boolean): String {
        val want = fingerprint.uppercase()
        val end = System.currentTimeMillis() + waitMs
        var skipped = 0
        while (true) {
            val p = read(adb)
            if (p == null) {
                if (System.currentTimeMillis() > end) return "none"
                Thread.sleep(800)
                continue
            }
            if (p.fingerprint != want) {
                if (!skipOthers || skipped >= 6) return "other:" + p.fingerprint
                press(adb, p) { it.cancel }
                skipped++
                Thread.sleep(1200)
                continue
            }
            if (p.always?.checked == false) press(adb, p) { it.always }
            val now = read(adb)
            if (now != null && now.fingerprint == want && now.always?.checked == false) return "failed"   // never answer without "Always"
            press(adb, now ?: p) { it.allow }
            Thread.sleep(900)
            val after = read(adb)
            return if (after == null || after.fingerprint != want) "allowed" else "failed"
        }
    }

    /** Declines the question about [fingerprint] if it is the one on screen. */
    @Throws(IOException::class)
    fun deny(adb: Adb, fingerprint: String): Boolean {
        val p = read(adb) ?: return false
        if (p.fingerprint != fingerprint.uppercase()) return false
        press(adb, p) { it.cancel }
        return true
    }

    /** A tap where the part is; if the TV ignores taps, the arrow keys to it and OK. Then checks that something changed. */
    private fun press(adb: Adb, p: Seen, part: (Seen) -> Node?) {
        val n = part(p) ?: throw IOException("the question on the TV looks different than expected")
        adb.shell("input tap ${n.x} ${n.y}")
        Thread.sleep(500)
        val after = read(adb)
        if (after == null || after.fingerprint != p.fingerprint || changed(part(after), n)) return
        var s: Seen = after
        for (i in 0 until 8) {                                        // keys: move the focus to the part, then OK
            val t = part(s) ?: return
            val f = s.focused
            if (f != null && f.x == t.x && f.y == t.y) { adb.shell("input keyevent 23"); return }
            val key = when {
                f == null -> 20
                t.y > f.y + 10 -> 20
                t.y < f.y - 10 -> 19
                t.x > f.x -> 22
                else -> 21
            }
            adb.shell("input keyevent $key")
            s = read(adb) ?: return
        }
    }

    private fun changed(now: Node?, before: Node): Boolean = now == null || now.checked != before.checked

    // ---------------------------------------------------------------- asking TV Setup, from a device it has paired with

    /** A TV Setup pass for this device, handed out over ADB only (a broadcast only the shell may send). Null without TV Setup. */
    @Throws(IOException::class)
    fun token(adb: Adb): String? =
        Regex("data=\"([0-9a-f]{48})\"").find(adb.shell("am broadcast -n dev.tvremote.setup/.TrustReceiver 2>&1", 15000))?.groupValues?.get(1)

    /** Asks TV Setup on [host] to answer the TV's question about [fingerprint]. allowed, none, other:…, forgotten (pass no longer valid) or an error. */
    fun ask(host: String, token: String, fingerprint: String, waitS: Int): String {
        return try {
            val c = URL("http://$host:8765/api/adb/allow").openConnection() as HttpURLConnection
            c.connectTimeout = 4000
            c.readTimeout = (waitS + 30) * 1000
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Cookie", "tvr=$token")
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(JSONObject().put("fingerprint", fingerprint).put("wait", waitS).put("others", true).toString().toByteArray()) }
            when (c.responseCode) {
                200 -> JSONObject(c.inputStream.bufferedReader().readText()).optString("result")
                401, 403 -> "forgotten"
                else -> "error"
            }
        } catch (e: Exception) {
            "unreachable"
        }
    }
}
