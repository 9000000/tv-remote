#!/usr/bin/env python3
"""dev/gen/app_links.py: remakes the VTVgo, TV360 and FPT Play entries of docs/home/links.json (the channels TV Home can pin, each opening
that app at that channel) and their logos in docs/home/logos/, then signs the list (dev/sign/sign_links.py).

Every channel each app shows, not only the ones TV Channels plays: VTVgo's ids 1..MAX_ID asked of its channel API (the call TV
Channels makes, docs/channels/providers.json) and kept when the app shows them (a 2025 merged province stays in the API, hidden);
TV360's from the channel list its own web page carries (tv360.vn/tv/vtv1, __NEXT_DATA__), less its "live events" (gone in a day),
paid ones too (a user of the app may have the package; without it TV360 asks). FPT Play's from the API its web page calls
(fptplay.vn), signed as that page signs; twice in the list, one entry per package: the Android TV app on Google Play and the one
FPT's own boxes carry as their launcher (same code, same share links: fptplay.vn/xem-truyen-hinh/<id>). Logos are the apps' own, trimmed of their empty
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
FPT = [{"pkg": "net.fptplay.ottbox", "name": "FPT Play", "uri": FPT_URI}, {"pkg": "com.fptplay.launcher", "name": "FPT Play", "uri": FPT_URI}]
FPT_API = "https://api.fptplay.net/api/v7.1_w/"
FPT_KEY = "6ea6d2a4e2d3a4bd5e275401aa086d"                           # in fptplay.vn's own script: what its page signs with
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


def main():
    vtvgo, tv360, fpt = vtvgo_items(), tv360_items(), fpt_items()
    d = json.load(open(LIST, encoding="utf-8")) if os.path.exists(LIST) else {"v": 1, "rev": 0, "apps": []}
    mine = {VTVGO["pkg"]: dict(VTVGO, items=vtvgo), TV360["pkg"]: dict(TV360, items=tv360), **{a["pkg"]: dict(a, items=fpt) for a in FPT}}
    d["apps"] = [mine.pop(a["pkg"]) if a["pkg"] in mine else a for a in d["apps"]] + list(mine.values())
    d["rev"] += 1
    with open(LIST, "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=1)
        f.write("\n")
    used = {i.get("logo") for x in d["apps"] for i in x["items"]}
    for f in os.listdir(LOGOS):                                      # a channel gone takes its logo with it
        if f.endswith(".png") and f[:-4] not in used:
            os.remove(os.path.join(LOGOS, f))
    print("VTVgo %d, TV360 %d, FPT Play %d channels" % (len(vtvgo), len(tv360), len(fpt)))
    subprocess.run([sys.executable, os.path.join(ROOT, "dev", "sign", "sign_links.py")], check=True)


main()
