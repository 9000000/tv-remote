// node --test app/shared/tests/   The picture steps down when the browser keeps falling behind (js/screen.js fellBehind) and back
// up, one size at a time, after a quiet spell: never above the person's pick, a longer wait after a step up that did not hold.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const src = fs.readFileSync(path.join(__dirname, '../web/js/screen.js'), 'utf8');
const code = src.slice(src.indexOf('const PIC_STEPS'), src.indexOf('let behindAt'));
const sb = {}; vm.createContext(sb);
vm.runInContext(code.replace(/^const /gm, 'var ') + '\nthis.picNext = picNext; this.picWaitAfter = picWaitAfter; this.U = PIC_UP_MS; this.MAX = PIC_UP_MAX;', sb);
const { picNext, picWaitAfter, U, MAX } = sb;

test('steps up one size only after the wait', () => {
  const s = { mode: '480', chosen: '960', calmFrom: 0, wait: U };
  assert.equal(picNext(s, U - 1), null);
  assert.equal(picNext(s, U), '720');
});
test('never above the chosen size, and not at it', () => {
  assert.equal(picNext({ mode: '960', chosen: '960', calmFrom: 0, wait: U }, 1e9), null);
  assert.equal(picNext({ mode: '720', chosen: '480', calmFrom: 0, wait: U }, 1e9), null);
  assert.equal(picNext({ mode: '720', chosen: '960', calmFrom: 0, wait: U }, 1e9), '960');
  assert.equal(picNext({ mode: 'snap', chosen: '960', calmFrom: 0, wait: U }, 1e9), null);
});
test('climbs to hd step by step', () => {
  let mode = '240', t = 0; const seen = [];
  for (let n = 0; n < 9 && mode !== 'hd'; n++) { t += U; const up = picNext({ mode, chosen: 'hd', calmFrom: t - U, wait: U }, t); if (!up) break; mode = up; seen.push(mode); }
  assert.deepEqual(seen, ['360', '480', '720', '960', 'hd']);
});
test('falling behind soon after a step up doubles the wait, up to a cap', () => {
  assert.equal(picWaitAfter(U, 1000, 1000 + 59000), U * 2);
  assert.equal(picWaitAfter(U, 1000, 1000 + 61000), U);
  assert.equal(picWaitAfter(U, 0, 5000), U);                         // no step up before
  assert.equal(picWaitAfter(MAX, 1000, 2000), MAX);
});
