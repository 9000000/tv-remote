package dev.tvremote.core.ontv

/**
 * What Optimize TV knows by brand, from what owners of each TV reported in cleanyourtv (github.com/Gathin23/cleanyourtv, MIT)
 * and its forks, issues and pull requests (October 2026, read again up to PR #573). [SUGGEST] is ticked by default, so it holds only what sends data,
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
        "com.google.android.partnersetup" to "Google partner setup",
        // partner-app installers of other makers (Play Auto Install configs, one per maker)
        "android.autoinstalls.config.JVC" to "JVC app installer",
        "android.autoinstalls.config.SW" to "Skyworth app installer",
        "android.autoinstalls.config.skyworth" to "Skyworth app installer",
        "android.autoinstalls.config.haier.hanyang" to "Haier app installer",
        "android.autoinstalls.config.hisense.xiaoyushan" to "Hisense app installer",
        "android.autoinstalls.config.nvidia" to "NVIDIA app installer",
        "android.autoinstalls.config.sei" to "Free app installer",
        "android.autoinstalls.config.soc.amlogic" to "Amlogic app installer",
        "android.autoinstalls.config.tcl.device" to "TCL app installer",
        "android.autoinstalls.config.tpv.device" to "TP Vision app installer",
        "android.autoinstalls.config.xioami.dangal" to "Xiaomi app installer",
        "android.autoinstalls.config.xioami.mibox3" to "Xiaomi app installer",
        "android.autoinstalls.config.hisense" to "Hisense app installer",
        "android.autoinstalls.config.hisense.hayward" to "Hisense app installer",
        "android.autoinstalls.config.hkc" to "HKC app installer",
        "android.autoinstalls.config.sharp" to "Sharp app installer",
        "android.autoinstalls.config.scbc.device" to "SCBC app installer",
        "android.autoinstalls.config.sdmc" to "SDMC app installer",
        "android.autoinstalls.config.vestel.coma" to "Vestel app installer",
        "android.autoinstalls.config.vestel.cosmos" to "Vestel app installer",
        "android.autoinstalls.config.vestel.medusa" to "Vestel app installer",
        // content recognition (what is on screen, sent to a measurement company), several makers
        "tv.anoki.acr.anokiacroptin" to "Anoki content recognition",
        "tv.anoki.acr.controller" to "Anoki content recognition",
        "tv.inscape.tvclient.inscapeacr" to "Inscape content recognition",
        // Sony
        "com.sony.dtv.da.service" to "Sony device agent",
        "com.qterics.da.product" to "Sony remote support agent",
        "com.sony.dtv.sonybugreportsys" to "Sony bug reports",
        "android.autoinstalls.config.sony.bravia" to "Sony app installer",
        "com.sonyericsson.idd.agent" to "Sony usage data",
        "com.sony.dtv.promos" to "Sony promotions (My BRAVIA)",
        "com.sony.dtv.demosystemsupport" to "Sony demo support",
        "tv.samba.ssm.ui" to "Samba TV opt-in screens",
        "com.sony.dtv.demomode" to "Sony demo mode",
        // Xiaomi
        "com.miui.tv.analytics" to "Xiaomi analytics",
        "com.xiaomi.statistic" to "Xiaomi statistics",
        "com.xiaomi.tvqs.overseas.y24" to "Xiaomi quality stats",
        "com.xm.webcontent" to "Xiaomi web content",
        "com.mitv.tvhome.oemtab" to "Mi Home tab",
        "com.mitv.tvhome.michannel" to "Mi Channel",
        "com.mitv.tvhome.mitvplus" to "Mi TV+",
        "android.autoinstalls.config.xiaomi.amelie" to "Xiaomi app installer",
        "com.xiaomi.tvqs" to "Xiaomi quality stats",
        "com.miui.misightservice" to "Xiaomi usage data",
        "com.mitv.cloudcontrol" to "Xiaomi remote app push",
        "com.miui.analytics" to "Xiaomi MIUI analytics",
        // TCL
        "com.tcl.bi" to "TCL analytics",
        "com.tcl.logkit" to "TCL log uploader",
        "com.tcl.esticker" to "TCL e-sticker (shop demo)",
        "com.tcl.bootadservice" to "TCL start-up ads",
        "com.tcl.smartlink.core" to "TCL silent app installer",
        // Hisense
        "com.hisense.app.logreport" to "Hisense log reports",
        "android.autoinstalls.config.hisense.laohushan" to "Hisense app installer",
        "com.vidaa.oemtabcontentprovider" to "Hisense tab",
        "com.vt.storemode" to "Hisense store mode",
        "com.hisense.storemode" to "Hisense store mode",
        "com.hisense.logupload" to "Hisense log upload",
        "com.hisense.platformreport" to "Hisense platform reports",
        "com.hisense.business.ad" to "Hisense business ads",
        "com.hisense.service.ad" to "Hisense ad service",
        "com.hisense.demonstrate" to "Hisense demo",
        // Philips / TP Vision (org.droidtv): the usage logger and the DAC analytics pair (gel runs inside the candeebug process)
        "org.droidtv.usagelogger" to "Philips usage logger",
        "org.droidtv.candeebug" to "Philips DAC analytics",
        "org.droidtv.gel" to "Philips DAC analytics",
        "org.droidtv.demome" to "Philips demo",
        "org.droidtv.sticker" to "Philips shop sticker",
        "org.droidtv.tv.frameworklogger" to "Philips framework logger",
        // Vestel (JVC, Toshiba, Cecotec), CVTE (Vision, Challenger), XGIMI, Verizon, HTC, Coocaa-made sets
        "com.vestel.vestelreporter" to "Vestel reporter",
        "com.vestel.smartlogger" to "Vestel logger",
        "com.cvte.gaia.ad.service" to "CVTE ad service",
        "com.cvte.wonder.cloud.service" to "CVTE ads and analytics",
        "com.cvte.tv.pictorial" to "CVTE screensaver ads",
        "com.xgimi.datareporter" to "XGIMI data reporter",
        "com.xgimi.bugreportsender" to "XGIMI bug reports",
        "com.verizon.tv.ams" to "Verizon event recorder",
        "com.wnc.wnclogger" to "WNC logger",
        "com.htc.eventuploadservice" to "HTC event upload",
        "com.smartdevice.common.demonow" to "Shop demo",
        // Panasonic, NVIDIA, Realtek sets
        "com.panasonic.demomode" to "Panasonic demo mode",
        "com.nvidia.stats" to "NVIDIA statistics",
        "com.nvidia.feedback" to "NVIDIA feedback",
        "com.apps.esticker3" to "Shop energy-label sticker",
        // Amazon Fire TV
        "com.amazon.kso.blackbird" to "Fire TV ads (screensaver)",
        "com.amazon.tv.fw.metrics" to "Fire TV metrics",
        "com.amazon.wirelessmetrics.service" to "Fire TV Wi-Fi metrics",
        "com.amazon.perfcollection" to "Fire TV performance data",
        "com.amazon.hybridadidservice" to "Fire TV ad ID",
        "com.amazon.device.sale.service" to "Amazon deals",
        "com.amazon.tv.acr" to "Fire TV content recognition",
        "com.amazon.client.metrics.api" to "Fire TV metrics API",
        "com.amazon.metrics.api" to "Fire TV metrics API",
        "com.amazon.neo.minerva" to "Fire TV metrics (Minerva)",
        "com.amazon.neo.minerva.logcat" to "Fire TV log collector",
        "com.amazon.perfc" to "Fire TV performance data",
        "com.amazon.dp.logger" to "Fire TV logger",
        "com.amazon.wifilocker" to "Fire TV Wi-Fi password upload",
        "com.amazon.sneakpeek" to "Fire TV sponsored previews",
        "com.amazon.tv.easyupgrade" to "Fire TV upgrade promos",
        "com.amazon.shoptv.client" to "Amazon shopping on TV",
        "com.amazon.shoptv.firetv.client" to "Amazon shopping on TV",
        "com.amazon.tv.releasenotes" to "Fire TV release notes",
        "com.amazon.device.telemetry.emitter" to "Fire TV telemetry",
        "com.amazon.tv.democontent.provider" to "Fire TV demo content")

    /**
     * Never offered or turned off on top of [Setup.PROTECTED]: an owner found something stop working without them. TCL: the
     * remote, its pairing, Wi-Fi, the tuner, the Input key (suspension), the source menu (sourcemananger), the keyboard. Xiaomi: the
     * source switcher (livetv), the Bluetooth remote's pairing (tvsetup.partnercustomizer). Sony: inputs, HDMI-CEC, the remote.
     * Hisense / Vidda / Vu (VIDAA): the tuner, the source panel (tiletransition: HDMI does nothing without it), the keyboard, and
     * the smartsense / aiot family (it sent a Hisense to Android Recovery). Philips (org.droidtv): the TV engine; eum and aurora
     * hang it when off. Realtek, Homwee, CVTE / Haier, Skyworth, Freebox, JMGO and projector boards: the tuner, the remote keys and
     * pairing, the factory service the Input key goes through. Fire TV: the app store and its updates, settings, casting.
     * MediaTek and Amlogic: the TV itself on most of these brands. partnercustomizer (in PROTECTED already): its loss crash-loops
     * the TV after the next restart. MTC: fusion.android.tv.demo looks like a retail demo and is the Input button.
     * Any *.partnercustomizer: the remote's keys and the stock home's Inputs icon (TCL, Realtek). Xiaomi tvmanager: XiaoAI opens apps
     * through it. Vestel, CVTE, XGIMI, SDMC, Skyworth / HiSilicon, Kalley (com.apps / com.mk): inputs, keys, pairing, settings, updates.
     */
    /**
     * Factory test apps, never opened: once was enough on a Haier (CVTE, com.cvte.tv.hkcfac) to switch on factory mode, the Input key
     * and HDMI-CEC dead, and at the next boot Play services, Netflix and Cast turned off where the shell may not turn them back on
     * (cleanyourtv #470). A part of the name that ends in "fac" or holds "factory".
     */
    val FACTORY = Regex("(?i)(.*\\.)?(\\w*fac|\\w*factory\\w*)(\\..*)?")

    val KEEP = Regex(
        "com\\.tcl\\.(tv|tvinput|tvpassthrough|sourcemananger|keycustomfunctionservice|keyhelp|framework\\.custom|settings|inputmethod\\.international|globalkeyoverlay|TFDS\\.service|xian\\.StartandroidService|showmode" +
            "|system\\.?server|systemserver|tcl_bt_rcu_service|autopair|suspension|providers\\.config|initsetup|factory\\.view|dashboard|eva|appstatecontroller|rc\\.ota|UpdatePeripheral|useragreement|copydatatotv|guard|cyberui)" +
        "|.*\\.partnercustomizer|com\\.android\\.WifiCountryService|com\\.tvos|com\\.sohu\\.inputmethod\\.sogou\\.tv" +
        "|com\\.mitv\\.livetv|mitv\\.service|com\\.mitv\\.overseaservice|com\\.mi\\.bluetooth|com\\.xiaomi\\.android\\.tvsetup\\.partnercustomizer|com\\.xiaomi\\.mitv\\.(res|tvmanager|upgrade|tvplayer|settings|smartpanel)|com\\.mitv\\.(tvhome|codec\\.update)" +
        "|com\\.sony\\.dtv\\.(tvx|tvinput\\..*|braviasync.*|remotepairing|uinputservice|imeproxy|dialservice|tvapi|sonysystemservice|rcpowerkey|settingsprovider|smartui|tvlin|tvplayer.*|remotecontrolsettings|systemupdate|update|youview|irblaster|irbservice)" +
            "|com\\.youview\\.tv\\.servicehost|com\\.uei\\.quicksetsdk" +
        "|com\\.hisense\\.(hitvinput|kpad|favkey|uifac|audio_update_service|bluetoothpair|mixbar|tv\\.(hitvinput|inputs|baseservice|tiletransition|smartsense|smartservice|customerlauncher))" +
            "|com\\.hisense\\.tv\\.hitvinput|com\\.jamdeo\\.tv\\.(livetv|service)|com\\.mstar\\.netflixobserver|com\\.vidaa\\.persistent\\.service" +
            "|com\\.vt\\.tv\\.(ota|smartsense.*|ai_sense|aiot.*|tvcenter|input|commonui|smartservice|aipq|videocolor|customerlauncher)" +
            "|com\\.vt\\.(remoteupgrade|source\\..*|tvservice|avmiddleware|tvplayer|edv\\.drmp|eula|udpserver)" +
        "|org\\.droidtv\\.(ambiair|ambihuecommon|alhuewizard|audiocalibration|calmancalibrator|bluetoothaudiosink|xtv|xtvsystem|playtv|channels|epgdata|dvbi|teletext|ci|hdmiservice|euinstallertc|euinstallersat" +
            "|hbbtvopapp|tv\\.tptvservice|icp|sunrise|tvsystemui|tvpowermanagerservice|GlobalKey|lkb|settings|menudataapp|homeintentresolver|oad|devicesetup|networkwizard|nettvregistration|nettvbrowser" +
            "|contentexplorer|eum|aurora|fastip|exoplayerservice|usbbreakin|dlna)|com\\.easy\\.oad|com\\.vewd\\.core\\.browserui|com\\.tpv_soem\\..*" +
        "|com\\.realtek\\.(dtv|tv|tv\\.atv|tv\\.passthrough|systemservice|menu|netflix\\.setting)|com\\.realtek\\.keyinterceptserver|com\\.apps\\.(ota|authota|smartkey|tvmanager|globalkeyhandler|bluetoothremote|virtualkey)|com\\.mk\\.(service\\.tvmidware|tv\\.fivekey)|com\\.realsil\\.ota" +
        "|com\\.homwee\\.(dmt\\.upgrade|factory|synckey|dynamicbacklight)|com\\.gdch\\.android\\.tv\\.inputplayer" +
        "|com\\.haier\\.(tv\\.hmagicboard|hkcfac|appmanager\\.dispatcher|bt_pair_tips)|com\\.cvte\\.tv\\.(hkcfac|at|fac\\..*|androidsetting|api\\.impl|setting|globalui|systemupgrade)|com\\.cvte\\.(tvservice|hotkey\\.dispatcher)|com\\.at\\.factory\\..*|com\\.example\\.bt_pair_tips|com\\.telink\\.lt\\.ota" +
        "|fusion\\.android\\.tv\\.demo" +
        "|com\\.smartdevice\\.(livetv|global\\.service|factory|duplebluetooth|dailyshortcut)|com\\.cltv\\.(hybrid|mal)|com\\.coocaa\\.factory" +
        "|net\\.oqee\\.androidtv|org\\.dtvkit\\.inputsource|fr\\.freebox\\..*|com\\.nes\\.(dtvkitdatabackup|blerc|tvglobalkeyhandler|freeboxburnkey)" +
        "|com\\.sdt\\.(projector\\.observer|customization|globalkey|app\\.skysettings|projectionservice)|com\\.hisilicon\\..*|com\\.skyworthdigital\\..*" +
        "|com\\.vestel\\.(tv\\..*|overlay\\..*)|com\\.xgimi\\.(autokst|windowsystem|tof|misckey|uiconfiguration|xrmservice|xgimiservice|hardware|inputs|mirroring)|com\\.nbcast\\.wirelessscreen" +
        "|com\\.sdmc\\.(tv|bluetoothconnect|GlobalKey)|com\\.google\\.android\\.tv\\.inputplayer|com\\.mft\\.(rcupair|frontpanelledsservice)|com\\.jmgo\\.(hippo|autofocus|tofautofocus|autokeystone|dewarping|ptz|autobrightness|a2dpsinkservice|android\\.tv\\.globalkeyoverlay|remotecontrolota|factory)" +
        "|com\\.google\\.android\\.tv\\.dfuservice|com\\.dolby\\.android\\.audio\\.service|com\\.gretzky\\.GlobalKey" +
        "|com\\.amazon\\.(venezia|tigris|vizzini|cardinal|logan|tv\\.(settings.*|oobe|quicksettings|livetv|launcher|ime)|whisperlink.*|whisperjoin.*|aria)" +
        "|com\\.mediatek\\..*|com\\.droidlogic(\\..*)?")
}
