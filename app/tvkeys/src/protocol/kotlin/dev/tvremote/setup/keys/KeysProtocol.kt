package dev.tvremote.setup.keys

/**
 * What TV Setup and its add-on TV Setup Keys (`app/tvkeys`) agree on; compiled into both. The add-on holds the accessibility services
 * and does nothing but hand a key to TV Setup, which decides (KeyFix, HoldKeys) and answers at once: eat it or pass it.
 */
object KeysProtocol {
    const val ADDON = "dev.tvremote.setup.keys"
    const val SETUP = "dev.tvremote.setup"

    /** TV Setup's bound service the add-on talks to; guarded by [PERMISSION], which only an app with TV Setup's key can hold. */
    const val BRIDGE = "dev.tvremote.setup.KeysBridge"
    const val PERMISSION = "dev.tvremote.setup.permission.KEYS"
    const val DESCRIPTOR = "dev.tvremote.setup.keys.Bridge"

    /** In: key code, action, repeat count, down time, event time. Out: 1 = the key is taken (eat it), 0 = pass it on. */
    const val TX_KEY = 1

    /**
     * The only keys the add-on asks about; any other passes with no call at all (every key of a filtering service waits for its
     * answer). A superset of what TV Setup may ever take: the stock launcher's keys (KeyFix.RULES), the colour keys the person may
     * give an app (KeyFix.COLOUR) and the keys of "hold to use Input / Settings" (HoldKeys.KEYS) and the remote's mic button (MicVoice.KEYS: SEARCH, ASSIST, VOICE_ASSIST); a test in :tv keeps it so.
     */
    val INTEREST: Set<Int> = setOf(170, 178, 176, 190, 191, 192, 195, 284, 183, 184, 185, 186, 84, 219, 231) + (243..251)
}
