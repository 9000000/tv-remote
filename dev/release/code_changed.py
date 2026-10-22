#!/usr/bin/env python3
# Did an app's code change since a tag, beyond comments? release.sh bumps an app's number only then, so a path renamed in
# comments (dev/gen_icons.py -> dev/gen/gen_icons.py in every icon) does not send its users an update with nothing in it.
#   dev/release/code_changed.py <tag> <git pathspec>...     exit 0 = changed (prints the files), 1 = not
# Not counted: *.md, build.sh (not in the APK), tests, and comments in Kotlin / Java / Gradle / XML / shell / Python.
import re, subprocess, sys

tag, specs = sys.argv[1], sys.argv[2:]
names = subprocess.run(["git", "diff", "--name-only", tag, "--"] + specs, capture_output=True, text=True, check=True).stdout.split()

C_LIKE = (".kt", ".kts", ".java", ".gradle", ".js", ".css")
HASH = (".sh", ".py", ".pro", ".properties", ".toml")


def strip(name, text):
    if name.endswith(C_LIKE):
        text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
        text = re.sub(r"(?<![:\\\"'])//.*", "", text)                 # not the // of a URL in a string
    elif name.endswith((".xml", ".html", ".svg")):
        text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    elif name.endswith(HASH):
        text = re.sub(r"(?m)^\s*#(?!!).*$", "", text)
    return [l.rstrip() for l in text.splitlines() if l.strip()]


def old(name):
    r = subprocess.run(["git", "show", f"{tag}:{name}"], capture_output=True)
    return None if r.returncode else r.stdout.decode("utf-8", "replace")


changed = []
for n in names:
    if n.endswith(".md") or n.endswith("/build.sh") or "/src/test/" in n or "/tests/" in n:
        continue
    try:
        new = open(n, encoding="utf-8").read()
    except (FileNotFoundError, UnicodeDecodeError):
        changed.append(n); continue                                   # deleted, or a binary (a picture, a .so, a jar)
    o = old(n)
    if o is None or strip(n, o) != strip(n, new):
        changed.append(n)

print("\n".join(changed))
sys.exit(0 if changed else 1)
