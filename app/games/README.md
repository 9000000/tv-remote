# TV Games

Games on the TV with phones as pads, no extra hardware. Package `dev.tvremote.games`; standalone (no `:shared`, no TV Setup). Licence: GPL-3.0 (`LICENSE`), because LibretroDroid and the core are GPL.

- **NES games:** ROMs you add (upload from the phone page `/add`, or `files/roms`) run on LibretroDroid 0.13.2 (GPL-3, JitPack) with the FCEUmm core.
- **Web games:** a link (`/add` -> "Thêm link game") or the built-in quiz, shown full screen in a WebView. How a page reads the pads: [WEBGAMES.md](WEBGAMES.md).
- **Pads:** the TV shows a QR; a phone browser is the controller (WebSocket to the app, up to 4 players). Bluetooth/USB pads work as well.

## Cores
| Core | Source | Licence | Where |
|---|---|---|---|
| FCEUmm (NES) | libretro buildbot (`buildbot.libretro.com`, android arm64-v8a and armeabi-v7a, fetched 2026-10-21), upstream https://github.com/libretro/libretro-fceumm | GPL-2.0 | `src/main/jniLibs/{arm64-v8a,armeabi-v7a}/libfceumm_libretro_android.so` |

64- and 32-bit ARM (32-bit boxes such as the FPT Play 650). Record the exact buildbot build date when the core is next refreshed. Adding a system = one core `.so` + a line in `Systems.kt` (and its pad look in `assets/pad/pad.js`).

## Bundled ROM
`assets/homebrew/roborun.nes`: "Robo Run NES" by Haseeb (2021), GPL-3.0 (`assets/homebrew/LICENSE-roborun.txt`), source https://github.com/heaven-hm/roborun-nes.

## Suggested homebrew (`docs/games/games.json`, "homebrew")
Fetched by the user's OK from the author's own release (nothing is bundled), sha256 checked. All NES, all with a LICENSE file in the repo; the ROM is the author's release build.

| Game | Author | Licence | Source |
|---|---|---|---|
| Robo Run | Haseeb | GPL-3.0 | https://github.com/heaven-hm/roborun-nes |
| Nova the Squirrel | NovaSquirrel | GPL-3.0 | https://github.com/NovaSquirrel/NovaTheSquirrel |
| Witch n' Wiz | Matt Hughson | MIT | https://github.com/mhughson/mbh-A53-witchnwiz |
| Stallar | Wendel Scardua | MIT (music / SFX public domain) | https://github.com/wendelscardua/stallar |
| File Fixers | Wendel Scardua | MIT | https://github.com/wendelscardua/file-fixers |
| 8-Bit Table Tennis | Mike Michel | MIT | https://github.com/mike42/8bit-table-tennis |
| Snake, Breakout, Pong | Sebastian Dine | Zlib | https://github.com/sebastiandine/openNES-Snake (-Breakout, -Pong) |

Left out: Miroh (art and music all rights reserved), Falling (third-party music with no stated licence), Flappy Paratroopa (Nintendo characters), Galaxy Patrol (no release, a nesdev wiki tutorial copy), Super Tilt Bro (WTFPL, but no published ROM to link; add once one exists). GB / GBA / SNES homebrew not listed: those cores are still `"on": false` until the `cores-1` release exists.

## Tile pictures
Order (`Thumbs.kt`): packed `assets/thumbs/h_<id>.jpg` (catalog homebrew, by id or by the downloaded file name; the title screen taken from the game on the TV emulator, 400x250, so Suggestions and the library show the same offline picture) -> libretro-thumbnails box art -> the page's / catalog's `img` -> snapshot of the last play -> initials. A new catalog game: run it, screencap its title screen, add `h_<id>.jpg`.

## Build
`./build.sh` -> `build/tv-games.apk` (release, signed with `signing.jks`, created on first build and git-ignored; keep it to ship updates). Debug: `cd ../gradle && ./gradlew :games:assembleDebug`. Version: `VERSION`.
