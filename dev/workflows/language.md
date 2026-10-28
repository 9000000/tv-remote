# Add a UI language

Read first: "No language is the source" in the root [AGENTS.md](../../AGENTS.md). A language is its files plus ONE line;
no `strings.xml`, no language list in code. Model: `ru` (the newest).

1. **The line**: add `"<code>": "<Native name>"` to `app/shared/i18n/langs.json`. Kotlin gets `dev.tvremote.i18n.Langs` generated
   from it, `app/shared/web/js/i18n.js` reads it, and Gradle builds `res/values-<code>` from `app/shared/i18n/apps/`
   (`app/gradle/i18n.gradle`).
2. **The page and `T()`**: `app/shared/i18n/<code>.json` with every id of `app/shared/i18n/vi.json`, the same `{placeholders}`
   and plural shape (`{ one, other }`; some languages need more forms). Start from `app/shared/i18n/en.json`, then translate.
3. **The native apps**: `app/shared/i18n/apps/<code>/` with each module file the English folder has (`home`, `games`, `channels`,
   `tvkeys`, `android`, `tvui`): the same keys, the same `%s` / `%1$d`.
4. **Not translated, on purpose**: product names and TV Setup's installed-TV labels (Downloader codes, file names).
   `docs/` (the site) stays vi / en.
5. **Fonts and width**: look for a script the UI font lacks (CJK, Arabic, Thai) and for right-to-left; long words must wrap in
   the TV apps (`app/shared/tests/long_words.json` holds the stress words).
6. **Tests**: `node --test app/shared/tests/` (`app/shared/tests/i18n.test.js`: one file per language, same ids and placeholders,
   every module present) and `cd app/gradle && ./gradlew :shared:testDebugUnitTest` (the generated `Langs`).
7. README.md / README.vi.md in step if they name the languages; CHANGELOG under `## Unreleased` in both languages
   ([dev/release/AGENTS.md](../release/AGENTS.md)).

**Done =** Phone app on the phone emulator with the language chosen (native screens + Manage), the Computer app's page in Chrome at
1100 and 400 px with the language picked, TV Setup / TV Home / TV Channels / TV Games on the TV emulator with the locale set
(`dev/test/emu.sh`). Screenshots looked at: nothing cut, wrapped or clipped; report what was not checked.
