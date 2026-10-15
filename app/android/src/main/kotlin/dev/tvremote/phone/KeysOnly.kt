package dev.tvremote.phone

import android.app.AlertDialog
import android.os.Build
import android.view.View
import android.text.InputType
import android.widget.FrameLayout
import android.widget.Toast
import dev.tvremote.core.Atv
import java.util.Locale

// When the TV's ADB is off: the keys-only connection (Android TV Remote protocol), pairing it, and noticing when ADB comes on.

internal fun MainActivity.refused(err: String?): Boolean {
    val e = err?.lowercase(Locale.ROOT) ?: ""
    return e.contains("refused") && !e.contains("refused:") || e.contains("econnrefused")
}

/**
 * ADB did not work. Before saying so, see whether the TV answers as an Android TV: then the keys-only remote takes over
 * (straight away when this phone is paired with it, after asking otherwise). [error] is shown only when that is not possible.
 */
internal fun MainActivity.offerBasic(ip: String, error: String) {
    Thread {
        if (open(ip, 5555, 1500)) adbFailed.add(ip)                // the port answers, yet ADB did not work (not allowed on the TV, say): do not keep retrying by ourselves
        val keys = open(ip, 6466, 2500)                            // an Android TV: it takes keys
        ui.post {
            if (isFinishing || host != ip || tv != null) return@post   // the person has moved on
            if (!keys) { disconnect(error); return@post }
            disconnect(null)
            if (prefs.getBoolean("atv:$ip", false)) startBasic(ip, false)
            else AlertDialog.Builder(this).setTitle(t("TV chưa bật Gỡ lỗi ADB", "ADB debugging is off on this TV"))
                .setMessage(t("Vẫn bấm phím điều khiển được, sau khi ghép bằng mã hiện trên TV. Từ đó bạn mở Cài đặt của TV để bật ADB, rồi dùng đủ chức năng (xem hình, ứng dụng, tệp...).",
                    "You can still press keys, after pairing with a code the TV shows. From there open the TV’s Settings to turn ADB on, then everything works (picture, apps, files...)."))
                .setPositiveButton(t("Ghép bằng mã", "Pair with a code")) { _, _ -> startBasic(ip, false) }
                .setNegativeButton(t("Đóng", "Close")) { _, _ -> message(error, true) }
                .setOnCancelListener { message(error, true) }.show()
        }
    }.start()
}

internal fun MainActivity.startBasic(ip: String, justPaired: Boolean) {
    message(t("Đang kết nối ", "Connecting to ") + ip + "...", false)
    Thread {
        val a = Atv(filesDir, ip)
        try {
            a.connect { ui.post { if (basic === a) keysLost(a, ip) } }
            ui.post {
                if (isFinishing || host != null) { a.close(); return@post }
                prefs.edit().putBoolean("atv:$ip", true).apply()
                basic = a
                host = ip
                save(entry ?: ip, a.model)
                message(null, false)
                show(true)
                layoutRemote()
                status.text = title(ip) + " · " + t("chỉ bấm phím", "keys only")
                watchAdb(a, ip)
            }
        } catch (e: Atv.NotPaired) {
            a.close()
            if (justPaired) ui.post { message(t("TV không nhận kết nối sau khi ghép. Thử lại.", "The TV did not accept the connection after pairing. Try again."), true) }
            else pair(ip)
        } catch (e: Exception) {
            a.close()
            ui.post { message(explain(e.message), true) }
        }
    }.start()
}

/** The keys-only connection ended: away, it is opened again on return (onStart); on screen, at once. */
internal fun MainActivity.keysLost(a: Atv, ip: String) {
    basic = null
    a.close()
    if (foreground) resumeBasic(ip)
    else { keysAway = true; status.text = title(entry ?: ip) + " · " + t("kết nối lại khi mở app", "reconnects when the app is open") }
}

/** The keys-only connection again, to a TV this phone is paired with, staying on the remote; the list of TVs only if it fails. */
internal fun MainActivity.resumeBasic(ip: String) {
    waitFor(title(entry ?: ip), t("Đang kết nối lại...", "Reconnecting..."))
    Thread {
        val a = Atv(filesDir, ip)
        try {
            a.connect { ui.post { if (basic === a) keysLost(a, ip) } }
            ui.post {
                if (isFinishing || host != ip || basic != null || tv != null) { a.close(); return@post }   // the person has moved on
                basic = a
                waiting.visibility = View.GONE
                status.text = title(entry ?: ip) + " · " + t("chỉ bấm phím", "keys only")
                watchAdb(a, ip)
            }
        } catch (e: Exception) {
            a.close()
            ui.post { if (host == ip && basic == null && tv == null) disconnect(explain(e.message)) }
        }
    }.start()
}

/** While on the keys-only remote: the moment the TV starts answering on its ADB port, move over to the full connection. */
internal fun MainActivity.watchAdb(a: Atv, ip: String) {
    if (ip in adbFailed) return
    Thread {
        while (basic === a && !isFinishing) {
            Thread.sleep(4000)
            if (basic === a && open(ip, 5555, 1200)) {
                ui.post {
                    if (basic !== a || isFinishing) return@post
                    Toast.makeText(this, t("TV đã bật ADB: đang chuyển sang kết nối đầy đủ. Nhìn lên TV và chọn “Luôn cho phép”.", "ADB is on: switching to the full connection. Look at the TV and choose “Always allow”."), Toast.LENGTH_LONG).show()
                    val e = entry
                    disconnect(null)
                    connect(ip, e)
                }
                return@Thread
            }
        }
    }.apply { isDaemon = true }.start()
}

/** On a worker thread: the TV shows a code, the person types it here. */
internal fun MainActivity.pair(ip: String) {
    val p = Atv(filesDir, ip)
    try { p.startPairing() } catch (e: Exception) {
        p.close()
        ui.post { message(t("Không ghép được với TV: ", "Could not pair with the TV: ") + e.message, true) }
        return
    }
    ui.post {
        message(null, false)
        val code = input(t("Mã 6 ký tự trên TV", "The 6 characters on the TV"))
        code.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        val wrap = FrameLayout(this)
        wrap.setPadding(dp(20), dp(8), dp(20), 0)
        wrap.addView(code)
        AlertDialog.Builder(this).setTitle(t("Nhập mã đang hiện trên TV", "Type the code shown on the TV")).setView(wrap)
            .setPositiveButton(t("Ghép", "Pair")) { _, _ ->
                val typed = code.text.toString()
                Thread {
                    try {
                        p.finishPairing(typed)
                        ui.post { startBasic(ip, true) }
                    } catch (e: Exception) {
                        p.close()
                        ui.post { message(t("Mã không đúng. Bấm vào TV trong danh sách để thử lại, TV sẽ hiện mã mới.", "Wrong code. Tap the TV in the list to try again: it will show a new code."), true) }
                    }
                }.start()
            }
            .setNegativeButton(t("Hủy", "Cancel")) { _, _ -> p.close() }
            .setOnCancelListener { p.close() }.show()
    }
}

internal fun MainActivity.explain(err: String?): String {
    val e = err?.lowercase(Locale.ROOT) ?: ""
    if (refused(err))
        return t("TV từ chối kết nối: chưa bật “Gỡ lỗi ADB”.\n\nTrên TV: Cài đặt → Tùy chọn thiết bị → Giới thiệu → bấm OK 7 lần vào “Bản dựng”. Rồi Tùy chọn nhà phát triển → bật “Gỡ lỗi ADB” (hoặc “Gỡ lỗi USB”).",
            "The TV refused the connection: “ADB debugging” is off.\n\nOn the TV: Settings → Device Preferences → About → press OK 7 times on “Build”. Then Developer options → turn on “ADB debugging” (or “USB debugging”).")
    if (e.contains("timed out") || e.contains("timeout") || e.contains("unreachable") || e.contains("no route") || e.contains("failed to connect"))
        return t("Không tới được TV ở địa chỉ này. TV có đang bật không? Điện thoại phải cùng Wi-Fi với TV, hoặc cả hai cùng bật Tailscale (khi đó dùng IP 100.x của TV).",
            "Cannot reach the TV at this address. Is it on? The phone must be on the TV’s Wi-Fi, or both must have Tailscale on (then use the TV’s 100.x IP).") + "\n\n" +
            t("Nếu TV đang tắt, bấm nút nguồn cạnh tên TV để đánh thức.", "If the TV is off, tap the power button next to it to wake it.") + "\n\n(" + err + ")"
    return t("Lỗi: ", "Error: ") + err
}
