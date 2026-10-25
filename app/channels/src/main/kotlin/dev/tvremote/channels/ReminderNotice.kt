package dev.tvremote.channels

import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.tvremote.tvui.TvUi
import java.lang.ref.WeakReference

/** The error guard's words from strings.xml (no :shared here, so no i18n files). */
fun guardWords(c: android.content.Context) = dev.tvremote.guard.GuardWords(c.getString(R.string.err_title), c.getString(R.string.err_last_time),
    c.getString(R.string.err_copy), c.getString(R.string.err_copied), c.getString(R.string.err_share), c.getString(R.string.err_close)) { n ->
    c.getString(R.string.err_repeat, n) }

/** The app, only to know which of its screens is in front (a reminder then shows inside it). */
class ChannelsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        dev.tvremote.guard.Guard.install(this, { c -> guardWords(c) })
        dev.tvremote.report.OwnReport.start(this, "channels")            // anonymous error reports (TV Setup's switch, else on)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) { ReminderNotice.front = WeakReference(a) }
            override fun onActivityPaused(a: Activity) { if (ReminderNotice.front?.get() === a) ReminderNotice.front = null }
            override fun onActivityCreated(a: Activity, b: android.os.Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: android.os.Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }
}

/** The alarm of one reminder. Not exported: only our own PendingIntent reaches it. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val item = Reminders.Item(i.getStringExtra("id") ?: return, i.getStringExtra("name") ?: "", i.getStringExtra("title") ?: "", i.getLongExtra("start", 0))
        ReminderNotice.fire(c.applicationContext, item)
    }
}

/** Arms the kept reminders again after a restart or an update (the alarms are gone then). */
class ReminderBoot : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { Reminders.rearm(c.applicationContext) }
}

/**
 * What a reminder shows when it fires, by where the person is: inside TV Channels a card in the corner; over another app the
 * same card as a window over everything (needs "display over other apps", which the phone / computer app grants over ADB), else a
 * notification. Screen off: nothing, the TV is not woken.
 */
object ReminderNotice {
    @Volatile var front: WeakReference<Activity>? = null
    private val ui = Handler(Looper.getMainLooper())
    private const val SECONDS = 12_000L
    private var dismiss: (() -> Unit)? = null

    fun fire(c: Context, item: Reminders.Item) {
        Reminders.done(c, item.key())
        val pm = c.getSystemService(PowerManager::class.java)
        if (!pm.isInteractive) return                                  // standby: the TV is not woken, nothing stays pending
        ui.post {
            val a = front?.get()
            when {
                a != null && !a.isFinishing -> inApp(a, item)
                canOverlay(c) -> overlay(c, item)
                else -> notify(c, item)
            }
        }
    }

    fun canOverlay(c: Context) = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c)

    private fun open(c: Context, item: Reminders.Item) = Intent(Intent.ACTION_VIEW, Pins.link(item.id)).setClass(c, PlayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The card: bell, "Starting soon", the programme, its time and channel, and the keys. */
    private fun card(c: Context, item: Reminders.Item): LinearLayout {
        val d = { v: Number -> TvUi.dp(c, v).toInt() }
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d(22), d(16), d(22), d(16))
            background = TvUi.round(0xFF202124.toInt(), TvUi.dp(c, 16), d(2), TvUi.ACCENT)
            isFocusable = true; isFocusableInTouchMode = true
            addView(TvUi.text(c, "🔔 " + c.getString(R.string.remind_head), 14f, TvUi.ACCENT, true))
            addView(TvUi.text(c, item.title, 22f, Color.WHITE, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, d(4), 0, 0) })
            addView(TvUi.text(c, c.getString(R.string.remind_title, Guide.hm(item.start), item.name), 16f, TvUi.TEXT).apply { setPadding(0, d(2), 0, d(8)) })
            addView(TvUi.text(c, c.getString(R.string.remind_hint), 13f, TvUi.DIM))
            minimumWidth = d(300)
        }
    }

    /** OK = [go], Back = away; any other key is the person doing something else: away too. */
    private fun keys(v: View, go: () -> Unit, away: () -> Unit) {
        v.setOnKeyListener { _, k, e ->
            if (e.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener k != KeyEvent.KEYCODE_VOLUME_UP && k != KeyEvent.KEYCODE_VOLUME_DOWN
            when (k) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> go()
                else -> away()
            }
            true
        }
        v.setOnClickListener { go() }
    }

    private fun inApp(a: Activity, item: Reminders.Item) {
        dismiss?.invoke()
        val root = a.window.decorView as FrameLayout
        val was = a.currentFocus
        val card = card(a, item)
        val m = TvUi.dp(a, 32).toInt()
        root.addView(card, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(m, m, m, m) })
        val end = Runnable { dismiss?.invoke() }
        dismiss = {
            ui.removeCallbacks(end); dismiss = null
            root.removeView(card)
            if (was != null && was.isAttachedToWindow) was.requestFocus()
        }
        keys(card, { dismiss?.invoke(); a.startActivity(open(a, item)) }, { dismiss?.invoke() })
        card.requestFocus()
        ui.postDelayed(end, SECONDS)
    }

    private fun overlay(c: Context, item: Reminders.Item) {
        dismiss?.invoke()
        val wm = c.getSystemService(WindowManager::class.java)
        val card = card(c, item)
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        // focusable only while it is there: it takes the keys (OK / Back) and gives them back when it goes
        val lp = WindowManager.LayoutParams(-2, -2, type, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT)
        lp.gravity = Gravity.BOTTOM or Gravity.END
        lp.x = TvUi.dp(c, 32).toInt(); lp.y = lp.x
        val end = Runnable { dismiss?.invoke() }
        try { wm.addView(card, lp) } catch (e: Exception) { notify(c, item); return }
        dismiss = { ui.removeCallbacks(end); dismiss = null; try { wm.removeView(card) } catch (e: Exception) {} }
        keys(card, { dismiss?.invoke(); c.startActivity(open(c, item)) }, { dismiss?.invoke() })
        card.requestFocus()
        ui.postDelayed(end, SECONDS)
    }

    private fun notify(c: Context, item: Reminders.Item) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("remind", c.getString(R.string.remind_channel), NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(c, item.key().hashCode(), open(c, item), PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, "remind") else @Suppress("DEPRECATION") Notification.Builder(c).setPriority(Notification.PRIORITY_HIGH)
        b.setSmallIcon(R.drawable.ic_bell).setContentTitle(item.title).setContentText(c.getString(R.string.remind_title, Guide.hm(item.start), item.name))
            .setContentIntent(pi).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER)
        if (Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(10 * 60_000L)
        nm.notify(item.key().hashCode(), b.build())
    }
}
