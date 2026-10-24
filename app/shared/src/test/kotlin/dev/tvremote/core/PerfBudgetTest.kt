package dev.tvremote.core

import dev.tvremote.core.ontv.Bloat
import dev.tvremote.core.ontv.Channels
import dev.tvremote.core.ontv.Launchers
import dev.tvremote.core.scenes.Voice
import dev.tvremote.core.web.Qr
import dev.tvremote.core.web.WebPage
import dev.tvremote.home.HomeQr
import dev.tvremote.update.SignedList
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Speed budgets for the paths a person waits on (a gallery opening, the page's first load, a diagnosis, a QR): ~20 cases, not every
 * function. Each is timed as the median of several samples after a warm-up and compared with this machine's baseline. Red only when
 * it is BOTH over 2x the baseline AND over baseline + its floor: a noisy laptop or a busy CI moves a median by tens of percent,
 * a regression (a loop made quadratic, a regex built per call, a file read twice) by several times.
 * Baselines are local to the machine (dev/perf/.local/baseline-jvm.json, gitignored, keyed by OS / CPU count / JVM): the first run records
 * and passes, a clearly faster run lowers it. Red = find what got slower (git log on that function) and fix it. Only when slower is
 * intended: `TVR_PERF_UPDATE=1` re-records (say why in the commit). See AGENTS.md and dev/DECISIONS.md "Speed budgets".
 */
class PerfBudgetTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "dev/perf").isDirectory }
    private val docs = File(root, "docs")
    private val web = File(root, "app/shared/web")

    /**
     * [ms]: the baseline's unit is milliseconds per call. [batch]: calls per sample, so a microsecond case is timed over thousands
     * (a clock tick is ~40 ns, the sample must be well above it). [floor]: how many ms over the baseline still counts as noise.
     */
    private fun case(name: String, batch: Int = 1, floor: Double = 2.0, run: () -> Unit) {
        repeat(maxOf(2, 20 / batch.coerceAtMost(10))) { repeat(batch) { run() } }          // warm-up: JIT, class loading, regex compile
        val ms = ArrayList<Double>()
        val start = System.nanoTime()
        while (ms.size < MIN_SAMPLES || (ms.size < MAX_SAMPLES && System.nanoTime() - start < BOX_MS * 1_000_000L)) {
            val t = System.nanoTime()
            repeat(batch) { run() }
            ms.add((System.nanoTime() - t) / 1e6 / batch)
        }
        val now = ms.sorted()[ms.size / 2]
        val base = baseline[name]
        if (UPDATE || base == null || now < base * FASTER) { seen[name] = now; if (base == null && !UPDATE) println("perf: recorded '$name' = ${fmt(now)} ms (first run on this machine)"); return }
        if (now > base * 2 && now > base + floor)
            throw AssertionError("perf budget: '$name' is ${fmt(now)} ms (median of ${ms.size}), baseline ${fmt(base)} ms (this machine): over 2x and over +${fmt(floor)} ms. " +
                "Find the regression (git log on the function) and fix it; only if slower is intended: TVR_PERF_UPDATE=1 ./gradlew :shared:testDebugUnitTest --tests '*PerfBudgetTest', and say why in the commit.")
    }

    // ---- signed lists: Ed25519 in BigInteger was ~0.5-1 s on a TV box at every gallery opening ----
    private val list = File(docs, "launchers/launchers.json").readBytes()
    private val sig = File(docs, "launchers/launchers.json.sig").readText()

    @Test fun ed25519VerifyLauncherList() = case("ed25519.verify.launchers", floor = 5.0) { check(SignedList.verify(list, sig)) }

    /** The marker path: what every opening after the first pays. */
    @Test fun signedListKeptMarker() {
        val d = tmp(); SignedList.keep(File(d, "l.json"), list, sig); SignedList.kept(File(d, "l.json"))   // writes the .ok
        case("signedlist.kept.marker", batch = 20, floor = 1.0) { check(SignedList.kept(File(d, "l.json")) != null) }
    }

    /** No marker: the full signature check each time (a first opening, or the marker lost). */
    @Test fun signedListKeptFull() {
        val d = tmp(); val f = File(d, "l.json"); SignedList.keep(f, list, sig)
        case("signedlist.kept.full", floor = 5.0) { File(d, "l.json.ok").delete(); check(SignedList.kept(f) != null) }
    }

    // ---- the launcher gallery ----
    @Test fun launchersParse() = case("launchers.parse", batch = 20, floor = 1.0) { check(Launchers.parse(list).entries.isNotEmpty()) }

    private val facts = "34\n@@\narm64-v8a,armeabi-v7a,armeabi\n@@\ntrue\n@@\npackage:/data/app/x/base.apk\n@@\nGoogle\n@@\n\n"
    @Test fun launchersFacts() = case("launchers.facts", batch = 500, floor = 0.1) { check(Launchers.facts { facts }.sdk == 34) }

    // ---- the page every remote serves ----
    @Test fun webPageGzipCold() {
        // a new WebPage per call: read index.html, stamp its links, gzip 89 KB (what the first request after a start pays)
        val open = { n: String -> File(web, n).inputStream() }
        case("webpage.serve.gzip.cold", floor = 5.0) { check(WebPage(open, "tv", "1").serve("/index.html", "gzip") != null) }
    }

    @Test fun webPageServeCached() {
        val p = WebPage({ n -> File(web, n).inputStream() }, "tv", "1"); p.serve("/index.html", "gzip")
        case("webpage.serve.cached", batch = 200, floor = 0.1) { check(p.serve("/index.html", "gzip") != null) }
    }

    // ---- the words: both languages are ~200 KB of JSON ----
    @Test fun wordsLoadCold() = case("words.load.cold", floor = 5.0) {
        Words.open = Words.open                                  // the setter drops the books: the next get reads vi.json + en.json again
        check(Words.get("vi", "page.change_tv").isNotEmpty())
    }

    @Test fun wordsGetWarm() {
        Words.get("en", "fleet.count", "n" to 3)
        case("words.get.warm", batch = 5000, floor = 0.02) { check(Words.get("en", "fleet.count", "n" to 3).isNotEmpty()) }
    }

    // ---- QR (the invite and the pairing codes) ----
    @Test fun qrShort() = case("qr.modules.url", batch = 20, floor = 1.0) { check(Qr.modules("http://192.168.1.20:8765/?k=abcdef0123456789") != null) }
    @Test fun qrLong() {
        val t = "http://192.168.1.20:8765/invite?code=" + "abcdef0123456789".repeat(12)
        case("qr.modules.long", batch = 10, floor = 2.0) { check(Qr.modules(t) != null) }
    }

    @Test fun homeQrPayload() {
        val f = JSONObject("""{"ssid":"Home;Net","pass":"p:a,ss\"w","sec":"WPA"}""")
        case("homeqr.payload.wifi", batch = 2000, floor = 0.02) { check(HomeQr.payload("wifi", f).isNotEmpty()) }
    }

    // ---- the Diagnose screen: decide + the words + the JSON the page draws, with a fake network ----
    private val probe = object : Diagnose.Probe {
        override fun port(host: String, port: Int, ms: Int) = if (port == 5555) Diagnose.Port.OPEN else Diagnose.Port.SILENT
        override fun net(host: String) = Diagnose.Net.OK
        override fun vpn() = false
        override fun adb(host: String): String? = "ready"
        override fun find(kind: String, name: String, not: String): Diagnose.Found? = null
    }
    @Test fun diagnoseRunAndJson() = case("diagnose.run.json", batch = 20, floor = 2.0) {
        val r = Diagnose.run(probe, "192.168.1.20", "android", "Living room", "", "vi")
        check(Diagnose.json(r, "vi").length() > 0)
    }

    // ---- small parsers on the paths a screen waits on ----
    private val apps = mapOf("com.google.android.youtube.tv" to "YouTube", "com.netflix.ninja" to "Netflix", "com.fptplay.tv" to "FPT Play", "org.videolan.vlc" to "VLC")
    private val scenes = mapOf("s1" to "Đi ngủ", "s2" to "Xem phim")
    private val said = listOf("Mở YouTube!", "tắt tiếng", "hẹn giờ tắt 30 phút", "kênh VTV1", "tăng âm lượng 5", "xem phim", "mở app không có")
    @Test fun voiceParse() = case("voice.parse", batch = 100, floor = 0.5) { for (s in said) Voice.parse(s, apps, scenes) }

    private val status = "  mWakefulness=Awake\n#~#\n  mCurrentFocus=Window{71d7193 u0 com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.home.HomeActivity}\n#~#\n" +
        "454.40 1768.39\nMemTotal:        2025912 kB\nMemAvailable:     805432 kB\n#~#\nGoogle\nsdk_google_atv64_amati_arm64\n14"
    @Test fun fleetStatusParse() = case("fleetstatus.parse", batch = 2000, floor = 0.05) { check(FleetStatus.parse("h", status, now = 1).model.isNotEmpty()) }

    private val bloat = """{"v":1,"suggest":{${(1..80).joinToString(",") { "\"com.vendor$it.app\":\"App $it\"" }}},"drop":[${(1..60).joinToString(",") { "\"com.drop$it.x\"" }}],"keep":["com\\.keep\\..*"],"factory":["com\\.factory\\..*"]}""".toByteArray()
    @Test fun bloatParse() = case("bloat.parse", batch = 100, floor = 0.5) { check(Bloat.parse(bloat).suggest.size == 80) }

    // a country-sorted playlist, as the iptv-org one TV Channels counts: 20k lines
    private val m3u = (1..20000).map { """#EXTINF:-1 tvg-id="c$it.vn@SD" group-title="${if (it % 3 == 0) "Vietnam" else if (it % 3 == 1) "Japan" else "International"}",Channel $it""" }
    @Test fun channelsCount() = case("channels.count.20k", floor = 5.0) { check(Channels.count(m3u.asSequence(), mapOf("Vietnam" to "VN", "Japan" to "JP")).size == 2) }

    private fun tmp(): File = java.nio.file.Files.createTempDirectory("tvr-perf").toFile().apply { deleteOnExit() }
    private fun fmt(d: Double) = String.format(Locale.ROOT, "%.4f", d)

    companion object {
        private const val MIN_SAMPLES = 7
        private const val MAX_SAMPLES = 25
        private const val BOX_MS = 600L                   // a case's time box: ~20 cases stay under 20 s
        private const val FASTER = 0.8                    // a run this much under the baseline pulls it down (never up: that is TVR_PERF_UPDATE=1)
        private val UPDATE = System.getenv("TVR_PERF_UPDATE") == "1"
        private val seen = java.util.TreeMap<String, Double>()
        /** Baselines are per machine and never committed (dev/perf/.local is gitignored): another CPU, OS or JVM is another key, recorded on its first run. */
        private val machine = "${System.getProperty("os.name")} ${System.getProperty("os.arch")} / ${Runtime.getRuntime().availableProcessors()} cores / java ${System.getProperty("java.version")}"
        private val file by lazy { File(generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "dev/perf").isDirectory }, "dev/perf/.local/baseline-jvm.json") }
        private val all by lazy { try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() } }
        private val baseline: Map<String, Double> by lazy {
            val o = all.optJSONObject(machine) ?: JSONObject()
            o.keys().asSequence().associateWith { o.getDouble(it) }
        }

        @AfterClass @JvmStatic fun write() {
            if (seen.isEmpty()) return
            val o = all.optJSONObject(machine) ?: JSONObject().also { all.put(machine, it) }
            for ((k, v) in seen) o.put(k, String.format(Locale.ROOT, "%.4f", v).toDouble())
            file.parentFile.mkdirs()
            file.writeText(all.toString(2) + "\n")
        }
    }
}
