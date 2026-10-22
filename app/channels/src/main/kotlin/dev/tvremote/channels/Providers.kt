package dev.tvremote.channels

import android.content.Context
import dev.tvremote.update.Ed25519
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Base64

/**
 * Channels from other places than iptv-org, by a list the site keeps: docs/channels/providers.json (dev/sign/sign_providers.py).
 * How to ask a provider is code here, one [Kind] per kind; where to ask it (addresses, headers) and whether to ask at all ("on")
 * is the list, so a provider that moves or turns the app away is fixed or switched off without a new APK. The list is signed
 * with the launcher list's key: whoever takes over the site still cannot point the TVs at streams of their own.
 *
 * A provider's channel is merged into iptv-org's channel of the same name as one more stream (first when "first"), so
 * favourites and links keep working; one iptv-org lacks becomes a channel of its own, id "<slug>.<cc>@<provider>". Its stream
 * URL is a link of ours, "<provider>:<key>", turned into the real one only when played ([resolve]): those carry a token.
 */
object Providers {
    private val KEYS = listOf("de2714846a352ea4e0e5b5dcf2272fee9b10bd2da6ebdea8cff858bac1c3f6ea")   // Launchers.KEYS
    private val BASES = listOf("https://tvremote.pages.dev/channels/", "https://raw.githubusercontent.com/HoangTran0410/tv-remote/main/docs/channels/")
    private const val DAY = 24 * 3600_000L

    class Provider(val id: String, val kind: String, val name: String, val country: String, val first: Boolean, val o: JSONObject) {
        val headers: Map<String, String> = o.optJSONObject("headers")?.let { h -> h.keys().asSequence().associateWith { h.optString(it) } }.orEmpty()
        val skip: Set<String> = o.optJSONArray("skip")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }.orEmpty()
        /** Its key -> the name iptv-org gives the same channel, where the two differ ("vie-channel-htv2-hd" -> "HTV2"). */
        val aliases: Map<String, String> = o.optJSONObject("aliases")?.let { h -> h.keys().asSequence().associateWith { h.optString(it) } }.orEmpty()
        /** The provider's guide ids by channel name, for a provider that only gives the guide (TV360: "VTV3" -> "4"), by [key]. */
        val guides: Map<String, String> = o.optJSONObject("channels")?.let { h -> h.keys().asSequence().associate { key(it) to h.optString(it) } }.orEmpty()
    }

    /**
     * One way of asking: [list] the free channels of a country, [play] the playable URL of one (null: not free / gone),
     * [guide] what one shows on [day] ("2026-10-21") from its guide [id] (null: none, or not asked). A kind may do only some.
     */
    interface Kind {
        fun list(p: Provider, get: (String) -> String?): List<Channel> = emptyList()
        fun play(p: Provider, key: String): Played? = null
        fun guide(p: Provider, id: String, day: String): List<Show>? = null
    }
    class Played(val url: String, val until: Long)

    private val KINDS: Map<String, Kind> = mapOf("vieon" to Vieon, "tv360" to Tv360)

    fun kind(p: Provider): Kind? = KINDS[p.kind]

    fun verified(list: ByteArray, sig: String): Boolean {
        val raw = try { Base64.getDecoder().decode(sig.trim()) } catch (e: IllegalArgumentException) { return false }
        return KEYS.any { k -> Ed25519.verify(ByteArray(32) { k.substring(it * 2, it * 2 + 2).toInt(16).toByte() }, list, raw) }
    }

    /** The providers that are on and whose kind this app knows; a shape that is not ours is no provider, not a crash. */
    fun parse(text: String): List<Provider> = try {
        val o = JSONObject(text)
        if (o.optInt("v") != 1) emptyList() else {
            val a = o.optJSONArray("providers") ?: JSONArray()
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { it.optBoolean("on", true) && it.optString("kind") in KINDS }
                .map { Provider(it.getString("id"), it.getString("kind"), it.optString("name"), it.optString("country").lowercase(), it.optBoolean("first"), it) }
        }
    } catch (e: org.json.JSONException) { emptyList() }

    @Volatile private var mem: Pair<Long, List<Provider>>? = null

    /** The list: the site's (checked, kept a day), else the last one read, else the copy packed in the APK. */
    fun all(c: Context): List<Provider> {
        mem?.let { if (System.currentTimeMillis() - it.first < DAY) return it.second }
        val f = File(c.filesDir, "providers.json")
        var fresh = true
        val text = if (f.exists() && System.currentTimeMillis() - f.lastModified() < DAY) f.readText() else
            fetchSigned()?.also { File(c.filesDir, "providers.json.tmp").apply { writeText(it); renameTo(f) } }
                ?: run { fresh = false; null }
                ?: f.takeIf { it.exists() }?.readText()
                ?: try { c.assets.open("providers.json").bufferedReader().readText() } catch (e: IOException) { "" }
        // not reached: the old or packed list for half an hour, then asked again (a provider switched off reaches TVs the same day)
        return parse(text).also { mem = (System.currentTimeMillis() - if (fresh) 0 else DAY - 30 * 60_000L) to it }
    }

    private fun fetchSigned(): String? {
        for (b in BASES) try {
            val list = Catalog.get(b + "providers.json")?.takeIf { it.isNotEmpty() } ?: continue
            val sig = Catalog.get(b + "providers.json.sig") ?: continue
            if (verified(list.toByteArray(Charsets.UTF_8), sig)) return list
        } catch (e: Exception) {}
        return null
    }

    /** [iptv] (a country's playlist) with the providers' channels of that country merged in. */
    fun merge(c: Context, cc: String, iptv: List<Channel>, fresh: Boolean): List<Channel> {
        val ps = all(c).filter { it.country == cc && it.o.has("list") }
        if (ps.isEmpty()) return iptv
        val byName = iptv.groupBy { key(it.name) }
        val before = HashMap<String, MutableList<Channel>>()             // a provider's stream to put ahead of a channel's own
        val after = ArrayList<Channel>()
        for (p in ps) {
            val got = Catalog.cached(c, "prov-${p.id}-$cc.json", "", fresh) { channelsJson(p) }?.let { fromJson(it) }.orEmpty()
            for (ch in got) {
                val k = ch.url.substringAfter(':')
                if (k in p.skip) continue
                val same = byName[key(p.aliases[k] ?: ch.name)]?.first()
                if (same == null) { after.add(ch); continue }
                val logo = if (p.first) ch.logo.ifEmpty { same.logo } else same.logo.ifEmpty { ch.logo }   // the card shows the first stream's
                val s = ch.copy(id = same.id, name = same.name, logo = logo, group = same.group)
                if (p.first) before.getOrPut(same.id) { ArrayList() }.add(s) else after.add(s)
            }
        }
        val out = ArrayList<Channel>(iptv.size + after.size)
        val done = HashSet<String>()
        for (ch in iptv) { if (done.add(ch.id)) before[ch.id]?.let { out.addAll(it) }; out.add(ch) }
        val known = iptv.map { it.id }.toSet()
        return out + after.filter { it.id in known } + after.filter { it.id !in known }   // the same id groups together (byChannel)
    }

    /** A name as both lists would agree on it: no accents, no "HD" / "TV" / spaces ("Vĩnh Long 1 HD" = "Vinh Long TV 1"). */
    fun key(name: String): String {
        val plain = Normalizer.normalize(name.lowercase().replace('đ', 'd'), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return plain.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() && it !in NOISE }.joinToString("")
    }
    private val NOISE = setOf("hd", "sd", "fhd", "uhd", "4k", "tv", "kenh", "channel")

    /** Ours ("vieon:vtv1-hd") or a plain URL. */
    fun isLink(url: String) = !url.startsWith("http") && url.substringBefore(':', "").isNotEmpty()

    private val played = HashMap<String, Played>()

    /** The URL a link of ours plays now: asked of its provider, kept until shortly before it runs out. [fresh] asks again. */
    @Throws(IOException::class)
    fun resolve(c: Context, url: String, fresh: Boolean = false): String {
        if (!isLink(url)) return url
        synchronized(played) { if (!fresh) played[url]?.takeIf { it.until > System.currentTimeMillis() }?.let { return it.url } }
        val p = all(c).firstOrNull { it.id == url.substringBefore(':') } ?: throw IOException("provider off")
        val got = KINDS[p.kind]?.play(p, url.substringAfter(':')) ?: throw IOException("not free")
        synchronized(played) { played[url] = got }
        return got.url
    }

    private fun channelsJson(p: Provider): String? {
        val list = KINDS[p.kind]?.list(p) { u -> http(u, p.headers, null) } ?: return null
        if (list.isEmpty()) return null                                // a moved API: keep the last list rather than none
        return JSONArray(list.map { JSONObject().put("id", it.id).put("name", it.name).put("logo", it.logo).put("group", it.group).put("url", it.url).put("q", it.quality).put("g", it.guide) }).toString()
    }

    private fun fromJson(s: String): List<Channel> = try {
        val a = JSONArray(s)
        (0 until a.length()).map { a.getJSONObject(it) }.map { Channel(it.getString("id"), it.getString("name"), it.optString("logo"), it.optString("group"), it.getString("url"), it.optString("q"), guide = it.optString("g")) }
    } catch (e: org.json.JSONException) { emptyList() }

    /** GET, or POST [form] when given; the body on 2xx, null otherwise. */
    fun http(url: String, headers: Map<String, String>, form: String?): String? {
        val k = URL(url).openConnection() as HttpURLConnection
        k.connectTimeout = 8000; k.readTimeout = 12000
        headers.forEach { (n, v) -> k.setRequestProperty(n, v) }
        if (form != null) {
            k.requestMethod = "POST"; k.doOutput = true
            k.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            k.outputStream.use { it.write(form.toByteArray()) }
        }
        return try { if (k.responseCode in 200..299) k.inputStream.bufferedReader().readText() else null } finally { k.disconnect() }
    }
}

/**
 * VieON (vieon.vn): the same calls its web page makes, without an account. The list (`{items: [...]}`, ~40-60 a page) gives
 * every live channel; "is_premium" ones need a package and are left out. Playing asks the channel's details (a form POST of its
 * slug): "permission" 206 means it can be watched, and the H.264 HLS link (HEVC does not play on many TVs) holds its expiry
 * in the path (/<md5>/<ms>/), about a day; it is kept until an hour before that, at most 6 hours.
 */
object Vieon : Providers.Kind {
    override fun list(p: Providers.Provider, get: (String) -> String?): List<Channel> {
        val out = ArrayList<Channel>()
        for (page in 0 until p.o.optInt("pages", 6)) {
            val items = get(p.o.getString("list").replace("{page}", page.toString()))?.let { parseList(it, p) } ?: break
            if (items.isEmpty()) break
            out += items
        }
        return out.distinctBy { it.url }
    }

    fun parseList(text: String, p: Providers.Provider): List<Channel>? = try {
        val a = JSONObject(text).optJSONArray("items")
        a?.let { (0 until it.length()).mapNotNull { i -> it.optJSONObject(i) } }?.mapNotNull { o ->
            val slug = o.optString("slug"); val title = o.optString("title").trim()
            if (slug.isEmpty() || title.isEmpty() || o.optInt("is_premium") != 0) return@mapNotNull null
            val hd = HD.containsMatchIn(title)
            val name = HD.replace(title, "").trim().ifEmpty { title }    // "HTVC Phim HD": the card says HD by its badge
            val img = o.optJSONObject("images")
            val logo = img?.optString("livetv_logo_light")?.ifEmpty { null } ?: o.optString("image_link")
            Channel("$slug.${p.country}@${p.id}", name, logo, guessGroup(name), "${p.id}:$slug", if (hd) "1080p" else "", guide = o.optString("id"))
        }
    } catch (e: org.json.JSONException) { null }

    private val HD = Regex("(?i)\\s+(F?HD)$")
    private fun guessGroup(name: String) = if (Regex("(?i)\\b(FM|AM|radio|audio|VOH)\\b").containsMatchIn(name)) "Music" else "General"

    override fun play(p: Providers.Provider, key: String): Providers.Played? {
        val tpl = p.o.getString("form")                              // "livetv_slug=/truyen-hinh-truc-tuyen/{slug}/"
        val form = tpl.substringBefore('=') + "=" + URLEncoder.encode(tpl.substringAfter('=').replace("{slug}", key), "UTF-8")
        val text = Providers.http(p.o.getString("play"), p.headers, form) ?: return null
        return parsePlay(text, p.o.optInt("free", 206), System.currentTimeMillis())
    }

    /** Today only, whatever [day] says: its guide has no date to ask by. */
    override fun guide(p: Providers.Provider, id: String, day: String): List<Show>? {
        if (day != Guide.day(p)) return null
        return Providers.http(p.o.optString("guide").ifEmpty { return null }.replace("{id}", id), p.headers, null)?.let { parseGuide(it) }
    }

    fun parseGuide(text: String): List<Show>? = try {
        val a = org.json.JSONArray(text)
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { o ->
            val t = o.optString("title").trim(); val s = o.optLong("time_start"); val e = o.optLong("time_end")
            if (t.isEmpty() || s <= 0 || e <= s) null else Show(t, s * 1000, e * 1000)
        }
    } catch (e: org.json.JSONException) { null }

    fun parsePlay(text: String, free: Int, now: Long): Providers.Played? = try {
        val d = JSONObject(text)
        val links = d.optJSONObject("play_links")
        fun hls(v: String?) = v?.takeIf { Regex("^https?://.+\\.m3u8").containsMatchIn(it) }
        val url = hls(links?.optJSONObject("h264")?.optString("hls")) ?: hls(d.optString("hls_link_play")) ?: hls(d.optString("link_play"))
            ?: hls(links?.optJSONObject("h265")?.optString("hls"))
        if (d.optInt("permission") != free || url == null) null else {
            val expiry = Regex("/[0-9a-f]{32}/(\\d{13})/").find(url)?.groupValues?.get(1)?.toLong()
            Providers.Played(url, if (expiry == null) now + 3600_000L else maxOf(now, minOf(expiry - 3600_000L, now + 6 * 3600_000L)))
        }
    } catch (e: org.json.JSONException) { null }
}

/**
 * TV360 (tv360.vn): only its programme guide, which it shows to anyone (its streams need a short-lived token from a browser).
 * Its channel ids come from the list (`"channels": {"VTV3": "4"}`): its own list is a 1 MB web page, too much for a TV box.
 * A day is asked by date, today or tomorrow.
 */
object Tv360 : Providers.Kind {
    override fun guide(p: Providers.Provider, id: String, day: String): List<Show>? =
        Providers.http(p.o.optString("guide").ifEmpty { return null }.replace("{id}", id).replace("{date}", day), p.headers, null)?.let { parseGuide(it) }

    fun parseGuide(text: String): List<Show>? = try {
        val a = JSONObject(text).optJSONObject("data")?.optJSONArray("schedules") ?: org.json.JSONArray()
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { o ->
            val t = o.optString("name").trim(); val s = o.optLong("epochSt"); val e = o.optLong("epochEt")
            if (t.isEmpty() || s <= 0 || e <= s) null else Show(t, s, e)
        }
    } catch (e: org.json.JSONException) { null }
}
