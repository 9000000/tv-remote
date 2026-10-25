package dev.tvremote.update

/**
 * Who updates TV Home, TV Channels and TV Games, and when: the pure decisions, so a test can hold them (UpdatePlanTest).
 * TV Setup ([AutoUpdate], over ADB) does it without a question; the app's own popup (SelfUpdate) is the fallback.
 */
object UpdatePlan {
    /**
     * How long an app leaves a newer release to TV Setup before asking itself. TV Setup looks every 3 h and installs within
     * hours, but a TV that is watched all day, or that TV Setup could not reach, may need a few days of standbys; 3 days
     * covers a weekend away without leaving an app old for long when TV Setup cannot do it after all.
     */
    const val GRACE = 3 * 24 * 3600_000L

    /** How long an app that was in front stays "in use" for a silent install while the TV is awake (the person may come back to it). */
    const val RECENT = 30 * 60_000L

    /**
     * true: the app stays quiet about [firstSeen] (when it first saw this release; 0 = just now) because TV Setup will install it.
     * [setupSilent]: TV Setup answered that its auto-update is on and it holds an ADB link; null/false = it did not (missing,
     * an old one, switched off, no ADB): the app asks as it always did.
     */
    fun quiet(setupSilent: Boolean?, firstSeen: Long, now: Long): Boolean = setupSilent == true && now - firstSeen < GRACE

    /**
     * May TV Setup install an own app now? [awake] the screen is on, [top] the app is the resumed activity, [lastUsed] the last
     * time it was in front (null: usage unknown), [careful] false for TV Home, whose rule is only "not on top".
     * In standby anything goes (the install closes the app under nobody). Awake: not on top, and for TV Channels / TV Games
     * not in front within [RECENT] either, since they are opened and left again (a paused stream, a game being picked).
     */
    fun mayInstall(awake: Boolean, top: Boolean, lastUsed: Long?, now: Long, careful: Boolean): Boolean {
        if (!awake) return true
        if (top) return false
        if (!careful) return true
        return lastUsed != null && now - lastUsed >= RECENT
    }

    /** TV Setup looks for new releases (its own and the apps') this often: a versions.json and the signed list, cheap, and a new app waits at most this long. */
    const val CHECK = 3 * 3600_000L

    /** When an app had to wait (on screen, used lately), the apps step is tried again after this, on the cached release, without asking GitHub again. */
    const val RETRY = 20 * 60_000L
}
