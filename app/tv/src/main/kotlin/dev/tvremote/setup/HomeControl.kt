package dev.tvremote.setup

import android.content.Context
import org.json.JSONObject

/**
 * What TV Home's Control Center may ask of the rules (HomeInfo "control"): the sleep timer set to one of [SLEEP] minutes and kids mode
 * switched ON, named words and nothing else, never a rules JSON from the caller. Kids mode is not switched off from here: TV Home is
 * free in kids mode (Kids.ALWAYS), so a child could undo it from its own panel; "kids:off" only OPENS the PIN screen ([PinActivity] with
 * `kids_off`: the same PIN / Authenticator / backup code / ask a parent and Lockout as anywhere), and that screen changes the rules on a right code.
 * "kids:setup" opens TV Setup's Manage screen on the TV at the lock card, where the PIN is set; "manage:health" at the Health tab.
 * `arg`: "" asks | "sleep:<minutes>" | "kids:on" | "kids:off" | "kids:setup" | "manage:health". The answer {sleep_at, kids, pin, error?}: error "bad" | "pin"
 * (kids needs a PIN or a code set first) | "failed" (the screen could not open). An old TV Setup answers "bad" to the last two.
 */
object HomeControl {
    val SLEEP = setOf(0, 15, 30, 60, 90, 120)

    /** What a word does: "rules" (changes them here), "pin" / "manage" (only opens a screen, the rules untouched), "ask" (nothing), "bad". */
    fun route(word: String): String = when {
        word.isEmpty() -> "ask"
        word.startsWith("sleep:") && word.substringAfter(':').toIntOrNull() in SLEEP -> "rules"
        word == "kids:on" -> "rules"
        word == "kids:off" -> "pin"
        word == "kids:setup" || word == "manage:health" -> "manage"
        else -> "bad"
    }

    private fun open(c: Context, i: android.content.Intent): Boolean = try { c.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }

    /** The screen a "pin" / "manage" word opens. */
    private fun screen(c: Context, word: String): android.content.Intent? = when (route(word)) {
        "pin" -> android.content.Intent(c, PinActivity::class.java).putExtra("kids_off", true)
        "manage" -> android.content.Intent(c, ManageActivity::class.java).putExtra(ManageActivity.PASS, RemoteService.selfPass)
            .apply {
                if (word == "manage:health") putExtra(ManageActivity.TAB, "health")   // TV Home's watched-today tile and an app's week
                else putExtra(ManageActivity.TAB, "auto").putExtra(ManageActivity.SECTION, if (Rules.kidsMode(Rules.load(c))) "kidsadd" else "lockcard")   // on: the apps a child may open; off: the PIN first
            }
        else -> null
    }

    /**
     * That screen for TV Home to start itself: this app is in the background when the panel asks, and Android 10+ drops its own start
     * without a word unless it may draw over others (granted through ADB: a TV without ADB for TV Setup, or the emulator, got nothing;
     * the kids tile closed the panel and nothing came). TV Home is the screen in front, so the start is its own.
     */
    fun handBack(c: Context, word: String): android.app.PendingIntent? = screen(c, word)?.let {
        android.app.PendingIntent.getActivity(c, word.hashCode(), it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** [handOver]: the caller starts the screen itself ([handBack]); a TV Home older than that has this app open it. */
    fun run(c: Context, arg: String?, handOver: Boolean = false): JSONObject {
        var error: String? = null
        val word = arg.orEmpty()
        try {
            when (route(word)) {
                "ask" -> {}
                "rules" ->
                    if (word == "kids:on") Rules.change(c, JSONObject().put("do", "kids").put("mode", "kids"))
                    else Rules.change(c, JSONObject().put("do", "sleep").put("minutes", word.substringAfter(':').toInt()))
                "pin", "manage" -> if (!handOver && !open(c, screen(c, word)!!)) error = "failed"
                else -> error = "bad"
            }
        } catch (e: IllegalArgumentException) { error = "pin" }
        if (error == null && route(word) == "rules" && Rules.active(c) && !RemoteService.isRunning()) {
            try { RemoteService.start(c, true) } catch (e: Exception) { }   // the watcher that enforces them, as RulesReceiver starts it
        }
        val o = Rules.load(c)
        val now = System.currentTimeMillis()
        val lock = o.optJSONObject("lock")
        return JSONObject().put("sleep_at", o.optLong("sleep_at").let { if (it > now) it else 0L })
            .put("kids", Rules.kidsMode(o)).put("pin", lock?.has("hash") == true || lock?.has("totp") == true)
            .also { if (error != null) it.put("error", error) }
    }
}
