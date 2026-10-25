# app/shared: the shared code: the web UI (`web/`), the Kotlin core (`src/`) and the page tests

Read with the root [AGENTS.md](../../AGENTS.md) (rules, build, tests, releases apply here too). What is where:

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
  web/                   Manage (the /api core for the web UI), HttpServer, WebPage, Invite, Qr, Transfer (Stop, no room), Hook, Telegram + TelegramHttp (the Telegram bot: long polling, runs in TV Setup; commands, alerts via `RemoteService.alert(kind)`, history, `/start <code>` pairing; the phone/computer card reaches it through Manage `/api/telegram` -> RulesReceiver `do:"telegram"`; TV Home asks HomeInfo `call("telegram", ""|"code")` -> `{state: token_missing|none|ready, bot, code, code_left, link}`, see dev/DECISIONS.md)
                         (Home Assistant / Shortcuts: `/api/hook`, its own token; TV Setup on 8765, the computer app on port+1 only while on)
  ontv/                  things done on the TV over ADB: Setup (Optimize TV; TV Home as the home screen; Bloat: brands' apps,
                         plus `docs/optimize/bloat.json` from the site, signed by `dev/sign/sign_bloat.py`, only adding to keep), SetupNeed / SetupUpdate, SetupFiles (reading TV Setup's
                         files: the shell, or TV Setup hands them over where the shell may not, a TCL on Android 11), RulesClient (TV Setup's
                         rules), Draw (strokes to TV Setup's layer), Notify, NowPlaying (through Playing), RemoteLabels (app
                         names, pictures and Caps, through Meta and Caps: Java in src/main/java, dexed into
                         assets/tvr-helper.jar by :shared:tvHelper, kept in git; app/shared/assets/README.md), ScreenOff, Sensors, Meminfo, Tailscale
  scenes/                Scenes (scenes + key names), Voice (spoken commands), RemoteSpec (remote.json)
  (guard)                `app/shared/src/guard`: Guard.kt (every Android app: keeps the main looper alive, shows the error in a dialog; DECISIONS.md) + GuardLogic.kt (also the computer app); the page's is `js/errbox.js`. An error shown there is also sent to the census worker (`POST /v1/crash`, `Census.crash`, `Scrub.kt` + `js/scrub.js`, one case table `tests/scrub_cases.json`) when the stats switch is on; the apps without :shared (TV Home, Channels, Games) have no switch and send nothing (`Guard.crash` stays null)
  (core itself)          Errors, Diagnose (the self-check when a TV cannot be reached: one conclusion + fix, `/api/diagnose`, `js/diagnose.js`, phone `Diagnosing.kt`), WireLog (what the TVs said and the errors shown, keys blanked: About → Send the log,
                         `/api/wirelog`), Fleet (several saved TVs at once), SameTv (one TV saved under two addresses), B64, SelfSigned
app/shared/tests/        node tests of the page's scripts, and the case tables the Kotlin tests read too (its README.md)
```
