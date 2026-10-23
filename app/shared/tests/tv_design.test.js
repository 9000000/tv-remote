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
  channels: { 'BrowseActivity.kt': 4, 'Pins.kt': 2, 'PlayActivity.kt': 8, 'ReminderNotice.kt': 1 },
  games: { 'GameActivity.kt': 4, 'GameCards.kt': 4, 'LibraryActivity.kt': 34, 'LobbyTheme.kt': 1, 'LobbyView.kt': 46, 'Pins.kt': 3, 'Systems.kt': 4,
    'WebGameActivity.kt': 4 },
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

// The shared look (app/shared/src/tvui): tv_style.xml and Tv.kt are the ONE copy; channels and games build them in (res.srcDirs / srcDirs).
const TVUI = path.join(ROOT, 'app', 'shared', 'src', 'tvui');

test('tv_style.xml: one copy in tvui; app/tv keeps an identical one until it moves; channels and games have none of their own', () => {
  const one = fs.readFileSync(path.join(TVUI, 'res/values/tv_style.xml'), 'utf8');
  assert.strictEqual(fs.readFileSync(path.join(ROOT, 'app/tv/src/main/res/values/tv_style.xml'), 'utf8'), one, 'app/tv tv_style.xml differs from tvui\'s');
  for (const a of ['channels', 'games']) {
    assert.ok(!fs.existsSync(path.join(ROOT, 'app', a, 'src/main/res/values/tv_style.xml')), `app/${a} has its own tv_style.xml: delete it, tvui's is built in`);
    assert.match(fs.readFileSync(path.join(ROOT, 'app', a, 'build.gradle.kts'), 'utf8'), /res\.srcDirs\("\.\.\/shared\/src\/tvui\/res"\)/, `${a} must build tvui/res in`);
  }
});

test('Tv.kt\'s colours are tv_style.xml\'s, and it holds no raw colour outside a named constant', () => {
  const xml = fs.readFileSync(path.join(TVUI, 'res/values/tv_style.xml'), 'utf8');
  const kt = fs.readFileSync(path.join(TVUI, 'kotlin/dev/tvremote/tvui/Tv.kt'), 'utf8');
  const kv = (n) => (kt.match(new RegExp('const val ' + n + ' = 0x([0-9A-Fa-f]{8})')) || [])[1];
  for (const [xmlName, k] of [['tv_bg', 'BG'], ['tv_card', 'CARD'], ['tv_ink', 'TEXT'], ['tv_dim', 'DIM'], ['tv_ring_accent', 'RING_ACCENT']]) {
    const x = (xml.match(new RegExp('name="' + xmlName + '">#([0-9A-Fa-f]{8})')) || [])[1];
    assert.ok(x && kv(k), xmlName + ' / ' + k + ' missing');
    assert.strictEqual(kv(k).toUpperCase(), x.toUpperCase(), `${k} in Tv.kt is not ${xmlName} of tv_style.xml`);
  }
  for (const f of walk(TVUI)) assert.strictEqual(raw(f), 0, `${path.basename(f)}: a raw colour: name it in Tv.kt`);
});

// The components live in tvui and nowhere else: a hand-built look-alike row / sheet / panel is how the apps drifted (DECISIONS.md, 2026-10-23).
test('channels and games define no component tvui already has', () => {
  const names = ['row', 'sheet', 'sheetTitle', 'sheetNote', 'states', 'ring', 'field', 'panel', 'panelBg', 'scrim', 'menuButton', 'withIcon',
    'pill', 'pillText', 'focusBg', 'heading', 'label'];   // (a game's own initials() / a pad button() are other things)
  const bad = [];
  for (const a of ['channels', 'games']) for (const f of walk(path.join(ROOT, 'app', a, 'src', 'main'))) {
    const src = fs.readFileSync(f, 'utf8');
    for (const n of names) if (new RegExp('fun (\\w+\\.)?' + n + '\\(').test(src)) bad.push(`app/${a}/.../${path.basename(f)}: fun ${n}( : use tvui's`);
    if (/object Ui\b/.test(src)) bad.push(`app/${a}/.../${path.basename(f)}: object Ui: the look is tvui's TvUi`);
  }
  assert.deepStrictEqual(bad, []);
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
