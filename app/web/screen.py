"""The TV's picture and input over ADB: screenshots, scrcpy (its control socket and video), the live MJPEG view."""
import asyncio, hashlib, random, re, struct, time
from adbtools import AdbError
from tvtools import ADB, FFMPEG, ROOT


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
