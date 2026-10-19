package dev.tvremote.core.ontv

/**
 * What Optimize TV knows by brand, from what owners of each TV reported in cleanyourtv (github.com/Gathin23/cleanyourtv, MIT)
 * and its forks, issues and pull requests (October 2026). [SUGGEST] is ticked by default, so it holds only what sends data,
 * shows ads or demos, or installs apps by itself: nothing a remote button, an input, the sound or a store needs. Each is
 * skipped on a TV that lacks it, and Undo turns it back on ([Setup.OFF_LIST]). The sources, what was left out and why, and how
 * to read them again: dev/bloat-sources.md.
 */
object Bloat {
    val SUGGEST: Map<String, String> = linkedMapOf(
        // every brand with Google's apps
        "com.google.android.feedback" to "Google Feedback",
        "com.google.android.tv.bugreportsender" to "Bug report sender",
        "com.android.tv.feedbackconsent" to "Feedback consent",
        "android.autoinstalls.config.google.gtvpai" to "Google TV partner app installer",
        // Sony
        "com.sony.dtv.da.service" to "Sony device agent",
        "com.qterics.da.product" to "Sony remote support agent",
        "com.sony.dtv.sonybugreportsys" to "Sony bug reports",
        "android.autoinstalls.config.sony.bravia" to "Sony app installer",
        // Xiaomi
        "com.miui.tv.analytics" to "Xiaomi analytics",
        "com.xiaomi.statistic" to "Xiaomi statistics",
        "com.xiaomi.tvqs.overseas.y24" to "Xiaomi quality stats",
        "com.xm.webcontent" to "Xiaomi web content",
        "com.mitv.tvhome.oemtab" to "Mi Home tab",
        "com.mitv.tvhome.michannel" to "Mi Channel",
        "com.mitv.tvhome.mitvplus" to "Mi TV+",
        "android.autoinstalls.config.xiaomi.amelie" to "Xiaomi app installer",
        "com.xiaomi.mitv.tvmanager" to "Xiaomi TV Manager (cleaner)",
        // TCL
        "com.tcl.bi" to "TCL analytics",
        "com.tcl.logkit" to "TCL log uploader",
        "com.tcl.esticker" to "TCL e-sticker (shop demo)",
        "com.tcl.bootadservice" to "TCL start-up ads",
        // Hisense
        "com.hisense.app.logreport" to "Hisense log reports",
        "android.autoinstalls.config.hisense.laohushan" to "Hisense app installer",
        "com.vidaa.oemtabcontentprovider" to "Hisense tab",
        "com.vt.storemode" to "Hisense store mode",
        "com.vt.tv.mobiledeviceconnectiontips" to "Hisense phone tips",
        // Amazon Fire TV
        "com.amazon.kso.blackbird" to "Fire TV ads (screensaver)",
        "com.amazon.tv.fw.metrics" to "Fire TV metrics",
        "com.amazon.wirelessmetrics.service" to "Fire TV Wi-Fi metrics",
        "com.amazon.perfcollection" to "Fire TV performance data",
        "com.amazon.hybridadidservice" to "Fire TV ad ID",
        "com.amazon.device.sale.service" to "Amazon deals",
        "com.amazon.tv.acr" to "Fire TV content recognition")

    /**
     * Never offered or turned off on top of [Setup.PROTECTED]: an owner found something stop working without them. TCL: the
     * remote, its pairing, Wi-Fi, the tuner, the Input key (suspension). Xiaomi: the source switcher (livetv). Sony: inputs,
     * HDMI-CEC, the remote. Hisense: the tuner and inputs. Fire TV: the app store and its updates, settings, casting.
     * MediaTek: the TV itself on most of these brands. partnercustomizer (in PROTECTED already): its loss crash-loops the TV
     * after the next restart.
     */
    val KEEP = Regex(
        "com\\.tcl\\.(tv|tvinput|system\\.?server|systemserver|tcl_bt_rcu_service|autopair|suspension|providers\\.config|initsetup|factory\\.view|dashboard|eva)" +
        "|com\\.mitv\\.livetv|mitv\\.service|com\\.mitv\\.overseaservice|com\\.mi\\.bluetooth" +
        "|com\\.sony\\.dtv\\.(tvx|tvinput\\..*|braviasync.*|remotepairing|uinputservice|imeproxy|dialservice)" +
        "|com\\.hisense\\.hitvinput|com\\.vt\\.tv\\.ota|com\\.vt\\.remoteupgrade" +
        "|com\\.amazon\\.(venezia|tv\\.settings.*|whisperlink.*|whisperjoin.*|aria)" +
        "|com\\.mediatek\\..*")
}
