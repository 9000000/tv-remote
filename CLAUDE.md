# CLAUDE.md

A guide for agents (and people) changing this repo. User-facing docs: [README.md](README.md) (Vietnamese) and [README.en.md](README.en.md).

## What this is

Remote control for Android TV / Google TV with no server in between. Three clients, one shared web UI:

| Client | Code | Serves the web UI from |
|---|---|---|
| **TV Remote**, the Android phone app | `app/android` | its Manage screen (in-app HTTP core); the remote screen itself is native Kotlin |
| **TV Setup**, the app on the TV | `app/tv` | `RemoteService` on port 8765, for any browser (iPhone included), after a 6-digit pairing code |
| **Web on the computer** | `app/web` (Python, aiohttp) | `server.py` on `127.0.0.1:8765` |

The phone app and TV Setup speak ADB themselves (`app/shared/.../Adb.kt`) and push `scrcpy-server` (`app/shared/assets`) for video, touch and keys. The computer uses the `adb` binary, ffmpeg, and `androidtvremote2` for keys when ADB is off. The phone app also sends keys without ADB through the Android TV Remote protocol (`Atv.kt`, ports 6466/6467).

## Layout

```
app/shared/web/          THE web UI (index.html, style.css, js/*.js), used by all three clients
app/shared/src/.../core/ Kotlin shared by both apps: Adb, Tv (scrcpy session), Atv (keys without ADB), Manage (the /api core
                         for the web UI), Setup (Optimize TV), Tailscale, AdbPrompt ("Allow USB debugging?"), Errors, Meta
app/android/             phone app: MainActivity.kt holds nearly all of it (picker, remote screen, assistants, updates)
app/tv/                  TV Setup: MainActivity (the TV screen), RemoteService (web server), PairActivity, Stats, TrustReceiver
app/web/                 server.py (the computer's /api core), adbtools.py, tvconfig.py (tvs.json), cli/ (command line tools)
dev/                     release.sh, gen_icons.py
```

## Rules that matter

- **Every change reaches all three clients.** UI changes go in `app/shared/web` once. A new `/api` endpoint used by the page must exist in `app/web/server.py` (Python) **and** in the Kotlin cores (`Manage.kt`, plus `RemoteService` / the phone's Manage screen where they differ). The phone's native remote screen is separate code: mirror the change there by hand, or say clearly why it cannot be.
- The server stamps `<html data-core="mac|tv|phone">`; core-only markup uses `data-for="mac tv phone"`, JS checks `CORE`.
- **Vietnamese is the source language.** The web UI writes Vietnamese; `js/i18n.js` maps strings (and regexes) to English at render time, so every new user-visible string needs an entry there. Kotlin uses `t("vi", "en")`. README, CHANGELOG entries and release notes are written in both languages.
- **Users first:** make things automatic; show an error only after every fallback has failed, in words a person can act on (`Errors.kt`, `friendly()` in `js/core.js`). Choices look like buttons. Long waits show what is going on and can be cancelled.
- **Never commit** `cert.pem`, `key.pem`, `tvs.json`, `*.jks` (they pair with or sign for real devices). Never expose port 8765 to the Internet; away from home everything goes over Tailscale.
- **"Allow USB debugging?"** is only ever answered for the key whose fingerprint matches the asking device (`AdbPrompt.kt`, `adbtools.py`); never press Allow on an unknown key.
- Comments say why, not what; match the surrounding style (end-of-line comments aligned to column ~70).

## Build and run

- Apps: `app/android/build.sh`, `app/tv/build.sh` (JDK 17, Android SDK) → `app/*/build/*.apk`. The web UI is packed into both APKs as assets.
- Computer web: `venv/bin/python app/web/server.py` (first run: `start-mac.command` creates `venv`). For tests, run a second copy on another port with its own TV list so real TVs are untouched: `TVS_FILE=/tmp/x.json venv/bin/python app/web/server.py --port 8767`.

## Versions and releases

Three numbers, each bumped only when its part changes: `VERSION` (every release; the tag and the computer web), `app/android/VERSION`, `app/tv/VERSION`.

- A change in `app/shared` (Kotlin **or** web) changes both APKs, so bump both app versions.
- Steps: bump versions → add `## <VERSION> - <YYYY-MM-DD>` to `CHANGELOG.md` (line `Web x · app điện thoại y · app TV z`, then **Tiếng Việt** and **English** lists) → commit → push → `dev/release.sh` (builds, commits the APKs as "Build x", creates the GitHub release with `versions.json`). It needs `gh` logged in and both `signing.jks` files, and refuses when app code changed without a version bump.
- Both apps update themselves from GitHub releases (`update/Release.kt`).

## Testing

Test on emulators, not on real TVs. Two AVDs are used: `tvremote_gtv` (Google TV) and `tvremote_test` (phone).

- Boot the TV emulator **first** so it is `emulator-5554`; its ADB port 5555 is then reachable from the phone emulator as `10.0.2.2:5555`. Check `getprop ro.product.model` before installing anything: the serials swap when the boot order differs.
- Boot the TV emulator with `-prop ro.adb.secure=1` to get the real "Allow USB debugging?" prompt. The TV shows one prompt at a time and queues prompts of dead connections first.
- Emulator snapshots can bring back an old saved-TV list holding real TV addresses: before tapping anything, check `shared_prefs/tvs.xml` (`ips`) of `dev.tvremote.phone` (needs `adb root`) and reduce it to `10.0.2.2`.
- Tap by text (find the node in `uiautomator dump`), never by fixed coordinates.
- If `adb exec-out screencap` comes back blank on the phone emulator, use `adb emu screenrecord screenshot <file>`.
- Web UI: headless Chrome through the DevTools protocol against the test server above.

## Handy facts

- Downloader (AFTVnews) short codes, always pointing at the latest release: **2397346** → `tv-setup.apk`, **2973135** → `tv-remote.apk`. The phone app's step-by-step installer types 2397346 into Downloader.
- TV Setup keeps its state on the TV: `/data/local/tmp/tvr-disabled.txt` (apps Optimize TV turned off, for Undo), stats in `/sdcard/Android/data/dev.tvremote.setup/files/stats/`.
- Release download links: `https://github.com/HoangTran0410/tv-remote/releases/latest/download/<tv-setup|tv-remote>.apk`.
