#!/bin/bash
# Builds app/tvgames/build/tv-games.apk (TV Games, the NES and web games with phones as pads; release, signed) with Gradle. Needs JDK 17+ and the Android SDK.
# Signed with app/tvgames/signing.jks (created on first build, git-ignored; keep it to be able to ship updates).
set -e
cd "$(dirname "$0")"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [ ! -f signing.jks ]; then                                       # said out loud: a new key cannot update the installed app
  keytool -genkeypair -keystore signing.jks -storepass tvgames -keypass tvgames -alias tvgames \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tv-remote" >/dev/null 2>&1
  printf '\033[1;33m%s\033[0m\n' >&2 \
    "WARNING: app/tvgames/signing.jks was missing, a NEW signing key was made." \
    "  An APK signed with it cannot update the TV Games already installed on TVs (Android refuses another key)." \
    "  Fine for a first build or tests. For a release: rm app/tvgames/signing.jks and get the old key back: dev/release/keys.sh restore <file>"
fi
../gradle/gradlew -q -p ../gradle :tvgames:assembleRelease
cp build/outputs/apk/release/tvgames-release.apk build/tv-games.apk
ls -l build/tv-games.apk | awk '{print "built", $9, $5, "bytes"}'
