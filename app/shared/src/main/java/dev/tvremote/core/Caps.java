package dev.tvremote.core;

import android.content.Context;
import android.content.pm.FeatureInfo;
import android.hardware.display.DisplayManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.Looper;
import android.view.Display;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * What the TV's hardware can do, for the Capabilities card of the Health tab: video and audio codecs (and which are done in
 * hardware), HDR types, display modes, the sound formats the HDMI/optical output takes, and the system features. Only the
 * TV's own Android knows this for sure (the codec XML files differ per maker and are only half the story), so it is asked in
 * place: TV Setup calls report() itself, the phone and the computer start main() on the TV with app_process, as for Meta
 * (and, like it, in Java so tvr-helper.jar needs no Kotlin library).
 *
 * Plain lines the page reads (js/tvread.js capsRead), each part on its own so one that fails leaves the rest:
 *   sdk <n> <release>         what the page's "what this TV can do" lines depend on
 *   codec dec|enc hw|sw <name> <mime> <max WxH or -> <flags,comma-separated or ->
 *   hdr <type> ...            dv hdr10 hlg hdr10+
 *   mode <W>x<H>@<fps>
 *   audio <device> <encoding> ...
 *   feature <name>
 */
public final class Caps {
    private Caps() { }

    @SuppressWarnings("deprecation")
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> at = Class.forName("android.app.ActivityThread");
        Context ctx = (Context) at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        System.out.print(report(ctx));
        System.out.flush();
        System.exit(0);                                               // the system context leaves threads running
    }

    public static String report(Context ctx) {
        StringBuilder out = new StringBuilder("sdk ").append(Build.VERSION.SDK_INT).append(' ').append(Build.VERSION.RELEASE).append('\n');
        try { codecs(out); } catch (Throwable ignored) { }
        try { display(ctx, out); } catch (Throwable ignored) { }
        try { audio(ctx, out); } catch (Throwable ignored) { }
        try { for (FeatureInfo f : ctx.getPackageManager().getSystemAvailableFeatures()) if (f.name != null) out.append("feature ").append(f.name).append('\n'); } catch (Throwable ignored) { }
        return out.toString();
    }

    private static void codecs(StringBuilder out) {
        boolean q = Build.VERSION.SDK_INT >= 29;
        for (MediaCodecInfo c : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
            String low = c.getName().toLowerCase(Locale.ROOT);
            boolean hw = q ? c.isHardwareAccelerated() : !(low.startsWith("omx.google.") || low.startsWith("c2.android.") || low.contains(".sw."));
            if (q && c.isAlias()) continue;                           // the same codec under an older name
            for (String mime : c.getSupportedTypes()) {
                MediaCodecInfo.CodecCapabilities caps;
                try { caps = c.getCapabilitiesForType(mime); } catch (Exception e) { caps = null; }
                MediaCodecInfo.VideoCapabilities v = caps != null ? caps.getVideoCapabilities() : null;
                String size = v != null ? v.getSupportedWidths().getUpper() + "x" + v.getSupportedHeights().getUpper() : "-";
                ArrayList<String> flags = new ArrayList<>();
                Set<Integer> profiles = new HashSet<>();
                if (caps != null && caps.profileLevels != null) for (MediaCodecInfo.CodecProfileLevel p : caps.profileLevels) profiles.add(p.profile);
                switch (mime.toLowerCase(Locale.ROOT)) {
                    case "video/hevc":
                        if (profiles.contains(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10)) flags.add("10bit");
                        if (profiles.contains(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10)) flags.add("hdr10");
                        if (profiles.contains(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus)) flags.add("hdr10+");
                        break;
                    case "video/x-vnd.on2.vp9":
                        if (profiles.contains(MediaCodecInfo.CodecProfileLevel.VP9Profile2)) flags.add("10bit");
                        if (profiles.contains(MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR)) flags.add("hdr10");
                        break;
                    case "video/av01":                                // AV1ProfileMain10[HDR10[Plus]]
                        if (profiles.contains(2) || profiles.contains(0x1000) || profiles.contains(0x2000)) flags.add("10bit");
                        break;
                    default:
                }
                if (q && c.isSoftwareOnly()) flags.add("soft");
                if (caps != null && caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback)) flags.add("secure");
                if (caps != null && caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback)) flags.add("tunnel");
                out.append("codec ").append(c.isEncoder() ? "enc" : "dec").append(hw ? " hw " : " sw ").append(c.getName()).append(' ')
                    .append(mime).append(' ').append(size).append(' ').append(flags.isEmpty() ? "-" : join(flags)).append('\n');
            }
        }
    }

    private static String join(ArrayList<String> flags) {             // String.join is Android 8 and later
        StringBuilder s = new StringBuilder();
        for (String f : flags) s.append(s.length() > 0 ? "," : "").append(f);
        return s.toString();
    }

    @android.annotation.TargetApi(24)                                 // called in a try that catches Throwable: an old TV just says less
    private static void display(Context ctx, StringBuilder out) {
        Display d = ((DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(Display.DEFAULT_DISPLAY);
        if (d == null) return;
        Display.HdrCapabilities hdr = d.getHdrCapabilities();
        int[] types = hdr != null ? hdr.getSupportedHdrTypes() : new int[0];
        out.append("hdr");
        for (int t : types) switch (t) {
            case 1: out.append(" dv"); break;
            case 2: out.append(" hdr10"); break;
            case 3: out.append(" hlg"); break;
            case 4: out.append(" hdr10+"); break;
            default:
        }
        out.append('\n');
        for (Display.Mode m : d.getSupportedModes()) out.append("mode ").append(m.getPhysicalWidth()).append('x').append(m.getPhysicalHeight()).append('@')
            .append(Math.round(m.getRefreshRate() * 100) / 100.0).append('\n');
    }

    @android.annotation.TargetApi(23)                                 // the same
    private static void audio(Context ctx, StringBuilder out) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        for (AudioDeviceInfo dev : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            String kind;
            switch (dev.getType()) {
                case AudioDeviceInfo.TYPE_HDMI: kind = "hdmi"; break;
                case AudioDeviceInfo.TYPE_HDMI_ARC: kind = "arc"; break;
                case 29: kind = "earc"; break;                        // TYPE_HDMI_EARC (API 31)
                case AudioDeviceInfo.TYPE_LINE_DIGITAL: kind = "optical"; break;
                case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: kind = "speaker"; break;
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: kind = "bluetooth"; break;
                default: continue;
            }
            LinkedHashSet<String> enc = new LinkedHashSet<>();
            for (int e : dev.getEncodings()) { String s = encoding(e); if (s != null) enc.add(s); }
            out.append("audio ").append(kind);
            for (String e : enc) out.append(' ').append(e);
            out.append('\n');
        }
    }

    private static String encoding(int e) {
        switch (e) {
            case AudioFormat.ENCODING_PCM_16BIT: case AudioFormat.ENCODING_PCM_FLOAT: return "pcm";
            case AudioFormat.ENCODING_AC3: return "ac3";
            case AudioFormat.ENCODING_E_AC3: return "eac3";
            case AudioFormat.ENCODING_DTS: return "dts";
            case AudioFormat.ENCODING_DTS_HD: return "dtshd";
            case AudioFormat.ENCODING_DOLBY_TRUEHD: return "truehd";
            case 18: return "eac3joc";                                // ENCODING_E_AC3_JOC (API 28): Dolby Atmos over Dolby Digital Plus
            case AudioFormat.ENCODING_IEC61937: return "iec61937";
            case 19: return "ac4";                                    // ENCODING_AC4 (API 28)
            default: return null;
        }
    }
}
