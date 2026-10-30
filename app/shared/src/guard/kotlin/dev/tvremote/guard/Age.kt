package dev.tvremote.guard

/**
 * Whether something stamped at [at] (a file's lastModified, a saved time) is still younger than [ttl]. A stamp AHEAD of now is
 * not young: cheap TV boxes boot with the clock a year ahead and get it corrected seconds later, so what they saved in between
 * would otherwise look fresh until that year came (a list, a guide or a catalog never asked for again, no error anywhere).
 * Lives in the guard source set only because every app compiles it.
 */
object Age {
    fun fresh(at: Long, ttl: Long, now: Long = System.currentTimeMillis()): Boolean = now - at in 0 until ttl
}
