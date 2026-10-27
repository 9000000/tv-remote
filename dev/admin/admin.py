#!/usr/bin/env python3
"""dev/admin/admin.py: the owner's local admin page (themes waiting for review, crash reports) instead of terminal prompts.

  python3 dev/admin/admin.py [--port N] [--no-open] [--tailscale]

--tailscale: listens on this Mac's Tailscale IP only (100.x, reachable from the owner's own devices, never the LAN or the
Internet) and keeps its key in ~/.tv-remote-release/admin-key, so the link stays the same across restarts.

Serves on 127.0.0.1 only. A random key is made per run and sits in the link it opens (?k=, like the computer app's); every request
(page, API, pictures) must carry it and a local Host, so another page in the browser cannot drive it. The tokens are read from their
files here (~/.tv-remote-release/themes-admin-token, crash-token) and never reach the browser.
Themes: approve runs the same code as dev/sign/review_themes.py; nothing is committed. THEMES_URL / CENSUS_URL point at local
`wrangler dev` workers (tests); THEMES_TOKEN_FILE / CRASH_TOKEN as in the two scripts. Stdlib only.
"""
import argparse, json, os, secrets, sys, tempfile, threading, urllib.error, urllib.parse, urllib.request, webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "sign"))
import review_themes as rt   # noqa: E402
sys.path.insert(0, os.path.join(HERE, "..", "census"))
import crashes as cr         # noqa: E402

CENSUS = os.environ.get("CENSUS_URL", "https://tvr-census.99-hoangtran.workers.dev").rstrip("/")
CRASH_FILE = os.path.expanduser("~/.tv-remote-release/crash-token")
LOCK = threading.Lock()      # approving edits docs/ and the list: one at a time
KEY = secrets.token_urlsafe(24)
TMP = tempfile.mkdtemp(prefix="tvr-admin-")
PORT = 0
HOST = "127.0.0.1"
PAGE = os.path.join(HERE, "admin.html")


def crash_token():
    t = os.environ.get("CRASH_TOKEN", "").strip()
    return t or open(CRASH_FILE).read().strip()


def census(path, params=None, method="GET"):
    q = urllib.parse.urlencode({k: v for k, v in (params or {}).items() if v})
    r = urllib.request.Request(CENSUS + path + ("?" + q if q else ""), method=method,
                               headers={"Authorization": "Bearer " + crash_token(), "User-Agent": "tvr-admin"})
    with urllib.request.urlopen(r, timeout=20) as f:
        return json.load(f)


def crash_mark(sig, v, note):
    """crashes.py --mark from the page: one sig (every version, or [v]) looked at, at its count now; checked.json is not committed here."""
    rows = [r for r in census("/v1/crashes", {"sig": sig})["crashes"] if not v or r["v"] == v]
    marks = cr.checked()
    at = __import__("datetime").datetime.now().astimezone().isoformat(timespec="seconds")
    for r in rows:
        k = r["sig"] + "|" + r["v"]
        marks[k] = {"at": at, "n": r["n"], "note": note or (marks.get(k) or {}).get("note", "")}
    with open(cr.CHECKED, "w") as f:
        json.dump(dict(sorted(marks.items())), f, ensure_ascii=False, indent=1)
        f.write("\n")
    return {"marked": len(rows), "at": at}


def themes_list():
    token = rt.read_token()
    items = json.loads(rt.call("/v1/admin/pending", token=token))
    rt.fetch_assets(items, token, TMP)
    used = len(json.load(open(rt.LIST, encoding="utf-8"))["themes"])
    return {"items": items, "slots": {"max": rt.READY_MAX, "used": used}}


def theme_approve(tid):
    token = rt.read_token()
    items = [t for t in json.loads(rt.call("/v1/admin/pending", token=token)) if t["id"] == tid]
    if not items:
        return 404, {"error": "Không còn trong hàng chờ."}
    t = items[0]
    rt.fetch_assets([t], token, TMP)
    log = []
    rt.say = lambda *a: log.append(" ".join(str(x) for x in a).strip())
    rt.call("/v1/admin/%s/approve" % tid, "POST", token)
    i = rt.approve(t, token, TMP)
    if not i:
        return 409, {"error": "Không duyệt được.", "log": log}
    rt.call("/v1/admin/%s/done" % tid, "POST", token)
    paths = rt.git_paths([i])
    cmd = "git add " + " ".join(paths) + '\ngit commit -m "TV Home: community theme %s"' % t["name"].replace('"', "'")
    return 200, {"id": i, "files": paths, "cmd": cmd, "log": log, "note": "Nhớ tăng app/home/VERSION nếu bản đóng gói trong APK đổi."}


class H(BaseHTTPRequestHandler):
    server_version = "tvr-admin"

    def log_message(self, fmt, *a):
        if HOST != "127.0.0.1":                                         # --tailscale: who reached it (path without ?k=), for "it does not load"
            sys.stderr.write("%s %s %s\n" % (self.client_address[0], self.command, self.path.split("?")[0]))
            sys.stderr.flush()

    def send(self, code, body, ctype="application/json; charset=utf-8"):
        if not isinstance(body, bytes):
            body = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("content-type", ctype)
        self.send_header("content-length", str(len(body)))
        self.send_header("cache-control", "no-store")
        self.send_header("x-content-type-options", "nosniff")
        self.end_headers()
        self.wfile.write(body)

    def allowed(self, url):
        host_ok = self.headers.get("Host", "") in ("%s:%d" % (HOST, PORT), "localhost:%d" % PORT)
        k = (urllib.parse.parse_qs(url.query).get("k") or [self.headers.get("x-admin-key", "")])[0]
        return host_ok and secrets.compare_digest(k, KEY)

    def handle_any(self, method):
        url = urllib.parse.urlparse(self.path)
        if url.path == "/favicon.ico":
            return self.send(204, b"", "image/x-icon")
        if not self.allowed(url):
            return self.send(403, {"error": "forbidden"})
        q = {k: v[0] for k, v in urllib.parse.parse_qs(url.query).items()}
        p = url.path
        try:
            if method == "GET" and p == "/":
                return self.send(200, open(PAGE, "rb").read(), "text/html; charset=utf-8")
            if method == "GET" and p == "/api/themes":
                return self.send(200, themes_list())
            m = __import__("re").fullmatch(r"/api/themes/([0-9a-f]{8})/(preview|wall|wall/photo\d*|approve|reject)", p)
            if m:
                tid, what = m.groups()
                if what.startswith("wall/") and method == "GET":    # one photo of the rotation
                    f = os.path.join(TMP, "%s.wall.%s.jpg" % (tid, what[5:]))
                elif what in ("preview", "wall"):
                    f = os.path.join(TMP, tid + (".preview" if what == "preview" else ".wall.jpg"))
                if what in ("preview", "wall") or what.startswith("wall/"):
                    if method != "GET":
                        return self.send(405, {"error": "GET"})
                    if not os.path.exists(f):
                        return self.send(404, {"error": "none"})
                    b = open(f, "rb").read()
                    return self.send(200, b, "image/jpeg" if b[:2] == b"\xff\xd8" else "image/webp" if b[:4] == b"RIFF" else "image/png")
                if what == "approve" and method == "POST":
                    with LOCK:
                        code, body = theme_approve(tid)
                    return self.send(code, body)
                if what == "reject" and method == "POST":
                    rt.call("/v1/admin/%s/reject" % tid, "POST", rt.read_token())   # the API has no reason field: not stored
                    return self.send(200, {"ok": True})
            if p == "/api/crashes" and method == "GET":
                d = census("/v1/crashes", {"sig": q.get("sig", "")})
                marks = cr.checked()                                  # dev/census/checked.json: what was looked at, same as crashes.py --new
                for r in d.get("crashes", []):
                    r["seen"] = marks.get(r["sig"] + "|" + r["v"]); r["unseen"] = cr.unseen(r, marks)
                return self.send(200, d)
            if p == "/api/crashes/mark" and method == "POST" and q.get("sig"):
                with LOCK:
                    return self.send(200, crash_mark(q["sig"], q.get("v", ""), q.get("note", "")))
            if p == "/api/crashes" and method == "DELETE" and q.get("sig"):
                return self.send(200, census("/v1/crashes", {"sig": q["sig"]}, "DELETE"))
            self.send(404, {"error": "not found"})
        except urllib.error.HTTPError as e:
            self.send(502, {"error": "Máy chủ trả %d: %s" % (e.code, e.read().decode(errors="replace")[:200])})
        except FileNotFoundError as e:
            self.send(500, {"error": "Thiếu token: %s" % os.path.basename(e.filename or "?")})
        except Exception as e:   # shown on the page
            self.send(500, {"error": "%s: %s" % (type(e).__name__, e)})

    def do_GET(self): self.handle_any("GET")
    def do_POST(self): self.handle_any("POST")
    def do_DELETE(self): self.handle_any("DELETE")


def main():
    global PORT, HOST, KEY
    a = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    a.add_argument("--port", type=int, default=0)
    a.add_argument("--no-open", action="store_true")
    a.add_argument("--tailscale", action="store_true")
    o = a.parse_args()
    if o.tailscale:
        import subprocess
        HOST = subprocess.run(["tailscale", "ip", "-4"], capture_output=True, text=True, timeout=10).stdout.split()[0]
        if not HOST.startswith("100."):
            sys.exit("no Tailscale IPv4: " + HOST)
        kf = os.path.expanduser("~/.tv-remote-release/admin-key")
        if not os.path.exists(kf):
            with open(os.open(kf, os.O_WRONLY | os.O_CREAT, 0o600), "w") as f:
                f.write(secrets.token_urlsafe(24))
        KEY = open(kf).read().strip()
    srv = ThreadingHTTPServer((HOST, o.port), H)
    PORT = srv.server_address[1]
    link = "http://%s:%d/?k=%s" % (HOST, PORT, KEY)
    print("Admin: " + link + "\nCtrl+C để tắt.")
    if not o.no_open:
        webbrowser.open(link)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
