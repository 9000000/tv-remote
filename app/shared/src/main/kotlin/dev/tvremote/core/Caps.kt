package dev.tvremote.core

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.os.Looper
import android.view.Display

/**
 * What the TV's hardware can do, for the Capabilities card of the Health tab: video and audio codecs (and which are done in
 * hardware), HDR types, display modes, the sound formats the HDMI/optical output takes, and the system features. Only the
 * TV's own Android knows this for sure (the codec XML files differ per maker and are only half the story), so it is asked in
 * place: TV Setup calls [report] itself, the phone and the computer start [main] on the TV with app_process, as for [Meta].
 *
 * Plain lines the page reads (js/tvread.js capsRead), each part on its own so one that fails leaves the rest:
 *   sdk <n> <release>         what the page's "what this TV can do" lines depend on
 *   codec dec|enc hw|sw <name> <mime> <max WxH or -> <flags,comma-separated or ->
 *   hdr <type> ...            dv hdr10 hlg hdr10+
 *   mode <W>x<H>@<fps>
 *   audio <device> <encoding> ...
 *   feature <name>
 */
object Caps {
    @JvmStatic
    fun main(args: Array<String>) {
        @Suppress("DEPRECATION")
        Looper.prepareMainLooper()
        val at = Class.forName("android.app.ActivityThread")
        val ctx = at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null)) as Context
        print(report(ctx))
        System.out.flush()
        System.exit(0)                                                // the system context leaves threads running
    }

    fun report(ctx: Context): String {
        val out = StringBuilder("sdk ").append(Build.VERSION.SDK_INT).append(' ').append(Build.VERSION.RELEASE).append('\n')
        try { codecs(out) } catch (ignored: Throwable) { }
        try { display(ctx, out) } catch (ignored: Throwable) { }
        try { audio(ctx, out) } catch (ignored: Throwable) { }
        try { for (f in ctx.packageManager.systemAvailableFeatures) if (f.name != null) out.append("feature ").append(f.name).append('\n') } catch (ignored: Throwable) { }
        return out.toString()
    }

    private fun codecs(out: StringBuilder) {
        for (c in MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos) {
            val hw = if (Build.VERSION.SDK_INT >= 29) c.isHardwareAccelerated
                else !c.name.lowercase().let { it.startsWith("omx.google.") || it.startsWith("c2.android.") || it.contains(".sw.") }
            if (Build.VERSION.SDK_INT >= 29 && c.isAlias) continue    // the same codec under an older name
            for (mime in c.supportedTypes) {
                val caps = try { c.getCapabilitiesForType(mime) } catch (e: Exception) { null }
                val v = caps?.videoCapabilities
                val size = if (v != null) "${v.supportedWidths.upper}x${v.supportedHeights.upper}" else "-"
                val flags = ArrayList<String>()
                val profiles = caps?.profileLevels?.map { it.profile }?.toSet() ?: emptySet()
                when (mime.lowercase()) {
                    "video/hevc" -> {
                        if (MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 in profiles) flags.add("10bit")
                        if (MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 in profiles) flags.add("hdr10")
                        if (MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus in profiles) flags.add("hdr10+")
                    }
                    "video/x-vnd.on2.vp9" -> {
                        if (MediaCodecInfo.CodecProfileLevel.VP9Profile2 in profiles) flags.add("10bit")
                        if (MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR in profiles) flags.add("hdr10")
                    }
                    "video/av01" -> if (profiles.any { it == 2 || it == 0x1000 || it == 0x2000 }) flags.add("10bit")   // AV1ProfileMain10[HDR10[Plus]]
                }
                if (Build.VERSION.SDK_INT >= 29 && c.isSoftwareOnly) flags.add("soft")
                if (caps?.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback) == true) flags.add("secure")
                if (caps?.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback) == true) flags.add("tunnel")
                out.append("codec ").append(if (c.isEncoder) "enc" else "dec").append(if (hw) " hw " else " sw ").append(c.name).append(' ')
                    .append(mime).append(' ').append(size).append(' ').append(if (flags.isEmpty()) "-" else flags.joinToString(",")).append('\n')
            }
        }
    }

    private fun display(ctx: Context, out: StringBuilder) {
        val d = (ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY) ?: return
        val types = d.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
        out.append("hdr")
        for (t in types) when (t) { 1 -> out.append(" dv"); 2 -> out.append(" hdr10"); 3 -> out.append(" hlg"); 4 -> out.append(" hdr10+") }
        out.append('\n')
        for (m in d.supportedModes) out.append("mode ").append(m.physicalWidth).append('x').append(m.physicalHeight).append('@')
            .append(Math.round(m.refreshRate * 100) / 100.0).append('\n')
    }

    private fun audio(ctx: Context, out: StringBuilder) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        for (dev in am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            val kind = when (dev.type) {
                AudioDeviceInfo.TYPE_HDMI -> "hdmi"; AudioDeviceInfo.TYPE_HDMI_ARC -> "arc"; 29 -> "earc"   // TYPE_HDMI_EARC (API 31)
                AudioDeviceInfo.TYPE_LINE_DIGITAL -> "optical"; AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bluetooth"; else -> continue
            }
            val enc = LinkedHashSet<String>()
            for (e in dev.encodings) encoding(e)?.let { enc.add(it) }
            out.append("audio ").append(kind)
            for (e in enc) out.append(' ').append(e)
            out.append('\n')
        }
    }

    private fun encoding(e: Int): String? = when (e) {
        AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_FLOAT -> "pcm"
        AudioFormat.ENCODING_AC3 -> "ac3"; AudioFormat.ENCODING_E_AC3 -> "eac3"
        AudioFormat.ENCODING_DTS -> "dts"; AudioFormat.ENCODING_DTS_HD -> "dtshd"
        AudioFormat.ENCODING_DOLBY_TRUEHD -> "truehd"; 18 -> "eac3joc"   // ENCODING_E_AC3_JOC (API 28): Dolby Atmos over Dolby Digital Plus
        AudioFormat.ENCODING_IEC61937 -> "iec61937"; 19 -> "ac4"         // ENCODING_AC4 (API 28)
        else -> null
    }
}
