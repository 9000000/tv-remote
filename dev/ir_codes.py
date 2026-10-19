#!/usr/bin/env python3
"""Builds app/android/src/main/assets/ir/codes.json (the Phone app's infrared remote) from Flipper-IRDB.

  git clone --depth 1 https://github.com/Lucaslhm/Flipper-IRDB.git /some/empty/dir/irdb
  python3 -I dev/ir_codes.py /some/empty/dir/irdb

Flipper-IRDB is CC0-1.0 (public domain); credit kept in the file's "src" and app/android/src/main/assets/ir/README.md.
Its files are untrusted data: read as text, only `type: parsed` signals of the protocols Ir.kt can encode are kept, every
number is range-checked, nothing is executed. Run with -I so a planted module beside the data cannot be imported.

A profile is one remote: its keys by our names (Scenes.KEYS), each "<protocol> <address> <command>" in hex.
Profiles of a brand are picked for coverage and for being different (not the same codes under another model name).
"""
import json, os, re, subprocess, sys

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "android", "src", "main", "assets", "ir", "codes.json")
# (folder in the clone, brand shown, most profiles kept). Brands common in Vietnam first; boxes after.
BRANDS = [
    ("TVs/Samsung", "Samsung", 6), ("TVs/LG", "LG", 6), ("TVs/Sony", "Sony", 5), ("TVs/TCL", "TCL", 4),
    ("TVs/Panasonic", "Panasonic", 3), ("TVs/Sharp", "Sharp", 3), ("TVs/Toshiba", "Toshiba", 3), ("TVs/Philips", "Philips", 4),
    ("TVs/Hisense", "Hisense", 4), ("TVs/Xiaomi", "Xiaomi TV", 2), ("TVs/Haier", "Haier", 2), ("TVs/Sanyo", "Sanyo", 2),
    ("TVs/JVC", "JVC", 2), ("TVs/Hitachi", "Hitachi", 2), ("TVs/Daewoo", "Daewoo", 2), ("TVs/Funai", "Funai", 2),
    ("TVs/Android", "Android TV box", 3), ("Streaming_Devices/Xiaomi", "Mi Box", 3), ("Streaming_Devices/Mecool", "Mecool", 2),
    ("Streaming_Devices/onn", "onn", 2), ("Streaming_Devices/Formuler", "Formuler", 2), ("Streaming_Devices/Amazon", "Fire TV", 2),
    ("Streaming_Devices/Bqeel", "Bqeel", 2),
]
KEEP = {"NEC", "NECext", "Samsung32", "SIRC", "SIRC15", "SIRC20", "RC5", "RC5X", "RC6", "RCA"}
RAW_DIRS = ("TVs/Xiaomi", "Streaming_Devices/Xiaomi")   # Xiaomi's own multi-level code is no protocol Ir.kt knows: its first frames, as recorded
RAW_ALIAS = {"open": "POWER", "more": "MENU", "volmore": "VOL_UP", "volless": "VOL_DOWN", "homee": "HOME"}
DIGITS = {"zero": 0, "one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6, "seven": 7, "eight": 8, "nine": 9}
# normalised Flipper button name (lower case, letters and digits only; + and - spelled) -> our key. First match wins by list order below.
NAMES = {
    "POWER": "power poweronoff poweroffon powertoggle onoff standby pwr standbyon tvpower stby",
    "HOME": "home homepage smarthub hub launcher homemenu",
    "BACK": "back return backreturn goback",
    "UP": "up arrowup cursorup navup upper dpadup nupup",
    "DOWN": "down arrowdown cursordown navdown dpaddown",
    "LEFT": "left arrowleft cursorleft navleft dpadleft",
    "RIGHT": "right arrowright cursorright navright dpadright",
    "OK": "ok select center centre okenter enter confirm ent selectok",
    "MENU": "menu mainmenu",
    "SETTINGS": "settings setup tools option options",
    "VOL_UP": "volup volumeup volplus volumeplus vol+ vol_up volup+ volumeinc",
    "VOL_DOWN": "voldown voldn volumedown volminus volumeminus vol- voldec volumedec",
    "MUTE": "mute muting",
    "CH_UP": "chup channelup chplus channelplus chnext ch+ pageup chnxt programup pup",
    "CH_DOWN": "chdown chdn channeldown chminus channelminus chprev ch- pagedown chprv programdown pdown",
    "INPUT": "input source tvav av inputsource avtv sourceinput inputtv extin",
    "PLAY_PAUSE": "playpause play pause",
    "STOP": "stop",
    "REWIND": "rewind rew rw fr",
    "FFWD": "ffwd ff fastforward forward fwd",
    "NEXT": "next skipnext",
    "PREV": "prev previous skipprev skipback",
    "INFO": "info display information",
    "GUIDE": "guide epg",
    "SUBTITLE": "subtitle subtitles sub cc",
}
LOOKUP = {}
for key, words in NAMES.items():
    for w in words.split():
        LOOKUP.setdefault(re.sub(r"[^a-z0-9+-]", "", w.replace("_", "")), key)
for z, d in DIGITS.items():
    LOOKUP[z] = str(d)
ORDER = ["POWER", "HOME", "BACK", "UP", "DOWN", "LEFT", "RIGHT", "OK", "MENU", "SETTINGS", "VOL_UP", "VOL_DOWN", "MUTE", "CH_UP", "CH_DOWN",
         "INPUT", "PLAY_PAUSE", "STOP", "REWIND", "FFWD", "NEXT", "PREV", "INFO", "GUIDE", "SUBTITLE"] + [str(d) for d in range(10)]


def norm(name):
    n = name.strip().lower().replace("_", "").replace(" ", "").replace(".", "")
    n = n.replace("volume+", "volup").replace("volume-", "voldown").replace("vol+", "volup").replace("vol-", "voldown")
    n = n.replace("ch+", "chup").replace("ch-", "chdown").replace("channel+", "chup").replace("channel-", "chdown")
    return re.sub(r"[^a-z0-9]", "", n)


def key_of(name):
    n = norm(name)
    m = re.fullmatch(r"(?:num|number|digit|key|btn|button|n)?(\d)", n)
    if m:
        return m.group(1)
    return LOOKUP.get(n)


def le(hexbytes, n):
    """Flipper's "01 00 00 00" (little endian bytes) -> the number, only if it fits in n bytes."""
    b = hexbytes.split()
    if not b or any(not re.fullmatch(r"[0-9A-Fa-f]{2}", x) for x in b):
        return None
    v = sum(int(x, 16) << (8 * i) for i, x in enumerate(b))
    return v if v < (1 << (8 * n)) else None


def raw_frames(f):
    """"RAW <Hz> <us>..." from a `type: raw` signal: its first two frames (cut at the long gaps), or None."""
    try:
        hz = int(f.get("frequency", ""))
        nums = [int(x) for x in f.get("data", "").split()]
    except ValueError:
        return None
    if not 30000 <= hz <= 60000 or len(nums) < 8 or any(not 100 <= n <= 30000 for n in nums):
        return None
    cut, frames = 0, 0
    for i, n in enumerate(nums):
        if i % 2 == 1 and n >= 6000:                          # the gap after a frame
            frames += 1
            cut = i + 1
            if frames == 2:
                break
    nums = nums[:cut] if cut else nums
    return "RAW %d %s" % (hz, " ".join(map(str, nums))) if len(nums) <= 120 and len(nums) % 2 == 0 else None


def parse_file(path, raw=False):
    """{our key: "PROTO addr cmd"} from one .ir file (parsed signals only), plus the file's title."""
    try:
        text = open(path, encoding="utf-8", errors="replace").read(400_000)
    except OSError:
        return {}
    keys = {}
    for block in re.split(r"\n#\s*\n", text.replace("\r", "")):
        f = dict(m.groups() for m in re.finditer(r"^(\w+):[ \t]*(.*)$", block, re.M))
        if raw and f.get("type") == "raw":
            key = RAW_ALIAS.get(norm(f.get("name", ""))) or key_of(f.get("name", ""))
            val = raw_frames(f)
            if key and val and key not in keys:
                keys[key] = val
            continue
        if f.get("type") != "parsed" or f.get("protocol") not in KEEP:
            continue
        key = key_of(f.get("name", ""))
        if not key:
            continue
        p = f["protocol"]
        a = le(f.get("address", ""), 4)
        c = le(f.get("command", ""), 4)
        if a is None or c is None:
            continue
        lim = {"NEC": (0xFF, 0xFF), "NECext": (0xFFFF, 0xFFFF), "Samsung32": (0xFF, 0xFF), "SIRC": (0x1F, 0x7F), "SIRC15": (0xFF, 0x7F),
               "SIRC20": (0x1FFF, 0x7F), "RC5": (0x1F, 0x3F), "RC5X": (0x1F, 0x7F), "RC6": (0xFF, 0xFF), "RCA": (0xF, 0xFF)}[p]
        if a > lim[0] or c > lim[1]:
            continue
        val = "%s %X %X" % (p, a, c)
        if key not in keys:                                   # the first of a name wins (some files hold "Power" twice)
            keys[key] = val
    return keys


def clean_name(stem, brand):
    s = re.sub(r"[_\s]+", " ", stem).strip()
    for pre in (brand, brand.split()[0]):
        if s.lower().startswith(pre.lower() + " "):
            s = s[len(pre) + 1:]
            break
    s = re.sub(r"\.ir$", "", s)
    return s[:28].strip() or stem[:28]


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    root = os.path.realpath(sys.argv[1])
    try:
        rev = subprocess.run(["git", "-C", root, "rev-parse", "--short", "HEAD"], capture_output=True, text=True, timeout=10).stdout.strip()
    except Exception:
        rev = ""
    profiles = []
    for folder, brand, most in BRANDS:
        d = os.path.join(root, folder)
        if not os.path.isdir(d):
            print("missing", folder)
            continue
        cands = []
        for fn in sorted(os.listdir(d)):
            full = os.path.realpath(os.path.join(d, fn))
            if not fn.endswith(".ir") or not full.startswith(root + os.sep):
                continue
            keys = parse_file(full, folder in RAW_DIRS)
            if "POWER" in keys and len(keys) >= 6:
                cands.append((fn[:-3], keys))
        cands.sort(key=lambda c: -len(c[1]))
        seen, pick = set(), []
        for stem, keys in cands:
            sig = tuple(keys.get(k) for k in ("POWER", "VOL_UP", "OK", "HOME"))
            if sig in seen:                                   # the same codes under another model name
                continue
            seen.add(sig)
            pick.append((stem, keys))
            if len(pick) >= most:
                break
        for stem, keys in pick:
            ordered = {k: keys[k] for k in ORDER if k in keys}
            profiles.append({"brand": brand, "name": clean_name(stem, brand), "keys": ordered})
        print("%-16s %2d of %d files" % (brand, len(pick), len(cands)))
    ids = set()
    for p in profiles:                                        # a stable id: brand-name, made unique
        base = re.sub(r"[^a-z0-9]+", "-", (p["brand"] + " " + p["name"]).lower()).strip("-")
        i, n = base, 2
        while i in ids:
            i, n = "%s-%d" % (base, n), n + 1
        ids.add(i)
        p["id"] = i
    out = {"v": 1, "src": "Flipper-IRDB (CC0-1.0) https://github.com/Lucaslhm/Flipper-IRDB" + (" @" + rev if rev else ""), "profiles": profiles}
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    print(len(profiles), "profiles,", os.path.getsize(OUT), "bytes ->", os.path.relpath(OUT))


if __name__ == "__main__":
    main()
