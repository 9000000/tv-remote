#!/bin/bash
# Builds app/karaoke/build/tv-karaoke.apk (TV Karaoke, songs from YouTube queued from phones; release, signed) with Gradle. Needs JDK 17+ and the Android SDK.
# Signed with app/karaoke/signing.jks (created on first build, git-ignored; keep it to be able to ship updates).
set -e
cd "$(dirname "$0")"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [ ! -f signing.jks ]; then                                       # said out loud: a new key cannot update the installed app
  keytool -genkeypair -keystore signing.jks -storepass tvkaraoke -keypass tvkaraoke -alias tvkaraoke \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tv-remote" >/dev/null 2>&1
  printf '\033[1;33m%s\033[0m\n' >&2 \
    "WARNING: app/karaoke/signing.jks was missing, a NEW signing key was made." \
    "  An APK signed with it cannot update the TV Karaoke already installed on TVs (Android refuses another key)." \
    "  Fine for a first build or tests. For a release: rm app/karaoke/signing.jks and get the old key back: dev/release/keys.sh restore <file>"
fi
../gradle/gradlew -q -p ../gradle :karaoke:assembleRelease
cp build/outputs/apk/release/karaoke-release.apk build/tv-karaoke.apk
ls -l build/tv-karaoke.apk | awk '{print "built", $9, $5, "bytes"}'
