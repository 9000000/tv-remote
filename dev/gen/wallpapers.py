#!/usr/bin/env python3
"""dev/gen/wallpapers.py: makes TV Home's wallpaper gallery (docs/home/): 1920x1080 JPEGs (about 150-400 KB), 320x180 thumbnails,
and wallpapers.json (SHA-256 of both, credit, source, licence), then signs it like the launcher list (launchers.key, Ed25519).
Pictures: NASA images (public domain; ESA / CSA credits kept) fetched from images-api.nasa.gov, photos from Wikimedia Commons (PHOTOS below:
the author and licence are read from Commons itself; the ready-made themes of docs/home/themes.json show them through loop.html), and two renders made here (CC0).
Run again after changing WALLS; commit docs/home/ whole. Needs Pillow and a JDK."""
import hashlib, io, json, os, subprocess, urllib.request
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "docs", "home")
CACHE = os.path.join(ROOT, ".cache", "wallpapers")
KEY = os.environ.get("LAUNCHERS_KEY", os.path.expanduser("~/.tv-remote-release/launchers.key"))
# id, NASA id (None: a render), words, credit, crop (0 top .. 1 bottom of what is cut off)
WALLS = [
    ("cosmic-cliffs", "carina_nebula", {"vi": "Vách đá vũ trụ", "en": "Cosmic Cliffs"}, "NASA, ESA, CSA, STScI (James Webb)", .5),
    ("carina-hubble", "GSFC_20171208_Archive_e002076", {"vi": "Tinh vân Carina", "en": "Carina Nebula"}, "NASA, ESA (Hubble)", .5),
    ("lagoon", "GSFC_20171208_Archive_e001955", {"vi": "Tinh vân Đầm phá", "en": "Lagoon Nebula"}, "NASA, ESA (Hubble)", .5),
    ("aurora-green", "iss072e188141", {"vi": "Cực quang xanh", "en": "Green aurora"}, "NASA (International Space Station)", .5),
    ("aurora-red", "iss072e451060", {"vi": "Cực quang đỏ", "en": "Red aurora"}, "NASA (International Space Station)", .5),
    ("aurora-glow", "iss072e159172", {"vi": "Ánh cực quang", "en": "Aurora glow"}, "NASA (International Space Station)", .5),
    ("orbit-night", "iss072e709711", {"vi": "Đêm trên quỹ đạo", "en": "Night in orbit"}, "NASA (International Space Station)", .5),
    ("sunrise-orbit", "iss073e0982823", {"vi": "Bình minh từ quỹ đạo", "en": "Sunrise from orbit"}, "NASA (International Space Station)", .5),
    ("city-lights", "iss025e015176", {"vi": "Đèn thành phố", "en": "City lights"}, "NASA (International Space Station)", .5),
    ("snow-ranges", "iss074e0247458", {"vi": "Dãy núi tuyết", "en": "Snowy ranges"}, "NASA (International Space Station)", .5),
    ("soft-violet", None, {"vi": "Tím dịu", "en": "Soft violet"}, "TV Remote", [(0x1a0f3a, 0, 0), (0x6a3fa0, .75, .3), (0x2b6cb0, .2, .85), (0xd9577a, .95, .95)]),
    ("soft-teal", None, {"vi": "Xanh biển đêm", "en": "Deep teal"}, "TV Remote", [(0x04141f, 0, 0), (0x0f6e7a, .3, .35), (0x1d3f7a, .85, .8), (0x2fb39a, .05, .95)]),
]
# Photos of the ready-made themes: id, Commons file, words, (x, y) = where the 16:9 window sits in what is cut off (0 left/top .. 1 right/bottom)
PHOTOS = [
    ("terraces-sapa", "Sapa view 3.jpg", {"vi": "Ruộng bậc thang Sa Pa", "en": "Sa Pa rice terraces"}, (.5, .5)),
    ("beach-sunset", "DZ6 2673 Golden hour at the beach - palm-thatched huts gentle waves and a sunset that slows time.jpg", {"vi": "Hoàng hôn biển", "en": "Sunset beach"}, (.5, .45)),
    ("hk-night", "Hong Kong night skylines (Hong Kong Island) 20170807-1.jpg", {"vi": "Đêm Hồng Kông", "en": "Hong Kong night"}, (.5, .5)),
    ("pitch-night", "The Peninsula Stadium at night - Salford City - Aug 24.jpg", {"vi": "Sân cỏ đêm", "en": "Pitch at night"}, (.5, .5)),
    ("lanterns-red", "Chinese Lanterns at Night in Chinatown (2).jpg", {"vi": "Đèn lồng đỏ", "en": "Red lanterns"}, (.5, .5)),
    ("theatre-red", "Theatre Lionel Groulx.jpg", {"vi": "Rạp hát", "en": "Theatre"}, (.5, .5)),
    ("tulips-sunset", "Tulip field in Holland2.jpg", {"vi": "Cánh đồng tulip", "en": "Tulip field"}, (.5, .6)),
    ("milky-way", "Milky Way Lake Mountains (Unsplash).jpg", {"vi": "Dải Ngân Hà", "en": "Milky Way"}, (.5, .4)),
]
UA = {"User-Agent": "tv-remote-wallpapers/1.0 (https://github.com/HoangTran0410/tv-remote)"}


def get(u):
    import time
    for k in range(6):
        try: return urllib.request.urlopen(urllib.request.Request(u, headers=UA), timeout=120).read()
        except urllib.error.HTTPError as e:
            if e.code != 429: raise
            time.sleep(8 * (k + 1))
    raise SystemExit("429 from " + u)


def commons(name):
    """(picture, credit, page, licence) of a Commons file: 2560 px wide copy, author and licence from its own page."""
    import re, time, urllib.parse
    f = os.path.join(CACHE, "commons-" + re.sub(r"[^A-Za-z0-9.]+", "_", name))
    q = urllib.parse.urlencode({"action": "query", "format": "json", "titles": "File:" + name, "prop": "imageinfo", "iiprop": "extmetadata",
                                "iiextmetadatafilter": "LicenseShortName|Artist"})
    time.sleep(1.5)
    m = json.loads(get("https://commons.wikimedia.org/w/api.php?" + q))["query"]["pages"]
    m = next(iter(m.values()))["imageinfo"][0]["extmetadata"]
    lic = m["LicenseShortName"]["value"]
    author = re.sub(r"\s+", " ", re.sub("<[^>]+>", "", m.get("Artist", {}).get("value", ""))).strip()
    os.makedirs(CACHE, exist_ok=True)
    if not os.path.exists(f):
        open(f, "wb").write(get("https://commons.wikimedia.org/wiki/Special:FilePath/" + urllib.parse.quote(name) + "?width=2560"))
    return Image.open(f).convert("RGB"), author, "https://commons.wikimedia.org/wiki/File:" + urllib.parse.quote(name.replace(" ", "_")), lic


def fetch(nid):
    os.makedirs(CACHE, exist_ok=True)
    f = os.path.join(CACHE, nid + ".jpg")
    if not os.path.exists(f):
        def j(u): return json.load(urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": "tv-remote"}), timeout=30))
        jp = [x["href"] for x in j("https://images-api.nasa.gov/asset/" + nid)["collection"]["items"] if x["href"].lower().endswith((".jpg", ".jpeg"))]
        pick = next((u for u in jp if "~large" in u), None) or next(u for u in jp if "~orig" in u)
        open(f, "wb").write(urllib.request.urlopen(urllib.request.Request(pick.replace(" ", "%20"), headers={"User-Agent": "tv-remote"}), timeout=90).read())
    return Image.open(f).convert("RGB")


def render(blobs):
    w, h = 192, 108
    im = Image.new("RGB", (w, h), "#%06x" % blobs[0][0])
    for c, x, y in blobs[1:]:
        m = Image.new("L", (w, h), 0)
        r = int(h * .9)
        ImageDraw.Draw(m).ellipse((x * w - r, y * h - r, x * w + r, y * h + r), fill=255)
        im.paste(Image.new("RGB", (w, h), "#%06x" % c), mask=m.filter(ImageFilter.GaussianBlur(h * .45)))
    return Image.eval(im, lambda v: int(v * .5)).resize((1920, 1080), Image.BICUBIC)   # dark: the clock and the names stay readable


def fit(im, at, ax=.5):
    w, h = im.size
    t = w * 9 // 16
    if h > t:
        top = int((h - t) * at)
        im = im.crop((0, top, w, top + t))
    elif h < t:
        left = int((w - h * 16 // 9) * ax)
        im = im.crop((left, 0, left + h * 16 // 9, h))
    return im.resize((1920, 1080), Image.LANCZOS)


def save(im, path, **k):
    b = io.BytesIO()
    im.save(b, "JPEG", optimize=True, progressive=True, **k)
    open(path, "wb").write(b.getvalue())
    return hashlib.sha256(b.getvalue()).hexdigest(), len(b.getvalue())


def main():
    os.makedirs(OUT, exist_ok=True)
    items = []
    for i, nid, name, credit, a in WALLS:
        im = render(a) if nid is None else fit(fetch(nid), a)
        sha, n = save(im, os.path.join(OUT, i + ".jpg"), quality=82 if nid else 90)
        tsha, _ = save(im.resize((320, 180), Image.LANCZOS), os.path.join(OUT, i + "-t.jpg"), quality=72)
        e = {"id": i, "name": name, "file": i + ".jpg", "sha256": sha, "bytes": n, "thumb": i + "-t.jpg", "thumb_sha256": tsha, "credit": credit,
             "source": "https://images.nasa.gov/details/" + nid if nid else "https://github.com/HoangTran0410/tv-remote",
             "license": "Public domain (NASA)" if nid else "CC0"}
        items.append(e)
        print(i, n // 1024, "KB")
    for i, name, words, (ax, ay) in PHOTOS:
        im, author, page, lic = commons(name)
        im = fit(im, ay, ax)
        sha, n = save(im, os.path.join(OUT, i + ".jpg"), quality=84)
        tsha, _ = save(im.resize((320, 180), Image.LANCZOS), os.path.join(OUT, i + "-t.jpg"), quality=72)
        items.append({"id": i, "name": words, "file": i + ".jpg", "sha256": sha, "bytes": n, "thumb": i + "-t.jpg", "thumb_sha256": tsha,
                      "credit": author + " (Wikimedia Commons)", "source": page, "license": lic})
        print(i, n // 1024, "KB", author, lic)
    lst = os.path.join(OUT, "wallpapers.json")
    json.dump({"v": 1, "walls": items}, open(lst, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    open(lst, "a").write("\n")
    subprocess.run(["java", os.path.join(ROOT, "dev", "release", "Sign.java"), "sign", KEY, lst], check=True)
    print("signed", lst)


main()
