package dev.tvremote.core.scenes
import org.json.JSONArray
import org.json.JSONObject

/**
 * The remote's keys and menu entries (app/shared/web/remote.json), which the Phone app's native remote and the web remote
 * (js/remote_spec.js) both draw from: add, reorder or rename an entry there and both change. [parse] reads it for one side:
 * [core] is "phone" for the native remote, "web" for a page; that side's own text (the entry's "phone" / "web" fields) lies over the
 * common one, and entries other cores show are left out.
 */
object RemoteSpec {
    /**
     * One key or menu entry. [key]: a name from [Scenes.KEYS]; [action]: what the side's handler map does (with [arg]); [expand] stands
     * in for a list the handler supplies; [needs]: what the TV must be able to do ([Remote.Kind.caps]), else greyed or left out.
     */
    class Entry(
        val id: String, val icon: String, val vi: String, val en: String, val subVi: String, val subEn: String,
        val key: String?, val action: String?, val arg: String?, val whenCond: String?, val expand: String?, val style: String?,
        val needs: List<String> = emptyList(),
    ) {
        fun can(caps: Set<String>) = needs.all { it in caps }
        fun words(vietnamese: Boolean) = if (vietnamese) vi else en
        fun sub(vietnamese: Boolean) = if (vietnamese) subVi else subEn
    }

    class Spec(val rows: Map<String, List<List<Entry>>>, val menus: Map<String, List<Entry>>)

    fun parse(json: String, core: String): Spec {
        val root = JSONObject(json)
        val rows = LinkedHashMap<String, List<List<Entry>>>()
        root.optJSONObject("rows")?.let { r ->
            for (name in r.keys()) rows[name] = r.getJSONArray(name).let { a -> (0 until a.length()).map { i -> entries(a.getJSONArray(i), core) } }
        }
        val menus = LinkedHashMap<String, List<Entry>>()
        root.optJSONObject("menus")?.let { m -> for (name in m.keys()) menus[name] = entries(m.getJSONArray(name), core) }
        return Spec(rows, menus)
    }

    private fun entries(a: JSONArray, core: String): List<Entry> = (0 until a.length()).mapNotNull { entry(a.getJSONObject(it), core) }

    private fun entry(e: JSONObject, core: String): Entry? {
        e.optJSONArray("cores")?.let { c -> if ((0 until c.length()).none { c.getString(it) == core }) return null }
        val over = e.optJSONObject(core) ?: JSONObject()
        val sub = over.optJSONObject("sub") ?: e.optJSONObject("sub")
        fun text(name: String) = over.optString(name, e.optString(name))
        return Entry(
            e.getString("id"), e.optString("icon"), text("vi"), text("en"), sub?.optString("vi") ?: "", sub?.optString("en") ?: "",
            e.optString("key").ifEmpty { null }, e.optString("do").ifEmpty { null }, e.optString("arg").ifEmpty { null },
            e.optString("when").ifEmpty { null }, e.optString("expand").ifEmpty { null }, e.optString("style").ifEmpty { null },
            e.optJSONArray("needs")?.let { n -> (0 until n.length()).map { n.getString(it) } } ?: emptyList(),
        )
    }
}
