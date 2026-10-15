#!/bin/bash
# The window of the computer app: webview (github.com/webview/webview, MIT), the system's own web view behind one small
# C API (WebKit on Mac, WebView2 on Windows, WebKitGTK on Linux), built here and kept in git next to this script
# (<os>-<arch>/), so building the app needs no compiler. Window.kt loads it with JNA.
#   app/desktop/native/build.sh [mac|linux|windows]   Mac (arm64 + x64 in one file) here; Linux (arm64, x64) and Windows (x64) in Docker
set -euo pipefail
cd "$(dirname "$0")"
VER=0.12.0
SUM=e2c8d0bed3fcd13e624074448b7d3b1d0b0d03546a15839f17ee0013f2cba1db  # the tarball of that tag
src=.src/webview-$VER
if [ ! -d "$src" ]; then
  mkdir -p .src
  curl -fsSL "https://github.com/webview/webview/archive/refs/tags/$VER.tar.gz" -o .src/webview.tgz
  got=$(shasum -a 256 .src/webview.tgz | cut -d' ' -f1)
  if [ -n "$SUM" ] && [ "$got" != "$SUM" ]; then echo "webview $VER: unexpected download ($got)"; exit 1; fi
  echo "webview $VER sha256 $got"
  tar xzf .src/webview.tgz -C .src
fi
inc=$src/core/include
only=${1:-}
want() { [ -z "$only" ] || [ "$only" = "$1" ]; }

if want mac && [ "$(uname)" = Darwin ]; then
  mkdir -p mac
  flags="-O2 -arch arm64 -arch x86_64 -mmacosx-version-min=11.0 -DWEBVIEW_BUILD_SHARED -DWEBVIEW_SHARED -I$inc"
  clang++ $flags -std=c++11 -Wno-deprecated-literal-operator -c "$src/core/src/webview.cc" -o .src/webview.o
  clang $flags -fobjc-arc -c mac.m -o .src/mac.o
  clang++ -dynamiclib -arch arm64 -arch x86_64 -mmacosx-version-min=11.0 .src/webview.o .src/mac.o -framework Cocoa -framework WebKit \
    -install_name @rpath/libwebview.dylib -o mac/libwebview.dylib
  echo "built mac/libwebview.dylib"
fi

if want linux; then
  for a in arm64 amd64; do                                            # an older Ubuntu: the file then runs on newer systems too
    out=linux-$([ $a = amd64 ] && echo x64 || echo arm64)
    mkdir -p $out
    docker run --rm --platform linux/$a -v "$PWD:/n" -w /n ubuntu:22.04 bash -c "
      apt-get update -qq && apt-get install -y -qq g++ pkg-config libgtk-3-dev libwebkit2gtk-4.1-dev >/dev/null &&
      g++ -std=c++11 -O2 -shared -fPIC -DWEBVIEW_BUILD_SHARED -DWEBVIEW_SHARED -I$inc $src/core/src/webview.cc \
        \$(pkg-config --cflags --libs gtk+-3.0 webkit2gtk-4.1) -s -o $out/libwebview.so"
    echo "built $out/libwebview.so"
  done
fi

if want windows; then
  mkdir -p windows-x64                                                # WebView2 is part of Windows 10 and 11; its loader is built in
  w2=.src/webview2-1.0.1150.38                                        # the header webview is written against
  if [ ! -f $w2/build/native/include/WebView2.h ]; then
    curl -fsSL https://www.nuget.org/api/v2/package/Microsoft.Web.WebView2/1.0.1150.38 -o .src/webview2.zip
    mkdir -p $w2 && unzip -q -o .src/webview2.zip 'build/native/include/*' -d $w2
  fi
  docker run --rm -v "$PWD:/n" -w /n ubuntu:24.04 bash -c "
    apt-get update -qq && apt-get install -y -qq g++-mingw-w64-x86-64 >/dev/null &&
    x86_64-w64-mingw32-g++-posix -std=c++14 -O2 -shared -DWEBVIEW_BUILD_SHARED -DWEBVIEW_SHARED -I$inc -I$w2/build/native/include -I$src/compatibility/mingw/include $src/core/src/webview.cc \
      -static -ladvapi32 -lole32 -lshell32 -lshlwapi -luser32 -lversion -s -o windows-x64/webview.dll"
  echo "built windows-x64/webview.dll"
fi
