#!/usr/bin/env python3
"""dev/sign/review_themes.py: review the community themes sent from TV Home (the queue is the worker in dev/themes).

Reads the admin token (~/.tv-remote-release/themes-admin-token, or $THEMES_TOKEN_FILE), fetches the pending themes, writes a local
HTML page (preview, name, sender, settings, red flags) in a temp folder and opens it; then asks per theme: [a]pprove / [r]eject / [s]kip.
Approve: id "c_<slug>" (a-z 0-9 _, at most 16: the ready-theme id rule; a "-" would be refused), a wall photo (if sent) re-encoded to
docs/home/community/<id>.jpg (<= 1920 px, JPEG q80, no metadata) and shown through loop.html like the "sapa" theme, the preview as
docs/img/themes/<id>.webp (480x270) + <id>-big.webp (1280x720), a 330 px docs/home/community/<id>-t.jpg as "thumb"; the theme is
appended to docs/home/themes.json (community: true, by), dev/sign/sign_themes.py signs it, the worker is told (done), and the git add
line is printed. Nothing is committed or pushed.

THEMES_URL overrides the worker's address (a local `wrangler dev`). Needs sips (macOS) and cwebp.
"""
import base64, html, json, os, re, shutil, subprocess, sys, tempfile, unicodedata, urllib.parse, urllib.request, webbrowser

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
URL = os.environ.get("THEMES_URL", "https://tvr-themes.99-hoangtran.workers.dev").rstrip("/")
TOKEN_FILE = os.environ.get("THEMES_TOKEN_FILE", os.path.expanduser("~/.tv-remote-release/themes-admin-token"))
SITE = "https://tvremote.pages.dev"
LIST = os.path.join(ROOT, "docs", "home", "themes.json")
WALL_DIR = os.path.join(ROOT, "docs", "home", "community")
IMG_DIR = os.path.join(ROOT, "docs", "img", "themes")
LOOK = "mist"   # the list's drawn stand-in for a photo wall (sign_themes.py wants one); the real picture is the thumb


def call(path, method="GET", token=""):
    r = urllib.request.Request(URL + path, method=method, headers={"Authorization": "Bearer " + token, "User-Agent": "tvr-review"})
    if method == "POST":
        r.data = b""
    with urllib.request.urlopen(r, timeout=30) as f:
        return f.read()


def slug(name):
    s = unicodedata.normalize("NFKD", name.replace("đ", "d").replace("Đ", "D")).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", "", s) or "theme"


def pick_id(name, taken):
    base = "c_" + slug(name)[:13]
    i, n = base, 2
    while i in taken:
        i = base[: 16 - len(str(n))] + str(n)
        n += 1
    return i


def no_urls(v):
    """conf without any http(s) string (keys too); a dropped value is removed, not blanked."""
    if isinstance(v, str):
        return None if re.search(r"https?://", v, re.I) else v
    if isinstance(v, list):
        return [x for x in (no_urls(i) for i in v) if x is not None]
    if isinstance(v, dict):
        out = {}
        for k, x in v.items():
            if re.search(r"https?://", k, re.I):
                continue
            c = no_urls(x)
            if c is not None:
                out[k] = c
        return out
    return v


def run(*a):
    subprocess.run(a, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def strip_jpeg(path):
    """Drop EXIF / XMP / comments (APP1..APP15, COM) from a JPEG: a phone photo carries its GPS position."""
    b = open(path, "rb").read()
    out, i = bytearray(b[:2]), 2
    while i + 4 <= len(b) and b[i] == 0xFF:
        m, n = b[i + 1], int.from_bytes(b[i + 2:i + 4], "big")
        if m == 0xDA:   # start of scan: the rest is the picture
            break
        if not (0xE1 <= m <= 0xEF or m == 0xFE):
            out += b[i:i + 2 + n]
        i += 2 + n
    open(path, "wb").write(bytes(out) + b[i:])


def page(items, tmp):
    rows = []
    for t in items:
        conf = "".join("<tr><td>%s</td><td>%s</td></tr>" % (html.escape(k), html.escape(json.dumps(v, ensure_ascii=False))) for k, v in t["conf"].items())
        fl = "".join('<p class="flag">%s</p>' % html.escape(f) for f in t["flags"])
        wall = '<p>+ own wall photo: <a href="%s.wall.jpg">%s.wall.jpg</a></p>' % (t["id"], t["id"]) if t.get("has_wall") else ""
        rows.append('<section><img src="%s.preview"><div><h2>%s <small>by %s &middot; %s &middot; %s</small></h2>%s%s<table>%s</table></div></section>'
                    % (t["id"], html.escape(t["name"]), html.escape(t["by"]), t["lang"], t["id"], fl, wall, conf))
    doc = ("<meta charset=utf-8><title>Community themes</title><style>body{font:14px system-ui;margin:20px;background:#111;color:#eee}"
           "section{display:flex;gap:16px;margin:0 0 24px;border-bottom:1px solid #333;padding-bottom:16px}img{width:480px;height:270px;object-fit:contain;background:#000}"
           "small{color:#999;font-weight:400}.flag{color:#ff6b6b;font-weight:600;margin:4px 0}td{padding:2px 10px 2px 0;vertical-align:top;font-family:ui-monospace,monospace}"
           "a{color:#6cf}</style><h1>%d pending</h1>%s" % (len(items), "".join(rows)))
    open(os.path.join(tmp, "index.html"), "w", encoding="utf-8").write(doc)


def approve(t, token, tmp):
    d = json.load(open(LIST, encoding="utf-8"))
    taken = {x["id"] for x in d["themes"]}
    if len(d["themes"]) >= 40:
        print("  ! the list already holds 40 themes (HomeConfig.READY_MAX): TV Home would ignore this one. Not approved.")
        return False
    i = pick_id(t["name"], taken)
    conf = no_urls(t["conf"])
    entry = {"id": i, "names": {"vi": t["name"], "en": t["name"]}, "for": {"vi": "", "en": ""}, "by": t["by"], "community": True, "conf": conf}
    prev = os.path.join(tmp, t["id"] + ".preview")
    os.makedirs(IMG_DIR, exist_ok=True)
    for suffix, size in (("", "480x270"), ("-big", "1280x720")):
        w, h = size.split("x")
        run("cwebp", "-q", "80", "-resize", w, h, "-metadata", "none", prev, "-o", os.path.join(IMG_DIR, i + suffix + ".webp"))
    wall_src = os.path.join(tmp, t["id"] + ".wall.jpg")
    if t.get("has_wall"):
        os.makedirs(WALL_DIR, exist_ok=True)
        out = os.path.join(WALL_DIR, i + ".jpg")
        wpx = int(re.search(r"pixelWidth: (\d+)", subprocess.run(["sips", "-g", "pixelWidth", wall_src], capture_output=True, text=True).stdout).group(1))
        resize = ["--resampleWidth", "1920"] if wpx > 1920 else []   # only shrink
        run("sips", "-s", "format", "jpeg", "-s", "formatOptions", "80", *resize, wall_src, "--out", out)
        thumb = os.path.join(WALL_DIR, i + "-t.jpg")
        run("sips", "-s", "format", "jpeg", "-s", "formatOptions", "70", "--resampleWidth", "330", out, "--out", thumb)
        for f in (out, thumb):
            strip_jpeg(f)
        photo = lambda a: isinstance(a, str) and re.fullmatch(r"photo\d*", a)
        walls = [x for x in conf.get("walls", []) if not photo(x)] if isinstance(conf.get("walls"), list) else None
        if walls:
            conf["walls"] = walls
        else:
            conf.pop("walls", None)
        conf["wall"] = "web"
        conf["wurl"] = SITE + "/home/loop.html?i=" + urllib.parse.quote(SITE + "/home/community/" + i + ".jpg", safe="")
        entry["look"] = LOOK
        entry["thumb"] = SITE + "/home/community/" + i + "-t.jpg"
        entry["src"] = "Community: " + t["by"]
    elif conf.get("wall") == "web":
        # a web wall without a photo of ours (a foreign page was dropped above only when it carried a URL in another key): not allowed
        conf.pop("wurl", None)
        conf["wall"] = "dawn"
        print("  ! its web wall was not ours: the wall is now 'dawn'")
    elif conf.get("wall") in ("bing",):
        entry["look"] = LOOK
    entry["conf"] = conf
    d["themes"].append(entry)
    with open(LIST, "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=1); f.write("\n")
    run(sys.executable, os.path.join(ROOT, "dev", "sign", "sign_themes.py"))
    print("  approved as %s" % i)
    return i


def main():
    token = open(TOKEN_FILE).read().strip()
    items = json.loads(call("/v1/admin/pending", token=token))
    if not items:
        print("nothing pending")
        return
    tmp = tempfile.mkdtemp(prefix="tvr-themes-")
    for t in items:
        open(os.path.join(tmp, t["id"] + ".preview"), "wb").write(call("/v1/admin/%s/preview" % t["id"], token=token))
        try:
            open(os.path.join(tmp, t["id"] + ".wall.jpg"), "wb").write(call("/v1/admin/%s/wall" % t["id"], token=token))
            t["has_wall"] = True
        except urllib.error.HTTPError as e:
            if e.code != 404:
                raise
    page(items, tmp)
    webbrowser.open("file://" + os.path.join(tmp, "index.html"))
    print("review page: %s/index.html" % tmp)
    added = []
    for t in items:
        print("\n%s  %r by %r (%s)%s" % (t["id"], t["name"], t["by"], t["lang"], "  + wall photo" if t.get("has_wall") else ""))
        for f in t["flags"]:
            print("  \033[31m! %s\033[0m" % f)
        while True:
            a = input("[a]pprove / [r]eject / [s]kip? ").strip().lower()[:1]
            if a in ("a", "r", "s"):
                break
        if a == "s":
            continue
        if a == "r":
            call("/v1/admin/%s/reject" % t["id"], "POST", token)
            print("  rejected")
            continue
        call("/v1/admin/%s/approve" % t["id"], "POST", token)
        i = approve(t, token, tmp)
        if i:
            call("/v1/admin/%s/done" % t["id"], "POST", token)
            added.append(i)
    shutil.rmtree(tmp, ignore_errors=True)
    if added:
        paths = ["docs/home/themes.json", "docs/home/themes.json.sig"] + ["docs/img/themes/%s%s.webp" % (i, s) for i in added for s in ("", "-big")]
        paths += [p for i in added for p in ("docs/home/community/%s.jpg" % i, "docs/home/community/%s-t.jpg" % i) if os.path.exists(os.path.join(ROOT, p))]
        print("\nNext (nothing committed): git add " + " ".join(paths))
        print("Also bump app/home/VERSION if the packed copy changed (HomeThemesTest keeps it equal).")


main()
