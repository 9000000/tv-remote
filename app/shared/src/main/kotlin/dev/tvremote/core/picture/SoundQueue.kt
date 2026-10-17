package dev.tvremote.core.picture
/**
 * When to play each piece of the TV's sound (AudioOut.kt), apart from Android so it can be tried against made-up networks
 * (SoundQueueTest). Frames are at 48 kHz. [target] frames are kept queued: [base] at first, half as many again each time the
 * sound ran dry (4G, Tailscale through a relay: packets come in bursts), up to [max], back down after a calm half minute.
 * Once it ran dry nothing plays until [target] frames are here again: a stall is one clean gap, not the crackle of every
 * packet played the moment it arrives. Kept close to live: a packet that would play well past [target] is dropped, but
 * seldom, so a skip is never heard as a faster song.
 */
class SoundQueue(private val base: Int, private val max: Int) {
    enum class Do { PLAY, HOLD, DROP }

    var target = base; private set
    private var heldFrames = 0
    private var calm = 0L                                             // since when it has not run dry
    private var played = false
    private var lastDrop = 0L

    /** The track's buffer for this [target], two packets more. Android starts a track again (after a long stall) only once
     *  that is full: so [target] plus what arrives meanwhile fills it. Written while it is not full yet, even if the packet
     *  goes past it (the write then waits for room, under 20 ms), or it might never fill. */
    val room get() = target + RATE / 25

    /** [n] frames just decoded, [behind] frames waiting in the track, [now] in ms. PLAY: write what was held, then these. */
    fun next(n: Int, behind: Long, now: Long): Do {
        if (heldFrames == 0 && behind <= 0) {                         // ran dry (or not started yet)
            if (played) target = minOf(target * 3 / 2, max)
            calm = now
        } else if (heldFrames == 0) {
            if (target > base && now - calm > 30000) { target = maxOf(base, target * 4 / 5); calm = now }
            // one packet at most every half second: dropped one after another, the music would sound sped up (a full
            // track only makes the write wait for room, nothing is lost)
            if (behind >= room + RATE / 10 && now - lastDrop >= 500) { lastDrop = now; return Do.DROP }
            return Do.PLAY
        }
        heldFrames += n
        if (heldFrames < target) return Do.HOLD
        heldFrames = 0; played = true
        return Do.PLAY
    }

    companion object {
        const val RATE = 48000
    }
}
