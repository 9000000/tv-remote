package dev.tvremote.core

import android.os.Build
import dev.tvremote.guard.Guard
import org.json.JSONObject

/** The Android part of Census's crash reports (the computer app compiles Census without it): the phone's facts, and the error guard's hook. */
object CensusAndroid {
    /** Phone app and TV Setup call it where they start Census. Errors the guard catches go to [Census.crash]: same switch, same server. */
    fun use() {
        Census.device = {
            JSONObject().put("brand", Build.BRAND).put("model", Build.MODEL).put("device", Build.DEVICE).put("board", Build.BOARD)
                .put("hw", Build.HARDWARE).put("android", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT)
        }
        Guard.crash = { kind, msg, stack, done -> Census.crash("kotlin", kind, msg, stack, done) }
    }
}
