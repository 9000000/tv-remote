#!/usr/bin/env python3
"""dev/sign/sign_keys.py: checks docs/setup/keys.json and signs it (keys.json.sig, Ed25519, base64).

The list fixes one TV model's odd remote button with no new APK: on a TV whose Build.MODEL or Build.DEVICE matches, a key code
does one of TV Setup's own actions (input picker, home, settings, an app). What each action does is code (KeyFix.kt); only the
match is here. Navigation, volume and power keys are refused, so a bad entry cannot take the remote away. Find a remote's codes
with support: `support.py CODE sh "getevent -pl"` and `logcat -d -s KeyFix` ("key <code>" while support looks).

Signed with the launcher list's key (LAUNCHERS_KEY, default ~/.tv-remote-release/launchers.key). Run after every edit (raise rev),
commit keys.json and keys.json.sig together; :tv packs both as the copy used until the site's is read. Nothing here uploads.
"""
import json, os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LIST = os.path.join(ROOT, "docs", "setup", "keys.json")
KEY = os.environ.get("LAUNCHERS_KEY", os.path.expanduser("~/.tv-remote-release/launchers.key"))
ACTS = {"input", "home", "settings", "app"}                                   # KeyFix.INPUT / HOME / SETTINGS / APP
NEVER = {3, 4, 19, 20, 21, 22, 23, 24, 25, 26, 66, 82, 164, 223, 224}        # KeyList.NEVER: Home, Back, D-pad, OK, volume, power, menu


def fail(m):
    print("x " + m)
    sys.exit(1)


def check():
    d = json.load(open(LIST, encoding="utf-8"))
    if d.get("v") != 1:
        fail("v must be 1")
    if not isinstance(d.get("rev"), int):
        fail("rev must be a number (raise it on each edit)")
    for i, k in enumerate(d.get("keys", [])):
        what = "keys[%d]" % i
        if not (k.get("model") or k.get("device")):
            fail(what + ": needs a model or a device (one TV, never all)")
        if not isinstance(k.get("code"), int) or not 0 < k["code"] < 1000 or k["code"] in NEVER:
            fail(what + ": code must be a key code that is not navigation / volume / power")
        if k.get("act") not in ACTS:
            fail(what + ": act must be one of " + ", ".join(sorted(ACTS)))
        if k["act"] == "app" and not (isinstance(k.get("pkgs"), list) and k["pkgs"] and all(re.fullmatch(r"[\w]+(\.[\w]+)+", p) for p in k["pkgs"])):
            fail(what + ": app needs pkgs, a list of package names")
        if not k.get("why"):
            fail(what + ": say why (the TV, the button, where it was seen)")
    return d


def main():
    d = check()
    if not os.path.exists(KEY):
        fail("no key at %s (dev/README.md: the launcher list's key)" % KEY)
    subprocess.run(["java", os.path.join(ROOT, "dev", "release", "Sign.java"), "sign", KEY, LIST], check=True)
    print("signed %s: rev %d, %d keys" % (LIST, d["rev"], len(d["keys"])))


main()
