// node --test app/shared/tests/   Health › History: where the red "network lost" bands go (stats.js statsCuts).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const sb = { window: {}, document: {}, localStorage: {} };
vm.createContext(sb);
vm.runInContext(fs.readFileSync(path.join(__dirname, '../web/stats.js'), 'utf8'), sb);
const cuts = (...a) => JSON.parse(JSON.stringify(sb.window.statsCuts(...a)));   // out of the vm realm
const to = 1e6, from = to - 86400;
const sample = (t, kind) => [t, 1, 10, 1000, 2000, 1, 2, 'pkg', -50, kind, 100];

test('one outage of a few minutes is a narrow span', () => {
  const t = from + 40000;
  assert.deepEqual(cuts([[t, 0, '', 1], [t + 300, 1, 'wifi', 1]], [], from, to), [[t, t + 300]]);
});
test('a lost with no back is closed by the next sample with a link, not stretched to the end', () => {
  const t = from + 40000;
  const rows = [sample(t - 60, 'wifi'), sample(t + 60, ''), sample(t + 180, 'wifi'), sample(to - 30, 'wifi')];
  assert.deepEqual(cuts([[t, 0, '', 1]], rows, from, to), [[t, t + 180]]);
});
test('a lost with no back and no link since stays open only while samples are fresh', () => {
  const t = from + 40000;
  assert.deepEqual(cuts([[t, 0, '', 1]], [sample(to - 30, '')], from, to), [[t, null]]);
  assert.deepEqual(cuts([[t, 0, '', 1]], [sample(t + 120, '')], from, to), [[t, t + 120]]);
});
test('an old unclosed lost that ended before the window is dropped; one while the TV slept is not a fault', () => {
  const old = from - 5000;
  assert.deepEqual(cuts([[old, 0, '', 1]], [sample(from + 100, 'wifi')], from, to), [[old, from + 100]]);
  assert.deepEqual(cuts([[old, 0, '', 1]], [sample(old + 100, 'wifi')], from, to), []);
  assert.deepEqual(cuts([[from + 10, 0, '', 0], [from + 400, 1, 'wifi', 0]], [], from, to), []);
});
