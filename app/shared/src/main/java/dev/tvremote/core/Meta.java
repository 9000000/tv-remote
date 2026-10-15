package dev.tvremote.core;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;

/**
 * App names and pictures of a TV, for a remote that is not running on it. Half of this runs on the TV: the phone and the
 * computer copy tvr-helper.jar there (built from this file and Caps.java by :shared:tvHelper) and start main() with
 * app_process, the way scrcpy-server is started. As the shell user it can ask the TV's package manager what a remote cannot
 * see from outside: each app's display name and its banner or icon.
 * It prints "package<TAB>name" lines and leaves one PNG per app that can be opened in the directory it is given.
 * Java, not Kotlin: the jar then needs no Kotlin library on the TV and stays a few KB.
 */
public final class Meta {
    private Meta() { }

    @SuppressWarnings("deprecation")
    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        dir.mkdirs();
        Looper.prepareMainLooper();
        Class<?> at = Class.forName("android.app.ActivityThread");
        Context ctx = (Context) at.getMethod("getSystemContext").invoke(at.getMethod("systemMain").invoke(null));
        PackageManager pm = ctx.getPackageManager();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            String pkg = ai.packageName;
            String label;
            try { label = ai.loadLabel(pm).toString(); } catch (Exception e) { label = pkg; }
            System.out.println(pkg + "\t" + label.replace('\n', ' ').replace('\t', ' '));
            File png = new File(dir, pkg + ".png");
            boolean shown = (ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0 || pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null;
            if (!shown || png.length() > 0 && png.lastModified() >= new File(ai.sourceDir).lastModified()) continue;   // drawn already, and the app has not been updated since
            try {
                Drawable d = null;
                try { d = pm.getApplicationBanner(ai); } catch (Exception ignored) { }   // TV apps carry a wide banner; fall back to the icon
                boolean banner = d != null;
                Drawable drawable = banner ? d : pm.getApplicationIcon(ai);
                Bitmap bmp = Bitmap.createBitmap(banner ? 240 : 96, banner ? 135 : 96, Bitmap.Config.ARGB_8888);
                drawable.setBounds(0, 0, bmp.getWidth(), bmp.getHeight());
                drawable.draw(new Canvas(bmp));
                File tmp = new File(dir, pkg + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) { bmp.compress(Bitmap.CompressFormat.PNG, 100, out); }
                tmp.renameTo(png);
            } catch (Exception ignored) {
            }
        }
        System.out.flush();
        System.exit(0);                                               // the system context leaves threads running
    }
}
