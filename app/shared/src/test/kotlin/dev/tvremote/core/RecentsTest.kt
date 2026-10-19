package dev.tvremote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.tvremote.core.ontv.Recents

class RecentsTest {
    // the Google TV emulator (Android 14), shortened
    private val a14 = """
ACTIVITY MANAGER RECENT TASKS (dumpsys activity recents)
  Recent tasks:
  * Recent #0: Task{aa #201 type=standard A=10085:com.google.android.youtube.tv}
    mActivityComponent=com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity
    mRootProcess=ProcessRecord{fabd9f0 25621:com.google.android.youtube.tv/u0a85}
    taskId=201 rootTaskId=201
    lastActiveTime=4380066 (inactive for 3s)
  * Recent #1: Task{6e0a833 #190 type=home I=com.google.android.apps.tv.launcherx/.home.HomeActivity}
    taskId=190 rootTaskId=190
    lastActiveTime=4380065 (inactive for 60s)
  * Recent #2: Task{fd4e559 #192 type=standard A=10085:com.android.vending:977579449}
    mActivityComponent=com.android.vending/com.google.android.finsky.activities.MainActivity
    mRootProcess=null
    taskId=192 rootTaskId=192
    lastActiveTime=4233324 (inactive for 3107s)
  * Recent #3: Task{e45c139 #159 type=standard I=com.android.vending/com.google.android.finsky.protectdialogs.activity.PlayProtectDialogsActivity}
    taskId=159 rootTaskId=159
    lastActiveTime=1 (inactive for 9000s)
  * Recent #4: Task{bb #150 type=dream A=1000:com.android.systemui}
    taskId=150 rootTaskId=150
"""
    // Android 9 (a Sony), shortened
    private val a9 = """
  Recent tasks:
  * Recent #0: TaskRecord{c3b8f1d #45 A=com.google.android.tvlauncher U=0 StackId=0 sz=1}
    intent={act=android.intent.action.MAIN cat=[android.intent.category.HOME] flg=0x10000100 cmp=com.google.android.tvlauncher/.MainActivity}
    realActivity=com.google.android.tvlauncher/.MainActivity
    lastActiveTime=12345 (inactive for 10s)
  * Recent #1: TaskRecord{d1 #47 A=com.netflix.ninja U=0 StackId=3 sz=1}
    realActivity=com.netflix.ninja/.MainActivity
    lastActiveTime=1000 (inactive for 400s)
"""

    @Test fun android14() {
        val apps = Recents.json(a14).getJSONArray("apps")
        assertEquals(listOf("com.google.android.youtube.tv", "com.android.vending"), (0 until apps.length()).map { apps.getJSONObject(it).getString("pkg") })
        assertTrue(apps.getJSONObject(0).getBoolean("top"))
        assertFalse(apps.getJSONObject(1).getBoolean("running"))
        assertEquals(3107L, apps.getJSONObject(1).getLong("ago"))
    }

    @Test fun android9() {
        val apps = Recents.json(a9).getJSONArray("apps")
        assertEquals(1, apps.length())
        assertEquals("com.netflix.ninja", apps.getJSONObject(0).getString("pkg"))
        assertFalse(apps.getJSONObject(0).getBoolean("top"))                  // home is on screen
        assertFalse(apps.getJSONObject(0).has("running"))
    }

    @Test fun closeRemovesOnlyOwnRootTasks() {
        val said = ArrayList<String>()
        Recents.close({ said += it; if (it == Recents.CMD) a14 else "" }, "com.android.vending", keep = false)
        assertEquals(listOf(Recents.CMD, "am stack remove 192", "am stack remove 159", "am force-stop com.android.vending"), said)
        said.clear()
        Recents.close({ said += it; if (it == Recents.CMD) a9 else "" }, "com.netflix.ninja", keep = true)
        assertEquals(listOf(Recents.CMD), said)                                // Android 9: a stack number, never removed
    }
}
