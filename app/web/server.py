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
import scenes as scn
from adbtools import Adb, AdbError, PKG_RE, FP_RE, PROTECTED, my_fingerprint, memory_by_app
from appmeta import AppMeta
from tvtools import ADB, APP, CERT, FFMPEG, KEY, ROOT, SCRIPTS, find

KEYS = {
    "UP": "DPAD_UP", "DOWN": "DPAD_DOWN", "LEFT": "DPAD_LEFT", "RIGHT": "DPAD_RIGHT", "OK": "DPAD_CENTER",
    "BACK": "BACK", "HOME": "HOME", "MENU": "MENU", "POWER": "POWER", "MUTE": "MUTE", "ASSIST": "SEARCH",   # over the remote protocol the search key opens the Assistant
    "VOL_UP": "VOLUME_UP", "VOL_DOWN": "VOLUME_DOWN", "PLAY_PAUSE": "MEDIA_PLAY_PAUSE",
    "NEXT": "MEDIA_NEXT", "PREV": "MEDIA_PREVIOUS", "CH_UP": "CHANNEL_UP", "CH_DOWN": "CHANNEL_DOWN",
    "SETTINGS": "SETTINGS", "INPUT": "TV_INPUT", "SEARCH": "SEARCH", "DEL": "DEL",
    "INFO": "INFO", "GUIDE": "GUIDE", "LIVE": "TV", "SUBTITLE": "CAPTIONS", "REWIND": "MEDIA_REWIND", "STOP": "MEDIA_STOP", "FFWD": "MEDIA_FAST_FORWARD",
    "SLEEP": "SLEEP", "WAKE": "WAKEUP",                               # "tắt TV" / "bật TV" spoken: never POWER, which would turn a TV that is off on
}
# The "More" keys go through ADB when it is open: this TV ignores some of them over the remote protocol (the input key, for one).
ADB_KEYS = {"INPUT": 178, "INFO": 165, "GUIDE": 172, "LIVE": 170, "SUBTITLE": 175, "CH_UP": 166, "CH_DOWN": 167, "REWIND": 89, "STOP": 86, "FFWD": 90}
OPENERS = {"MENU", "SETTINGS", "INPUT", "GUIDE", "LIVE"}             # keys that should bring up a screen of their own
APPS = {"settings": "android-app://com.android.tv.settings"}          # remote-protocol fallback when ADB is closed

WEB = ROOT / "app" / "shared" / "web"                             # the one page all three remotes serve (the two Android apps carry it as an asset)
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
SCRCPY_JAR = ROOT / "app" / "shared" / "assets" / "scrcpy-server.jar"   # the same file the two Android apps carry
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
        f"cleanup=false {opts}", stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL)
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
        self.proc, self.port = await scrcpy_launch(self.adb, "video=false audio=false control=true send_dummy_byte=true")
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
    # longest side -> bits per second, frames per second: the same levels as the phone app (Tv.QUALITY)
    LEVELS = {240: (120_000, 8), 360: (200_000, 8), 480: (300_000, 8), 720: (800_000, 10), 960: (2_000_000, 8)}

    def __init__(self, adb, size=960):
        size = size if size in self.LEVELS else 960
        self.rate, self.fps = self.LEVELS[size]
        self.adb, self.size, self.port, self.proc, self.writer, self.first = adb, size, None, None, None, b""

    async def open(self):
        self.proc, self.port = await scrcpy_launch(self.adb, f"audio=false control=false raw_stream=true video_bit_rate={self.rate} max_fps={self.fps} max_size={self.size}")
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


async def live_session(adb, write, alive, eco=True, size=960):
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
        scr = Scrcpy(adb, size)
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


async def cast_name(ip):
    """The name an Android TV gives itself, asked from its Chromecast service (every Android TV has one). "" when it does not answer."""
    try:
        r, w = await asyncio.wait_for(asyncio.open_connection(ip, 8008), 0.8)
        try:
            w.write(f"GET /setup/eureka_info HTTP/1.1\r\nHost: {ip}:8008\r\nConnection: close\r\n\r\n".encode())
            got = b""
            while len(got) < 20000:                                   # the TV may keep the connection open: stop at the name
                chunk = await asyncio.wait_for(r.read(2048), 1.5)
                if not chunk:
                    break
                got += chunk
                m = re.search(r'"name"\s*:\s*"([^"]{1,60})"', got.decode("utf-8", "replace"))
                if m:
                    return m[1]
        finally:
            w.close()
    except (OSError, asyncio.TimeoutError):
        pass
    return ""


async def discover():
    """TVs we can see: LAN hosts answering on the remote (6466) or ADB (5555) port, and Android devices on the tailnet."""
    sem, found = asyncio.Semaphore(120), {}

    async def probe(ip, via, name=""):
        async with sem:
            remote, adb = await asyncio.gather(port_open(ip, 6466), port_open(ip, 5555))
        if remote or adb:
            found[ip] = {"ip": ip, "remote": remote, "adb": adb, "via": via, "name": await cast_name(ip) or name}

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
        # The page names its scripts and its stylesheet with a stamp of their newest change, so a browser never pairs a new
        # page with scripts it kept from an older version (which showed the new layout unstyled, run by the old code).
        stamp = str(int(max(f.stat().st_mtime for f in WEB.rglob("*") if f.is_file())))
        html = re.sub(r'((?:src|href)="(?:style\.css|stats\.js|js/[^"?]+))"', r'\1?v=' + stamp + '"', (WEB / "index.html").read_text(encoding="utf-8"))
        html = html.replace("<html ", '<html data-core="mac" ', 1)        # which core serves the page decides what it shows (see core.js)
        return web.Response(text=html, content_type="text/html", headers={"Cache-Control": "no-store"})

    @routes.get("/api/state")
    async def state(_):
        entry = tvconfig.find(S.cfg, tv.host) or {}
        st = await adb.status()
        if st["state"] in ("ready", "unauthorized"):
            asyncio.create_task(keep_trusted(st["state"]))
        return web.json_response({**tv.state(), "adb": st, "name": entry.get("name", ""), "saved": bool(entry)})

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
        if (k in OPENERS or k in ADB_KEYS) and direction == "SHORT":
            try:
                front = lambda: adb.shell("dumpsys window | grep -m1 mCurrentFocus", timeout=5)
                before = (await front()).strip() if k in OPENERS else ""   # openers: say so when the screen ignores them
                if name == "SETTINGS":
                    await adb.shell("am start -a android.settings.SETTINGS")
                elif k in ADB_KEYS:
                    await adb.shell(f"input keyevent {ADB_KEYS[k]}")
                else:
                    tv.need().send_key_command(name, "SHORT")
                if k not in OPENERS:
                    return web.json_response({"ok": True})
                await asyncio.sleep(0.9)
                return web.json_response({"ok": True, "noop": bool(before) and (await front()).strip() == before})
            except web.HTTPException:
                raise
            except Exception:
                pass                                                  # ADB closed: just press the key
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
                                          eco=request.query.get("q") != "hd", size=int(request.query.get("size", "960") or 960) if request.query.get("size", "960").isdigit() else 960)
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

    # ---- the TV's "Allow USB debugging?" question: answered for one named key only (see AdbPrompt.kt) ----
    @routes.get("/api/adb/prompt")
    @adb_errors
    async def adb_prompt(_):
        p = await adb.prompt_read() if (await adb.status())["state"] == "ready" else None
        return web.json_response({"fingerprint": p[0] if p else ""})

    @routes.post("/api/adb/allow")
    @adb_errors
    async def adb_allow(request):
        d = await body(request)
        fp = str(d.get("fingerprint", "")).upper()
        if not FP_RE.fullmatch(fp):
            return web.json_response({"error": "bad fingerprint"}, status=400)
        async with prompt_lock:
            return web.json_response({"result": await adb.prompt_allow(fp, min(max(int(d.get("wait", 0) or 0), 0), 40), bool(d.get("others")))})

    @routes.post("/api/adb/deny")
    @adb_errors
    async def adb_deny(request):
        async with prompt_lock:
            return web.json_response({"ok": await adb.prompt_deny(str((await body(request)).get("fingerprint", "")))})

    prompt_lock = asyncio.Lock()
    trust = {"asked": 0, "fetched": set()}

    async def keep_trusted(st):
        """While ADB works, fetch a TV Setup pass for this TV once; when the TV asks about this computer, have TV Setup answer."""
        entry = tvconfig.find(S.cfg, tv.host)
        if not entry:
            return
        host = tv.host
        try:
            if st == "ready" and not entry.get("setup_pass") and host not in trust["fetched"]:
                trust["fetched"].add(host)
                token = await adb.setup_pass()
                if token:
                    entry["setup_pass"] = token; tvconfig.save(S.cfg)
            elif st == "unauthorized" and entry.get("setup_pass") and time.time() - trust["asked"] > 45:
                trust["asked"] = time.time()
                fp = my_fingerprint()
                if not fp:
                    return
                import aiohttp
                async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=70)) as s:
                    async with s.post(f"http://{host}:8765/api/adb/allow", json={"fingerprint": fp, "wait": 30, "others": True},
                                      headers={"Cookie": "tvr=" + entry["setup_pass"]}) as r:
                        if r.status in (401, 403):
                            entry.pop("setup_pass", None); tvconfig.save(S.cfg)   # "Unpair all" was pressed on the TV
                        elif r.status == 200 and (await r.json()).get("result") == "allowed":
                            print(f"TV Setup allowed this computer on {host}", flush=True)
                            await adb.ensure()
        except Exception:
            pass

    @routes.get("/api/apps")
    @adb_errors
    async def apps(request):
        pkgs = await adb.apps(request.query.get("system") == "1")
        off = await adb.disabled()
        return web.json_response({"apps": [{"pkg": p, "off": p in off, "keep": bool(PROTECTED.fullmatch(p)), "meta": meta.cached(p, adb.apk_paths[p])} for p in pkgs]})

    @routes.get("/api/apps/report")
    @adb_errors
    async def apps_report(_):
        """What an AI (or a person) needs to say which apps can go; the page turns it into a prompt to copy."""
        lst = lambda out: sorted(l[8:].strip() for l in out.splitlines() if l.startswith("package:"))
        user = lst(await adb.shell("pm list packages -3 -e"))
        m = re.search(r"MemTotal:\s+(\d+)", await adb.shell("cat /proc/meminfo"))
        await adb.apps(False)                                         # the apk paths the names come from
        return web.json_response({
            "device": ((await adb.shell("getprop ro.product.manufacturer")).strip() + " " + (await adb.shell("getprop ro.product.model")).strip()).strip(),
            "android": (await adb.shell("getprop ro.build.version.release")).strip(),
            "ram_mb": int(m.group(1)) // 1024 if m else 0,
            "user": user, "system": lst(await adb.shell("pm list packages -s -e")), "disabled": lst(await adb.shell("pm list packages -d")),
            "memory": [{"pkg": p, "mb": mb} for p, mb in memory_by_app(await adb.shell("dumpsys meminfo"))],
            "labels": {p: (meta.cached(p, adb.apk_paths[p]) or {}).get("label") for p in user if p in adb.apk_paths and (meta.cached(p, adb.apk_paths[p]) or {}).get("label")},
        })

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
        if action not in ("launch", "stop", "uninstall", "disable", "enable"):
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
        d = await body(request)
        mode = d.get("mode")
        if mode not in ("apply", "undo"):
            raise web.HTTPBadRequest(text="bad mode")

        async def go(j):                                              # same script as the command line: deploy/tv_setup.py
            choice = [] if mode == "undo" else [*([] if d.get("anim", True) else ["--keep-anim"]), *([] if d.get("home", True) else ["--keep-home"]),
                      *(["--disable=" + ",".join(x for x in d["disable"] if PKG_RE.match(str(x)))] if isinstance(d.get("disable"), list) else [])]
            p = await asyncio.create_subprocess_exec(sys.executable, str(SCRIPTS / "tv_setup.py"), mode, adb.target, *(["--vi"] if d.get("lang") != "en" else []), *choice,
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

    @routes.get("/api/setup/plan")
    @adb_errors
    async def setup_plan(_):
        p = await asyncio.create_subprocess_exec(sys.executable, str(SCRIPTS / "tv_setup.py"), "plan", adb.target, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.STDOUT)
        out, _ = await asyncio.wait_for(p.communicate(), 90)
        try:
            plan = json.loads(out.decode(errors="replace").strip().splitlines()[-1])
        except Exception:
            raise AdbError(out.decode(errors="replace").strip()[-200:] or "could not read the TV")
        await adb.apps(True)                                          # labels the apps tab already knows
        for a in plan["suggest"] + plan["apps"]:
            a["label"] = (meta.cached(a["pkg"], adb.apk_paths.get(a["pkg"], "")) or {}).get("label") or a["label"] or ".".join(a["pkg"].split(".")[-2 if len(a["pkg"].split(".")[-1]) <= 3 else -1:])
        plan["apps"].sort(key=lambda a: a["label"].lower())
        return web.json_response(plan)

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

    STATS = "/sdcard/Android/data/dev.tvremote.setup/files/stats"

    @routes.get("/api/stats")
    @adb_errors
    async def stats(request):
        """What the TV Setup app on the TV has recorded (app/tv .../Stats.kt): time per app per day, and memory/CPU/storage samples."""
        days = max(1, min(90, int(request.query.get("days", "7") or 7)))
        since = time.time() - days * 86400
        try:
            usage = json.loads(await adb.shell(f"cat {STATS}/usage.json 2>/dev/null"))
        except ValueError:
            usage = None
        samples = []
        for line in (await adb.shell(f"tail -n {days * 300} {STATS}/samples.jsonl 2>/dev/null")).splitlines():
            try:
                row = json.loads(line)
                if row[0] >= since:
                    samples.append(row)
            except (ValueError, IndexError, TypeError):
                pass
        try:                                                          # every package's display name, system apps included
            known = await asyncio.get_running_loop().run_in_executor(None, meta._names)
        except Exception:
            known = {}
        names = {pkg: known[pkg] for per_day in (usage or {}).values() for pkg in per_day if known.get(pkg)}
        return web.json_response({"days": days, "collector": usage is not None or bool(samples), "usage": usage or {}, "samples": samples, "labels": names})

    @routes.post("/api/free")
    @adb_errors
    async def free(_):
        """Closes the apps that are only waiting in the background (what is on screen stays); says how much memory came back, in MB."""
        async def avail():
            m = re.search(r"MemAvailable:\s+(\d+)", await adb.shell("cat /proc/meminfo"))
            return int(m.group(1)) // 1024 if m else 0
        before = await avail()
        await adb.shell("am kill-all")
        await asyncio.sleep(1.5)
        after = await avail()
        return web.json_response({"before": before, "after": after, "freed": max(0, after - before)})

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
                name = re.sub(r"@[\d.]+-service.*$", "", re.sub(r"^(android|vendor\.\w+)\.hardware\.", "", m[3].split()[0].strip("[]")))   # system services: keep the part that says what it is
                top.append({"cpu": float(m[1]), "mem": float(m[2]), "name": name})
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

    # ---- automation: TV Setup's timers and limits (they run on the TV, see scenes.rules), and scenes ----
    @routes.get("/api/rules")
    @adb_errors
    async def rules_get(_):
        return web.json_response(await rules_with_names(None))

    @routes.post("/api/rules")
    @adb_errors
    async def rules_set(request):
        return web.json_response(await rules_with_names(await body(request)))

    async def rules_with_names(change):
        r = await scn.rules(adb, change)
        for l in r.get("limits", []):                                 # the names the Apps tab shows
            l["label"] = (meta.cached(l["pkg"], adb.apk_paths.get(l["pkg"], "")) or {}).get("label") or l.get("label") or l["pkg"]
        return r

    @routes.get("/api/scenes")
    async def scenes_get(request):
        if "download" in request.query:                               # "Export": a file to keep
            name = time.strftime("tv-remote-scenes-%Y-%m-%d.json")
            return web.Response(text=json.dumps(scn.backup(S.cfg.get("scenes", [])), ensure_ascii=False, indent=2), content_type="application/json",
                                headers={"Content-Disposition": f'attachment; filename="{name}"', "Cache-Control": "no-store"})
        return web.json_response({"scenes": S.cfg.get("scenes", [])})

    @routes.post("/api/scenes")
    async def scenes_set(request):
        S.cfg["scenes"] = scn.clean((await body(request)).get("scenes"))
        tvconfig.save(S.cfg)
        return web.json_response({"scenes": S.cfg["scenes"]})

    @routes.post("/api/scene/run")
    @adb_errors
    async def scene_run(request):
        d = await body(request)
        steps = d.get("steps") or next((x["steps"] for x in S.cfg.get("scenes", []) if x["id"] == d.get("id")), None)
        if steps is None:
            return web.json_response({"error": "no such scene"}, status=404)
        cleaned = scn.clean([{"name": "x", "steps": steps}])
        failed = await scn.run(adb, cleaned[0]["steps"] if cleaned else [])
        return web.json_response({"ok": not failed, "failed": failed or ""})

    # ---- 🎧 the TV's sound in this browser (see listen.js): Opus straight from scrcpy, or PCM made 24 kHz mono here ----
    @routes.get("/api/audio")
    async def audio(request):
        resp = web.StreamResponse(headers={"Content-Type": "application/octet-stream", "Cache-Control": "no-store"})
        await resp.prepare(request)
        opus = request.query.get("codec") == "opus"
        share = request.query.get("share") == "1"                     # the TV keeps playing too (playback capture, Android 13+)
        proc = port = writer = None
        try:
            if (await adb.status())["state"] != "ready":
                await resp.write(scn.packet(5, b"adb: not ready")); return resp
            sdk = int((await adb.shell("getprop ro.build.version.sdk")).strip() or 0)
            if sdk < 30:                                              # scrcpy captures sound from Android 11
                await resp.write(scn.packet(5, b"unsupported")); return resp
            if share and sdk < 33:                                    # too old to keep playing: the private way, and say so
                share = False
                await resp.write(scn.packet(3, b"noshare"))
            proc, port = await scrcpy_launch(S.cur.adb, "video=false control=false send_dummy_byte=true send_device_meta=false audio=true "
                                             + ("audio_codec=opus audio_bit_rate=64000" if opus else "audio_codec=raw")
                                             + (" audio_source=playback audio_dup=true" if share else ""))
            reader = None
            for _ in range(100):                                      # until the server listens, adb closes the socket at once
                reader, writer = await asyncio.open_connection("127.0.0.1", port)
                try:
                    first = await asyncio.wait_for(reader.readexactly(1), 4)
                except (asyncio.TimeoutError, asyncio.IncompleteReadError):
                    first = b""
                if first == b"\x00":
                    break
                writer.close(); writer = None
                if proc.returncode is not None:
                    break
                await asyncio.sleep(0.25)
            if not writer:
                raise AdbError("scrcpy-server did not start")
            if int.from_bytes(await reader.readexactly(4), "big") in (0, 1):   # the TV could not capture sound
                await resp.write(scn.packet(5, b"unsupported")); return resp
            await resp.write(scn.packet(0, bytes([1 if opus else 2]) + (48000 if opus else 24000).to_bytes(4, "big") + bytes([2 if opus else 1])))
            carry = b""
            while True:
                head = await reader.readexactly(12)
                flags, size = int.from_bytes(head[:8], "big"), int.from_bytes(head[8:], "big")
                data = await reader.readexactly(size)
                if flags & (1 << 62):                                 # the codec header (OpusHead)
                    if opus:
                        await resp.write(scn.packet(1, data))
                    continue
                if not opus:
                    data, carry = scn.downmix(data, carry)
                await resp.write(scn.packet(2, data))
        except (ConnectionError, asyncio.CancelledError, asyncio.IncompleteReadError):
            pass
        except AdbError as e:
            try:
                await resp.write(scn.packet(5, str(e).encode()))
            except ConnectionError:
                pass
        finally:
            if writer:
                writer.close()
            if proc and proc.returncode is None:
                proc.kill()                                           # the TV's speakers come back when the capture ends
            if port:
                await adb.run("forward", "--remove", f"tcp:{port}")
        return resp

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

    @routes.get("/api/tailscale")
    @adb_errors
    async def tailscale_state(request):
        return web.json_response({"installed": "package:" in await adb.shell("pm path " + TS_PKG + "; echo ."), "ip": await adb.tailscale_ip()})

    @routes.post("/api/tailscale/start")
    @adb_errors
    async def tailscale_start(request):
        await body(request)
        return web.json_response(adb.job_tailscale_start())

    # ---- turning ADB on with the remote buttons alone (port 6466), as the phone app does ----
    SETUP_APK = "https://github.com/hoangtran0410/tv-remote/releases/latest/download/tv-setup.apk"
    DOWNLOADER, SETUP_PKG = "com.esaba.downloader", "dev.tvremote.setup"
    SETUP_CODE = "downloader://2397346"                               # Downloader's own link for the AFTVnews code of SETUP_APK: opens Downloader alone, no "Open with"
    assist = {"status": "idle", "msg": "", "stop": False, "installed": False, "task": None}

    def front():
        return (tv.remote.current_app or "") if tv.available else ""

    async def until_front(want, seconds, stop=lambda: False):
        for _ in range(int(seconds * 5)):
            if stop():
                return False
            if want(front()):
                return True
            await asyncio.sleep(0.2)
        return False

    async def send_link(link):
        for _ in range(30):                                           # the TV may have dropped us after a link it disliked
            if tv.available:
                break
            await asyncio.sleep(0.2)
        tv.need().send_launch_app_command(link)

    async def press_ok():
        tv.need().send_key_command("DPAD_CENTER")

    async def open_on_tv(pkg, stop=lambda: False):
        """Bring an app to the front without ADB: ask for it, else open its Play Store page and press its main button."""
        if front() == pkg:
            return True
        await send_link(f"market://launch?id={pkg}")
        if await until_front(lambda a: a == pkg, 4, stop):
            return True
        await send_link(f"https://play.google.com/store/apps/details?id={pkg}")
        if not await until_front(lambda a: a == "com.android.vending", 10, stop):
            return False
        await asyncio.sleep(3)                                        # the page draws, with its main button in focus
        await press_ok()
        return await until_front(lambda a: a == pkg, 15, stop)

    async def answer_chooser(stop):
        for _ in range(3):                                            # "Open with": OK takes what is offered
            await asyncio.sleep(1.5)
            if front() != "android" or stop():
                break
            await press_ok()

    async def install_setup():
        """Get the TV Setup app onto the TV: hand the TV the download link; a TV without an app for links gets Downloader first."""
        stop = lambda: assist["stop"]
        say = lambda m: assist.update(msg=m)
        try:
            if not front():
                assist.update(status="blind", msg="TV này không cho biết đang mở gì nên không tự làm được. Làm theo cách “Tự làm” bên dưới.")
                return
            say("Đang gửi đường dẫn tải cho TV...")
            await send_link(SETUP_APK)
            await until_front(lambda a: a in (DOWNLOADER, "android"), 6, stop)
            if front() == "android":
                say("TV đang hỏi “Mở bằng”: đang chọn giúp...")
                await answer_chooser(stop); await asyncio.sleep(1.5)
            a = front()
            direct = not stop() and (a == DOWNLOADER or "chrome" in a or "browser" in a)
            there = direct or await open_on_tv(DOWNLOADER, stop)
            while not there and not stop():                          # its Play Store page was told to install it
                say("TV chưa có app Downloader: đang cài từ CH Play. Nếu TV không tự cài, chọn “Cài đặt” trên màn hình TV. Khi TV báo cài xong, bấm “Đã cài xong”.")
                for _ in range(600):
                    if stop() or assist["installed"] or front() == DOWNLOADER:
                        break
                    await asyncio.sleep(0.2)
                if stop():
                    break
                assist["checking"] = True                             # one check at a time: more presses of "Installed" change nothing
                say("Đang mở Downloader trên TV để kiểm tra...")
                there = front() == DOWNLOADER or await open_on_tv(DOWNLOADER, stop)
                assist.update(installed=False, checking=False)
            if stop():
                assist.update(status="idle", msg=""); return
            if not direct:
                say("Đang đưa đường dẫn tải cho Downloader...")
                await asyncio.sleep(3)
                await send_link(SETUP_CODE)
                await answer_chooser(stop); await asyncio.sleep(2.5)
            was = front()
            say("TV đang tải TV Setup...")
            await until_front(lambda x: x != was, 120, stop)          # until the TV's installer (or its permission screen) comes up
            assist.update(status="idle" if stop() else "look", msg="")
        except (web.HTTPException, ConnectionClosed, CannotConnect, OSError) as e:
            assist.update(status="failed", msg=f"Mất kết nối với TV giữa chừng, thử lại: {getattr(e, 'text', '') or e}")
        except asyncio.CancelledError:
            assist.update(status="idle", msg="")
        finally:
            assist["checking"] = False

    @routes.get("/api/assist")
    async def assist_state(_):
        return web.json_response({"status": assist["status"], "msg": assist["msg"], "front": front(), "connected": tv.available,
                                  "checking": bool(assist.get("checking") or assist["installed"])})

    @routes.post("/api/assist")
    async def assist_do(request):
        d = await body(request)
        do = str(d.get("do", ""))
        if do == "stop":
            assist["stop"] = True
            return web.json_response({"ok": True})
        if do == "installed":
            if not assist.get("checking"):
                assist["installed"] = True
            return web.json_response({"ok": True})
        tv.need()
        if do in ("open", "settings") and (assist["status"] == "running" or assist.get("opening")):   # one job on the TV at a time
            return web.json_response({"error": "TV đang làm việc vừa yêu cầu, chờ vài giây rồi bấm lại"}, status=409)
        if do == "setup":
            old = assist.get("task")
            if assist["status"] == "running" and assist["stop"] and old and not old.done():   # stopped a moment ago, still winding down
                old.cancel()
                try:
                    await old
                except (asyncio.CancelledError, Exception):
                    pass
            if assist["status"] != "running":
                assist.update(status="running", msg="", stop=False, installed=False, checking=False)
                assist["task"] = asyncio.create_task(install_setup())
            return web.json_response({"ok": True})
        if do == "open":                                              # an app by package name, or any link
            what = str(d.get("what", "")).strip()
            if not what:
                raise web.HTTPBadRequest(text="what required")
            if ":" in what:
                await send_link(what)
                return web.json_response({"ok": True, "front": front()})
            if not PKG_RE.match(what):
                raise web.HTTPBadRequest(text="not a package name")
            assist["opening"] = True
            try:
                return web.json_response({"ok": await open_on_tv(what), "front": front()})
            finally:
                assist["opening"] = False
        if do == "settings":                                          # every way a TV may open its Settings when asked
            is_settings = lambda a: "settings" in a.lower()
            tries = [lambda: tv.need().send_key_command("SETTINGS"), lambda: send_link("android-app://com.android.tv.settings"),
                     lambda: send_link("intent:#Intent;action=android.settings.SETTINGS;end")]
            ok = is_settings(front())
            for attempt in tries:
                if ok:
                    break
                try:
                    r = attempt()
                    if asyncio.iscoroutine(r):
                        await r
                except (web.HTTPException, ConnectionClosed, OSError, ValueError):
                    continue
                ok = await until_front(is_settings, 2.4)
            return web.json_response({"ok": ok, "blind": not front()})
        raise web.HTTPBadRequest(text="unknown action")

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
        released = ""                                                # the day of that version, from its heading in the changelog
        try:
            m = re.search(r"^## " + re.escape(version) + r" - (\d{4})-(\d{2})-(\d{2})", (ROOT / "CHANGELOG.md").read_text(), re.M)
            if m and version:
                released = f"{m.group(3)}/{m.group(2)}/{m.group(1)}"
        except OSError:
            pass
        tv_web = f"http://{tv.host}:8765" if tv.host and tv.host not in ("127.0.0.1", "localhost") and await port_open(tv.host, 8765) else ""   # the TV Setup app's page
        return web.json_response({"version": version, "released": released, "tv_web": tv_web})

    @routes.post("/api/shutdown")
    async def shutdown(request):
        await body(request)
        asyncio.get_running_loop().call_later(0.3, S.stop.set)        # after this reply has gone out
        return web.json_response({"ok": True})

    app_ = web.Application(middlewares=[guard])
    app_.add_routes(routes)
    for name in ("style.css", "stats.js", "manifest.json", "icon-180.png", "icon-192.png", "icon-512.png", "icon-maskable.png"):
        app_.router.add_get("/" + name, lambda _, f=WEB / name: web.FileResponse(f, headers={"Cache-Control": "no-store"}))
    app_.router.add_static("/js", WEB / "js")
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
