// node --test app/shared/tests/   The page's scripts share one global scope: a name declared twice (let/const/class, or one of
// those and a function) stops the later file from running at all, which only shows as a broken tab in the browser.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

test('the page scripts declare no name twice', () => {
  const web = path.join(__dirname, '../web');
  const html = fs.readFileSync(path.join(web, 'index.html'), 'utf8');
  const srcs = [...html.matchAll(/<script[^>]*\ssrc="([^"?]+)/g)].map((m) => m[1]);
  assert.ok(srcs.length > 5, 'found the page scripts');
  // one script, in page order: a redeclaration is an early SyntaxError, found by compiling without running
  const all = srcs.map((s) => `// ${s}\n` + fs.readFileSync(path.join(web, s), 'utf8')).join('\n;\n');
  assert.doesNotThrow(() => new vm.Script(all, { filename: 'page' }));
});

// The intro site (docs/) is published on its own and cannot reach app/shared, so it carries a copy of qr.js: the copy
// must stay the same, apart from its first line saying where it comes from
test('docs/ has the same qr.js as the page', () => {
  const root = path.join(__dirname, '../../..');
  const copy = ['docs/js/qr.js'].map((f) => path.join(root, f)).find((f) => fs.existsSync(f));
  assert.ok(copy, 'docs/js/qr.js is missing');
  const body = fs.readFileSync(copy, 'utf8').split('\n').slice(1).join('\n');
  assert.equal(body, fs.readFileSync(path.join(__dirname, '../web/js/qr.js'), 'utf8'),
    'docs/js/qr.js differs from app/shared/web/js/qr.js: copy it again (keeping its first line)');
});

test('a field of the page is written through put()', () => {
  // $('#x').textContent = … in a draw that runs every tick writes the same words again and again: each is a change for the
  // page's observers and WebKit's styles (the now playing bar 5 times a second). put() writes only what changed.
  const dir = path.join(__dirname, '../web/js');
  const bad = fs.readdirSync(dir).filter((f) => f.endsWith('.js')).flatMap((f) => {
    const s = fs.readFileSync(path.join(dir, f), 'utf8');
    return [...s.matchAll(/\$\('[^']+'\)\.(textContent|hidden|className|disabled|title) = /g)].map((m) => f + ':' + s.slice(0, m.index).split('\n').length);
  });
  assert.deepEqual(bad, [], "put('#x', 'textContent', …) instead");
});
