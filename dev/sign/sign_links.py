#!/usr/bin/env python3
"""dev/sign/sign_links.py: checks docs/home/links.json and signs it (links.json.sig, Ed25519, base64).

The list tells TV Home which places inside other apps it can pin to the grid: per app its package, a link with "{id}" in it (the
app's own deep link, e.g. vtvgo://play?mediaType=CHANNEL&contentId={id}) and the items (id, name, logo). TV Home opens the link
in that package only (Intent.setPackage), and offers an app's items only when the app is installed. A new app is an entry here,
no new APK. A package "*" names no app: whoever handles that https link opens it (apps/LinkApps.kt; the YouTube entry). "logo" is a file name under docs/home/logos/ (without .png) or an https address. dev/gen/app_links.py remakes the
VTVgo and TV360 entries; others are kept as written.

Run it after every edit (raise "rev"), then commit links.json, links.json.sig and the logos together. :home packs the list as the
copy used until the site's is read. Signed with the launcher list's key (LAUNCHERS_KEY, default ~/.tv-remote-release/launchers.key).
"""
import json, os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LIST = os.path.join(ROOT, "docs", "home", "links.json")
LOGOS = os.path.join(ROOT, "docs", "home", "logos")
KEY = os.environ.get("LAUNCHERS_KEY", os.path.expanduser("~/.tv-remote-release/launchers.key"))
PKG = re.compile(r"[A-Za-z][\w]*(\.[\w]+)+")
ID = re.compile(r"[A-Za-z0-9_.\-]{1,40}")
SCHEME = re.compile(r"([a-z][a-z0-9+.\-]*):")
BANNED = {"intent", "file", "javascript", "content", "android-app"}   # Links.kt refuses the same: a link only names a place in its app


def fail(m):
    print("x " + m)
    sys.exit(1)


def check():
    d = json.load(open(LIST, encoding="utf-8"))
    if d.get("v") != 1:
        fail("v must be 1")
    if not isinstance(d.get("rev"), int):
        fail("rev must be a number (raise it on each edit)")
    pkgs = set()
    for a in d.get("apps", []):
        p = a.get("pkg", "")
        key = p if p != "*" else "*" + a.get("name", "")
        if not (PKG.fullmatch(p) or p == "*") or key in pkgs:
            fail("bad or repeated pkg: %r" % p)
        pkgs.add(key)
        u = a.get("uri", "")
        m = SCHEME.match(u)
        if p == "*" and (not m or m.group(1) != "https"):
            fail("%s: an entry with no package (\"*\", whoever handles the link) takes https links only" % a.get("name"))
        if "{id}" not in u or not m or m.group(1) in BANNED:
            fail("%s: uri needs a scheme (not %s) and {id}" % (p, "/".join(sorted(BANNED))))
        if not a.get("name"):
            fail("%s: needs a name" % p)
        ids = set()
        for i in a.get("items", []):
            if not ID.fullmatch(i.get("id", "")) or i["id"] in ids:
                fail("%s: bad or repeated item id %r" % (p, i.get("id")))
            ids.add(i["id"])
            if not i.get("name"):
                fail("%s/%s: needs a name" % (p, i["id"]))
            lg = i.get("logo", "")
            if lg and not lg.startswith("https://") and not os.path.isfile(os.path.join(LOGOS, lg + ".png")):
                fail("%s/%s: no docs/home/logos/%s.png" % (p, i["id"], lg))
    return d


def main():
    d = check()
    if not os.path.exists(KEY):
        fail("no key at %s (dev/README.md: the launcher list's key)" % KEY)
    subprocess.run(["java", os.path.join(ROOT, "dev", "release", "Sign.java"), "sign", KEY, LIST], check=True)
    print("signed %s: rev %d, %d apps, %d items" % (LIST, d["rev"], len(d["apps"]), sum(len(a["items"]) for a in d["apps"])))


main()
