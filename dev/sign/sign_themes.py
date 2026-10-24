#!/usr/bin/env python3
"""dev/sign/sign_themes.py: checks docs/home/themes.json and signs it (themes.json.sig, Ed25519, base64).

The list holds TV Home's ready-made themes (id, names per language, conf = the same keys HomeConfig cleans, optional "since" = the
first TV Home version that shows it). TV Home reads it from the site, else GitHub, else the copy it kept, else the one packed in
its APK; a list without a good signature is ignored. It is signed because a theme may set "wurl" (a web page as the background),
which only a signed list can. It uses the launcher list's key (LAUNCHERS_KEY, default ~/.tv-remote-release/launchers.key).

Run it after every edit, then commit themes.json and themes.json.sig together (:home packs both as the copy used until the site's is
read; HomeThemesTest fails when the packed copy differs). Nothing here uploads: the site is deployed on push.
"""
import json, os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LIST = os.path.join(ROOT, "docs", "home", "themes.json")
KEY = os.environ.get("LAUNCHERS_KEY", os.path.expanduser("~/.tv-remote-release/launchers.key"))
THEME_KEYS = ["wall", "walls", "wurl", "wmode", "wsnap", "wkeep", "wevery", "shade", "vsound", "vvol", "widgets", "wsize", "wstyle",
              "cols", "rows", "focus", "motion", "anim", "clock24", "greet"]   # HomeConfig.THEME_KEYS


def fail(m):
    print("x " + m)
    sys.exit(1)


def check():
    d = json.load(open(LIST, encoding="utf-8"))
    if d.get("v") != 1:
        fail("v must be 1")
    ids = set()
    for t in d.get("themes", []):
        i = t.get("id")
        if not isinstance(i, str) or not re.fullmatch(r"[a-z][a-z0-9_]{0,15}", i):
            fail("bad id: %r" % i)
        if i in ids:
            fail("two themes named " + i)
        ids.add(i)
        n = t.get("names") or {}
        if not n.get("en"):
            fail("%s: no English name (the fallback)" % i)
        if not isinstance(t.get("conf"), dict) or not t["conf"].get("wall"):
            fail("%s: no conf / wall" % i)
        bad = [k for k in t["conf"] if k not in THEME_KEYS]
        if bad:
            fail("%s: not a theme setting: %s" % (i, ", ".join(bad)))
        if "wurl" in t["conf"] and not t["conf"]["wurl"].startswith(("https://", "http://")):
            fail("%s: wurl is not http(s)" % i)
        if t.get("since") and not re.fullmatch(r"\d+(\.\d+){1,2}", t["since"]):
            fail("%s: bad since" % i)
    if not ids:
        fail("no themes")
    return d


def main():
    d = check()
    if not os.path.exists(KEY):
        fail("no key at %s (dev/README.md: the launcher list's key)" % KEY)
    subprocess.run(["java", os.path.join(ROOT, "dev", "release", "Sign.java"), "sign", KEY, LIST], check=True)
    print("signed %s: %d themes (%s)" % (LIST, len(d["themes"]), ", ".join(t["id"] for t in d["themes"])))


main()
