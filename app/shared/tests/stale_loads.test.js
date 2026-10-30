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
    'let appsGen = 0;', 'const appsStale = () => { quickLoaded = appsLoaded = false; };',
    'onTvChange(() => { appsGen++; appsSysBusy = false; appsStale(); });',
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
