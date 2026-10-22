// The web games keep docs/games/web/DESIGN.md: JS and CSS an old TV WebView runs, no pointer, nothing from other hosts, one set of seat colours.
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..', '..');
const WEB = path.join(ROOT, 'docs/games/web');
const files = (ext) => fs.readdirSync(WEB, { withFileTypes: true }).filter((d) => d.isDirectory())
  .flatMap((d) => fs.readdirSync(path.join(WEB, d.name)).filter((f) => f.endsWith(ext)).map((f) => path.join(d.name, f)));
const read = (f) => fs.readFileSync(path.join(WEB, f), 'utf8');
const code = (s) => s.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:'"\\])\/\/.*$/gm, '$1').replace(/'(?:\\.|[^'\\\n])*'|"(?:\\.|[^"\\\n])*"|`(?:\\.|[^`\\])*`/g, '""');

test('game scripts run in an old WebView: plain ES2017, strict, no modules', () => {
  const bad = [];
  for (const f of files('.js')) {
    const s = read(f), c = code(s);
    if (f !== path.join('_lib', 'qr.js') && !/^'use strict';/.test(s)) bad.push(`${f}: starts with 'use strict';`);
    if (/\?\.[\w$[(]/.test(c)) bad.push(`${f}: ?. (optional chaining)`);
    if (/\?\?/.test(c)) bad.push(`${f}: ?? (nullish)`);
    if (/catch\s*\{/.test(c)) bad.push(`${f}: catch without (e)`);
    if (/^\s*(import|export)\s/m.test(c)) bad.push(`${f}: a module`);
    for (const fn of ['alert', 'confirm', 'prompt']) {
      if (new RegExp(`(^|[^\\w$.])${fn}\\(`).test(c) && !new RegExp(`function ${fn}\\(`).test(c)) bad.push(`${f}: ${fn}() (nothing answers it on a TV)`);
    }
  }
  assert.deepStrictEqual(bad, []);
});

test('game styles: no single translate / rotate / scale properties (Chrome 104+), no fonts or files from other hosts', () => {
  const bad = [];
  for (const f of files('.css')) {
    const c = read(f).replace(/\/\*[\s\S]*?\*\//g, '');
    if (/(^|[;{\s])(translate|rotate|scale)\s*:/.test(c)) bad.push(`${f}: translate/rotate/scale as a property: use transform`);
    if (/@import|@font-face|url\(\s*['"]?https?:/.test(c)) bad.push(`${f}: a font or file from elsewhere`);
  }
  for (const f of files('.html')) {
    if (/<(script|link|img)[^>]+(src|href)="https?:/.test(read(f))) bad.push(`${f}: loads from another host`);
  }
  assert.deepStrictEqual(bad, []);
});

test('the TV hides the pointer in every game (nopad.js gives it back in a plain browser)', () => {
  const kit = /cursor\s*:\s*none/.test(read('_lib/kit.css'));
  const bad = fs.readdirSync(WEB, { withFileTypes: true }).filter((d) => d.isDirectory() && d.name !== '_lib').map((d) => d.name).filter((g) => {
    const html = read(path.join(g, 'index.html'));
    const css = files('.css').filter((f) => f.startsWith(g + path.sep)).some((f) => /cursor\s*:\s*none/.test(read(f)));
    return !(css || (kit && html.includes('_lib/kit.css')));
  });
  assert.deepStrictEqual(bad, []);
});

test('every game page loads qr.js, then nopad.js', () => {
  const bad = fs.readdirSync(WEB, { withFileTypes: true }).filter((d) => d.isDirectory() && d.name !== '_lib').map((d) => d.name).filter((g) => {
    const h = read(path.join(g, 'index.html')), q = h.indexOf('_lib/qr.js'), n = h.indexOf('_lib/nopad.js');
    return q < 0 || n < q;
  });
  assert.deepStrictEqual(bad, []);
});

test('one set of seat colours: the pad\'s, Table\'s and Kit\'s', () => {
  const list = (s, name) => (s.match(new RegExp(`const ${name} = (\\[[^\\]]*\\])`)) || [])[1];
  const pad = list(fs.readFileSync(path.join(ROOT, 'app/games/src/main/assets/pad/pad.js'), 'utf8'), 'COLORS');
  assert.ok(pad, 'pad.js COLORS');
  assert.strictEqual(list(read('_lib/table.js'), 'COLORS'), pad, 'table.js COLORS = pad.js COLORS');
  assert.strictEqual(list(read('_lib/kit.js'), 'PCOL'), pad, 'kit.js PCOL = pad.js COLORS');
});
