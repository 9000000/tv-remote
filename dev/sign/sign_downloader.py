#!/usr/bin/env python3
"""dev/sign/sign_downloader.py: checks docs/data/downloader.json and signs it (downloader.json.sig, Ed25519, base64).

The file is the one place that lists the Downloader (AFTVnews) short codes of the project's APKs: the apps read it from the site
(core/Downloader.kt; else GitHub, else the copy packed in the code, which a test keeps equal to this file). A code makes a TV
install whatever it points at, so the apps use the file only when this signature checks. Same key as dev/sign/sign_games.py
(LAUNCHERS_KEY, default ~/.tv-remote-release/launchers.key; never printed). Run it after every edit, then commit the file and
its .sig together. The pages of docs/ may keep the codes as text, but this file is where they come from.
"""
import json, os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LIST = os.path.join(ROOT, "docs", "data", "downloader.json")
KEY = os.environ.get("LAUNCHERS_KEY", os.path.expanduser("~/.tv-remote-release/launchers.key"))
IDS = {"setup", "remote", "home", "channels", "games"}               # tv-setup, tv-remote, tv-home, tv-channels, tv-games


def fail(m):
    print("x " + m)
    sys.exit(1)


d = json.load(open(LIST, encoding="utf-8"))
if d.get("v") != 1:
    fail("v must be 1")
for k, v in d.items():
    if k == "v":
        continue
    if k not in IDS:
        fail("unknown app %r (core/Downloader.kt users: %s)" % (k, sorted(IDS)))
    if v is not None and not re.fullmatch(r"\d{4,9}", str(v)):
        fail("%s: a code is 4-9 digits (or null while the APK has none)" % k)
if not os.path.exists(KEY):
    fail("no key at %s; downloader.json.sig is left for the maintainer (the apps use the packed codes until then)" % KEY)
subprocess.run(["java", os.path.join(ROOT, "dev", "release", "Sign.java"), "sign", KEY, LIST], check=True)
print("signed %s: %d codes" % (LIST, sum(1 for k in d if k != "v" and d[k])))
