# The TV apps' design system

Rules for any UI change in **TV Setup** (`app/tv`), **TV Home** (`app/home`), **TV Channels** (`app/channels`) and **TV Games**
(`app/games`), plus the **Phone app's native screens** (last section). They describe what the code does today. Where the apps
disagree, see [Known drift](#7-known-drift): don't make it worse, and don't start a new variant.
The shared values are copied into each app's `res/values/tv_style.xml` (this file replaced `dev/TV_STYLE.md`).

## 0. Shared code, and which app is the reference

| App | Shared code | Words from | Tokens from |
|---|---|---|---|
| TV Setup | `:shared` (`tv/build.gradle.kts:50`) | `T("id")`: `app/shared/web/i18n/<lang>.json` (`core/Words.kt`); **no strings.xml** | `values/tv_style.xml` + constants in `MainActivity.kt:62-70` |
| TV Home | none (only `shared/src/home` HomeConfig) | `res/values{,-vi}/strings.xml` | constants in `HomeActivity.kt` (**no tv_style.xml**) |
| TV Channels | none (only `shared/src/sign`) | `res/values{,-vi}/strings.xml`, own language picker (`Lang.kt`) | `values/tv_style.xml` + `Ui.kt:16-21` |
| TV Games | none (only `Qr.kt` copied, `shared/src/sign`) | `res/values{,-vi}/strings.xml` | `values/tv_style.xml` (no colors.xml) + `Ui.kt` |

- No code is shared between them, so the rule is **copy the values, not the look-alike**: take the number from the reference
  and give it the same name (`tv_card`, `tv_dim`, …).
- `tv_style.xml` is byte-identical in tv / channels / games: change a value in all three and here together.
- When the apps disagree, the reference is:
  - **TV Home** for focus on tiles: ring, glass, grow, motion (`HomeActivity.kt:857-1012`, `Glass.kt`);
  - **TV Channels' `Ui.kt`** for rows, lists, fields and dialogs;
  - **TV Games' `Ui.kt`** (`panel()` / `scrim()`) for full-screen popups.

## 1. Principles (10-foot UI)

| Rule | How, today |
|---|---|
| **D-pad first** | Everything you can act on has `isFocusable = true; isClickable = true` (`channels/Ui.kt:58`, `games/Ui.kt:76`). No touch-only gestures: swipes, drags and taps on things that can't be focused are out. A mouse wheel is a bonus (`games/LibraryActivity.kt:291`). |
| **Focus always visible** | Every focusable control has a focused state: a ring, the glass or a light fill (§2.5). Never rely on the system's default highlight. |
| **The first focus lands somewhere useful** | A screen or popup that opens puts the focus on something (e.g. Retry: `BrowseActivity.kt:379`). A rebuilt list keeps the focused row (`BrowseActivity.kt:243`). |
| **Back closes, then leaves** | Inner layer first: a download is cancelled (`LibraryActivity.kt:175`), then an overlay closes (`:176`, `closeOverlay()` `:185`), then a filter is cleared, then you leave (`:165`). Move mode: Back cancels (`HomeActivity.kt:498`). TV Home (the launcher) never leaves: Back scrolls to the top, ignoring a second press within 500 ms (`:340`). |
| **Hold OK = the item's menu** (same as the Menu key) | Channels: `e.startTracking()` + `onKeyLongPress` → `actions()` (`BrowseActivity.kt:659-669`); Home: tile `isLongClickable` → `menu()` (`:744`); Games: `setOnLongClickListener { menu(i) }` (`LibraryActivity.kt:796`). A new list of things gets the same: hold OK and Menu both open its menu. |
| **Readable at 3 m** | Body text ≥ 15 sp, secondary ≥ 12 sp (§2.4). One line + ellipsis for names (`maxLines = 1; ellipsize = END`). Contrast: ink on bg/card ≥ 4.5:1. |
| **Overscan / safe area** | Main screens are padded `tv_margin_x` 48 dp × `tv_margin_y` 27 dp (5 % of 960×540 dp): `BrowseActivity.kt:75`, `MainActivity.kt:144`, `LibraryActivity.kt:272`. Nothing to read or focus goes outside. Corner overlays sit ≥ 32 dp in (`NotifyLayer.kt:53`). |
| **Weak boxes (1 GB, MT9221 / MT5895)** | Plain Views, no AppCompat / Compose (`channels/Ui.kt:14`, `Lang.kt:9`). No real blur (`Frost.kt`: a 1/16 copy, stretched), no per-frame redraws (`Glass.kt:15-18`: drawn once, then moved), `clipRect` not `clipPath` (`Glass.kt:53`), `withLayer()` on scale animations (`HomeActivity.kt:1103`), `hasOverlappingRendering() = false` on dimmed cards (`BrowseActivity.kt:719`). |
| **Motion can be off** | Respect `ANIMATOR_DURATION_SCALE == 0` (`Motion.read` `HomeActivity.kt:1092`, `animOn` `LibraryActivity.kt:714`) and go *calm* on ≤ 1.5 GB RAM (`Motion.lowRam` `:1086`, `calm` `LibraryActivity.kt:715`): shorter (×11/20), one animator, no overshoot. |
| **TV vs phone** | TV Setup also runs on phones: decide by `FEATURE_LEANBACK`, never screen size alone (`MainActivity.kt` `phone`). |

## 2. Tokens

### 2.1 Colours (dark only: there is no light theme on the TV)

| Token | Value | Where it lives | Use |
|---|---|---|---|
| `tv_bg` | `#0E1116` | `tv_style.xml`; Setup `BG` (`MainActivity.kt:62`) | screen background (Channels `windowBackground`) |
| `tv_card` | `#1B2028` | `tv_style.xml`; Channels `Ui.CARD`; Setup `CARD` | panels, cards, fields |
| tile (Setup) | `#22272F` | Setup `TILE` (`MainActivity.kt:64`) | a resting action tile / button on a card |
| `tv_ink` | `#E8EAED` | `tv_style.xml`; `Ui.TEXT`; Setup `INK` | main text, icon tint |
| `tv_dim` | `#9AA0A6` | `tv_style.xml`; `Ui.DIM` | secondary text, counts, hints |
| `tv_ring_accent` | `#2F7DF6` | `tv_style.xml`; Home `RING_ACCENT` (`HomeActivity.kt:1013`) | **the focus ring only** |
| app accent | Channels `#5EEAD4` (`Ui.ACCENT`, theme `colorAccent`); Setup `#3B73F6`; Home `#8AB4F8` | per app | spinners, a chosen item, the app's own brand; never the focus |
| danger | Channels `Ui.BAD` `#F28B82`; Setup `BAD` `#FF7070` | per app | errors, "off" states |
| ok / warn | Setup `OK` `#46D283`, `WARN` `#F0B34A` (`MainActivity.kt:68-69`) | Setup | status dot + title (`setStatus` `:598`) |
| scrim | Games `0xD90E1116` (`games/Ui.kt:69`) | Games | the dimmed screen under a popup |
| logo tile | `#F2F4F7` light / `#3A4350` dark (`LogoFit.kt:51-52`) | Channels | what a channel logo sits on (picked by contrast) |
| player colours | `#3B82F6 #EF4444 #22C55E #EAB308` (`games/Ui.kt:22`) | Games | player 1-4 dots |

- Read `tv_*` from resources (`getColor(R.color.tv_dim)`) or the app's named constant. **No new raw hex in a view**: a colour
  needed once more becomes a named constant beside the others (Channels `Ui`, Setup's `BG…BAD`, Home's top-level `const`s).
- White-alpha fills are the second layer of greys: `0x14FFFFFF` field rest, `0x22FFFFFF` divider (`BrowseActivity.kt:236`),
  `0x33FFFFFF` focused row fill (`Ui.states` default), `0x40FFFFFF` panel hairline.

### 2.2 Radii (dp)

| Shape | Radius | Source |
|---|---|---|
| Panel / card / popup | **16** `tv_radius_card` | `games/Ui.kt:54` |
| Dialog sheet (Channels), notice card | 20 | `BrowseActivity.kt:462`, `WarnActivity.kt:44`, `NotifyLayer.kt:81` (drift, §7) |
| Field / pill-button | 22 (Channels search, Retry) · 18 (field inside a sheet) | `BrowseActivity.kt:94,126,551` |
| List row / channel card | 12 / 16 | `Ui.row` `channels/Ui.kt:57`; card `BrowseActivity.kt:723` |
| App / game tile, poster | **10** `tv_radius_tile` | `HomeActivity.kt:877`, `LibraryActivity.kt:735` |
| App icon in the header | 10 `tv_head_radius` | `tv_style.xml` |
| Small button / menu button | 10 (Setup `smallButton`, Games `menuButton`) · 14 (Setup tile, Warn button) | `MainActivity.kt:1083`, `games/Ui.kt:77` |
| Chip / pill (choices) | 30 (Games `pill`) | `LibraryActivity.kt:353` |

### 2.3 Spacing (dp)

Use the steps already in use: **4 · 6 · 8 · 10 · 12 · 14 · 16 · 20 · 24 · 28 · 32 · 48**.

| Where | Value |
|---|---|
| Screen margin | 48 × 27 (`tv_margin_x/y`); TV Home 48 / 34 / 48 / 48 (`HomeActivity.kt:130`) |
| Header | icon 44 (`tv_head_icon`), gap 14 (`tv_head_gap`), name 24 sp bold `tv_ink`, 18 below (`BrowseActivity.kt:211`) |
| Grid gap between tiles | 32 (`GRID_GAP` `HomeActivity.kt:1011`): room under a grown tile for its name |
| List row padding | 12 × 10, icon 20 + 12 gap (`channels/Ui.kt:55-62`) |
| Dialog sheet padding | 14 (`BrowseActivity.kt:461`); its title 12 left, 10 below |
| Panel padding | 36 / 26 / 36 / 28 (`games/Ui.kt:59`) |
| Notice card | 40 / 32 / 40 / 28 (`WarnActivity.kt:45`); buttons 52 high, 12 apart (`:80`) |

### 2.4 Text (sp, system font; TV Home alone uses Inter from `Font.kt`)

| Role | Size | Source |
|---|---|---|
| Clock (Home) | 60-64, light | `HomeActivity.kt:116,144` |
| Panel heading (Games) | 28 bold, `tv_ink` | `games/Ui.kt:63` |
| App name in the header | 24 bold (`tv_head_name`) | `tv_style.xml` |
| Dialog / sheet title | 18 bold | `BrowseActivity.kt:469` |
| Menu button | 20-22 (Games) · 16-17 (Home Drop, Warn) | `LibraryActivity.kt:590`, `Drop.kt:58`, `WarnActivity.kt:134` |
| Empty / state message | 17, `tv_dim`, centred, line spacing 1.2 | `BrowseActivity.kt:122` |
| Row label | 15 | `Ui.row` |
| Field | 15-16 | `BrowseActivity.kt:92,550` |
| Count, meta, tile name | 13-14 | `Ui.row` count, `HomeActivity.kt:918` |
| Floor | 12 (section caps, small hints) | Setup `section()` `MainActivity.kt:1059` |

- Use the app's text helper: Channels `Ui.text(c, s, sp, color, bold)` (`includeFontPadding = false`), Games `label()` /
  `heading()`, Setup `text()`, Home `Font.on(TextView)`. Over a wallpaper (Home), words get the shadow `shadow()` (`:840`).

### 2.5 Focus

| Thing | Treatment | Helper |
|---|---|---|
| **Tile** (app, game, poster) | TV Home's: ring 3 dp `tv_ring_accent` outside the face + 1 dp white inside (`RING_OUT` / `tv_ring`); card grows to **1.12** (`GROW`) on `Motion.spring` 520 ms in / glide 300 ms out; picture ×1.05 inside it; shine; name fades in under it. Optional **glass** instead of the ring (`Glass.kt`, Home setting `focus`) | Home `Tile.focus` (`HomeActivity.kt:941`); Games copies it: `shell()` + `focusTile()` (`LibraryActivity.kt:734,845`) |
| **Card / action tile** (smaller) | Same ring via `ring()` / `Ui.ring()`, scale **1.06** | Channels `Ui.states(c, fill, r)` (`channels/Ui.kt:38`); Setup `ring()` (`MainActivity.kt:1044`) |
| **Row of a plain list** | light fill `0x33FFFFFF`, no ring; *activated* (the one in use) `0x2214B8A6` | `Ui.states(..., edge = false)` / `Ui.row` |
| **Button / menu entry** | Games: dark `#2A3042` → white fill + 4 dp white edge, words go dark (`menuButton`); Home: white fill, dark words/icons (`Drop.kt:53`, `TopBar.kt:20`), ×1.06 (`Motion.pop` `:1098`) | per app (§7) |
| **Chip / choice** | rest `0x24FFFFFF`, focused `0x73FFFFFF` + ×1.05, *selected* `0xF2FFFFFF` with dark words | Games `pill()` (`LibraryActivity.kt:349`) |

### 2.6 Motion (ms)

| Move | Duration | Interpolator |
|---|---|---|
| Tile focus in / out | 520 / 300 | `Motion.spring` (damped spring, 0.62) / `Motion.glide` (`HomeActivity.kt:1075-1080`) |
| Ring fade in / out | 200 / 160 | glide |
| Glass glide | 460; first show fade 180; hide 140 | spring (`Glass.kt:80-101`) |
| Edge nudge (nothing that way) | 80 out + 460 back | glide, then `Motion.bounce` |
| Small card / tile scale | 120 (Channels, Setup), 200 (Games pill), 420 / 220 (`Motion.pop`) | default / spring |
| Overlay / hint fade | 150 | glide (`HomeActivity.kt:478-485`) |
| Page in | 240 (420 sliding) | glide (`:314`) |

Durations go through `Motion.ms(t)` (Home) / `ms(t)` (Games) so *calm* shortens them; with motion off, set the end state at once.

## 3. Components: reuse, don't rebuild

| Component | Reuse | App | Notes |
|---|---|---|---|
| **Header** (icon + app name) | `buildRail()` head (`BrowseActivity.kt:210-220`), Setup header (`MainActivity.kt:158-165`), Games (`LibraryActivity.kt:306`) | Channels, Setup, Games | Home has none (clock + date). Name: 24 sp bold `tv_ink`, never the accent |
| **List row** | `Ui.row(c, icon, label, count) { go }` | Channels | icon tinted `TEXT`, count in `DIM`; `isActivated` = the one in use |
| **Dialog sheet** (choices) | `sheet(w)` + `heading()` + `Ui.row`s (`BrowseActivity.kt:457-469`), theme `R.style.Dialog` | Channels | 380-420 dp wide; Back closes it |
| **Popup on a scrim** | `scrim(panel(title).apply { addView(menuButton(...).withIcon(R.drawable.ic_x)) })` (`games/Ui.kt:57-89`, used `LibraryActivity.kt:588`) | Games | always add a Close button; Back closes; first entry focused |
| **System list menu** | `AlertDialog.Builder(this, R.style.Menu)` / `Theme_DeviceDefault_Dialog_Alert` | Home (`:744`), Setup (`:293`) | for plain lists of actions only |
| **Drop-down from a head button** | `Drop.show(a, anchor, rows)` | Home | a Dialog: Home and Back close it |
| **App / game tile** | Home `Tile` (`HomeActivity.kt:857`); Games `shell()` | Home, Games | 16:9; rings outside the face; name under it, in the row gap |
| **Action tile** (icon, title, one line) | Setup `Tile` (`MainActivity.kt:73`): `say()`, `work(on)` spinner, `enable()` | Setup | |
| **Channel card** | `Cards` adapter (`BrowseActivity.kt:694`) | Channels | logo on `LogoFit` light/dark tile; initials when no logo (`Ui.initials`) |
| **Button** | Setup `smallButton()` (`:1075`); Games `menuButton()`; Channels `button` (bold 16 sp on `Ui.states(CARD, 22)`, `:124`) | per app | a verb, short; `isAllCaps = false` |
| **Chip / choice** | Games `pill()` | Games | `isSelected` = chosen |
| **Text field** | `EditText` with `Ui.states(c, CARD or 0x14FFFFFF, 22 / 18)`, `isSingleLine`, `IME_FLAG_NO_EXTRACT_UI` + `IME_ACTION_SEARCH` / `DONE` | Channels | see the Back pattern below |
| **Toast** | `Toast.makeText(...)`: one-line results only (`pin_refused`, `guide_none`, Home `toast()` `:836`) | all | never the only way to report an error the person must act on |
| **Notice over other apps** | `NotifyLayer` (corner card, `#F2202124` r20, 22 sp) ; `WarnActivity` (scrim + card + countdown + buttons) | Setup | corner / seconds clamped in shared `Notify.kt` |
| **Loading / empty / error** | Channels `chrome()` (`BrowseActivity.kt:373`): one full-screen state (spinner tinted accent, then the words + Retry, Retry focused); in-grid note `say()` (`:370`); Setup `Tile.work()`; Games download bar (Back cancels) (`LibraryActivity.kt:696`) | | words say what to do next (`offline`, `none_here`, `no_match`); a long wait shows progress and can be cancelled |

**Text field + keyboard + Back** (`BrowseActivity.kt:562-620`, `countries()`): the keyboard eats Back, so

1. subclass `EditText` and override `onKeyPreIme`: on `KEYCODE_BACK` + `ACTION_DOWN`, if there are results, hide the IME and move focus to the results (`toList()`), return true;
2. set a `swallowUp` flag and eat the matching key-up in `dlg.setOnKeyListener`, or the Dialog closes too;
3. D-pad Down from the field → the list; Up from the list's first row → the field; the IME action key also goes to the list;
4. empty field or no results: Back behaves normally (closes).

## 4. Words

- **Home, Channels, Games:** every visible string in `res/values/strings.xml` (English) **and** `res/values-vi/strings.xml`
  (Vietnamese), same ids. Today only `app_name` and `wifi` (Home) are values-only. No string literals in Kotlin.
- **TV Setup:** no strings.xml; `T("area.id", "n" to 3)` from `app/shared/web/i18n/vi.json` + `en.json` (the AGENTS.md rule:
  `vi.json` first, `en.json` complete). No new `t("vi", "en")` pairs.
- Short labels: a verb or a noun, 1-3 words on a button, one line in a row; details go in the sub-line (`tv_dim`), not the label.
  Check that the Vietnamese fits too: it is usually longer.
- Emoji: allowed in content and big states (Warn icons `😴 🌙 🧠 ⏳` `WarnActivity.kt:46`, Setup's rule lines, `✓`, Games `📱`).
  **Controls use line icons** (§5), not emoji.
- Numbers and names come through placeholders (`%1$d`, `{n}`), never glued strings.

## 5. Icons

- Line icons only: 24×24, stroke 2, round ends (Lucide shapes), white, tinted in code (`imageTintList` / `setTintList`).
- Add one in `dev/gen/gen_icons.py` (`ICONS`, or `GAME_ICONS`), run `python dev/gen/gen_icons.py`: it writes `ic_*.xml` into
  android, tv, home, channels and games (games only the names in `GAMES`, `gen_icons.py:154`) and the web sprite.
  **Never hand-edit `res/drawable/ic_*.xml`** (each says "generated by dev/gen/gen_icons.py").
- Sizes: 20 dp in a row (`Ui.row`), 22 dp in a menu button (`withIcon`), 26 dp badge in a Setup tile; icon colour = the words' colour
  (`setTintList(textColors)` so it turns dark with the words under a white focus).
- App icons and banners: `banner.png` / `icon.png` per app; rounded corners from `dev/gen/round_icons.py`.

## 6. Checklist before "done"

1. `dev/test/emu.sh` (or `install`), screenshots from **`tvremote_gtv`**: `adb -s emulator-5554 exec-out screencap -p > shot.png`.
   Look at each new or changed screen: no cut or overflowing label, no wrapped row, focus visible, inside the 48 × 27 dp margins.
2. **D-pad walk** with `adb shell input keyevent` (19-22 arrows, 23 OK, 4 Back, 82 Menu; hold OK = `--longpress 23`): every control
   reachable, none skipped, no focus trap, Back closes each layer in order, hold OK / Menu open the item's menu.
3. **Both languages**: Vietnamese and English (Channels: its Language row; the others: the TV's language). Long Vietnamese words still fit.
4. **Motion off** (`settings put global animator_duration_scale 0`): the end states are right with no animation.
5. Text fields: open the keyboard, press Back, check the focus lands on the results and the dialog stays.
6. `node --test app/shared/tests/` (`tv_design.test.js`: no new raw colour outside a named constant, one `tv_style.xml`, words in both languages, generated icons); unit tests of the app (`./gradlew :<app>:testDebugUnitTest`); TV Setup words: `node --test app/shared/tests/` (`i18n.test.js`).
7. Report what was checked where, and what was not (a real 1 GB box, a real remote's long press, overscan on a real panel).

## 7. Known drift (documented, not fixed: don't copy it, don't add more)

Raw colours are counted per file in `app/shared/tests/tv_design.test.js` (`RAW`): a count may only go down.

| # | What | Where |
|---|---|---|
| 1 | TV Home has no `tv_style.xml`: ring colour, tile radius and margins are hard-coded | `HomeActivity.kt:130,877,1012-1013` |
| 2 | Two focus lifts: TV Home and Games tiles grow **1.12×** on a 520 ms spring, Channels cards and Setup tiles **1.06×** in 120 ms (§2.5 keeps both: big tiles vs small cards; don't add a third) | `HomeActivity.kt:967`, `LibraryActivity.kt:917`, `BrowseActivity.kt:726`, `MainActivity.kt:112` |
| 3 | Muted grey: `tv_dim` `#9AA0A6` vs Setup `MUTED` `#8C96A3`, Warn `#AAB2BD`, Games `#AAB0C0` `#8892A6` `#6B7385` `#9AA3B8` `#D5DAE6`, Home white-alpha `0xA6FFFFFF` / `0x8CFFFFFF` | `MainActivity.kt:66`, `WarnActivity.kt:61`, `LibraryActivity.kt:265,276`, `LobbyView.kt:36-46`, `Widgets.kt:75`, `Drop.kt:27` |
| 4 | Panel fill / radius: `tv_card` r16 (Games) vs Channels sheets `0xF21B2028` r20 + hairline, Warn `#191D23` r20, Notify `#F2202124` r20, Home Drop gradient r20, Customize r28, Home/Setup system AlertDialogs | `games/Ui.kt:54`, `BrowseActivity.kt:395,462,567`, `WarnActivity.kt:44`, `NotifyLayer.kt:81`, `Drop.kt:37`, `Customize.kt:270` |
| 5 | Scrim: Games `0xD90E1116` vs Warn black at 200/255 | `games/Ui.kt:69`, `WarnActivity.kt:39` |
| 6 | Button focus: ring (tiles) vs Games white fill + 4 dp white edge vs Setup / Warn solid accent fill `#3B73F6` + 2 dp white vs Home white fill | `games/Ui.kt:27,77`, `MainActivity.kt:1083`, `WarnActivity.kt:138-139`, `Drop.kt:53` |
| 7 | App accent differs: Channels teal `#5EEAD4` (and activated `0x2214B8A6`), Setup `#3B73F6`, Home `#8AB4F8`, Games lobby `#4F8CFF` | `channels/Ui.kt:18,42`, `MainActivity.kt:67`, `home/styles.xml`, `LobbyTheme.kt:11` |
| 8 | Tile radius: `tv_radius_tile` 10 vs Channels logo tile 12, player tiles 10 / 6 | `BrowseActivity.kt:696-733`, `PlayActivity.kt:105,394` |
| 9 | Danger red: Channels `#F28B82`, Setup `#FF7070`, Home lock `#FF8A80`, Games `#F87171` | `channels/Ui.kt:21`, `MainActivity.kt:70`, `HomeActivity.kt:1024`, `GameCards.kt:142` |
| 10 | Text below the 12 sp floor / small: Setup tile sub-line 11 sp, title 13 sp | `MainActivity.kt:100-104` |
| 11 | Fields: Channels `Ui.states` r22 / r18; Games search hint `0x99FFFFFF` on its own look, rename field `focusBg(10, #2A3042, #3B4460)` | `BrowseActivity.kt:94,551`, `LibraryActivity.kt:308,609` |
| 12 | Language: Channels has an in-app picker (`Lang.kt`); Home and Games follow the TV; Setup follows the TV through `Words` | |
| 13 | Font: TV Home bundles Inter (`Font.kt`); the others use the system font | |
| 14 | Games `LobbyView` predates the family look (its own bg `#0B0D12`, greys, sizes up to 150 sp) | `LobbyView.kt:36-67` |

Fixing drift is a separate change: pick the reference value (§0), change every copy and this table.

## 8. Phone app's native screens (`app/android/.../phone/`)

They mirror the web UI (`app/shared/web`, `css/kit.css`), not the TV apps.

- Colours only from `Palette.kt` (`dark` and `light`, both checked by `PaletteContrastTest.kt`), copied into `Ui.kt` by
  `applyPalette()`. No raw hex in a screen.
- **Two blues**, as on the page: the full `ACCENT` fill = a button that *does* something (OK, Send, Install); *on / chosen* =
  `setOn(on, radius = 14)` (`Ui.kt:120`): `onBox` = `SEL` tint + 1.5 dp `ACCENT` edge, words `ACCENT_TEXT` (`Ui.kt:116`). Never the fill for "on".
- Build with the helpers in `Ui.kt`: `box()`, `button()`, `label()`, `head()`, `note()`, `row()`, `input()`, `inDialog()`,
  `setBusy()`, `say()`; icons via `icon()` / `IconButton` from the same `ic_*` drawables.
- Words: `T("id")` from `i18n/*.json` (`core/Words.kt`); `t(vi, en)` (`Ui.kt:76`) is legacy, no new pairs.
- A change to the remote's keys or menus goes in `app/shared/web/remote.json` (read by `PhoneRemote.kt`), not by hand; any other web
  UI change is mirrored by hand here, or the change says why it can't be (AGENTS.md).
- Check on the phone emulator `tvremote_test`, both themes and both languages, with screenshots.
