#!/usr/bin/env python3
"""Which topics of the user guide (docs/guide.html) may be out of date.

Each topic carries data-v, the release it was last checked against ("next" = checked against main, stamped with the
version by release.sh), and data-src, the files whose changes can make it wrong (web/, core/, phone/, tv/, pc/ are
short for the app folders). A topic is listed when one of its files changed since its release's tag.
  dev/guide_stale.py          the list, with the commits
  dev/guide_stale.py -q       only the topic ids (release.sh)"""
import os, re, subprocess, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
P = {"web/": "app/shared/web/", "core/": "app/shared/src/main/kotlin/dev/tvremote/core/",
     "phone/": "app/android/src/main/kotlin/dev/tvremote/phone/", "tv/": "app/tv/src/main/kotlin/dev/tvremote/setup/",
     "pc/": "app/desktop/src/main/kotlin/dev/tvremote/desktop/"}

def full(x):
    for k, v in P.items():
        if x.startswith(k): return v + x[len(k):]
    return x

def git(*a):
    return subprocess.run(["git", *a], cwd=ROOT, capture_output=True, text=True).stdout.strip()

quiet = "-q" in sys.argv
html = open(os.path.join(ROOT, "docs/guide.html"), encoding="utf-8").read()
stale = 0
for tid, v, src in re.findall(r'<article class="topic" id="([^"]+)" data-v="([^"]+)" data-src="([^"]*)"', html):
    paths = [full(x) for x in src.split()]
    gone = [p for p in paths if not os.path.exists(os.path.join(ROOT, p))]
    if v == "next":
        log = ""                                                     # checked against main: not released yet
    elif not git("tag", "-l", "v" + v):
        log = f"(no tag v{v})"
    else:
        log = git("log", "--format=  %h %s", f"v{v}..HEAD", "--", *[p for p in paths if p not in gone])
    if log or gone:
        stale += 1
        if quiet: print(tid); continue
        print(f"{tid} (v{v}): " + ", ".join(src.split()))
        for g in gone: print(f"  moved or removed: {g}: fix data-src")
        if log: print("\n".join(log.splitlines()[:8]))
if not quiet:
    print(f"{stale} topic(s) to look at; after updating one, set its data-v=\"next\"" if stale else "every topic is up to date")
