#!/usr/bin/env python3
"""Contrast scanner for the web UI, on real rendering. See AGENTS.md "Testing".

    dev/test/contrast.py [URL]        default http://127.0.0.1:8772/ (any core: the computer app's test server, a TV, ...)
    TVR_PASS=...                 the key the computer app was started with (Pass.kt); a TV's page needs its pairing instead

For both themes (?theme=light|dark) x widths 1100 and 400 x every tab + the settings dialog, it walks the visible elements
and reports text under 4.5:1 (3:1 for text >= 24px, or >= 18.66px bold) and line icons (svg.ic / .ui-ic) under 3:1, using
the computed colour and the background composited up the ancestors. Exit code 1 if any.
Skipped: hidden / zero-size elements, disabled controls (and [data-kind-off]), anything under an ancestor with opacity < 0.5,
canvas / video / img / iframe (the live TV picture, app artwork), and text whose background is an image or gradient
(reported as "unknown" only when it sits directly on one). Static token checks: app/shared/tests/contrast.test.js.
Needs only python3 and Chrome (the DevTools client is the small one from shots.py).
"""
import base64, json, os, re, shutil, socket, struct, subprocess, sys, tempfile, time, urllib.request

CHROME = os.environ.get("CHROME") or next((p for p in ["/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
          shutil.which("google-chrome") or "", shutil.which("chromium") or ""] if p and os.path.exists(p)), "")
TABS = ["remote", "apps", "files", "health", "auto", "tv"]

class Ws:
    def __init__(self, url):
        host, port, path = re.match(r"ws://([^:/]+):(\d+)(/.*)", url).groups()
        self.s = socket.create_connection((host, int(port)))
        key = base64.b64encode(os.urandom(16)).decode()
        self.s.sendall(f"GET {path} HTTP/1.1\r\nHost: {host}:{port}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                       f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n".encode())
        head = b""
        while b"\r\n\r\n" not in head: head += self.s.recv(1)
        self.n = 0
    def _read(self, n):
        b = b""
        while len(b) < n:
            c = self.s.recv(n - len(b))
            if not c: raise IOError("closed")
            b += c
        return b
    def _recv(self):
        out = b""
        while True:
            b0, b1 = self._read(2); n = b1 & 0x7F
            if n == 126: n = struct.unpack(">H", self._read(2))[0]
            elif n == 127: n = struct.unpack(">Q", self._read(8))[0]
            out += self._read(n)
            if b0 & 0x80: return out.decode()
    def call(self, method, **params):
        self.n += 1; data, mask = json.dumps({"id": self.n, "method": method, "params": params}).encode(), os.urandom(4); n = len(data)
        head = bytes([0x81]) + (bytes([0x80 | n]) if n < 126 else bytes([0x80 | 126]) + struct.pack(">H", n) if n < 65536 else bytes([0x80 | 127]) + struct.pack(">Q", n))
        self.s.sendall(head + mask + bytes(c ^ mask[i % 4] for i, c in enumerate(data)))
        while True:
            r = json.loads(self._recv())
            if r.get("id") == self.n: return r.get("result", r)
    def js(self, expr):
        r = self.call("Runtime.evaluate", expression=expr, awaitPromise=True, returnByValue=True)
        if "exceptionDetails" in r: raise RuntimeError(r["exceptionDetails"].get("exception", {}).get("description", "js error"))
        return r["result"].get("value")

SCAN = r"""(root) => {
  const P = (s) => { const m = s.match(/[\d.]+/g).map(Number); const k = s.startsWith('color(') ? 255 : 1; return [m[0] * k, m[1] * k, m[2] * k, m.length > 3 ? m[3] : 1]; };   // color-mix() computes to color(srgb 0..1)
  const over = (f, b) => [0, 1, 2].map((i) => f[i] * f[3] + b[i] * (1 - f[3])).concat(1);
  const L = (c) => { const x = c.slice(0, 3).map((v) => (v /= 255) <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4); return .2126 * x[0] + .7152 * x[1] + .0722 * x[2]; };
  const hex = (c) => '#' + c.slice(0, 3).map((v) => Math.round(v).toString(16).padStart(2, '0')).join('');
  const path = (e) => { const o = []; for (let i = 0; e && i < 3 && e.nodeType === 1 && e !== document.body; i++, e = e.parentElement) o.unshift(e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + (typeof e.className === 'string' && e.className.trim() ? '.' + e.className.trim().split(/\s+/).slice(0, 2).join('.') : '')); return o.join(' > '); };
  const bgOf = (e) => { let layers = [], unknown = false;
    for (let a = e; a && a.nodeType === 1; a = a.parentElement) { const s = getComputedStyle(a);
      if (s.backgroundImage !== 'none') { if (a === e) unknown = true; }
      const c = P(s.backgroundColor); if (c[3] > 0) { layers.push(c); if (c[3] === 1) break; } }
    let b = [255, 255, 255, 1]; if (!layers.length || layers[layers.length - 1][3] < 1) b = P(getComputedStyle(document.documentElement).backgroundColor); if (b[3] < 1) b = [255, 255, 255, 1];
    for (const l of layers.reverse()) b = over(l, b); return [b, unknown]; };
  const skip = (e) => { let op = 1; for (let a = e; a && a.nodeType === 1; a = a.parentElement) { const s = getComputedStyle(a);
      if (s.display === 'none' || s.visibility === 'hidden') return true; op *= +s.opacity; if (op < .5) return true;
      if (a.matches('canvas,video,img,iframe,[disabled],[aria-disabled=true],[data-kind-off]')) return true; } return false; };
  const out = [];
  const add = (e, kind, text, size, bold) => {
    const r = e.getBoundingClientRect(); if (r.width < 1 || r.height < 1 || skip(e)) return;
    const s = getComputedStyle(e); const [bg, unknown] = bgOf(e); const fg = over(P(s.color), bg);
    const a = L(fg), b = L(bg), ratio = (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    const need = kind === 'icon' ? 3 : (size >= 24 || (bold && size >= 18.66)) ? 3 : 4.5;
    if (unknown) { if (kind === 'text') out.push({ path: path(e), text, fg: hex(fg), bg: 'unknown', ratio: 0 }); return; }
    if (ratio < need) out.push({ path: path(e), text, fg: hex(fg), bg: hex(bg), ratio: Math.round(ratio * 100) / 100 });
  };
  const w = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT); const seen = new Set();
  for (let e = w.currentNode; e; e = w.nextNode()) {
    if (e.matches?.('svg.ic,.ui-ic,svg.ui-ic')) { add(e, 'icon', '(icon ' + (e.querySelector('use')?.getAttribute('href') || '') + ')', 0, false); continue; }
    if (e.closest?.('svg')) continue;
    const t = [...e.childNodes].filter((n) => n.nodeType === 3).map((n) => n.textContent.trim()).join(' ').trim();
    if (!t || ['SCRIPT', 'STYLE', 'OPTION'].includes(e.tagName) || seen.has(e)) continue;
    const s = getComputedStyle(e); add(e, 'text', t.slice(0, 40), parseFloat(s.fontSize), +s.fontWeight >= 700);
  }
  return out; }"""

def main():
    url = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8772/").rstrip("/") + "/"
    if not CHROME: sys.exit("Chrome not found (set CHROME=...)")
    key = os.environ.get("TVR_PASS", "")
    try: urllib.request.urlopen(urllib.request.Request(url, headers={"x-tvr-pass": key}), timeout=5).read(1)
    except Exception as e: sys.exit(f"{url} does not answer: {e}")
    with socket.socket() as s: s.bind(("127.0.0.1", 0)); port = s.getsockname()[1]
    prof = tempfile.mkdtemp(prefix="tvr-contrast-")
    p = subprocess.Popen([CHROME, "--headless=new", f"--remote-debugging-port={port}", f"--user-data-dir={prof}", "--disk-cache-size=1", "about:blank"],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    rows = []
    try:
        for _ in range(60):
            try: tabs = json.load(urllib.request.urlopen(f"http://127.0.0.1:{port}/json")); break
            except Exception: time.sleep(.2)
        ws = Ws(next(t for t in tabs if t["type"] == "page")["webSocketDebuggerUrl"])
        for theme in ("light", "dark"):
            for width in (1100, 400):
                ws.call("Emulation.setDeviceMetricsOverride", width=width, height=900, deviceScaleFactor=1, mobile=width < 700)
                ws.call("Page.navigate", url=f"{url}?theme={theme}" + (f"&k={key}" if key else "")); time.sleep(3)
                ws.js("wantPick=false; refresh()"); time.sleep(1)
                for tab in TABS + ["settings"]:
                    if tab == "settings": ws.js("document.getElementById('aboutbtn')?.click()"); root = "document.querySelector('dialog[open]') || document.body"
                    else: ws.js(f"showTab('{tab}'); window.scrollTo(0,0)"); root = "document.body"
                    time.sleep(1.5)
                    for r in ws.js(f"({SCAN})({root})") or []: rows.append((theme, width, tab, r))
                    if tab == "settings": ws.js("document.querySelector('dialog[open]')?.close()")
    finally: p.kill(); shutil.rmtree(prof, ignore_errors=True)
    seen, n = set(), 0
    print(f"{'theme':6} {'w':>5} {'tab':8} {'ratio':>5} {'fg':8} {'bg':8} path | text")
    for theme, width, tab, r in rows:
        k = (theme, width, tab, r["path"], r["text"])
        if k in seen: continue
        seen.add(k); n += 1
        print(f"{theme:6} {width:>5} {tab:8} {r['ratio']:>5} {r['fg']:8} {r['bg']:8} {r['path']} | {r['text']}")
    print(f"\n{n} item(s) under the limit" if n else "contrast: clean")
    sys.exit(1 if n else 0)

if __name__ == "__main__": main()
