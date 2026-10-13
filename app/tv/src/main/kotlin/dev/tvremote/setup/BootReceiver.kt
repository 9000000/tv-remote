package dev.tvremote.setup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the web remote again after the TV restarts or this app is updated, if it was left on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (RemoteService.enabled(ctx)) RemoteService.start(ctx)
    }
}
