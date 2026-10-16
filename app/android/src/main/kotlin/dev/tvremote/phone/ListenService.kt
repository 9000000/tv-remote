package dev.tvremote.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import org.json.JSONObject

/**
 * While the TV's sound plays on the phone (see Listen.kt): a media notification, the phone's own media controls (the
 * lock screen, the player in Quick Settings) showing what the TV plays, with ⏮ ⏯ ⏭ for the TV and ✕ to stop listening,
 * so the sound is never left playing from an app nobody remembers. Android keeps the app and the Wi-Fi awake with the
 * phone's screen off, as for any music player. The sound itself comes through the remote's session; [control] is the
 * remote's hand on the TV, [show] what the now playing bar last heard (NowPlayingBar.kt).
 */
class ListenService : Service() {
    private var wifi: WifiManager.WifiLock? = null
    private var session: MediaSession? = null
    private var started = false

    override fun onBind(i: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onCreate() {
        super.onCreate()
        loadLang(this)
        if (Build.VERSION.SDK_INT >= 26)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel("listen", t("Nghe tiếng TV", "TV sound"), NotificationManager.IMPORTANCE_LOW))
        session = MediaSession(this, "tvremote-listen").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = act("play")
                override fun onPause() = act("pause")
                override fun onSkipToNext() = act("next")
                override fun onSkipToPrevious() = act("prev")
                override fun onStop() = act(STOP)
                override fun onCustomAction(action: String, extras: android.os.Bundle?) = act(action)
            })
            isActive = true
        }
        current = this
        shown = null
        show(null, last)
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifi = wm.createWifiLock(if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF, "tvremote:listen").apply { setReferenceCounted(false); acquire() }
    }

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int {
        i?.action?.let { act(it) }                                    // a button of the notification
        return START_NOT_STICKY
    }

    private fun act(what: String) {
        val c = control
        if (c != null) c(what) else if (what == STOP) stopSelf()       // the remote is gone, and its sound with it
    }

    /** What the TV plays ([o] from NowPlaying, null: not known), on the notification and the phone's media controls. */
    @Suppress("DEPRECATION")
    private fun show(o: JSONObject?, tv: String) {
        val s = session ?: return
        val text = { k: String -> if (o == null || o.isNull(k)) "" else o.optString(k) }
        val state = o?.optString("state", "none") ?: "none"
        val film = state != "none"
        val name = text("title").ifEmpty { text("app") }.ifEmpty { t("Tiếng TV", "TV sound") }
        val line = listOf(text("sub").takeIf { text("title").isNotEmpty() } ?: "", tv).filter { it.isNotEmpty() }.joinToString(" · ")
        val dur = o?.optLong("dur") ?: 0
        s.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, name).putString(MediaMetadata.METADATA_KEY_ARTIST, line)
            .apply { if (dur > 0) putLong(MediaMetadata.METADATA_KEY_DURATION, dur) }.build())
        val paused = state == "paused"
        // the sound plays whatever the TV does; a paused film shows as paused so ⏯ reads right
        s.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_STOP or (if (film) PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS else 0L))
            .addCustomAction(PlaybackState.CustomAction.Builder(STOP, t("Thôi nghe", "Stop listening"), R.drawable.ic_close).build())   // Android 13+ draws only these, not the notification's own buttons
            .setState(if (paused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING, o?.optLong("pos") ?: 0, if (paused || !film) 0f else o!!.optDouble("speed", 1.0).toFloat())
            .build())

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags)
        fun button(icon: Int, words: String, what: String, code: Int) =
            Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, icon), words,
                PendingIntent.getService(this, code, Intent(this, ListenService::class.java).setAction(what), flags)).build()
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "listen") else Notification.Builder(this)
        b.setSmallIcon(R.drawable.ic_headphones).setContentTitle(name).setContentText(line.ifEmpty { t("Đang nghe tiếng TV", "Listening to the TV") })
            .setContentIntent(open).setOngoing(true).setVisibility(Notification.VISIBILITY_PUBLIC)
        val compact = if (film) {
            b.addAction(button(R.drawable.ic_prev, t("Bài trước", "Previous"), "prev", 3))
            b.addAction(if (paused) button(R.drawable.ic_play, t("Phát", "Play"), "play", 4) else button(R.drawable.ic_pause, t("Dừng phim", "Pause"), "pause", 4))
            b.addAction(button(R.drawable.ic_next, t("Bài sau", "Next"), "next", 5))
            b.addAction(button(R.drawable.ic_close, t("Thôi nghe", "Stop listening"), STOP, 2))
            intArrayOf(1, 3)
        } else {
            b.addAction(button(R.drawable.ic_close, t("Thôi nghe", "Stop listening"), STOP, 2))
            intArrayOf(0)
        }
        b.setStyle(Notification.MediaStyle().setMediaSession(s.sessionToken).setShowActionsInCompactView(*compact))
        val n = b.build()
        if (!started) {
            started = true
            if (Build.VERSION.SDK_INT >= 29) startForeground(4, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(4, n)
        } else (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(4, n)
    }

    override fun onDestroy() {
        if (current === this) current = null
        session?.run { isActive = false; release() }
        wifi?.release()
        super.onDestroy()
    }

    companion object {
        const val STOP = "dev.tvremote.phone.STOP_LISTEN"
        @Volatile private var current: ListenService? = null
        private var shown: String? = null
        private var last = ""
        /** play, pause, next, prev for the TV, or [STOP]: set by the remote while it lives. */
        @Volatile var control: ((String) -> Unit)? = null

        /** On the UI thread, from the now playing bar's poll; redrawn only when something a person sees changed. */
        fun show(o: JSONObject?, tv: String) {
            val s = current ?: return
            val key = (o?.let { listOf(it.opt("state"), it.opt("title"), it.opt("app"), it.opt("sub"), it.opt("dur"), it.optLong("pos") / 15000).toString() } ?: "") + tv
            last = tv
            if (key == shown) return
            shown = key
            s.show(o, tv)
        }
    }
}
