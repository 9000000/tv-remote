#!/bin/bash
# Publishes a GitHub release for the version in VERSION: builds both APKs, takes the notes from that version's section
# in CHANGELOG.md, and uploads tv-remote.apk + tv-setup.apk (the names the apps look for when they check for updates).
#   1. put the new number in VERSION    2. add a "## <version> - <date>" section to CHANGELOG.md    3. commit, push
#   4. scripts/release.sh
# Needs the gh CLI logged in, and the two signing.jks files the earlier releases were signed with (or updates will not install).
set -e
cd "$(dirname "$0")/.."
VER=$(cat VERSION)
[ -f phoneapp/signing.jks ] && [ -f tvapp/signing.jks ] || { echo "signing.jks missing: an APK signed with a new key cannot update the installed apps"; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "commit your changes first"; exit 1; }
NOTES=$(awk -v v="## $VER " 'index($0, v) == 1 {on = 1; next} on && /^## / {exit} on' CHANGELOG.md)
[ -n "$NOTES" ] || { echo "CHANGELOG.md has no section for $VER"; exit 1; }
./phoneapp/build.sh && ./tvapp/build.sh
cp phoneapp/build/tv-remote.apk phoneapp/tv-remote.apk && cp tvapp/build/tv-setup.apk tvapp/tv-setup.apk
if [ -n "$(git status --porcelain)" ]; then git add phoneapp/tv-remote.apk tvapp/tv-setup.apk && git commit -q -m "Build $VER" && git push -q; fi
gh release create "v$VER" phoneapp/tv-remote.apk tvapp/tv-setup.apk --title "v$VER" --notes "$NOTES"
