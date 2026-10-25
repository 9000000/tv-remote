# app/games: TV Games

Read with the root [AGENTS.md](../../AGENTS.md) (rules, build, tests, releases apply here too). What is where:

```
app/games/               TV Games: LibraryActivity (the games, Suggestions from the catalog), GameActivity (LibretroDroid, save / load),
                         WebGameActivity (a web game in a WebView: keys, the Gamepad API and `window.tvpad`, WEBGAMES.md), GameServer (8780:
                         the pad page `assets/pad`, uploads, WebSocket slots 1-4), Systems (extension -> system -> core, pad layout), Catalog +
                         Cores (`docs/games/games.json`, signed by `dev/sign/sign_games.py` with the launcher list's key: cores downloaded by sha256 from
                         the `cores-1` release made by `dev/release/cores.sh`, suggested web games, free homebrew; fceumm packed as the offline fallback)
```
