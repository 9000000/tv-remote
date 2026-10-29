package dev.tvremote.channels

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.net.Uri
import android.os.Build

/**
 * This app in Android TV's shared list of rows (TvProvider, 8.0+): the favourites as a channel of its own ("preview channel") and the
 * last channels watched in "Play Next", the way VTV Go, YouTube and the rest put theirs. Any launcher can show them: the TV's own,
 * and TV Home, which reads every app's rows (READ_TV_LISTINGS) and tested its row on this one (the user, 2026-10-26).
 * Best effort and off the main thread: a TV without TvProvider (some boxes) simply has no rows.
 */
object HomeRows {
    private const val PREFS = "home_rows"
    private const val PLAY_NEXT = 3                                  // the last few channels watched, newest first
    private const val FAVS = 30

    private fun ok() = Build.VERSION.SDK_INT >= 26

    private fun play(c: Context, id: String) = Intent(Intent.ACTION_VIEW, Pins.link(id)).setClass(c, PlayActivity::class.java).toUri(Intent.URI_INTENT_SCHEME)

    /** The favourites written again as the channel's programmes (an empty list leaves the channel empty: launchers hide it). */
    fun favourites(c: Context) {
        if (!ok()) return
        val app = c.applicationContext
        Thread({ try { writeFavs(app) } catch (e: Exception) { } }, "home-rows").start()
    }

    /** [ch] first in Play Next; the oldest of ours beyond [PLAY_NEXT] go. */
    fun watched(c: Context, ch: Channel) {
        if (!ok()) return
        val app = c.applicationContext
        Thread({ try { writeNext(app, ch) } catch (e: Exception) { } }, "home-rows").start()
    }

    @android.annotation.TargetApi(26)   // callers return early unless [ok]
    private fun channel(c: Context): Long {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = p.getLong("channel", -1)
        if (id >= 0) c.contentResolver.query(TvContract.buildChannelUri(id), arrayOf(TvContract.Channels._ID), null, null, null)?.use { if (it.moveToFirst()) return id }
        val v = ContentValues().apply {
            put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_PREVIEW)
            put(TvContract.Channels.COLUMN_DISPLAY_NAME, c.getString(R.string.sc_favs_long))
            put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI, Intent(c, BrowseActivity::class.java).toUri(Intent.URI_INTENT_SCHEME))
            put(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID, "favs")
        }
        val made = ContentUris.parseId(c.contentResolver.insert(TvContract.Channels.CONTENT_URI, v) ?: return -1)
        BitmapFactory.decodeResource(c.resources, R.drawable.icon)?.let { logo ->   // the row's icon on the TV's own launcher
            try { c.contentResolver.openOutputStream(TvContract.buildChannelLogoUri(made))?.use { logo.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (e: Exception) { }
        }
        try { TvContract.requestChannelBrowsable(c, made) } catch (e: Exception) { }   // the TV's launcher may ask the person once; TV Home shows it either way
        p.edit().putLong("channel", made).apply()
        return made
    }

    @android.annotation.TargetApi(26)   // callers return early unless [ok]
    private fun writeFavs(c: Context) {
        val favs = Store.favourites(c).distinctBy { it.id }.take(FAVS)
        val id = channel(c).takeIf { it >= 0 } ?: return
        c.contentResolver.delete(TvContract.buildPreviewProgramsUriForChannel(id), null, null)
        val rows = favs.mapIndexed { i, ch ->
            ContentValues().apply {
                put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, id)
                put(TvContract.PreviewPrograms.COLUMN_TYPE, TvContract.PreviewPrograms.TYPE_CHANNEL)
                put(TvContract.PreviewPrograms.COLUMN_TITLE, ch.name)
                put(TvContract.PreviewPrograms.COLUMN_INTENT_URI, play(c, ch.id))
                put(TvContract.PreviewPrograms.COLUMN_INTERNAL_PROVIDER_ID, ch.id)
                put(TvContract.PreviewPrograms.COLUMN_WEIGHT, FAVS - i)   // the order the person gave them
                put(TvContract.PreviewPrograms.COLUMN_LIVE, 1)
                logo(ch)?.let { put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, it); put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, TvContract.PreviewPrograms.ASPECT_RATIO_16_9) }
            }
        }
        if (rows.isNotEmpty()) c.contentResolver.bulkInsert(TvContract.PreviewPrograms.CONTENT_URI, rows.toTypedArray())
    }

    @android.annotation.TargetApi(26)   // callers return early unless [ok]
    private fun writeNext(c: Context, ch: Channel) {
        val r = c.contentResolver
        val ours = ArrayList<Pair<Long, String>>()                   // only this app's rows come back: TvProvider filters by caller
        r.query(TvContract.WatchNextPrograms.CONTENT_URI, arrayOf(TvContract.WatchNextPrograms._ID, TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID),
            null, null, TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC")?.use { while (it.moveToNext()) ours.add(it.getLong(0) to (it.getString(1) ?: "")) }
        val keep = ours.filter { it.second != ch.id }.take(PLAY_NEXT - 1).map { it.first }.toSet()
        for ((id, _) in ours) if (id !in keep) r.delete(TvContract.buildWatchNextProgramUri(id), null, null)
        r.insert(TvContract.WatchNextPrograms.CONTENT_URI, ContentValues().apply {
            put(TvContract.WatchNextPrograms.COLUMN_TYPE, TvContract.WatchNextPrograms.TYPE_CHANNEL)
            put(TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            put(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, System.currentTimeMillis())
            put(TvContract.WatchNextPrograms.COLUMN_TITLE, ch.name)
            put(TvContract.WatchNextPrograms.COLUMN_INTENT_URI, play(c, ch.id))
            put(TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, ch.id)
            put(TvContract.WatchNextPrograms.COLUMN_LIVE, 1)
            logo(ch)?.let { put(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI, it); put(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, TvContract.WatchNextPrograms.ASPECT_RATIO_16_9) }
        })
    }

    private fun logo(ch: Channel): String? = ch.logo.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { Uri.parse(it).toString() }
}
