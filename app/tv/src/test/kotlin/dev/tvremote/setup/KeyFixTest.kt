package dev.tvremote.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyFixTest {
    private val stock = "com.google.android.tv/com.google.android.tv.receiver.GlobalKeyReceiver"
    // what Google TV's global_keys.xml and the FPT box's mKeyMapping say
    private val mapped = KeyFix.byCode(listOf("KEYCODE_TV" to stock, "KEYCODE_TV_INPUT" to stock, "KEYCODE_GUIDE" to stock, "KEYCODE_BUTTON_3" to stock,
        "KEYCODE_BUTTON_2" to "com.example.facservicetest/.MyReceiver", "KEYCODE_0" to "com.hisense.bigfac/.Boot", "KEYCODE_PAIRING" to "com.android.tv.settings/.G"))

    @Test fun onlyKeysWeHaveSomethingForAreRead() {
        assertEquals(setOf(170, 178, 190), mapped.keys)               // GUIDE, 0, BUTTON_2 and PAIRING are not ours
    }

    @Test fun aKeyWhoseHandlerIsStillThereIsNeverTaken() {
        assertTrue(KeyFix.plan(mapped, { true }, { true }).isEmpty())
    }

    @Test fun deadHandlerGivesTheKeyBack() {
        val other = KeyFix.byCode(listOf("KEYCODE_TV_INPUT" to "x/y", "KEYCODE_TV" to stock))
        assertEquals(setOf(170), KeyFix.plan(other, { it == "x/y" }, { true }).keys)   // only the one whose receiver is gone
        val dead = KeyFix.plan(mapped, { false }, { it == "com.google.android.youtube.tv" })
        assertEquals(KeyFix.INPUT, dead[178]?.act); assertEquals(KeyFix.INPUT, dead[170]?.act)
        assertEquals(listOf("com.google.android.youtube.tv"), dead[190]?.pkgs)
    }

    @Test fun anAppKeyWithoutItsAppKeepsDoingNothingAsBefore() {
        val dead = KeyFix.plan(mapped, { false }, { false })
        assertNull(dead[190]); assertEquals(KeyFix.INPUT, dead[178]?.act)
    }

    @Test fun aKeyThatIsNotMappedIsNotTaken() {
        // Netflix 191 here goes to the focused app: nothing is dead about it
        assertFalse(191 in KeyFix.plan(mapped, { false }, { true }))
        assertTrue(KeyFix.plan(emptyMap(), { false }, { true }).isEmpty())
    }

    @Test fun numbersWorkAsWellAsNames() {
        assertEquals(mapOf(284 to "a/b"), KeyFix.byCode(listOf("284" to "a/b", null to "x/y", "KEYCODE_TV" to null)))
    }

    @Test fun theListKeepsTheOthers() {
        val s = KeyFix.SERVICE
        assertEquals(s, KeyFix.list(null, true)); assertEquals(s, KeyFix.list("null", true)); assertEquals("", KeyFix.list(s, false))
        assertEquals("a/b:c/d:$s", KeyFix.list("a/b:c/d", true))
        assertEquals("a/b:c/d:$s", KeyFix.list("a/b:$s:c/d", true))   // once, never twice
        assertEquals("a/b:c/d", KeyFix.list("a/b:$s:c/d", false))
        assertTrue(KeyFix.has("a/b:dev.tvremote.setup/.KeyFixService")); assertFalse(KeyFix.has("a/b:dev.tvremote.setup/.KeyCatch"))   // the system's short form
        assertEquals("a/b:$s", KeyFix.list("a/b:dev.tvremote.setup/.KeyFixService", true))
        assertEquals("dev.tvremote.setup/dev.tvremote.setup.KeyCatch", KeyFix.list("dev.tvremote.setup/dev.tvremote.setup.KeyCatch:$s", false))
    }
}
