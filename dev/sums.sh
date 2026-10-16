#!/bin/bash
# dev/sums.sh <tag>: lists the SHA-256 of every file of that release in SHA256SUMS, signs it with the release key
# (dev/Sign.java; RELEASE_KEY, by default ~/.tv-remote-release/ed25519.pem) and attaches both. dev/release.sh runs it last;
# run it again after attaching or replacing any file of a release by hand, or the apps refuse the file that changed.
set -e
cd "$(dirname "$0")/.."
TAG=${1:?usage: dev/sums.sh <tag>}
KEY=${RELEASE_KEY:-$HOME/.tv-remote-release/ed25519.pem}
[ -f "$KEY" ] || { echo "release key missing ($KEY)"; exit 1; }
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
gh release download "$TAG" -D "$T" -p 'tv-*.apk' -p 'TV-Remote-*' -p versions.json
(cd "$T" && shasum -a 256 * > SHA256SUMS)
# the version numbers inside the signed text, so an old signed list cannot be passed off as a newer release
python3 -c 'import json,sys; v=json.load(open(sys.argv[1])); print("version "+" ".join("%s=%s"%(k,v[k]) for k in sorted(v)))' "$T/versions.json" >> "$T/SHA256SUMS"
java dev/Sign.java sign "$KEY" "$T/SHA256SUMS"
gh release upload "$TAG" "$T/SHA256SUMS" "$T/SHA256SUMS.sig" --clobber
cat "$T/SHA256SUMS"
