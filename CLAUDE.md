# CLAUDE.md

A guide for agents (and people) changing this repo. User-facing docs: [README.md](README.md) (Vietnamese) and [README.en.md](README.en.md).

## What this is

Remote control for Android TV / Google TV with no server in between. Three apps and a web page, always called by these
names (README, code comments, the site; Vietnamese in brackets):

| Name | Code | `data-core` | What it is |
|---|---|---|---|
| **Phone app** (App điện thoại) | `app/android` | `phone` | Android app "TV Remote"; its remote screen is native Kotlin, its Manage screen is the web UI (in-app HTTP core) |
| **Computer app** (App máy tính) | `app/desktop` (Kotlin, JVM) | `pc` | "TV Remote" for Mac, Windows, Linux: `Server.kt` on `127.0.0.1:8765`, shown in its own window (the system's web view, `Window.kt`) |
| **TV app** (App TV) | `app/tv` | | the app on the TV, shown as "TV Setup" (keep that label: it is on installed TVs, Downloader codes and file names) |
| **TV web page** (Web của TV) | `app/tv` `RemoteService` | `tv` | the web UI the TV app serves on port 8765, for any browser (iPhone included), after a 6-digit pairing code |

What each can do, and diagrams of how they connect: README "Ba app và một trang web" / README.en "Three apps and a web page".
Keep that table true when a feature lands on only some of them.

The Phone app and the TV app speak ADB themselves (`app/shared/.../Adb.kt`) and push `scrcpy-server` (`app/shared/assets`) for video, touch and keys. So does the Computer app, on the same core. The Phone app and the Computer app also send keys without ADB through the Android TV Remote protocol (`Atv.kt`, ports 6466/6467).

## Layout

```
app/shared/web/          THE web UI (index.html, style.css, js/*.js): the Computer app, the TV web page, the Phone app's Manage screen
app/shared/src/.../core/ Kotlin shared by both apps: Adb, Tv (scrcpy session, video/audio/control), Atv (keys without ADB),
                         Manage (the /api core for the web UI), Setup (Optimize TV), Tailscale, AdbPrompt ("Allow USB
                         debugging?"), Errors, RemoteLabels (app names, pictures and Caps from the TV, through Meta and Caps:
                         Java in src/main/java, dexed into assets/tvr-helper.jar by :shared:tvHelper, kept in git), Scenes (scenes + key names), RulesClient (talks to TV Setup's rules),
                         Voice (spoken commands), AudioOut (Opus playback, PCM downmix),
                         Fleet (several saved TVs at once), Draw (strokes to TV Setup's layer over the TV's screen)
app/android/.../phone/   phone app, one file per part, as extension functions on MainActivity: MainActivity (state, lifecycle),
                         Ui, Net, PickerScreen, RemoteScreen, Typing, InfoScreens, KeysOnly, AdbHelp, Media, Managing (the
                         web UI in a WebView), Updater, Listen + ListenService (🎧), Automation (timer, scenes, voice),
                         SceneStore, Headless + SceneActivity/SceneService (widget, tiles, shortcuts), Tiles, RemoteWidget, ManyTvs (the list of TVs:
                         install / remove / restart on several at once), SetupBanner (a new TV Setup), Drawing (✏️ on the picture)
app/tv/                  TV Setup: MainActivity (the TV screen), RemoteService (web server), PairActivity, Stats, TrustReceiver,
                         Rules + Guard + RulesReceiver + WarnActivity (sleep timer, bedtime, app limits), DnsWatch (turns off a
                         DNS filter that cut the TV off), DrawLayer + DrawReceiver (the drawing over every app)
app/gradle/              the Gradle build of everything Kotlin (settings point at ../shared, ../android, ../tv, ../desktop); open it in Android Studio
app/desktop/             the computer app on the shared Kotlin core (JVM, no adb/Python/ffmpeg), the "pc" core: Server.kt
                         (the routes only the computer has) + Manage.kt; Updater.kt (updates itself from releases, swapped in by a script once it quits); Window.kt shows the page in the system's web view
                         (native/: webview, built by native/build.sh and kept in git). `./gradlew :desktop:installDist`, then
                         `app/desktop/build/install/desktop/bin/desktop --port 8769 [--window]` (inside the repo: its tvs.json, keys in
                         .cache/keys). `./gradlew :desktop:packageApp` -> the app for this system + zip / tar.gz (own Java via jlink;
                         data in the user's app data folder); .github/workflows/desktop.yml builds and opens it on Mac, Windows, Linux
app/shared/tests/        node tests of the page's scripts, and the case tables (fleet_cases.json, tvshell_cases.json) the Kotlin tests read
dev/                     release.sh, gen_icons.py
```

## Rules that matter

- **Every change reaches the Phone app, the TV web page and the Computer app.** UI changes go in `app/shared/web` once. A new `/api` endpoint used by the page goes in `Manage.kt` (all three cores), or where only some cores have it: `app/desktop` `Server.kt`, `RemoteService`, the Phone app's Manage screen. The Phone app's native remote screen is separate code: mirror the change there by hand, or say clearly why it cannot be.
- The server stamps `<html data-core="pc|tv|phone">`; core-only markup uses `data-for="pc tv phone"`, JS checks `CORE`.
- **Vietnamese is the source language.** The web UI writes Vietnamese; `js/i18n.js` maps strings (and regexes) to English at render time, so every new user-visible string needs an entry there. Kotlin uses `t("vi", "en")`. README, CHANGELOG entries and release notes are written in both languages.
- **Users first:** make things automatic; show an error only after every fallback has failed, in words a person can act on (`Errors.kt`, `friendly()` in `js/core.js`). Choices look like buttons. Long waits show what is going on and can be cancelled.
- **Never commit** `cert.pem`, `key.pem`, `tvs.json`, `*.jks` (they pair with or sign for real devices). Never expose port 8765 to the Internet; away from home everything goes over Tailscale.
- **"Allow USB debugging?"** is only ever answered for the key whose fingerprint matches the asking device (`AdbPrompt.kt`); never press Allow on an unknown key.
- Comments say why, not what; match the surrounding style (end-of-line comments aligned to column ~70).

## Build and run

- Apps: `app/android/build.sh`, `app/tv/build.sh` (JDK 17, Android SDK) → `app/*/build/*.apk`. The web UI is packed into both APKs as assets.
- Computer app: `cd app/gradle && ./gradlew :desktop:installDist`, then `app/desktop/build/install/desktop/bin/desktop [--window]` (inside the repo: its tvs.json, keys in .cache/keys; the page is read from `app/shared/web` on each load, so a web change needs only a reload). For tests, another port and its own TV list so real TVs are untouched: `TVS_FILE=/tmp/x.json TVR_KEYS=/tmp/keys app/desktop/build/install/desktop/bin/desktop --port 8769`.
- Computer app for people: `./gradlew :desktop:packageApp` → `app/desktop/build/package/` (the app for this system + `TV-Remote-<os>-<arch>.zip|tar.gz`); a release gets them from `.github/workflows/desktop.yml`: Windows, Linux x64 / arm64, and one Mac app for both chips (`TV-Remote-mac.zip`, the arm64 and x64 builds joined by `app/desktop/universal.sh`).

## Automation (sleep timer, bedtime, app limits, scenes)

- The timer, bedtime and limits live **on the TV**, in TV Setup (`Rules.kt`, prefs), enforced by `Guard` (standby with keyevent 223,
  closing apps, the notice `WarnActivity`), so they work with every other device off. All three clients reach them the same way:
  `am broadcast -n dev.tvremote.setup/.RulesReceiver --es set <base64 json>` over ADB (`RulesClient.kt`);
  the receiver needs the shell's DUMP permission, so apps cannot call it. No receiver = TV Setup missing or older than 2.13.0.
- Scenes are kept per client (computer: `tvs.json` "scenes"; TV Setup: prefs; phone: `SceneStore`), cleaned by `Scenes.clean`; `GET /api/scenes?download=1` is the backup file the page imports back.

## A feature that needs a newer TV Setup

The phone or computer can be newer than TV Setup on the TV. Add the feature and its first TV Setup version to `SetupNeed.MIN`
(and its name to `SetupNeed.name` and `SETUP_NEEDS` in `js/core.js`). Then check before starting it (`/api/setup/need?f=`,
`setupFor()` on the page, `withSetup()` on the phone) or throw `SetupNeed.Missing` where it finds out. Every client turns
that into one question with an Update / Install TV Setup button (`needSetup()` in `js/core.js`, `offerSetup()` on the phone).

## Versions and releases

Three numbers, each bumped only when its part changes: `VERSION` (every release; the tag and the computer app), `app/android/VERSION`, `app/tv/VERSION`.

- A change in `app/shared` (Kotlin **or** web) changes both APKs, so bump both app versions.
- Steps: bump versions → add `## <VERSION> - <YYYY-MM-DD>` to `CHANGELOG.md` (line `Web x · app điện thoại y · app TV z`, then **Tiếng Việt** and **English** lists) → commit → push → `dev/release.sh` (builds, creates the GitHub release with both APKs and `versions.json`; APKs are never committed). It needs `gh` logged in and both `signing.jks` files, and refuses when app code changed without a version bump.
- The computer apps are not built by `release.sh`: when `app/desktop` or `app/shared` changed since the last tag it starts
  `.github/workflows/desktop.yml` with the new tag, which builds them on each system and attaches them (names without a version,
  so `releases/latest/download/TV-Remote-<os>-<arch>.zip` always works), and waits for it; otherwise it attaches the last
  release's archives again, and `versions.json` "web" (the number the computer app's Updater.kt reads) keeps theirs.
- Release only when the user says so, and keep GitHub Actions runs few: the workflow never runs on push; try things locally.
- Both apps update themselves from GitHub releases (`update/Release.kt`).

## Testing

Test on emulators, not on real TVs. Two AVDs are used: `tvremote_gtv` (Google TV) and `tvremote_test` (phone).

- Boot the TV emulator **first** so it is `emulator-5554`; its ADB port 5555 is then reachable from the phone emulator as `10.0.2.2:5555`. Check `getprop ro.product.model` before installing anything: the serials swap when the boot order differs.
- Boot the TV emulator with `-prop ro.adb.secure=1` to get the real "Allow USB debugging?" prompt. The TV shows one prompt at a time and queues prompts of dead connections first.
- Emulator snapshots can bring back an old saved-TV list holding real TV addresses: before tapping anything, check `shared_prefs/tvs.xml` (`ips`) of `dev.tvremote.phone` (needs `adb root`) and reduce it to `10.0.2.2`.
- Tap by text (find the node in `uiautomator dump`), never by fixed coordinates.
- If `adb exec-out screencap` comes back blank on the phone emulator, use `adb emu screenrecord screenshot <file>`.
- Web UI: headless Chrome through the DevTools protocol against the test server above.
- Unit tests (no device needed): `node --test app/shared/tests/`,
  `cd app/gradle && ./gradlew :shared:testDebugUnitTest :tv:testDebugUnitTest`. The voice tests exist on both sides
  (`voice.test.js`, `VoiceTest.kt`): keep `js/voice.js` and `Voice.kt` understanding the same sentences.
- The TV's sound: the Google TV emulator cannot run YouTube; press D-pad keys on the TV during a capture to get click sounds.

## Handy facts

- Downloader (AFTVnews) short codes, always pointing at the latest release: **2397346** → `tv-setup.apk`, **2973135** → `tv-remote.apk`. Downloader registers the `downloader:` scheme: opening `downloader://2397346` (an intent, or the remote protocol's launch command) makes it fetch the APK and bring up the installer with nothing typed. Only send it once Downloader is known to be installed; a link the TV has no app for can make it drop the remote connection.
- TV Setup also runs on phones and tablets (needs ADB on port 5555 there). Tell a TV from a phone with the `android.software.leanback` feature, never the screen size alone: a 1080p TV is only `sw540dp`. Optimize TV refuses non-TVs (`Setup.tvOnly`).
- TV Setup keeps its state on the TV: `/data/local/tmp/tvr-disabled.txt` (apps Optimize TV turned off, for Undo), stats in `/sdcard/Android/data/dev.tvremote.setup/files/stats/`.
- Release download links: `https://github.com/HoangTran0410/tv-remote/releases/latest/download/<tv-setup|tv-remote>.apk`.
