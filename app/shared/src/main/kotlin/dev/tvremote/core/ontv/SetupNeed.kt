package dev.tvremote.core.ontv
import dev.tvremote.update.Release
import java.io.IOException
import dev.tvremote.core.adb.Adb

/**
 * What each feature needs of TV Setup on the TV, in one place: a phone or computer can be newer than the TV's TV Setup.
 * A client asks before a feature starts (/api/setup/need?f=, [check]); a feature that finds out midway throws [Missing].
 * Either way every client shows the same question with one button, update or install TV Setup (needSetup() in js/core.js,
 * offerSetup() in the phone app), instead of an error once the work is lost.
 * A feature that needs a newer TV Setup adds one line to [MIN], and its name to both of those.
 */
object SetupNeed {
    /** feature → the first TV Setup that has it */
    val MIN = linkedMapOf(
        "rules" to "0.1.0",                                            // sleep timer, bedtime, app limits
        "draw" to "0.1.0",                                             // ✏️ drawing over the screen
        "pic" to "0.1.0",                                              // 🖼️ a picture over it
        "volume" to "0.1.0",                                           // 🔊 volume limits
        "bedtimes" to "0.3.1",                                         // 🌙 more than one bedtime
        "notify" to "0.4.2",                                           // 🔔 a card with a message over the TV's screen
        "invite" to "0.4.2",                                           // 📲 sharing the remote by QR (an older one answers with a pass)
        "ram" to "0.4.2",                                              // 🧠 the notice when the memory is nearly full
        "awake" to "0.7.1",                                            // 😴 the chip kept up while the TV is off (KeepAwake)
    )

    /** "setup:old:pic:2.21.0:2.20.0" or "setup:missing:draw:2.18.0:": the words every client looks for in an error. */
    class Missing(val feature: String, val have: String) :
        IOException("setup:" + (if (have.isEmpty()) "missing" else "old") + ":" + feature + ":" + (MIN[feature] ?: "") + ":" + have)

    /** The feature's name in a sentence; the same words as SETUP_NEEDS in js/core.js. */
    fun name(feature: String, vi: Boolean): String = when (feature) {
        "rules" -> if (vi) "Hẹn giờ, giờ đi ngủ, giới hạn app" else "The sleep timer, bedtime and app limits"
        "draw" -> if (vi) "Vẽ lên TV" else "Drawing on the TV"
        "pic" -> if (vi) "Đưa ảnh lên TV" else "A picture on the TV"
        "volume" -> if (vi) "Giới hạn âm lượng" else "Volume limits"
        "bedtimes" -> if (vi) "Nhiều khoảng giờ đi ngủ" else "Several bedtimes"
        "notify" -> if (vi) "Gửi thông báo lên TV" else "Sending a notification to the TV"
        "invite" -> if (vi) "Chia sẻ remote bằng QR" else "Sharing the remote by QR"
        "ram" -> if (vi) "Báo khi RAM đầy" else "The full memory notice"
        "awake" -> if (vi) "Giữ kết nối khi tắt TV" else "Staying reachable while the TV is off"
        else -> feature
    }

    /** What to say about [e] when it is a [Missing] (or its words came back from a server), else null. */
    fun say(e: Throwable, vi: Boolean): String? {
        val m = Regex("setup:(old|missing):(\\w+):([0-9.]*):(\\S*)").find(generateSequence(e) { it.cause }.joinToString(" ") { it.message ?: "" }) ?: return null
        val (kind, f, min, have) = m.destructured
        return if (kind == "missing") (if (vi) "${name(f, true)} cần app TV Setup trên TV. Cài TV Setup?" else "${name(f, false)} needs the TV Setup app on the TV. Install TV Setup?")
        else if (vi) "${name(f, true)} cần TV Setup $min trở lên (TV đang có $have). Cập nhật TV Setup?"
        else "${name(f, false)} needs TV Setup $min or newer (the TV has $have). Update TV Setup?"
    }

    fun ok(have: String, feature: String): Boolean {
        val min = MIN[feature] ?: return true
        return have.isNotEmpty() && Release.compare(have, min) >= 0
    }

    @Throws(IOException::class)
    fun check(adb: Adb, feature: String) {
        val have = SetupUpdate.installed(adb)
        if (!ok(have, feature)) throw Missing(feature, have)
    }

    /** The same from a shell's answer (dumpsys), for callers holding a shell rather than an [Adb]. */
    fun have(sh: (String) -> String): String =
        Regex("versionName=(\\S+)").find(sh("dumpsys package ${RulesClient.SETUP} 2>/dev/null | grep -m1 versionName="))?.groupValues?.get(1) ?: ""
}
