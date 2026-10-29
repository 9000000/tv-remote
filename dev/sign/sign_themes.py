#!/usr/bin/env python3
"""dev/sign/sign_themes.py: checks docs/home/themes.json and signs it (themes.json.sig, Ed25519, base64).

The list holds TV Home's ready-made themes (id, names per language, conf = the same keys HomeConfig cleans, optional "tiles" (live tiles added first to the grid, kept beside the person's own), "look" (a drawn wall for its picture), "thumb" (a small https picture of a photo / web / video background, ~330 px, for its preview), "for" (who it is for), "src" (licence of a web background), "by" + "community": true (sent by a person, reviewed: dev/themes), optional "since" = the
first TV Home version that shows it), optional "tags" (only the site's themes page reads them; TV Home ignores them). TV Home reads it from the site, else GitHub, else the copy it kept, else the one packed in
its APK; a list without a good signature is ignored. It is signed because a theme may set "wurl" (a web page as the background),
which only a signed list can. It uses the launcher list's key (LAUNCHERS_KEY, default ~/.tv-remote-release/launchers.key).

Run it after every edit, then commit themes.json and themes.json.sig together (:home packs both as the copy used until the site's is
read; HomeThemesTest fails when the packed copy differs). Nothing here uploads: the site is deployed on push.
"""
import json, time, os, re, subprocess, sys

import home_keys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LIST = os.path.join(ROOT, "docs", "home", "themes.json")
KEY = os.environ.get("LAUNCHERS_KEY", os.path.expanduser("~/.tv-remote-release/launchers.key"))
LOOKS = ["dawn", "peaks", "dunes", "waves", "candy", "peach", "meadow", "mist", "bokeh", "canyon", "night", "ocean", "dusk", "forest", "aurora", "black", "sunset", "ember",
         "plum", "midnight", "teal", "slate", "graphite", "navy", "wine", "moss"]   # HomeConfig.ART + PLAIN: what a tile's picture can be drawn from
TAGS = {"video", "slideshow", "photo", "art", "minimal", "vietnam", "kids", "night", "nature", "city", "community"}   # docs/js/themes.js
TILE = re.compile(r"tile:[a-z][a-z0-9_.]*/[a-z0-9_]+")
THEME_KEYS = home_keys.theme_keys()


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
        if "wurl" in t["conf"] and not t["conf"]["wurl"].startswith("https://"):
            fail("%s: wurl is not https" % i)
        for k in t:
            if k not in ("id", "names", "for", "conf", "since", "tiles", "look", "src", "thumb", "by", "community", "tags"):
                fail("%s: unknown field %s" % (i, k))
        f = t.get("for") or {}
        com = t.get("community") is True   # a theme sent by a person (dev/sign/review_themes.py): "by" = their name, no "for" line needed
        if "by" in t and not (isinstance(t["by"], str) and 1 <= len(t["by"]) <= 24):
            fail("%s: bad by" % i)
        if "community" in t and not com:
            fail("%s: community must be true" % i)
        if not com and (not f.get("en") or not f.get("vi")):
            fail("%s: no 'for' (who the theme is for, vi + en)" % i)
        if not n.get("vi"):
            fail("%s: no Vietnamese name" % i)
        for x in t.get("tiles", []):
            if not TILE.fullmatch(x):
                fail("%s: bad tile %s" % (i, x))
        if len(t.get("tiles", [])) > 4:
            fail("%s: at most 4 tiles" % i)
        if t.get("look") and t["look"] not in LOOKS:
            fail("%s: look %s is not a drawn wall" % (i, t["look"]))
        if t["conf"].get("wall") in ("web", "bing") and not t.get("look"):
            fail("%s: a web / Bing wall needs a 'look' for its picture in the list" % i)
        if "thumb" in t and not (isinstance(t["thumb"], str) and re.fullmatch(r"https://[^\s\"'<>]{4,280}", t["thumb"])):
            fail("%s: thumb must be a short https address (a small picture of the background, 330 px)" % i)
        if "wurl" in t["conf"] and not t.get("src") and not com:
            fail("%s: a web background needs 'src' (where it is from, its licence)" % i)
        if not set(t.get("tags", [])) <= TAGS:
            fail("%s: tags must be from %s (the showcase page, docs/themes.html; TV Home ignores them)" % (i, ", ".join(sorted(TAGS))))
        if t.get("since") and not re.fullmatch(r"\d+(\.\d+){1,2}", t["since"]):
            fail("%s: bad since" % i)
    if not ids:
        fail("no themes")
    return d


def main():
    d = check()
    if not os.path.exists(KEY):
        fail("no key at %s (dev/README.md: the launcher list's key)" % KEY)
    d["rev"] = int(time.time())   # the apps keep the list with the higher rev, so a site not yet deployed cannot put an older list over a newer APK's
    with open(LIST, "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=1); f.write("\n")
    subprocess.run(["java", os.path.join(ROOT, "dev", "release", "Sign.java"), "sign", KEY, LIST], check=True)
    print("signed %s: %d themes (%s)" % (LIST, len(d["themes"]), ", ".join(t["id"] for t in d["themes"])))


if __name__ == "__main__":
    main()
