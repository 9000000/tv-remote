package dev.tvremote.setup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the web remote again after the TV restarts or this app is updated, if it was left on, or timers and limits need it. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        // Android 12+ may refuse a foreground service at boot on some TVs: the next app open or TV Setup broadcast starts it
        if (RemoteService.wanted(ctx)) try { RemoteService.start(ctx, true) } catch (e: Exception) { }
    }
}
