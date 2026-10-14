#!/bin/bash
# Builds app/tv/build/tv-setup.apk (release, signed) with Gradle. Needs JDK 17+ and the Android SDK
# (ANDROID_HOME, default ~/Library/Android/sdk). The project can also be opened in Android Studio: open the "app/gradle" folder.
# The APK is signed with app/tv/signing.jks (created on first build, git-ignored; keep it to be able to ship updates).
set -e
cd "$(dirname "$0")"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
[ -f signing.jks ] || keytool -genkeypair -keystore signing.jks -storepass tvsetup -keypass tvsetup -alias tvsetup \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tv-remote" >/dev/null 2>&1
../gradle/gradlew -q -p ../gradle :tv:assembleRelease
cp build/outputs/apk/release/tv-release.apk build/tv-setup.apk
ls -l build/tv-setup.apk | awk '{print "built", $9, $5, "bytes"}'
