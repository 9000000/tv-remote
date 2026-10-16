package dev.tvremote.setup

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Gives a TV Setup pass to a phone or computer that already has ADB on this TV (it could do everything anyway). With it,
 * that device can later ask TV Setup to answer the TV's "Allow USB debugging?" for its own key, when the person is away.
 * Only the shell can send this broadcast (see the manifest), so no app on the TV can get a pass this way.
 * With `--es want fp` it answers the fingerprint of the HTTPS certificate instead (no pass): the shell is the one channel
 * already trusted, so the device pins the HTTPS port to it. An older TV Setup ignores that and gives a pass, which the
 * device takes for what it is. With `--es want invite`: a one-time invite and this TV's home address, for a QR another phone
 * scans (Invite.kt); clients check the TV Setup version first, as an older one would hand out a pass instead.
 */
class TrustReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        resultCode = Activity.RESULT_OK
        if (i.getStringExtra("want") == "fp") { resultData = Tls.fingerprint(c); return }
        if (i.getStringExtra("want") == "invite") {                  // a QR to share (Invite.kt): the web page must be on to open it
            if (!RemoteService.enabled(c)) RemoteService.setEnabled(c, true)
            resultData = RemoteService.newInvite() + " " + RemoteService.lanIp()
            return
        }
        val vi = java.util.Locale.getDefault().language == "vi"
        val name = when (i.getStringExtra("app")) {
            "phone" -> if (vi) "App điện thoại" else "Phone app"
            "computer" -> if (vi) "App máy tính" else "Computer app"
            else -> "App"
        }
        resultData = RemoteService.newToken(c, "app", name, "", i.getStringExtra("tls") == "1")   // an app that pins the HTTPS port says so: its pass is no good over HTTP
    }
}
