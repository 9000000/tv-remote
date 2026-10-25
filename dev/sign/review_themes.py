#!/usr/bin/env python3
"""dev/sign/review_themes.py: review the community themes sent from TV Home (the queue is the worker in dev/themes).

Reads the admin token (~/.tv-remote-release/themes-admin-token, or $THEMES_TOKEN_FILE), fetches the pending themes, writes a local
HTML page (preview, name, sender, settings, red flags) in a temp folder and opens it; then asks per theme: [a]pprove / [r]eject / [s]kip.
Approve: id "c_<slug>" (a-z 0-9 _, at most 16: the ready-theme id rule; a "-" would be refused), a wall photo (if sent) re-encoded to
docs/home/community/<id>.jpg (<= 1920 px, JPEG q80, no metadata) and shown through loop.html like the "sapa" theme, the preview as
docs/img/themes/<id>.webp (480x270) + <id>-big.webp (1280x720), a 330 px docs/home/community/<id>-t.jpg as "thumb"; the theme is
appended to docs/home/themes.json (community: true, by), dev/sign/sign_themes.py signs it, the worker is told (done), and the git add
line is printed. Nothing is committed or pushed.

Agents: `--list --json` (pending + flags; pictures saved to a temp dir, paths printed), `--approve ID`, `--reject ID`, and
`--local DIR` (a pull-request submission folder, see THEMES.md: same validation as the worker, same approve path, then the folder is
removed; with `--dry` nothing is removed or told). Rules for agents: dev/themes/REVIEW.md.

THEMES_URL overrides the worker's address (a local `wrangler dev`). Needs sips (macOS) and cwebp.
"""
import argparse, base64, html, json, os, re, shutil, subprocess, sys, tempfile, unicodedata, urllib.parse, urllib.request, webbrowser

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
URL = os.environ.get("THEMES_URL", "https://tvr-themes.99-hoangtran.workers.dev").rstrip("/")
TOKEN_FILE = os.environ.get("THEMES_TOKEN_FILE", os.path.expanduser("~/.tv-remote-release/themes-admin-token"))
SITE = "https://tvremote.pages.dev"
LIST = os.path.join(ROOT, "docs", "home", "themes.json")
WALL_DIR = os.path.join(ROOT, "docs", "home", "community")
IMG_DIR = os.path.join(ROOT, "docs", "img", "themes")
READY_MAX = 120   # HomeConfig.READY_MAX: TV Home reads at most this many ready themes
LOOK = "mist"   # the list's drawn stand-in for a photo wall (sign_themes.py wants one); the real picture is the thumb


# The worker's rules (dev/themes/src/logic.js), ported for --local: keep the same.
THEME_KEYS = ["wall", "walls", "wurl", "wmode", "wsnap", "wkeep", "wevery", "shade", "vsound", "vvol", "widgets", "wsize", "wstyle", "wopt",
              "cols", "rows", "focus", "motion", "anim", "clock24", "greet"]
MAX_PREVIEW, MAX_WALL, MAX_CONF, ALLOWED_HOST = 150_000, 1_000_000, 8192, "tvremote.pages.dev"


def clean(s, n=24):
    if not isinstance(s, str):
        return ""
    s = "".join(" " if unicodedata.category(c) in ("Cc", "Cf", "Zl", "Zp") else c for c in s)
    return re.sub(r"\s+", " ", s).strip()[:n].strip()


def is_jpeg(b):
    return len(b) > 4 and b[:3] == b"\xff\xd8\xff"


def is_webp(b):
    return len(b) > 12 and b[:4] == b"RIFF" and b[8:12] == b"WEBP"


def plain(v, depth=0):
    if depth > 5:
        return False
    if v is None or isinstance(v, (bool, int, float)):
        return True
    if isinstance(v, str):
        return len(v) <= 600
    if isinstance(v, list):
        return len(v) <= 60 and all(plain(x, depth + 1) for x in v)
    if isinstance(v, dict):
        return len(v) <= 60 and all(len(k) <= 60 and plain(x, depth + 1) for k, x in v.items())
    return False


def own_site(v):
    s = urllib.parse.unquote(v)
    al = re.findall(r"(https?)://([^/?#\s\"']+)", s, re.I)
    return bool(al) and all(a.lower() == "https" and h.lower() == ALLOWED_HOST for a, h in al)


def flags_of(name, by, conf):
    out = []
    if conf.get("wall") == "web":
        u = urllib.parse.urlparse(str(conf.get("wurl")))
        if not (u.scheme == "https" and u.hostname == ALLOWED_HOST):
            out.append("web wall: wurl is not on %s (%s)" % (ALLOWED_HOST, str(conf.get("wurl"))[:80]))
    urls = []

    def walk(v):
        if isinstance(v, str):
            if re.search(r"https?://", v, re.I) and not own_site(v):
                urls.append(v)
        elif isinstance(v, list):
            for x in v:
                walk(x)
        elif isinstance(v, dict):
            for k, x in v.items():
                walk(k)
                walk(x)
    walk(conf)
    if urls:
        out.append("URL in conf: " + " | ".join(u[:80] for u in urls[:3]))
    if len(name) >= 20 or len(by) >= 20:
        out.append("very long name or sender")
    return out


def validate_folder(d):
    """A submission folder -> (item, None) or (None, error). item looks like a pending one from the worker (+ "dir")."""
    try:
        o = json.load(open(os.path.join(d, "theme.tvtheme.json"), encoding="utf-8"))
    except (OSError, ValueError):
        return None, "theme.tvtheme.json missing or not JSON"
    if not isinstance(o, dict) or o.get("tvtheme") != 1:
        return None, "tvtheme must be 1"
    name, by = clean(o.get("name")), clean(o.get("by"))
    if not name:
        return None, "name: 1-24 characters"
    if not by:
        return None, 'by: 1-24 characters (add "by" to the json)'
    lang = o.get("lang", "en")
    if lang not in ("vi", "en"):
        return None, "lang must be vi or en"
    conf = o.get("conf")
    if not isinstance(conf, dict):
        return None, "conf must be an object"
    extra = [k for k in conf if k not in THEME_KEYS]
    if extra:
        return None, "not theme settings: " + ", ".join(extra[:5])
    if not plain(conf):
        return None, "conf: plain JSON only"
    if len(json.dumps(conf, separators=(",", ":"), ensure_ascii=False)) > MAX_CONF:
        return None, "conf too big (8 KB)"
    try:
        prev = open(os.path.join(d, "preview.jpg"), "rb").read()
    except OSError:
        return None, "preview.jpg missing"
    if len(prev) > MAX_PREVIEW:
        return None, "preview over 150 KB"
    if not (is_jpeg(prev) or is_webp(prev)):
        return None, "preview is not a jpeg/webp"
    wall = os.path.join(d, "wall.jpg")
    if os.path.exists(wall):
        w = open(wall, "rb").read()
        if len(w) > MAX_WALL:
            return None, "wall over 1 MB"
        if not is_jpeg(w):
            return None, "wall is not a jpeg"
    sid = re.sub(r"[^a-z0-9]+", "", os.path.basename(os.path.abspath(d)).lower())[:16] or "local"
    return {"id": sid, "name": name, "by": by, "lang": lang, "conf": conf, "flags": flags_of(name, by, conf), "has_wall": os.path.exists(wall),
            "dir": os.path.abspath(d)}, None


def say(*a):   # where approve() reports; dev/admin.py swaps it to collect the lines for its page
    print(*a)


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
    if len(d["themes"]) >= READY_MAX:
        say("  ! the list already holds %d themes (HomeConfig.READY_MAX): TV Home would ignore this one. Not approved." % READY_MAX)
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
        say("  ! its web wall was not ours: the wall is now 'dawn'")
    elif conf.get("wall") in ("bing",):
        entry["look"] = LOOK
    entry["conf"] = conf
    d["themes"].append(entry)
    with open(LIST, "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=1); f.write("\n")
    run(sys.executable, os.path.join(ROOT, "dev", "sign", "sign_themes.py"))
    say("  approved as %s" % i)
    return i


def fetch_assets(items, token, tmp):
    """Preview (and wall photo, when sent) of each pending theme into tmp; sets t["has_wall"]."""
    for t in items:
        open(os.path.join(tmp, t["id"] + ".preview"), "wb").write(call("/v1/admin/%s/preview" % t["id"], token=token))
        t["has_wall"] = False
        try:
            open(os.path.join(tmp, t["id"] + ".wall.jpg"), "wb").write(call("/v1/admin/%s/wall" % t["id"], token=token))
            t["has_wall"] = True
        except urllib.error.HTTPError as e:
            if e.code != 404:
                raise


def git_paths(added):
    """Files an approval wrote (relative to the repo), for git add."""
    paths = ["docs/home/themes.json", "docs/home/themes.json.sig"] + ["docs/img/themes/%s%s.webp" % (i, s) for i in added for s in ("", "-big")]
    return paths + [p for i in added for p in ("docs/home/community/%s.jpg" % i, "docs/home/community/%s-t.jpg" % i) if os.path.exists(os.path.join(ROOT, p))]


def read_token():
    return open(TOKEN_FILE).read().strip()


def pending_items(token):
    items = json.loads(call("/v1/admin/pending", token=token))
    tmp = tempfile.mkdtemp(prefix="tvr-themes-")
    fetch_assets(items, token, tmp)
    return items, tmp


def list_json(token):
    """Agent view: pending themes + flags; the pictures are files an agent can open."""
    items, tmp = pending_items(token)
    out = []
    for t in items:
        t["preview_path"] = os.path.join(tmp, t["id"] + ".preview")
        t["wall_path"] = os.path.join(tmp, t["id"] + ".wall.jpg") if t.get("has_wall") else None
        t["free_slots"] = READY_MAX - len(json.load(open(LIST, encoding="utf-8"))["themes"])
        out.append(t)
    print(json.dumps(out, ensure_ascii=False, indent=1))


def decide(token, tid, ok):
    """--approve / --reject for one pending id, no prompts."""
    items, tmp = pending_items(token)
    t = next((x for x in items if x["id"] == tid), None)
    if not t:
        sys.exit("no pending theme %r" % tid)
    if not ok:
        call("/v1/admin/%s/reject" % tid, "POST", token)
        print("rejected", tid)
        return
    i = approve(t, token, tmp)
    if not i:
        sys.exit("not approved")
    call("/v1/admin/%s/approve" % tid, "POST", token)
    call("/v1/admin/%s/done" % tid, "POST", token)
    print("approved %s as %s\nNext (nothing committed): git add %s" % (tid, i, " ".join(git_paths([i]))))


def local(d, dry=False):
    t, err = validate_folder(d)
    if err:
        sys.exit("refused: " + err)
    for f in t["flags"]:
        print("! " + f)
    if t["flags"]:
        sys.exit("refused: flags need a person (a foreign URL in conf is never approved)")
    tmp = tempfile.mkdtemp(prefix="tvr-themes-")
    shutil.copy(os.path.join(d, "preview.jpg"), os.path.join(tmp, t["id"] + ".preview"))
    if t["has_wall"]:
        shutil.copy(os.path.join(d, "wall.jpg"), os.path.join(tmp, t["id"] + ".wall.jpg"))
    i = approve(t, "", tmp)
    shutil.rmtree(tmp, ignore_errors=True)
    if not i:
        sys.exit("not approved")
    if not dry:
        shutil.rmtree(d)
    print("approved %s as %s%s\nNext (nothing committed): git add %s%s" % (t["id"], i, " (dry: folder kept)" if dry else "", " ".join(git_paths([i])),
                                                                         "" if dry else " && git rm -r " + os.path.relpath(d, ROOT)))
    return i


def main():
    a = argparse.ArgumentParser(description="Review community themes (see the module doc).")
    a.add_argument("--list", action="store_true")
    a.add_argument("--json", action="store_true")
    a.add_argument("--approve", metavar="ID")
    a.add_argument("--reject", metavar="ID")
    a.add_argument("--local", metavar="DIR")
    a.add_argument("--dry", action="store_true")
    o = a.parse_args()
    if o.local:
        return local(o.local, o.dry)
    token = read_token()
    if o.list:
        return list_json(token) if o.json else sys.exit("--list needs --json")
    if o.approve or o.reject:
        return decide(token, o.approve or o.reject, bool(o.approve))
    items = json.loads(call("/v1/admin/pending", token=token))
    if not items:
        print("nothing pending")
        return
    tmp = tempfile.mkdtemp(prefix="tvr-themes-")
    fetch_assets(items, token, tmp)
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
        paths = git_paths(added)
        print("\nNext (nothing committed): git add " + " ".join(paths))
        print("Also bump app/home/VERSION if the packed copy changed (HomeThemesTest keeps it equal).")


if __name__ == "__main__":
    main()
