# TV Games

Games on the TV with phones as pads, no extra hardware. Package `dev.tvremote.games`; standalone (no `:shared`, no TV Setup). Licence: GPL-3.0 (`LICENSE`), because LibretroDroid and the core are GPL.

- **NES games:** ROMs you add (upload from the phone page `/add`, or `files/roms`) run on LibretroDroid 0.13.2 (GPL-3, JitPack) with the FCEUmm core.
- **Web games:** a link (`/add` -> "Thêm link game") or the built-in quiz, shown full screen in a WebView. How a page reads the pads: [WEBGAMES.md](WEBGAMES.md).
- **Pads:** the TV shows a QR; a phone browser is the controller (WebSocket to the app, up to 4 players). Bluetooth/USB pads work as well.

## Cores
| Core | Source | Licence | Where |
|---|---|---|---|
| FCEUmm (NES) | libretro buildbot (`buildbot.libretro.com`, android arm64-v8a), upstream https://github.com/libretro/libretro-fceumm | GPL-2.0 | `src/main/jniLibs/arm64-v8a/libfceumm_libretro_android.so` |

arm64 only for now. Record the exact buildbot build date when the core is next refreshed. Adding a system = one core `.so` + a line in `Systems.kt` (and its pad look in `assets/pad/pad.js`).

## Bundled ROM
`assets/homebrew/roborun.nes`: "Robo Run NES" by Haseeb (2021), GPL-3.0 (`assets/homebrew/LICENSE-roborun.txt`), source https://github.com/heaven-hm/roborun-nes.

## Build
`./build.sh` -> `build/tv-games.apk` (release, signed with `signing.jks`, created on first build and git-ignored; keep it to ship updates). Debug: `cd ../gradle && ./gradlew :games:assembleDebug`. Version: `VERSION`.
