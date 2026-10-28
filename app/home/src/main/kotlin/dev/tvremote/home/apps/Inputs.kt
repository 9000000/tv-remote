package dev.tvremote.home.apps

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import dev.tvremote.home.R
import dev.tvremote.home.ui.Drop

/**
 * The TV's inputs as the TV shows them (cleanyourtv's launcher reads them the same way): the names the owner gave them, the
 * hidden ones left out, a device on HDMI-CEC folded into its port, "Connected" where the TV knows, HDMI first, then AV and
 * the like, the tuner last. One reading for the inputs menu and for the Control Center's tile (its state, and whether it shows).
 * [onFail]: an input that would not open.
 */
object Inputs {
    fun rows(a: Activity, fold: Boolean = false, onFail: () -> Unit): List<Drop.Row> {
        val tim = try { a.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager } catch (e: Exception) { null }
        val list: List<TvInputInfo> = try { tim?.tvInputList.orEmpty() } catch (e: Exception) { emptyList() }
        fun hidden(i: TvInputInfo) = try { i.isHidden(a) } catch (e: Exception) { false }
        fun custom(i: TvInputInfo) = try { i.loadCustomLabel(a)?.toString()?.trim().orEmpty() } catch (e: Exception) { "" }
        fun state(id: String) = try { tim?.getInputState(id) ?: -1 } catch (e: Exception) { -1 }
        val ids = list.map { it.id }.toSet()
        val top = list.filter { (it.parentId == null || it.parentId !in ids) && !hidden(it) }
        val devices = list.filter { it.isPassthroughInput && it.parentId in ids && !hidden(it) }.groupBy { it.parentId }
        val ports = top.filter { it.isPassthroughInput && it.type == TvInputInfo.TYPE_HDMI }
        // some TVs say every HDMI port is connected: then it means nothing
        val bogus = ports.size > 1 && ports.all { state(it.id) == TvInputManager.INPUT_STATE_CONNECTED }
        val rows = top.map { i ->
            val hdmi = i.isPassthroughInput && i.type == TvInputInfo.TYPE_HDMI
            val base = name(custom(i), i.loadLabel(a).toString(), i.id)
            val dev = devices[i.id]?.firstNotNullOfOrNull { d -> name(custom(d), d.loadLabel(a).toString(), d.id).takeIf { it != base } }
            val connected = !(hdmi && bogus) && (state(i.id) == TvInputManager.INPUT_STATE_CONNECTED || devices[i.id].orEmpty().any { state(it.id) == TvInputManager.INPUT_STATE_CONNECTED })
            val group = if (hdmi) 0 else if (i.isPassthroughInput) 1 else 2
            val folds = fold && foldable(group, state(i.id), bogus)
            Drop.Row(if (group == 2) R.drawable.ic_tv else if (hdmi) R.drawable.ic_input else R.drawable.ic_plug,
                if (dev != null) "$base · $dev" else base, if (connected && group < 2) a.getString(R.string.connected) else null, group, folds = folds && !devices[i.id].orEmpty().any { state(it.id) == TvInputManager.INPUT_STATE_CONNECTED }) {
                val uri = if (i.isPassthroughInput) TvContract.buildChannelUriForPassthroughInput(i.id) else TvContract.buildChannelsUriForInput(i.id)
                try { a.startActivity(InputOpen.intent(a, uri)) } catch (e: Exception) { if (!xgimi(a, uri)) onFail() }
            }
        }.sortedWith(compareBy<Drop.Row> { it.group }.thenBy(Nat) { it.label })
        return if (rows.isEmpty()) coocaa(a, onFail) else if (fold) foldRows(rows, a.getString(R.string.inputs_more, rows.count { it.folds })) else rows
    }

    /**
     * The "fold empty ports" option: [group] 0 = HDMI, 1 = other passthrough (AV, component: no hotplug, so the TV cannot say), 2 = tuner and
     * the like. A jack always folds; an HDMI port folds only when the TV says it is DISCONNECTED and its answers can be trusted
     * ([bogus] = every port "connected": it means nothing); STANDBY is a device that is there. Pure.
     */
    fun foldable(group: Int, state: Int, bogus: Boolean): Boolean = when (group) {
        1 -> true
        0 -> !bogus && state == TvInputManager.INPUT_STATE_DISCONNECTED
        else -> false
    }

    /** [rows] with the folded ones moved behind one "More inputs" row (which expands in place); nothing is folded when it would leave nothing to show. Pure. */
    fun foldRows(rows: List<Drop.Row>, moreLabel: String): List<Drop.Row> {
        val keep = rows.filter { !it.folds }; val away = rows.filter { it.folds }
        if (away.isEmpty() || keep.isEmpty()) return rows
        return keep + Drop.Row(R.drawable.ic_more, moreLabel, null, (keep.last().group + 1), more = away)
    }

    /**
     * An input's name as a person reads it. Philips (TP Vision) saves "Xbox&GAME_CONSOLE" (the type picks an icon in its own menu) and an
     * unnamed port as the text "null": the part before the "&" stays when what follows is a type word (3+ capitals), blank or "null" is
     * nothing. Pure. Idea from cleanyourtv's InputNames.java (MIT).
     */
    fun tidy(raw: CharSequence?): String? {
        val s = raw?.toString()?.trim().orEmpty()
        if (s.isEmpty() || s == "null") return null
        val amp = s.lastIndexOf('&')
        if (amp < 0 || !Regex("[A-Z][A-Z0-9_]{2,}").matches(s.substring(amp + 1))) return s   // "Tom & Jerry" stays
        return s.substring(0, amp).trim().ifEmpty { null }
    }

    /** The custom name, else the port's own label, else the end of its id; each cleaned by [tidy]. Pure. */
    fun name(custom: String, label: String, id: String): String = tidy(custom) ?: tidy(label) ?: id.substringAfterLast('/').trimStart('.')

    /** XGIMI projectors answer no VIEW for an input, only their own HDMI player (cleanyourtv, MIT); only when that app resolves. */
    private fun xgimi(a: Context, uri: Uri): Boolean = try {
        val i = Intent("com.xgimi.action.hdmiPlayer").setDataAndType(uri, "vnd.android.cursor.item/channel").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i.resolveActivity(a.packageManager) == null) false else { a.startActivity(i); true }
    } catch (e: Exception) { false }

    /**
     * TCL's own source panel, for sets that list no input: a service of its settings app that shows the panel (cleanyourtv's examples for
     * TCL China, MIT). Only a service that resolves is started. Cannot be tried without such a TV.
     */
    fun panel(a: Context): Boolean {
        val tries = listOf("com.tcl.settings" to "com.tcl.settings.ShowWindowService", "com.tcl.sourcemananger" to "com.tcl.insrc.ShowWindowService")
        for ((pkg, cls) in tries) try {
            val i = Intent("com.tcl.insrc.SHOW_WINDOW").setClassName(pkg, cls)
            if (a.packageManager.resolveService(i, 0) == null) continue
            a.startService(i); return true
        } catch (e: Exception) { }
        return false
    }

    /** Live TV apps that stand for a TV with a tuner or inputs of its own (cleanyourtv's list, MIT: github.com/cleanyourtv). */
    val LIVE_APPS = listOf("com.tcl.tv", "com.sony.dtv.tvx", "com.mediatek.wwtv.tvcenter", "com.mitv.livetv", "com.realtek.tv", "com.cltv.hybrid", "com.android.tv")

    fun liveApp(a: Context): String? = LIVE_APPS.firstOrNull { p -> try { a.packageManager.getPackageInfo(p, 0); true } catch (e: Exception) { false } }

    /** Whether the Control Center shows its Inputs tile: any input listed, or a live TV app; a box (playback device) with neither has none; an unknown TV keeps it (its settings open). Pure. */
    fun showTile(listed: Int, liveApp: Boolean, box: Boolean): Boolean = listed > 0 || liveApp || !box

    /**
     * Coocaa OS (Skyworth) TVs register nothing with TvInputManager: com.skyworth.tv lists the sources its own panel shows and switches to
     * one. Ported from cleanyourtv's launcher (MIT, CoocaaSources.java). Empty where the provider is missing.
     */
    private fun coocaa(a: Context, onFail: () -> Unit): List<Drop.Row> {
        val out = ArrayList<Triple<String, Int, String>>()
        try {
            a.contentResolver.query(Uri.parse("content://com.skyworth.tv.provider/system/source_list"), arrayOf("sourceName", "sourceIndex", "displayName"), null, null, null)?.use { c ->
                while (c.moveToNext()) { val n = c.getString(0) ?: continue; out.add(Triple(n, c.getInt(1), c.getString(2)?.takeIf { it.isNotEmpty() } ?: n)) }
            }
        } catch (e: Exception) { return emptyList() }
        fun rank(n: String) = when (n) { "HDMI" -> 0; "AV" -> 1; "VOD" -> 3; else -> 2 }
        return out.sortedWith(compareBy<Triple<String, Int, String>>({ rank(it.first) }, { it.second })).map { (name, index, label) ->
            Drop.Row(if (name == "HDMI") R.drawable.ic_input else if (name == "VOD") R.drawable.ic_tv else R.drawable.ic_plug, label, null, rank(name).coerceAtMost(2)) {
                val ok = try { a.contentResolver.call(Uri.parse("content://com.skyworth.tv.sdk.provider/method"), "METHOD_GOTO_SOURCE", null, android.os.Bundle().apply { putString("name", name); putInt("index", index) })?.getBoolean("ret") == true } catch (e: Exception) { false }
                if (!ok) onFail()
            }
        }
    }

    /** Whether this is a box (playback device, ro.hdmi.device_type has 4 and not 0) rather than a TV: its inputs are then not the TV's to list. */
    fun isBox(): Boolean = try {
        val v = try { Class.forName("android.os.SystemProperties").getMethod("get", String::class.java, String::class.java).invoke(null, "ro.hdmi.device_type", "") as String }
            catch (e: Exception) { Runtime.getRuntime().exec(arrayOf("getprop", "ro.hdmi.device_type")).inputStream.bufferedReader().readText() }   // hidden API blocked: the shell's own read
        val t = v.split(',').map { it.trim() }
        "4" in t && "0" !in t
    } catch (e: Exception) { false }

    private object Nat : Comparator<String> {
        override fun compare(a: String, b: String): Int {
            val n = Regex("\\d+|\\D+")
            val x = n.findAll(a.lowercase()).map { it.value }.toList(); val y = n.findAll(b.lowercase()).map { it.value }.toList()
            for (k in 0 until minOf(x.size, y.size)) {
                val c = if (x[k][0].isDigit() && y[k][0].isDigit()) (x[k].toBigInteger()).compareTo(y[k].toBigInteger()) else x[k].compareTo(y[k])
                if (c != 0) return c
            }
            return x.size - y.size
        }
    }
}
