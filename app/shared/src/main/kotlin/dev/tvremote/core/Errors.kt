package dev.tvremote.core

/** Turns the errors people actually meet (a TV with the wrong date, no Internet, a TV that stops answering) into words they can act on. */
object Errors {
    fun say(e: Throwable, vi: Boolean): String {
        fun t(v: String, en: String) = if (vi) v else en
        val all = generateSequence(e) { it.cause }.joinToString(" ") { it.javaClass.simpleName + ": " + (it.message ?: "") }
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
            Regex("INSUFFICIENT_STORAGE|No space", RegexOption.IGNORE_CASE).containsMatchIn(all) -> t("TV hết bộ nhớ trống.", "The TV is out of storage.")
            else -> e.message ?: e.javaClass.simpleName
        }
    }
}
