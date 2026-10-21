#!/usr/bin/env python3
"""The screenshots of the intro site (docs/img), taken again from today's UI in one go. See AGENTS.md "Testing".

    dev/shots.py                  every picture
    dev/shots.py web ts           only some: web (computer app's remote), ts (Tailscale box), tv (TV web page),
                                  phone (phone app's remote), tvapp (TV Setup's screen, + the og link previews)

Needs: python3 with Pillow, Google Chrome, cwebp, the computer app built (./gradlew :desktop:installDist) and, for tv / phone /
tvapp, the two emulators running with the apps installed (dev/emu.sh). The picture on the TV is a made-up home screen
(dev/shots/home-<lang>.html): no real TV, no real app's artwork, no one's address in the pictures.
Only the standard library besides Pillow: Chrome is driven through its DevTools protocol over a small WebSocket client below.
"""
import base64, functools, http.server, json, os, re, shutil, socket, struct, subprocess, sys, tempfile, threading, time
import urllib.request
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IMG = os.path.join(ROOT, "docs", "img")
CACHE = os.path.join(ROOT, ".cache", "shots")                     # its own TV list and keys: real TVs stay untouched
SDK = os.environ.get("ANDROID_HOME") or os.path.expanduser("~/Library/Android/sdk")
ADB = shutil.which("adb") or os.path.join(SDK, "platform-tools", "adb")
CHROME = os.environ.get("CHROME") or next((p for p in ["/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
          shutil.which("google-chrome") or "", shutil.which("chromium") or ""] if p and os.path.exists(p)), "")
def free_port():
    with socket.socket() as s: s.bind(("127.0.0.1", 0)); return s.getsockname()[1]
WEB_PORT, PIC_PORT, CDP_PORT, TV_FWD = free_port(), free_port(), free_port(), free_port()   # beside a running app or another run
PASS = "shots-" + os.urandom(8).hex()                                 # the computer app's key (Pass.kt), carried in each link
TV, PHONE = os.environ.get("TV_SERIAL", "emulator-5554"), os.environ.get("PHONE_SERIAL", "emulator-5556")
NAME = {"vi": "TV phòng khách", "en": "Living room TV"}
FONT = os.path.join(ROOT, "dev", "shots", "Roboto.ttf")            # Android's own face, for the words drawn over the emulators' names
TMP = tempfile.mkdtemp(prefix="tvr-shots-")


def log(*a): print("·", *a, flush=True)


# ---- Chrome over the DevTools protocol ----------------------------------------------------------------------------------

class Ws:
    """Just enough WebSocket for DevTools: masked text frames out, (fragmented) frames in."""
    def __init__(self, url):
        host, port, path = re.match(r"ws://([^:/]+):(\d+)(/.*)", url).groups()
        self.s = socket.create_connection((host, int(port)))
        key = base64.b64encode(os.urandom(16)).decode()
        self.s.sendall(f"GET {path} HTTP/1.1\r\nHost: {host}:{port}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                       f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n".encode())
        head = b""
        while b"\r\n\r\n" not in head: head += self.s.recv(1)
        if b" 101 " not in head.split(b"\r\n")[0]: raise IOError(head.decode(errors="replace"))

    def _read(self, n):
        b = b""
        while len(b) < n:
            c = self.s.recv(n - len(b))
            if not c: raise IOError("closed")
            b += c
        return b

    def send(self, text):
        data, mask = text.encode(), os.urandom(4)
        n = len(data)
        head = bytes([0x81]) + (bytes([0x80 | n]) if n < 126 else bytes([0x80 | 126]) + struct.pack(">H", n) if n < 65536
                                else bytes([0x80 | 127]) + struct.pack(">Q", n))
        self.s.sendall(head + mask + bytes(c ^ mask[i % 4] for i, c in enumerate(data)))

    def recv(self):
        out = b""
        while True:
            b0, b1 = self._read(2)
            n = b1 & 0x7F
            if n == 126: n = struct.unpack(">H", self._read(2))[0]
            elif n == 127: n = struct.unpack(">Q", self._read(8))[0]
            out += self._read(n)
            if b0 & 0x80: return out.decode()


class Chrome:
    def __init__(self, w, h, dpr=1, cookie=None, url=None):
        self.prof = tempfile.mkdtemp(prefix="cdp-", dir=TMP)
        self.p = subprocess.Popen([CHROME, "--headless=new", f"--remote-debugging-port={CDP_PORT}", f"--user-data-dir={self.prof}",
                                   f"--window-size={w},{h}", "--hide-scrollbars", "about:blank"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for _ in range(100):
            try:
                tabs = json.load(urllib.request.urlopen(f"http://127.0.0.1:{CDP_PORT}/json")); break
            except Exception: time.sleep(0.1)
        self.ws, self.n = Ws([t for t in tabs if t["type"] == "page"][0]["webSocketDebuggerUrl"]), 0
        self.call("Emulation.setDeviceMetricsOverride", width=w, height=h, deviceScaleFactor=dpr, mobile=w < 700)
        if cookie: self.call("Network.setCookie", name=cookie[0], value=cookie[1], url=url)

    def call(self, method, **params):
        self.n += 1
        self.ws.send(json.dumps({"id": self.n, "method": method, "params": params}))
        while True:
            r = json.loads(self.ws.recv())
            if r.get("id") == self.n:
                if "error" in r: raise RuntimeError(f"{method}: {r['error']}")
                return r.get("result", {})

    def open(self, url, wait=3):
        self.call("Page.navigate", url=url); time.sleep(wait)

    def js(self, code):
        r = self.call("Runtime.evaluate", expression=code, awaitPromise=True, returnByValue=True)
        if "exceptionDetails" in r: raise RuntimeError(r["exceptionDetails"].get("exception", {}).get("description", r["exceptionDetails"]))
        return r["result"].get("value")

    def shot(self, path):
        open(path, "wb").write(base64.b64decode(self.call("Page.captureScreenshot")["data"])); return Image.open(path)

    def close(self):
        self.p.kill(); self.p.wait()


def chrome_shot(url, js, w, h, dpr=1, cookie=None, wait=3):
    c = Chrome(w, h, dpr, cookie, url)
    try:
        c.open(url, wait)
        c.js("window.W = ms => new Promise(r => setTimeout(r, ms))")  # the scripts below wait with it
        got = c.js(js) if js else None
        time.sleep(0.5)
        return c.shot(os.path.join(TMP, f"s{time.time_ns()}.png")), got
    finally: c.close()


SMALL = {"tvapp": (640, 960), "phone-remote": (240, 360)}   # widths of the hero's srcset (docs/index.html)


def save_webp(img, name):
    png = os.path.join(TMP, name + ".png")
    img.convert("RGB").save(png)
    subprocess.run(["cwebp", "-quiet", "-q", "80", png, "-o", os.path.join(IMG, name + ".webp")], check=True)
    log("docs/img/" + name + ".webp", img.size)
    for w in SMALL.get(name.rsplit("-", 1)[0] if name.startswith("tvapp") else name, ()):   # the hero's srcset: -<w> copies, never upscaled
        small = img.resize((w, round(img.height * w / img.width)), Image.LANCZOS)
        small.convert("RGB").save(png)
        subprocess.run(["cwebp", "-quiet", "-q", "80", png, "-o", os.path.join(IMG, f"{name}-{w}.webp")], check=True)
        log(f"docs/img/{name}-{w}.webp", small.size)


# ---- the made-up TV picture --------------------------------------------------------------------------------------------

def serve_pictures():
    """home-<lang>.png on PIC_PORT, the picture the pages show as the TV's screen."""
    for lang in ("vi", "en"):
        img, _ = chrome_shot("file://" + os.path.join(ROOT, "dev", "shots", f"home-{lang}.html"), None, 1280, 720, wait=1.5)
        img.save(os.path.join(TMP, f"{lang}.png"))
    class Quiet(http.server.SimpleHTTPRequestHandler):
        def log_message(self, *a): pass
    h = functools.partial(Quiet, directory=TMP)
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", PIC_PORT), h)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    return srv


SHOW_PICTURE = """
  for (let i = 0; i < 60 && $('#screenbtn').disabled; i++) await W(250);
  const m = $('#screenmode'); m.value = '720'; m.dispatchEvent(new Event('change'));
  $('#screenbtn').click(); await W(5000);
  const img = $('#screen'), v = $('#screenv');
  img.src = 'http://127.0.0.1:%d/' + LANG + '.png'; await W(800);
  v.hidden = true; v.style.display = 'none'; img.hidden = false; img.style.display = 'block'; $('#screenph').hidden = true;
  $('#setupupd')?.setAttribute('hidden', '');
""" % PIC_PORT


# ---- the computer app ---------------------------------------------------------------------------------------------------

def desktop():
    """The computer app on its own port, TV list and keys, with the TV emulator (127.0.0.1:5555) as its one TV."""
    exe = os.path.join(ROOT, "app", "desktop", "build", "install", "desktop", "bin", "desktop")
    if not os.path.exists(exe): sys.exit("Build the computer app first: cd app/gradle && ./gradlew :desktop:installDist")
    os.makedirs(os.path.join(CACHE, "keys"), exist_ok=True)
    tvs = os.path.join(CACHE, "tvs.json")
    old = json.load(open(tvs)) if os.path.exists(tvs) else {}
    t = (old.get("tvs") or [{}])[0]
    t.update(host="127.0.0.1", name=NAME["vi"])                       # keeps setup_pass, so TV Setup is not asked again
    json.dump({"tvs": [t], "current": "127.0.0.1"}, open(tvs, "w"), ensure_ascii=False, indent=1)
    env = dict(os.environ, TVS_FILE=tvs, TVR_KEYS=os.path.join(CACHE, "keys"), TVR_PASS=PASS)
    p = subprocess.Popen([exe, "--port", str(WEB_PORT)], env=env, stdout=open(os.path.join(TMP, "desktop.log"), "w"), stderr=subprocess.STDOUT)
    for _ in range(100):
        try: urllib.request.urlopen(urllib.request.Request(f"http://127.0.0.1:{WEB_PORT}/api/about", headers={"x-tvr-pass": PASS})); return p
        except Exception: time.sleep(0.2)
    p.kill(); sys.exit("The computer app did not start:\n" + open(os.path.join(TMP, "desktop.log")).read()[-2000:])


def shot_web():
    """web-remote-<lang>: the computer app's remote with the picture on, 1200 wide, from the tab row down."""
    for lang in ("vi", "en"):
        js = """(async () => {
          wantPick = false; await W(3000); $('[data-tab=remote]')?.click(); await W(4000);
          %s
          await W(6000);                                              // the app logos come in
          [...document.querySelectorAll('#quick > *, .appgrid > *')].forEach(e => { if (/com\\./.test(e.textContent)) e.remove() });
          setChip('#chipTv', 'ok', tr('TV bật')); $('#info').textContent = tr('đã kết nối');
          scrollTo(0, 0);
          return (document.querySelector('[role=tablist]') || $('[data-tab]').parentElement).getBoundingClientRect().top;
        })()""" % SHOW_PICTURE
        img, top = chrome_shot(f"http://127.0.0.1:{WEB_PORT}/?lang={lang}&k={PASS}", js, 1200, 1000)
        save_webp(img.crop((0, int(top), 1200, int(top) + 852)), f"web-remote-{lang}")


def shot_ts():
    """tailscale-<lang>: the Tailscale box waiting for the sign-in, its QR code blurred (a made-up link, but a real-looking code)."""
    for lang in ("vi", "en"):
        js = """(async () => {
          await W(1500);
          const real = api;
          api = async (u, b, q) => u.startsWith('/api/tailscale/start') ? { status: 'done', url: 'https://login.tailscale.com/a/1f2e3d4c5b6a' }
            : u.startsWith('/api/tailscale') ? {} : real(u, b, q);
          tailscaleGo(); await W(1200);
          const box = e => { const r = e.getBoundingClientRect(); return [r.x, r.y, r.right, r.bottom] };
          return [box($('#ts .card') || $('#ts')), box($('#ts .tsqr svg'))];
        })()"""
        img, (card, qr) = chrome_shot(f"http://127.0.0.1:{WEB_PORT}/?lang={lang}&k={PASS}", js, 400, 900, dpr=2)
        q = tuple(int(v * 2) for v in qr)
        img.paste(img.crop(q).filter(ImageFilter.GaussianBlur(10)), q[:2])
        c = img.crop(tuple(int(v * 2) for v in card))
        save_webp(c.resize((600, round(c.height * 600 / c.width)), Image.LANCZOS), f"tailscale-{lang}")


# ---- the TV web page ----------------------------------------------------------------------------------------------------

def adb(serial, *a, text=True):
    return subprocess.run([ADB, "-s", serial, *a], capture_output=True, text=text, check=False).stdout


def screen_texts(serial):
    adb(serial, "shell", "uiautomator", "dump", "/sdcard/u.xml")
    return adb(serial, "shell", "cat", "/sdcard/u.xml")


def tv_cookie(base):
    """The TV web page's cookie, paired once with the code read off the TV emulator's screen and kept in .cache/shots."""
    path = os.path.join(CACHE, "tvcookie")
    if os.path.exists(path):
        tok = open(path).read().strip()
        try:
            urllib.request.urlopen(urllib.request.Request(base + "/api/state", headers={"Cookie": "tvr=" + tok})); return tok
        except Exception: pass
    post = lambda p, body: urllib.request.urlopen(urllib.request.Request(base + p, data=json.dumps(body).encode(), method="POST",
                                                                         headers={"Content-Type": "application/json"}))
    post("/api/pair/start", {})
    time.sleep(2)
    code = re.search(r'text="(\d{6})"', screen_texts(TV)) or re.search(r"\b(\d{6})\b", screen_texts(TV))
    if not code: sys.exit("No pairing code on the TV emulator's screen")
    r = post("/api/pair/finish", {"code": code.group(1)})
    tok = re.search(r"tvr=([^;]+)", r.headers["Set-Cookie"]).group(1)
    adb(TV, "shell", "input", "keyevent", "KEYCODE_BACK")             # the code's screen off the TV
    open(path, "w").write(tok)
    return tok


def shot_tv():
    """tvpage-remote-<lang>: the TV web page on a phone-sized browser (400 wide), from the TV emulator's TV Setup."""
    adb(TV, "forward", f"tcp:{TV_FWD}", "tcp:8765")
    base = f"http://127.0.0.1:{TV_FWD}"
    tok = tv_cookie(base)
    for lang in ("vi", "en"):
        js = """(async () => { %s $('#name').textContent = %s; scrollTo(0, 0); return 1 })()""" % (SHOW_PICTURE, json.dumps(NAME[lang]))
        img, _ = chrome_shot(f"{base}/?lang={lang}", js, 400, 880, dpr=2, cookie=("tvr", tok))
        c = img.crop((0, 0, 800, 1733))
        save_webp(c.resize((400, 867), Image.LANCZOS), f"tvpage-remote-{lang}")


# ---- the emulators' own screens -----------------------------------------------------------------------------------------

def screencap(serial):
    path = os.path.join(TMP, serial + ".png")
    open(path, "wb").write(adb(serial, "exec-out", "screencap", "-p", text=False))
    img = Image.open(path)
    if img.getextrema() in [((0, 0),) * 3, ((0, 0),) * 4]:          # some phone images give blank screencaps
        adb(serial, "emu", "screenrecord", "screenshot", path); img = Image.open(path)
    return img.convert("RGB")


def tap(serial, text):
    for n in re.findall(r"<node [^>]*>", screen_texts(serial)):
        g = lambda k: (re.search(k + r'="([^"]*)"', n) or [None, ""])[1]
        if text in g("text") + " " + g("content-desc"):
            a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n).groups())
            adb(serial, "shell", "input", "tap", str((a + c) // 2), str((b + d) // 2)); return True
    return False


def words(img, box, bg, xy, text, size, weight, color):
    """The emulator's model name over-written by a room's name, in the same face, size and colour."""
    d = ImageDraw.Draw(img)
    d.rectangle(box, fill=bg)
    f = ImageFont.truetype(FONT, size)
    try: f.set_variation_by_axes([weight, 100, 0])
    except Exception: pass
    d.text(xy, text, font=f, fill=color)


def shot_phone():
    """phone-remote: the phone app's remote on the phone emulator, the TV emulator's picture swapped for the made-up one."""
    adb(PHONE, "shell", "am", "start", "-n", "dev.tvremote.phone/.MainActivity"); time.sleep(3)
    if tap(PHONE, "10.0.2.2"): time.sleep(6)
    if tap(PHONE, "480p"): time.sleep(1); tap(PHONE, "720p"); time.sleep(5)
    img = screencap(PHONE)
    if img.size != (1080, 2340): sys.exit(f"The phone emulator's screen is {img.size}, the crop below wants 1080×2340")
    img.paste(Image.open(os.path.join(TMP, "vi.png")).convert("RGB").resize((1080, 606), Image.LANCZOS), (0, 308))
    words(img, (160, 168, 760, 229), (17, 20, 24), (167, 173), NAME["vi"], 41, 700, (232, 234, 237))
    save_webp(img.resize((480, 1040), Image.LANCZOS), "phone-remote")


def shot_tvapp():
    """tvapp-<lang>: TV Setup's screen on the TV emulator in each language, and from it the link previews og-<lang>.jpg."""
    app = "dev.tvremote.setup"
    try:
        for lang, loc in (("vi", "vi-VN"), ("en", "en-US")):
            adb(TV, "shell", "cmd", "locale", "set-app-locales", app, "--locales", loc)
            adb(TV, "shell", "am", "force-stop", app)
            adb(TV, "shell", "am", "start", "-n", app + "/.MainActivity"); time.sleep(4)
            img = screencap(TV)
            if img.size != (1920, 1080): sys.exit(f"The TV emulator's screen is {img.size}, the edit below wants 1920×1080")
            words(img, (158, 266, 790, 302), (25, 29, 35), (162, 267), NAME[lang] + ", Android 14", 26, 400, (140, 150, 163))
            img = img.resize((1280, 720), Image.LANCZOS)
            save_webp(img, f"tvapp-{lang}")
            img.resize((1200, 675), Image.LANCZOS).crop((0, 23, 1200, 653)).save(os.path.join(IMG, f"og-{lang}.jpg"), quality=85)
            log(f"docs/img/og-{lang}.jpg")
    finally:
        adb(TV, "shell", "cmd", "locale", "set-app-locales", app, "--locales", "")


# ---- all of it ----------------------------------------------------------------------------------------------------------

def main():
    want = sys.argv[1:] or ["web", "ts", "tv", "phone", "tvapp"]
    for w in want:
        if w not in ("web", "ts", "tv", "phone", "tvapp"): sys.exit(__doc__)
    if not CHROME: sys.exit("Google Chrome not found (set CHROME=…)")
    if not shutil.which("cwebp"): sys.exit("cwebp not found (brew install webp)")
    os.makedirs(CACHE, exist_ok=True)
    pics = srv = None
    try:
        pics = serve_pictures() if {"web", "tv", "phone"} & set(want) else None
        srv = desktop() if {"web", "ts"} & set(want) else None
        for w in want:
            log("…", w); globals()["shot_" + w]()
    finally:
        if srv: srv.kill()
        if pics: pics.shutdown()
        shutil.rmtree(TMP, ignore_errors=True)
    log("done: check the sizes in docs/index.html (width/height of each <img>)")


if __name__ == "__main__":
    main()
