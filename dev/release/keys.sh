#!/bin/sh
# Every release key in one file locked with a password (dev/release/Keys.java), to keep away from this computer (Drive, USB stick):
#   dev/release/keys.sh backup [file]   -> tv-remote-keys-<date>.tvrkeys (asks a password twice)
#   dev/release/keys.sh list <file>     what is in it (asks the password: checks the copy opens)
#   dev/release/keys.sh restore <file>  puts each key back in place; a different key already there is kept, the copy written beside it
# The password is never stored: lose it and the file is useless. Never commit the file (.gitignore: *.tvrkeys).
set -e
cd "$(dirname "$0")/../.."
R="$HOME/.tv-remote-release"
cmd=${1:-}; [ $# -gt 0 ] && shift
# the admin tokens too: a lost one means a new worker secret and every tool re-pointed, not a lost update path, but a chore
places="ed25519.pem=$R/ed25519.pem ed25519-spare.pem=$R/ed25519-spare.pem launchers.key=$R/launchers.key crash-token=$R/crash-token themes-admin-token=$R/themes-admin-token android-signing.jks=app/android/signing.jks tv-signing.jks=app/tv/signing.jks home-signing.jks=app/home/signing.jks channels-signing.jks=app/channels/signing.jks games-signing.jks=app/games/signing.jks karaoke-signing.jks=app/karaoke/signing.jks"
case "$cmd" in
  backup)  exec java dev/release/Keys.java pack "${1:-tv-remote-keys-$(date +%Y-%m-%d).tvrkeys}" $places ;;
  list)    exec java dev/release/Keys.java list "${1:?which file?}" ;;
  restore) exec java dev/release/Keys.java unpack "${1:?which file?}" $places ;;
  *) sed -n '2,6p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
