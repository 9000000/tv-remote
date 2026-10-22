#!/bin/bash
# A fake GitHub "latest release" on 127.0.0.1:8099 carrying tv-home.apk, signed with a throwaway key: the update and the
# launcher gallery tried end to end without a real release (as .github/workflows/desktop.yml does for the computer app).
#   dev/test/fakegh.sh <tv-home.apk> <its version>
# then start the computer app with what it prints (TVR_RELEASES, TVR_RELEASE_KEY, TVR_LAUNCHERS). Ctrl-C stops the server.
# Everything goes in .cache/fakegh (ignored by git); the key there is only for this test, never a real one.
set -e
cd "$(dirname "$0")/../.."
APK=${1:?usage: dev/test/fakegh.sh <tv-home.apk> <version>}; VER=${2:?which version?}
C=.cache/fakegh; R=$C/www; KEYF=$C/test-key.pem; U=http://127.0.0.1:8099
rm -rf "$R"; mkdir -p "$R/repos/HoangTran0410/tv-remote/releases" "$R/launchers"
[ -f "$KEYF" ] || java dev/release/Sign.java new "$KEYF" > "$C/test-pub.txt"
cp "$APK" "$R/tv-home.apk"
echo "{\"home\": \"$VER\"}" > "$R/versions.json"
(cd "$R" && shasum -a 256 tv-home.apk versions.json > SHA256SUMS && echo "version home=$VER" >> SHA256SUMS)
java dev/release/Sign.java sign "$KEYF" "$R/SHA256SUMS"
a() { echo "{\"name\": \"$1\", \"browser_download_url\": \"$U/$1\"}"; }
echo "{\"tag_name\": \"v9.9.9\", \"body\": \"\", \"assets\": [$(a tv-home.apk), $(a versions.json), $(a SHA256SUMS), $(a SHA256SUMS.sig)]}" \
  > "$R/repos/HoangTran0410/tv-remote/releases/latest"
cp docs/launchers/* "$R/launchers/"
echo "TVR_RELEASES=$U TVR_RELEASE_KEY=$(cat "$C/test-pub.txt") TVR_LAUNCHERS=$U/launchers/ app/desktop/build/install/desktop/bin/desktop --port 8769"
cd "$R" && exec python3 -m http.server 8099 --bind 127.0.0.1
