#!/usr/bin/env python3
"""dev/gen/app_links.py: remakes the VTVgo and TV360 entries of docs/home/links.json (the channels TV Home can pin, each opening
that app at that channel) and their logos in docs/home/logos/, then signs the list (dev/sign/sign_links.py).

The channel ids are the ones TV Channels already uses (docs/channels/providers.json: VTVgo's "ids", TV360's "channels"); names and
logos come from VTVgo's own channel list (the same call TV Channels makes), trimmed of their empty edges and kept as PNG at most
LOGO_W wide. A TV360 channel is listed when VTVgo has its logo (same name). The deep links were read from the apps (VTVgo 12.10,
TV360 6.4) and tried on the FPT box, 2026-10-27. Other apps in links.json are kept as they are. Raises "rev". Needs Pillow and a JDK.
"""
import io, json, os, re, subprocess, sys, urllib.request
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LIST = os.path.join(ROOT, "docs", "home", "links.json")
LOGOS = os.path.join(ROOT, "docs", "home", "logos")
PROVIDERS = os.path.join(ROOT, "docs", "channels", "providers.json")
LOGO_W = 320
VTVGO = {"pkg": "vn.vtv.vtvgotv", "name": "VTVgo", "uri": "vtvgo://play?mediaType=CHANNEL&contentId={id}"}
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
    im.save(os.path.join(LOGOS, name + ".png"), optimize=True)
    return name


def main():
    p = provider("vtvgo")
    strip = re.compile(p.get("strip") or "^$")
    data = {}
    ids = [i.strip() for i in p["ids"].split(",") if i.strip()]
    for k in range(0, len(ids), 20):
        data.update(json.loads(get(p["list"].replace("{ids}", ",".join(ids[k:k + 20])), p.get("headers")))["data"])
    vtvgo, logos = [], {}
    for i in ids:
        o = data.get(i)
        if not o or o.get("mustLogin") or not o.get("name"):
            continue
        name = strip.sub("", o["name"]).strip() or o["name"].strip()
        lg = logo(o["logo"], slug(name), p.get("headers")) if o.get("logo") else ""
        logos[name.lower()] = lg
        vtvgo.append({"id": i, "name": name, "logo": lg})
    order = [v["name"].lower() for v in vtvgo]                       # VTV first, as VTVgo lists them (TV360's map is by name)
    tv360 = sorted(({"id": cid, "name": n, "logo": logos[n.lower()]} for n, cid in provider("tv360")["channels"].items() if logos.get(n.lower())),
                   key=lambda x: order.index(x["name"].lower()))
    d = json.load(open(LIST, encoding="utf-8")) if os.path.exists(LIST) else {"v": 1, "rev": 0, "apps": []}
    mine = {VTVGO["pkg"]: dict(VTVGO, items=vtvgo), TV360["pkg"]: dict(TV360, items=tv360)}
    d["apps"] = [mine.pop(a["pkg"]) if a["pkg"] in mine else a for a in d["apps"]] + list(mine.values())
    d["rev"] += 1
    with open(LIST, "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("VTVgo %d, TV360 %d channels" % (len(vtvgo), len(tv360)))
    subprocess.run([sys.executable, os.path.join(ROOT, "dev", "sign", "sign_links.py")], check=True)


main()
