package dev.tvremote.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder

/**
 * While the TV's sound plays on the phone (see Listen.kt): a notification with "Stop", and Android keeps the app and the
 * Wi-Fi awake with the phone's screen off, as for any music player. The sound itself comes through the remote's session.
 */
class ListenService : Service() {
    private var wifi: WifiManager.WifiLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onCreate() {
        super.onCreate()
        loadLang(this)
        val b = if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel("listen", t("Nghe tiếng TV", "TV sound"), NotificationManager.IMPORTANCE_LOW))
            Notification.Builder(this, "listen")
        } else Notification.Builder(this)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags)
        val stop = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java).setAction(STOP).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags)
        val n = b.setSmallIcon(R.drawable.ic_headphones).setContentTitle(t("Đang nghe tiếng TV", "Listening to the TV"))
            .setContentText(t("Bấm Dừng để thôi nghe trên điện thoại.", "Stop to end listening on the phone."))
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, t("Dừng", "Stop"), stop).build()).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(4, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(4, n)
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifi = wm.createWifiLock(if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF, "tvremote:listen").apply { setReferenceCounted(false); acquire() }
    }

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        wifi?.release()
        super.onDestroy()
    }

    companion object {
        const val STOP = "dev.tvremote.phone.STOP_LISTEN"
    }
}
