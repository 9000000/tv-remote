#!/bin/bash
# Builds app/channels/build/tv-channels.apk (TV Channels, the iptv-org player; release, signed) with Gradle. Needs JDK 17+ and the Android SDK.
# Signed with app/channels/signing.jks (created on first build, git-ignored; keep it to be able to ship updates).
set -e
cd "$(dirname "$0")"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [ ! -f signing.jks ]; then                                       # said out loud: a new key cannot update the installed app
  keytool -genkeypair -keystore signing.jks -storepass tvchannels -keypass tvchannels -alias tvchannels \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tv-remote" >/dev/null 2>&1
  printf '\033[1;33m%s\033[0m\n' >&2 \
    "WARNING: app/channels/signing.jks was missing, a NEW signing key was made." \
    "  An APK signed with it cannot update the TV Channels already installed on TVs (Android refuses another key)." \
    "  Fine for a first build or tests. For a release: rm app/channels/signing.jks and get the old key back: dev/keys.sh restore <file>"
fi
../gradle/gradlew -q -p ../gradle :channels:assembleRelease
cp build/outputs/apk/release/channels-release.apk build/tv-channels.apk
ls -l build/tv-channels.apk | awk '{print "built", $9, $5, "bytes"}'
