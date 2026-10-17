package dev.tvremote.phone

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
import android.os.PowerManager

/**
 * While the Manage screen copies a file to the TV, installs an app or updates TV Setup: a notification, and Android lets the
 * app run on in the background (switching to another app would otherwise freeze it and cut the copy halfway). Only closing
 * the app itself ends the work.
 */
class WorkService : Service() {
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        loadLang(this)
        reachAroundVpn()
        val n = note(this, "")
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, n)
        wake = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tvremote:work")
            .apply { setReferenceCounted(false); acquire(60 * 60 * 1000L) }   // an hour at most, whatever happens
    }

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int {
        i?.getStringExtra(WHAT)?.let { show(this, it) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wake?.release()
        super.onDestroy()
    }

    companion object {
        private const val ID = 5
        const val WHAT = "what"

        /** In words, what Manage.working() names. */
        fun say(what: String): String = when (what) {
            "upload" -> t("Đang chép tệp lên TV", "Copying a file to the TV")
            "install" -> t("Đang cài app lên TV", "Installing an app on the TV")
            "update" -> t("Đang cập nhật TV Setup", "Updating TV Setup")
            "tailscale" -> t("Đang cài Tailscale trên TV", "Setting up Tailscale on the TV")
            else -> t("Đang làm việc trên TV", "Working on the TV")
        }

        /** [pct] 0..100, or below 0 when the work does not tell (a bar that only moves). */
        fun show(c: Context, what: String, pct: Int = -1) {
            try { (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(ID, note(c, what, pct)) } catch (ignored: Exception) { }
        }

        @Suppress("DEPRECATION")
        private fun note(c: Context, what: String, pct: Int = -1): Notification {
            val b = if (Build.VERSION.SDK_INT >= 26) {
                (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel("work", t("Việc đang làm trên TV", "Work on the TV"), NotificationManager.IMPORTANCE_LOW))
                Notification.Builder(c, "work")
            } else Notification.Builder(c)
            val open = PendingIntent.getActivity(c, 3, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return b.setSmallIcon(R.drawable.ic_tv).setContentTitle(say(what) + (if (pct >= 0) " · $pct%" else "..."))
                .setContentText(t("Vẫn chạy khi chuyển sang app khác.", "Goes on while you use other apps."))
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setProgress(100, pct.coerceAtLeast(0), pct < 0).build()
        }
    }
}
