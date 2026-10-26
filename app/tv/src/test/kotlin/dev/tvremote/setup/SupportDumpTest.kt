package dev.tvremote.setup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Support's commands from afar only read: anything that chains, writes or changes is refused before ADB sees it. */
class SupportDumpTest {
    @Test fun readsPass() {
        listOf("getprop ro.build.version.sdk", "settings get secure enabled_accessibility_services", "settings list global",
            "dumpsys package dev.tvremote.setup.keys", "dumpsys activity activities", "pm list packages -3", "pm dump dev.tvremote.setup",
            "appops get dev.tvremote.setup SYSTEM_ALERT_WINDOW", "logcat -d -s KeyFix:I", "logcat -d -t 200", "wm size", "uptime",
            "cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN")
            .forEach { assertTrue(it, SupportDump.allowed(it)) }
    }

    @Test fun changesAndChainsAreRefused() {
        listOf("settings put global adb_enabled 0", "pm uninstall dev.tvremote.setup", "pm clear dev.tvremote.setup", "input keyevent 26",
            "am start -a android.intent.action.VIEW", "dumpsys battery set level 5", "dumpsys deviceidle force-idle", "dumpsys jobscheduler run x",
            "getprop; reboot", "getprop | sh", "getprop && reboot", "logcat -d > /sdcard/x", "getprop `reboot`", "getprop \$(reboot)",
            "getprop\nreboot", "rm -rf /sdcard", "cat /sdcard/DCIM/a.jpg", "logcat", "logcat -c", "wm size 100x100", "appops set x y allow",
            "dumpsys clipboard", "dumpsys notification --noredact", "dumpsys account", "content query --uri content://sms")
            .forEach { assertFalse(it, SupportDump.allowed(it)) }
    }
}
