package dev.tvremote.home

import android.app.Activity
import android.os.Bundle

/** The screensaver's "Customize" in the system's settings: TV Home's own Customize panel over nothing, gone when it closes. */
class DreamSettings : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Customize.show(this).setOnDismissListener { finish() }
    }
}
