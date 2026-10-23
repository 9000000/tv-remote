package dev.tvremote.setup

import org.json.JSONObject

/**
 * "One heavy app" mode, for TVs with 1 to 1.5 GB of RAM: a video app left behind for another one is closed after a short wait,
 * so the next one does not start with the memory full (and the TV does not freeze or kill TV Setup). Off until a remote turns it
 * on. The pure part lives here, so it is tested without a TV: which apps are heavy, which fronts do not count as "another app",
 * the [Tracker] that says what has been left long enough, and [verdict], the last look before `am force-stop`.
 * [HeavyMode] is the part that touches the TV.
 */
internal object Heavy {
    const val WAIT_MS = 15_000L                                       // long enough to come back from a key press or a settings glance
    const val RETRY_MS = 60_000L                                      // sound still playing: look again in a minute, not every 3 s
    const val LOG_MAX = 20
    const val LOW_MB = 1536                                           // below it the page suggests the mode (never turns it on)

    /** The video and streaming apps worth closing; a name that is not installed costs nothing. Label by package for the page. */
    val LIST: Map<String, String> = linkedMapOf(
        "com.google.android.youtube.tv" to "YouTube",
        "com.google.android.youtube.tvkids" to "YouTube Kids",
        "com.google.android.youtube" to "YouTube",
        "com.netflix.ninja" to "Netflix",
        "com.fptplay.tv" to "FPT Play",
        "com.amazon.amazonvideo.livingroom" to "Prime Video",
        "com.disney.disneyplus" to "Disney+",
        "com.apple.atve.androidtv.appletv" to "Apple TV",
        "com.wbd.stream" to "Max",
        "com.hbo.hbonow" to "HBO",
        "com.viettel.tv360" to "TV360",
        "com.vieon.tv" to "VieON",
        "vn.vnpt.mytv" to "MyTV",
        "com.vtvcab.onplus" to "ON Plus",
        "tv.twitch.android.app" to "Twitch",
        "com.plexapp.android" to "Plex",
        "org.videolan.vlc" to "VLC",
    )

    /** Apps people listen to with the screen off: never closed, even if one of them were put on [LIST] one day. */
    val MUSIC = setOf(
        "com.spotify.tv.android", "com.spotify.music", "com.google.android.apps.youtube.music", "com.google.android.youtube.tvmusic",
        "com.zing.mp3", "ht.nct", "com.amazon.mp3", "com.apple.android.music", "deezer.android.tv", "deezer.android.app",
        "com.soundcloud.android", "com.google.android.apps.podcasts", "com.bambuna.podcastaddict", "au.com.shiftyjelly.pocketcasts",
        "fm.castbox.audiobook.radio.podcast", "com.audible.application", "com.aspiro.tidal", "org.xbmc.kodi",
    )

    /** What the page shows for [LIST]'s entries that are installed: ordered as the list. */
    fun label(pkg: String): String? = LIST[pkg]

    /** On for these rules. */
    fun on(o: JSONObject): Boolean = o.optJSONObject("heavy")?.optBoolean("on") == true

    /** The apps the person keeps: [pkg] strings, only well-formed names. */
    fun keep(o: JSONObject): Set<String> = o.optJSONObject("heavy")?.optJSONArray("keep")
        ?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }.toSet() } ?: emptySet()

    /** Closable: on the list, not a music app, not kept. */
    fun isHeavy(pkg: String, keep: Set<String>): Boolean = pkg in LIST && pkg !in MUSIC && pkg !in keep

    /**
     * A front that is not "another app": the home screen, the keyboard, a settings panel, the screensaver, voice search, a
     * permission or install dialog, TV Setup's own screens. Going there from a heavy app starts no wait (and ends none).
     * [homes]: the launchers' packages as the TV answers (HOME intent), [ime]: the enabled keyboards.
     */
    fun transient(pkg: String, homes: Set<String>, ime: Set<String>): Boolean =
        pkg.isEmpty() || pkg in homes || pkg in ime || pkg == "android" || pkg == "dev.tvremote.setup" || pkg == "dev.tvremote.home" ||
            TRANSIENT.matches(pkg)

    private val TRANSIENT = Regex(
        "com\\.android\\.(systemui|settings|tv\\.settings|dreams\\..*|permissioncontroller|packageinstaller|inputmethod\\..*|tv\\.provision)" +
            "|com\\.google\\.android\\.(tvsettings|permissioncontroller|packageinstaller|katniss|googlequicksearchbox|tvlauncher" +
            "|apps\\.tv\\.launcherx|apps\\.tv\\.dreamx|backdrop|inputmethod\\..*|leanbacklauncher.*|tv\\.remote\\.service|apps\\.tv\\.setup.*)" +
            "|.*\\.(dream|dreams|screensaver|daydream)(\\..*)?|com\\.droidlogic\\.(tv)?settings.*")

    enum class Verdict { STOP, NEVER, LATER }

    /**
     * The last look before closing [pkg], with what the TV says now. NEVER: leave it (and forget it until it is on screen again);
     * LATER: it is making sound, ask again; STOP: close it. [safe]: apps that are never closed whatever the list says (launchers,
     * keyboards, accessibility services, TV Setup, persistent ones). [playing]: packages with a player started or a media session
     * playing; [fgMedia]: packages with a foreground service of type mediaPlayback.
     */
    fun verdict(pkg: String, front: String, keep: Set<String>, mustKeep: (String) -> Boolean, safe: Set<String>,
                playing: Set<String>, fgMedia: Set<String>): Verdict = when {
        pkg == front -> Verdict.NEVER                                 // it is what is on screen: no longer "left"
        !isHeavy(pkg, keep) || mustKeep(pkg) || pkg in safe -> Verdict.NEVER
        pkg in playing || pkg in fgMedia -> Verdict.LATER
        else -> Verdict.STOP
    }

    /**
     * Follows the heavy apps that were on screen and says which have been left for another app for [wait] ms. Fed one reading of
     * the foreground app at a time ([look]); the reading may be late by a few seconds, so [wait] is a minimum.
     *  - heavy A to a real app or another heavy one: A's wait begins; back to A before it ends: nothing happens
     *  - heavy A to a [Heavy.transient] front (home, keyboard, dream...): nothing begins; A to home to B: A's wait begins at B
     *  - screen off: all waits are dropped (they begin afresh when it is on and an app is in front)
     * Once an app was reported it is [done] (closed, or never to be) or [later] (asked again after [RETRY_MS]): once per switch.
     */
    class Tracker(private val wait: Long = WAIT_MS) {
        private val seen = LinkedHashSet<String>()                    // heavy apps that have been in front and not been closed since
        private val away = LinkedHashMap<String, Long>()              // of those, the ones left for a real app: when the wait is up

        /** [due]: the packages whose wait is over, in the order they were left. */
        fun look(front: String, now: Long, on: Boolean, isHeavy: (String) -> Boolean, isTransient: (String) -> Boolean): List<String> {
            if (!on) { away.clear(); return emptyList() }
            if (front.isEmpty()) return due(now, front)
            if (isHeavy(front)) { seen.add(front); away.remove(front); leave(now, front) }
            else if (!isTransient(front)) leave(now, front)
            return due(now, front)
        }

        private fun leave(now: Long, except: String) { for (a in seen) if (a != except && a !in away) away[a] = now + wait }

        private fun due(now: Long, front: String): List<String> = away.filter { it.value <= now && it.key != front }.keys.toList()

        /** Closed, or never to be closed: forgotten until it is in front again. */
        fun done(pkg: String) { seen.remove(pkg); away.remove(pkg) }

        /** It is in front after all: no longer left (it stays followed for the next time). */
        fun cancel(pkg: String) { away.remove(pkg) }

        /** Not closed now (sound is playing): due again in [RETRY_MS]. */
        fun later(pkg: String, now: Long) { if (pkg in away) away[pkg] = now + RETRY_MS }

        /** For tests and the settings: the apps being waited on. */
        fun waiting(): Set<String> = away.keys.toSet()

        fun reset() { seen.clear(); away.clear() }
    }

    /**
     * The packages that have a player started, from `dumpsys audio`'s lines like
     * `AudioPlaybackConfiguration piid:63 ... u/pid:10132/3883 state:started attr:...`, as app ids (uid); the caller maps them to packages.
     */
    fun startedUids(audio: String): Set<Int> = audio.lineSequence().filter { "state:started" in it }
        .mapNotNull { Regex("u/pid:(\\d+)/").find(it)?.groupValues?.get(1)?.toIntOrNull() }.toSet()

    /**
     * The packages of the media sessions in the playing (3) or buffering (6) state, from `dumpsys media_session`. Android 14 writes
     * `state=PLAYING(3)`, older ones `state=3`. A session block names its package on the line before its state.
     */
    fun playingSessions(media: String): Set<String> {
        val out = HashSet<String>()
        var pkg = ""
        val state = Regex("PlaybackState \\{state=(?:[A-Z_]+\\()?(\\d+)\\)?[,(]")
        for (line in media.lineSequence()) {
            val t = line.trim()
            if (t.startsWith("package=")) pkg = t.removePrefix("package=").trim()
            else state.find(t)?.let { if (pkg.isNotEmpty() && it.groupValues[1] in setOf("3", "6")) out.add(pkg) }
        }
        return out
    }

    /** Whether a service dump (`dumpsys activity services <pkg>`) shows a foreground service of type mediaPlayback (2); with no types shown (before Android 10), any foreground service. */
    fun foregroundMedia(services: String): Boolean = services.lineSequence().filter { "isForeground=true" in it }.any { l ->
        val m = Regex("types=([0-9a-fA-F]+)").find(l)
        m == null || (m.groupValues[1].toLongOrNull(16) ?: 0L) and 2L != 0L
    }
}
