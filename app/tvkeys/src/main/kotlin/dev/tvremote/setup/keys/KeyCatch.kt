package dev.tvremote.setup.keys

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * The first key of the TV's own remote while the screen is off (ScreenOff.kt) only turns the screen back on: without this, the
 * OK that woke it would also open the app under the focus. Turned on and off by tvr-helper's Screen (as the shell, through
 * the secure settings) for just that time. A key from a real input device is eaten, down to its last repeat and its up, and the
 * service turns itself off; the remotes' keys (ADB `input`, scrcpy: a virtual device) pass, so the phone still drives the TV.
 * Needs nothing from TV Setup, so it works on its own.
 */
class KeyCatch : AccessibilityService() {
    private var eating = -1

    companion object {
        /** Bound now: [KeyFixService] leaves keys alone then, the one that wakes the screen is this service's. */
        @Volatile var live = false
    }

    override fun onServiceConnected() { live = true }
    override fun onUnbind(i: android.content.Intent?): Boolean { live = false; return super.onUnbind(i) }
    override fun onDestroy() { live = false; super.onDestroy() }

    override fun onKeyEvent(e: KeyEvent): Boolean {
        val dev = InputDevice.getDevice(e.deviceId)
        if (dev == null || dev.isVirtual) return false
        if (eating < 0 && e.action == KeyEvent.ACTION_DOWN) eating = e.keyCode
        if (e.keyCode != eating) return false
        if (e.action == KeyEvent.ACTION_UP && Build.VERSION.SDK_INT >= 24) disableSelf()   // older: Screen takes it out of the settings
        return true
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) { }
    override fun onInterrupt() { }
}
