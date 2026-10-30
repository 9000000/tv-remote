#!/usr/bin/env python3
"""Which topics of the user guide (docs/guide.html) may be out of date.

Each topic carries data-v, the release it was last checked against ("next" = checked against main, stamped with the
version by release.sh), and data-src, the files whose changes can make it wrong (web/, core/, phone/, tv/, pc/ are
short for the app folders). A topic is listed when one of its files changed since its release's tag.
  dev/release/guide_stale.py          the list, with the commits
  dev/release/guide_stale.py -q       only the topic ids (release.sh)"""
import json, os, re, subprocess, sys
from renamed import before

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
P = {"web/": "app/shared/web/", "core/": "app/shared/src/main/kotlin/dev/tvremote/core/",
     "phone/": "app/android/src/main/kotlin/dev/tvremote/phone/", "setup/": "app/tvsetup/src/main/kotlin/dev/tvremote/setup/",
     "pc/": "app/desktop/src/main/kotlin/dev/tvremote/desktop/"}

def full(x):
    for k, v in P.items():
        if x.startswith(k): return v + x[len(k):]
    return x

def git(*a):
    return subprocess.run(["git", *a], cwd=ROOT, capture_output=True, text=True).stdout.strip()

quiet = "-q" in sys.argv


def moved_out(log):
    """git log --name-status less the commits that only moved files (R100: the folder rename), as "  <hash> <subject>" lines."""
    out = []
    for c in log.split("\x01")[1:]:
        head, *files = c.strip("\n").split("\n")
        if any(f.strip() and not f.startswith("R100") for f in files): out.append(head)
    return "\n".join(out)
html = open(os.path.join(ROOT, "docs/guide.html"), encoding="utf-8").read()
stale = 0
try:
    SINCE = json.load(open(os.path.join(ROOT, "docs/data/since.json"), encoding="utf-8"))["topics"]
except (OSError, ValueError):
    SINCE = {}
for tid, v, src in re.findall(r'<article class="topic" id="([^"]+)" data-v="([^"]+)" data-src="([^"]*)"', html):
    if tid not in SINCE: print(f"{tid}: no entry in docs/data/since.json (add it: \"since\": \"next\")", file=sys.stderr)
    paths = [full(x) for x in src.split() if x != "docs/guide.html"]   # its own text changes in every guide edit: no signal
    gone = [p for p in paths if not os.path.exists(os.path.join(ROOT, p))]
    if v == "next" or not paths:
        log = ""                                                     # nothing in the code it follows (git log -- with no path = every commit); "next": read on main, not released yet
    elif not git("tag", "-l", "v" + v):
        log = f"(no tag v{v})"
    else:
        log = moved_out(git("log", "-M", "--name-status", "--format=%x01  %h %s", f"v{v}..HEAD", "--", *dict.fromkeys(q for p in paths if p not in gone for q in (p, before(p)))))
    if log or gone:
        stale += 1
        if quiet: print(tid); continue
        print(f"{tid} (v{v}): " + ", ".join(src.split()))
        for g in gone: print(f"  moved or removed: {g}: fix data-src")
        if log: print("\n".join(log.splitlines()[:8]))
if not quiet:
    print(f"{stale} topic(s) to look at; after updating one, set its data-v=\"next\"" if stale else "every topic is up to date")
