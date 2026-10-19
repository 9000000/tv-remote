package dev.tvremote.core;

import android.os.Build;
import android.os.IBinder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The TV's screen off while it goes on playing and stays in the remotes' hands, and on again by itself: run as the shell with
 * app_process, started in the background so it outlives the remote that asked. `off` turns the panel off the way scrcpy does (the
 * TV stays awake: the sound goes on, the picture still reaches the remotes, their keys still work), then waits: a key of a real
 * input device (the TV's own remote, a keyboard: anything in /dev/input, which getevent reads) turns it back on, since a person
 * at a black TV would think it broke. Keys the remotes inject (input, scrcpy) never show there, so they leave it off; when one
 * makes Android light the panel again it is turned off at once. It quits when the TV goes to standby (waking turns the screen on
 * anyway). `on` turns it on at once (the remotes stop the waiting one first: ScreenOff.kt). Both say "ok" or "error ..." on one line.
 * Meanwhile TV Setup's KeyCatch is on (where TV Setup has it), so the key that wakes the screen does nothing else.
 */
public final class Screen {
    private Screen() { }

    private static final int OFF = 0, NORMAL = 2;                     // SurfaceControl.POWER_MODE_*
    private static final Pattern ACTIVITY = Pattern.compile("astUserActivityTime(?:\\(excludingAttention\\))?=(\\d+)");

    public static void main(String[] args) throws Exception {
        try { run(args); } finally {
            try { catchKeys(false); } catch (Throwable ignored) { }
            Process g = getevent;
            if (g != null) g.destroy();                               // or it would outlive this process
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
        try { catchKeys(true); } catch (Throwable ignored) { }         // an older TV Setup, or none: the key goes on to the app too
        Thread keys = new Thread(Screen::watchKeys);
        keys.setDaemon(true);
        keys.start();
        long was = -1;
        for (int i = 1; ; i++) {
            Thread.sleep(700);
            if (pressed) { power(NORMAL); Thread.sleep(1500); return; }   // KeyCatch kept a moment: the key's up (and repeats) eaten too
            String p = run("dumpsys power | grep -F -e mWakefulness= -e astUserActivityTime");
            if (p.contains("mWakefulness=") && !p.contains("mWakefulness=Awake")) return;   // standby: waking turns it on
            long now = activity(p);
            if (now != was || i % 40 == 0) power(OFF);               // a remote's key may light it again; some TVs do so by themselves (a source change)
            was = now;
        }
    }

    private static volatile boolean pressed;
    private static volatile Process getevent;

    /** Waits for a key pressed on a real input device. Without getevent (or not allowed to read them) nothing turns it on but `on`. */
    private static void watchKeys() {
        try {
            Process p = getevent = Runtime.getRuntime().exec(new String[] { "getevent", "-l" });
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                for (String line; (line = r.readLine()) != null; ) {
                    if (line.contains("EV_KEY") && line.trim().endsWith("DOWN")) { pressed = true; return; }
                }
            }
        } catch (Exception ignored) { }
    }

    private static final String CATCH = "dev.tvremote.setup/dev.tvremote.setup.KeyCatch";

    /** TV Setup's KeyCatch on (it eats the key that wakes the screen) or off again, the TV's other accessibility services kept. */
    private static void catchKeys(boolean on) throws Exception {
        if (on && !run("dumpsys package dev.tvremote.setup | grep -F KeyCatch").contains("KeyCatch")) return;
        String was = run("settings get secure enabled_accessibility_services").trim();
        StringBuilder now = new StringBuilder();
        if (!was.isEmpty() && !was.equals("null"))
            for (String c : was.split(":")) if (!c.isEmpty() && !c.equals(CATCH)) now.append(now.length() > 0 ? ":" : "").append(c);
        boolean others = now.length() > 0;
        if (on) now.append(others ? ":" : "").append(CATCH);
        else if (!was.contains(CATCH)) return;                        // not ours to touch
        run(now.length() > 0 ? "settings put secure enabled_accessibility_services " + now + "; settings put secure accessibility_enabled 1"
            : "settings delete secure enabled_accessibility_services; settings put secure accessibility_enabled 0");
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
