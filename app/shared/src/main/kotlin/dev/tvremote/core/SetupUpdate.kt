package dev.tvremote.core

import dev.tvremote.core.Adb.Companion.q
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Updating TV Setup on the TV without leaving it broken. The update is done by a small script run by the TV's shell, apart
 * from every app (setsid), so it outlives TV Setup, which the install closes, and the device that started it:
 *  1. installs the new APK with --enable-rollback (Android 10+), which keeps a copy of the old app and its data;
 *  2. starts TV Setup and waits for it to say it runs well ([alive]: its service is up and its ADB works) and not crash;
 *  3. if it does not, puts the old version back with `pm rollback-app` (data and all: rules, paired devices, ADB key) and
 *     notes the version as bad, so nothing offers it again on this TV.
 * A plain downgrade is not possible on a TV (only debuggable builds allow `pm install -d`), and a remove + install would lose
 * the data and the ADB key, so without rollback (Android 9 and older) an update is only checked, not undone.
 * Its state is a line in [DIR]/state: "<word> <seconds> <version> <from> [detail]", word one of installing, checking, ok,
 * rolledback, broken (rollback impossible or failed), failed (the install itself failed: nothing changed).
 */
object SetupUpdate {
    const val PKG = RulesClient.SETUP
    const val DIR = "/data/local/tmp/tvr-update"
    const val APK = "$DIR/new.apk"                                     // where a phone or computer puts the APK it brings
    private val VERSION = Regex("[0-9]+(\\.[0-9]+)*")

    /** The version of TV Setup on the TV, "" when it has none. */
    fun installed(adb: Adb): String =
        Regex("versionName=(\\S+)").find(adb.shell("dumpsys package $PKG 2>/dev/null | grep -m1 versionName="))?.groupValues?.get(1) ?: ""

    /** {state, at (ms), version, from, detail, bad: [versions that failed here]}; state "" when no update ever ran. */
    fun state(adb: Adb): JSONObject = parse(adb.shell("cat $DIR/state 2>/dev/null; echo '|'; cat $DIR/bad 2>/dev/null"))

    fun parse(out: String): JSONObject {
        val parts = out.split("|", limit = 2)
        val w = parts[0].trim().split(Regex("\\s+"), limit = 5)
        val o = JSONObject().put("state", "").put("at", 0).put("version", "").put("from", "").put("detail", "")
        if (w.size >= 4) o.put("state", w[0]).put("at", (w[1].toLongOrNull() ?: 0) * 1000).put("version", w[2]).put("from", w[3]).put("detail", w.getOrElse(4) { "" })
        val bad = JSONArray()
        parts.getOrElse(1) { "" }.split(Regex("\\s+")).filter { it.matches(VERSION) }.distinct().forEach { bad.put(it) }
        return o.put("bad", bad)
    }

    fun busy(s: JSONObject): Boolean =                                 // a script that died with the TV leaves "installing" behind: stale after 10 minutes
        (s.optString("state") == "installing" || s.optString("state") == "checking") && System.currentTimeMillis() - s.optLong("at") < 600_000

    fun bad(s: JSONObject, version: String): Boolean {
        val b = s.optJSONArray("bad") ?: return false
        return (0 until b.length()).any { b.optString(it) == version }
    }

    /**
     * One update at a time, whoever starts it (the TV itself, a phone, a computer): taken before the APK is put in place, given
     * back by the script when it ends. mkdir is atomic; a lock older than 10 minutes is a script's that died with the TV.
     */
    fun lock(adb: Adb): Boolean = adb.shell("mkdir -p $DIR; if mkdir $DIR/lock 2>/dev/null || " +
        "[ $(( $(date +%s) - $(stat -c %Y $DIR/lock 2>/dev/null || date +%s) )) -gt 600 ]; then touch $DIR/lock; echo got; fi").contains("got")

    fun unlock(adb: Adb) { adb.shell("rmdir $DIR/lock 2>/dev/null") }

    /** Starts the script on [apk] (a file the shell can read) and returns at once; [state] follows it. [open]: TV Setup's screen after. */
    @Throws(IOException::class)
    fun start(adb: Adb, apk: String, version: String, open: Boolean = false, locked: Boolean = false) {
        require(version.matches(VERSION)) { "bad version" }
        if (!locked && !lock(adb)) throw IOException("an update of TV Setup is already running")
        try { launch(adb, apk, version, open) } catch (e: Exception) { unlock(adb); throw e }
    }

    private fun launch(adb: Adb, apk: String, version: String, open: Boolean) {
        adb.shell("mkdir -p $DIR; rm -f $DIR/run.sh")              // not an old one of the same size: see below
        val body = SCRIPT.toByteArray(Charsets.UTF_8)
        adb.put("$DIR/run.sh", body)
        // cat finishes writing a moment after the stream closes, a slow TV later still: the script runs only once it is whole
        val whole = "[ \"$(stat -c %s $DIR/run.sh 2>/dev/null)\" = ${body.size} ]"
        for (i in 0 until 50) {
            if (adb.shell("$whole && echo whole").contains("whole")) break
            Thread.sleep(100)
        }
        // adbd ends the whole process group when this command's stream closes, which can be before the script has left it
        // (setsid): from TV Setup itself, on the same device, at once. So this waits for the script to say it is on its own.
        val run = "rm -f $DIR/pid; setsid sh $DIR/run.sh ${q(apk)} $version ${if (open) "open" else "-"} </dev/null >$DIR/log 2>&1 & " +
            "for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do [ -s $DIR/pid ] && break; sleep 0.2; done; [ -s $DIR/pid ] && echo started"
        val out = adb.shell("$whole && { $run; }")
        if (!out.contains("started")) throw IOException("could not write the update script on the TV (is its storage full?)")
    }

    /** TV Setup, once it runs well after an update: the script waiting for this keeps the new version. */
    fun alive(adb: Adb, version: String) {                            // "installing" too: Android may start it before the script says "checking"
        if (version.matches(VERSION)) adb.shell("grep -qE '^(installing|checking) [0-9]+ $version ' $DIR/state 2>/dev/null && echo $version > $DIR/ok")
    }

    // $1 the APK, $2 its version, $3 "open" to show TV Setup's screen when done (an update started there). Each step says where it is in $D/state, so any device can follow it, and a person can read the log.
    private val SCRIPT = """
        D=$DIR; P=$PKG; A=${'$'}1; V=${'$'}2; O=${'$'}3; echo ${'$'}${'$'} > ${'$'}D/pid   # on its own now: see start()
        trap 'rmdir ${'$'}D/lock 2>/dev/null' EXIT                    # the next update may start
        svc() { am start-foreground-service -n ${'$'}P/.RemoteService >/dev/null 2>&1 || am startservice -n ${'$'}P/.RemoteService >/dev/null 2>&1; }   # Android 7 and older: startservice
        say() { echo "${'$'}1 ${'$'}(date +%s) ${'$'}V ${'$'}{old:--} ${'$'}2" > ${'$'}D/state; }
        ver() { dumpsys package ${'$'}P 2>/dev/null | grep -m1 versionName= | sed 's/.*versionName=//'; }
        crashes() { logcat -b crash -d 2>/dev/null | grep -c "Process: ${'$'}P,"; }
        old=${'$'}(ver); rm -f ${'$'}D/ok; say installing
        size=${'$'}(stat -c %s "${'$'}A"); c0=${'$'}(crashes); rb=1
        out=${'$'}(cat "${'$'}A" | pm install -r --enable-rollback -S ${'$'}size 2>&1)
        case "${'$'}out" in *Success*) ;; *) rb=0; out=${'$'}(cat "${'$'}A" | pm install -r -S ${'$'}size 2>&1) ;; esac   # Android 9: no rollback
        [ "${'$'}A" = ${'$'}D/new.apk ] && rm -f "${'$'}A"
        case "${'$'}out" in *Success*) ;; *) say failed "${'$'}(echo ${'$'}out | tr -s ' \n' ' ' | cut -c1-200)"; exit 0 ;; esac
        echo "installed ${'$'}V (rollback ${'$'}rb)"; say checking
        svc
        good=0; i=0
        while [ ${'$'}i -lt 90 ]; do                                    # up to 90 s for it to say it runs, fewer if it keeps crashing
          sleep 1; i=${'$'}((i + 1))
          [ "${'$'}(cat ${'$'}D/ok 2>/dev/null)" = "${'$'}V" ] && { good=1; break; }
          [ ${'$'}((${'$'}(crashes) - c0)) -ge 2 ] && break
          [ ${'$'}((i % 20)) = 0 ] && svc
        done
        [ ${'$'}good = 1 ] && sleep 20                                  # and still running a little later
        show() { [ "${'$'}O" = open ] && am start -n ${'$'}P/.MainActivity >/dev/null 2>&1; }
        if [ ${'$'}good = 1 ] && [ "${'$'}(crashes)" = "${'$'}c0" ]; then rm -f ${'$'}D/ok; say ok; show; exit 0; fi
        echo "${'$'}V did not start well (ok=${'$'}good, crashes ${'$'}c0 -> ${'$'}(crashes))"; echo ${'$'}V >> ${'$'}D/bad
        if [ ${'$'}rb = 1 ] && [ "${'$'}old" != "" ]; then
          for t in 1 2 3 4 5; do pm rollback-app ${'$'}P 2>&1; [ "${'$'}(ver)" = "${'$'}old" ] && break; sleep 3; done
        fi
        if [ "${'$'}old" != "" ] && [ "${'$'}(ver)" = "${'$'}old" ]; then
          svc; say rolledback; show
        else say broken; fi
        rm -f ${'$'}D/ok
    """.trimIndent() + "\n"
}
