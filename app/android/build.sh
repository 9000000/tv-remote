#!/bin/bash
# Builds app/android/build/tv-remote.apk (release, signed) with Gradle. Needs JDK 17+ and the Android SDK
# (ANDROID_HOME, default ~/Library/Android/sdk). The project can also be opened in Android Studio: open the "app" folder.
# The APK is signed with app/android/signing.jks (created on first build, git-ignored; keep it to be able to ship updates).
set -e
cd "$(dirname "$0")"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
[ -f signing.jks ] || keytool -genkeypair -keystore signing.jks -storepass tvremote -keypass tvremote -alias tvremote \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tv-remote" >/dev/null 2>&1
../gradlew -q -p .. :android:assembleRelease
cp build/outputs/apk/release/android-release.apk build/tv-remote.apk
ls -l build/tv-remote.apk | awk '{print "built", $9, $5, "bytes"}'
