#!/usr/bin/env python3
# Did an app's code change since a tag, beyond comments? release.sh bumps an app's number only then, so a path renamed in
# comments (dev/gen_icons.py -> dev/gen/gen_icons.py in every icon) does not send its users an update with nothing in it.
#   dev/release/code_changed.py <tag> <git pathspec>...     exit 0 = changed (prints the files), 1 = not
#   dev/release/code_changed.py --show <tag> <path>         the file as it was at the tag (release.sh: an app's last number)
# Not counted: *.md, build.sh (not in the APK), tests, and comments in Kotlin / Java / Gradle / XML / shell / Python (compare.py).
import sys
from compare import at, changed

if sys.argv[1] == "--show":
    b = at(sys.argv[2], sys.argv[3])
    if b is None:
        sys.exit(1)
    sys.stdout.write(b.decode("utf-8", "replace"))
    sys.exit(0)

c = changed(sys.argv[1], sys.argv[2:])
print("\n".join(c))
sys.exit(0 if c else 1)
