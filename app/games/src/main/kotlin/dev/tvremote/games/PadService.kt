package dev.tvremote.games

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Keeps the process (and so the pad server) alive while TV Games is in the background with phones connected: without a foreground
 * service a TV that is short of memory kills it after Home, and the pads sit on a stale screen. Started by [GameServer] while an
 * activity is in front (the only time Android lets it), stopped with the server.
 */
class PadService : Service() {
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, getString(R.string.pad_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, LibraryActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH) else Notification.Builder(this)
        val n = b.setSmallIcon(R.drawable.icon).setContentTitle(getString(R.string.app_name)).setContentText(getString(R.string.pad_running))
            .setContentIntent(open).setOngoing(true).build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(ID, n)
        } catch (e: Exception) { stopSelf() }                                // not allowed from the background (Android 12+): the server just runs without it
        return START_NOT_STICKY
    }

    companion object {
        private const val CH = "pad"
        private const val ID = 8780
        fun start(ctx: Context) { try { ctx.startForegroundService(Intent(ctx, PadService::class.java)) } catch (e: Exception) { } }
        fun stop(ctx: Context) { try { ctx.stopService(Intent(ctx, PadService::class.java)) } catch (e: Exception) { } }
    }
}
