"""Local web remote for Android TV / Google TV.  Run: ./venv/bin/python app/server.py  ->  http://127.0.0.1:8765

Binds to 127.0.0.1 only. Mutating endpoints require a JSON body (forces a CORS preflight) and a same-origin
Host/Origin, so other websites open in your browser cannot drive the TV.
"""
import argparse, asyncio, hashlib, ipaddress, json, mimetypes, os, random, re, socket, struct, sys, time
from pathlib import Path
from aiohttp import web
from androidtvremote2 import AndroidTVRemote, CannotConnect, ConnectionClosed, InvalidAuth
from androidtvremote2.remotemessage_pb2 import RemoteKeyCode
import shlex
import tempfile
import tvconfig
from adbtools import Adb, AdbError, PKG_RE
from appmeta import AppMeta
from tvtools import ADB, APP, CERT, FFMPEG, KEY, ROOT, SCRIPTS, find

KEYS = {
    "UP": "DPAD_UP", "DOWN": "DPAD_DOWN", "LEFT": "DPAD_LEFT", "RIGHT": "DPAD_RIGHT", "OK": "DPAD_CENTER",
    "BACK": "BACK", "HOME": "HOME", "MENU": "MENU", "POWER": "POWER", "MUTE": "MUTE", "ASSIST": "SEARCH",   # over the remote protocol the search key opens the Assistant
    "VOL_UP": "VOLUME_UP", "VOL_DOWN": "VOLUME_DOWN", "PLAY_PAUSE": "MEDIA_PLAY_PAUSE",
    "NEXT": "MEDIA_NEXT", "PREV": "MEDIA_PREVIOUS", "CH_UP": "CHANNEL_UP", "CH_DOWN": "CHANNEL_DOWN",
    "SETTINGS": "SETTINGS", "INPUT": "TV_INPUT", "SEARCH": "SEARCH", "DEL": "DEL",
}
APPS = {"settings": "android-app://com.android.tv.settings"}          # remote-protocol fallback when ADB is closed

WEB = APP / "static"
TS_PKG = "com.tailscale.ipn"
TAILSCALE = find("tailscale", "/Applications/Tailscale.app/Contents/MacOS/Tailscale", r"C:\Program Files\Tailscale\tailscale.exe")


class Screen:
    """Screenshots via ADB (needs Developer options > ADB debugging on the TV). One capture at a time, shared by all viewers."""
    def __init__(self, target):
        self.target, self.lock, self.t, self.jpg = target, asyncio.Lock(), 0.0, b""

    async def _run(self, *cmd, stdin=None, timeout=12):
        p = await asyncio.create_subprocess_exec(*cmd, stdin=asyncio.subprocess.PIPE if stdin else None,
                                                 stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
        out, err = await asyncio.wait_for(p.communicate(stdin), timeout)
        return out, err.decode(errors="replace")

    async def grab(self, fresh=False):
        async with self.lock:
            if not fresh and time.time() - self.t < 0.7 and self.jpg:
                return self.jpg
            png, err = await self._run(ADB, "-s", self.target, "exec-out", "screencap", "-p")
            if not png.startswith(b"\x89PNG"):                                   # offline/unauthorized: reconnect once
                await self._run(ADB, "connect", self.target)
                png, err = await self._run(ADB, "-s", self.target, "exec-out", "screencap", "-p")
                if not png.startswith(b"\x89PNG"):
                    raise RuntimeError((err or png[:80].decode(errors="replace")).strip() or "screencap failed")
            jpg, err = await self._run(FFMPEG, "-loglevel", "error", "-i", "pipe:0", "-vf", "scale=1280:-1",
                                       "-q:v", "5", "-f", "mjpeg", "pipe:1", stdin=png)
            if not jpg:
                raise RuntimeError(err.strip() or "ffmpeg failed")
            self.t, self.jpg = time.time(), jpg
            return jpg


AUD = b"\x00\x00\x00\x01\x09\xf0"                                       # H.264 access unit delimiter
START = b"\x00\x00\x00\x01"
RESYNC = -1
PART = b"--ffmpeg\r\nContent-type: image/jpeg\r\nContent-length: %d\r\n\r\n"


SCRCPY_VER = "5.0.1"                                                   # github.com/Genymobile/scrcpy release asset, Apache-2.0
SCRCPY_JAR = ROOT / "app" / "shared" / f"scrcpy-server-v{SCRCPY_VER}"
SCRCPY_SHA = "764eb6f79811d5211fe9df341120882ba9994c7a61b897d7bf3fb662e53bc536"
SCRCPY_REMOTE = "/data/local/tmp/scrcpy-server.jar"


async def scrcpy_launch(adb, opts):
    """Start scrcpy-server on the TV with these options; returns (adb shell process, local TCP port forwarded to it)."""
    if hashlib.sha256(SCRCPY_JAR.read_bytes()).hexdigest() != SCRCPY_SHA:
        raise AdbError("scrcpy-server file does not match the pinned checksum")
    if "yes" not in await adb.shell(f"[ -f {SCRCPY_REMOTE} ] && echo yes || echo no"):   # the server deletes itself unless cleanup=false
        rc, out, err = await adb.run("push", str(SCRCPY_JAR), SCRCPY_REMOTE, timeout=60)
        if rc:
            raise AdbError(err.strip() or "push failed")
    scid = "%08x" % random.getrandbits(31)
    rc, out, err = await adb.run("forward", "tcp:0", f"localabstract:scrcpy_{scid}")
    port = int(out.decode().strip())
    proc = await asyncio.create_subprocess_exec(
        ADB, "-s", adb.target, "shell",
        f"CLASSPATH={SCRCPY_REMOTE} app_process / com.genymobile.scrcpy.Server {SCRCPY_VER} scid={scid} tunnel_forward=true "
        f"audio=false cleanup=false {opts}", stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL)
    return proc, port


class Control:
    """Mouse and keyboard on the TV through scrcpy-server's control channel. No video here, so it costs the TV no encoding;
    the helper process is started on first use and stopped after a few idle minutes."""
    IDLE = 180
    W, H = 1920, 1080                                                 # without video, positions are raw display pixels

    def __init__(self, adb):
        self.adb, self.lock, self.proc, self.port, self.writer, self.timer = adb, asyncio.Lock(), None, None, None, None

    async def _open(self):
        size = re.search(r"(\d+)x(\d+)", await self.adb.shell("wm size"))
        if size:
            self.W, self.H = int(size.group(1)), int(size.group(2))
        self.proc, self.port = await scrcpy_launch(self.adb, "video=false control=true send_dummy_byte=true")
        for _ in range(100):                                          # until the server listens, adb closes the socket at once
            reader, writer = await asyncio.open_connection("127.0.0.1", self.port)
            try:
                first = await asyncio.wait_for(reader.read(1), 4)
            except asyncio.TimeoutError:
                first = b""
            if first == b"\x00":
                self.writer = writer
                self.drain = asyncio.create_task(reader.read(-1))     # device -> us messages (clipboard...) are not needed
                return
            writer.close()
            if self.proc.returncode is not None:
                break
            await asyncio.sleep(0.25)
        await self.close()
        raise AdbError("scrcpy control did not start")

    async def close(self):
        if self.timer:
            self.timer.cancel(); self.timer = None
        if self.writer:
            self.writer.close(); self.writer = None
        if self.proc and self.proc.returncode is None:
            self.proc.kill()
        if self.port:
            await self.adb.run("forward", "--remove", f"tcp:{self.port}"); self.port = None

    async def send(self, *msgs):
        async with self.lock:
            for attempt in (0, 1):
                try:
                    if not self.writer or self.writer.is_closing() or self.proc.returncode is not None:
                        await self.close(); await self._open()
                    self.writer.write(b"".join(msgs)); await self.writer.drain()
                    break
                except (ConnectionError, OSError):
                    await self.close()
                    if attempt:
                        raise AdbError("control channel lost")
            if self.timer:
                self.timer.cancel()
            self.timer = asyncio.get_running_loop().call_later(self.IDLE, lambda: asyncio.create_task(self.close()))

    # scrcpy control messages (big-endian), see ControlMessageReader.java in scrcpy v5.0.1
    def touch(self, action, x, y):                                    # action: 0 down, 1 up, 2 move; x, y in 0..1
        px, py = round(min(max(x, 0), 1) * (self.W - 1)), round(min(max(y, 0), 1) * (self.H - 1))
        return struct.pack(">BBqiiHHHii", 2, action, -2, px, py, self.W, self.H, 0 if action == 1 else 0xFFFF, 0, 0)

    def scroll(self, x, y, dy):
        px, py = round(min(max(x, 0), 1) * (self.W - 1)), round(min(max(y, 0), 1) * (self.H - 1))
        return struct.pack(">BiiHHhhi", 3, px, py, self.W, self.H, 0, max(-0x7FFF, min(0x7FFF, round(dy / 16 * 0x7FFF))), 0)

    @staticmethod
    def key(code):
        return struct.pack(">BBiii", 0, 0, code, 0, 0) + struct.pack(">BBiii", 0, 1, code, 0, 0)

    @staticmethod
    def text(t):
        raw = t.encode()
        if t.isascii():
            return struct.pack(">BI", 1, len(raw)) + raw              # typed as key events
        ctrl = 0x3000                                                 # META_CTRL_ON | META_CTRL_LEFT_ON
        k = lambda action, code, meta: struct.pack(">BBiii", 0, action, code, 0, meta)
        # accents, CJK...: key events cannot type them, so put the text on the TV clipboard and press Ctrl+V
        # (scrcpy's own paste sends KEYCODE_PASTE, which Flutter text fields ignore)
        return struct.pack(">BqBI", 9, 0, 0, len(raw)) + raw + k(0, 113, ctrl) + k(0, 50, ctrl) + k(1, 50, ctrl) + k(1, 113, 0)


class Scrcpy:
    """Raw H.264 of the TV screen from scrcpy-server, with a frame-rate cap.

    Encoding is done in software on the TV and is what makes it lag while someone watches: screenrecord encodes every
    frame the TV draws (measured ~1.8 of 4 cores on the animated home screen), scrcpy capped at 8 fps / 960 px ~0.7.
    """
    def __init__(self, adb, fps=8, size=960):
        self.adb, self.fps, self.size, self.port, self.proc, self.writer, self.first = adb, fps, size, None, None, None, b""

    async def open(self):
        self.proc, self.port = await scrcpy_launch(self.adb, f"control=false raw_stream=true video_bit_rate=2000000 max_fps={self.fps} max_size={self.size}")
        for _ in range(100):                                          # until the server listens, adb closes the socket at once
            reader, self.writer = await asyncio.open_connection("127.0.0.1", self.port)
            try:
                self.first = await asyncio.wait_for(reader.read(65536), 4)
            except asyncio.TimeoutError:
                return reader                                         # connected, nothing drawn yet
            if self.first:
                return reader
            self.writer.close()
            if self.proc.returncode is not None:
                break
            await asyncio.sleep(0.25)
        raise AdbError("scrcpy-server did not start")

    async def close(self):
        if self.writer:
            self.writer.close()                                       # the server on the TV exits when its client goes
        if self.proc and self.proc.returncode is None:
            self.proc.kill()
        if self.port:
            await self.adb.run("forward", "--remove", f"tcp:{self.port}")


async def live_session(adb, write, alive, eco=True):
    """One recording run decoded to an MJPEG multipart stream. Returns bytes sent, or RESYNC.

    eco: scrcpy-server with a frame-rate cap (light on the TV). Otherwise, or if that fails, screenrecord (sharper and
    smoother, heavy on the TV, capped by the TV at 3 minutes per run).

    screenrecord only emits a frame when the picture changes, and the H.264 parser holds each frame until the next one
    starts, so the last frame of any motion would stay hidden. When the stream goes quiet we feed a delimiter to push
    that frame out. If the quiet gap was really a network stall in the middle of a frame, the delimiter has corrupted
    it; we notice because the next chunk does not begin with a start code, and restart the recorder (RESYNC).
    """
    rec = scr = source = None
    if eco:
        scr = Scrcpy(adb)
        try:
            source = await scr.open()
        except Exception:
            await scr.close(); scr = None
    if not source:
        rec = await asyncio.create_subprocess_exec(
            ADB, "-s", adb.target, "exec-out", "screenrecord", "--output-format=h264", "--size", "1280x720",
            "--bit-rate", "3000000", "--time-limit", "180", "-", stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
        source = rec.stdout
    ff = await asyncio.create_subprocess_exec(
        FFMPEG, "-loglevel", "error", "-probesize", "32", "-analyzeduration", "0", "-flags", "low_delay", "-threads", "1", "-strict", "1",
        "-f", "h264", "-i", "pipe:0", "-fps_mode", "passthrough", "-pix_fmt", "yuvj420p", "-q:v", "6", "-max_error_rate", "1",
        "-threads", "1",                                              # a threaded JPEG encoder sits on the last few frames
        "-f", "mpjpeg", "-flush_packets", "1", "pipe:1",
        stdin=asyncio.subprocess.PIPE, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
    sent, resync = 0, False

    async def emit(jpg):
        nonlocal sent
        await write(PART % len(jpg) + jpg + b"\r\n"); sent += len(jpg)

    async def feed():
        nonlocal resync
        pending = flushed = False
        try:
            if scr and scr.first:
                ff.stdin.write(scr.first); await ff.stdin.drain(); pending = True
            while True:
                try:
                    chunk = await asyncio.wait_for(source.read(65536), 0.25)
                except asyncio.TimeoutError:
                    if not alive():                                   # viewer closed the page while the picture was still
                        break
                    if pending:
                        ff.stdin.write(AUD); await ff.stdin.drain(); pending, flushed = False, True
                    continue
                if not chunk:
                    break
                if flushed and not chunk.startswith(START):           # we flushed mid-frame: the picture is damaged, start over
                    resync = True
                    break
                ff.stdin.write(chunk); await ff.stdin.drain(); pending, flushed = True, False
        finally:
            ff.stdin.close()

    async def pump():
        buf = b""
        while chunk := await ff.stdout.read(65536):
            buf += chunk
            while True:
                h = buf.find(b"\r\n\r\n")
                if h < 0:
                    break
                m = re.search(rb"content-length:\s*(\d+)", buf[:h], re.I)
                if not m:
                    buf = buf[h + 4:]; continue
                start, n = h + 4, int(m.group(1))
                if len(buf) < start + n:
                    break
                await emit(buf[start:start + n]); buf = buf[start + n:]

    tasks = [asyncio.create_task(feed()), asyncio.create_task(pump())]
    try:
        await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
        if tasks[0].done() and not tasks[1].done() and not resync:    # recorder ended: let ffmpeg flush what is left
            await asyncio.wait([tasks[1]], timeout=2)
        if tasks[1].done() and tasks[1].exception():
            raise tasks[1].exception()                                # viewer went away
    finally:
        for t in tasks:
            t.cancel()
        for p in (rec, ff):
            if p and p.returncode is None:
                p.kill()
        if scr:
            await scr.close()
    return RESYNC if resync else sent


class Tv:
    """The Android TV Remote protocol connection (remote keys, volume, power state) to one TV."""
    def __init__(self, host):
        self.host = host
        self.remote = AndroidTVRemote("Claude Remote", CERT, KEY, host or "0.0.0.0")
        self.available = False
        self.paired = None                                            # None: not known yet
        self.error = "" if host else "no TV selected"
        self._task = None

    async def start(self):
        if await self.remote.async_generate_cert_if_missing():
            os.chmod(KEY, 0o600)
        self.remote.add_is_available_updated_callback(lambda ok: setattr(self, "available", ok))
        if self.host:
            self._task = asyncio.create_task(self._connect_loop())

    async def stop(self):
        if self._task:
            self._task.cancel()
        try:
            self.remote.disconnect()
        except Exception:
            pass
        self.available = False

    async def _connect_loop(self):
        while True:
            try:
                await self.remote.async_connect()
                self.available, self.paired, self.error = True, True, ""
                self.remote.keep_reconnecting()
                return
            except InvalidAuth:
                self.paired, self.error = False, "not paired"
                return
            except (CannotConnect, ConnectionClosed, OSError) as e:
                self.error = f"cannot connect: {e!r}"
                await asyncio.sleep(5)

    async def pair_start(self):
        """Makes the TV show a 6-character code."""
        if self._task:
            self._task.cancel()
        try:
            self.remote.disconnect()
        except Exception:
            pass
        self.available = False
        await self.remote.async_start_pairing()

        async def abandoned():                                        # nobody typed the code: go back to normal connecting
            await asyncio.sleep(180)
            await self._connect_loop()
        self._task = asyncio.create_task(abandoned())

    async def pair_finish(self, code):
        await self.remote.async_finish_pairing(code)
        if self._task:
            self._task.cancel()
        self.error = ""
        self._task = asyncio.create_task(self._connect_loop())

    def state(self):
        r = self.remote
        return {"host": self.host, "connected": self.available, "paired": self.paired, "error": self.error,
                "is_on": r.is_on, "app": r.current_app, "volume": r.volume_info, "device": r.device_info}

    def need(self):
        if not self.available:
            raise web.HTTPServiceUnavailable(text=json.dumps({"error": self.error or "TV not connected"}),
                                             content_type="application/json")
        return self.remote


class Session:
    """Everything bound to the TV being controlled. Picking another TV swaps the whole thing."""
    def __init__(self, host):
        self.host, target = host, f"{host}:5555" if host else ""
        self.tv, self.adb, self.screen = Tv(host), Adb(target), Screen(target)
        self.meta, self.control, self.held, self.warm = AppMeta(ADB, target), Control(self.adb), {}, None

    async def start(self):
        await self.tv.start()
        if self.host:
            self.warm = asyncio.create_task(self._prewarm())

    async def _prewarm(self):
        """Read app names/icons in the background so the page finds them cached."""
        for _ in range(60):
            if (await self.adb.status())["state"] == "ready":
                break
            await asyncio.sleep(10)
        try:
            await self.adb.shell("pkill screenrecord")                # recorders orphaned by a previous run of this server
        except Exception:
            pass
        try:
            await asyncio.gather(*(self.meta.get(p, self.adb.apk_paths[p]) for p in await self.adb.apps()), return_exceptions=True)
        except Exception:
            pass

    async def close(self):
        if self.warm:
            self.warm.cancel()
        for t in self.held.values():
            t.cancel()
        await self.tv.stop()
        await self.control.close()


class Live:
    """Stands in for one part of the current Session, so the route handlers keep working after a TV switch."""
    def __init__(self, get):
        self.__dict__["_get"] = get

    def __getattr__(self, name):
        return getattr(self._get(), name)


async def port_open(ip, port, timeout=0.7):
    try:
        _, w = await asyncio.wait_for(asyncio.open_connection(ip, port), timeout)
        w.close()
        return True
    except (OSError, asyncio.TimeoutError):
        return False


def local_networks():
    """The /24 around each of this computer's private IPv4 addresses (where a TV on the same Wi-Fi would be)."""
    ips = set()
    for probe in ("192.168.0.1", "10.0.0.1", "172.16.0.1", "8.8.8.8"):    # no packet is sent: this only asks the OS which address it would use
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sk:
            try:
                sk.connect((probe, 9)); ips.add(sk.getsockname()[0])
            except OSError:
                pass
    try:
        ips.update(i[4][0] for i in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET))
    except OSError:
        pass
    nets = []
    for ip in sorted(ips):
        a = ipaddress.ip_address(ip)
        if a.is_private and not a.is_loopback and not ip.startswith("100."):
            nets.append((ip, ipaddress.ip_network(ip + "/24", strict=False)))
    return nets


async def discover():
    """TVs we can see: LAN hosts answering on the remote (6466) or ADB (5555) port, and Android devices on the tailnet."""
    sem, found = asyncio.Semaphore(120), {}

    async def probe(ip, via, name=""):
        async with sem:
            remote, adb = await asyncio.gather(port_open(ip, 6466), port_open(ip, 5555))
        if remote or adb:
            found[ip] = {"ip": ip, "remote": remote, "adb": adb, "via": via, "name": name}

    jobs, mine = [], set()
    for me, net in local_networks():
        mine.add(me)
        jobs += [probe(str(h), "lan") for h in net.hosts() if str(h) != me]
    try:
        p = await asyncio.create_subprocess_exec(TAILSCALE, "status", "--json", stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
        out, _ = await asyncio.wait_for(p.communicate(), 8)
        for peer in (json.loads(out).get("Peer") or {}).values():
            if peer.get("OS") == "android" and peer.get("Online") and peer.get("TailscaleIPs"):
                jobs.append(probe(peer["TailscaleIPs"][0], "tailscale", peer.get("HostName", "")))
    except Exception:
        pass                                                          # no Tailscale CLI here: LAN results only
    await asyncio.gather(*jobs)
    return {"networks": [str(n) for _, n in local_networks()], "found": sorted(found.values(), key=lambda f: (f["via"], f["ip"]))}


@web.middleware
async def guard(request, handler):
    host = request.headers.get("Host", "").split(":")[0]
    if host not in ("127.0.0.1", "localhost"):
        raise web.HTTPForbidden(text="bad host")                      # DNS-rebinding guard
    if request.method == "POST":
        origin = request.headers.get("Origin")
        if origin and origin.split("//", 1)[-1] != request.headers.get("Host"):
            raise web.HTTPForbidden(text="cross-origin")
        if request.content_type not in ("application/json", "application/octet-stream"):
            raise web.HTTPUnsupportedMediaType(text="json only")
    return await handler(request)


async def body(request):
    try:
        return await request.json()
    except Exception:
        raise web.HTTPBadRequest(text="bad json")


def build(S):
    """S.cur is the current Session, S.cfg the saved TVs (tvconfig)."""
    routes = web.RouteTableDef()
    tv, adb, screen, meta, control = (Live(lambda n=n: getattr(S.cur, n)) for n in ("tv", "adb", "screen", "meta", "control"))

    async def switch(host):
        old, S.cur = S.cur, Session(host)
        await S.cur.start()
        await old.close()

    @routes.get("/")
    async def index(_):
        return web.FileResponse(WEB / "index.html", headers={"Cache-Control": "no-store"})

    @routes.get("/api/state")
    async def state(_):
        entry = tvconfig.find(S.cfg, tv.host) or {}
        return web.json_response({**tv.state(), "adb": await adb.status(), "name": entry.get("name", ""), "saved": bool(entry)})

    @routes.post("/api/adb/connect")
    async def adb_connect(request):
        await body(request)
        await adb.ensure()
        return web.json_response(await adb.status(force=True))

    @routes.post("/api/key")
    async def key(request):
        d = await body(request)
        k = str(d.get("key", "")).upper()
        name = KEYS.get(k) or (k if "KEYCODE_" + k in RemoteKeyCode.keys() else None)   # friendly alias or any raw key name
        if not name:
            raise web.HTTPBadRequest(text="unknown key")
        direction = str(d.get("dir") or "SHORT").upper()
        if direction not in ("SHORT", "START_LONG", "END_LONG"):
            raise web.HTTPBadRequest(text="bad dir")
        if name == "SETTINGS" and direction != "END_LONG":            # the key itself does nothing on many TVs; the Settings app always opens
            try:
                await adb.shell("am start -a android.settings.SETTINGS")
                return web.json_response({"ok": True})
            except Exception:
                pass                                                  # ADB closed: fall back to the key
        r, held = tv.need(), S.cur.held
        if direction != "SHORT" and name in held:
            held.pop(name).cancel()
        if direction == "START_LONG":                                 # safety net: never leave a key held if the page vanishes
            held[name] = asyncio.get_running_loop().call_later(8, lambda: (held.pop(name, None), tv.available and r.send_key_command(name, "END_LONG")))
        r.send_key_command(name, direction)
        return web.json_response({"ok": True})

    @routes.post("/api/app")
    async def app(request):
        d = await body(request)
        link = str(d.get("link", "")).strip()
        if not link:
            raise web.HTTPBadRequest(text="link required")
        if (await adb.status())["state"] == "ready":                  # ADB reports whether the TV really opened it
            try:
                await (adb.open_settings() if link == "settings" else adb.launch(link) if PKG_RE.match(link) else adb.open_link(link))
            except AdbError as e:
                return web.json_response({"error": str(e)}, status=400)
            return web.json_response({"ok": True, "via": "adb"})
        tv.need().send_launch_app_command(APPS.get(link, link))        # best effort: this TV often ignores it
        return web.json_response({"ok": True, "via": "remote"})

    @routes.post("/api/volume")
    async def volume(request):
        want = (await body(request)).get("level")
        info = tv.remote.volume_info or {}
        if not isinstance(want, int) or not 0 <= want <= int(info.get("max") or 100):
            raise web.HTTPBadRequest(text="bad level")
        if (await adb.status())["state"] == "ready":
            try:
                if await adb.set_volume_fast(want):
                    for _ in range(10):                               # the TV reports the new level over the remote link
                        if (tv.remote.volume_info or {}).get("level") == want:
                            return web.json_response({"ok": True, "via": "adb-fast"})
                        await asyncio.sleep(0.1)
                await adb.set_volume(want)
                return web.json_response({"ok": True, "via": "adb"})
            except AdbError:
                pass
        r = tv.need()
        for _ in range(60):                                           # no ADB: step with the volume keys
            cur = (r.volume_info or {}).get("level")
            if cur is None or cur == want:
                break
            r.send_key_command("VOLUME_UP" if cur < want else "VOLUME_DOWN")
            await asyncio.sleep(0.2)
        return web.json_response({"ok": True, "via": "keys", "level": (r.volume_info or {}).get("level")})

    @routes.post("/api/text")
    async def text(request):
        d = await body(request)
        tv.need().send_text(str(d.get("text", ""))[:500])
        return web.json_response({"ok": True})

    @routes.get("/api/screen")
    async def screen_(_):
        try:
            return web.Response(body=await screen.grab(), content_type="image/jpeg", headers={"Cache-Control": "no-store"})
        except Exception as e:
            return web.json_response({"error": f"screen: {e}"}, status=503)

    @routes.get("/api/stream")
    async def stream(request):
        resp = web.StreamResponse(headers={"Content-Type": "multipart/x-mixed-replace;boundary=ffmpeg", "Cache-Control": "no-store"})
        await resp.prepare(request)
        try:
            while True:
                t = time.time()
                sent = await live_session(S.cur.adb, resp.write, lambda: request.transport is not None and not request.transport.is_closing(),
                                          eco=request.query.get("q") != "hd")
                if request.transport is None or request.transport.is_closing():
                    break
                if sent == RESYNC:
                    continue
                if not sent or time.time() - t < 5:                   # recorder is not working: end so the page falls back
                    break
        except (ConnectionError, asyncio.CancelledError):
            pass
        return resp

    def adb_errors(fn):
        async def wrapped(request):
            try:
                return await fn(request)
            except AdbError as e:
                return web.json_response({"error": str(e)}, status=400)
            except web.HTTPException:
                raise
            except Exception as e:
                return web.json_response({"error": f"{e.__class__.__name__}: {e}"}, status=500)
        return wrapped

    @routes.get("/api/apps")
    @adb_errors
    async def apps(request):
        pkgs = await adb.apps(request.query.get("system") == "1")
        return web.json_response({"apps": [{"pkg": p, "meta": meta.cached(p, adb.apk_paths[p])} for p in pkgs]})

    @routes.get("/api/app/meta")
    @adb_errors
    async def app_meta(request):
        pkg = request.query.get("pkg", "")
        if pkg not in adb.apk_paths:
            raise web.HTTPNotFound()
        return web.json_response(await meta.get(pkg, adb.apk_paths[pkg]))

    @routes.get("/api/app/icon")
    async def app_icon(request):
        pkg = request.query.get("pkg", "")
        f = meta.icon_file(pkg)
        if not PKG_RE.match(pkg) or not f.exists():
            raise web.HTTPNotFound()
        data = f.read_bytes()
        kind = "image/png" if data[:4] == b"\x89PNG" else "image/webp" if data[:4] == b"RIFF" else "image/jpeg"
        return web.Response(body=data, content_type=kind, headers={"Cache-Control": "max-age=86400", "X-Content-Type-Options": "nosniff"})

    @routes.post("/api/app/{action}")
    @adb_errors
    async def app_action(request):
        action, pkg = request.match_info["action"], str((await body(request)).get("pkg", ""))
        if action not in ("launch", "stop", "uninstall"):
            raise web.HTTPNotFound()
        res = await getattr(adb, action)(pkg)
        return web.json_response({"ok": True, "result": res})

    @routes.get("/api/files")
    @adb_errors
    async def files(request):
        path, items = await adb.ls(request.query.get("path", ""))
        return web.json_response({"path": path, "items": items, "space": adb.space, "volumes": adb.volumes})

    @routes.get("/api/file")
    @adb_errors
    async def file_get(request):
        path = request.query.get("path", "")
        proc = await adb.open_pull(path)
        kind = mimetypes.guess_type(path)[0] or ""
        inline = request.query.get("inline") == "1" and kind in ("image/jpeg", "image/png", "image/webp", "image/gif")   # preview: bitmaps only
        resp = web.StreamResponse(headers={"Content-Type": kind if inline else "application/octet-stream", "X-Content-Type-Options": "nosniff",
                                           "Content-Disposition": '%s; filename="%s"' % ("inline" if inline else "attachment", path.rsplit("/", 1)[-1].replace('"', "_"))})
        await resp.prepare(request)
        while chunk := await proc.stdout.read(1 << 20):
            await resp.write(chunk)
        await proc.wait()
        return resp

    @routes.post("/api/file/delete")
    @adb_errors
    async def file_delete(request):
        await adb.delete(str((await body(request)).get("path", "")))
        return web.json_response({"ok": True})

    @routes.post("/api/file/{action}")
    @adb_errors
    async def file_action(request):
        action, d = request.match_info["action"], await body(request)
        path = str(d.get("path", ""))
        if action == "mkdir":
            await adb.mkdir(path)
        elif action == "rename":
            await adb.rename(path, str(d.get("name", "")))
        elif action == "open":
            await adb.open_on_tv(path)
        elif action == "install":
            return web.json_response(adb.job_install_path(path))
        else:
            raise web.HTTPNotFound()
        return web.json_response({"ok": True})

    @routes.post("/api/fetch")
    @adb_errors
    async def fetch(request):
        d = await body(request)
        return web.json_response(adb.job_fetch(str(d.get("url", "")).strip(), d.get("dest") or ""))

    @routes.post("/api/install")
    @adb_errors
    async def install(request):
        return web.json_response(adb.job_install_url(str((await body(request)).get("url", "")).strip()))

    @routes.post("/api/upload")
    @adb_errors
    async def upload(request):
        kind = request.query.get("kind", "file")                          # file -> push to dest, install -> adb install
        if kind not in ("file", "install"):
            raise web.HTTPBadRequest(text="bad kind")
        tmp = tempfile.TemporaryDirectory(prefix="tvup-")
        try:
            local = await adb.save_upload(request, tmp.name, request.query.get("name", "upload.bin"))
            return web.json_response(adb.job_from_local(local, tmp, kind, request.query.get("dest", "")))
        except BaseException:
            tmp.cleanup()
            raise

    @routes.post("/api/touch")
    @adb_errors
    async def touch(request):
        d = await body(request)
        x, y, a = float(d.get("x", 0)), float(d.get("y", 0)), d.get("a")
        if a == "tap":
            await control.send(control.touch(0, x, y), control.touch(1, x, y))
        elif a in ("down", "move", "up"):
            await control.send(control.touch({"down": 0, "up": 1, "move": 2}[a], x, y))
        elif a == "scroll":
            await control.send(control.scroll(x, y, float(d.get("dy", 0))))
        else:
            raise web.HTTPBadRequest(text="bad action")
        return web.json_response({"ok": True})

    @routes.post("/api/type")
    @adb_errors
    async def type_(request):
        d = await body(request)
        n, t = min(int(d.get("del") or 0), 500), str(d.get("text") or "")[:500]
        msgs = [control.key(67)] * n                                  # KEYCODE_DEL
        msgs += [control.key(112)] * min(int(d.get("fdel") or 0), 500)  # KEYCODE_FORWARD_DEL
        if t:
            msgs.append(control.text(t))
        if d.get("enter"):
            msgs.append(control.key(66))                              # KEYCODE_ENTER
        if msgs:
            await control.send(*msgs)
        return web.json_response({"ok": True})

    @routes.post("/api/setup")
    async def setup(request):
        mode = (await body(request)).get("mode")
        if mode not in ("apply", "undo"):
            raise web.HTTPBadRequest(text="bad mode")

        async def go(j):                                              # same script as the command line: deploy/tv_setup.py
            p = await asyncio.create_subprocess_exec(sys.executable, str(SCRIPTS / "tv_setup.py"), mode, adb.target,
                                                     stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.STDOUT)
            async for line in p.stdout:
                text = line.decode(errors="replace")
                j["log"] += text; j["msg"] = text.strip()[:140] or j["msg"]; j["pct"] = min(95, j["pct"] + 4)
            if await p.wait():
                raise AdbError(j["msg"] or "setup failed")
            adb.apk_paths.clear()
            return "xong"
        j = adb.job(go, f"setup {mode}")
        j["log"] = ""
        return web.json_response(j)

    @routes.get("/api/job")
    async def job(request):
        j = adb.jobs.get(request.query.get("id", ""))
        return web.json_response(j) if j else web.json_response({"error": "unknown job"}, status=404)

    @routes.post("/api/wake")
    async def wake(request):
        await body(request)
        e = tvconfig.find(S.cfg, tv.host) or {}
        p = await asyncio.create_subprocess_exec(
            sys.executable, str(SCRIPTS / "wake.py"), "--wait", "60",
            env={**os.environ, "TV_HOST": tv.host, "TV_MAC": e.get("mac", ""), "TV_LAN": e.get("lan", ""), "RELAY_USER": e.get("relay_user", ""),
                 "RELAY_HOST": e.get("relay_host", ""), "RELAY_CMD": e.get("relay_cmd", "")},
            stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.STDOUT)
        out, _ = await p.communicate()
        if p.returncode == 0 and not tv.remote.is_on:
            await asyncio.sleep(2)
            if tv.available:
                tv.remote.send_key_command("POWER")                   # screen-off but network alive
        return web.json_response({"ok": p.returncode == 0, "log": out.decode(errors="replace")[-400:]})

    # ---- TVs: saved list, discovery, first-time setup ----
    @routes.get("/api/tvs")
    async def tvs(_):
        return web.json_response({"current": tv.host, "tvs": S.cfg["tvs"]})

    @routes.get("/api/discover")
    async def discover_(_):
        return web.json_response(await discover())

    @routes.post("/api/tv/select")
    async def tv_select(request):
        host = str((await body(request)).get("host", "")).strip()
        try:
            ipaddress.ip_address(host)
        except ValueError:
            raise web.HTTPBadRequest(text="not an IP address")
        if tvconfig.find(S.cfg, host):
            S.cfg["current"] = host; tvconfig.save(S.cfg)
        await switch(host)
        return web.json_response({"ok": True})

    async def tv_info():
        """What the first-time setup needs to know about the current TV (needs ADB)."""
        out = await adb.shell("getprop ro.product.manufacturer; echo '#'; getprop ro.product.model; echo '#'; pm path " + TS_PKG
                              + "; echo '#'; pm path me.efesser.flauncher; echo '#'; ip addr show")
        part = [x.strip() for x in out.split("#", 4)] + [""] * 5
        ts = lan = mac = ""
        for block in re.split(r"^\d+: ", part[4], flags=re.M):         # one block per network interface
            ip = re.search(r"inet ([0-9.]+)/", block)
            ether = re.search(r"link/ether ([0-9a-f:]{17})", block)
            if not ip:
                continue
            if ip[1].startswith("100."):
                ts = ip[1]
            elif not lan and ether and ipaddress.ip_address(ip[1]).is_private and not ip[1].startswith("127."):
                lan, mac = ip[1], ether[1].upper()                    # the interface that is actually on the home network
        return {"model": f"{part[0]} {part[1]}".strip(), "tailscale_installed": "package:" in part[2], "tailscale_ip": ts, "lan_ip": lan,
                "mac": mac, "flauncher": "package:" in part[3]}

    @routes.get("/api/setup/status")
    async def setup_status(_):
        st = await adb.status(force=True)
        info = {}
        if st["state"] == "ready":
            try:
                info = await tv_info()
            except AdbError:
                pass
        return web.json_response({"host": tv.host, "adb": st, "remote": tv.state(), "saved": tvconfig.find(S.cfg, tv.host), **info})

    HEALTH = ("getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk; "
              "getprop ro.build.version.security_patch; getprop ro.board.platform; getprop ro.product.cpu.abi; cat /sys/devices/system/cpu/present; "
              "printf '\\043~\\043\\n'; head -1 /proc/stat; sleep 0.6; head -1 /proc/stat; printf '\\043~\\043\\n'; grep -E '^(MemTotal|MemAvailable):' /proc/meminfo; printf '\\043~\\043\\n'; df -k /data | tail -1; "
              "printf '\\043~\\043\\n'; cat /proc/uptime; printf '\\043~\\043\\n'; wm size; wm density; dumpsys display | grep -m1 -o 'fps=[0-9.]*'; printf '\\043~\\043\\n'; dumpsys wifi | grep -m1 mWifiInfo; "
              "printf '\\043~\\043\\n'; ip -o -4 addr show; printf '\\043~\\043\\n'; top -n 1 -b -m 7 -o %CPU,%MEM,ARGS -s 1 | tail -7")

    @routes.get("/api/health")
    @adb_errors
    async def health(_):
        """A snapshot of the TV for the Health tab: hardware, load, memory, storage, Wi-Fi, and how long ADB takes to answer."""
        t0 = time.monotonic()
        await adb.shell(":")
        ping = round((time.monotonic() - t0) * 1000)
        part = [x.strip() for x in (await adb.shell(HEALTH, timeout=20)).split("#~#")] + [""] * 8
        props = (part[0].splitlines() + [""] * 8)[:8]
        cpu = None
        stat = [[int(n) for n in line.split()[1:]] for line in part[1].splitlines() if line.startswith("cpu")]
        if len(stat) == 2:
            total, idle = sum(stat[1]) - sum(stat[0]), (stat[1][3] + stat[1][4]) - (stat[0][3] + stat[0][4])
            cpu = round(100 * (total - idle) / total) if total > 0 else None
        mem = {m[1]: int(m[2]) * 1024 for m in re.finditer(r"(?m)^(\w+):\s+(\d+) kB", part[2])}
        df = part[3].split()
        wifi = {k.strip(): v.strip() for k, v in (kv.split(":", 1) for kv in part[6].replace("mWifiInfo", "").split(",") if ":" in kv)}
        num = lambda s: int(m[0]) if (m := re.search(r"-?\d+", s or "")) else None
        top = []
        for line in part[8].splitlines():
            m = re.match(r"\s*([\d.]+)\s+([\d.]+)\s+(.+)", line)
            if m and not m[3].startswith("top -n"):
                top.append({"cpu": float(m[1]), "mem": float(m[2]), "name": m[3].split()[0].strip("[]")})
        cores = props[7].split("-")
        return web.json_response({
            "ping_ms": ping, "maker": props[0], "model": props[1], "android": props[2], "sdk": props[3], "patch": props[4], "chip": props[5], "abi": props[6],
            "cores": int(cores[-1]) + 1 if cores[-1].isdigit() else None, "cpu": cpu,
            "mem_total": mem.get("MemTotal"), "mem_free": mem.get("MemAvailable"),
            "disk_total": int(df[1]) * 1024 if len(df) > 3 and df[1].isdigit() else None, "disk_free": int(df[3]) * 1024 if len(df) > 3 and df[3].isdigit() else None,
            "uptime": float(part[4].split()[0]) if part[4].split() else None,
            "screen": (re.search(r"(\d+x\d+)", part[5]) or [""])[0], "density": num((re.search(r"density: (\d+)", part[5]) or ["", ""])[1]),
            "fps": round(float(m[1])) if (m := re.search(r"fps=([\d.]+)", part[5])) else None,
            "wifi": {"ssid": wifi.get("SSID", ""), "rssi": num(wifi.get("RSSI")), "speed": num(wifi.get("Link speed")), "freq": num(wifi.get("Frequency"))} if wifi.get("SSID") else None,
            "ips": [{"if": m[1], "ip": m[2]} for m in re.finditer(r"(?m)^\d+: (\S+)\s+inet ([\d.]+)", part[7]) if m[1] != "lo"],
            "top": top[:5], "remote": tv.state()})

    @routes.post("/api/search")
    @adb_errors
    async def search(request):
        """Hands a sentence to the TV's own search / Assistant, as if it had been spoken to the TV's remote."""
        q = str((await body(request)).get("query", "")).strip()
        if not q:
            raise web.HTTPBadRequest(text="empty query")
        out = await adb.shell("am start -a android.search.action.GLOBAL_SEARCH --es query " + shlex.quote(q) + " 2>&1")
        if "Error" in out:
            return web.json_response({"error": "TV này không có tìm kiếm / Assistant của Google"}, status=400)
        return web.json_response({"ok": True})

    @routes.post("/api/shell")
    @adb_errors
    async def shell(request):
        """Terminal tab: one command as the TV's shell user."""
        cmd = str((await body(request)).get("cmd", "")).strip()
        if not cmd:
            raise web.HTTPBadRequest(text="empty command")
        out = await adb.shell(cmd + " 2>&1", timeout=30)
        return web.json_response({"out": out.replace("\r\n", "\n")[-200000:]})

    @routes.post("/api/tv/save")
    @adb_errors
    async def tv_save(request):
        """Remember the current TV. Once it has a Tailscale IP that becomes its address, so it works from anywhere."""
        d = await body(request)
        info = await tv_info() if (await adb.status())["state"] == "ready" else {}
        old = tv.host
        host = info.get("tailscale_ip") or old
        if not host:
            raise web.HTTPBadRequest(text="no TV selected")
        entry = tvconfig.upsert(S.cfg, {"name": str(d.get("name") or "").strip()[:60] or info.get("model") or host, "host": host,
                                        "lan": info.get("lan_ip", ""), "mac": info.get("mac", "")}, replace_host=old)
        tvconfig.save(S.cfg)
        if host != old:
            await switch(host)
        return web.json_response({"ok": True, "tv": entry, "moved": host != old})

    @routes.post("/api/tv/remove")
    async def tv_remove(request):
        host = str((await body(request)).get("host", ""))
        tvconfig.remove(S.cfg, host); tvconfig.save(S.cfg)
        if host == tv.host:
            await switch(S.cfg["current"])
        return web.json_response({"ok": True})

    @routes.post("/api/tailscale/install")
    @adb_errors
    async def tailscale_install(request):
        await body(request)
        return web.json_response(adb.job_install_tailscale())

    @routes.post("/api/tailscale/open")
    @adb_errors
    async def tailscale_open(request):
        await body(request)
        await adb.launch(TS_PKG)
        return web.json_response({"ok": True})

    @routes.post("/api/pair/start")
    async def pair_start(request):
        await body(request)
        try:
            await tv.pair_start()
        except (CannotConnect, ConnectionClosed, OSError) as e:
            return web.json_response({"error": f"không tới được dịch vụ remote của TV (cổng 6467): {e}"}, status=400)
        return web.json_response({"ok": True})

    @routes.post("/api/pair/finish")
    async def pair_finish(request):
        code = str((await body(request)).get("code", "")).strip()
        try:
            await tv.pair_finish(code)
        except InvalidAuth:
            return web.json_response({"error": "sai mã, thử lại"}, status=400)
        except (CannotConnect, ConnectionClosed, OSError, ValueError) as e:
            return web.json_response({"error": f"ghép thất bại, bấm Ghép lại: {e}"}, status=400)
        for _ in range(30):
            if tv.available:
                break
            await asyncio.sleep(0.2)
        return web.json_response({"ok": True, "connected": tv.available})

    @routes.get("/api/about")
    async def about(request):
        try:
            version = (ROOT / "VERSION").read_text().strip()
        except OSError:
            version = ""
        return web.json_response({"version": version})

    @routes.post("/api/shutdown")
    async def shutdown(request):
        await body(request)
        asyncio.get_running_loop().call_later(0.3, S.stop.set)        # after this reply has gone out
        return web.json_response({"ok": True})

    app_ = web.Application(middlewares=[guard])
    app_.add_routes(routes)
    app_.router.add_static("/web", WEB)                              # style.css and js/ next to index.html
    return app_


class State:
    pass


async def main(a):
    S = State()
    S.cfg = tvconfig.load()
    S.stop = asyncio.Event()
    S.cur = Session(a.tv or S.cfg["current"])
    await S.cur.start()
    runner = web.AppRunner(build(S))
    await runner.setup()
    await web.TCPSite(runner, "127.0.0.1", a.port).start()
    print(f"Remote: http://127.0.0.1:{a.port}  (TV {S.cur.host or 'not chosen yet'})", flush=True)
    await S.stop.wait()                                               # the page's "Tắt" button, or Ctrl+C
    print("Stopping.", flush=True)
    await S.cur.close()
    try:
        await asyncio.wait_for(runner.cleanup(), 4)
    except asyncio.TimeoutError:
        pass                                                          # open live views are cancelled when the loop ends


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--tv", default=os.environ.get("TV_HOST", ""), help="TV IP for this run (default: the one last picked in the web UI)")
    p.add_argument("--port", type=int, default=8765)
    try:
        asyncio.run(main(p.parse_args()))
    except KeyboardInterrupt:
        pass
