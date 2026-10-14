package dev.tvremote.setup

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Gives a TV Setup pass to a phone or computer that already has ADB on this TV (it could do everything anyway). With it,
 * that device can later ask TV Setup to answer the TV's "Allow USB debugging?" for its own key, when the person is away.
 * Only the shell can send this broadcast (see the manifest), so no app on the TV can get a pass this way.
 */
class TrustReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        resultCode = Activity.RESULT_OK
        resultData = RemoteService.newToken(c)
    }
}
