# Web games design system (TV Games)

Rules for every page in `docs/games/web/<game>/` and for what such a page asks of the phone pad
(`app/tvgames/src/main/assets/pad`). Read with `app/tvgames/WEBGAMES.md` (the `window.tvpad` API, lobby, wire).
Values below are what the code does today; where games differ, the **reference** is named and a new game follows it.
Existing differences are listed in "Known drift" at the end: do not copy them.

References: **table / card / board games** -> `tienlen/` (+ `_lib/table.js`); **arcade (canvas)** -> `hockey/` (+ `_lib/kit.js`);
**aiming** -> `balloons/` (+ `_lib/pointer.js`); **phone pad** -> `assets/pad/pad.css` + `pad.js`.

## 1. Principles

| | Rule | Where it is done |
|---|---|---|
| Screen | A TV, 3 m away, 1280x720 up to 4K. Size everything from the viewport (`vh`), or draw on a fixed 1280x720 stage that is scaled (Kit canvas, cangua `#stage`). Never fixed `px` on a page that is not scaled. | `_lib/credit.js:16-26` (vh), `_lib/kit.css:10`, `cangua/cangua.js:227` |
| Inputs | The TV remote (arrows, Enter = OK, Back belongs to TV Games), phones through `window.tvpad`, Bluetooth pads (Gamepad API), the keyboard for player 1 off the TV. Every screen is usable with arrows + OK alone. | `_lib/kit.js:99-145`, `_lib/table.js:202-230` |
| No pointer | A TV has no mouse: `cursor:none` on `html,body`; no hover states, no click-only controls. A click may exist only as an extra (desktop tests, `pointer.js` mouse = player 1). | `tienlen/tienlen.css:3`, `_lib/pointer.js:30-35` |
| Old WebView | TV Games runs from Android 5 (`app/tvgames/build.gradle.kts:15`). Plain ES2017 JS (no `?.`, `??`, `catch {}`; `games_design.test.js`), `'use strict'`, no modules, no build step. CSS no newer than what `_lib` uses (custom properties, `inset`, `aspect-ratio`, flex/grid). | `_lib/*.js` |
| Plain browser | Without `tvpad` the page says what it is for (`nopad.js`, after an 800 ms grace for an old WebView), and a Kit game can still be tried with the keyboard. | `_lib/nopad.js:38-40` |
| Language | vi / en by `navigator.language` (`startsWith('vi')`), nothing else. Every word comes from one words object. | `_lib/table.js:32-43`, `_lib/kit.js:10,15-18` |
| Offline-ish | Each game is its own folder of plain files, served by the site; no CDN, no web font, no network call other than a data file next to the page (quiz `bank.json`, last good copy in `localStorage`). | `quiz/quiz.js:14-15` |
| Lobby and pause are not yours | TV Games draws the lobby (QR, seats, Ready, 3-2-1) and the pause card, and freezes the page meanwhile; a page's own title screen is `_lib/lobby.js`, never a new design. Time things with game ticks / `requestAnimationFrame` so a frozen page really stops. | `WEBGAMES.md` "Lobby", `quiz/quiz.js:3` |
| The TV is the authority | Phones show only their own seat and send presses; the page checks every move. | `_lib/table.js:71-82` |

## 2. Tokens

Define them on `:root` in the game's CSS (reference `tienlen/tienlen.css:1`); canvas games use the same values in JS.

| Token | Value | Use | Source |
|---|---|---|---|
| `--bg` | `#0b0d12` | page background (also the pad's `--bg`, `theme-color`) | `tienlen.css:1`, `pad.css:1` |
| `--card` | `#161b27` | panels: seats, rows, chips, options | `tienlen.css:1`, `quiz.css:1` |
| `--ink` | `#f1f4fb` | text | `tienlen.css:1` |
| `--dim` | `#9aa5bd` | secondary text, hints, "waiting" | `tienlen.css:1`, `table.js:132` |
| `--pc` | the seat's colour (below) | set per element: `e.style.setProperty('--pc', t.COLORS[s])` | `tienlen.js:112` |
| win | bg `#3a2f0b`, edge `#eab308`; place 1 chip `#ca8a04` | the winner's row / chip | `tienlen.css:35,43` |
| warn / refusal | `#fdba74` text, `#f97316` glow | a refused move, a "boom" | `table.js:133`, `tienlen.css:14` |
| plus / minus | `#4ade80` / `#f87171` | score changes | `quiz.css:26` |
| main action (TV) | `#2563eb`, white text | the main act in the remote panel | `table.js:159` |
| neutral control | `#243044` bg, `#e2e8f0` text; off = opacity `.4` | other acts on the TV | `table.js:157,160` |
| scrim | `#0b0d12e8` (game over), `#000a` (QR modal), `#0b0f1af2` (panels over play) | overlays | `tienlen.css:38`, `credit.js:19`, `table.js:128` |
| playing card | face `#f7f7f2`, ink `#1b1e27`, red `#d7263d`; back `repeating-linear-gradient(45deg,#3348a8 …,#2a3b8c …)` edge `#dfe4f1` | cards on TV and phone | `tienlen.css:16-17,30`, `pad.css:144-146` |
| felt | green `#14532d`→`#0c3b20` (radial) | a table; a game may pick its own felt pair (colors blue, meono purple) and keep the rest | `tienlen.css:1,5` |

**Seat colours (seats 1-4), canonical:** `#3b82f6` blue, `#ef4444` red, `#22c55e` green, `#eab308` yellow.
They are the phone's own colour for that slot (`pad.js:13,190`: the pad's `--c`), so the TV must use the same:
`Table.COLORS` (`table.js:45`). Answer keys A-D: `#e5484d` `#2f6fe0` `#e0a100` `#2fa05a` (`pad.css:74`, `quiz.css:1`).

**Type.** Stack `system-ui, Roboto, "Helvetica Neue", Arial, sans-serif` (`kit.js:13`; CSS pages may shorten to
`system-ui,sans-serif`). Weights: 600 body, 700-800 names and buttons, 900 big numbers / winner.

| Role | Size (vh at 16:9; vw x1.78 = vh) | Seen in |
|---|---|---|
| body | 3.2vh (`1.8vw`) | `tienlen.css:3` |
| turn banner `#msg b` | 4.1vh (`2.3vw`); "boom" 6vh | `tienlen.css:14` |
| seat name | 3.6vh (`2vw`) | `tienlen.css:27` |
| game-over title | 7.8vh (`4.4vw`); rows 4.6vh | `tienlen.css:40,42` |
| modal title / body | 4vh / 2.4vh | `credit.js:21,24` |
| smallest allowed | 1.8vh (credit's "press any key") | `credit.js:26` |
| canvas (720 px stage) | title 104, banner 92, HUD name 22 / score 38, help 26, smallest 19 px | `kit.js:180-191,207-208,214` |

**Shape.** Panels radius `1.4vw` (≈2.5vh) (`tienlen.css:21`), pills `99px`, cards `cw * .1`, modal `2vh`
(`credit.js:21`), remote buttons `1.2vh` (`table.js:157`). Shadows: lift `0 1vh 4vh #0008` (`tienlen.css:5`),
modal `0 2vh 6vh #000a` (`credit.js:22`), whose turn `0 0 3vh var(--pc)` + border `.3vw` in `--pc` (`tienlen.css:22`).

**Focus (TV remote).** White ring, raised and enlarged: card `outline .7vh #fff; translateY(-3.6vh) scale(1.08)`,
button `border #fff + 0 0 2.4vh #fff9; scale(1.07)` (`table.js:154,161`). Picked (not focused): `#38bdf8` ring + raised (`table.js:153`).

**Motion.** Turn change `.2s` (`tienlen.css:21`); a card moves by `_lib/cards.js` (FLIP + WAAPI, `transform`/`opacity` only, `cubic-bezier(.2,.8,.25,1)`): played = flies from the hand (the
remote panel's card, else the seat's backs) to the pile, `.34s`, a few degrees of turn, `.05s` between cards; drawn = from the deck to the hand
turned face-down to face-up (`back:true`, `.48s`), others' draws and steals = one small back from the deck / the seat to the seat's backs;
new game = hand dealt one by one from the deck (`.035s` apart) and a few backs to each seat; the hand closing up after a play slides `.22s`.
A picked card slides up and back down with a `.18s` transition (`table.js` `.cd`, `pad.css` `.cd`); the elements are kept between
redraws so the transition can run (a rebuilt card jumps). Never animate `box-shadow`/layout; `will-change` only while a card moves;
`prefers-reduced-motion` = no flight (`Cards.reduced()`). A card game loads `_lib/cards.js` before `table.js`, and uses `Cards.flyIn` / `flow` / `deal`.
The phone pad has its own copy of the idea (`pad.js` `cMotion`): the pad bundle cannot load the site's `_lib`.
focus `.12s` (`table.js:138`); clocks `width 1s linear` (`tienlen.css:37`); refusal note 2.5 s (`table.js:195`);
QR modal closes itself after 30 s (`credit.js:41`); bots think 0.9-1.5 s (`table.js:68`); arcade step fixed 60 Hz (`kit.js:11`).

## 3. Layout pieces (TV)

| Piece | Markup / API | Rules |
|---|---|---|
| Turn banner | `#msg` > `b` (what happens) + `span` (dim hint) | Centered near the top of the table (`tienlen.css:13-15`). One line each. Also holds the "needs a newer TV Games" / "open in TV Games" message (`W.old/oldh/nopad`, `tienlen.js:108`). |
| Seats | `.seat.s/.e/.n/.w` around the table, seat 1 at the bottom (`tienlen.js:99`) | `.name` = `.dot` in `--pc` + name, ellipsis, max 22vw; a bot gets `🤖 ` before its name; `.info` count / total / place; `.timer` bar only on a phone's turn (`tienlen.css:21-37`, `tienlen.js:110-122`). |
| Names | `t.name(s)` / `Kit` slot `.name` | `tvpad.name(p)` (≤16 chars, `pad.js:46`) or `W.p(n)` "Player N"; always truncate with ellipsis (CSS) or `…` (canvas, `kit.js:190,207`). |
| Scores | `.info .tot` "Total +3" (Table), HUD bar on canvas `Kit.hud` | Table scoring is fixed: `SCORE` 2-4 players (`table.js:44`). Canvas HUD = card per slot, colour strip, score in seat colour (`kit.js:201-211`). |
| Cards on TV | `.card` (`b` rank, `i` small suit, `u` big suit), width `--cw` | `tienlen.css:16-20`; the remote panel's `.cd` (`table.js:137-155`) is the same card. |
| Game over | `#over[hidden]`, `h1` "🏆 X wins!", `.row` grid `place · dot · name · +pts · total`, `.row.win`, `p` "New game: …" | `tienlen.css:38-45`, `tienlen.js:130-138`. Canvas: `drawOver` (`kit.js:216-225`). New game by OK / START / `again`. |
| Round banner (canvas) | `Kit.banner(c, big, small, color)` | full-width band, `rgba(4,8,20,.66)` (`kit.js:212-215`). |
| Remote seat | `remote: true` in `Table({...})` -> `#rmt` panel at the bottom | Game CSS makes room: `body.rmt-on #table{bottom:39vh} body.rmt-on .seat.s{display:none}` (`tienlen.css:47`). Selectors need `body #rmt …` to win. |
| QR modal | `credit.js`: tag bottom-right + `#gh24q` | The only modal pattern: centered card, QR 30vh on white, title / text / link / "press any key to close"; any key closes and is eaten (`credit.js:46-51`). Copy it for any other info card. |
| Key hints | text with arrows, or `<kbd>` styled as in `credit.js:18` (inherits font, `#94a3b8`) | e.g. `◀  CPU players: 2  ▶` (`kit.js:193`), `↓ QR code` (`credit.js:10-11`). Show a hint only while the key does that (`credit.js:52`). |
| Toasts / events | no shared toast | Private (a refusal) -> `t.say(seat, words)` + vibrate 250 ms on that phone (`table.js:246`); the pad shows it 6 s (`pad.js:169`). Public -> the `#msg` line, or a dim event line (meono `#evt`, typhu `#log`). Never `alert()`. |
| Lobby (own screen) | `_lib/lobby.js`: `Lobby.set({title, tag, chips, min, go, extra, hints})` / `Lobby.hide()`; used by the Kit title, quiz and caro | **Spec** (matches TV Games' native `LobbyView`): QR on the left 40% (drawn once from `qrModules`, whole pixels per module, 4-module white quiet zone, about 590 px at 1080p) with "Scan to play" 4.4vh + the short link 2.4vh under it; right: title 9vh, tag 3vh, players as chips (colour badge `P1`, name; a CPU is a dashed ghost; a joining phone pops in `.28s` + `ui` tick), ONE pill: "OK Start" (blue, glow) when `have >= min`, else amber "Needs N more"; hints in one dim 2.1vh line at the bottom (stops before the credit tag). DOM only, no animation but the pop: the Kit loop polls at 20 Hz and draws no canvas while it shows, so an idle lobby has ~0 rAF. Inside TV Games (`window.TVPadNative`) the native lobby has just shown the QR, so the page leaves it out (layout centres; `?lobbyqr` forces it, tests / plain hosts). Table games start at once and have no lobby of their own. |

### Sound

Every page loads `_lib/sfx.js` (right after `nopad.js`): no audio files, tones and noise made with one shared WebAudio context, at most a dozen voices at once, `?mute` silences it. Kit games reach it as `K.tone` / `K.noise` / `K.sfx.x()`; table, card, board and quiz pages call `Sfx.play('name')`. The set: `ui ok tick go win lose` (menus, countdown, result), `hit boom bounce shoot dash fall goal` (arcade), `move place card pickup dice` (board and cards), `score right bad turn` (points, answers, refusals, your move). Each name has a minimum gap (30-80 ms), so ten collisions in one frame sound once. `table.js` plays `turn` (a human's turn), `bad` (a refused move) and `win` / `lose` itself; a game adds only its own events. Kit also plays `lose` when a CPU wins over the phones and ticks the last 5 s of a timed game (`K.timeTick`). Keep new sounds short and quiet (gain <= 0.15), and tune them in `sfx.js`, not per game.

## 4. Phone pad

The pad is one page for every game; a game only chooses a layout. Its look: `--bg #0b0d12`, keys `--key #1a1f2b`,
the player's colour as `--c` (`pad.css:1`, `pad.js:190`). Landscape and portrait both work (`pad.css:93-100,134,143`).

| Layout (`tvpad.layout(p|'all', …)`) | Shows | Used by |
|---|---|---|
| `null` | the system look (web = ps: stick + diamond + L/R + SELECT/START) | default |
| `{buttons:['A','START'], dpad:false}` | only these buttons; `dpad:false` drops D-pad / stick | Kit menus (`kit.js:169`), quiz (`quiz.js:59`) |
| `{choices:['A','B','C','D']}` | four big coloured answers | quiz (`quiz.js:76`) |
| `{text:true}` | a field + Send -> `on('text')` | |
| `{pointer:true, buttons:['A']}` | touchpad + Fire / Alt / Recenter | `pointer.js:37-43` |
| `{pointer:true, side:'left'\|'right', aspect: w/h}` | the touchpad on that side, shaped like what it steers | `hockey.js` (`Ptr.use({side, aspect})`) |
| `{cards:{hand, note, turn, sel?, acts}}` | private hand + up to 6 act buttons | every Table game (`tienlen.js:88-96`) |

**Cards layout** (`pad.js:388-440`, `pad.css:135-188`):
- `hand` ≤ 40 cards: `"10h"` (rank 2-10 J Q K A + suit s c d h) or `{id, t ≤16, sub ≤28, k ≤4, i ≤4, bg '#hex', look:'oval'|'deed', off}`.
  Portrait with > 7 cards: two rows. Empty hand: the acts become big buttons (a board game's choices).
- `note`: one short line (≈ 30 chars fits 390 px), white + glow when `turn:true`.
- `acts` ≤ 6: `{id, t, i?, main?, off?}`. Exactly one `main` (filled in the player's colour); `off` while it cannot be pressed.
  Known ids get an icon on both sides: `play pass skip hint draw again roll buy build sell mort end ok nope give jailpay jailcard`
  (`pad.js:395`). Words ≤ ~12 chars: buttons are `nowrap`, max 220 px (`pad.css:152`); a long word goes in `s` for the TV panel (`table.js:188`).
- Order: secondary acts first, `main` last (right, under the thumb): `hint, pass, play` (`tienlen.js:92`). After a game: one `again` main.
- A press comes back as `on('text')` `{"act":id,"cards":[ids]}`. Send the layout again after every change (Table does, only when it changed).
- Check `tvpad.has && tvpad.has('cards')`; else show `W.old` / `W.oldh` (`table.js:50`).

A game must send: a layout for each state (menu, play, over), `say('')` to clear a refusal once the move succeeded
(`tienlen.js:46`), a vibrate on the events that matter to that player only (turn won 700 ms, others 120 ms: `table.js:93`).

## 5. Boilerplate for a new game

```html
<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Tiến lên</title>                     <!-- the game's name; Kit sets document.title itself -->
<link rel="stylesheet" href="mygame.css">   <!-- Kit games: ../_lib/kit.css -->
</head>
<body>
<!-- table game: #table (#felt #pile #what #msg), #seats, #over hidden ; Kit game: <canvas id="c" width="1280" height="720"> -->
<script src="../_lib/qr.js"></script>       <!-- always first: nopad and credit draw QRs with it -->
<script src="../_lib/nopad.js"></script>    <!-- always -->
<script src="../_lib/credit.js"></script>   <!-- only when ported from gamehub24 -->
<script src="../_lib/table.js"></script>    <!-- turn-based; or ../_lib/kit.js (+ ../_lib/pointer.js) for arcade -->
<script src="rules.js"></script>            <!-- pure rules + bots, no DOM: testable in node -->
<script src="mygame.js"></script>
</body>
</html>
```

Words, one object, the library's first (`tienlen.js:4-14`):

```js
'use strict';
const W = Object.assign({}, Table.W, Table.VI ? {
  play: 'Đánh', pass: 'Bỏ lượt', bad: 'Bài không hợp lệ',
} : {
  play: 'Play', pass: 'Pass', bad: 'Not a valid combination',
});
```

Kit games give `title`, `tag`, `help` as `{vi, en}` to `Kit.run({...})` and read in-game words through `K.tr({vi, en})` (`kit.js:5,22`).
Reuse `Table.W` / `Kit.words` (Player N, Bot N, Your turn, New game, wins, Total, places) instead of new copies.
A game whose D-pad plays sets `Credit.key = () => <between games>` so Down still moves (`caro.js:182`, `bida.js:356`).
CSS: `:root` tokens, `*{box-sizing:border-box}`, `html,body{margin:0;height:100%;background:var(--bg);color:var(--ink);font:600 3.2vh/1.25 system-ui,sans-serif;overflow:hidden;cursor:none}`.
List the game in `docs/games/games.json` "web" (`players`, `min`, `genre`, `img`) and run `dev/sign/sign_games.py`.

**3D: `_lib/k3d.js` (K3), ours, no Three.js.** WebGL 1 (a Mali-450 box has no WebGL 2), ~8 KB gzipped, one shader: low-poly
meshes with vertex colours, one sun, fog, an additive flash. Load it before `kit.js`; it puts a `#gl` canvas under Kit's `#c`, so
the HUD, name tags and Kit's screens stay 2D. In `draw(c)`: `K3.begin(cam)`, `K3.draw(mesh, K3.place(m, x, y, z, yaw))` per
thing, `K3.cube(...)` for small moving bits (one batch), `K3.end()`, then the 2D HUD (`K3.toScreen` puts a tag over a 3D point).
`if (!K3.init({...}))` at start: tell the player the TV has no 3D, do not start. Budgets for a weak box: under ~60 draws and
~40k triangles a frame (`dev/test/k3bench.html` prints both); build meshes once (`K3.geo()` ... `build()`), never per frame;
cut a big world into a few meshes so the ones out of view are skipped; ground in tiles (`plane(..., tile)`: fog is per vertex).

## 6. Don'ts

- No web fonts, CDNs, frameworks, bundlers or images from other hosts; no `import`.
- No control that needs a pointer, hover or a long press; no text field on the TV (use `{text:true}` on a phone).
- No text under 1.8vh, no fixed `px` on an unscaled page, no layout that only fits one aspect ratio without letterboxing.
- No colours outside the tokens; seat colours only from `Table.COLORS` (or the same four values); never colour a seat by anything else.
- No `alert()`, `confirm()`, `prompt()`, no own pause screen and no join screen of your own design (TV Games has them; a title screen uses `_lib/lobby.js`), no own Back handling.
- Never trust a phone: check every act; never show one player's hand on the TV.
- No words in code outside the `W` object; no third language branch (vi / en only, en is the fallback).
- No `localStorage` for game state: only a cache of data and the TV's high scores (quiz `quiz.best`, top 10), wrapped in try/catch (`quiz.js:15`).
- Don't change `_lib` behaviour for one game: add an option (as `remote: {mount, quick}`), keep the header comment true.

## 7. Checklist before done

- [ ] `node --test app/tvgames/tests/` (`games_design.test.js` checks ES2017, no `translate:`/`scale:` properties, no other hosts, cursor hidden, qr.js then nopad.js, one set of seat colours); `node --check` every JS file; `rules.js` exercised in node (no DOM) for a few hundred random games.
- [ ] Serve the site: `dev/test/serve_docs.py` (http://localhost:8000/games/web/<id>/).
- [ ] Playwright (`npm install --prefix dev playwright`), Chromium at **1280x720** and **1920x1080**, with a fake `tvpad`
      added by `page.addInitScript` before the page runs; copy the shim of the site's demo (`docs/demo/games.html:150-153`):
      `on/off/players/name/ping/joinUrl/qr/layout/vibrate/say/has(()=>['cards'])` + `window.__emit(type, data)`
      to fire `join`, `button`, `axis`, `text`. Record every `layout()` / `say()` call and assert on them.
      Play a full game: 1 phone + bots, 4 phones, a phone leaving mid-turn, the clock running out, game over, `again`.
- [ ] Remote alone (no tvpad players): arrows + Enter can finish a game; Down opens / any key closes the credit QR.
- [ ] Plain browser (no shim, after 1 s): the `nopad` card shows; Kit games' "Try with the keyboard" works.
- [ ] Both languages: `locale: 'vi-VN'` and `'en-US'` in the browser context: no clipped or wrapped label in either.
- [ ] Phone pad at **844x390** and **390x844** with each layout the game sends (render `assets/pad` or the demo pad
      `docs/demo/pad/` with the recorded layouts): longest act words, 13+ cards, empty hand.
- [ ] TV emulator (`dev/test/emu.sh`): add the page as a link in TV Games (`/add`, http on the LAN: `http://10.0.2.2:8000/...`),
      open it, screenshot: fonts, no cursor, frame rate (`?fps=1` on Kit games).
- [ ] Screenshots looked at: nothing cut, no ellipsis on a short name, focus ring visible, contrast of `--dim` on `--card`.
- [ ] `docs/games/games.json` signed; `WEBGAMES.md` updated if a `_lib` or pad API changed.

## 8. Known drift (as of this file; do not copy, fix only on purpose)

| # | Drift | Where |
|---|---|---|
| 2 | Units: tienlen / colors / meono / quiz in `vw`; typhu in `vh`; cangua in `px` on a scaled 1280x720 stage; caro mixes `vh` board + `vw` text | each `*.css:1-3` |
| 3 | Body size: `1.8vw` (tienlen, colors, meono), `2.2vw` (quiz, caro), `1.8vh` (typhu), `24px` (cangua) | `*.css:3` |
| 5 | Words: quiz and caro keep their own `T` object; Kit games write inline `K.VI ? … : …` | `quiz.js:5`, `caro.js:5`, `bombers.js:7,123-125`, `hockey.js:116` |
| 6 | The remote panel's icon map lacks `again` (🔄 on the phone) | `table.js:101` vs `pad.js:395` |
| 7 | Main action: `#2563eb` on the TV panel, the player's colour on the phone | `table.js:159`, `pad.css:157` |
| 9 | Game-over overlay: scrim `e8` / `ea` / `ee`, z-index none / 20 / 6 / 5, title `4.4vw` / `56px` / `6vh`, typhu's columns differ | `tienlen.css:38-45`, `cangua.css:46-54`, `meono.css:59-66`, `typhu.css:54-62` |
| 10 | `#msg` placement: top 9% (tienlen, colors), 5% (meono), 40vh (typhu), beside the die (cangua) | the same files |
| 11 | Own palettes beside the tokens: meono `--bg #0d0a18 --ink #f5f3ff --dim #a99fcb --card #1a1530`; caro X/O `#3b82f6 / #ef4444` and cursors `#0e9f4a / #7c3aed` | `meono.css:1`, `caro.js:15` |
| 12 | (fixed) Kit / quiz / caro title screens now share `_lib/lobby.js`; table games still start without one | `lobby.js` |
