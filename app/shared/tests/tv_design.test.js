// The TV apps keep app/DESIGN-TV.md: no new raw colour in a view (named constants only), one tv_style.xml, every word in both languages, icons generated.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..', '..');
const APPS = ['tv', 'home', 'channels', 'games'];
const walk = (d) => fs.readdirSync(d, { withFileTypes: true })
  .flatMap((e) => e.isDirectory() ? walk(path.join(d, e.name)) : e.name.endsWith('.kt') ? [path.join(d, e.name)] : []);

// Raw colours outside a named constant (`val CARD = 0xFF1B2028.toInt()`), per file, as they were when the rule came in
// (DESIGN-TV.md "Known drift"). A count may only go down: lower it here when a file is cleaned.
const RAW = {
  tv: { 'DrawLayer.kt': 2, 'LauncherConfirmActivity.kt': 6, 'MainActivity.kt': 5, 'ManageActivity.kt': 3, 'NotifyLayer.kt': 3, 'PairActivity.kt': 6,
    'PinActivity.kt': 8, 'WarnActivity.kt': 6 },
  home: { 'Customize.kt': 42, 'Drop.kt': 7, 'Frost.kt': 3, 'HomeActivity.kt': 59, 'HomeDream.kt': 4, 'TopBar.kt': 2, 'WallArt.kt': 3,
    'Widgets.kt': 41, 'Zoom.kt': 3 },
  channels: { 'BrowseActivity.kt': 13, 'Pins.kt': 2, 'PlayActivity.kt': 8, 'ReminderNotice.kt': 1, 'Ui.kt': 2 },
  games: { 'GameActivity.kt': 4, 'GameCards.kt': 4, 'LibraryActivity.kt': 39, 'LobbyTheme.kt': 1, 'LobbyView.kt': 46, 'Pins.kt': 3, 'Systems.kt': 4,
    'Ui.kt': 4, 'WebGameActivity.kt': 4 },
};
const COLOUR = /0x[0-9A-Fa-f]{8}\b|Color\.(rgb|argb|parseColor)\(/g;
const NAMED = /^\s*(private |internal |public )?(const )?va[lr] [A-Z][A-Z0-9_]*\s*[:=]/;
const raw = (f) => fs.readFileSync(f, 'utf8').split('\n')
  .reduce((n, l) => NAMED.test(l) ? n : n + (l.replace(/\/\/.*$/, '').match(COLOUR) || []).length, 0);

test('no new raw colour in the TV apps\' views: a colour is a named constant or tv_style.xml', () => {
  const bad = [];
  for (const a of APPS) for (const f of walk(path.join(ROOT, 'app', a, 'src', 'main'))) {
    const n = raw(f), allowed = RAW[a][path.basename(f)] || 0;
    if (n > allowed) bad.push(`app/${a}/${path.relative(path.join(ROOT, 'app', a), f)}: ${n} raw colours (allowed ${allowed})`);
    if (n < allowed) bad.push(`app/${a}/.../${path.basename(f)}: down to ${n}: lower RAW in tv_design.test.js`);
  }
  assert.deepStrictEqual(bad, [], 'name it beside the others (Channels Ui, Setup BG…BAD, Home\'s consts) or use tv_style.xml:\n' + bad.join('\n'));
});

// Views that draw themselves (onDraw / dispatchDraw), per file: a hand-drawn view is how a new screen drifts from the
// components (DESIGN-TV.md §3): the RAM tile and its own frame, 2026-10-23. A count may only go down; a new one needs a reason here.
const DRAWN = {
  tv: { 'DrawLayer.kt': 1 },                                          // drawing over the TV: strokes, nothing to reuse
  home: { 'Widgets.kt': 8, 'HomeActivity.kt': 1, 'QrCard.kt': 1, 'Glass.kt': 1, 'Zoom.kt': 1, 'Customize.kt': 1, 'Tiles.kt': 1 },
  android: { 'RemoteScreen.kt': 1, 'IconButton.kt': 1, 'AirMouse.kt': 2, 'Drawing.kt': 1 },
};

test('no new hand-drawn view: build from the components (DESIGN-TV.md §3, phone: Ui.kt)', () => {
  const bad = [];
  for (const a of ['tv', 'home', 'channels', 'games', 'android']) {
    for (const f of walk(path.join(ROOT, 'app', a, 'src', 'main'))) {
      const n = (fs.readFileSync(f, 'utf8').match(/override fun (onDraw|dispatchDraw)\(/g) || []).length;
      const allowed = (DRAWN[a] || {})[path.basename(f)] || 0;
      if (n > allowed) bad.push(`app/${a}/.../${path.basename(f)}: ${n} drawn view(s), ${allowed} allowed: reuse a component, or add it to DRAWN with why`);
      if (n < allowed) bad.push(`app/${a}/.../${path.basename(f)}: down to ${n}: lower DRAWN in tv_design.test.js`);
    }
  }
  assert.deepEqual(bad, []);
});

test('tv_style.xml is the same in every TV app that has one', () => {
  const copies = ['tv', 'channels', 'games'].map((a) => fs.readFileSync(path.join(ROOT, 'app', a, 'src/main/res/values/tv_style.xml'), 'utf8'));
  copies.forEach((c, i) => assert.strictEqual(c, copies[0], `app/${['tv', 'channels', 'games'][i]} tv_style.xml differs from app/tv's`));
});

test('every word of TV Home, TV Channels and TV Games is in English and Vietnamese', () => {
  const SAME = new Set(['app_name', 'wifi']);                        // the same in both languages: kept in values only
  const ids = (f) => new Set([...fs.readFileSync(f, 'utf8').matchAll(/<(?:string|plurals|string-array)[^>]*name="([^"]+)"[^>]*>/g)]
    .filter((m) => !/translatable="false"/.test(m[0])).map((m) => m[1]));
  for (const a of ['home', 'channels', 'games']) {
    const en = ids(path.join(ROOT, 'app', a, 'src/main/res/values/strings.xml'));
    const vi = ids(path.join(ROOT, 'app', a, 'src/main/res/values-vi/strings.xml'));
    assert.deepStrictEqual([...en].filter((x) => !vi.has(x) && !SAME.has(x)), [], `app/${a}: missing in values-vi`);
    assert.deepStrictEqual([...vi].filter((x) => !en.has(x)), [], `app/${a}: missing in values`);
  }
});

test('the TV apps\' line icons all come from dev/gen/gen_icons.py', () => {
  const bad = [];
  for (const a of APPS) {
    const d = path.join(ROOT, 'app', a, 'src/main/res/drawable');
    for (const f of fs.existsSync(d) ? fs.readdirSync(d) : []) {
      if (/^ic_.*\.xml$/.test(f) && !fs.readFileSync(path.join(d, f), 'utf8').includes('generated by dev/gen/gen_icons.py')) bad.push(`app/${a}/${f}`);
    }
  }
  assert.deepStrictEqual(bad, [], 'add it to dev/gen/gen_icons.py, never by hand');
});
