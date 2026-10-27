#!/usr/bin/env python3
"""dev/gen/app_links.py: remakes the VTVgo, TV360 and FPT Play entries of docs/home/links.json (the channels TV Home can pin, each opening
that app at that channel) and their logos in docs/home/logos/, then signs the list (dev/sign/sign_links.py).
`app_links.py youtube` remakes only the YouTube entry (a hand-picked list below, every handle read from its channel page on 2026-10-27;
pkg "*": no app named, whoever handles the https link opens it, apps/LinkApps.kt; avatars are round PNGs, "y-<handle>").

Every channel each app shows, not only the ones TV Channels plays: VTVgo's ids 1..MAX_ID asked of its channel API (the call TV
Channels makes, docs/channels/providers.json) and kept when the app shows them (a 2025 merged province stays in the API, hidden);
TV360's from the channel list its own web page carries (tv360.vn/tv/vtv1, __NEXT_DATA__), less its "live events" (gone in a day),
paid ones too (a user of the app may have the package; without it TV360 asks). FPT Play's from the API its web page calls
(fptplay.vn), signed as that page signs; twice in the list, one entry per package: the one FPT's own boxes carry as their launcher
(first: the box's account is signed in there, not in a copy of the Google Play app) and the Android TV app (same code, same share links: fptplay.vn/xem-truyen-hinh/<id>). Logos are the apps' own, trimmed of their empty
edges and kept as PNG at most LOGO_W wide (TV360's as "t-<slug>"). The deep links were read from the apps (VTVgo 12.10, TV360 6.4)
and tried on the FPT box, 2026-10-27 (FPT Play 7.34 both packages, the same day). Other apps in links.json are kept as they are. Raises "rev". Needs Pillow and a JDK.
"""
import base64, hashlib, io, json, os, re, subprocess, sys, time, urllib.request
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LIST = os.path.join(ROOT, "docs", "home", "links.json")
LOGOS = os.path.join(ROOT, "docs", "home", "logos")
PROVIDERS = os.path.join(ROOT, "docs", "channels", "providers.json")
LOGO_W = 320
MAX_ID = 600                                                         # VTVgo's ids reach 555; one call per 20 (more is a 400)
UA = {"User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"}
TV360_PAGE = "https://tv360.vn/tv/vtv1"
TV360_SKIP = {"Sự kiện trực tiếp"}
VTVGO = {"pkg": "vn.vtv.vtvgotv", "name": "VTVgo", "uri": "vtvgo://play?mediaType=CHANNEL&contentId={id}"}
FPT_URI = "https://fptplay.vn/xem-truyen-hinh/{id}"                    # the app's own share link, taken by both packages
FPT = [{"pkg": "com.fptplay.launcher", "name": "FPT Play", "uri": FPT_URI}, {"pkg": "net.fptplay.ottbox", "name": "FPT Play", "uri": FPT_URI}]   # the box's own first: signed in there
FPT_API = "https://api.fptplay.net/api/v7.1_w/"
FPT_KEY = "6ea6d2a4e2d3a4bd5e275401aa086d"                           # in fptplay.vn's own script: what its page signs with
YOUTUBE = {"pkg": "*", "name": "YouTube", "uri": "https://www.youtube.com/@{id}"}
# popular Vietnamese channels (and the kids' ones every family has): (handle, name); the handle was found by YouTube's own search and
# its page read (name and subscribers matched the real channel), ASCII only (an id is [A-Za-z0-9_.-]). Order: news, music, kids, cooking, football, fun.
YOUTUBE_PICKS = [
    ("vtv24", "VTV24"), ("vtvthoitietofficial", "VTV Thời tiết"), ("baotuoitre", "Báo Tuổi Trẻ"), ("thanhnientvnews", "Báo Thanh Niên"),
    ("VTCNewstintuc", "VTC News"), ("THVLGiaiTri", "THVL Giải trí"), ("THVLPhim", "THVL Phim"),
    ("Sontungmtp", "Sơn Tùng M-TP"), ("MTPEntertainment", "M-TP Entertainment"), ("casihaanhtuan", "Hà Anh Tuấn"), ("ERIKOfficial", "ERIK"),
    ("DenVau1305", "Đen Vâu"), ("hoaminzy3105", "Hòa Minzy"), ("HIEUTHUHAIOFFICIAL", "HIEUTHUHAI"), ("NhacHayVietNam", "Nhạc hay Việt Nam"),
    ("BabyBusVN", "BabyBus"), ("WolfooTiengViet", "Wolfoo"), ("nhacthieunhi68", "Nhạc thiếu nhi"), ("CoComelon", "CoComelon"),
    ("Pinkfong", "Baby Shark"), ("LittleBabyBum", "Little Baby Bum"),
    ("MonAnNgon", "Món ăn ngon"), ("AmThucMeLam", "Ẩm thực Mẹ Làm"),
    ("FPTBongDaViet", "FPT Bóng đá Việt"), ("VFFChannel6527", "VFF Channel"), ("KplusSportsOfficial", "K+ Sports"), ("vtvthethao_official", "VTV Thể thao"),
    ("htvthethaoofficial", "HTV Thể thao"), ("v.league.official", "V.League"),
    ("PewPew", "PewPew"), ("MixiGaming3con", "MixiGaming"), ("KhoaPug_solotrip", "Khoa Pug"),
]
TV360 = {"pkg": "com.viettel.tv360.tv", "name": "TV360", "uri": "tv360://recommendation?type=LIVE&id={id}"}


def provider(kind):
    found = []
    def walk(o):
        if isinstance(o, dict):
            if o.get("kind") == kind:
                found.append(o)
            for v in o.values():
                walk(v)
        elif isinstance(o, list):
            for v in o:
                walk(v)
    walk(json.load(open(PROVIDERS, encoding="utf-8")))
    return found[0]


def get(url, headers):
    return urllib.request.urlopen(urllib.request.Request(url, headers=headers or {"User-Agent": "Mozilla/5.0"}), timeout=20).read()


def slug(name):
    s = name.lower().replace("đ", "d")
    s = re.sub(r"[àáạảãâầấậẩẫăằắặẳẵ]", "a", s); s = re.sub(r"[èéẹẻẽêềếệểễ]", "e", s); s = re.sub(r"[ìíịỉĩ]", "i", s)
    s = re.sub(r"[òóọỏõôồốộổỗơờớợởỡ]", "o", s); s = re.sub(r"[ùúụủũưừứựửữ]", "u", s); s = re.sub(r"[ỳýỵỷỹ]", "y", s)
    return re.sub(r"[^a-z0-9]+", "-", s).strip("-")


def logo(url, name, headers):
    im = Image.open(io.BytesIO(get(url, headers))).convert("RGBA")
    box = im.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox()   # the older logos sit in a wide empty frame
    if box:
        im = im.crop(box)
    if im.width > LOGO_W:
        im = im.resize((LOGO_W, round(im.height * LOGO_W / im.width)), Image.LANCZOS)
    os.makedirs(LOGOS, exist_ok=True)
    im.quantize(256, method=Image.Quantize.FASTOCTREE).save(os.path.join(LOGOS, name + ".png"), optimize=True)   # 256 colours: 4.3 MB of logos to 1 MB, alike on a TV
    return name


def vtvgo_items():
    p = provider("vtvgo")
    strip = re.compile(p.get("strip") or "^$")
    data = {}
    for k in range(1, MAX_ID + 1, 20):
        try:
            data.update(json.loads(get(p["list"].replace("{ids}", ",".join(str(i) for i in range(k, k + 20))), p.get("headers")))["data"])
        except Exception:
            pass                                                     # a 404 when none of the 20 is a channel
    known = [i.strip() for i in p["ids"].split(",") if i.strip()]
    ids = known + sorted((i for i in data if i not in known), key=int)  # TV Channels' order (VTV first), then the rest
    items = []
    for i in ids:
        o = data.get(i)
        shown = ((o or {}).get("condition") or {}).get("visibility", {}).get("enabled", True)
        if not o or not o.get("enabled", True) or not shown or not o.get("name"):
            continue
        name = strip.sub("", o["name"]).strip() or o["name"].strip()
        items.append({"id": i, "name": name, "logo": logo(o["logo"], slug(name), p.get("headers")) if o.get("logo") else ""})
    return items


def tv360_items():
    page = get(TV360_PAGE, UA).decode("utf-8")
    cats = json.loads(re.search(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', page, re.S).group(1))["props"]["pageProps"]["detailCat"]
    items, seen = [], set()
    for cat in cats:                                                 # TV360's own groups and order: VTV, entertainment, sport, abroad, HTV, VTVcab, provinces
        if cat.get("name") in TV360_SKIP:
            continue
        for c in cat.get("content") or []:
            cid = str(c.get("id", ""))
            if c.get("type") != "LIVE" or not c.get("name") or cid in seen:
                continue
            seen.add(cid)
            lg = ""
            if c.get("coverImage"):
                try:
                    lg = logo(c["coverImage"], "t-" + (slug(c.get("slug") or c["name"]) or cid), UA)
                except Exception:
                    pass
            items.append({"id": cid, "name": c["name"].strip(), "logo": lg})
    return items


def fpt_items():
    path = "content/tv/channels"
    e = int(time.time()) + 3600
    st = base64.urlsafe_b64encode(hashlib.md5((FPT_KEY + str(e) + "/api/v7.1_w/" + path).encode()).digest()).decode().rstrip("=")
    h = dict(UA, Origin="https://fptplay.vn", Referer="https://fptplay.vn/")
    chans = json.loads(get("%s%s?st=%s&e=%d&device=Chrome&drm=1" % (FPT_API, path, st, e), h))["data"]["channels"]
    items = []
    for c in chans:                                                  # the app's own order: its sport and film chain, VTV, HTV, provinces, abroad
        cid = str(c.get("id", ""))
        if not re.fullmatch(r"[a-z0-9-]{1,40}", cid) or not c.get("name"):
            continue
        lg = ""
        if c.get("original_logo"):
            try:
                lg = logo(c["original_logo"], "f-" + cid, UA)
            except Exception:
                pass
        items.append({"id": cid, "name": c["name"].strip(), "logo": lg})
    return items


def youtube_items():
    items = []
    for handle, name in YOUTUBE_PICKS:
        try:
            page = get("https://www.youtube.com/@" + handle, UA)[:3_000_000].decode("utf-8", "ignore")
            img = re.search(r'property="og:image" content="([^"]*)"', page).group(1)
            title = re.search(r'property="og:title" content="([^"]*)"', page).group(1)
            im = Image.open(io.BytesIO(get(re.sub(r"=s\d+-", "=s256-", img), UA))).convert("RGBA")
        except Exception as e:
            print("! YouTube @%s skipped: %s" % (handle, e))
            continue
        side = min(im.size)
        im = im.crop(((im.width - side) // 2, (im.height - side) // 2, (im.width + side) // 2, (im.height + side) // 2)).resize((192, 192), Image.LANCZOS)
        mask = Image.new("L", (768, 768), 0)                         # drawn 4x then shrunk: a smooth edge
        from PIL import ImageDraw
        ImageDraw.Draw(mask).ellipse((0, 0, 767, 767), fill=255)
        im.putalpha(mask.resize((192, 192), Image.LANCZOS))
        lg = "y-" + slug(handle)
        os.makedirs(LOGOS, exist_ok=True)
        im.quantize(256, method=Image.Quantize.FASTOCTREE).save(os.path.join(LOGOS, lg + ".png"), optimize=True)   # 256 colours, as the other logos
        print("  @%s = %s (page: %s)" % (handle, name, title))
        items.append({"id": handle, "name": name, "logo": lg})
    return items


def main():
    only = sys.argv[1:] == ["youtube"]
    vtvgo, tv360, fpt = ([], [], []) if only else (vtvgo_items(), tv360_items(), fpt_items())
    d = json.load(open(LIST, encoding="utf-8")) if os.path.exists(LIST) else {"v": 1, "rev": 0, "apps": []}
    mine = {} if only else {VTVGO["pkg"]: dict(VTVGO, items=vtvgo), TV360["pkg"]: dict(TV360, items=tv360), **{a["pkg"]: dict(a, items=fpt) for a in FPT}}
    mine[YOUTUBE["pkg"]] = dict(YOUTUBE, items=youtube_items())
    d["apps"] = [mine.pop(a["pkg"]) if a["pkg"] in mine else a for a in d["apps"]] + list(mine.values())
    d["rev"] += 1
    with open(LIST, "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=1)
        f.write("\n")
    used = {i.get("logo") for x in d["apps"] for i in x["items"]}
    for f in os.listdir(LOGOS):                                      # a channel gone takes its logo with it
        if f.endswith(".png") and f[:-4] not in used:
            os.remove(os.path.join(LOGOS, f))
    print("VTVgo %d, TV360 %d, FPT Play %d channels (0: not remade)" % (len(vtvgo), len(tv360), len(fpt)))
    subprocess.run([sys.executable, os.path.join(ROOT, "dev", "sign", "sign_links.py")], check=True)


main()
