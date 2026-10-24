#!/bin/bash
# Test clips for TV Home's video / GIF backgrounds, made once and kept in .cache/media (prints the folder).
#   dev/test/media.sh
# One encode at a time, 2 threads each, through light.sh. The length is the SOURCE's (duration=8), never `-t` after it: palettegen
# waits for the end of its input, so an endless testsrc2 cut by -t grows in memory until the system kills it, which with
# full-thread encodes beside it froze the computer (dev/DECISIONS.md "Tests that froze the computer"). A clip already there is kept.
set -eu
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/.cache/media"; mkdir -p "$OUT"
command -v ffmpeg >/dev/null || { echo "Missing ffmpeg (brew install ffmpeg)"; exit 1; }
make() {   # file, then ffmpeg arguments after the input
  local f="$1" src="$2"; shift 2
  [ -s "$OUT/$f" ] && return
  "$ROOT/dev/test/light.sh" ffmpeg -loglevel error -y -threads 2 -f lavfi -i "$src:duration=8" "$@" "$OUT/$f.part.${f##*.}"
  mv "$OUT/$f.part.${f##*.}" "$OUT/$f"
}
X264=(-c:v libx264 -preset veryfast -threads 2 -pix_fmt yuv420p)
make a_1080p60_12M.mp4 "testsrc2=size=1920x1080:rate=60" "${X264[@]}" -profile:v high -b:v 12M -maxrate 12M -bufsize 24M -bf 3 -g 120
make b_1080p30_4M.mp4  "testsrc2=size=1920x1080:rate=30" "${X264[@]}" -profile:v main -b:v 4M -maxrate 4M -bufsize 8M -bf 0 -g 60
make c_720p30_2M.mp4   "testsrc2=size=1280x720:rate=30"  "${X264[@]}" -profile:v main -b:v 2M -maxrate 2M -bufsize 4M -bf 0 -g 60
make d_1080p30_gop300.mp4 "testsrc2=size=1920x1080:rate=30" "${X264[@]}" -profile:v main -b:v 4M -maxrate 4M -bufsize 8M -bf 0 -g 300
make g_small.gif "testsrc2=size=480x270:rate=15" -vf "split[a][b];[a]palettegen[p];[b][p]paletteuse"
make g_big.gif   "testsrc2=size=800x450:rate=15" -vf "split[a][b];[a]palettegen[p];[b][p]paletteuse"
echo "$OUT"
