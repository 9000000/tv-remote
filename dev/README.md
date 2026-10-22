# dev

Tools for people working on the code, one folder per kind of work. Each script says more in its first lines.
Run them from the repo root (`dev/test/emu.sh`, `python3 dev/gen/gen_icons.py`); each finds the root itself.

| Folder | For |
|---|---|
| `test/` | emulators, checks of the page and the routes, the site served locally |
| `gen/` | files made from a recipe: icons, wallpapers, IR codes. Edit the script, never its output |
| `shots/` | the site's screenshots and the pictures composed from them |
| `sign/` | the signed lists on the site (`docs/**.json` + `.sig`) |
| `release/` | releases, update keys and their backup (only when the maintainer says so) |

Notes beside them: `DECISIONS.md` (why some code looks odd), `TV_STYLE.md` (the TV apps' shared look),
`bloat-sources.md` (where Optimize TV's lists come from). Playwright for the Node scripts: `npm install --prefix dev playwright`.

## test/

| Script | Does |
|---|---|
| `emu.sh` | the two test emulators (Google TV and phone, Android 14): `create` once, then no argument to start them and install the apps, `install`, `reset`, `stop` (AGENTS.md "Testing") |
| `smoke.py [tv\|URL]` | every `/api` route over a real network path (the TV emulator through `emu redir`, not adb forward): each must answer in full, without 500; exit 1 if not |
| `layout.js [URL]` | the page in Chrome **and** WebKit × 1100 / 400 px × every tab + Settings, English and Vietnamese: sideways scroll, cut words, wrapped labels, things out of their card, overlapping cards, wrapped chips; screenshots in `/tmp/tvr-layout` |
| `audit.js` | the whole page pressed through by a script (every tab, dialog, menu and chip, down to `--depth`), no TV: `/api` is the live demo's made-up TV. Each screen checked for layout, contrast, the UI rules, words, touch size, script errors; report with boxed screenshots in `/tmp/tvr-audit/index.html` (~20 min; `--only=apps`, `--budget=` s per tab) |
| `contrast.py [URL]` | colour contrast on the rendered page, both themes × 1100 / 400 px × every tab; exit 1 under 4.5:1 (needs Chrome) |
| `check_quiz.py` | the TV Games quiz bank (`docs/games/web/quiz/bank.json`) |
| `serve_docs.py [port]` | the site (`docs/`) on localhost:8000 as Cloudflare Pages serves it (`/guide` is `guide.html`), never cached |

## gen/

| Script | Makes |
|---|---|
| `gen_icons.py` | the line icons: the web sprite and `res/drawable/ic_*.xml` of the apps. Add an icon here |
| `round_icons.py` | the logo's rounded corners everywhere it is a picture, and `app/desktop/icon.icns` / `.ico` |
| `channels_art.py`, `games_art.py` | TV Channels' and TV Games' launcher icons and banners |
| `wallpapers.py` | TV Home's wallpaper gallery (`docs/home/`), signed |
| `gen_wallspec.py` | `WallSpec.kt`, the wallpapers TV Home paints, from the page's recipes |
| `ir_codes.py` | `assets/ir/codes.json` of the phone app, from Flipper-IRDB (CC0) |

## shots/

| File | Does |
|---|---|
| `shots.py` | the site's screenshots from the emulators (`docs/README.md`) |
| `home-<lang>.html`, `Roboto.ttf` | what `shots.py` puts on the TV's screen: a made-up home screen per language, and its font |
| `games/` | the TV Games picture of the home page: the TV, the library and two phones as pads. `node dev/shots/games/compose.js` → `docs/img/tv-games-<lang>[-640].webp` |

## sign/

Each checks its list, then signs it with the launcher list's key (`~/.tv-remote-release/launchers.key`, apart from the
release key; its public half is `Launchers.KEYS`). Run after every edit of the list and commit both files.

| Script | List |
|---|---|
| `sign_launchers.py` | `docs/launchers/launchers.json`, and each APK's SHA-256 and thumbnail. Makes the key when absent |
| `sign_games.py` | `docs/games/games.json`: TV Games' cores, suggested web games, homebrew |
| `sign_providers.py` | `docs/channels/providers.json`: TV Channels' extra sources, e.g. VieON |
| `sign_bloat.py` | `docs/optimize/bloat.json`: Optimize TV's suggestions added from the site |
| `sign_downloader.py` | `docs/data/downloader.json`: the Downloader codes |

## release/

| Script | Does |
|---|---|
| `release.sh` | the whole release: version numbers, CHANGELOG, tag, APKs, GitHub release, the computer apps through GitHub Actions, then `sums.sh` (AGENTS.md "Versions and releases") |
| `sums.sh <tag>` | `SHA256SUMS` of a release, signed, attached. Run again after replacing any file of a release by hand: the apps refuse a file the list does not match |
| `guide_stale.py` | the guide's topics whose app files changed since their `data-v`; `release.sh` stops on them |
| `stamp_since.py` | `docs/data/since.json`: "next" becomes the version and its date |
| `churn.sh` | how often the `/api` still changes, week by week; `release.sh` prints it and says when 1.0 is near |
| `cores.sh` | downloads the libretro cores TV Games lists from the buildbot, prints their sha256 / size entries and the `gh release upload cores-1` command (run by hand) |

**Keys.** The apps install an update only if its `SHA256SUMS` is signed (Ed25519) by a key in `Release.KEYS`.

- `java dev/release/Sign.java new|pub|sign`: makes a key, prints its public half, signs a file. No build needed.
- The keys live in `~/.tv-remote-release/`: `ed25519.pem` in use, `ed25519-spare.pem` kept elsewhere. Never on GitHub.
  Losing both means no installed app takes an update again.
- `dev/release/keys.sh backup` packs the keys and every `signing.jks` (the APKs' signatures) into one password-locked file
  (`dev/release/Keys.java`, AES-GCM, `*.tvrkeys`, never committed) to keep off this computer. `dev/release/keys.sh list <file>` checks a
  copy opens; `restore <file>` puts the keys back.
