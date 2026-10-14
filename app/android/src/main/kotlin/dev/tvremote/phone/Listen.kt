package dev.tvremote.phone

import android.content.Intent
import android.os.Build
import android.widget.Toast

// 🎧 The TV's sound on the phone, two ways, picked each time: only here (the TV's speakers quiet: headphones on, the house
// asleep; they come back the moment listening stops), or both (the TV keeps playing: scrcpy's playback capture, Android 13+;
// the phone lags the TV a little and apps that refuse capture are silent). Opus at 64 kbit/s, about 8 KB/s, decoded here (AudioOut): next to the picture's up to 2 Mbit/s it
// costs the network and the TV almost nothing, and it keeps going with the phone's screen off (ListenService).

/** Off, or the two ways to pick from (the one used last is marked). The session restarts either way, as for the picture. */
internal fun MainActivity.toggleListen() {
    if (needsAdb()) return
    if (listen) { stopListen(null); return }
    val start = { share: Boolean ->
        listen = true
        listenShare = share
        prefs.edit().putBoolean("listenShare", share).apply()
        restartForSound()
        listenService(true)
        Toast.makeText(this, if (share) t("Đang mở tiếng TV trên cả TV và điện thoại.", "Opening the TV’s sound on both the TV and the phone.")
            else t("Đang mở tiếng TV trên điện thoại. Loa TV tạm im.", "Opening the TV’s sound on the phone. The TV’s speakers go quiet."), Toast.LENGTH_SHORT).show()
    }
    menu(t("Nghe tiếng TV", "Hear the TV"), if (prefs.getBoolean("listenShare", false)) 1 else 0, listOf(
        Triple(t("🎧 Chỉ điện thoại", "🎧 Only the phone"), t("Loa TV tạm im. Hợp khi đeo tai nghe, cả nhà ngủ.", "The TV’s speakers go quiet. For headphones while the house sleeps.")) { start(false) },
        Triple(t("🔈 Cả TV và điện thoại", "🔈 TV and phone"), t("TV vẫn có tiếng (TV Android 13+). Điện thoại trễ hơn TV một chút; vài app như Netflix chặn thu tiếng kiểu này.",
            "The TV keeps its sound (Android 13+ TVs). The phone lags the TV a little; some apps such as Netflix block this kind of capture.")) { start(true) }))
}

/** Back to the TV's speakers; [why] is said when it was not the person's choice (e.g. the TV cannot send its sound). */
internal fun MainActivity.stopListen(why: String?) {
    if (!listen) return
    listen = false
    listenService(false)
    if (host != null) restartForSound()
    if (why != null) Toast.makeText(this, why, Toast.LENGTH_LONG).show()
}

private fun MainActivity.restartForSound() {
    stopSession()
    bgListen = false
    layoutRemote()
    startSession()
}

internal fun MainActivity.listenService(on: Boolean) {
    val i = Intent(this, ListenService::class.java)
    try {
        if (!on) stopService(i)
        else if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    } catch (ignored: Exception) {                                    // no notification allowed: the sound still plays while the app is open
    }
}
