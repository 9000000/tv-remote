package dev.tvremote.core;

import java.io.File;
import java.io.IOException;

/** An ADB connection to one TV that is opened when first needed and opened again after it drops. */
public final class AdbLink {
    private final File keyDir;
    private final String host;
    private Adb adb;
    private volatile boolean waiting;                                 // the TV is showing "Allow USB debugging?"

    public AdbLink(File keyDir, String host) { this.keyDir = keyDir; this.host = host; }

    public synchronized Adb get() throws IOException {
        if (adb != null && !adb.isDead()) return adb;
        try {
            Adb a = new Adb(keyDir);
            a.connect(host, 5555, () -> waiting = true);
            waiting = false;
            adb = a;
            return a;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    public boolean waitingForUser() { return waiting; }

    public synchronized void close() {
        if (adb != null) adb.close();
        adb = null;
    }
}
