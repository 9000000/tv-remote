package dev.tvremote.phone

import android.content.Context
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import dev.tvremote.core.Atv

// Helping somebody turn ADB on with the keys alone: installing TV Setup through Downloader, finding Settings, other home screens.

internal fun MainActivity.tvFree(): Boolean {
    if (tvBusy.compareAndSet(false, true)) return true
    Toast.makeText(this, t("TV đang làm việc vừa yêu cầu. Chờ vài giây rồi bấm lại.", "The TV is still doing what was just asked. Wait a few seconds, then tap again."), Toast.LENGTH_SHORT).show()
    return false
}
internal fun MainActivity.tvJob(work: () -> Unit) {
    if (!tvFree()) return
    Toast.makeText(this, t("Đang làm trên TV...", "Working on the TV..."), Toast.LENGTH_SHORT).show()
    Thread { try { work() } catch (ignored: Exception) { } finally { tvBusy.set(false) } }.start()
}

/** Everything the keys-only mode can do towards turning ADB on, in the order one needs it. */
internal fun MainActivity.adbHelp() {
    menu(t("Bật ADB cho TV này", "Turn on ADB for this TV"), 0, listOf(
        Triple("@zap|" + t("Tự động: cài TV Setup lên TV", "Automatic: install TV Setup on the TV"), t("Chỉ cần xác nhận trên TV", "Just confirm on the TV")) { installSetup() },
        Triple("@gear|" + t("Tự làm: mở Cài đặt TV", "By hand: open TV Settings"), "") { findSettings() },
        Triple("@home|" + t("Cài màn hình chính khác", "Install another home screen"), t("Khi TV giấu Cài đặt", "When the TV hides Settings")) { otherHome() },
        Triple("@wifi|" + t("Ghép bằng Gỡ lỗi không dây", "Pair with Wireless debugging"), t("Android 11 trở lên: mã 6 số TV hiện ra", "Android 11 or newer: the 6-digit code the TV shows")) { wirelessPair() },
        Triple("@refresh|" + t("Kết nối đầy đủ", "Full connection"), "") {
            val ip = host
            adbFailed.remove(ip)
            val e = entry
            disconnect(null)
            if (ip != null) connect(ip, e)
        },
        Triple("@store|" + t("Mở CH Play trên TV", "Open the Play Store on the TV"), "") { basic?.launch("https://play.google.com/store/apps"); Unit },
        Triple("@package|" + t("Mở app theo tên gói", "Open an app by package name"), "") {
            val box = input("com.example.app")
            val wrap = inDialog(box)
            AlertDialog.Builder(this).setTitle(t("Tên gói hoặc đường dẫn", "Package name or link")).setView(wrap)
                .setPositiveButton(t("Mở", "Open")) { _, _ ->
                    val what = box.text.toString().trim()
                    val b = basic
                    if (what.isNotEmpty() && b != null) { if (what.contains(":")) b.launch(what) else tvJob { openOnTv(b, what) { false } } }
                }
                .setNegativeButton(t("Hủy", "Cancel"), null).show()
            Unit
        }))
}

/**
 * Wireless debugging (Android 11+): the TV shows a 6-digit code and an IP:port to pair with. Pairing, then the TLS connect
 * port (found by mDNS, typed when nothing answered), then the usual connection; no "Allow USB debugging?" question.
 */
internal fun MainActivity.wirelessPair() {
    val ip = host
    val addr = input(t("IP:cổng ghép nối", "Pairing IP:port"))
    val code = input(t("Mã 6 số", "6-digit code"))
    addr.setSingleLine(true); code.inputType = android.text.InputType.TYPE_CLASS_NUMBER
    val body = col()
    body.addView(label(t("Trên TV: Cài đặt → Tùy chọn nhà phát triển → Gỡ lỗi không dây → Ghép nối thiết bị bằng mã ghép nối.",
        "On the TV: Settings → Developer options → Wireless debugging → Pair device with pairing code."), 14, TEXT))
    body.addView(addr, gap(8)); body.addView(code, gap(8))
    Thread {                                                               // prefilled when the TV announces itself on the network
        try {
            val (pairing, _) = dev.tvremote.core.AdbTls.discover()
            val p = pairing.firstOrNull { it.ip == ip } ?: pairing.firstOrNull()
            if (p != null) ui.post { if (addr.text.isEmpty()) addr.setText("${p.ip}:${p.port}") }
        } catch (ignored: Exception) { }
    }.start()
    AlertDialog.Builder(this).setTitle(t("Ghép bằng Gỡ lỗi không dây", "Pair with Wireless debugging")).setView(inDialog(body))
        .setPositiveButton(t("Ghép", "Pair")) { _, _ ->
            val m = Regex("^(?:(.+):)?(\\d{2,5})$").find(addr.text.toString().trim())
            val c = dev.tvremote.core.AdbTls.cleanCode(code.text.toString())
            if (m == null || c == null) { Toast.makeText(this, t("Nhập IP:cổng và mã 6 số hiện trên TV.", "Type the IP:port and the 6-digit code the TV shows."), Toast.LENGTH_LONG).show(); return@setPositiveButton }
            val target = m.groupValues[1].ifEmpty { ip ?: "" }
            Toast.makeText(this, t("Đang ghép nối...", "Pairing..."), Toast.LENGTH_SHORT).show()
            Thread {
                try {
                    dev.tvremote.core.AdbTls.pair(dev.tvremote.core.Adb(filesDir), target, m.groupValues[2].toInt(), c)
                    var port = 0
                    for (i in 0..2) if (port <= 0) port = dev.tvremote.core.AdbTls.discoverConnect(target)
                    if (port > 0) ui.post { pairedAt(target, port) } else ui.post { askConnectPort(target) }
                } catch (e: Exception) {
                    ui.post { Toast.makeText(this, dev.tvremote.core.Errors.say(e, VI), Toast.LENGTH_LONG).show() }
                }
            }.start()
        }
        .setNegativeButton(t("Hủy", "Cancel"), null).show()
}

private fun MainActivity.askConnectPort(target: String) {
    val box = input(t("Cổng kết nối", "Connect port"))
    box.inputType = android.text.InputType.TYPE_CLASS_NUMBER
    AlertDialog.Builder(this).setTitle(t("Đã ghép. Cổng kết nối?", "Paired. Connect port?"))
        .setMessage(t("Ở màn hình Gỡ lỗi không dây, dưới “Địa chỉ IP và cổng”.", "On the Wireless debugging screen, under “IP address & Port”."))
        .setView(inDialog(box))
        .setPositiveButton(t("Kết nối", "Connect")) { _, _ -> box.text.toString().trim().toIntOrNull()?.let { pairedAt(target, it) } }
        .setNegativeButton(t("Hủy", "Cancel"), null).show()
}

private fun MainActivity.pairedAt(target: String, port: Int) {
    dev.tvremote.core.AdbTls.save(filesDir, target, port)
    adbFailed.remove(target)
    val e = entry
    disconnect(null)
    connect(target, e)
}

/**
 * Getting our TV app onto a TV that has no ADB yet, in one tap: the Downloader app (installed from the Play Store when
 * missing) fetches it, and the phone opens Downloader, types the link and presses Go. What only the person can do is
 * confirm the install on the TV.
 */
internal fun MainActivity.installSetup() {
    val a = basic ?: return
    if (!tvFree()) return
    val link = "github.com/hoangtran0410/tv-remote/releases/latest/download/tv-setup.apk"
    val dl = "com.esaba.downloader"
    var stop = false
    var installed = false                                         // the person says the TV has finished installing Downloader
    val box = AlertDialog.Builder(this).setTitle(t("Cài TV Setup lên TV", "Install TV Setup on the TV")).setMessage(t("Đang mở Downloader trên TV...", "Opening Downloader on the TV..."))
        .setNeutralButton(t("Đã cài xong", "Installed"), null)
        .setNegativeButton(t("Dừng", "Stop")) { _, _ -> stop = true }.setOnCancelListener { stop = true }.show()
    val doneBtn = box.getButton(AlertDialog.BUTTON_NEUTRAL)
    val say = { vi: String, en: String -> ui.post { if (box.isShowing) box.setMessage(t(vi, en)) } }
    // The box changes its words as it goes. It must not change its size with them, or the buttons slide away from under
    // a finger that is still tapping, and a tap that lands beside the box must not close it and stop the whole thing.
    box.setCanceledOnTouchOutside(false)
    box.findViewById<TextView>(android.R.id.message)?.minLines = 7
    // greyed out while the check runs; its words stay, or the buttons would shift under a finger that is still tapping
    val checking = { on: Boolean -> ui.post { doneBtn.isEnabled = !on; doneBtn.alpha = if (on) 0.4f else 1f } }
    doneBtn.setOnClickListener { if (!installed) { installed = true; checking(true); say("Đang kiểm tra trên TV, chờ khoảng nửa phút...", "Checking on the TV, about half a minute...") } }   // set here so that the box stays open; one check at a time
    // waits until the TV says [pkg] is in front; false when it does not within [seconds], or the person stopped it
    val front = { pkg: String, seconds: Int ->
        var ok = false
        for (i in 0 until seconds * 5) { if (stop || basic !== a) break; if (a.app == pkg) { ok = true; break }; Thread.sleep(200) }
        ok
    }
    Thread {
        try {
            if (a.app.isEmpty()) {                                    // this TV does not say what is on its screen: no way to follow along
                ui.post { box.dismiss(); installSetupByHand() }
                return@Thread
            }
            // Downloader first, always: a TV's own browser takes the APK link too and downloads it, then stays on a white page,
            // unable to hand the file to the installer. Downloader's own link (downloader://) opens nothing else.
            var there = openOnTv(a, dl) { stop }
            if (!there && !stop && basic === a) {                     // its Play Store page was told to install it
                say("TV chưa có Downloader: đang cài từ CH Play...\n\nNếu TV không tự cài, bấm “Cài đặt” trên màn hình TV. Khi TV báo cài xong, bấm “Đã cài xong” bên dưới.",
                    "The TV has no Downloader yet: installing it from the Play Store...\n\nIf the TV does not start by itself, choose “Install” on its screen. When the TV says it is installed, tap “Installed” below.")
                while (!stop && basic === a && !there) {
                    for (i in 0 until 600) { if (stop || installed || a.app == dl) break; Thread.sleep(200) }
                    if (stop || basic !== a) break
                    checking(true)
                    say("Đang mở Downloader trên TV để kiểm tra...", "Opening Downloader on the TV to check...")
                    there = a.app == dl || openOnTv(a, dl) { stop }
                    installed = false
                    checking(false)
                    if (!there) say("TV vẫn chưa có Downloader. Chọn “Cài đặt” trên màn hình TV; khi TV báo cài xong, bấm “Đã cài xong”.", "The TV still has no Downloader. Choose “Install” on its screen; when it says installed, tap “Installed”.")
                }
            }
            if (!there) {
                ui.post { if (box.isShowing) box.dismiss(); if (!stop && basic === a) installSetupByHand() }
                return@Thread
            }
            say("Đang đưa đường dẫn tải cho Downloader...", "Handing the download link to Downloader...")
            Thread.sleep(3000)
            a.launch("downloader://$DL_CODE")                         // its own link: opens Downloader alone
            for (press in 0 until 3) {                                // "Open with", should another app claim it too
                Thread.sleep(1500)
                if (a.app != "android" || stop) break
                a.pulse(23, 110)
            }
            Thread.sleep(2500)
            val from = a.app
            say("Đã nhập đường dẫn. Đang chờ TV tải xong...", "Link entered. Waiting for the TV to finish the download...")
            for (i in 0 until 240) {                                  // up to 2 minutes, until the TV's installer (or its permission screen) comes up
                if (stop || basic !== a || a.app != from) break
                Thread.sleep(500)
            }
            ui.post {
                if (box.isShowing) box.dismiss()
                if (stop || basic !== a || isFinishing) return@post
                AlertDialog.Builder(this).setTitle(t("Nhìn lên TV", "Look at the TV"))
                    .setMessage(t("App đã nhập đường dẫn và cho Downloader tải.\n\n1. TV hỏi cài đặt: chọn Cài đặt (Install). Nếu TV hỏi quyền cài từ nguồn này: bật cho Downloader, bấm Quay lại, rồi chọn Install.\n2. Cài xong chọn Mở (hoặc bấm “Mở TV Setup” bên dưới).\n3. TV Setup tự mở Tùy chọn nhà phát triển: bật “Gỡ lỗi USB”.\n\nNếu TV vẫn đứng ở trang chủ Downloader: bấm “Làm từng bước”.",
                        "The app has entered the link and Downloader is fetching it.\n\n1. The TV asks to install: choose Install. If it asks to allow installs from this source: allow Downloader, press Back, then choose Install.\n2. Then choose Open (or tap “Open TV Setup” below).\n3. TV Setup opens Developer options by itself: turn on “USB debugging”.\n\nIf the TV is still on Downloader’s home page: tap “Step by step”."))
                    .setPositiveButton(t("Mở TV Setup", "Open TV Setup")) { _, _ -> tvJob { openOnTv(a, "dev.tvremote.setup") { false } } }
                    .setNeutralButton(t("Làm từng bước", "Step by step")) { _, _ -> installSetupByHand() }
                    .setNegativeButton(t("Đóng", "Close"), null).show()
            }
        } catch (ignored: Exception) {
        } finally {
            tvBusy.set(false)
        }
    }.start()
}

/**
 * Brings the app [pkg] to the front on the TV, on a worker thread; true once the TV says it is there. Asking for the app by
 * name works on some TVs and not on others (and not every time), so the sure way follows: its Play Store page, where OK
 * presses the main button. That is "Open" when the app is installed; when it is not, the same press starts installing it,
 * and this returns false.
 */
internal fun MainActivity.openOnTv(a: dev.tvremote.core.Remote, pkg: String, stop: () -> Boolean): Boolean {
    val front = { want: String, seconds: Int ->
        var ok = false
        for (i in 0 until seconds * 5) { if (stop() || basic !== a) break; if (a.app == want) { ok = true; break }; Thread.sleep(200) }
        ok
    }
    if (a.app == pkg) return true
    a.launch(pkg)
    if (front(pkg, 4)) return true
    a.launch("https://play.google.com/store/apps/details?id=$pkg")
    if (!front("com.android.vending", 10)) return false
    Thread.sleep(3000)                                            // the page draws, with its main button in focus
    a.pulse(23, 110)
    return front(pkg, 15)
}

/**
 * Drives Downloader's home page to load [link], on a worker thread. The page has a pointer and no field in focus, so:
 * hold Up until the pointer leaves the page and the toolbar takes the focus; go to its right end and two back, which is
 * the address field wherever the focus first landed; OK opens the field with the TV's keyboard; type; Enter loads.
 */
internal fun MainActivity.downloaderGo(a: dev.tvremote.core.Remote, link: String) {
    for (i in 0 until 40) a.pulse(19, 70)                          // many short holds: over this protocol a key cannot be kept down, each one nudges the pointer
    for (i in 0 until 4) a.pulse(22, 110)
    for (i in 0 until 2) a.pulse(21, 110)
    Thread.sleep(40 * 90L + 6 * 350L)                             // the keys above are queued; let them play out
    a.pulse(23, 110)
    Thread.sleep(2500)                                            // the field and the keyboard open
    (a as? Atv)?.keys(link) ?: a.text(link)
    Thread.sleep(link.length * 60L + 1000)
    a.key(66, Atv.SHORT)
}

/** The same, one step at a time, for when the automatic run loses its way. */
internal fun MainActivity.installSetupByHand() {
    val link = "github.com/hoangtran0410/tv-remote/releases/latest/download/tv-setup.apk"
    menu(t("Cài TV Setup lên TV", "Install TV Setup on the TV"), -1, listOf(
        Triple(t("1. Mở Downloader trên TV", "1. Open Downloader on the TV"), t("Chưa có thì cài từ CH Play", "Not there? Install it from Play")) {
            basic?.let { b -> tvJob { openOnTv(b, "com.esaba.downloader") { false } } }
            ui.postDelayed({ if (basic != null && !isFinishing) installSetupByHand() }, 600)
        },
        Triple(t("2. Tải TV Setup trong Downloader", "2. Download TV Setup in Downloader"), t("Mã $DL_CODE", "Code $DL_CODE")) {
            basic?.let { b -> tvJob { b.launch("downloader://$DL_CODE") } }
            Toast.makeText(this, t("Đang bảo Downloader tải...", "Asking Downloader to download..."), Toast.LENGTH_LONG).show()
            ui.postDelayed({ if (basic != null && !isFinishing) installSetupByHand() }, 600)
        },
        Triple(t("2b. Không được? Gõ mã vào Downloader", "2b. No luck? Type the code into Downloader"), t("App tự gõ mã", "The app types the code")) {
            basic?.let { b -> tvJob { downloaderGo(b, DL_CODE) } }
            Toast.makeText(this, t("Đang nhập đường dẫn trên TV...", "Entering the link on the TV..."), Toast.LENGTH_LONG).show()
            ui.postDelayed({ if (basic != null && !isFinishing) installSetupByHand() }, 600)
        },
        Triple(t("3. Mở TV Setup trên TV", "3. Open TV Setup on the TV"), t("Rồi bật Gỡ lỗi USB", "Then turn on USB debugging")) { basic?.let { b -> tvJob { openOnTv(b, "dev.tvremote.setup") { false } } }; Unit },
        Triple(t("Chép đường dẫn", "Copy the link"), t("Tự gõ trong Downloader: mã $DL_CODE, hoặc ", "To type in Downloader yourself: the code $DL_CODE, or ") + link) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("tv-setup", link))
            Toast.makeText(this, t("Đã chép", "Copied"), Toast.LENGTH_SHORT).show()
        }))
}

internal fun MainActivity.isSettings(pkg: String) = pkg.contains("settings", true)

/** Tries the Settings key, then the links that open Settings on other TVs, watching which app the TV says is in front. */
internal fun MainActivity.findSettings() {
    val a = basic ?: return
    if (!tvFree()) return
    Toast.makeText(this, t("Đang thử mở Cài đặt trên TV...", "Trying to open Settings on the TV..."), Toast.LENGTH_SHORT).show()
    Thread {
        try {
            val tries = listOf<() -> Unit>({ a.key(176, Atv.SHORT) }, { a.launch("android-app://com.android.tv.settings") }, { a.launch("intent:#Intent;action=android.settings.SETTINGS;end") })
            var ok = isSettings(a.app)
            for (attempt in tries) {
                if (ok) break
                for (i in 0 until 30) { if (a.ready()) break; Thread.sleep(200) }   // the TV may have dropped us after the last try
                attempt()
                for (i in 0 until 12) { Thread.sleep(200); if (isSettings(a.app)) { ok = true; break } }
            }
            val blind = a.app.isEmpty()                                // this TV does not say what is on its screen
            ui.post {
                if (isFinishing || basic !== a) return@post
                if (ok) adbSteps()
                else if (blind) AlertDialog.Builder(this).setMessage(t("TV có mở màn hình Cài đặt không?", "Did the TV open its Settings?"))
                    .setPositiveButton(t("Có", "Yes")) { _, _ -> adbSteps() }.setNegativeButton(t("Không", "No")) { _, _ -> lookOnHome() }.show()
                else lookOnHome()
            }
        } catch (ignored: Exception) {
        } finally {
            tvBusy.set(false)
        }
    }.start()
}

/** The keys and links did nothing: the TV's own home screen may still have Settings somewhere, before anything gets installed. */
internal fun MainActivity.lookOnHome() {
    AlertDialog.Builder(this).setTitle(t("TV không mở Cài đặt theo lệnh", "The TV did not open Settings when asked"))
        .setMessage(t("Tìm trên màn hình chính của TV, dùng các phím mũi tên ở đây: biểu tượng hồ sơ/tài khoản hoặc bánh răng (thường ở góc trên) → Cài đặt → “Cài đặt thiết bị”.\n\nBox FPT Play: mở bảng Quản lý (hồ sơ) → Cài đặt → Cài đặt thiết bị.",
            "Look on the TV’s home screen, using the arrows here: the profile/account or gear icon (usually in a top corner) → Settings → “Device settings”.\n\nFPT Play box: open the profile panel → Settings → Device settings."))
        .setPositiveButton(t("Thấy rồi", "Found it")) { _, _ -> adbSteps() }
        .setNegativeButton(t("Không có · Cài màn hình chính khác", "Not there · Install another home screen")) { _, _ -> otherHome() }.show()
}

internal fun MainActivity.otherHome() {
    val a = basic ?: return
    menu(t("Mở Cài đặt qua màn hình chính khác", "Reach Settings through another home screen"), -1, HOMES.map { (name, pkg) ->
        Triple(name, if (a.app == pkg) t("Đang mở trên TV", "Open on the TV now") else t("Đã cài thì mở, chưa cài thì mở trang CH Play", "Opens it, or its Play Store page")) {
            tvJob { openOnTv(a, pkg) { false } }
            AlertDialog.Builder(this).setTitle(name).setMessage(t("Nhìn lên TV.\n\n• Thấy trang CH Play: bấm Cài đặt, xong bấm “Mở lại” bên dưới.\n• CH Play báo không tương thích hoặc không tìm thấy: bấm “Chọn cái khác”.\n• Thấy màn hình chính mới: tìm biểu tượng bánh răng → Cài đặt Android, rồi bấm “Các bước bật ADB”.\n\nCH Play cần TV đã đăng nhập tài khoản Google.",
                    "Look at the TV.\n\n• A Play Store page: choose Install, then tap “Open again” below.\n• The store says it is incompatible or not found: tap “Pick another”.\n• The new home screen: find the gear icon → Android settings, then tap “ADB steps”.\n\nThe Play Store needs a Google account on the TV."))
                .setPositiveButton(t("Các bước bật ADB", "ADB steps")) { _, _ -> adbSteps() }
                .setNeutralButton(t("Mở lại", "Open again")) { _, _ -> tvJob { openOnTv(a, pkg) { false } }; ui.postDelayed({ if (basic === a && !isFinishing) adbSteps() }, 800) }
                .setNegativeButton(t("Chọn cái khác", "Pick another")) { _, _ -> otherHome() }.show()
            Unit
        }
    })
}

internal fun MainActivity.adbSteps() {
    AlertDialog.Builder(this).setTitle(t("Bật Gỡ lỗi ADB trên TV", "Turn on ADB debugging on the TV"))
        .setMessage(t("Dùng các phím mũi tên và OK của remote này:\n\n1. Trong Cài đặt: Tùy chọn thiết bị (hoặc Hệ thống) → Giới thiệu → bấm OK 7 lần vào “Bản dựng”.\n2. Quay lại, vào Tùy chọn nhà phát triển → bật “Gỡ lỗi USB” (hoặc “Gỡ lỗi ADB”).\n3. Bấm “Kết nối đầy đủ”, rồi trên TV tích “Luôn cho phép” và chọn OK.\n\nNếu TV đòi “mật khẩu ADB” khi bấm “Bản dựng” (vd. box FPT Play): không cần mật khẩu, hãy cài TV Setup lên TV, nó mở thẳng Tùy chọn nhà phát triển. Nếu vẫn không được, TV này vẫn dùng được chế độ bấm phím.",
            "Use this remote’s arrows and OK:\n\n1. In Settings: Device Preferences (or System) → About → press OK 7 times on “Build”.\n2. Go back, open Developer options → turn on “USB debugging” (or “ADB debugging”).\n3. Tap “Full connection”, then on the TV tick “Always allow” and choose OK.\n\nIf the TV asks for an “ADB password” when you press “Build” (some operator boxes do): you don’t need it, install TV Setup on the TV and it opens Developer options directly. If that still fails, this TV still works in keys-only mode."))
        .setPositiveButton(t("Kết nối đầy đủ", "Full connection")) { _, _ ->
            val ip = host
            val e = entry
            disconnect(null)
            if (ip != null) connect(ip, e)
        }
        .setNegativeButton(t("Đóng", "Close"), null).show()
}
