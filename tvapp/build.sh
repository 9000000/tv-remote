#!/bin/bash
# Builds tvapp/build/tv-setup.apk with the Android SDK command-line tools only (no Gradle).
#   ANDROID_HOME (default ~/Library/Android/sdk) must contain build-tools and a platform; JDK 11+ on PATH.
# The APK is signed with tvapp/signing.jks (created on first build, git-ignored; keep it to be able to ship updates).
set -e
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
JAR=$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)/android.jar
rm -rf build && mkdir -p build/classes build/gen build/dex
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml --java build/gen \
  --min-sdk-version 21 --target-sdk-version 34 --version-code "${VERSION_CODE:-1}" --version-name "${VERSION_NAME:-1.0}" build/res.zip
javac -nowarn -source 8 -target 8 -Xlint:-options -cp "$JAR" -d build/classes $(find src build/gen -name '*.java')
"$BT/d8" --min-api 21 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
[ -f signing.jks ] || keytool -genkeypair -keystore signing.jks -storepass tvsetup -keypass tvsetup -alias tvsetup \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tv-remote" >/dev/null 2>&1
"$BT/apksigner" sign --ks signing.jks --ks-pass pass:tvsetup --key-pass pass:tvsetup --out build/tv-setup.apk build/aligned.apk
"$BT/apksigner" verify build/tv-setup.apk && ls -l build/tv-setup.apk | awk '{print "built", $9, $5, "bytes"}'
