#!/bin/bash
# Publishes a GitHub release. Three version numbers, each bumped only when its own part changed:
#   VERSION               the release itself (the tag) and the web UI on the computer: bump it for every release
#   app/android/VERSION   the phone app            app/tv/VERSION   the TV app
# Both APKs are attached to every release together with versions.json (the computer apps follow, from GitHub Actions); each app reads its own number from that file,
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
printf '{"android": "%s", "tv": "%s", "web": "%s"}\n' "$(cat app/android/VERSION)" "$(cat app/tv/VERSION)" "$VER" > app/android/build/versions.json
D=https://github.com/HoangTran0410/tv-remote/releases/latest/download
NOTES="$NOTES

**Tải về / Download**

| | |
|---|---|
| TV (TV Setup) | [tv-setup.apk]($D/tv-setup.apk) · Downloader \`2397346\` |
| Android | [tv-remote.apk]($D/tv-remote.apk) · Downloader \`2973135\` |
| Mac (Apple Silicon M1–M4) | [TV-Remote-mac-arm64.zip]($D/TV-Remote-mac-arm64.zip) |
| Mac (Intel) | [TV-Remote-mac-x64.zip]($D/TV-Remote-mac-x64.zip) |
| Windows 10/11 | [TV-Remote-windows-x64.zip]($D/TV-Remote-windows-x64.zip) |
| Linux x64 / arm64 | [TV-Remote-linux-x64.tar.gz]($D/TV-Remote-linux-x64.tar.gz) · [TV-Remote-linux-arm64.tar.gz]($D/TV-Remote-linux-arm64.tar.gz) |"
# the APKs live only in releases, never in git
gh release create "v$VER" app/android/build/tv-remote.apk app/tv/build/tv-setup.apk app/android/build/versions.json --title "v$VER" --notes "$NOTES"
# the computer apps are built on each system by .github/workflows/desktop.yml, which the new release starts and which
# attaches them to it: wait for it, so the release is only called done once every download link works
echo "waiting for the computer apps (.github/workflows/desktop.yml)..."
for i in $(seq 30); do RUN=$(gh run list --workflow desktop.yml --event release -L 5 --json databaseId,headBranch -q ".[] | select(.headBranch == \"v$VER\") | .databaseId" | head -1); [ -n "$RUN" ] && break; sleep 5; done
[ -n "$RUN" ] || { echo "the desktop workflow did not start: gh workflow run desktop.yml, then gh release upload v$VER <archives>"; exit 1; }
gh run watch "$RUN" --exit-status >/dev/null || { echo "a computer app failed to build: gh run view $RUN --log-failed"; exit 1; }
gh release view "v$VER" --json assets -q '.assets[].name'
