package dev.tvremote.core

/** Turns the errors people actually meet (a TV with the wrong date, no Internet, a TV that stops answering) into words they can act on. */
object Errors {
    fun say(e: Throwable, vi: Boolean): String {
        fun t(v: String, en: String) = if (vi) v else en
        val all = generateSequence(e) { it.cause }.joinToString(" ") { it.javaClass.simpleName + ": " + (it.message ?: "") }
        SetupNeed.say(e, vi)?.let { return it }
        return when {
            Regex("SSLHandshake|CertPathValidator|Chain validation|CERTIFICATE_VERIFY|not yet valid|expired", RegexOption.IGNORE_CASE).containsMatchIn(all) ->
                t("Không mở được kết nối bảo mật. Ngày giờ trên TV có đúng không? (Cài đặt → Hệ thống → Ngày và giờ)",
                    "Could not open a secure connection. Is the TV's date and time right? (Settings → System → Date & time)")
            all.contains("UnknownHost") -> t("TV chưa vào được Internet.", "The TV cannot reach the Internet.")
            Regex("SocketTimeout|timed out|ConnectException|Connection reset", RegexOption.IGNORE_CASE).containsMatchIn(all) ->
                t("Mạng chậm hoặc vừa mất kết nối. Thử lại sau ít phút.", "The network is slow or dropped. Try again in a few minutes.")
            all.contains("stopped answering") || all.contains("stopped taking data") ->
                t("TV ngừng trả lời giữa chừng. Thử lại; vẫn vậy thì khởi động lại TV.", "The TV stopped answering midway. Try again; if it keeps happening, restart the TV.")
            all.contains("copy to the TV incomplete") -> t("Chép tệp lên TV không trọn vẹn. Thử lại.", "The file did not reach the TV whole. Try again.")
            all.contains("no sign-in link") -> t("Không đọc được link đăng nhập trên màn hình TV. Mở Tailscale trên TV và quét mã QR nó hiện.",
                "Could not read the sign-in link off the TV's screen. Open Tailscale on the TV and scan the QR code it shows.")
            all.contains("no app that can open") -> t("TV không có app mở loại tệp này (cài VLC rồi thử lại).", "The TV has no app for this kind of file (install VLC, then retry).")
            Regex("INSUFFICIENT_STORAGE|No space", RegexOption.IGNORE_CASE).containsMatchIn(all) -> t("TV hết bộ nhớ trống.", "The TV is out of storage.")
            // installing an APK: the same words as friendly() in js/core.js
            Regex("apk needs sdk (\\d+), the tv has sdk (\\d+)").containsMatchIn(all) -> {
                val m = Regex("apk needs sdk (\\d+), the tv has sdk (\\d+)").find(all)!!.groupValues
                t("APK này cần Android ${android(m[1])} trở lên; TV đang chạy Android ${android(m[2])}. Tìm bản cũ hơn của app.",
                    "This APK needs Android ${android(m[1])} or newer; the TV runs Android ${android(m[2])}. Look for an older version of the app.")
            }
            Regex("apk is built for (\\S+), the tv runs (\\S+)").containsMatchIn(all) -> {
                val m = Regex("apk is built for (\\S+), the tv runs (\\S+)").find(all)!!.groupValues
                t("APK này dành cho chip ${m[1]}, còn TV dùng ${m[2]}. Tải bản cho đúng loại chip (thường là armeabi-v7a cho TV).",
                    "This APK is built for ${m[1]} chips; the TV uses ${m[2]}. Get the build for the right chip (usually armeabi-v7a for TVs).")
            }
            all.contains("removed by the TV right after install") -> t("TV báo cài xong nhưng tự gỡ app này ngay sau đó (box của nhà mạng chặn một số app).",
                "The TV said it installed the app, then removed it at once (operator boxes block some apps).")
            Regex("INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match", RegexOption.IGNORE_CASE).containsMatchIn(all) ->
                t("TV đã có app này nhưng từ nguồn khác (khác chữ ký). Gỡ bản đang có rồi cài lại.", "The TV has this app from another source (another signature). Remove it, then install again.")
            all.contains("INSTALL_FAILED_VERSION_DOWNGRADE") -> t("TV đang có bản mới hơn của app này.", "The TV has a newer version of this app.")
            all.contains("not an APK file") -> t("Tệp này không phải APK.", "This file is not an APK.")
            all.contains("protected: the TV needs this app") -> t("TV cần app này, không gỡ được.", "The TV needs this app; it cannot be removed.")
            else -> e.message ?: e.javaClass.simpleName
        }
    }

    private val ANDROID = mapOf(21 to "5.0", 22 to "5.1", 23 to "6", 24 to "7", 25 to "7.1", 26 to "8", 27 to "8.1", 28 to "9", 29 to "10",
        30 to "11", 31 to "12", 32 to "12L", 33 to "13", 34 to "14", 35 to "15", 36 to "16")
    private fun android(sdk: String) = ANDROID[sdk.toIntOrNull()] ?: "SDK $sdk"
}
