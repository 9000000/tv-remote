"""App name + icon for the app list.

Asked from the TV itself, the same way the phone app does it: the phone app's APK (app/android/tv-remote.apk) is copied
to the TV once and its Meta class is started there with app_process. As the shell user it asks the TV's package manager
for every app's display name and draws each app's banner or icon into a PNG; we read the names from its output and pull
the PNGs. One run covers every app. Results are cached in .cache/apps (keyed by the APK path, which changes on every update).
"""
import asyncio, json, subprocess, threading
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
CACHE = ROOT / ".cache" / "apps"
HELPER = ROOT / "app" / "android" / "tv-remote.apk"                   # carries dev.tvremote.core.Meta (app/shared/.../Meta.kt)
REMOTE, DIR = "/data/local/tmp/tvr-meta.apk", "/data/local/tmp/tvr-meta"


class AppMeta:
    def __init__(self, adb_bin, target):
        self.adb_bin, self.target = adb_bin, target
        self.sem, self.inflight = asyncio.Semaphore(4), {}
        self.lock, self.labels = threading.Lock(), None

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

    def _names(self, pkg):
        """{package: name} from one run of Meta on the TV; run again when an app installed since then is asked for."""
        with self.lock:
            if self.labels is None or pkg not in self.labels:
                size = str(HELPER.stat().st_size)
                if self._adb("shell", f"[ -f {REMOTE} ] && wc -c < {REMOTE} || echo 0").decode().strip() != size:
                    self._adb("push", str(HELPER), REMOTE)
                out = self._adb("shell", f"CLASSPATH={REMOTE} app_process / dev.tvremote.core.Meta {DIR} 2>/dev/null").decode(errors="replace")
                self.labels = dict(line.rstrip("\r").split("\t", 1) for line in out.splitlines() if "\t" in line)
            return self.labels

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
