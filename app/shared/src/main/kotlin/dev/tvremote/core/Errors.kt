package dev.tvremote.core

import dev.tvremote.core.web.Cancelled
import dev.tvremote.core.web.NoRoom
import dev.tvremote.core.remote.Roku
import dev.tvremote.core.remote.Vidaa
import dev.tvremote.core.ontv.SetupNeed
import dev.tvremote.core.ontv.Tailscale
/** Turns the errors people actually meet (a TV with the wrong date, no Internet, a TV that stops answering) into words they can act on. */
object Errors {

    fun say(e: Throwable, lang: String): String {
        fun t(id: String, vararg a: Pair<String, Any?>) = Words.get(lang, id, *a)
        val all = generateSequence(e) { it.cause }.joinToString(" ") { it.javaClass.simpleName + ": " + (it.message ?: "") }
        WireLog.all.add("·", "error " + all)
        SetupNeed.say(e, lang)?.let { return it }
        if (all.contains("stopped") && generateSequence(e) { it.cause }.any { it is Cancelled }) return t("errors.stopped")
        NoRoom.parse(all)?.let { return it.say(lang) }                  // the TV has no room: sizes, and what to do
        return when {
            all.contains(dev.tvremote.update.Release.MISMATCH) || all.contains(dev.tvremote.update.Release.UNSIGNED) ->
                t("errors.downloaded_update_does_not")
            all.contains("adb pair: wrong code") -> t("errors.pairing_code_wrong_has")
            all.contains("adb pair: not a pairing port") -> t("errors.not_pairing_port_use")
            all.contains("adb tls: this device is not paired") -> t("errors.tv_not_paired_with")
            all.contains("adb tls: this app cannot pair") -> t("errors.app_cannot_pair_through")
            Regex("SSLHandshake|CertPathValidator|Chain validation|CERTIFICATE_VERIFY|not yet valid|expired", RegexOption.IGNORE_CASE).containsMatchIn(all) ->
                t("errors.could_not_open_secure")
            all.contains(Lan.BLOCKED) -> t("errors.vpn_so_tv_at")
            all.contains(dev.tvremote.core.adb.AdbLink.ASKING) -> t("errors.tv_asks_allow_usb")
            all.contains(dev.tvremote.core.adb.AdbLink.STILL) -> t("errors.still_connecting_tv_over")
            all.contains("UnknownHost") -> t("errors.tv_cannot_reach_internet")
            Regex("SocketTimeout|timed out|ConnectException|Connection reset", RegexOption.IGNORE_CASE).containsMatchIn(all) ->
                t("errors.network_slow_dropped_try")
            all.contains("stopped answering") || all.contains("stopped taking data") ->
                t("errors.tv_stopped_answering_midway")
            all.contains("copy to the TV incomplete") -> t("errors.file_did_not_reach")
            all.contains("no sign-in link") -> t("errors.could_not_read_sign")
            all.contains("no app that can open") -> t("errors.tv_has_no_app")
            Regex("INSUFFICIENT_STORAGE|No space", RegexOption.IGNORE_CASE).containsMatchIn(all) -> t("errors.tv_out_storage")
            // installing an APK: the same words as friendly() in js/core.js
            Regex("apk needs sdk (\\d+), the tv has sdk (\\d+)").containsMatchIn(all) -> {
                val m = Regex("apk needs sdk (\\d+), the tv has sdk (\\d+)").find(all)!!.groupValues
                t("errors.apk_needs_android_newer", "need" to android(m[1]), "have" to android(m[2]))
            }
            Regex("apk is built for (\\S+), the tv runs (\\S+)").containsMatchIn(all) -> {
                val m = Regex("apk is built for (\\S+), the tv runs (\\S+)").find(all)!!.groupValues
                t("errors.apk_built_chips_tv", "apk" to m[1], "tv" to m[2])
            }
            all.contains("removed by the TV right after install") -> t("errors.tv_said_installed_app")
            Regex("INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match", RegexOption.IGNORE_CASE).containsMatchIn(all) ->
                t("errors.tv_has_app_from")
            all.contains("INSTALL_FAILED_VERSION_DOWNGRADE") -> t("errors.tv_has_newer_version")
            all.contains("not an APK file") -> t("errors.file_not_apk")
            all.contains(Roku.LIMITED) -> t("errors.roku_blocks_control_from")
            all.contains(Vidaa.NO_CERT) -> t("errors.hisense_tv_needs_certificate")
            all.contains("protected: the TV needs this app") -> t("errors.tv_needs_app_cannot")
            else -> e.message ?: e.javaClass.simpleName
        }
    }

    private val ANDROID = mapOf(21 to "5.0", 22 to "5.1", 23 to "6", 24 to "7", 25 to "7.1", 26 to "8", 27 to "8.1", 28 to "9", 29 to "10",
        30 to "11", 31 to "12", 32 to "12L", 33 to "13", 34 to "14", 35 to "15", 36 to "16")
    private fun android(sdk: String) = ANDROID[sdk.toIntOrNull()] ?: "SDK $sdk"
}
