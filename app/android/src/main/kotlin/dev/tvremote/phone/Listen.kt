package dev.tvremote.phone

import android.content.Intent
import android.os.Build
import android.widget.Toast

// 🎧 The TV's sound on the phone, two ways, picked each time: only here (the TV's speakers quiet: headphones on, the house
// asleep; they come back the moment listening stops), or both (the TV keeps playing: scrcpy's playback capture, Android 13+;
// the phone lags the TV a little and apps that refuse capture are silent). Opus at 64 kbit/s (160 with ✨), about 8 KB/s, decoded here (AudioOut): next to the picture's up to 2 Mbit/s it
// costs the network and the TV almost nothing, and it keeps going with the phone's screen off (ListenService).

/** 🎧 on the top bar: on or off at once, the way picked last (the chip under the picture, [listenMenu]). The session restarts either way, as for the picture. */
internal fun MainActivity.toggleListen() {
    if (needsAdb()) return
    if (listen) { stopListen(null); return }
    listen = true
    listenShare = prefs.getBoolean("listenShare", false)
    restartForSound()
    listenService(true)
    Toast.makeText(this, if (listenShare) t("Đang mở tiếng TV trên cả TV và điện thoại.", "Opening the TV’s sound on both the TV and the phone.")
        else t("Đang mở tiếng TV trên điện thoại. Loa TV tạm im.", "Opening the TV’s sound on the phone. The TV’s speakers go quiet."), Toast.LENGTH_SHORT).show()
}

/** The way to hear the TV and its quality, from the chip under the picture; while listening, the change applies at once. */
internal fun MainActivity.listenMenu() {
    val share = prefs.getBoolean("listenShare", false)
    val hq = prefs.getBoolean("listenHq", false)
    val menu = android.widget.PopupMenu(this, listenChip)
    menu.menu.add(0, 0, 0, t("Chỉ điện thoại · loa TV tạm im", "Only the phone · TV quiet") + (if (!share) "  ✓" else ""))
    menu.menu.add(0, 1, 1, t("Cả TV và điện thoại · Android 13+", "TV and phone · Android 13+") + (if (share) "  ✓" else ""))
    menu.menu.add(0, 2, 2, t("Chất lượng cao · 160 kbit/s", "High quality · 160 kbit/s") + (if (hq) "  ✓" else ""))
    menu.setOnMenuItemClickListener { item ->
        when (item.itemId) {
            0, 1 -> prefs.edit().putBoolean("listenShare", item.itemId == 1).apply()
            2 -> prefs.edit().putBoolean("listenHq", !hq).apply()
        }
        if (listen) { listenShare = prefs.getBoolean("listenShare", false); restartForSound() } else layoutRemote()
        true
    }
    menu.show()
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
