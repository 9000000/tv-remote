package dev.tvremote.core.ontv

import org.json.JSONArray
import org.json.JSONObject

/**
 * The TV's open apps, as a phone's recents screen shows them: most TVs have no such screen (Google TV, Android TV 10+), but the
 * system keeps the list. Read from `dumpsys activity recents`: Android 10+ prints `Task{… #186 type=standard A=10130:pkg}`,
 * 9 and older `TaskRecord{… #45 A=pkg U=0 StackId=0 sz=1}`. Home screens and the system's own (recents, dream, assistant) are
 * left out, and so is Android 9's task of a closed app (no screen left in it); a package with several tasks shows once, its newest. No pictures: a task's snapshot is readable only as root.
 */
object Recents {
    const val CMD = "dumpsys activity recents"
    private val HEAD = Regex("""\* Recent #\d+: Task(?:Record)?\{\S+ #(\d+)([^}]*)\}""")
    private val TYPE = Regex("""\btype=(\w+)""")
    private val AFF = Regex("""\b[AI]=(?:\d+:)?([A-Za-z][\w.]*)""")
    private val COMP = Regex("""^\s*(?:mActivityComponent|realActivity)=([A-Za-z][\w.]*)/""")
    private val AGO = Regex("""lastActiveTime=\d+ \(inactive for (\d+)s\)""")
    private val ROOT = Regex("""\brootTaskId=(\d+)""")
    private val EMPTY = Regex("""\bStackId=-1 sz=0\b""")

    class Task(val id: Int, val pkg: String, val ago: Long, val running: Boolean?, val root: Boolean, val home: Boolean)

    fun parse(out: String): List<Task> {
        val all = ArrayList<Task>()
        val blocks = out.split(Regex("(?=\\n\\s*\\* Recent #)"))
        for (b in blocks) {
            val h = HEAD.find(b) ?: continue
            val id = h.groupValues[1].toInt()
            val rest = h.groupValues[2]
            val type = TYPE.find(rest)?.groupValues?.get(1)
            val pkg = b.lineSequence().mapNotNull { COMP.find(it)?.groupValues?.get(1) }.firstOrNull()
                ?: AFF.find(rest)?.groupValues?.get(1) ?: continue
            val home = type == "home" || b.contains("android.intent.category.HOME")
            val system = type != null && type != "standard" && type != "undefined" && type != "home"   // recents, dream, assistant
            if (system || EMPTY.containsMatchIn(rest)) continue        // Android 9 keeps a closed app's task with no screen in it (a BRAVIA): not open
            val running = when {
                b.contains("mRootProcess=null") -> false
                b.contains("mRootProcess=ProcessRecord") -> true
                else -> null                                          // Android 9 does not say
            }
            val root = ROOT.find(b)?.groupValues?.get(1)?.toInt() == id   // a task of its own: `am stack remove` takes it alone
            all += Task(id, pkg, AGO.find(b)?.groupValues?.get(1)?.toLong() ?: 0, running, root, home)
        }
        return all
    }

    /** {apps: [{task, pkg, ago, running, top}]}: top is the app on screen now (the first task, unless it is a home screen). */
    fun json(out: String): JSONObject {
        val tasks = parse(out)
        val top = tasks.firstOrNull()?.takeIf { !it.home }?.pkg
        val seen = HashSet<String>()
        val apps = JSONArray()
        for (t in tasks) {
            if (t.home || !seen.add(t.pkg)) continue
            apps.put(JSONObject().put("task", t.id).put("pkg", t.pkg).put("ago", t.ago).put("top", t.pkg == top)
                .apply { if (t.running != null) put("running", t.running) })
        }
        return JSONObject().put("apps", apps)
    }

    /**
     * Closes [pkg]: its tasks leave the list (`am stack remove`, Android 10+ where a task is its own root; on older ones the
     * number is a stack's, which can hold the home screen, so only the app is stopped) and the app stops unless [keep].
     */
    fun close(sh: (String) -> String, pkg: String, keep: Boolean) {
        for (t in parse(sh(CMD))) if (t.pkg == pkg && t.root && !t.home) sh("am stack remove ${t.id}")
        if (!keep) sh("am force-stop $pkg")
    }
}
