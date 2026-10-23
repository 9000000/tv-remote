// node --test app/shared/tests/   The page's error box (js/errbox.js): an uncaught error or a rejected promise shows one box with
// the message, the stack and Copy; a resource that failed to load and the cross-origin "Script error." do not.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function page() {
  const listeners = {}, mk = (tag) => ({ tag, children: [], className: '', textContent: '', attrs: {}, on: {},
    append(...c) { this.children.push(...c); }, setAttribute(k, v) { this.attrs[k] = v; }, addEventListener(e, f) { this.on[e] = f; },
    remove() { this.removed = true; const i = body.children.indexOf(this); if (i >= 0) body.children.splice(i, 1); }, focus() {}, select() {} });
  const body = mk('body'), clip = [];
  const window = { addEventListener: (e, f) => { listeners[e] = f; } };
  const sandbox = { window, document: { createElement: mk, body, execCommand: () => true }, location: { href: 'http://x/', search: '' },
    navigator: { userAgent: 'test', clipboard: { writeText: (t) => { clip.push(t); return Promise.resolve(); } } } };
  vm.createContext(sandbox);
  vm.runInContext(fs.readFileSync(path.join(__dirname, '../web/js/errbox.js'), 'utf8'), sandbox);
  const text = (e, out = []) => { out.push(e.textContent); e.children.forEach((c) => text(c, out)); return out.join('\n'); };
  return { listeners, body, clip, text, window };
}

test('an uncaught error shows a box with the message and the stack', () => {
  const p = page();
  p.listeners.error({ message: 'x is not a function', error: Object.assign(new Error('x is not a function'), { stack: 'Error: x\n    at a (a.js:1:1)\n    at b (b.js:2:2)' }), lineno: 1 });
  assert.equal(p.body.children.length, 1);
  const t = p.text(p.body.children[0]);
  assert.match(t, /x is not a function/);
  assert.match(t, /at a \(a\.js:1:1\)/);
  assert.match(t, /err\.title/);                                      // T() is not loaded here: the id stands in for the words
});

test('a rejected promise shows one too', () => {
  const p = page();
  p.listeners.unhandledrejection({ reason: new Error('nobody awaited me') });
  assert.match(p.text(p.body.children[0]), /nobody awaited me/);
});

test('a resource that failed to load, the cross-origin "Script error." and ResizeObserver are ignored', () => {
  const p = page();
  p.listeners.error({ message: '', target: { nodeType: 1, tagName: 'IMG' } });
  p.listeners.error({ message: 'Script error.', lineno: 0 });
  p.listeners.error({ message: 'ResizeObserver loop completed with undelivered notifications.' });
  assert.equal(p.body.children.length, 0);
});

test('the same error again is one box, counted; another one replaces the words', () => {
  const p = page();
  const e = { message: 'boom', error: Object.assign(new Error('boom'), { stack: 'Error: boom\n    at f (f.js:1:1)' }) };
  p.listeners.error(e); p.listeners.error(e); p.listeners.error(e);
  assert.equal(p.body.children.length, 1);
  assert.match(p.text(p.body.children[0]), /×3/);
  p.listeners.error({ message: 'other', error: new Error('other') });
  assert.equal(p.body.children.length, 1);
  assert.match(p.text(p.body.children[0]), /other/);
});

test('Copy puts the whole report on the clipboard and Close removes the box', async () => {
  const p = page();
  p.listeners.error({ message: 'boom', error: Object.assign(new Error('boom'), { stack: 'Error: boom\n' + Array.from({ length: 20 }, (_, i) => `    at f${i} (f.js:${i}:1)`).join('\n') }) });
  const box = p.body.children[0], row = box.children[3];
  row.children[0].on.click();
  await Promise.resolve();
  assert.match(p.clip[0], /boom/);
  assert.match(p.clip[0], /at f19/);                                  // the screen shows 6 lines, the clipboard all of it
  assert.doesNotMatch(p.text(box), /at f19/);
  row.children[1].on.click();
  assert.equal(p.body.children.length, 0);
});
