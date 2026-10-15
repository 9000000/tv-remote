#!/bin/bash
# One TV Remote.app for both kinds of Mac: jpackage only builds for the chip it runs on, so the release workflow builds an
# arm64 and an x64 app and this joins them. Every Mach-O file (the launcher, Java's libraries, the window's library) is
# joined with lipo; any other file must be the same in both, and the ones that are not are listed (the arm64 copy is kept).
# Expected: the signatures (made again here), desktop.jar (build times), Java's lib/modules (only the platform each module
# names in its module-info, "macos-aarch64" or "macos-amd64", which nothing checks at run time: the classes are the same).
#   app/desktop/universal.sh <arm64 TV Remote.app> <x64 TV Remote.app> <out TV Remote.app>
set -e
arm=$(cd "$1" && pwd); x64=$(cd "$2" && pwd); out=$3
rm -rf "$out"; ditto "$arm" "$out"; out=$(cd "$out" && pwd)
cd "$arm"
joined=0
while IFS= read -r -d '' f; do
  o="$x64/$f"
  if [ ! -f "$o" ]; then echo "only in arm64: $f"; continue; fi
  if file -b "$f" | grep -q "Mach-O"; then
    if lipo -info "$f" 2>/dev/null | grep -q "x86_64"; then continue; fi   # already both (native/mac/libwebview.dylib)
    lipo -create "$f" "$o" -output "$out/$f"; joined=$((joined + 1))
  elif ! cmp -s "$f" "$o"; then
    echo "differs, the arm64 copy kept: $f"
  fi
done < <(find . -type f -print0)
(cd "$x64" && find . -type f) | while IFS= read -r f; do [ -e "$arm/$f" ] || echo "only in x64: $f"; done
echo "joined $joined Mach-O files"
codesign --force --deep --sign - "$out"                               # each lipo'd file lost its signature
