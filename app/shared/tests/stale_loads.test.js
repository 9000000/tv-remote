// node --test app/shared/tests/   A list still on its way from the old TV (or an older folder) must not replace what the person
// looks at now: apps.js and files.js drop an answer that is no longer the newest call / the same TV.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const js = (f) => fs.readFileSync(path.join(__dirname, '../web/js', f), 'utf8');
// the source of one top-level function, up to its closing brace at column 0
const fn = (src, name) => { const m = src.match(new RegExp('^(async )?function ' + name + '\\b[\\s\\S]*?^}', 'm')); assert.ok(m, name); return m[0]; };
// the real onTvChange(...) statement of a file whose first line holds `marker`: what a test runs is the code the page runs, so
// deleting a reset from the file turns the test red
const tvChange = (src, marker) => {
  const lines = src.split('\n');
  for (let i = 0; i < lines.length; i++) {
    if (!lines[i].startsWith('onTvChange(')) continue;
    const one = /\);\s*(\/\/.*)?$/.test(lines[i]) && !lines[i].endsWith('{');
    const j = one ? i : lines.findIndex((l, k) => k > i && l.startsWith('});')), block = lines.slice(i, j + 1).join('\n');
    if (block.includes(marker)) return block;
  }
  assert.fail('no onTvChange block with ' + marker);
};
const later = () => new Promise((r) => setTimeout(r, 0));

function sandbox(extra) {
  const resets = [], calls = [], waiting = [];
  const stub = () => ({ replaceChildren() {}, dataset: {}, checked: false, value: '' });
  const ctx = vm.createContext({
    resets, calls, waiting, el: () => ({}), T: (x) => x, $: stub, put() {}, META: new Map(), metaFailed: new Map(), appsIcons: new Map(),
    onTvChange: (f) => resets.push(f), seedMeta() {}, renderApps() {}, renderPlaces() {}, renderFiles() {}, filterQuick() {},
    bootRead: () => new Set(), quickLoaded: true,
    api: (p) => new Promise((ok) => { calls.push(p); waiting.push(ok); }), ...extra,
  });
  return ctx;
}

test('apps.js: the old TV\'s app list that arrives after a TV change is dropped', async () => {
  const src = js('apps.js');
  const ctx = sandbox();
  vm.runInContext([
    'let appsLoaded = false, appsAll = [], appsInfo = new Map(), appsSys = null, appsSysBusy = false, appsBoot = new Set(), quickLoaded = true;',
    'let appsGen = 0, quickApps = [];', src.match(/^const appsStale = .*$/m)[0],
    tvChange(src, 'appsGen++'),
    fn(src, 'loadApps'), 'this.loadApps = loadApps; this.state = () => ({ appsLoaded, appsAll });',
  ].join('\n'), ctx);
  const p = ctx.loadApps(true);
  ctx.resets.forEach((f) => f());                                  // the person picks another TV
  ctx.waiting[0]({ apps: [{ pkg: 'old.tv.app' }] });
  await p; await later();
  assert.equal(ctx.state().appsAll.length, 0,'the old TV\'s apps are not shown');
  assert.equal(ctx.state().appsLoaded, false, 'so the new TV is read again');
});

test('files.js: a slow folder does not replace the one opened after it', async () => {
  const src = js('files.js');
  const ctx = sandbox();
  vm.runInContext([
    "let cwd = '/sdcard/Download', filesLoaded = false, fileItems = [], volumes = [], filesGen = 0;",
    'onTvChange(() => { filesGen++; });',
    fn(src, 'loadFiles'), 'this.loadFiles = loadFiles; this.cwd = () => cwd;',
  ].join('\n'), ctx);
  const a = ctx.loadFiles('/sdcard/A'), b = ctx.loadFiles('/sdcard/B');
  ctx.waiting[1]({ path: '/sdcard/B', items: [] });                // B answers first
  await b; ctx.waiting[0]({ path: '/sdcard/A', items: [] });       // then the slower A
  await a; await later();
  assert.equal(ctx.cwd(), '/sdcard/B');
});

test('apps.js: Quick launch drops the old TV\'s apps that arrive after a TV change, and forgets them', async () => {
  const src = js('apps.js');
  const ctx = sandbox();
  vm.runInContext([
    'let quickLoaded = false, quickApps = ["old.tv.app"], appsGen = 0, shown = null;',
    'const appsStale = () => { quickLoaded = appsLoaded = false; }; let appsSysBusy = false;', tvChange(src, 'appsGen++'),
    '$ = () => ({ replaceChildren(...x) { shown = x; } });',
    fn(src, 'loadQuick'), 'this.loadQuick = loadQuick; this.st = () => ({ quickLoaded, quickApps, shown });',
  ].join('\n'), ctx);
  const p = ctx.loadQuick();
  ctx.resets.forEach((f) => f());
  vm.runInContext('shown = null', ctx);                              // the reset itself draws its "apps appear when ADB…" line; what matters is what the late answer adds
  ctx.waiting[0]({ apps: [{ pkg: 'old.tv.app' }] });
  await p; await later();
  assert.equal(ctx.st().quickApps.length, 0);
  assert.equal(ctx.st().shown, null, 'nothing of the old TV is drawn');
  assert.equal(ctx.st().quickLoaded, false, 'so the new TV is read again');
});

test('health.js: the settings list read from the old TV is dropped after a TV change', async () => {
  const src = js('health.js');
  const ctx = sandbox({ ADB: { state: 'ready' }, settingsRead: () => [{ name: 'old' }], drawSettings() {}, el: () => ({}) });
  vm.runInContext([
    'let setRows = [], setBusy = false, setGen = 0;', 'onTvChange(() => { setGen++; setBusy = false; setRows = []; });',
    fn(src, 'loadSettings'), 'this.loadSettings = loadSettings; this.st = () => ({ setRows, setBusy });',
  ].join('\n'), ctx);
  const p = ctx.loadSettings();
  ctx.resets.forEach((f) => f());
  assert.equal(ctx.st().setBusy, false, 'the new TV may be read at once');
  ctx.waiting[0]({ text: 'x' });
  await p; await later();
  assert.equal(ctx.st().setRows.length, 0);
});

test('auto.js: rules read from the old TV are dropped after a TV change', async () => {
  const src = js('auto.js');
  let drawn = 0;
  const ctx = sandbox({ renderRules: () => { drawn++; } });
  vm.runInContext([
    'let RULES = null, rulesSkew = 0, rulesBusy = false, rulesGen = 0;', 'onTvChange(() => { rulesGen++; rulesBusy = false; RULES = null; });',
    fn(src, 'loadRules'), 'this.loadRules = loadRules; this.st = () => ({ RULES, rulesBusy });',
  ].join('\n'), ctx);
  const p = ctx.loadRules(null, true);
  ctx.resets.forEach((f) => f());
  ctx.waiting[0]({ now: 1 });
  assert.equal(await p, null); await later();
  assert.equal(ctx.st().RULES, null); assert.equal(drawn, 0);
});

test('core.js: an older state read that ends after a newer one is dropped', async () => {
  const src = js('core.js');
  const answers = [];
  const ctx = sandbox({ stopped: false, invited: null, CORE: 'pc', setLocked() {}, tvOn: false, STATE: null, stateMiss: 0, setTimeout() {},
    timeoutSignal: () => undefined, applyKind() {}, setChip() {}, NATIVE: () => false, setPicking() {}, showNowApp() {}, ADB_TEXT: { ready: ['', '', ''] }, tvChanged() {},
    fetch: () => new Promise((ok) => answers.push(ok)), picking: false, curHost: null, justPicked: null, volHold: 0 });
  // stop right after the state is stored: what matters is which answer got that far
  vm.runInContext(fn(src, 'refresh').replace('STATE = s || {};', 'STATE = s || {}; return;') + '\nlet seqOk = 0; this.refresh = refresh;', ctx);
  vm.runInContext('let refreshSeq = 0, refreshP = null;', ctx);
  const a = ctx.refresh(), b = ctx.refresh();
  assert.equal(answers.length, 2);
  const res = (host) => ({ status: 200, json: async () => ({ host, connected: true, is_on: true, adb: { state: 'ready' } }) });
  answers[1](res('new')); await b;                                 // newer answers first
  answers[0](res('old')); await a; await later();
  assert.equal(ctx.STATE.host, 'new');
});

test('core.js: a read a newer one replaced ends only when the newer one has stored its state', async () => {
  const src = js('core.js');
  const answers = [];
  const ctx = sandbox({ stopped: false, invited: null, CORE: 'pc', setLocked() {}, tvOn: false, STATE: null, stateMiss: 0, setTimeout() {},
    timeoutSignal: () => undefined, applyKind() {}, setChip() {}, NATIVE: () => false, setPicking() {}, showNowApp() {}, ADB_TEXT: { ready: ['', '', ''] }, tvChanged() {},
    fetch: () => new Promise((ok) => answers.push(ok)), picking: false, curHost: null, justPicked: null, volHold: 0 });
  vm.runInContext(fn(src, 'refresh').replace('STATE = s || {};', 'STATE = s || {}; return;') + '\nlet refreshSeq = 0, refreshP = null; this.refresh = refresh;', ctx);
  const res = (host) => ({ status: 200, json: async () => ({ host, connected: true, is_on: true, adb: { state: 'ready' } }) });
  const a = ctx.refresh(), b = ctx.refresh();
  let aDone = false; a.then(() => { aDone = true; });
  answers[0](res('old')); await later(); await later();             // the replaced read is answered; the newer one is not yet
  assert.equal(aDone, false, 'an awaiter of the replaced call keeps waiting');
  answers[1](res('new')); await a; await b;
  assert.equal(ctx.STATE.host, 'new', 'and then reads the fresh state');
});

test('core.js: refresh settles when the TV never answers (timeout signal, old WebView fallback)', async () => {
  const src = js('core.js');
  const sig = []; let chips = 0;
  const ctx = sandbox({ stopped: false, invited: null, CORE: 'pc', setLocked() {}, STATE: { host: 'tv' }, stateMiss: 0, setTimeout() {}, put() {}, setChip: () => { chips++; },
    fetch: (u, o) => new Promise((ok, no) => { sig.push(o && o.signal); o.signal.addEventListener('abort', () => no(new Error('timeout'))); }) });
  vm.runInContext(fn(src, 'refresh') + '\n' + src.match(/^function timeoutSignal[\s\S]*?^}/m)[0] + '\nlet refreshSeq = 0, refreshP = null; this.refresh = refresh;', ctx);
  // a WebView without AbortSignal.timeout: a controller and a timer
  ctx.AbortSignal = { }; ctx.AbortController = AbortController; ctx.setTimeout = (f, ms) => { assert.ok(ms <= 20000); return setTimeout(f, 1); };
  let polling = true;
  await Promise.race([ctx.refresh().finally(() => { polling = false; }), new Promise((_, no) => setTimeout(() => no(new Error('refresh hung')), 2000))]);
  assert.equal(polling, false); assert.ok(sig[0], 'the read carries a signal');
  assert.equal(ctx.STATE.host, 'tv', 'a read with no answer yet keeps the last good state'); assert.equal(chips, 0, 'and the chips: it is not "no TV"');
  await ctx.refresh(); assert.equal(chips, 0, 'two slow reads: still not "no TV"');
  await ctx.refresh(); assert.ok(chips > 0, 'a TV that never answers (off the network) ends in "disconnected"');
});

test('core.js: the pairing box is cleared and focused after Start', () => {
  const src = js('core.js');
  const line = src.split('\n').find((l) => l.includes("$('#paircode').value = ''"));
  assert.ok(line && !line.includes('//'), 'not swallowed by a comment');
});

test('home.js: the unused wall helpers are gone', () => {
  const src = js('home.js');
  assert.ok(!/homeOwnWall|wallFace/.test(src));
});
