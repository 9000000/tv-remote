package dev.tvremote.home.keys

import android.content.Context
import android.os.Bundle
import dev.tvremote.home.Target
import dev.tvremote.home.apps.App
import dev.tvremote.home.apps.Apps
import dev.tvremote.home.apps.Pins
import dev.tvremote.home.apps.Tiles

/**
 * Opens and describes a [Target] (an app, a launcher shortcut, a live tile) for the screens of TV Home: the key map, the grid, the
 * picker. TV Setup opens the same strings for a key pressed outside Home and for the power-on app (an app by itself, anything else by
 * handing it to [OpenActivity] here, because only the home screen may start a shortcut and read a tile's state).
 */
object Targets {
    private val bg = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "home-target").apply { isDaemon = true } }

    /** Starts [t]; false when it cannot be (the app is gone, the shortcut was taken back, this is not the home screen). A tile is started in the background: true then. */
    fun open(c: Context, t: String, o: Bundle? = null): Boolean = when (Target.kind(t)) {
        Target.Kind.APP -> Apps.launch(c, t, o)
        Target.Kind.PIN -> Pins.start(c, t, o)
        Target.Kind.TILE -> { val app = c.applicationContext; bg.execute { Tiles.press(app, t) { } }; true }
        null -> false
    }

    /** Whether [t] can be opened now, for the setting's words (an uninstalled app, a vanished shortcut). */
    fun there(c: Context, t: String): Boolean = when (Target.kind(t)) {
        Target.Kind.APP -> Apps.one(c, t) != null
        Target.Kind.PIN -> Pins.find(c, t) != null
        Target.Kind.TILE -> true
        null -> false
    }

    /** The tile to draw for [t] (name and picture), or null when it is not there. Pictures are read here: call from a thread of its own. */
    fun app(c: Context, t: String): App? = when (Target.kind(t)) {
        Target.Kind.APP -> Apps.one(c, t)?.also { it.load(c) }
        Target.Kind.PIN -> Pins.app(c, t)
        Target.Kind.TILE -> App(t, Tiles.title(c, t), null)
        null -> null
    }

    /** Its name only, cheap: for a row of words. */
    fun label(c: Context, t: String): String = when (Target.kind(t)) {
        Target.Kind.APP -> Apps.one(c, t)?.label ?: t
        Target.Kind.PIN -> Pins.find(c, t)?.let { (it.shortLabel ?: it.longLabel ?: it.id).toString() } ?: Target.id(t)
        Target.Kind.TILE -> Tiles.title(c, t)
        null -> "—"
    }
}
