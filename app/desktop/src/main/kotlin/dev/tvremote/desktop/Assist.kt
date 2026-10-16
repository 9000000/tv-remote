package dev.tvremote.desktop

import dev.tvremote.core.Atv
import dev.tvremote.core.HttpServer
import org.json.JSONObject

/**
 * Turning ADB on with the remote buttons alone (port 6466), the page's /api/assist: TV Setup is downloaded onto the TV
 * through links the TV opens (Downloader first when the TV has no app for links), and any app or the TV's Settings can be
 * brought to the front. [cur]: the session of the TV in use.
 */
class Assist(private val cur: () -> Session) {
    @Volatile private var status = "idle"
    @Volatile private var msg = ""
    @Volatile private var stop = false
    @Volatile private var installed = false
    @Volatile private var checking = false
    @Volatile private var opening = false
    @Volatile private var task: Thread? = null

    private fun front(): String = cur().let { s -> if (s.available) s.atv?.app ?: "" else "" }

    /** [stoppable]: only the setup job listens to "Stop"; opening an app or Settings is short anyway. */
    private fun untilFront(seconds: Double, stoppable: Boolean = true, want: (String) -> Boolean): Boolean {
        for (i in 0 until (seconds * 5).toInt()) {
            if (stoppable && stop) return false
            if (want(front())) return true
            Thread.sleep(200)
        }
        return false
    }

    private fun sendLink(link: String) {
        for (i in 0 until 30) { if (cur().available) break; Thread.sleep(200) }   // the TV may have dropped us after a link it disliked
        cur().need().launch(link)
    }

    private fun pressOk() = cur().need().key(23, Atv.SHORT)

    /** Brings an app to the front without ADB: asks for it, else opens its Play Store page and presses its main button. */
    private fun openOnTv(pkg: String, stoppable: Boolean = true): Boolean {
        if (front() == pkg) return true
        sendLink("market://launch?id=$pkg")
        if (untilFront(4.0, stoppable) { it == pkg }) return true
        sendLink("https://play.google.com/store/apps/details?id=$pkg")
        if (!untilFront(10.0, stoppable) { it == "com.android.vending" }) return false
        Thread.sleep(3000)                                            // the page draws, with its main button in focus
        pressOk()
        return untilFront(15.0, stoppable) { it == pkg }
    }

    private fun answerChooser() {
        for (i in 0 until 3) {                                        // "Open with": OK takes what is offered
            Thread.sleep(1500)
            if (front() != "android" || stop) break
            pressOk()
        }
    }

    private fun installSetup() {
        try {
            if (front().isEmpty()) {
                status = "blind"; msg = "TV này không cho biết đang mở gì nên không tự làm được. Làm theo cách “Tự làm” bên dưới."
                return
            }
            msg = "Đang gửi đường dẫn tải cho TV..."
            sendLink(SETUP_APK)
            untilFront(6.0) { it == DOWNLOADER || it == "android" }
            if (front() == "android") {
                msg = "TV đang hỏi “Mở bằng”: đang chọn giúp..."
                answerChooser(); Thread.sleep(1500)
            }
            val a = front()
            val direct = !stop && (a == DOWNLOADER || "chrome" in a || "browser" in a)
            var there = direct || openOnTv(DOWNLOADER)
            while (!there && !stop) {                                 // its Play Store page was told to install it
                msg = "TV chưa có app Downloader: đang cài từ CH Play. Nếu TV không tự cài, chọn “Cài đặt” trên màn hình TV. Khi TV báo cài xong, bấm “Đã cài xong”."
                for (i in 0 until 600) { if (stop || installed || front() == DOWNLOADER) break; Thread.sleep(200) }
                if (stop) break
                checking = true                                       // one check at a time: more presses of "Installed" change nothing
                msg = "Đang mở Downloader trên TV để kiểm tra..."
                there = front() == DOWNLOADER || openOnTv(DOWNLOADER)
                installed = false; checking = false
            }
            if (stop) { status = "idle"; msg = ""; return }
            if (!direct) {
                msg = "Đang đưa đường dẫn tải cho Downloader..."
                Thread.sleep(3000)
                sendLink(SETUP_CODE)
                answerChooser(); Thread.sleep(2500)
            }
            val was = front()
            msg = "TV đang tải TV Setup..."
            untilFront(120.0) { it != was }                           // until the TV's installer (or its permission screen) comes up
            status = if (stop) "idle" else "look"; msg = ""
        } catch (e: InterruptedException) {
            status = "idle"; msg = ""
        } catch (e: Exception) {
            status = "failed"; msg = "Mất kết nối với TV giữa chừng, thử lại: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            checking = false
        }
    }

    fun state(): JSONObject = JSONObject().put("status", status).put("msg", msg).put("front", front()).put("connected", cur().available)
        .put("checking", checking || installed)

    fun handle(d: JSONObject): HttpServer.Response {
        val ok = { HttpServer.Response.ok() }
        when (val what = d.optString("do")) {
            "stop" -> { stop = true; return ok() }
            "installed" -> { if (!checking) installed = true; return ok() }
            else -> {
                cur().need()
                if ((what == "open" || what == "settings") && (status == "running" || opening))   // one job on the TV at a time
                    return HttpServer.Response.error(409, "TV đang làm việc vừa yêu cầu, chờ vài giây rồi bấm lại")
                when (what) {
                    "setup" -> {
                        val old = task
                        if (status == "running" && stop && old != null && old.isAlive) { old.interrupt(); old.join(5000) }   // stopped a moment ago, still winding down
                        if (status != "running") {
                            status = "running"; msg = ""; stop = false; installed = false; checking = false
                            task = Thread({ installSetup() }, "assist").apply { isDaemon = true; start() }
                        }
                        return ok()
                    }
                    "open" -> {                                       // an app by package name, or any link
                        val target = d.optString("what").trim()
                        if (target.isEmpty()) return HttpServer.Response.error(400, "what required")
                        if (':' in target) { sendLink(target); return HttpServer.Response.json(JSONObject().put("ok", true).put("front", front())) }
                        if (!PKG.matches(target)) return HttpServer.Response.error(400, "not a package name")
                        opening = true
                        try { return HttpServer.Response.json(JSONObject().put("ok", openOnTv(target, false)).put("front", front())) } finally { opening = false }
                    }
                    "settings" -> {                                   // every way a TV may open its Settings when asked
                        val isSettings = { a: String -> "settings" in a.lowercase() }
                        val tries = listOf({ cur().need().key(176, Atv.SHORT) }, { sendLink("android-app://com.android.tv.settings") },
                            { sendLink("intent:#Intent;action=android.settings.SETTINGS;end") })
                        var done = isSettings(front())
                        for (t in tries) {
                            if (done) break
                            try { t() } catch (e: Exception) { continue }
                            done = untilFront(2.4, false, isSettings)
                        }
                        return HttpServer.Response.json(JSONObject().put("ok", done).put("blind", front().isEmpty()))
                    }
                    else -> return HttpServer.Response.error(400, "unknown action")
                }
            }
        }
    }

    companion object {
        const val SETUP_APK = "https://github.com/hoangtran0410/tv-remote/releases/latest/download/tv-setup.apk"
        const val DOWNLOADER = "com.esaba.downloader"
        const val SETUP_CODE = "downloader://2397346"                 // Downloader's own link for the AFTVnews code of SETUP_APK: opens Downloader alone, no "Open with"
        val PKG = dev.tvremote.core.Pkg.RE
    }
}
