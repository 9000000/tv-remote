#!/usr/bin/env python3
"""dev/sign/sign_bloat.py: checks docs/optimize/bloat.json and signs it (bloat.json.sig, Ed25519, base64).

Optimize TV's brand knowledge is Bloat.kt (built into every app) plus this list, read from the site when Optimize TV opens:
a TV owners report on (dev/bloat-sources.md) gets its apps here with no new app. "suggest" adds packages ticked by default,
"drop" takes some off the suggestions, "keep" and "factory" add patterns (Java regexes) that are never turned off / never
opened. The apps only add keep / factory patterns to Bloat.kt's, so a wrong list cannot free what the built-in ones hold.
Signed with the launcher list's key (LAUNCHERS_KEY, default ~/.tv-remote-release/launchers.key). Commit both files; the site
is deployed on push. Now and then fold the list into Bloat.kt and empty it.
"""
import json, os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LIST = os.path.join(ROOT, "docs", "optimize", "bloat.json")
KEY = os.environ.get("LAUNCHERS_KEY", os.path.expanduser("~/.tv-remote-release/launchers.key"))
PKG = re.compile(r"[A-Za-z]\w*(\.\w+)+")


def fail(m):
    print("x " + m)
    sys.exit(1)


def kotlin_keep():
    """Bloat.kt's KEEP pattern, joined as the Kotlin string concatenation does, to refuse a suggestion it would keep anyway."""
    s = open(os.path.join(ROOT, "app/shared/src/main/kotlin/dev/tvremote/core/ontv/Bloat.kt"), encoding="utf-8").read()
    body = s[s.index("val KEEP = Regex("):]
    body = body[:body.index(")\n")]
    return re.compile("".join(re.findall(r'"((?:[^"\\]|\\.)*)"', body)).replace("\\\\", "\\"))


def check():
    d = json.load(open(LIST, encoding="utf-8"))
    if d.get("v") != 1:
        fail("v must be 1")
    keep = [kotlin_keep()]
    for k in ("keep", "factory"):
        for p in d.get(k, []):
            try:
                r = re.compile(p)
            except re.error as e:
                fail("%s: bad pattern %r (%s)" % (k, p, e))
            if k == "keep":
                keep.append(r)
    for p, name in d.get("suggest", {}).items():
        if not PKG.fullmatch(p) or not isinstance(name, str) or not name:
            fail("suggest: bad entry %r" % p)
        if any(r.fullmatch(p) for r in keep):
            fail("suggest: %s is also kept (KEEP wins, so it would never be turned off)" % p)
    for p in d.get("drop", []):
        if not PKG.fullmatch(p):
            fail("drop: bad package %r" % p)
    return d


def main():
    d = check()
    if not os.path.exists(KEY):
        fail("no key at %s (dev/README.md: the launcher list's key)" % KEY)
    subprocess.run(["java", os.path.join(ROOT, "dev", "release", "Sign.java"), "sign", KEY, LIST], check=True)
    print("signed %s: %d suggested, %d dropped, %d keep, %d factory" % (LIST, len(d.get("suggest", {})), len(d.get("drop", [])), len(d.get("keep", [])), len(d.get("factory", []))))


main()
