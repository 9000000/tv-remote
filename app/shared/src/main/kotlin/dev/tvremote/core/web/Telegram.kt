package dev.tvremote.core.web
import dev.tvremote.core.WireLog
import dev.tvremote.core.Words
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

/**
 * The TV from a Telegram chat: the owner makes a bot with @BotFather and pastes its token in Settings; TV Setup then asks
 * Telegram for the bot's messages (long polling: `getUpdates` held open for 50 s) and answers them. No webhook and no open port,
 * so it works behind any router and costs nothing when switched off. Not Hook.kt's job (that one is a link inside the home
 * network); this one goes out, to api.telegram.org only.
 *
 * Who may command it: a chat that sent `/pair 123456` with the code the TV showed (one at a time, five minutes, five wrong
 * guesses cancel it and lock pairing, as [Hook] does for tokens). Anyone else is ignored silently, so a stranger who finds the bot
 * learns nothing, not even that it listens. A paired chat has a scope like a shared browser (Scope.kt in TV Setup): REMOTE by
 * default, FULL when the owner raises it.
 *
 * Nothing here presses a key: every command becomes the request the page would have sent and goes to the core's own router
 * ([route], with the chat's scope, so a REMOTE chat is refused exactly where a REMOTE browser is), through [Hook.perform] for the
 * actions the hook has too.
 */
class Telegram(
    private val store: Store,
    private val api: Api,
    private val hook: Hook,
    /** The core's router, called with a request built here and the scope it may use ("remote" / "full"). */
    private val route: (HttpServer.Request, String) -> HttpServer.Response,
    /** What /api/state says. */
    private val state: () -> JSONObject,
    /** The apps on the TV's home screen as (package, label), to open one by its name. */
    private val apps: () -> List<Pair<String, String>>,
    /** Shows the pairing code on the TV's own screen (the Notify card). */
    private val showCode: (String) -> Unit = { },
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: java.util.Random = SecureRandom(),
    /** false in tests: they call [step] themselves. */
    private val background: Boolean = true,
) {
    /** Where the switch, the token and the paired chats live (TV Setup's prefs). The token is never written anywhere else. */
    interface Store {
        var enabled: Boolean
        var token: String
        var offset: Long                    // the next update to ask for: kept, so a restart never runs an old command again
        var lang: String                    // "" = the TV's language
        var bot: String                     // the bot's @name, from getMe
        var chats: String                   // JSON array of the paired chats
    }

    /** One call to Telegram. [status] is the HTTP status (0 never: a network failure throws), [body] Telegram's JSON. */
    class Reply(val status: Int, val body: JSONObject) {
        val ok get() = status == 200 && body.optBoolean("ok")
        val retryAfterMs: Long get() = body.optJSONObject("parameters")?.optLong("retry_after", 0)?.times(1000) ?: 0L
    }

    interface Api {
        /** `https://api.telegram.org/bot<token>/<method>`; [timeoutS]: how long to wait for the answer (getUpdates holds 50 s). */
        @Throws(IOException::class) fun call(token: String, method: String, params: JSONObject, timeoutS: Int = 15): Reply
        @Throws(IOException::class) fun photo(token: String, chat: Long, png: ByteArray, caption: String): Reply
        /** Cuts the call in flight (the switch went off, the token changed). */
        fun abort()
    }

    class Chat(val id: Long, val name: String, val scope: String, val at: Long)
    class Cmd(val name: String, val arg: String)

    @Volatile var status = "off"                    // off | connecting | ok | badtoken | error
        private set
    @Volatile var lastError = ""
        private set

    private var code: String? = null
    private var codeUntil = 0L
    private var wrong = 0
    private var lockedUntil = 0L
    private val limit = Limiter(MIN_GAP)
    private val limitTap = Limiter(MIN_GAP_TAP)

    // ---------------------------------------------------------------- the loop
    private val wake = Object()
    private var worker: Thread? = null
    @Volatile private var stopping = false
    @Volatile private var readyFor = ""                       // token + language the webhook was cleared and the commands set for
    private var backoff = 0L
    @Volatile private var quiet = false
    @Volatile private var kicked = false

    /** Starts the loop when it is wanted and stops it when not; call after the switch, the token or the language changed. */
    fun sync() {
        synchronized(wake) {
            if (store.enabled && store.token.isNotEmpty()) {
                if (background && worker?.isAlive != true) {
                    stopping = false
                    worker = Thread({ loop() }, "telegram").apply { isDaemon = true; start() }
                }
            } else { status = "off" }
            readyFor = ""                           // a new token or language: clear the webhook and set the commands again
            kicked = true
            wake.notifyAll()
        }
        api.abort()
    }

    /** The service ends: no more calls. */
    fun stop() {
        synchronized(wake) { stopping = true; wake.notifyAll() }
        api.abort()
    }

    private fun loop() {
        while (!stopping) {
            val t0 = System.nanoTime()
            quiet = false
            var w = try { step() } catch (e: Exception) { fail("loop", e.message ?: e.javaClass.simpleName) }
            if (kicked) { kicked = false; if (status == "error") { status = "connecting"; lastError = ""; backoff = 0L }; w = 0 }   // our own abort ended the call: not Telegram's fault
            if (w == 0L && quiet && System.nanoTime() - t0 < 500_000_000L) w = 1000   // an answer with nothing in it, at once: a proxy that does not hold the call must not make this spin
            synchronized(wake) {
                if (stopping) return
                if (w < 0) { wake.wait() } else if (w > 0) wake.wait(w)
            }
        }
    }

    /**
     * One round: ask Telegram for updates and answer them. Returns the milliseconds to wait before the next round (0: at once;
     * the long poll itself was the wait), or -1 to sleep until [sync] (switched off, or the token was refused).
     */
    fun step(): Long {
        val token = store.token
        if (!store.enabled || token.isEmpty()) { status = "off"; return -1 }
        try {
            val key = token + "/" + lang()
            if (readyFor != key) {
                if (status != "ok") status = "connecting"
                val me = api.call(token, "getMe", JSONObject())
                if (refused(me)) return -1
                if (!me.ok) return fail("getMe", describe(me), me)
                me.body.optJSONObject("result")?.optString("username")?.takeIf { it.isNotEmpty() }?.let { store.bot = "@$it" }
                val dw = api.call(token, "deleteWebhook", JSONObject().put("drop_pending_updates", false))   // a webhook would stop getUpdates
                if (!dw.ok) return fail("deleteWebhook", describe(dw), dw)
                api.call(token, "setMyCommands", JSONObject().put("commands", commandList()))   // best effort: the bot works without the "/" menu
                readyFor = key
            }
            if (store.offset <= 0L) {                // first start: what was sent while nobody listened is not for now
                val last = api.call(token, "getUpdates", JSONObject().put("offset", -1).put("timeout", 0))
                if (refused(last)) return -1
                if (!last.ok) return fail("getUpdates", describe(last), last)
                val a = last.body.optJSONArray("result")
                store.offset = (if (a != null && a.length() > 0) a.getJSONObject(a.length() - 1).optLong("update_id") + 1 else 1L)
            }
            val p = JSONObject().put("timeout", POLL_S).put("offset", store.offset).put("allowed_updates", JSONArray(ALLOWED))
            val r = api.call(token, "getUpdates", p, POLL_S + 15)
            if (refused(r)) return -1
            if (r.status == 409) { readyFor = ""; return fail("getUpdates", "another listener (webhook?)", r) }
            if (!r.ok) return fail("getUpdates", describe(r), r)
            status = "ok"; lastError = ""; backoff = 0
            val got = r.body.optJSONArray("result") ?: JSONArray()
            quiet = got.length() == 0
            handle(got)
            return 0
        } catch (e: IOException) {
            if (stopping) return -1
            return fail("poll", clean(e.message ?: e.javaClass.simpleName))
        }
    }

    /** 401 / 404: Telegram does not know this token. Asking again would only repeat it: wait for a new one. */
    private fun refused(r: Reply): Boolean {
        if (r.status != 401 && r.status != 404) return false
        status = "badtoken"; lastError = describe(r)
        WireLog.all.add("·", "telegram: token refused (${r.status})")
        return true
    }

    private fun fail(what: String, why: String, r: Reply? = null): Long {
        status = "error"; lastError = clean(why)
        backoff = maxOf(next(backoff), r?.retryAfterMs ?: 0L)
        WireLog.all.add("·", "telegram $what: ${lastError} (retry in ${backoff / 1000}s)")
        return backoff
    }

    private fun describe(r: Reply) = "${r.status} " + r.body.optString("description")

    // ---------------------------------------------------------------- updates
    /**
     * [updates] as getUpdates gave them. The offset moves past all of them and is kept BEFORE any is answered: a command that
     * crashes the loop (or a restart in the middle) must not come back and run twice; "/off" twice is the worse fault.
     */
    fun handle(updates: JSONArray) {
        var top = store.offset
        for (i in 0 until updates.length()) top = maxOf(top, updates.getJSONObject(i).optLong("update_id") + 1)
        if (top != store.offset) store.offset = top
        for (i in 0 until updates.length()) {
            try { onUpdate(updates.getJSONObject(i)) } catch (e: Exception) { WireLog.all.add("·", "telegram update: " + clean(e.message ?: e.javaClass.simpleName)) }
        }
    }

    fun onUpdate(u: JSONObject) {
        u.optJSONObject("callback_query")?.let { return onTap(it) }
        val m = u.optJSONObject("message") ?: return
        val text = m.optString("text")
        if (text.isEmpty()) return
        if (clock() / 1000 - m.optLong("date") > STALE_S) return      // a command that waited in Telegram's queue for minutes is not for now
        val chat = m.optJSONObject("chat") ?: return
        val id = chat.optLong("id")
        val cmd = parse(text, store.bot) ?: return onText(id, chat)
        val known = chats().firstOrNull { it.id == id }
        if (known == null) { if (cmd.name == "pair" && pair(id, nameOf(chat), cmd.arg)) send(id, w("telegram.paired", "scope" to REMOTE) + "\n\n" + help(REMOTE)); return }   // anyone else: nothing, not even an answer
        if (!limit.ok(id, clock())) return
        WireLog.all.add("←", "telegram ${known.name}: /${cmd.name} ${if (cmd.name == "say") "…(${cmd.arg.length})" else cmd.arg}".trim())
        command(known, cmd)
    }

    /** A plain sentence in a private chat that is paired: the way to the commands. Groups and strangers: nothing. */
    private fun onText(id: Long, chat: JSONObject) {
        if (chat.optString("type") != "private" || chats().none { it.id == id } || !limit.ok(id, clock())) return
        send(id, w("telegram.plain"))
    }

    private fun onTap(cb: JSONObject) {
        val data = cb.optString("data")
        val id = cb.optJSONObject("message")?.optJSONObject("chat")?.optLong("id") ?: return
        val known = chats().firstOrNull { it.id == id } ?: return
        val qid = cb.optString("id")
        if (!data.startsWith("k:") || !limitTap.ok(id, clock())) { answer(qid, ""); return }
        val key = data.substring(2)
        if (key !in PAD) { answer(qid, ""); return }
        WireLog.all.add("←", "telegram ${known.name}: tap $key")
        val out = perform(known, "key", key)
        answer(qid, if (out.status in 200..299) "" else say(out, known))
    }

    // ---------------------------------------------------------------- pairing
    /** A fresh code (the old one dies), also on the TV's screen. Asking for it is the owner's act: it lifts a lock too. */
    @Synchronized fun newCode(): String {
        val c = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000))
        code = c; codeUntil = clock() + CODE_MS; wrong = 0; lockedUntil = 0
        try { showCode(c) } catch (e: Exception) { }
        return c
    }

    @Synchronized fun cancelCode() { code = null }
    @Synchronized fun codeLeftS(): Int = if (code != null && clock() < codeUntil) ((codeUntil - clock() + 999) / 1000).toInt() else 0
    @Synchronized fun lockedLeftS(): Int = if (clock() < lockedUntil) ((lockedUntil - clock() + 999) / 1000).toInt() else 0
    @Synchronized private fun codeNow(): String? = if (clock() < codeUntil) code else null

    /** `/pair 123456` from a chat nobody has let in. true when it was let in. */
    @Synchronized fun pair(id: Long, name: String, given: String): Boolean {
        val now = clock()
        if (now < lockedUntil) return false
        val want = codeNow() ?: return false
        val got = given.replace(Regex("\\D"), "")
        if (!MessageDigest.isEqual(want.toByteArray(), got.toByteArray())) {
            if (++wrong >= MAX_WRONG) { code = null; wrong = 0; lockedUntil = now + LOCK_MS; WireLog.all.add("·", "telegram: pairing locked after $MAX_WRONG wrong codes") }
            return false
        }
        code = null; wrong = 0
        val list = chats().filter { it.id != id } + Chat(id, name, REMOTE, now)
        save(list)
        WireLog.all.add("·", "telegram: paired $name (scope $REMOTE)")
        return true
    }

    // ---------------------------------------------------------------- commands
    private fun command(c: Chat, cmd: Cmd) {
        val a = cmd.arg
        when (cmd.name) {
            "start", "help" -> send(c.id, help(c.scope))
            "status" -> send(c.id, statusText())
            "off" -> send(c.id, done(perform(c, "power", "off"), c, w("telegram.off_done")))
            "on" -> send(c.id, done(perform(c, "power", "on"), c, w("telegram.on_done")))
            "vol" -> {
                if (a.isEmpty()) return send(c.id, w("telegram.usage_vol"))
                // a level goes through a scene step, which a REMOTE browser may not send; the level is checked 0..100 and is no more than
                // the volume keys can do, so the chat is let do exactly this
                val n = a.toIntOrNull()
                send(c.id, done(perform(c, "volume", a, if (n != null) FULL else c.scope), c, w("telegram.vol_done", "v" to a)))
            }
            "key" -> {
                if (a.isEmpty()) return send(c.id, w("telegram.usage_key"))
                send(c.id, done(perform(c, "key", a), c, w("telegram.key_done", "k" to a.uppercase(Locale.ROOT))))
            }
            "open" -> open(c, a)
            "scene" -> {
                if (a.isEmpty()) return send(c.id, w("telegram.usage_scene"))
                val out = perform(c, "scene", a)
                if (out.status == 404) {
                    val names = JSONObject(String(out.body)).optJSONArray("scenes")
                    val list = (0 until (names?.length() ?: 0)).joinToString(", ") { names!!.optString(it) }
                    return send(c.id, if (list.isEmpty()) w("telegram.no_scenes") else w("telegram.no_scene", "list" to list))
                }
                send(c.id, done(out, c, w("telegram.scene_done", "name" to a)))
            }
            "timer" -> {
                val n = a.toIntOrNull()?.takeIf { it in 0..720 } ?: return send(c.id, w("telegram.usage_timer"))
                val out = post(c, "/api/rules", JSONObject().put("do", "sleep").put("minutes", n))
                send(c.id, done(out, c, if (n == 0) w("telegram.timer_off") else w("telegram.timer_set", "n" to n)))
            }
            "say" -> {
                if (a.isEmpty()) return send(c.id, w("telegram.usage_say"))
                send(c.id, done(post(c, "/api/notify", JSONObject().put("title", "Telegram · " + c.name.take(30)).put("text", a)), c, w("telegram.say_done")))
            }
            "shot" -> shot(c)
            "remote" -> send(c.id, w("telegram.remote_title"), keypad())
            "pair" -> send(c.id, w("telegram.already"))
            else -> send(c.id, w("telegram.unknown", "c" to cmd.name))
        }
    }

    private fun open(c: Chat, a: String) {
        if (a.isEmpty()) return send(c.id, w("telegram.usage_open"))
        val list = apps()
        val q = a.trim().lowercase(Locale.ROOT)
        val hit = list.firstOrNull { it.second.lowercase(Locale.ROOT) == q || it.first.lowercase(Locale.ROOT) == q }
            ?: list.filter { it.second.lowercase(Locale.ROOT).contains(q) }.singleOrNull()
        val pkg = hit?.first ?: if (c.scope == FULL && PKG.matches(a.trim())) a.trim() else null   // a FULL chat may name any package; REMOTE only what the home screen shows
        if (pkg == null) {
            val near = list.filter { it.second.lowercase(Locale.ROOT).contains(q) }
            return send(c.id, if (near.size > 1) w("telegram.open_many", "list" to near.joinToString(", ") { it.second }) else w("telegram.open_none"))
        }
        send(c.id, done(perform(c, "app", pkg), c, w("telegram.open_done", "app" to (hit?.second ?: pkg))))
    }

    private fun shot(c: Chat) {
        val out = get(c, "/api/screen")
        if (out.status !in 200..299 || out.body.isEmpty()) return send(c.id, if (out.status == 403) w("telegram.scope_denied") else w("telegram.shot_failed"))
        try {
            val r = api.photo(store.token, c.id, out.body, "")
            if (!r.ok) send(c.id, w("telegram.shot_failed"))
        } catch (e: IOException) { WireLog.all.add("·", "telegram photo: " + clean(e.message ?: "")); }
    }

    private fun statusText(): String {
        val b = hook.brief(try { state() } catch (e: Exception) { JSONObject() })
        val on = if (b.isNull("on")) w("telegram.st_unknown") else if (b.optBoolean("on")) w("telegram.st_on") else w("telegram.st_off")
        val v = b.optJSONObject("volume")
        val vol = if (v == null) "" else "\n" + w("telegram.st_vol", "v" to v.opt("level"), "max" to v.opt("max")) + (if (v.optBoolean("muted")) " " + w("telegram.st_muted") else "")
        fun str(k: String) = if (b.isNull(k)) "" else b.optString(k)                                   // JSON null reads as the word "null"
        val app = if (str("app").isEmpty()) "" else "\n" + w("telegram.st_app", "app" to str("app_label").ifEmpty { str("app") })
        return w("telegram.st_tv", "on" to on) + vol + app
    }

    private fun help(scope: String) = w("telegram.help", "scope" to scope)

    /** The answer for a finished command: [ok] words when it went through, else why not. */
    private fun done(out: HttpServer.Response, c: Chat, ok: String) = if (out.status in 200..299) ok else say(out, c)

    private fun say(out: HttpServer.Response, c: Chat): String {
        val e = try { JSONObject(String(out.body)).optString("error") } catch (x: Exception) { "" }
        return when {
            out.status == 403 -> w("telegram.scope_denied")
            e.startsWith("adb:") || out.status == 503 -> w("telegram.tv_not_ready")
            e == "unknown key" -> w("telegram.bad_key")
            e.contains("setup:") -> w("telegram.needs_setup")
            e.isNotEmpty() -> "❌ " + e.take(200)
            else -> "❌ ${out.status}"
        }
    }

    // ---------------------------------------------------------------- reaching the TV (in-process, with the chat's scope)
    private fun perform(c: Chat, what: String, value: String, scope: String = c.scope): HttpServer.Response =
        try { hook.perform(what, value) { route(it, scope) } } catch (e: IllegalArgumentException) { HttpServer.Response.error(400, e.message ?: "bad request") }
            .also { WireLog.all.add("→", "telegram $what ${if (what == "text") "…" else value} → ${it.status}") }

    private fun post(c: Chat, path: String, body: JSONObject): HttpServer.Response = hook.send({ route(it, c.scope) }, path, body)
    private fun get(c: Chat, path: String): HttpServer.Response = hook.send({ route(it, c.scope) }, path, JSONObject(), "GET")

    // ---------------------------------------------------------------- talking to Telegram
    private fun send(chat: Long, text: String, markup: JSONObject? = null) {
        val p = JSONObject().put("chat_id", chat).put("text", text.take(4000))
        if (markup != null) p.put("reply_markup", markup)
        try { api.call(store.token, "sendMessage", p) } catch (e: IOException) { WireLog.all.add("·", "telegram send: " + clean(e.message ?: "")) }
    }

    private fun answer(id: String, text: String) {
        try { api.call(store.token, "answerCallbackQuery", JSONObject().put("callback_query_id", id).put("text", text)) }
        catch (e: IOException) { WireLog.all.add("·", "telegram answer: " + clean(e.message ?: "")) }
    }

    private fun keypad(): JSONObject {
        fun b(t: String, k: String) = JSONObject().put("text", t).put("callback_data", "k:$k")
        val rows = listOf(
            listOf(b("🏠", "HOME"), b("⬆️", "UP"), b("↩️", "BACK")),
            listOf(b("⬅️", "LEFT"), b("OK", "OK"), b("➡️", "RIGHT")),
            listOf(b("🔉", "VOL_DOWN"), b("⬇️", "DOWN"), b("🔊", "VOL_UP")),
            listOf(b("🔇", "MUTE")),
        )
        return JSONObject().put("inline_keyboard", JSONArray(rows.map { JSONArray(it) }))
    }

    private fun commandList(): JSONArray = JSONArray(listOf(
        "help" to w("telegram.cmd_help"), "status" to w("telegram.cmd_status"), "off" to w("telegram.cmd_off"), "on" to w("telegram.cmd_on"),
        "vol" to w("telegram.cmd_vol"), "key" to w("telegram.cmd_key"), "open" to w("telegram.cmd_open"), "scene" to w("telegram.cmd_scene"),
        "timer" to w("telegram.cmd_timer"), "say" to w("telegram.cmd_say"), "shot" to w("telegram.cmd_shot"), "remote" to w("telegram.cmd_remote"),
    ).map { JSONObject().put("command", it.first).put("description", it.second) })

    // ---------------------------------------------------------------- words, chats
    private fun lang() = Words.pick(store.lang.ifEmpty { Words.lang })
    private fun w(id: String, vararg a: Pair<String, Any?>) = Words.get(lang(), id, *a)

    fun chats(): List<Chat> = synchronized(this) {
        try {
            val a = JSONArray(store.chats.ifEmpty { "[]" })
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { Chat(it.optLong("id"), it.optString("name"), scopeOf(it.optString("scope")), it.optLong("at")) }
        } catch (e: Exception) { emptyList() }
    }

    private fun save(list: List<Chat>) = synchronized(this) {
        store.chats = JSONArray(list.map { JSONObject().put("id", it.id).put("name", it.name).put("scope", it.scope).put("at", it.at) }).toString()
    }

    fun setScope(id: Long, scope: String): Boolean {
        if (scope !in SCOPES) return false
        val list = chats()
        if (list.none { it.id == id }) return false
        save(list.map { if (it.id == id) Chat(it.id, it.name, scope, it.at) else it })
        return true
    }

    fun forget(id: Long): Boolean { val list = chats(); save(list.filter { it.id != id }); return list.any { it.id == id } }

    private fun nameOf(chat: JSONObject): String =
        chat.optString("title").ifEmpty { listOf(chat.optString("first_name"), chat.optString("last_name")).filter { it.isNotEmpty() }.joinToString(" ") }
            .ifEmpty { chat.optString("username") }.ifEmpty { chat.optLong("id").toString() }.take(40)

    // ---------------------------------------------------------------- the owner's page (/api/telegram; Scope.FULL only, the router checks)
    /** The state for the page: the token is only ever shown masked. */
    fun json(): JSONObject {
        val t = store.token
        val o = JSONObject().put("on", store.enabled && t.isNotEmpty()).put("has_token", t.isNotEmpty()).put("status", status).put("lang", store.lang)
        if (t.isNotEmpty()) o.put("masked", mask(t)).put("bot", store.bot).put("error", lastError)
        val left = codeLeftS()
        if (left > 0) o.put("code", codeNow() ?: "").put("code_left", left)
        o.put("locked", lockedLeftS())
        o.put("chats", JSONArray(chats().map { JSONObject().put("id", it.id).put("name", it.name).put("scope", it.scope).put("at", it.at) }))
        return o
    }

    fun config(r: HttpServer.Request): HttpServer.Response {
        if (r.method == "POST") {
            val d = r.json()
            when (d.optString("do")) {
                "token" -> {
                    val t = d.optString("token").trim()
                    if (!TOKEN.matches(t)) return HttpServer.Response.error(400, "telegram: bad token")
                    val me = try { api.call(t, "getMe", JSONObject(), 10) } catch (e: IOException) { null }   // checked now so a typo is told at once; no network: kept, and tried again
                    if (me != null && (me.status == 401 || me.status == 404)) return HttpServer.Response.error(400, "telegram: bad token")
                    if (t != store.token) { store.chats = "[]"; store.offset = 0 }   // another bot: the old chats never met it
                    store.token = t
                    store.bot = me?.body?.optJSONObject("result")?.optString("username")?.takeIf { it.isNotEmpty() }?.let { "@$it" } ?: ""
                    store.enabled = true
                }
                "on" -> { if (store.token.isEmpty()) return HttpServer.Response.error(400, "no token"); store.enabled = true }
                "off" -> { store.enabled = false; cancelCode() }
                "remove" -> { store.enabled = false; store.token = ""; store.bot = ""; store.chats = "[]"; store.offset = 0; cancelCode() }
                "code" -> { if (store.token.isEmpty() || !store.enabled) return HttpServer.Response.error(400, "off"); newCode() }
                "forget" -> if (!forget(d.optLong("id"))) return HttpServer.Response.error(400, "no such chat")
                "scope" -> if (!setScope(d.optLong("id"), d.optString("scope"))) return HttpServer.Response.error(400, "no such chat")
                "lang" -> store.lang = d.optString("lang").takeIf { it == "vi" || it == "en" } ?: ""
                else -> return HttpServer.Response.error(400, "do: token, on, off, remove, code, forget, scope or lang")
            }
            if (d.optString("do") in setOf("token", "on", "off", "remove", "lang")) sync()   // a code, a scope or a forgotten chat leaves the long poll alone
        }
        return HttpServer.Response.json(json())
    }

    private fun clean(s: String) = WireLog.clean(s).replace(Regex("\\d{6,}:[A-Za-z0-9_-]{20,}"), "…")

    companion object {
        const val REMOTE = "remote"
        const val FULL = "full"
        const val VIEW = "view"                         // /status and /shot only; the router refuses the rest
        private val SCOPES = listOf(FULL, REMOTE, VIEW)
        private fun scopeOf(s: String) = if (s in SCOPES) s else REMOTE
        const val POLL_S = 50
        private const val STALE_S = 120L
        private const val CODE_MS = 5 * 60_000L
        private const val MAX_WRONG = 5
        private const val LOCK_MS = 15 * 60_000L
        private const val MIN_GAP = 1000L                   // one command a second from one chat
        private const val MIN_GAP_TAP = 400L                // a keypad is tapped faster, but not without end
        private val ALLOWED = listOf("message", "callback_query")
        private val PAD = setOf("HOME", "UP", "BACK", "LEFT", "OK", "RIGHT", "VOL_DOWN", "DOWN", "VOL_UP", "MUTE")
        private val PKG = Regex("[A-Za-z0-9_.]{1,200}")
        val TOKEN = Regex("\\d{6,12}:[A-Za-z0-9_-]{30,60}")

        /** 5 s, 10, 20, 40, then 60 s at most; reset by the next good answer. */
        fun next(prev: Long): Long = if (prev <= 0) 5_000L else minOf(prev * 2, 60_000L)

        /** "/vol@MyBot 30" → vol, "30". null: not a command, or one for another bot. */
        fun parse(text: String, bot: String): Cmd? {
            val t = text.trim()
            if (!t.startsWith("/") || t.length < 2) return null
            val head = t.substring(1).substringBefore(' ').substringBefore('\n')
            val arg = t.substring(1 + head.length).trim()
            val name = head.substringBefore('@').lowercase(Locale.ROOT)
            val to = head.substringAfter('@', "")
            if (to.isNotEmpty() && bot.isNotEmpty() && !to.equals(bot.removePrefix("@"), ignoreCase = true)) return null
            if (!name.matches(Regex("[a-z0-9_]{1,32}"))) return null
            return Cmd(name, arg)
        }

        /** "123456789:••••wXyZ": enough to tell which bot, never the secret. */
        fun mask(t: String) = t.substringBefore(':') + ":••••" + t.takeLast(4)
    }

    /** At most one thing per [gap] ms per chat. */
    class Limiter(private val gap: Long) {
        private val last = HashMap<Long, Long>()
        @Synchronized fun ok(key: Long, now: Long): Boolean {
            if (last.size > 512) last.entries.removeAll { now - it.value > 60_000 }
            val before = last[key]
            if (before != null && now - before < gap && now >= before) return false
            last[key] = now
            return true
        }
    }
}
