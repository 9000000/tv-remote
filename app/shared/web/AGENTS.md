# app/shared/web: the web UI (Computer app, TV web page, Phone app's Manage screen)

Read with the root [AGENTS.md](../../../AGENTS.md).

## Rules of the page

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
- **How to write the words** (plain, per language, not translated word by word): `app/shared/i18n/WRITING.md`.
- **Vietnamese is the source language; words go by id.** `app/shared/i18n/<lang>.json` holds every word of the page under an
  id (`area.words`): `vi.json` first, then the same ids in each language (`en.json` complete: it is every other one's fallback).
  A language is its files plus its line in `i18n/langs.json` (the ONE list: `js/i18n.js` reads it, `i18n/apps/<lang>/<module>.json` holds the native apps' words, see the root AGENTS.md). In `index.html` an element keeps its Vietnamese and names the id
  (`data-i18n`, `data-i18n-html`, `data-i18n-title|aria-label|placeholder|alt`); scripts write `T('id', { n })` (`{n}` in the
  words, `{ one, other }` for plurals); a script writes no words of its own (`tests/i18n.test.js` fails on one).
  Kotlin reads the same files (`core/Words.kt`): `T("id", "n" to 3)` in the app's language (`Words.lang`: the phone's choice,
  the TV's locale), `Words.get(r.lang, "id")` for words a page asked for (`r.lang`: the page's `tvr-lang` cookie). No new
  `t("vi", "en")` pairs. What the server sends comes in the page's language already; an English error from a TV or a library
  gets its words in `friendly()` (`js/core.js`).
  README.md (English) and README.vi.md (Vietnamese) are kept in step; CHANGELOG entries and release notes are written in both languages.

The design reference these rules come from: [DESIGN.md](DESIGN.md).
