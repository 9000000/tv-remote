# Add a TV Home card (widget)

Read first: [app/home/AGENTS.md](../../app/home/AGENTS.md) (cards, "Every element has its own small menu"),
[app/DESIGN-TV.md](../../app/DESIGN-TV.md) ("Tonal cards"). Model to copy: `adblock` (a card with an action) or `lunar` (display only).

1. **Key and options**, the ONE shape file `app/shared/src/home/kotlin/dev/tvremote/home/HomeConfig.kt`: add the key to `WIDGETS`
   (the order is the default order) and to `TONAL` if it takes the tonal look. Anything beyond size / look: a case in
   `options(k)` (`Opt` id, values, default). A card not for every Android: gate it in `schema(sdk)` like `ADBLOCK_MIN_SDK`.
   Update the KDoc list of widgets at the top of the file. It is compiled into :shared, :desktop and :home, so all three see it.
2. **Draw it** in `app/home/src/main/kotlin/dev/tvremote/home/widgets/`: a `<Name>Widget.kt` beside `AdblockWidget.kt`, then the
   `when (k)` branches in `Widgets.kt` (build, `BIG` if it has a big size, the click at `setOnClickListener`). Data from TV Setup:
   HomeInfo (needs `SetupNeed.MIN`, see [api-route.md](api-route.md)); without it the card says to install or update TV Setup.
3. **TV side screens**: `app/home/src/main/kotlin/dev/tvremote/home/Customize.kt` (the Widgets list: icon, name id, subtitle id),
   `WidgetSettings.kt` (the options dialog is built from `options(k)`; add a row only for a note) and `HomeActivity.kt`
   (`elementMenu` items use the SAME `HomeConfig` keys Customize writes; the card is a focus stop, also in edit mode).
4. **The web page**: `app/shared/web/js/home.js`: `HOME_WIDGETS` (same order as `WIDGETS`) and `HOME_WIDGET_WORDS`. Options render
   from `/api/home` `schema`, so nothing else hardcodes them.
5. **Words by id**, no `t("vi", "en")`: `app/shared/i18n/apps/<lang>/home.json` (`w_<key>`, `c_<key>_sub`, `o_*`, `ov_*`) for
   every language in `app/shared/i18n/langs.json`, and `home.w_<key>` in `app/shared/i18n/vi.json` + each `<lang>.json`.
6. **Icon**, if new: `dev/gen/gen_icons.py` (never edit `ic_*.xml` by hand), then regenerate.
7. **Guide**: the topic `home-widgets` in `docs/guide.html`: describe the card (40-word limit) and set `data-v="next"`.
   README.md / README.vi.md "TV Home" in step; notes under `## Unreleased` in both languages
   ([dev/release/AGENTS.md](../release/AGENTS.md)).
8. **Tests**: a case in `app/shared/tests/home_opts.test.js` (options and defaults) or a new test like
   `app/shared/tests/home_adblock.test.js`; then `node --test app/shared/tests/` and
   `cd app/gradle && ./gradlew :shared:testDebugUnitTest :home:testDebugUnitTest`.
9. **Look at it**: `dev/test/emu.sh` (TV emulator), screenshot the card in each size, focused, its menu and in Customize
   ([dev/test/AGENTS.md](../test/AGENTS.md)).

**Done =** TV emulator (card on Home, its OK / menu, Customize, an old TV Setup if it uses HomeInfo) + the web page's Home tab in
Chrome at 1100 and 400 px (Computer app) + the Phone app's Manage screen if the page changed. Report what could not be run.
