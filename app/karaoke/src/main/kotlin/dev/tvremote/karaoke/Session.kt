package dev.tvremote.karaoke

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.security.SecureRandom

/**
 * The one shared state: the queue, pause, the API key, the phones' server. The phones' messages (server threads) and the TV's keys
 * (main thread) both end up here; every change is saved, pushed to every phone, and shown by [onChange] on the main thread.
 */
class Session private constructor(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("karaoke", Context.MODE_PRIVATE)
    private val ui = Handler(Looper.getMainLooper())
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val queue = KaraokeQueue()
    val searcher = Searcher()
    val token: String = prefs.getString("token", null) ?: ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }.also { prefs.edit().putString("token", it).apply() }
    @Volatile var paused = false; private set
    @Volatile var onChange: (() -> Unit)? = null
    @Volatile var onNotice: ((String, String) -> Unit)? = null     // (code, song title), on the main thread
    @Volatile var lastSource = ""                                  // "api" / "web": where the last search came from
    private var server: KaraokeServer? = null
    private var users = 0

    init { prefs.getString("queue", null)?.let { queue.load(it) } }

    // ---- the API key: stored in the app's prefs only, never sent anywhere but to Google, never logged ----
    var apiKey: String
        get() = prefs.getString("apikey", "") ?: ""
        set(v) { prefs.edit().putString("apikey", v.trim().take(100)).apply(); changed() }

    fun statusJson(): JSONObject = JSONObject().put("hasKey", apiKey.isNotEmpty()).put("source", lastSource).put("quota", searcher.quotaHits)

    fun search(q: String): JSONObject {
        val r = searcher.search(q, apiKey); lastSource = r.source
        return JSONObject().put("ok", !r.error).put("source", r.source).put("note", r.note).put("quota", searcher.quotaHits)
            .put("songs", org.json.JSONArray().also { a -> r.songs.forEach { a.put(JSONObject().put("vid", it.vid).put("title", it.title).put("thumb", it.thumb).put("channel", it.channel).put("len", it.length)) } })
    }

    // ---- what phones and the TV do ----
    fun add(song: Song, by: String, cid: String): Boolean = synchronized(this) {
        val started = queue.now == null
        if (queue.add(song, by, cid) == null) return false
        if (started) paused = false
        changed(); true
    }

    fun remove(qid: Int, cid: String, force: Boolean = false) = synchronized(this) { if (queue.remove(qid, cid, force)) changed() }
    fun move(qid: Int, dir: Int, cid: String, force: Boolean = false) = synchronized(this) { if (queue.move(qid, dir, cid, force)) changed() }
    fun skip() = synchronized(this) { if (queue.now != null) { queue.advance(); paused = false; changed() } }
    fun replay() = synchronized(this) { if (queue.replay()) { paused = false; changed() } }
    fun pause(on: Boolean) = synchronized(this) { if (queue.now != null && paused != on) { paused = on; changed() } }
    fun togglePause() = pause(!paused)

    /** The player reached the end of the song. */
    fun ended(qid: Int) = synchronized(this) { if (queue.now?.qid == qid) { queue.advance(); paused = false; changed() } }

    /** The player could not play it (embedding refused, removed, no network): on to the next, and the phones are told. */
    fun failed(qid: Int, code: String) = synchronized(this) {
        val e = queue.now ?: return
        if (e.qid != qid) return
        queue.advance(); paused = false
        server?.broadcast(JSONObject().put("t", "notice").put("code", code).put("title", e.song.title).put("by", e.by).toString())
        ui.post { onNotice?.invoke(code, e.song.title) }
        changed()
    }

    fun volume(dir: Int) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (cur + dir.coerceIn(-1, 1)).coerceIn(0, max), 0)
        changed()
    }

    fun volPercent(): Int = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).let { if (it <= 0) 0 else audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / it }

    fun stateJson(): String = synchronized(this) {
        queue.json().put("t", "state").put("paused", paused).put("vol", volPercent()).put("tick", queue.replayTick)
            .put("users", server?.count() ?: 0).put("hasKey", apiKey.isNotEmpty()).toString()
    }

    fun changed() {
        prefs.edit().putString("queue", queue.save()).apply()
        server?.broadcast(stateJson())
        ui.post { onChange?.invoke() }
    }

    fun url(): String = "http://${KaraokeServer.localIp()}:${server?.port ?: KaraokeServer.PORT}/?k=$token"

    @Synchronized fun open() { if (users++ == 0) server = KaraokeServer(ctx, this).also { it.start() } }
    @Synchronized fun close() { if (--users <= 0) { server?.stop(); server = null; users = 0 } }

    companion object {
        @Volatile private var inst: Session? = null
        fun get(c: Context): Session = inst ?: synchronized(this) { inst ?: Session(c.applicationContext).also { inst = it } }
    }
}
