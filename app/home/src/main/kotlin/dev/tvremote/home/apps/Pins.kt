package dev.tvremote.home.apps

import android.annotation.TargetApi
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Bundle
import android.os.Process
import dev.tvremote.home.HomeConfig
import dev.tvremote.home.Store
import dev.tvremote.home.Target
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shortcuts other apps pin to the favourites, through Android's own request (ShortcutManager.requestPinShortcut, Android 8+):
 * a TV channel, a playlist, a page. This home knows none of those apps; the system keeps which shortcuts are pinned here
 * (asked again on every build); [order] is ours, outside HomeConfig: the phone and computer do not set it. They sit in the dock after its apps, under the same limit.
 */
object Pins {
    private fun la(c: Context) = c.getSystemService(LauncherApps::class.java)

    /** Only the default home may read or start pinned shortcuts (hasShortcutHostPermission); anything else sees none. */
    fun can(c: Context) = Build.VERSION.SDK_INT >= 26 && try { la(c)?.hasShortcutHostPermission() == true } catch (e: Exception) { false }

    fun key(s: ShortcutInfo) = "pin:${s.`package`}/${s.id}"

    private fun prefs(c: Context) = c.getSharedPreferences("pins", Context.MODE_PRIVATE)
    fun order(c: Context): List<String> = prefs(c).getString("order", "")!!.split('\n').filter { it.isNotEmpty() }
    fun order(c: Context, l: List<String>) { prefs(c).edit().putString("order", l.joinToString("\n")).apply() }

    /** The pinned shortcuts, as tiles' apps, in the order they were added; a disabled one (its app took it back) is left out. */
    fun list(c: Context): List<App> {
        if (!can(c)) return emptyList()
        val got = try {
            la(c)!!.getShortcuts(LauncherApps.ShortcutQuery().setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED), Process.myUserHandle())
        } catch (e: Exception) { null }.orEmpty().filter { it.isEnabled }
        val order = order(c)
        return got.sortedBy { order.indexOf(key(it)).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            .map { App(key(it), (it.shortLabel ?: it.longLabel ?: it.`package`).toString(), null, it) }
    }

    /** When the app last changed [a] (its icon, its name): a pin given a new icon is drawn again. */
    @TargetApi(26)
    fun stamp(a: App) = (a.pin as? ShortcutInfo)?.lastChangedTimestamp ?: 0L

    /** A focused tile's width in px ([resize]): a pin's picture is drawn at it, not at [PinArt.W]: ~370 px at 5 columns on a 1080p TV, a third of the memory. */
    @Volatile var fit = dev.tvremote.tvui.PinArt.W; private set

    /** The grid's tiles changed size: pins are drawn again at [px] when next asked (true: there were some to redraw). */
    fun resize(px: Int): Boolean {
        val n = px.coerceIn(64, dev.tvremote.tvui.PinArt.W)
        if (n == fit) return false
        fit = n; made.clear(); return true
    }

    @TargetApi(26)
    fun picture(c: Context, a: App) {
        val s = a.pin as ShortcutInfo
        val sharp = if (s.`package` in OURS) dev.tvremote.tvui.PinArt.read(c, s.`package`, s.id, fit) else null   // the system's copy is ~127 px: ours keep a larger one
        val d = sharp?.let { android.graphics.drawable.BitmapDrawable(c.resources, it) }
            ?: try { la(c)!!.getShortcutIconDrawable(s, c.resources.displayMetrics.densityDpi) } catch (e: Exception) { null }
            ?: try { c.packageManager.getApplicationIcon(s.`package`) } catch (e: Exception) { null }   // the system lost its bitmap ("Icon file not found"): the app's own icon, not the hidden apps' eye
        if (d != null && wide(d.intrinsicWidth, d.intrinsicHeight) && d.intrinsicWidth > 0) a.banner = badged(c, d, if (s.`package` == c.packageName) sourceOf(c, s.id) else s.`package`) else a.icon = d
    }

    private val OURS = setOf("dev.tvremote.games", "dev.tvremote.channels")

    /**
     * A wide pin with its app's icon in a corner, as on the system's own launchers: a game's or a channel's picture alone did not
     * say which app it opens. Drawn at [fit], the focused tile's size, so the icon stays sharp over a small card too.
     */
    private const val BADGE_BG = 0x66000000                        // under the icon: a see-through one still reads on a white card
    private const val BADGE_SHADOW = 0x99000000.toInt()

    private fun badged(c: Context, card: android.graphics.drawable.Drawable, source: String?): android.graphics.drawable.Drawable {
        val icon = source?.let { try { c.packageManager.getApplicationIcon(it) } catch (e: Exception) { null } } ?: return card
        val w = fit; val h = fit * dev.tvremote.tvui.PinArt.H / dev.tvremote.tvui.PinArt.W
        val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(b)
        card.setBounds(0, 0, w, h); card.draw(cv)
        val z = h * 22 / 100; val m = h * 6 / 100                    // ~1/5 of the height, in from the bottom-left corner the launcher rounds
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = BADGE_BG; setShadowLayer(z * 0.12f, 0f, z * 0.04f, BADGE_SHADOW) }
        cv.drawRoundRect(android.graphics.RectF(m.toFloat(), (h - m - z).toFloat(), (m + z).toFloat(), (h - m).toFloat()), z * 0.24f, z * 0.24f, p)
        cv.save(); cv.clipPath(android.graphics.Path().apply { addRoundRect(android.graphics.RectF(m.toFloat(), (h - m - z).toFloat(), (m + z).toFloat(), (h - m).toFloat()), z * 0.24f, z * 0.24f, android.graphics.Path.Direction.CW) })
        icon.setBounds(m, h - m - z, m + z, h - m); icon.draw(cv); cv.restore()
        return android.graphics.drawable.BitmapDrawable(c.resources, b)
    }

    @TargetApi(26)
    fun open(c: Context, a: App, o: android.os.Bundle? = null): Boolean {
        val s = a.pin as ShortcutInfo
        return try { la(c)!!.startShortcut(s.`package`, s.id, null, o, Process.myUserHandle()); true } catch (e: Exception) { false }
    }

    /** Unpins [a]: the system keeps a list per app, so the app's other pins are named again. */
    @TargetApi(26)
    fun remove(c: Context, a: App) {
        val s = a.pin as ShortcutInfo
        val keep = list(c).mapNotNull { it.pin as? ShortcutInfo }.filter { it.`package` == s.`package` && it.id != s.id }.map { it.id }
        try { la(c)!!.pinShortcuts(s.`package`, keep, Process.myUserHandle()) } catch (e: Exception) {}
        order(c, order(c) - key(s))
    }

    /** Pins [key] again (the system keeps a list per app: its other pins are named with it); false when the shortcut is gone. */
    @TargetApi(26)
    fun add(c: Context, key: String): Boolean {
        val s = find(c, key) ?: return false
        val keep = list(c).mapNotNull { it.pin as? ShortcutInfo }.filter { it.`package` == s.`package` }.map { it.id }
        return try { la(c)!!.pinShortcuts(s.`package`, (keep + s.id).distinct(), Process.myUserHandle()); true } catch (e: Exception) { false }
    }

    /**
     * [key] off the home screen for good: its system pin and TV Home's own tile ("tiles", dock, order). A shortcut can be both (put on
     * the grid by the picker, pinned by its app too): taking only the tile off showed the pin again, so "Remove" seemed to do nothing.
     */
    fun off(c: Context, key: String) {
        val s = find(c, key)
        if (s != null && list(c).any { it.pkg == key }) {
            val keep = list(c).mapNotNull { it.pin as? ShortcutInfo }.filter { it.`package` == s.`package` && it.id != s.id }.map { it.id }
            try { la(c)!!.pinShortcuts(s.`package`, keep, Process.myUserHandle()) } catch (e: Exception) {}
        }
        order(c, order(c) - key)
        fun without(k: String) = JSONArray(Store.list(c, k) - key)
        Store.change(c, JSONObject().put("tiles", without("tiles")).put("dock", without("dock")).put("order", without("order")))
    }

    /**
     * Turns an item of another app's Play Next row into a pin of ours: a shortcut TV Home itself owns ("pin:dev.tvremote.home/pn…"),
     * so it is the grid's ordinary pinned tile (moved, renamed, hidden, unpinned like any). [intent] is already the safe one of
     * Watch.intent (no selector, no URI grants). Android asks the default home to confirm ([PinActivity] takes it). [SAME]: pinned
     * already; [FAIL]: the system refused (or no shortcut host rights). No limit: a pin is one more tile of the grid.
     */
    enum class Made { OK, SAME, FAIL }

    @TargetApi(26)
    fun pinIntent(c: Context, source: String, label: String, intent: android.content.Intent, art: android.graphics.Bitmap?): Made {
        if (!can(c)) return Made.FAIL
        val id = "pn" + java.security.MessageDigest.getInstance("SHA-1").digest(intent.toUri(0).toByteArray()).joinToString("") { "%02x".format(it) }.take(14)
        if (list(c).any { it.pkg == Target.pin(c.packageName, id) }) return Made.SAME
        val sm = c.getSystemService(android.content.pm.ShortcutManager::class.java) ?: return Made.FAIL
        val icon = art ?: try {                                      // no poster: the source app's own icon, not this home's
            val d = c.packageManager.getApplicationIcon(source)
            android.graphics.Bitmap.createBitmap(192, 192, android.graphics.Bitmap.Config.ARGB_8888).also { b -> d.setBounds(0, 0, 192, 192); d.draw(android.graphics.Canvas(b)) }
        } catch (e: Exception) { null }
        val go = android.content.Intent(intent).apply { if (action == null) action = android.content.Intent.ACTION_VIEW }
        val info = android.content.pm.ShortcutInfo.Builder(c, id).setShortLabel(label.take(40)).setIntent(go)
            .also { b -> icon?.let { b.setIcon(android.graphics.drawable.Icon.createWithBitmap(it)) } }.build()
        c.getSharedPreferences(SRC, Context.MODE_PRIVATE).edit().putString(id, source).apply()   // whose it is: the shortcut itself is this home's
        return try { if (sm.requestPinShortcut(info, null)) Made.OK else Made.FAIL } catch (e: Exception) { Made.FAIL }
    }

    private const val SRC = "pin_src"
    /** The app a Play Next item pinned by [pinIntent] came from (its menu named TV Home); null for any other shortcut. */
    fun sourceOf(c: Context, id: String): String? = c.getSharedPreferences(SRC, Context.MODE_PRIVATE).getString(id, null)

    // ---- every shortcut an app offers (static, dynamic, pinned), for the picker and for a key / the grid that points at one ----

    private const val ALL = LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED

    /** One app's shortcuts. The query names a package: without one the system answers only the pinned ones. */
    @TargetApi(26)
    private fun of(c: Context, pkg: String, ids: List<String>? = null): List<ShortcutInfo> = try {
        la(c)!!.getShortcuts(LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(ALL).also { q -> if (ids != null) q.setShortcutIds(ids) }, Process.myUserHandle())
    } catch (e: Exception) { null }.orEmpty().filter { it.isEnabled }

    /** The shortcut [key] ("pin:pkg/id", a [Target]) points at; null when its app is gone or took it back, or this is not the home screen. */
    fun find(c: Context, key: String): ShortcutInfo? =
        if (Build.VERSION.SDK_INT < 26 || !can(c) || !Target.isPin(key)) null else of(c, Target.pkg(key), listOf(Target.id(key))).firstOrNull()

    fun start(c: Context, key: String, o: Bundle? = null): Boolean {
        val s = find(c, key) ?: return false
        return try { la(c)!!.startShortcut(s.`package`, s.id, null, o, Process.myUserHandle()); true } catch (e: Exception) { false }
    }

    private val made = HashMap<String, App>()                       // a grid tile's app per key: the same object while nothing changed, so its tile is kept

    /** [key] as a tile's app with its picture (shortcut + app label), or null when the shortcut is not there. */
    fun app(c: Context, key: String): App? {
        val s = find(c, key) ?: return null
        val label = (s.shortLabel ?: s.longLabel ?: s.id).toString()
        made[key]?.let { old -> if ((old.pin as ShortcutInfo).let { (it.shortLabel ?: it.longLabel ?: it.id).toString() } == label) return old }
        return App(key, label, null, s).also { it.load(c); made[key] = it }
    }

    /**
     * Every shortcut of the apps TV Home lists, as [{key, title, app}] in app order: what the picker offers. A stored copy ([report]
     * reads it), made when the settings are read or Home comes back: asking each app is a binder call, not for every [Store.get].
     */
    fun refresh(c: Context, apps: List<App> = Apps.list(c)) {
        val out = JSONArray()
        if (can(c)) for (a in apps) for (s in of(c, a.pkg).sortedBy { it.rank })
            out.put(JSONObject().put("key", key(s)).put("title", (s.shortLabel ?: s.longLabel ?: s.id).toString()).put("app", a.pkg))
        prefs(c).edit().putString("avail", out.toString()).putBoolean("can", can(c)).apply()
    }

    /** The config the clients read gains "pinsOk", "pinsAvail" [{key, title, app}] and "pinsCan" (only the home screen may start a shortcut). */
    fun report(c: Context, conf: JSONObject): JSONObject = conf.put("pinsOk", true).put("pinsCan", can(c))
        .put("pinsAvail", try { JSONArray(prefs(c).getString("avail", "[]")) } catch (e: Exception) { JSONArray() })
}
