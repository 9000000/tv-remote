#!/usr/bin/env python3
# Did an app's code change since a tag, beyond comments? release.sh bumps an app's number only then, so a path renamed in
# comments (dev/gen_icons.py -> dev/gen/gen_icons.py in every icon) does not send its users an update with nothing in it.
#   dev/release/code_changed.py <tag> <git pathspec>...     exit 0 = changed (prints the files), 1 = not
#   dev/release/code_changed.py --show <tag> <path>         the file as it was at the tag (release.sh: an app's last number)
# Not counted: *.md, build.sh (not in the APK), tests, and comments in Kotlin / Java / Gradle / XML / shell / Python.
import re, subprocess, sys
from renamed import before                                           # a moved file is compared with its old path


def old_bytes(name):
    for n in dict.fromkeys([name, before(name)]):
        r = subprocess.run(["git", "show", f"{tag}:{n}"], capture_output=True)
        if not r.returncode:
            return r.stdout
    return None


def old(name):
    b = old_bytes(name)
    return None if b is None else b.decode("utf-8", "replace")


if sys.argv[1] == "--show":
    tag = sys.argv[2]
    o = old(sys.argv[3])
    if o is None:
        sys.exit(1)
    print(o, end="")
    sys.exit(0)

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


changed = []
for n in names:
    if n.endswith(".md") or n.endswith("/build.sh") or "/src/test/" in n or "/tests/" in n:
        continue
    try:
        new = open(n, encoding="utf-8").read()
    except FileNotFoundError:
        changed.append(n); continue                                   # deleted
    except UnicodeDecodeError:                                        # a binary (a picture, a .so, a jar): moved is no update
        with open(n, "rb") as f:
            if f.read() != old_bytes(n):
                changed.append(n)
        continue
    o = old(n)
    if o is None or strip(n, o) != strip(n, new):
        changed.append(n)

print("\n".join(changed))
sys.exit(0 if changed else 1)
