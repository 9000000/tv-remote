#!/bin/sh
# Every release key in one file locked with a password (dev/Keys.java), to keep away from this computer (Drive, USB stick):
#   dev/keys.sh backup [file]   -> tv-remote-keys-<date>.tvrkeys (asks a password twice)
#   dev/keys.sh list <file>     what is in it (asks the password: checks the copy opens)
#   dev/keys.sh restore <file>  puts each key back in place; a different key already there is kept, the copy written beside it
# The password is never stored: lose it and the file is useless. Never commit the file (.gitignore: *.tvrkeys).
set -e
cd "$(dirname "$0")/.."
R="$HOME/.tv-remote-release"
cmd=${1:-}; [ $# -gt 0 ] && shift
places="ed25519.pem=$R/ed25519.pem ed25519-spare.pem=$R/ed25519-spare.pem launchers.key=$R/launchers.key android-signing.jks=app/android/signing.jks tv-signing.jks=app/tv/signing.jks"
case "$cmd" in
  backup)  exec java dev/Keys.java pack "${1:-tv-remote-keys-$(date +%Y-%m-%d).tvrkeys}" $places ;;
  list)    exec java dev/Keys.java list "${1:?which file?}" ;;
  restore) exec java dev/Keys.java unpack "${1:?which file?}" $places ;;
  *) sed -n '2,6p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
