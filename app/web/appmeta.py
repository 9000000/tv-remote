"""App name + icon for the app list.

Asked from the TV itself, the same way the phone app does it: the phone app's APK (app/android/tv-remote.apk) is copied
to the TV once and its Meta class is started there with app_process. As the shell user it asks the TV's package manager
for every app's display name and draws each app's banner or icon into a PNG; we read the names from its output and pull
the PNGs. One run covers every app. Results are cached in .cache/apps (keyed by the APK path, which changes on every update).
"""
import asyncio, json, subprocess, threading, time, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
CACHE = ROOT / ".cache" / "apps"
HELPER = ROOT / "app" / "android" / "tv-remote.apk"                   # carries dev.tvremote.core.Meta (app/shared/.../Meta.kt)
DIR = "/data/local/tmp/tvr-meta"
# One file per helper build, written whole and then renamed: the computer, the phone app and TV Setup each bring their own APK,
# and with one shared name one of them was overwriting it while another started it (a crash on the TV, every time).
# Builds older than a few days are cleared; one still in use stays readable to the process that has it open.
CLEAN = "find /data/local/tmp -maxdepth 1 -name 'tvr-meta*.apk' -mtime +3 -delete 2>/dev/null; rm -f /data/local/tmp/tvr-meta.apk"


class AppMeta:
    def __init__(self, adb_bin, target):
        self.adb_bin, self.target = adb_bin, target
        self.sem, self.inflight = asyncio.Semaphore(4), {}
        self.lock, self.labels, self.failed_at = threading.Lock(), None, 0

    def cached(self, pkg, apk_path):
        try:
            m = json.loads((CACHE / f"{pkg}.json").read_text())
            return m if m.get("apk") == apk_path and m.get("v") == 2 else None
        except (OSError, ValueError):
            return None

    def icon_file(self, pkg):
        return CACHE / f"{pkg}.img"

    def _adb(self, *args, timeout=90):
        r = subprocess.run([self.adb_bin, "-s", self.target, *args], capture_output=True, timeout=timeout)
        if r.returncode != 0:
            raise RuntimeError(r.stderr.decode(errors="replace").strip() or "adb failed")
        return r.stdout

    def _names(self, pkg=None):
        """{package: name} from one run of Meta on the TV; run again when an app installed since then is asked for."""
        with self.lock:
            if (self.labels is None or (pkg and pkg not in self.labels)) and time.monotonic() - self.failed_at > 300:
                out = self._helper("dev.tvremote.core.Meta " + DIR)
                self.labels = dict(line.rstrip("\r").split("\t", 1) for line in out.splitlines() if "\t" in line)
                if not self.labels:                                   # it did not run: not again for every app on the list
                    self.failed_at = time.monotonic()
            return self.labels or {}

    def _helper(self, main):
        """Runs a class of the phone app's APK on the TV (Meta, Caps) and gives what it printed; copies the APK there first."""
        remote = f"/data/local/tmp/tvr-meta-{HELPER.stat().st_size}.apk"
        if self._adb("shell", f"[ -f {remote} ] && echo y || true").decode().strip() != "y":
            self._adb("shell", CLEAN)
            self._adb("push", str(HELPER), remote + ".part")
            self._adb("shell", f"mv {remote}.part {remote}; touch {remote}")   # push keeps the APK's own date: CLEAN would take it for old
        return self._adb("shell", f"CLASSPATH={remote} app_process / {main} 2>/dev/null").decode(errors="replace")

    def caps(self):
        """What the TV's hardware can do (Caps.kt): kept per TV, it does not change."""
        with self.lock:
            if not getattr(self, "_caps", ""):
                # an APK from before Caps existed would crash on the TV (ClassNotFoundException): look inside first
                with zipfile.ZipFile(HELPER) as z:
                    has = any(b"Ldev/tvremote/core/Caps;" in z.read(n) for n in z.namelist() if n.endswith(".dex"))
                self._caps = self._helper("dev.tvremote.core.Caps") if has else ""
            return self._caps                                         # "": the page says the computer's copy needs updating

    def _resolve(self, pkg, apk_path):
        meta, icon, failed = {"apk": apk_path, "v": 2, "label": "", "bg": "", "adaptive": False, "icon": False}, None, False
        try:
            meta["label"] = self._names(pkg).get(pkg, "")
            icon = self._adb("exec-out", f"cat {DIR}/{pkg}.png 2>/dev/null", timeout=30)
        except Exception:
            failed = True                                             # TV unreachable: don't cache the miss
        meta["icon"] = bool(icon)
        if not failed:
            CACHE.mkdir(parents=True, exist_ok=True)
            if icon:
                self.icon_file(pkg).write_bytes(icon)
            (CACHE / f"{pkg}.json").write_text(json.dumps(meta))
        return meta

    async def get(self, pkg, apk_path):
        m = self.cached(pkg, apk_path)
        if m:
            return m
        if pkg not in self.inflight:
            async def work():
                try:
                    async with self.sem:
                        return await asyncio.get_running_loop().run_in_executor(None, self._resolve, pkg, apk_path)
                finally:
                    self.inflight.pop(pkg, None)
            self.inflight[pkg] = asyncio.create_task(work())
        return await asyncio.shield(self.inflight[pkg])
