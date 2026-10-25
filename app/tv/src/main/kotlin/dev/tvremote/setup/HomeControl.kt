package dev.tvremote.setup

import android.content.Context
import org.json.JSONObject

/**
 * What TV Home's Control Center may ask of the rules (HomeInfo "control"): the sleep timer set to one of [SLEEP] minutes and kids mode
 * switched ON, named words and nothing else, never a rules JSON from the caller. Kids mode is not switched off from here: TV Home is
 * free in kids mode (Kids.ALWAYS), so a child could undo it from its own panel; the PIN screen is the way out.
 * `arg`: "" asks | "sleep:<minutes>" | "kids:on". The answer {sleep_at, kids, pin, error?}: error "bad" | "pin" (kids needs a PIN or a code set first).
 */
object HomeControl {
    val SLEEP = setOf(0, 15, 30, 60, 90, 120)

    fun run(c: Context, arg: String?): JSONObject {
        var error: String? = null
        val word = arg.orEmpty()
        try {
            when {
                word.isEmpty() -> {}
                word.startsWith("sleep:") && word.substringAfter(':').toIntOrNull() in SLEEP ->
                    Rules.change(c, JSONObject().put("do", "sleep").put("minutes", word.substringAfter(':').toInt()))
                word == "kids:on" -> Rules.change(c, JSONObject().put("do", "kids").put("mode", "kids"))
                else -> error = "bad"
            }
        } catch (e: IllegalArgumentException) { error = "pin" }
        if (error == null && word.isNotEmpty() && Rules.active(c) && !RemoteService.isRunning()) {
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
