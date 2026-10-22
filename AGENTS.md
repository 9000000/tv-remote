# AGENTS.md

A guide for agents (and people) changing this repo. User-facing docs: [README.md](README.md) (English) and [README.vi.md](README.vi.md) (Vietnamese).
Why some code looks odd (measured bugs, VPNs, the TV's ADB questions): [dev/DECISIONS.md](dev/DECISIONS.md).

## What this is

Remote control for Android TV / Google TV with no server in between. Three apps and a web page, always called by these
names (README, code comments, the site; Vietnamese in brackets):

| Name | Code | `data-core` | What it is |
|---|---|---|---|
| **Phone app** (App điện thoại) | `app/android` | `phone` | Android app "TV Remote"; its remote screen is native Kotlin, its Manage screen is the web UI (in-app HTTP core) |
| **Computer app** (App máy tính) | `app/desktop` (Kotlin, JVM) | `pc` | "TV Remote" for Mac, Windows, Linux: `Server.kt` on `127.0.0.1:8765`, shown in its own window (the system's web view, `Window.kt`) |
| **TV app** (App TV) | `app/tv` | | the app on the TV, shown as "TV Setup" (keep that label: it is on installed TVs, Downloader codes and file names) |
| **TV web page** (Web của TV) | `app/tv` `RemoteService` | `tv` | the web UI the TV app serves on port 8765, for any browser (iPhone included), after a 6-digit pairing code |
| **TV Home** | `app/home` | | the project's own launcher (`dev.tvremote.home`), first in the launcher gallery; set up from every client over ADB |
| **TV Channels** | `app/channels` | | iptv-org's free channels on the TV (`dev.tvremote.channels`); knows nothing of the other apps: they reach it only by its links (`tvchannels://play/<id>`, `group/<id>`, `find/<name>`); installed and opened from every client over ADB (`core/ontv/Channels.kt`, `js/channels.js`) |
| **TV Games** | `app/games` | | retro games (libretro cores, GPL-3: `app/games/LICENSE`) and web games on the TV, played with phones as pads (`dev.tvremote.games`): its own server on 8780 + a QR, no ADB and no TV Setup needed to play; installed, opened and updated from every client over ADB like TV Channels (`core/ontv/Games.kt`, `OwnApp.kt`, `js/games.js`) |

What each can do, and diagrams of how they connect: README "Three apps and a web page" / README.vi "Ba app và một trang web".
Keep that table true when a feature lands on only some of them.

The Phone app and the TV app speak ADB themselves (`app/shared/.../Adb.kt`) and push `scrcpy-server` (`app/shared/assets`) for video, touch and keys. So does the Computer app, on the same core. The Phone app and the Computer app also send keys without ADB through the Android TV Remote protocol (`Atv.kt`, ports 6466/6467).

LG (webOS, `Lg.kt`, SSAP on 3000/3001) and Samsung (Tizen, `Samsung.kt`, 8001/8002) TVs are adapters behind the same
`Remote` interface as `Atv` (`Remote.kt`: `Remote.Kind` with its `caps`, `RemoteSteps` for scenes), found by `Ssdp.kt`.
Only the Phone app and the Computer app have them (the TV web page needs TV Setup, Android only). The page reads `kind` /
`caps` from `/api/state` (`applyKind()` in `js/core.js`): `data-needs="cap"` hides a part or greys a control, and a
`remote.json` entry's `needs` does the same for keys and menu entries on both sides. A new feature that needs ADB: give it
`needs: ["adb"]` / `data-needs="adb"`.

## Layout

```
app/shared/web/          THE web UI (index.html, css/*.css, js/*.js): the Computer app, the TV web page, the Phone app's Manage screen
app/shared/src/.../core/ Kotlin shared by the apps, one package per kind of work (Java helpers Meta / Caps / Playing / Screen stay
                         in `core`: TV Setup and app_process call them by name):
  remote/                one TV's own remote link, by brand, behind `Remote`: Atv (Android TV, keys without ADB), Lg, Samsung,
                         Roku, Ssdp (finding them), WebSocket, Inputs (HDMI and other sources)
  adb/                   Adb, AdbLink, AdbPrompt ("Allow USB debugging?"), Spake2 + AdbTls + Mdns (pairing and connecting through
                         Android 11+ Wireless debugging: SPAKE2 with BoringSSL's own M/N, TLS 1.3 through `app/shared/conscrypt`
                         ConscryptBackend, found by mDNS; Phone and Computer apps only, `Manage.canPair`), TvShell, Pkg
  picture/               Tv (scrcpy session, video/audio/control), Feeds, AudioOut (Opus playback), Downmix, SoundQueue
  web/                   Manage (the /api core for the web UI), HttpServer, WebPage, Invite, Qr, Transfer (Stop, no room), Hook
                         (Home Assistant / Shortcuts: `/api/hook`, its own token; TV Setup on 8765, the computer app on port+1 only while on)
  ontv/                  things done on the TV over ADB: Setup (Optimize TV; TV Home as the home screen; Bloat: brands' apps,
                         plus `docs/optimize/bloat.json` from the site, signed by `dev/sign/sign_bloat.py`, only adding to keep), SetupNeed / SetupUpdate, SetupFiles (reading TV Setup's
                         files: the shell, or TV Setup hands them over where the shell may not, a TCL on Android 11), RulesClient (TV Setup's
                         rules), Draw (strokes to TV Setup's layer), Notify, NowPlaying (through Playing), RemoteLabels (app
                         names, pictures and Caps, through Meta and Caps: Java in src/main/java, dexed into
                         assets/tvr-helper.jar by :shared:tvHelper, kept in git; app/shared/assets/README.md), ScreenOff, Sensors, Meminfo, Tailscale
  scenes/                Scenes (scenes + key names), Voice (spoken commands), RemoteSpec (remote.json)
  (core itself)          Errors, Diagnose (the self-check when a TV cannot be reached: one conclusion + fix, `/api/diagnose`, `js/diagnose.js`, phone `Diagnosing.kt`), WireLog (what the TVs said and the errors shown, keys blanked: About → Send the log,
                         `/api/wirelog`), Fleet (several saved TVs at once), SameTv (one TV saved under two addresses), B64, SelfSigned
app/android/.../phone/   phone app, one file per part, as extension functions on MainActivity: MainActivity (state, lifecycle),
                         Ui, Net, PickerScreen, RemoteScreen, Typing, InfoScreens, KeysOnly, AdbHelp, Media, Managing (the
                         web UI in a WebView), Updater, Listen + ListenService (🎧), Automation (timer, scenes, voice),
                         SceneStore, Headless + SceneActivity/SceneService (widget, tiles, shortcuts), PadMap (a game controller → keys; the page's is `js/gamepad.js`), AirMouse (the gyroscope as a pointer: a virtual HID mouse through scrcpy, `Tv.mouseOpen`; LG's pointer by `Remote.pointer`), Diagnosing (the self-check's screen), Tiles, RemoteWidget, ManyTvs (the list of TVs:
                         install / remove / restart on several at once), SetupBanner (a new TV Setup), Drawing (✏️ on the picture), NowPlayingBar
                         (the now playing strip, the TV's clipboard), IrRemote + Ir + IrCodes (the infrared remote on the phone's IR LED: screen,
                         encoders NEC / Samsung32 / SIRC / RC5 / RC6 / RCA / raw, `assets/ir/codes.json` made by `dev/gen/ir_codes.py` from Flipper-IRDB, CC0)
app/tv/                  TV Setup: MainActivity (the TV screen), RemoteService (web server), ManageActivity (the web UI on the
                         TV itself: a WebView in its own process, `?ontv=1`, D-pad focus in `js/ontv.js`), PairActivity, Stats, TrustReceiver, Scope (what a shared browser may do: full / remote / view),
                         Rules + Guard + RulesReceiver + WarnActivity (sleep timer, bedtime, app limits, the full memory notice), DnsWatch (turns off a
                         DNS filter that cut the TV off), DrawLayer + DrawReceiver (the drawing over every app), NotifyLayer + NotifyReceiver (the message card in a corner; `Notify.kt` in the shared code is the one place its corner / seconds are clamped), Nightly (2-5 am in standby: caches, `cmd package compile` of the apps used, `stats/nightly.json` for Health), KeepAdb (ADB debugging
                         back on when switched off, with WRITE_SECURE_SETTINGS granted through ADB)
app/home/                TV Home, the launcher: plain Views, no :shared (it would pack the web UI); HomeActivity (clock, dock,
                         grid, long-press menu), Widgets (system / usage / lunar / timer / note cards; usage, timer and CPU from TV
                         Setup's HomeInfo provider, which answers only TV Home by its signing key), Lunar (the lunar calendar, offline), Apps, Store (prefs), HomeReceiver (`--es set <b64 json>`, DUMP-protected, like
                         RulesReceiver). Its settings' shape is ONE file, `app/shared/src/home/kotlin/.../HomeConfig.kt`, compiled
                         into :shared, :desktop and :home; the clients' side is `core/ontv/Home.kt` + `/api/home` + `js/home.js`.
                         In the gallery it is kind "self": its APK is the latest release's `tv-home.apk`, pinned by SHA256SUMS
app/channels/            TV Channels: BrowseActivity (countries, categories, favourites, groups, search), PlayActivity (ExoPlayer, the
                         lineup, a dead stream falls to the next), ChannelsReceiver + Edit (favourites and groups edited from the clients over ADB: DUMP-protected, whole state by channel id, `Edit` cleans it; a country's channels go to a file the shell reads: `core/ontv/Channels.kt` `manage`, `/api/channels/manage`, `js/chan_manage.js`), Catalog (iptv-org's m3u, kept a day), Store (prefs), Find (a name -> a
                         channel), Pins (links and pinned shortcuts), Providers (more sources than iptv-org, e.g. VieON: each kind is
                         code here, their addresses / headers / on-off in `docs/channels/providers.json`, signed by `dev/sign/sign_providers.py`
                         with the launcher list's key, read from the site, else GitHub, else the APK's packed copy; Ed25519 from
                         `app/shared/src/sign`), Guide + GuideList (the programme guide from the providers with a "guide": TV360 by
                         name, VieON by its id; now / next on the player and the cards, the day's list). AndroidX (Media3, RecyclerView) here only; no :shared
app/games/               TV Games: LibraryActivity (the games, Suggestions from the catalog), GameActivity (LibretroDroid, save / load),
                         WebGameActivity (a web game in a WebView: keys, the Gamepad API and `window.tvpad`, WEBGAMES.md), GameServer (8780:
                         the pad page `assets/pad`, uploads, WebSocket slots 1-4), Systems (extension -> system -> core, pad layout), Catalog +
                         Cores (`docs/games/games.json`, signed by `dev/sign/sign_games.py` with the launcher list's key: cores downloaded by sha256 from
                         the `cores-1` release made by `dev/release/cores.sh`, suggested web games, free homebrew; fceumm packed as the offline fallback)
app/gradle/              the Gradle build of everything Kotlin (settings point at ../shared, ../android, ../tv, ../home, ../desktop); open it in Android Studio
app/desktop/             the computer app on the shared Kotlin core (JVM, no adb/Python/ffmpeg), the "pc" core: Server.kt
                         (the routes only the computer has) + Manage.kt; Pass.kt (a key per start, in the link the window and browser open: tests pass `TVR_PASS` and `?k=`); Updater.kt (updates itself from releases, swapped in by a script once it quits); Window.kt shows the page in the system's web view
                         (native/: webview, kept in git, native/README.md). `./gradlew :desktop:installDist`, then
                         `app/desktop/build/install/desktop/bin/desktop --port 8769 [--window]` (inside the repo: its tvs.json, keys in
                         .cache/keys). `./gradlew :desktop:packageApp` -> the app for this system + zip / tar.gz (own Java via jlink;
                         data in the user's app data folder); .github/workflows/desktop.yml builds and opens it on Mac, Windows, Linux
app/shared/tests/        node tests of the page's scripts, and the case tables the Kotlin tests read too (its README.md)
dev/                     test/ (emu.sh, smoke.py, layout.js, audit.js), gen/ (icons, wallpapers), shots/, sign/ (the site's signed lists), release/ : dev/README.md
```

## Rules that matter

- **Every change reaches the Phone app, the TV web page and the Computer app.** UI changes go in `app/shared/web` once. A new `/api` endpoint used by the page goes in `Manage.kt` (all three cores), or where only some cores have it: `app/desktop` `Server.kt`, `RemoteService`, the Phone app's Manage screen. The Phone app's native remote screen is separate code: mirror the change there by hand, or say clearly why it cannot be.
- **The remote screen is drawn from one file.** The keys and menu entries (More keys, the arrows row, the media rows, the Open on the TV / sleep timer / Scenes menus) live ONCE in `app/shared/web/remote.json`: id, icon, `t` (the id of its words in `i18n/<lang>.json`, `rkey.*`; `sub` the id of the line under it), a `key` (name in `Scenes.KEYS`) or a `do` action, optional `sub`, `cores` (`phone` / `web` only) and per-side `web` / `phone` overrides, each with a `why`. It is served at `/remote.json` on every core and packed into the Phone app. The web reads it in `js/remote_spec.js` + `js/remote.js` (handlers: `REMOTE_DO`); the Phone app in `RemoteSpec.kt` (shared) + `PhoneRemote.kt` (handlers: `phoneActions`). Add, move or reword an entry in the JSON only (icons: `dev/gen/gen_icons.py`; its words go in `i18n/vi.json` and `en.json`); a new `do` action needs a handler in both maps. Hand-laid on each side, outside the file: the top row, the D-pad, the number keys, the volume / channel pills and the 🎧 menu (`listen.js`). Checks: `node --test app/shared/tests/` (`remote.test.js`: schema, icons, key names, handlers on the page and in `PhoneRemote.kt`) and `RemoteSpecTest` in `:shared` and `:android` (`./gradlew :shared:testDebugUnitTest :android:testDebugUnitTest`).
- **The tabs are made of a few shapes** (`js/ui.js` lists them, `css/kit.css`): a card opens with its title (`h2`, or a
  `.card-head` with tools on the right; folded: `details.card` > `summary`) and spaces its own children, so nothing in it needs a
  margin; `h3` a group's title, `p.note` a line of explanation, `p.state` what is on now, `p.empty` an empty list, `.warnline`
  something to fix, `.list` > `.item`, `.line` a field and its button, `.actions` a card's buttons. Cards built in JS use
  `UI.card` / `UI.say`. **Two blues:** the full accent fill (`.primary`) is a button that does something (OK, Send, Install,
  Add); on or chosen (a tab, a choice in use, a switch that is on) is `.on` / `aria-selected`, a tint with a blue edge
  (`--sel`, one rule in `css/kit.css`; the phone's `setOn()` in Ui.kt), never the fill (`css.test.js`). **One size per kind of control**: a field and the button beside it `--ctl`, `.small` (`--ctl-sm`) for
  tools in a head and acts in a list, a choice among several a chip (`.places` / `.fchips`, `--chip`). No `style="…"` / `style:`
  in markup and no new `min-height` in px: a look needed twice is a class. Checked by `tests/ui.test.js`.
  **Icons:** write a title or button with its emoji as before (i18n keys keep it); `ICON` in `js/ui.js` draws it as a line
  icon from the sprite (a title's in its own hue, a tab's in a coloured tile, a button's in the button's colour). A new emoji
  in a title: add it to `ICON` (its path to `dev/gen/gen_icons.py`). Content (scene icons, app tiles, sentences, the remote) keeps emoji.
- The server stamps `<html data-core="pc|tv|phone">`; core-only markup uses `data-for="pc tv phone"`, JS checks `CORE`.
- **The page's state:** a `js/` file sets only the globals it declares; another file calls a function the owner offers
  (`pickGo`, `adbNow`, `appsStale`), and what a file keeps about one TV it forgets in its own `onTvChange()`. Fields of the
  page are written through `put()`. Both checked by `tests/page.test.js`.
- **Vietnamese is the source language; words go by id.** `app/shared/web/i18n/<lang>.json` holds every word of the page under an
  id (`area.words`): `vi.json` first, then the same ids in each language (`en.json` complete: it is every other one's fallback).
  A language is a file plus its line in `LANGS` (`js/i18n.js`). In `index.html` an element keeps its Vietnamese and names the id
  (`data-i18n`, `data-i18n-html`, `data-i18n-title|aria-label|placeholder|alt`); scripts write `T('id', { n })` (`{n}` in the
  words, `{ one, other }` for plurals); a script writes no words of its own (`tests/i18n.test.js` fails on one).
  Kotlin reads the same files (`core/Words.kt`): `T("id", "n" to 3)` in the app's language (`Words.lang`: the phone's choice,
  the TV's locale), `Words.get(r.lang, "id")` for words a page asked for (`r.lang`: the page's `tvr-lang` cookie). No new
  `t("vi", "en")` pairs. What the server sends comes in the page's language already; an English error from a TV or a library
  gets its words in `friendly()` (`js/core.js`).
  README.md (English) and README.vi.md (Vietnamese) are kept in step; CHANGELOG entries and release notes are written in both languages.
- **Users first:** make things automatic; show an error only after every fallback has failed, in words a person can act on (`Errors.kt`, `friendly()` in `js/core.js`). Choices look like buttons. Long waits show what is going on and can be cancelled.
- **Never commit** `cert.pem`, `key.pem`, `tvs.json`, `*.jks` (they pair with or sign for real devices). Never expose port 8765 to the Internet; away from home everything goes over Tailscale.
- **"Allow USB debugging?"** is only ever answered for the key whose fingerprint matches the asking device (`AdbPrompt.kt`); never press Allow on an unknown key.
- **Nothing in `docs/` is generated:** the intro page's demo and stories are GSAP timelines (`demo.js`, `stories.js`) with
  hand-edited CSS (`demo.css`, `stories.css`). (`app/*/res/drawable/ic_*.xml` are generated by `dev/gen/gen_icons.py`: edit that. The logo's rounded corners and
  `app/desktop/icon.icns|ico` come from `dev/gen/round_icons.py`.)
- Comments say why, not what; match the surrounding style (end-of-line comments aligned to column ~70).

## Build and run

- Apps: `app/android/build.sh`, `app/tv/build.sh` (JDK 17, Android SDK) → `app/*/build/*.apk`. The web UI is packed into both APKs as assets.
- Computer app: `cd app/gradle && ./gradlew :desktop:installDist`, then `app/desktop/build/install/desktop/bin/desktop [--window]` (inside the repo: its tvs.json, keys in .cache/keys; the page is read from `app/shared/web` on each load, so a web change needs only a reload). For tests, another port and its own TV list so real TVs are untouched: `TVS_FILE=/tmp/x.json TVR_KEYS=/tmp/keys TVR_PASS=testtest app/desktop/build/install/desktop/bin/desktop --port 8769`, opened at `http://127.0.0.1:8769/?k=testtest` (it prints the link; curl: header `x-tvr-pass`).
- Computer app for people: `./gradlew :desktop:packageApp` → `app/desktop/build/package/` (the app for this system + `TV-Remote-<os>-<arch>.zip|tar.gz`); a release gets them from `.github/workflows/desktop.yml`: Windows, Linux x64 / arm64, and one Mac app for both chips (`TV-Remote-mac.zip`, the arm64 and x64 builds joined by `app/desktop/universal.sh`).

## Automation (sleep timer, bedtime, app limits, scenes)

- The timer, bedtime and limits live **on the TV**, in TV Setup (`Rules.kt`, prefs), enforced by `Guard` (standby with keyevent 223,
  closing apps, the notice `WarnActivity`), so they work with every other device off. All three clients reach them the same way:
  `am broadcast -n dev.tvremote.setup/.RulesReceiver --es set <base64 json>` over ADB (`RulesClient.kt`);
  the receiver needs the shell's DUMP permission, so apps cannot call it. No receiver = TV Setup missing or older than the timer.
- Scenes are kept per client (computer: `tvs.json` "scenes"; TV Setup: prefs; phone: `SceneStore`), cleaned by `Scenes.clean`; `GET /api/scenes?download=1` is the backup file the page imports back.

## A feature that needs a newer TV Setup

The phone or computer can be newer than TV Setup on the TV. Add the feature and its first TV Setup version to `SetupNeed.MIN`
(and its name to `SetupNeed.name` and `SETUP_NEEDS` in `js/core.js`). Then check before starting it (`/api/setup/need?f=`,
`setupFor()` on the page, `withSetup()` on the phone) or throw `SetupNeed.Missing` where it finds out. Every client turns
that into one question with an Update / Install TV Setup button (`needSetup()` in `js/core.js`, `offerSetup()` on the phone).

## Versions and releases

Six numbers, each bumped only when its part changes: `VERSION` (every release; the tag and the computer app), `app/android/VERSION`, `app/tv/VERSION`,
`app/home/VERSION` (TV Home: `app/home` or `app/shared/src/home` changed; its key `app/home/signing.jks`, backed up by `dev/release/keys.sh`),
`app/channels/VERSION` (TV Channels: `app/channels` changed; `app/channels/signing.jks`, also in `dev/release/keys.sh`),
`app/games/VERSION` (TV Games: `app/games` or its packed `docs/games/games.json` changed; `app/games/signing.jks`, also in `dev/release/keys.sh`).

The apps are an alpha: the numbers stay 0.x until the user calls a release 1.0.0. Patch (the default) for fixes and small changes,
`BUMP=minor` for a release with new features, `BUMP=major` only when the user says so. The numbers restarted at 0.1.0 in October 2026
(they had reached 2.24.2): `Release.legacy()` counts 1.3.0 … 2.x as older than every new number, `versionCode` is 100000 + the number
so Android installs over the old APKs, and `release.sh` stops before 1.3.0 until `legacy()` is removed.

- A change in `app/shared` (Kotlin **or** web) changes both APKs, so both app numbers go up (release.sh sees it).
- **The user guide** (`docs/guide.html`, one `<article class="topic">` per topic) says which release each topic was checked
  against (`data-v`) and which app files can make it wrong (`data-src`, short paths: `web/`, `core/`, `phone/`, `tv/`, `pc/`).
  A change to what a topic describes: update the topic and set its `data-v="next"` (release.sh stamps the version).
  `dev/release/guide_stale.py` lists the topics whose files changed since; `release.sh` stops on them (`GUIDE_OK=1` goes on).
  A new topic needs both attributes and an entry in `docs/data/since.json` (`"since": "next"`, plus `setup` / `phone` / `pc` minimum versions where the feature needs a newer app; `release.sh` stamps "next" with the version and its date; `guide_stale.py` warns when missing). Point to another topic with a link (`<a href="#id" data-t="id">`), never “see … below”.
- Keep the next release's notes under `## Unreleased` at the top of `CHANGELOG.md` (**Tiếng Việt** and **English** lists), added to as features land.
- `dev/release/release.sh` does the rest: bumps `VERSION` and the number of each app whose code (or `app/shared`) changed since the last tag (beyond comments: `dev/release/code_changed.py`) (patch; `BUMP=minor`), turns `## Unreleased` into `## <VERSION> - <date>` with the line `Web x · app điện thoại y · app TV z`, commits, pushes, builds and creates the GitHub release with both APKs and `versions.json` (APKs are never committed). It needs `gh` logged in and both `signing.jks` files. Run again after a failure: nothing is bumped twice.
- The computer apps are not built by `release.sh`: when `app/desktop` or `app/shared` changed since the last tag it starts
  `.github/workflows/desktop.yml` with the new tag, which builds them on each system and attaches them (names without a version,
  so `releases/latest/download/TV-Remote-<os>-<arch>.zip` always works), and waits for it; otherwise it attaches the last
  release's archives again, and `versions.json` "web" (the number the computer app's Updater.kt reads) keeps theirs.
- Release only when the user says so, and keep GitHub Actions runs few: the workflow never runs on push; try things locally.
- Both apps update themselves from GitHub releases (`update/Release.kt`). Every app installs only a file listed in the
  release's `SHA256SUMS` with its SHA-256, signed (`SHA256SUMS.sig`, Ed25519) by a key in `Release.KEYS`; a release without
  them is not offered. `release.sh` runs `dev/release/sums.sh <tag>` last (after the computer apps are attached); run it again after
  replacing any file of a release by hand. The keys (`~/.tv-remote-release/`, never on GitHub) and their
  backup: `dev/README.md`.

## Testing

**A feature is done only once it has been run on every platform it touches, logic and UI**: the Phone app's native
screens (phone emulator), the Computer app's page (Chrome, 1100 and 400 px), the TV web page / Manage on the TV, TV Setup
(TV emulator). Logic: the action really happens on the TV, plus its errors, Stop, and an old TV Setup. UI: a screenshot
of each new or changed screen, looked at: no label cut or overflowing, no row wrapping, chips on one line, nothing
clipped. Unit tests alone do not count. Report what was tested where, and what could not be (e.g. HDMI needs a real TV).

Test on emulators, not on real TVs. Two AVDs are used: `tvremote_gtv` (Google TV, Android 14) and `tvremote_test` (phone, Android 14).
`dev/test/emu.sh` does the setup below; use it rather than doing it by hand:

```
dev/test/emu.sh create    # both AVDs, once (system images downloaded; arm64 or x86_64 to match the computer)
dev/test/emu.sh           # start (the TV first) + build both APKs + install each on the right emulator; again after every change
dev/test/emu.sh install   # build + install only, the emulators already running
dev/test/emu.sh reset     # the phone app's saved TVs reduced to 10.0.2.2
dev/test/emu.sh stop
```

Then in the phone app the TV is `10.0.2.2`; accept "Allow USB debugging?" on the TV emulator the first time (its window).
Env: `SECURE=0` (no prompt), `HEADLESS=1` (no windows), `TV_AVD`/`PHONE_AVD`/`TV_PORT`/`PHONE_PORT` (a second pair beside the first).
What it takes care of, for doing it by hand:

- Boot the TV emulator **first** so it is `emulator-5554`; its ADB port 5555 is then reachable from the phone emulator as `10.0.2.2:5555`. Check which serial is the TV (`android.software.leanback` in `pm list features`) before installing anything: the serials swap when the boot order differs.
- Boot the TV emulator with `-prop ro.adb.secure=1` to get the real "Allow USB debugging?" prompt. The TV shows one prompt at a time and queues prompts of dead connections first.
- Emulator snapshots can bring back an old saved-TV list holding real TV addresses: before tapping anything, check `shared_prefs/tvs.xml` (`ips`) of `dev.tvremote.phone` (needs `adb root`) and reduce it to `10.0.2.2` (`dev/test/emu.sh reset`).
- The Google TV image is arm64-only (runs 64-bit apps only); an x86_64 image (Intel computers) behaves differently there.
- Tap by text (find the node in `uiautomator dump`), never by fixed coordinates.
- If `adb exec-out screencap` comes back blank on the phone emulator, use `adb emu screenrecord screenshot <file>`.
- Web UI: headless Chrome through the DevTools protocol against the test server above.
- Unit tests (no device needed): `node --test app/shared/tests/`,
  `cd app/gradle && ./gradlew :shared:testDebugUnitTest :tv:testDebugUnitTest`. The voice tests exist on both sides
  (`voice.test.js`, `VoiceTest.kt`): keep `js/voice.js` and `Voice.kt` understanding the same sentences.
- Real network and real browsers: `dev/test/smoke.py tv` asks every `/api` route of TV Setup on the TV emulator through the emulator's
  own port redirect (adb forward hid a reply lost to a reset); `TVR_PASS=… dev/test/smoke.py URL` does the same for the computer app.
  `TVR_PASS=… node dev/test/layout.js` checks the layout in Chrome and WebKit (the Mac app's window and iPhones are WebKit). Run both
  after a change to HttpServer / the routes, or to the page's CSS.
- Before a release with UI changes: `node dev/test/audit.js` presses every button of the page (no TV, the demo's made-up one) and checks each
  screen it opens; read `/tmp/tvr-audit/index.html` and fix what it boxes ("same" is advice). `--only=auto` for one tab.
- Contrast: `node --test app/shared/tests/` includes `contrast.test.js` (WCAG ratios of the `css/base.css` tokens in both themes); `dev/test/contrast.py [URL]` (Chrome, default the test server on 8772) scans the rendered page, both themes x 1100/400 px x every tab + Settings, exit 1 under 4.5:1 (3:1 icons, large text). Run it after colour changes.
- The TV's sound: the Google TV emulator cannot run YouTube; press D-pad keys on the TV during a capture to get click sounds.
- The intro site's screenshots: `dev/shots/shots.py` (`docs/README.md`).

## Handy facts

- Downloader (AFTVnews) short codes, always pointing at the latest release: **2397346** → `tv-setup.apk`, **2973135** → `tv-remote.apk`, **4356026** → `tv-home.apk`, **8135635** → `tv-channels.apk` (TV Games: none yet). They are listed once in `docs/data/downloader.json`, signed by `dev/sign/sign_downloader.py` (the launcher list's key; run it after every edit and commit the `.sig` with it); the apps read it through `core/Downloader.kt` (site, else GitHub, else the copy kept, else `PACKED`, which `DownloaderTest` keeps equal to the file; an unsigned list is ignored: a code makes the TV install what it points at), and the page asks `/api/downloader`. The words take `{code}`, never the digits. The static pages of `docs/` and the READMEs keep the codes as text. Downloader registers the `downloader:` scheme: opening `downloader://<code>` (an intent, or the remote protocol's launch command) makes it fetch the APK and bring up the installer with nothing typed. Only send it once Downloader is known to be installed; a link the TV has no app for can make it drop the remote connection.
- TV Setup also runs on phones and tablets (needs ADB on port 5555 there). Tell a TV from a phone with the `android.software.leanback` feature, never the screen size alone: a 1080p TV is only `sw540dp`. Optimize TV refuses non-TVs (`Setup.tvOnly`).
- TV Setup keeps its state on the TV: `/data/local/tmp/tvr-disabled.txt` (apps Optimize TV turned off, for Undo), stats in `/sdcard/Android/data/dev.tvremote.setup/files/stats/`.
- The intro site https://tvremote.pages.dev is `docs/`, deployed by Cloudflare Pages on every push to main that touches it:
  its files, animations and screenshots in `docs/README.md`. `docs/llms.txt` tells AI agents about the app: keep it true.
- Release download links: `https://github.com/HoangTran0410/tv-remote/releases/latest/download/<tv-setup|tv-remote>.apk`.
