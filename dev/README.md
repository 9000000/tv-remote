# dev

Tools for people working on the code. Each script says more in its first lines.

**Every day**

| Script | Does |
|---|---|
| `emu.sh` | the two test emulators (Google TV and phone, Android 14): `create` once, then no argument to start them and install both apps, `install`, `reset`, `stop` (AGENTS.md "Testing") |
| `contrast.py [URL]` | colour contrast on the rendered page, both themes × 1100 / 400 px × every tab; exit 1 under 4.5:1 (needs Chrome) |
| `smoke.py [tv\|URL]` | every `/api` route over a real network path (the TV emulator through `emu redir`, not adb forward): each must answer in full, without 500; exit 1 if not |
| `layout.js [URL]` | the page in Chrome **and** WebKit × 1100 / 400 px × every tab + Settings, English and Vietnamese: sideways scroll, cut words, wrapped labels, things out of their card, overlapping cards, wrapped chips; screenshots in `/tmp/tvr-layout` (needs `npm install --prefix dev playwright`) |
| `audit.js` | the whole page pressed through by a script (every tab, dialog, menu and chip, down to `--depth`), no TV: `/api` is the live demo's made-up TV. Each screen checked for layout, contrast, the UI rules, words, touch size, script errors; report with boxed screenshots in `/tmp/tvr-audit/index.html` (needs Playwright, ~20 min; `--only=apps`, `--budget=` s per tab) |
| `gen_icons.py` | the line icons: the web sprite and `res/drawable/ic_*.xml` of both apps. Add an icon here, never edit its output |
| `bloat-sources.md` | where Optimize TV's brand lists (`Bloat.kt`) and the Google TV home swap come from (cleanyourtv, its forks, PRs, issues), what was left out and why, and the commands to read them again |
| `round_icons.py` | the logo's rounded corners everywhere it is a picture, and `app/desktop/icon.icns` / `.ico` |
| `shots.py` | the product page's screenshots (`docs/README.md`) |
| `shots/` | what `shots.py` puts on the TV's screen: a made-up home screen per language, and its font |

**Releases** (only when the maintainer says so)

| Script | Does |
|---|---|
| `release.sh` | the whole release: version numbers, CHANGELOG, tag, APKs, GitHub release, the computer apps through GitHub Actions, then `sums.sh` (AGENTS.md "Versions and releases") |
| `sign_launchers.py` | signs `docs/launchers/launchers.json` (Ed25519, key `~/.tv-remote-release/launchers.key`, apart from the release key; its public half is `Launchers.KEYS`) and checks each APK's SHA-256 and each thumbnail. Run it after every edit of the list; `keys.sh backup` includes the key. |
| `sums.sh <tag>` | `SHA256SUMS` of a release, signed, attached. Run again after replacing any file of a release by hand: the apps refuse a file the list does not match |
| `churn.sh` | how often the `/api` still changes, week by week; `release.sh` prints it and says when 1.0 is near |

**Keys.** The apps install an update only if its `SHA256SUMS` is signed (Ed25519) by a key in `Release.KEYS`.

- `java dev/Sign.java new|pub|sign`: makes a key, prints its public half, signs a file. No build needed.
- The keys live in `~/.tv-remote-release/`: `ed25519.pem` in use, `ed25519-spare.pem` kept elsewhere. Never on GitHub.
  Losing both means no installed app takes an update again.
- `dev/keys.sh backup` packs both keys and both `signing.jks` (the APKs' signatures) into one password-locked file
  (`dev/Keys.java`, AES-GCM, `*.tvrkeys`, never committed) to keep off this computer. `dev/keys.sh list <file>` checks a
  copy opens; `restore <file>` puts the keys back.
