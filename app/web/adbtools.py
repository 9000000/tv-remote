"""ADB helpers for the TV web remote: app list/launch/uninstall, file browsing, push/pull, install APK, fetch-from-URL.

Everything shells out to `adb -s <target>`. Paths are restricted to the TV's shared storage, package names are
validated, and long transfers run as background jobs the page polls.
"""
import asyncio, hashlib, json, mimetypes, re, shlex, tempfile, time, urllib.request, uuid
from pathlib import Path
from urllib.parse import quote, unquote, urlsplit

from tvtools import ADB
MAX_BYTES = 4 * 1024**3
PKG_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")
ROOTS = ("/sdcard", "/storage")
DEFAULT_DEST = "/sdcard/Download"
TS_PKG = "com.tailscale.ipn"
LS_RE = re.compile(r"^([dl-])\S+\s+\d+\s+\S+\s+\S+\s+(\d+)\s+(\d{4}-\d\d-\d\d \d\d:\d\d)\s+(.+)$")


class AdbError(Exception):
    pass


# ---- the TV's "Allow USB debugging?" question (same rules as AdbPrompt.kt: only for the key named, after its fingerprint matches) ----
FP_RE = re.compile(r"(?:[0-9A-F]{2}:){15}[0-9A-F]{2}")


def my_fingerprint():
    """What the TV's question shows for this computer's adb key: MD5 of the key, AB:CD:..."""
    import base64, os
    try:
        blob = base64.b64decode(Path(os.path.expanduser("~/.android/adbkey.pub")).read_text().split()[0])
    except Exception:
        return ""
    return ":".join("%02X" % b for b in hashlib.md5(blob).digest())


def _nodes(xml):
    out = []
    for m in re.finditer(r"<node ([^>]*)", xml):
        a = dict(re.findall(r'([\w-]+)="([^"]*)"', m.group(1)))
        b = [int(x) for x in re.findall(r"\d+", a.get("bounds", ""))]
        if len(b) == 4:
            out.append({"id": a.get("resource-id", ""), "cls": a.get("class", ""), "text": a.get("text", ""), "checked": a.get("checked") == "true",
                        "focused": a.get("focused") == "true", "x": (b[0] + b[2]) // 2, "y": (b[1] + b[3]) // 2})
    return out


def _part(nodes, which):
    for n in nodes:
        if which == "always" and (n["id"].endswith("alwaysUse") or n["cls"].endswith("CheckBox")) \
                or which == "allow" and n["id"] == "android:id/button1" or which == "cancel" and n["id"] == "android:id/button2":
            return n
    return None


def safe_path(p):
    p = "/" + "/".join(x for x in (p or DEFAULT_DEST).split("/") if x not in ("", "."))
    if ".." in p.split("/") or not (p in ROOTS or p.startswith(tuple(r + "/" for r in ROOTS))):
        raise AdbError("path must be under /sdcard or /storage")
    return p


def safe_name(n):
    n = re.sub(r"[^\w.\- ()\[\]]", "_", Path(n or "").name).strip(". ")
    return n[:120] or "file"


class Adb:
    def __init__(self, target):
        self.target = target
        self.jobs = {}
        self.apk_paths = {}

    async def run(self, *args, timeout=30, stdin=None):
        p = await asyncio.create_subprocess_exec(ADB, "-s", self.target, *args,
                                                 stdin=asyncio.subprocess.PIPE if stdin else None,
                                                 stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
        try:
            out, err = await asyncio.wait_for(p.communicate(stdin), timeout)
        except asyncio.TimeoutError:
            p.kill(); raise AdbError("adb timed out")
        return p.returncode, out, err.decode(errors="replace")

    async def ensure(self):
        p = await asyncio.create_subprocess_exec(ADB, "connect", self.target, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.STDOUT)
        await asyncio.wait_for(p.communicate(), 10)

    async def status(self, force=False):
        """ready | unauthorized | offline | closed (port shut) | error.  Cached for a few seconds."""
        if not self.target:
            return {"state": "closed"}                                # no TV chosen yet
        if not force and time.time() - getattr(self, "_st_t", 0) < 4:
            return self._st
        host, port = self.target.rsplit(":", 1)
        try:
            _, w = await asyncio.wait_for(asyncio.open_connection(host, int(port)), 2.5)
            w.close()
        except Exception:
            st = {"state": "closed"}
        else:
            st = await self._state_once()
            if st["state"] == "error":                                    # port open but not attached: connect once, recheck
                await self.ensure()
                st = await self._state_once()
            # A link made while the TV was waking can stay "unauthorized"/"offline" although the TV trusts this computer:
            # adb never retries on its own, so drop it and connect again (at most every 30 s).
            if st["state"] in ("unauthorized", "offline") and time.time() - getattr(self, "_re_t", 0) > 30:
                self._re_t = time.time()
                p = await asyncio.create_subprocess_exec(ADB, "disconnect", self.target, stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL)
                await asyncio.wait_for(p.wait(), 6)
                await self.ensure()
                st = await self._state_once()
        self._st, self._st_t = st, time.time()
        return st

    async def _state_once(self):
        try:
            rc, out, err = await self.run("get-state", timeout=6)
        except AdbError:
            return {"state": "error"}
        text = (out.decode(errors="replace") + err).lower()
        if "device" in text.split() and rc == 0:
            return {"state": "ready"}
        if "unauthorized" in text:
            return {"state": "unauthorized"}
        if "offline" in text:
            return {"state": "offline"}
        return {"state": "error"}

    async def shell(self, cmd, timeout=30):
        rc, out, err = await self.run("shell", cmd, timeout=timeout)
        if rc != 0 and not out:
            await self.ensure()
            rc, out, err = await self.run("shell", cmd, timeout=timeout)
            if rc != 0 and not out:
                raise AdbError(err.strip() or "adb shell failed")
        return out.decode(errors="replace")

    # ---- the TV's "Allow USB debugging?" question ----
    async def prompt_read(self):
        """(fingerprint, nodes) of the question on screen, or None."""
        if "UsbDebuggingActivity" not in await self.shell("dumpsys window | grep -E 'mCurrentFocus|mFocusedWindow'", timeout=15):
            return None
        for _ in range(3):                                            # uiautomator sometimes finds the screen busy
            xml = await self.shell("uiautomator dump /data/local/tmp/tvr-ui.xml >/dev/null 2>&1; cat /data/local/tmp/tvr-ui.xml; rm -f /data/local/tmp/tvr-ui.xml", timeout=30)
            nodes = _nodes(xml)
            fp = next((m.group(0) for n in nodes for m in [FP_RE.search(n["text"])] if m), "")
            if fp:
                return fp, nodes
            await asyncio.sleep(0.6)
        return None

    async def _press(self, seen, which):
        """A tap on the part; if the TV ignores taps, the arrow keys to it and OK."""
        fp, nodes = seen
        n = _part(nodes, which)
        if not n:
            raise AdbError("the question on the TV looks different than expected")
        await self.shell(f"input tap {n['x']} {n['y']}")
        await asyncio.sleep(0.5)
        after = await self.prompt_read()
        if not after or after[0] != fp or (_part(after[1], which) or {}).get("checked") != n["checked"]:
            return
        for _ in range(8):
            t, f = _part(after[1], which), next((x for x in after[1] if x["focused"]), None)
            if not t:
                return
            if f and f["x"] == t["x"] and f["y"] == t["y"]:
                await self.shell("input keyevent 23"); return
            key = 20 if not f or t["y"] > f["y"] + 10 else 19 if t["y"] < f["y"] - 10 else 22 if t["x"] > f["x"] else 21
            await self.shell(f"input keyevent {key}")
            after = await self.prompt_read()
            if not after:
                return

    async def prompt_allow(self, want, wait=0, others=False):
        """allowed | none | other:<fp> | failed. Questions about other keys are declined only with others=True (never lets anyone in)."""
        want, end, skipped = want.upper(), time.time() + wait, 0
        while True:
            p = await self.prompt_read()
            if not p:
                if time.time() > end:
                    return "none"
                await asyncio.sleep(0.8); continue
            if p[0] != want:
                if not others or skipped >= 6:
                    return "other:" + p[0]
                await self._press(p, "cancel"); skipped += 1; await asyncio.sleep(1.2); continue
            if not (_part(p[1], "always") or {}).get("checked"):
                await self._press(p, "always")
            now = await self.prompt_read()
            if now and now[0] == want and not (_part(now[1], "always") or {}).get("checked"):
                return "failed"                                       # never answer without "Always allow"
            await self._press(now or p, "allow")
            await asyncio.sleep(0.9)
            after = await self.prompt_read()
            return "allowed" if not after or after[0] != want else "failed"

    async def prompt_deny(self, want):
        p = await self.prompt_read()
        if not p or p[0] != want.upper():
            return False
        await self._press(p, "cancel")
        return True

    async def setup_pass(self):
        """A TV Setup pass for this computer (a broadcast only the shell may send), or None without TV Setup."""
        m = re.search(r'data="([0-9a-f]{48})"', await self.shell("am broadcast -n dev.tvremote.setup/.TrustReceiver 2>&1", timeout=15))
        return m.group(1) if m else None

    # ---- apps ----
    async def apps(self, system=False):
        out = await self.shell("pm list packages -f" + ("" if system else " -3"))
        found = dict(l[8:].strip().rsplit("=", 1)[::-1] for l in out.splitlines() if l.startswith("package:") and "=" in l)
        self.apk_paths.update(found)
        return sorted(found)

    async def launch(self, pkg):
        self._pkg(pkg)
        for cat in ("LEANBACK_LAUNCHER", "LAUNCHER"):
            out = await self.shell(f"monkey -p {pkg} -c android.intent.category.{cat} 1")
            if "No activities found" not in out and "monkey aborted" not in out:
                return "launched"
        raise AdbError("app has no launcher activity")

    async def _am(self, args):
        out = await self.shell(f"am start {args} 2>&1")
        if "Error" in out or "unable to resolve" in out.lower():
            raise AdbError("the TV has no app that can open this")

    async def open_link(self, link):
        if not re.match(r"^[a-z][a-z0-9+.-]*://\S+$", link, re.I):
            raise AdbError("not a link")
        await self._am(f"-a android.intent.action.VIEW -d {shlex.quote(link)}")

    async def open_settings(self):
        await self._am("-a android.settings.SETTINGS")

    async def set_volume_fast(self, level):
        """Direct binder call to AudioService.setStreamVolume: ~0.3 s, where `media volume` boots a JVM and can take 5 s
        on this TV. The transaction number differs per Android release, so only where it has been checked (Android 9)."""
        if not hasattr(self, "sdk"):
            self.sdk = (await self.shell("getprop ro.build.version.sdk")).strip()
        if self.sdk != "28":
            return False
        await self.shell(f"service call audio 10 i32 3 i32 {int(level)} i32 0 s16 com.android.shell")
        return True

    async def set_volume(self, level):
        out = await self.shell(f"media volume --stream 3 --set {int(level)} 2>&1")
        if "Exception" in out or "Error" in out:
            raise AdbError("volume command failed")

    async def stop(self, pkg):
        self._pkg(pkg); await self.shell(f"am force-stop {pkg}")

    async def uninstall(self, pkg):
        self._pkg(pkg)
        out = (await self.shell(f"pm uninstall {pkg}")).strip()
        if "Success" not in out:
            raise AdbError(out or "uninstall failed")

    @staticmethod
    def _pkg(pkg):
        if not PKG_RE.match(pkg or ""):
            raise AdbError("bad package name")

    # ---- files ----
    async def ls(self, path):
        path = safe_path(path)
        q = shlex.quote(path.rstrip(chr(47)) + chr(47))                                   # trailing / follows the /sdcard symlink
        out, _, rest = (await self.shell(f"ls -lA {q} 2>&1; echo '#--'; df -k {q} 2>/dev/null | tail -1; echo '#--'; ls /storage 2>/dev/null")).partition("#--")
        df, _, vols = rest.partition("#--")
        df = df.split()
        self.space = {"free": int(df[3]) * 1024, "total": int(df[1]) * 1024} if len(df) > 3 and df[1].isdigit() and df[3].isdigit() else None
        self.volumes = [v for v in vols.split() if v not in ("emulated", "self", "sdcard", "sdcard0")]   # USB drives
        items = []
        for line in out.splitlines():
            m = LS_RE.match(line.strip())
            if m:
                kind, size, mtime, name = m.groups()
                name = name.split(" -> ")[0]
                items.append({"name": name, "dir": kind in "dl", "size": int(size), "mtime": mtime})
            elif line.strip() and not line.startswith("total"):
                if "No such file" in line or "Permission denied" in line:
                    raise AdbError(line.strip())
        items.sort(key=lambda i: (not i["dir"], i["name"].lower()))
        return path, items

    async def delete(self, path):
        path = safe_path(path)
        if path in ROOTS or path.count("/") < 3 and path.startswith("/storage"):
            raise AdbError("refusing to delete a storage root")
        out = await self.shell(f"rm -rf {shlex.quote(path)}")
        if out.strip():
            raise AdbError(out.strip())

    async def mkdir(self, path):
        path = safe_path(path)
        out = (await self.shell(f"mkdir {shlex.quote(path)} 2>&1")).strip()
        if out:
            raise AdbError(out)

    async def rename(self, path, name):
        path = safe_path(path)
        if path in ROOTS or name != safe_name(name):
            raise AdbError("bad name")
        dst = shlex.quote(path.rsplit("/", 1)[0] + "/" + name)
        out = (await self.shell(f"if [ -e {dst} ]; then echo exists; else mv {shlex.quote(path)} {dst} 2>&1; fi")).strip()
        if out:
            raise AdbError("a file with that name already exists" if out == "exists" else out)

    async def open_on_tv(self, path):
        """Ask the TV to open a file with whatever app handles its type (video player, image viewer...)."""
        path = safe_path(path)
        real = "/storage/emulated/0" + path[7:] if path.startswith("/sdcard") else path
        mime = mimetypes.guess_type(path)[0] or "*/*"
        await self._am(f"-a android.intent.action.VIEW -d {shlex.quote('file://' + quote(real))} -t {shlex.quote(mime)}")

    def job_install_path(self, path):
        path = safe_path(path)

        async def go(j):
            q = shlex.quote(path)
            if (await self.shell(f"head -c 2 {q}"))[:2] != "PK":
                raise AdbError("not an APK (not a zip file)")
            size = (await self.shell(f"stat -c %s {q}")).strip()
            if not size.isdigit():
                raise AdbError("cannot read file")
            j["msg"], j["pct"] = "installing on TV", 50
            out = await self.shell(f"cat {q} | pm install -r -S {size} 2>&1", timeout=1800)
            if "Success" not in out:
                raise AdbError(out.strip()[-300:] or "install failed")
            return "installed"
        return self.job(go, f"install {path.rsplit('/', 1)[-1]}")

    def open_pull(self, path):
        return asyncio.create_subprocess_exec(ADB, "-s", self.target, "exec-out", "cat", safe_path(path),
                                              stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)

    # ---- jobs ----
    def job(self, coro_fn, title):
        jid = uuid.uuid4().hex[:8]
        j = {"id": jid, "title": title, "status": "running", "msg": "starting", "pct": 0, "t": time.time()}
        self.jobs[jid] = j
        for old in sorted(self.jobs.values(), key=lambda x: x["t"])[:-20]:
            self.jobs.pop(old["id"], None)

        async def runner():
            try:
                j["msg"] = await coro_fn(j) or "done"
                j["status"], j["pct"] = "done", 100
            except Exception as e:
                j["status"], j["msg"] = "error", str(e) or e.__class__.__name__
        asyncio.create_task(runner())
        return j

    async def fetch_to_file(self, url, j, tmpdir):
        """Download url to tmpdir on this machine (blocking IO in a thread); returns (path, filename)."""
        parts = urlsplit(url)
        if parts.scheme not in ("http", "https") or not parts.netloc:
            raise AdbError("only http/https links")
        loop = asyncio.get_running_loop()

        def work():
            req = urllib.request.Request(url, headers={"User-Agent": "tv-remote/1.0"})
            with urllib.request.urlopen(req, timeout=30) as r:
                total = int(r.headers.get("Content-Length") or 0)
                if total > MAX_BYTES:
                    raise AdbError("file too large")
                cd = r.headers.get("Content-Disposition", "")
                m = re.search(r'filename\*?=(?:UTF-8\'\')?"?([^";]+)', cd)
                name = safe_name(unquote(m.group(1)) if m else unquote(Path(urlsplit(r.geturl()).path).name))
                dest, done = Path(tmpdir) / name, 0
                with open(dest, "wb") as f:
                    while chunk := r.read(1 << 20):
                        done += len(chunk)
                        if done > MAX_BYTES:
                            raise AdbError("file too large")
                        f.write(chunk)
                        j["msg"] = f"downloading {name}: {done >> 20} MB"
                        if total:
                            j["pct"] = int(done * 50 / total)
                return dest, name
        return await loop.run_in_executor(None, work)

    async def push(self, local, remote, j):
        j["msg"] = f"pushing to TV: {remote}"; j["pct"] = max(j["pct"], 55)
        rc, out, err = await self.run("push", str(local), remote, timeout=3600)
        if rc != 0:
            raise AdbError((err or out.decode(errors="replace")).strip()[-300:])

    async def install(self, local, j):
        if open(local, "rb").read(2) != b"PK":
            raise AdbError("not an APK (not a zip file)")
        j["msg"] = "installing on TV"; j["pct"] = max(j["pct"], 60)
        rc, out, err = await self.run("install", "-r", str(local), timeout=1800)
        text = (out.decode(errors="replace") + err).strip()
        if "Success" not in text:
            raise AdbError(text[-300:] or "install failed")
        return "installed"

    def job_fetch(self, url, dest_dir):
        dest_dir = safe_path(dest_dir)

        async def go(j):
            with tempfile.TemporaryDirectory(prefix="tvfetch-") as d:
                local, name = await self.fetch_to_file(url, j, d)
                await self.push(local, f"{dest_dir}/{name}", j)
                return f"saved {dest_dir}/{name}"
        return self.job(go, f"fetch {url[:60]}")

    def job_install_url(self, url):
        async def go(j):
            with tempfile.TemporaryDirectory(prefix="tvapk-") as d:
                local, _ = await self.fetch_to_file(url, j, d)
                return await self.install(local, j)
        return self.job(go, f"install {url[:60]}")

    def job_install_tailscale(self):
        """Official Tailscale APK from pkgs.tailscale.com, checked against the published SHA-256."""
        base = "https://pkgs.tailscale.com/stable/"

        async def go(j):
            loop = asyncio.get_running_loop()
            get = lambda url: loop.run_in_executor(None, lambda: urllib.request.urlopen(url, timeout=30).read())
            name = json.loads(await get(base + "?mode=json"))["APKs"]["universal"]
            want = (await get(base + name + ".sha256")).decode().split()[0]
            with tempfile.TemporaryDirectory(prefix="tvts-") as d:
                local, _ = await self.fetch_to_file(base + name, j, d)
                digest = await loop.run_in_executor(None, lambda: hashlib.sha256(Path(local).read_bytes()).hexdigest())
                if digest != want:
                    raise AdbError("Tailscale checksum mismatch, not installing")
                return await self.install(local, j)
        return self.job(go, "install Tailscale")

    async def tailscale_ip(self):
        m = re.search(r"inet (100\.[0-9.]+)/32", await self.shell("ip -4 addr show"))
        return m.group(1) if m else ""

    def job_tailscale_start(self):
        """Tailscale on the TV with as little done by hand as possible (the same steps as Tailscale.kt in the apps): install the
        official APK when missing, let it make its VPN without asking, switch it on, and when it still has to be signed in, read the
        sign-in link off its screen. The job ends with "ip" (in the tailnet) or "url" (open it in a browser signed in to Tailscale)."""
        login = re.compile(r"^(get started|continue|log ?in|sign ?in|bắt đầu|đăng nhập|anmelden|connexion|iniciar sesión|ログイン|登录|로그인)$", re.I)
        code = re.compile(r"^[0-9a-f]{8,20}$")
        node = re.compile(r'<node [^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')

        async def go(j):
            if "package:" not in await self.shell("pm path " + TS_PKG + "; echo ."):
                inner = self.job_install_tailscale()                  # its own job: wait for it, showing its progress here
                while inner["status"] == "running":
                    j["msg"], j["pct"] = inner["msg"], int(inner["pct"] * .8)
                    await asyncio.sleep(.5)
                self.jobs.pop(inner["id"], None)
                if inner["status"] != "done":
                    raise AdbError(inner["msg"])
            j["msg"], j["pct"] = "start", 80
            # comes back by itself after standby or a restart, and Android does not put it to sleep (as Tailscale.kt keepAlive)
            await self.shell(f"dumpsys deviceidle whitelist +{TS_PKG}; am set-standby-bucket {TS_PKG} active; "
                             f"cmd appops set {TS_PKG} RUN_IN_BACKGROUND allow; cmd appops set {TS_PKG} RUN_ANY_IN_BACKGROUND allow; "
                             f"settings put secure always_on_vpn_app {TS_PKG}; settings put secure always_on_vpn_lockdown 0; echo .")
            await self.shell(f"cmd appops set {TS_PKG} ACTIVATE_VPN allow")   # no "Tailscale wants to set up a VPN connection" box
            await self.shell(f"pm grant {TS_PKG} android.permission.POST_NOTIFICATIONS 2>/dev/null; echo .")   # nor "Allow notifications?"
            if (ip := await self.tailscale_ip()):
                j["ip"] = ip; return "in"
            await self.shell(f"am broadcast -a com.tailscale.ipn.CONNECT_VPN -n {TS_PKG}/.IPNReceiver")   # signed in but switched off
            for _ in range(6):
                await asyncio.sleep(1)
                if (ip := await self.tailscale_ip()):
                    j["ip"] = ip; return "in"
            await self.shell(f"am start -n {TS_PKG}/.MainActivity")
            pressed = 0
            for i in range(20):
                await asyncio.sleep(1.5)
                if (ip := await self.tailscale_ip()):
                    j["ip"] = ip; return "in"
                nodes = node.findall(await self.shell("uiautomator dump /sdcard/tvr-ui.xml >/dev/null 2>&1; cat /sdcard/tvr-ui.xml; rm -f /sdcard/tvr-ui.xml"))
                found = next((n for n in nodes if code.match(n[0])), None)
                if found:
                    j["url"] = "https://login.tailscale.com/a/" + found[0]; return "sign in"
                btn = next((n for n in nodes if login.match(n[0].strip())), None)
                if btn and pressed < 5:
                    x1, y1, x2, y2 = map(int, btn[1:])
                    await self.shell(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}"); pressed += 1
                elif not btn and i == 4 and not pressed:
                    await self.shell("input keyevent 23"); pressed += 1   # a button we cannot read: it is the one in focus on a TV
            raise AdbError("no sign-in link on the TV's screen")
        return self.job(go, "tailscale")

    async def save_upload(self, request, tmpdir, name):
        dest, done = Path(tmpdir) / safe_name(name), 0
        with open(dest, "wb") as f:
            async for chunk in request.content.iter_chunked(1 << 20):
                done += len(chunk)
                if done > MAX_BYTES:
                    raise AdbError("file too large")
                f.write(chunk)
        return dest

    def job_from_local(self, local, tmpdir_obj, kind, dest_dir=None):
        async def go(j):
            try:
                if kind == "install":
                    return await self.install(local, j)
                dd = safe_path(dest_dir)
                await self.push(local, f"{dd}/{Path(local).name}", j)
                return f"saved {dd}/{Path(local).name}"
            finally:
                tmpdir_obj.cleanup()
        return self.job(go, f"{kind} {Path(local).name}")
