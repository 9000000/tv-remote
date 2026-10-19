#!/usr/bin/env python3
"""dev/sign_launchers.py: checks docs/launchers/launchers.json and signs it (launchers.json.sig, Ed25519, base64).

  1. the list's shape (and writes each thumbnail's SHA-256 into it) (what core/ontv/Launchers.kt reads) and that every thumbnail file exists;
  2. every APK url is downloaded and its SHA-256 compared with the one in the list (SKIP_DOWNLOAD=1: only shape);
  3. launchers.json is signed with LAUNCHERS_KEY (default ~/.tv-remote-release/launchers.key), a key SEPARATE from the
     release key: the list names APKs that go onto people's TVs, and a leak of one key must not carry the other.
     The key is made here when absent (never committed, never overwritten; backup: dev/README.md) and its public half is
     printed for Launchers.KEYS in app/shared/.../core/ontv/Launchers.kt.

Run it after every edit of the list, then commit launchers.json and launchers.json.sig together: the apps refuse a list whose
signature does not match its bytes. Nothing here uploads: the docs/ site is deployed by Cloudflare Pages on push.
"""
import hashlib, json, os, subprocess, sys, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIR = os.path.join(ROOT, "docs", "launchers")
LIST = os.path.join(DIR, "launchers.json")
KEY = os.environ.get("LAUNCHERS_KEY", os.path.expanduser("~/.tv-remote-release/launchers.key"))
ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64", "any"}


def fail(m):
    print("x " + m)
    sys.exit(1)


def sha(url):
    h = hashlib.sha256()
    req = urllib.request.Request(url, headers={"User-Agent": "tv-remote"})
    with urllib.request.urlopen(req, timeout=120) as r:
        while True:
            b = r.read(1 << 16)
            if not b:
                break
            h.update(b)
    return h.hexdigest()


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
                    got = sha(f["url"])
                    print(("ok " if got == f["sha256"] else "x  ") + "%s %s %s" % (i, abi, got))
                    if got != f["sha256"]:
                        fail("%s/%s: the file at the url has another SHA-256 than the list" % (i, abi))
        elif e["kind"] == "play":
            if not e["play"]["url"].startswith("market://details?id=" + e["package"]):
                fail("%s: play.url must open this package" % i)
        else:
            fail("%s: kind must be apk or play" % i)
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
