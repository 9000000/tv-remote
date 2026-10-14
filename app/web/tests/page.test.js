// node --test app/web/tests/   The page's scripts share one global scope: a name declared twice (let/const/class, or one of
// those and a function) stops the later file from running at all, which only shows as a broken tab in the browser.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

test('the page scripts declare no name twice', () => {
  const web = path.join(__dirname, '../../shared/web');
  const html = fs.readFileSync(path.join(web, 'index.html'), 'utf8');
  const srcs = [...html.matchAll(/<script[^>]*\ssrc="([^"?]+)/g)].map((m) => m[1]);
  assert.ok(srcs.length > 5, 'found the page scripts');
  // one script, in page order: a redeclaration is an early SyntaxError, found by compiling without running
  const all = srcs.map((s) => `// ${s}\n` + fs.readFileSync(path.join(web, s), 'utf8')).join('\n;\n');
  assert.doesNotThrow(() => new vm.Script(all, { filename: 'page' }));
});
