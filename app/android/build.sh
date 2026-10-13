#!/bin/bash
# Builds app/android/build/tv-remote.apk with the Android SDK command-line tools only (no Gradle).
#   ANDROID_HOME (default ~/Library/Android/sdk) must contain build-tools and a platform; JDK 11+ on PATH.
# The APK is signed with app/android/signing.jks (created on first build, git-ignored; keep it to be able to ship updates).
set -e
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT=$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)
JAR=$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)/android.jar
VER="${VERSION_NAME:-$(cat VERSION)}"                                 # this app's own version: bump it only when this app changes
CODE=$(echo "$VER" | awk -F. '{print $1 * 10000 + $2 * 100 + $3}')
rm -rf build && mkdir -p build/classes build/gen build/dex build/assets
cp ../shared/scrcpy-server-v5.0.1 build/assets/scrcpy-server.jar      # started on the TV for the picture and the input
"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml --java build/gen -A build/assets -0 jar \
  --min-sdk-version 21 --target-sdk-version 34 --version-code "$CODE" --version-name "$VER" build/res.zip
javac -nowarn -source 8 -target 8 -Xlint:-options -cp "$JAR" -d build/classes $(find src ../shared/src build/gen -name '*.java')
"$BT/d8" --min-api 21 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q -j ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
[ -f signing.jks ] || keytool -genkeypair -keystore signing.jks -storepass tvremote -keypass tvremote -alias tvremote \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tv-remote" >/dev/null 2>&1
"$BT/apksigner" sign --ks signing.jks --ks-pass pass:tvremote --key-pass pass:tvremote --out build/tv-remote.apk build/aligned.apk
"$BT/apksigner" verify build/tv-remote.apk && ls -l build/tv-remote.apk | awk '{print "built", $9, $5, "bytes"}'
