#!/bin/bash
# Builds app/tv/build/tv-setup.apk (release, signed) with Gradle. Needs JDK 17+ and the Android SDK
# (ANDROID_HOME, default ~/Library/Android/sdk). The project can also be opened in Android Studio: open the "app/gradle" folder.
# The APK is signed with app/tv/signing.jks (created on first build, git-ignored; keep it to be able to ship updates).
set -e
cd "$(dirname "$0")"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [ ! -f signing.jks ]; then                                       # said out loud: a new key cannot update the installed apps
  keytool -genkeypair -keystore signing.jks -storepass tvsetup -keypass tvsetup -alias tvsetup \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tv-remote" >/dev/null 2>&1
  printf '\033[1;33m%s\033[0m\n' >&2 \
    "WARNING: app/tv/signing.jks was missing, a NEW signing key was made." \
    "  An APK signed with it cannot update the app already installed on phones and TVs (Android refuses another key)." \
    "  Fine for a first build or tests. For a release: rm app/tv/signing.jks and get the old key back: dev/release/keys.sh restore <file>"
fi
../gradle/gradlew -q -p ../gradle :tv:assembleRelease
cp build/outputs/apk/release/tv-release.apk build/tv-setup.apk
ls -l build/tv-setup.apk | awk '{print "built", $9, $5, "bytes"}'
