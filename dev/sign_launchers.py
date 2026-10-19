#!/usr/bin/env python3
"""dev/sign_launchers.py: checks docs/launchers/launchers.json and signs it (launchers.json.sig, Ed25519, base64).

  1. the list's shape (and writes each thumbnail's SHA-256 into it) (what core/ontv/Launchers.kt reads) and that every thumbnail file exists;
  2. every APK url is downloaded and its SHA-256 compared with the one in the list (SKIP_DOWNLOAD=1: only shape), and the languages
     its screens are written in are read from it into "langs" (see apk_langs; a "play" entry's are written by hand);
  3. launchers.json is signed with LAUNCHERS_KEY (default ~/.tv-remote-release/launchers.key), a key SEPARATE from the
     release key: the list names APKs that go onto people's TVs, and a leak of one key must not carry the other.
     The key is made here when absent (never committed, never overwritten; backup: dev/README.md) and its public half is
     printed for Launchers.KEYS in app/shared/.../core/ontv/Launchers.kt.

Run it after every edit of the list, then commit launchers.json and launchers.json.sig together: the apps refuse a list whose
signature does not match its bytes. Nothing here uploads: the docs/ site is deployed by Cloudflare Pages on push.
"""
import glob, hashlib, json, os, re, subprocess, sys, tempfile, urllib.request, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIR = os.path.join(ROOT, "docs", "launchers")
LIST = os.path.join(DIR, "launchers.json")
KEY = os.environ.get("LAUNCHERS_KEY", os.path.expanduser("~/.tv-remote-release/launchers.key"))
ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64", "any"}


def fail(m):
    print("x " + m)
    sys.exit(1)


def sha(url, keep=None):
    h = hashlib.sha256()
    req = urllib.request.Request(url, headers={"User-Agent": "tv-remote"})
    out = open(keep, "wb") if keep else None
    with urllib.request.urlopen(req, timeout=120) as r:
        while True:
            b = r.read(1 << 16)
            if not b:
                break
            h.update(b)
            if out:
                out.write(b)
    if out:
        out.close()
    return h.hexdigest()


# Strings of the libraries every app carries (AndroidX, Material): translated into ~80 languages whatever the app's own screens are
LIB = ("abc_", "m3_", "m3c_", "mtrl", "material_", "exo_", "common_", "androidx", "call_notification", "fallback_menu", "search_menu",
       "status_bar", "bottomsheet", "bottom_sheet", "character_counter", "clear_text", "error_", "exposed_", "hide_", "icon_content",
       "item_view", "password_", "path_password", "searchbar", "searchview", "side_sheet", "appbar", "fab_", "chip_", "not_set",
       "leanback", "lb_", "preference", "v7_", "summary_collapsed", "expand_button", "dropdown_menu", "navigation_menu", "range_",
       "close_drawer", "close_sheet", "default_error", "default_popup", "dialog", "in_progress", "indeterminate", "state_", "switch_role",
       "tab", "template_percent", "tooltip", "selected", "collapsed", "expanded", "autofill", "spoken_", "wear_", "offline_", "gcm_", "fcm_")


def apk_langs(path):
    """The languages an APK's own screens are written in, so a TV in another language can show the launcher in English instead
    (core/ontv/Launchers.kt english()): Android's own pick then falls back to the app's default words, which may be Chinese
    (UnitedU), and a Flutter app falls back to the first language it has, Arabic for LTV, right to left. None when unknown."""
    b = zipfile.ZipFile(path)
    lib = [n for n in b.namelist() if n.endswith("/libapp.so")]
    if lib:                                                            # Flutter: its words are in Dart, one AppLocalizations<Lang> class each
        found = set(re.findall(rb"AppLocalizations([A-Z][a-z])\b", b.read(lib[0])))
        return sorted({x.decode().lower() for x in found}) or None
    tools = sorted(glob.glob(os.path.expanduser(os.environ.get("ANDROID_HOME", "~/Library/Android/sdk")) + "/build-tools/*/aapt2"))
    if not tools:
        print("!  no aapt2 (Android SDK build-tools): langs not read")
        return None
    out = subprocess.run([tools[-1], "dump", "resources", path], capture_output=True, text=True, errors="replace").stdout
    own, cur = {}, None
    for line in out.splitlines():
        m = re.match(r"\s+resource 0x\w+ string/(\S+)", line)
        if m:
            cur = None if m.group(1).startswith(LIB) else m.group(1)
            continue
        m = re.match(r'\s+\((\S*)\) "(.*)"', line)
        if m and cur:
            own.setdefault(cur, {})[m.group(1).split("-")[0]] = m.group(2)
    if not own:
        return None
    n = len(own)
    langs = {l for l in {l for v in own.values() for l in v} if l and sum(l in v for v in own.values()) * 2 >= n}
    default = " ".join(v.get("", "") for v in own.values())            # the words without a language: English, unless they are not
    langs.add("zh" if re.search("[\u4e00-\u9fff]", default) else "en")
    return sorted({"id" if l == "in" else "he" if l == "iw" else l for l in langs})


def check():
    d = json.load(open(LIST, encoding="utf-8"))
    if d.get("v") != 1:
        fail("v must be 1")
    seen = set()
    for e in d["launchers"]:
        i = e.get("id", "")
        if not i or i in seen or not all(c.isalnum() or c in "-_" for c in i):
            fail("bad or repeated id: %r" % i)
        seen.add(i)
        for k in ("package", "name", "thumb", "source", "license", "kind", "supports"):
            if k not in e:
                fail("%s: no %s" % (i, k))
        for l in ("vi", "en"):
            if not e.get("desc", {}).get(l):
                fail("%s: desc.%s missing" % (i, l))
        if not os.path.isfile(os.path.join(DIR, e["thumb"])):
            fail("%s: thumbnail %s not in docs/launchers/" % (i, e["thumb"]))
        e["thumb_sha256"] = hashlib.sha256(open(os.path.join(DIR, e["thumb"]), "rb").read()).hexdigest()   # a picture is checked like an APK
        if not e["source"].startswith("https://"):
            fail("%s: source must be https" % i)
        if e["kind"] == "apk":
            files = e["apk"]["files"]
            if not files or not set(files) <= ABIS:
                fail("%s: apk.files keys must be among %s" % (i, sorted(ABIS)))
            for abi, f in files.items():
                if not f["url"].startswith("https://") or len(f["sha256"]) != 64:
                    fail("%s/%s: https url and a 64-hex sha256 needed" % (i, abi))
                if os.environ.get("SKIP_DOWNLOAD") != "1":
                    with tempfile.TemporaryDirectory() as t:
                        got = sha(f["url"], os.path.join(t, "a.apk"))
                        print(("ok " if got == f["sha256"] else "x  ") + "%s %s %s" % (i, abi, got))
                        if got != f["sha256"]:
                            fail("%s/%s: the file at the url has another SHA-256 than the list" % (i, abi))
                        langs = apk_langs(os.path.join(t, "a.apk"))
                    if langs:
                        e["langs"] = langs
        if "langs" in e and not (isinstance(e["langs"], list) and all(isinstance(l, str) and re.fullmatch("[a-z]{2,3}", l) for l in e["langs"])):
            fail("%s: langs must be a list of language codes (en, vi, zh)" % i)
        if e["kind"] == "apk":
            pass
        elif e["kind"] == "play":
            if not e["play"]["url"].startswith("market://details?id=" + e["package"]):
                fail("%s: play.url must open this package" % i)
        elif e["kind"] == "self":                                      # TV Home: its file comes from the latest release, pinned by the signed SHA256SUMS
            if e["package"] != "dev.tvremote.home":
                fail("%s: kind self is only TV Home" % i)
        else:
            fail("%s: kind must be apk, play or self" % i)
    return d


def main():
    d = check()
    with open(LIST, "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=2)
        f.write("\n")
    if not os.path.exists(KEY):
        pub = subprocess.run(["java", os.path.join(ROOT, "dev", "Sign.java"), "new", KEY], capture_output=True, text=True, check=True).stdout.strip()
        print("new key %s\n  public key for Launchers.KEYS: %s" % (KEY, pub))
    pub = subprocess.run(["java", os.path.join(ROOT, "dev", "Sign.java"), "pub", KEY], capture_output=True, text=True, check=True).stdout.strip()
    subprocess.run(["java", os.path.join(ROOT, "dev", "Sign.java"), "sign", KEY, LIST], check=True)
    print("signed %s with the key %s" % (LIST, pub))


main()
