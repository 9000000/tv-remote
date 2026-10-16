package dev.tvremote.core;

import android.os.Build;
import android.os.IBinder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The TV's screen off while it keeps playing (music, a podcast), and on again by itself: run as the shell with app_process,
 * started in the background so it outlives the remote that asked. `off` turns the panel off the way scrcpy does (the TV stays
 * awake, so the sound goes on), then waits: any key on any remote (the TV's own, the phone's) turns it back on, since a person
 * pressing keys at a black TV would think it broke. It quits when the TV goes to standby (waking it turns the screen on anyway).
 * `on` turns it on at once. Both say "ok" or "error ..." on one line.
 */
public final class Screen {
    private Screen() { }

    private static final int OFF = 0, NORMAL = 2;                     // SurfaceControl.POWER_MODE_*
    private static final Pattern ACTIVITY = Pattern.compile("astUserActivityTime(?:\\(excludingAttention\\))?=(\\d+)");

    public static void main(String[] args) throws Exception {
        try { run(args); } finally {
            System.out.flush();
            Runtime.getRuntime().halt(0);                             // android_servers crashes in the normal exit's teardown
        }
    }

    private static void run(String[] args) throws Exception {
        boolean off = args.length > 0 && args[0].equals("off");
        try {
            power(off ? OFF : NORMAL);
        } catch (Throwable e) {
            System.out.println("error " + e);
            return;
        }
        System.out.println("ok");
        System.out.flush();
        if (!off) return;
        long was = -1;
        for (int i = 0; ; i++) {
            Thread.sleep(700);
            String p = run("dumpsys power");
            if (p.contains("mWakefulness=") && !p.contains("mWakefulness=Awake")) return;   // standby: waking turns it on
            long now = activity(p);
            if (was < 0) { was = now; continue; }
            if (now > was) { power(NORMAL); return; }
            if (i % 40 == 0) power(OFF);                              // some TVs turn the panel back on by themselves (a source change)
        }
    }

    private static long activity(String dump) {
        long most = 0;
        Matcher m = ACTIVITY.matcher(dump);
        while (m.find()) most = Math.max(most, Long.parseLong(m.group(1)));
        return most;
    }

    private static String run(String cmd) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[] { "sh", "-c", cmd });
        StringBuilder b = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            for (String line; (line = r.readLine()) != null; ) b.append(line).append('\n');
        }
        p.waitFor();
        return b.toString();
    }

    /** Every physical display's power mode, by the hidden calls each Android version has (as scrcpy's Device.setDisplayPower). */
    private static void power(int mode) throws Exception {
        if (Build.VERSION.SDK_INT >= 35) try {                        // Android 15+: asked of the display manager
            Class<?> g = Class.forName("android.hardware.display.DisplayManagerGlobal");
            Object dm = g.getMethod("getInstance").invoke(null);
            Method m = g.getMethod("requestDisplayPower", int.class, boolean.class);
            m.invoke(dm, 0, mode != OFF);
            return;
        } catch (NoSuchMethodException older) { }
        Class<?> sc = Class.forName("android.view.SurfaceControl");
        Method set = sc.getMethod("setDisplayPowerMode", IBinder.class, int.class);
        if (Build.VERSION.SDK_INT < 29) {
            set.invoke(null, sc.getMethod("getBuiltInDisplay", int.class).invoke(null, 0), mode);
            return;
        }
        long[] ids;
        Method token;
        try {
            ids = (long[]) sc.getMethod("getPhysicalDisplayIds").invoke(null);
            token = sc.getMethod("getPhysicalDisplayToken", long.class);
        } catch (NoSuchMethodException android14) {                   // moved to services.jar's DisplayControl
            Class<?> dc = displayControl();
            ids = (long[]) dc.getMethod("getPhysicalDisplayIds").invoke(null);
            token = dc.getMethod("getPhysicalDisplayToken", long.class);
        }
        for (long id : ids) set.invoke(null, token.invoke(null, id), mode);
    }

    private static Class<?> displayControl;                           // its library loads once per process

    private static Class<?> displayControl() throws Exception {
        if (displayControl != null) return displayControl;
        Class<?> f = Class.forName("com.android.internal.os.ClassLoaderFactory");
        Method make = f.getDeclaredMethod("createClassLoader", String.class, String.class, String.class, ClassLoader.class, int.class, boolean.class, String.class);
        ClassLoader l = (ClassLoader) make.invoke(null, "/system/framework/services.jar", null, null, ClassLoader.getSystemClassLoader(), 0, true, null);
        Class<?> dc = l.loadClass("com.android.server.display.DisplayControl");
        Method load = Runtime.class.getDeclaredMethod("loadLibrary0", Class.class, String.class);
        load.setAccessible(true);
        load.invoke(Runtime.getRuntime(), dc, "android_servers");
        return displayControl = dc;
    }
}
