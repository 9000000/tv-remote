package dev.tvremote.phone

import dev.tvremote.core.scenes.Scenes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** assets/ir/codes.json (dev/ir_codes.py): every profile parses, every key is one of ours, every code can be sent. */
class IrCodesTest {
    private val text = File("src/main/assets/ir/codes.json").readText()
    private val all = IrCodes.parse(text)

    @Test fun theFileHoldsProfilesOfTheBrandsThatMatter() {
        val brands = all.map { it.brand }.toSet()
        for (b in listOf("Samsung", "LG", "Sony", "TCL", "Philips", "Panasonic", "Sharp", "Toshiba", "Hisense", "Android TV box", "Mi Box")) assertTrue(b, b in brands)
        assertTrue(all.size in 30..200)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertTrue(text.length < 120_000)                               // a few KB per brand
    }

    @Test fun everyKeyIsOneOfOursAndOneOfTheScenesKeys() {
        for (p in all) for (k in p.keys.keys) {
            assertTrue("${p.id}: $k", k in IrCodes.KEYS)
            assertTrue("$k is not in Scenes.KEYS", k in Scenes.KEYS)
        }
    }

    @Test fun everyProfileCanPowerTheTvAndEveryCodeCanBeSent() {
        for (p in all) {
            assertNotNull("${p.id} has no POWER", p.keys["POWER"])
            for ((k, c) in p.keys) assertNotNull("${p.id} $k: $c", Ir.encode(c))
        }
    }

    @Test fun allTheProtocolsAreUsed() {
        val used = all.flatMap { it.keys.values }.map { Ir.protocol(it) }.toSet()
        for (n in listOf("NEC", "NECext", "Samsung32", "SIRC", "RC5", "RC6", "RAW")) assertTrue(n, n in used)
    }

    @Test fun distinctGroupsProfilesThatShareACode() {
        val d = IrCodes.distinct(all, "POWER")
        assertTrue(d.size < all.size)
        assertEquals(all.size, d.sumOf { it.second })
    }
}
