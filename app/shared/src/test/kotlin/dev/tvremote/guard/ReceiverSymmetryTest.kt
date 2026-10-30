package dev.tvremote.guard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * unregisterReceiver throws "Receiver not registered" when the matching register never ran (an earlier line of onResume threw, the
 * system dropped it, a second stop). Every call in the apps sits inside a try, as the census crash 630851a6 shows what it costs.
 */
class ReceiverSymmetryTest {
    @Test fun everyUnregisterReceiverIsInATry() {
        val apps = File("..").listFiles { f -> f.isDirectory }.orEmpty()
        val bare = apps.flatMap { File(it, "src").walkTopDown().filter { f -> f.extension == "kt" && "/test/" !in f.path && "/build/" !in f.path }.toList() }
            .flatMap { f -> f.readLines().withIndex().filter { (_, l) -> "unregisterReceiver(" in l && "try" !in l }.map { "${f.name}:${it.index + 1}" } }
        assertEquals("wrap in try { } catch (e: Exception) { }", emptyList<String>(), bare)
    }
}
