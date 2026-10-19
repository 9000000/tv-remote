#!/bin/bash
# Publishes a GitHub release. Three version numbers, each bumped only when its own part changed:
#   VERSION               the release itself (the tag) and the computer app: bump it for every release (the computer app
#                         is only built again when app/desktop or app/shared changed; versions.json "web" says which it is)
#   app/android/VERSION   the phone app            app/tv/VERSION   the TV app
# Both APKs are attached to every release together with versions.json (the computer apps follow, from GitHub Actions); each app reads its own number from that file,
# so an app that did not change is not offered a pointless update.
#
#   1. write the notes under "## Unreleased" in CHANGELOG.md, commit        2. dev/release.sh
# It bumps the numbers itself: VERSION when it is still the last tag's, an app's when that app (or app/shared) changed since
# the last tag and its number did not (SKIP_CHECK=1: the apps' numbers are left alone). Which part goes up:
#   patch (default)   x.y.z+1    fixes and small changes
#   BUMP=minor        x.y+1.0    a release with new features
#   BUMP=major        x+1.0.0    only when the user says so: 0.x is the alpha, 1.0.0 the first release for everyone
# The numbers restarted at 0.1.0 in October 2026 (they had run up to 2.24.2 before anyone used the apps).
# "## Unreleased" becomes "## <VERSION> - <date>" with the line of the three numbers; then it commits that and pushes.
# Run again after a failure: nothing is bumped twice (VERSION is no longer the last tag's, and its section is there).
# Needs the gh CLI logged in, and the two signing.jks files the earlier releases were signed with (or updates will not install).
# Last it lists every file's SHA-256 in SHA256SUMS and signs that with the release key (dev/Sign.java; RELEASE_KEY, by default
# ~/.tv-remote-release/ed25519.pem): the apps install nothing a release does not list there, signed by a key in Release.KEYS.
set -e
cd "$(dirname "$0")/.."
KEY=${RELEASE_KEY:-$HOME/.tv-remote-release/ed25519.pem}
[ -f "$KEY" ] || { echo "release key missing ($KEY): without SHA256SUMS.sig no app takes the update"; exit 1; }
grep -q "$(java dev/Sign.java pub "$KEY")" app/shared/src/main/kotlin/dev/tvremote/update/Release.kt || { echo "$KEY is not one of Release.KEYS"; exit 1; }
[ -f app/android/signing.jks ] && [ -f app/tv/signing.jks ] || { echo "signing.jks missing: an APK signed with a new key cannot update the installed apps"; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "commit your changes first"; exit 1; }
# the user guide: a topic whose app files changed since the release it was checked against may now be wrong
if [ -z "$GUIDE_OK" ] && [ -n "$(dev/guide_stale.py -q)" ]; then
  dev/guide_stale.py
  echo "update those topics in docs/guide.html (or check they are still right) and set their data-v=\"next\"; GUIDE_OK=1 releases anyway"; exit 1
fi
LAST=$(git describe --tags --abbrev=0 2>/dev/null || true)
next() { python3 -c 'import sys; a = [int(x) for x in sys.argv[1].split(".")]; i = {"major": 0, "minor": 1}.get(sys.argv[2], 2); a[i] += 1; a[i + 1:] = [0] * (2 - i); print(".".join(map(str, a)))' "$1" "${BUMP:-patch}"; }
if [ -n "$LAST" ]; then
  [ "$(cat VERSION)" = "${LAST#v}" ] && next "$(cat VERSION)" > VERSION
  for a in android tv; do                                             # changed code must come with a new number, or nobody gets it
    [ -z "$SKIP_CHECK" ] || continue
    old=$(git show "$LAST:app/$a/VERSION" 2>/dev/null || true)
    if [ -n "$old" ] && [ "$old" = "$(cat app/$a/VERSION)" ] && ! git diff --quiet "$LAST" -- "app/$a" app/shared ":(exclude)app/$a/*.apk" ":(exclude)app/shared/tests"; then
      next "$old" > "app/$a/VERSION"
    fi
  done
fi
# Release.legacy() takes 1.3.0 … 2.x for the numbers before the restart: past 1.2 the new ones would look old
for v in VERSION app/android/VERSION app/tv/VERSION; do
  if python3 -c 'import sys; a = [int(x) for x in open(sys.argv[1]).read().split(".")]; sys.exit(0 if a[0] >= 2 or (a[0] == 1 and a[1] >= 3) else 1)' "$v" &&
     grep -q "fun legacy" app/shared/src/main/kotlin/dev/tvremote/update/Release.kt; then
    echo "$v reaches $(cat $v): first remove Release.legacy() (and its tests), the numbers from before the restart at 0.1.0"; git checkout -- VERSION app/android/VERSION app/tv/VERSION; exit 1
  fi
done
VER=$(cat VERSION)
if ! grep -q "^## $VER " CHANGELOG.md; then
  grep -q "^## Unreleased$" CHANGELOG.md || { echo "CHANGELOG.md: write this release's notes under a \"## Unreleased\" heading"; git checkout -- VERSION app/android/VERSION app/tv/VERSION; exit 1; }
  python3 - "$VER" "$(date +%F)" "$(cat app/android/VERSION)" "$(cat app/tv/VERSION)" <<'PY'
import sys
v, day, phone, tv = sys.argv[1:]
s = open("CHANGELOG.md").read().replace("## Unreleased\n", f"## {v} - {day}\n\nWeb {v} · app điện thoại {phone} · app TV {tv}\n", 1)
open("CHANGELOG.md", "w").write(s)
PY
fi
# the guide's topics checked against main ("next") now describe this release
python3 - "$VER" <<'PY'
import sys
s = open("docs/guide.html", encoding="utf-8").read()
open("docs/guide.html", "w", encoding="utf-8").write(s.replace('data-v="next"', f'data-v="{sys.argv[1]}"'))
PY
python3 dev/stamp_since.py "$VER" "$(date +%F)"          # docs/data/since.json: "next" -> $VER, with its date
NOTES=$(awk -v v="## $VER " 'index($0, v) == 1 {on = 1; next} on && /^## / {exit} on' CHANGELOG.md)
[ -n "$NOTES" ] || { echo "CHANGELOG.md has no section for $VER"; exit 1; }
if [ -n "$(git status --porcelain)" ]; then
  git commit -qam "Release v$VER (app điện thoại $(cat app/android/VERSION), app TV $(cat app/tv/VERSION))"
  echo "numbers: web $VER, phone $(cat app/android/VERSION), TV $(cat app/tv/VERSION)"
fi
git push -q origin HEAD                                               # the release is made from what GitHub has
# the computer app is built again (on GitHub, each system) only when its code changed; otherwise the last release's archives
# are attached again and "web" (what the computer app reads) keeps their version, so nobody is offered the same app again
# NO_DESKTOP=1: only the APKs, no GitHub Actions run (its minutes are limited). Changes are counted from the release that
# last built the computer app, so what a NO_DESKTOP release skipped is built by the next one
DESK=$VER
if [ -n "$LAST" ]; then
  BUILT=$(gh release download "$LAST" -p versions.json -O - | python3 -c 'import json, sys; d = json.load(sys.stdin); print(d["web"])')
  if [ -n "$NO_DESKTOP" ] || git diff --quiet "v$BUILT" -- app/desktop app/shared ":(exclude)app/shared/tests"; then DESK=$BUILT; fi
fi
./app/android/build.sh && ./app/tv/build.sh
printf '{"android": "%s", "tv": "%s", "web": "%s"}\n' "$(cat app/android/VERSION)" "$(cat app/tv/VERSION)" "$DESK" > app/android/build/versions.json
D=https://github.com/HoangTran0410/tv-remote/releases/latest/download
NOTES="$NOTES

**Tải về / Download**

| | |
|---|---|
| TV (TV Setup) | [tv-setup.apk]($D/tv-setup.apk) · Downloader \`2397346\` |
| Android | [tv-remote.apk]($D/tv-remote.apk) · Downloader \`2973135\` |
| Mac (Apple Silicon + Intel) | [TV-Remote-mac.zip]($D/TV-Remote-mac.zip) |
| Windows 10/11 | [TV-Remote-windows-x64.zip]($D/TV-Remote-windows-x64.zip) |
| Linux x64 / arm64 | [TV-Remote-linux-x64.tar.gz]($D/TV-Remote-linux-x64.tar.gz) · [TV-Remote-linux-arm64.tar.gz]($D/TV-Remote-linux-arm64.tar.gz) |"
# the APKs live only in releases, never in git
gh release create "v$VER" app/android/build/tv-remote.apk app/tv/build/tv-setup.apk app/android/build/versions.json --title "v$VER" --notes "$NOTES" --latest   # by date: GitHub would still call 2.24.2 the latest
if [ "$DESK" != "$VER" ]; then
  echo "computer app not built: the $DESK archives attached again"
  T=$(mktemp -d); gh release download "$LAST" -p 'TV-Remote-*' -D "$T" && gh release upload "v$VER" "$T"/TV-Remote-*; rm -rf "$T"
else
  # built on each system by .github/workflows/desktop.yml, which attaches them: waited for, so the release is only called
  # done once every download link works
  echo "building the computer apps (.github/workflows/desktop.yml)..."
  START=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  gh workflow run desktop.yml -f tag="v$VER"
  for i in $(seq 30); do RUN=$(gh run list --workflow desktop.yml --event workflow_dispatch -L 5 --json databaseId,createdAt -q ".[] | select(.createdAt >= \"$START\") | .databaseId" | head -1); [ -n "$RUN" ] && break; sleep 5; done
  [ -n "$RUN" ] || { echo "the desktop workflow did not start: gh workflow run desktop.yml -f tag=v$VER"; exit 1; }
  gh run watch "$RUN" --exit-status >/dev/null || { echo "a computer app failed to build: gh run view $RUN --log-failed"; exit 1; }
fi
dev/sums.sh "v$VER"                                                   # the signed list of every file, last: until it is there no app offers this release
gh release view "v$VER" --json assets -q '.assets[].name'
echo; dev/churn.sh || true                                           # how settled the /api is: says when 1.0 is worth thinking about
