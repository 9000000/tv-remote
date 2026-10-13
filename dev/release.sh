#!/bin/bash
# Publishes a GitHub release. Three version numbers, each bumped only when its own part changed:
#   VERSION               the release itself (the tag) and the web UI on the computer: bump it for every release
#   app/android/VERSION   the phone app            app/tv/VERSION   the TV app
# Both APKs are attached to every release together with versions.json; each app reads its own number from that file,
# so an app that did not change is not offered a pointless update.
#
#   1. bump VERSION, and app/android/VERSION and/or app/tv/VERSION if that app (or app/shared) changed
#   2. add a "## <VERSION> - <date>" section to CHANGELOG.md        3. commit, push        4. dev/release.sh
# Needs the gh CLI logged in, and the two signing.jks files the earlier releases were signed with (or updates will not install).
set -e
cd "$(dirname "$0")/.."
VER=$(cat VERSION)
[ -f app/android/signing.jks ] && [ -f app/tv/signing.jks ] || { echo "signing.jks missing: an APK signed with a new key cannot update the installed apps"; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "commit your changes first"; exit 1; }
NOTES=$(awk -v v="## $VER " 'index($0, v) == 1 {on = 1; next} on && /^## / {exit} on' CHANGELOG.md)
[ -n "$NOTES" ] || { echo "CHANGELOG.md has no section for $VER"; exit 1; }
LAST=$(git describe --tags --abbrev=0 2>/dev/null || true)
for a in android tv; do                                               # changed code must come with a new number, or nobody gets it
  [ -n "$LAST" ] && [ -z "$SKIP_CHECK" ] || continue
  old=$(git show "$LAST:app/$a/VERSION" 2>/dev/null || true)
  if [ "$old" = "$(cat app/$a/VERSION)" ] && ! git diff --quiet "$LAST" -- "app/$a" app/shared ":(exclude)app/$a/*.apk"; then
    echo "app/$a (or app/shared) changed since $LAST but app/$a/VERSION is still $old: bump it, or run with SKIP_CHECK=1"; exit 1
  fi
done
./app/android/build.sh && ./app/tv/build.sh
cp app/android/build/tv-remote.apk app/android/tv-remote.apk && cp app/tv/build/tv-setup.apk app/tv/tv-setup.apk
if [ -n "$(git status --porcelain)" ]; then git add app/android/tv-remote.apk app/tv/tv-setup.apk && git commit -q -m "Build $VER" && git push -q; fi
printf '{"android": "%s", "tv": "%s", "web": "%s"}\n' "$(cat app/android/VERSION)" "$(cat app/tv/VERSION)" "$VER" > app/android/build/versions.json
gh release create "v$VER" app/android/tv-remote.apk app/tv/tv-setup.apk app/android/build/versions.json --title "v$VER" --notes "$NOTES"
