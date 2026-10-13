package dev.tvremote.setup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts the web remote again after the TV restarts or this app is updated, if it was left on. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent i) {
        if (RemoteService.enabled(ctx)) RemoteService.start(ctx);
    }
}
