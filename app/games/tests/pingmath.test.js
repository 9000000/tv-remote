const { test } = require('node:test');
const assert = require('node:assert');
const { median, Tracker, level } = require('../src/main/assets/webgames/_lib/pingmath.js');

test('median', () => {
  assert.strictEqual(median([]), null);
  assert.strictEqual(median([5]), 5);
  assert.strictEqual(median([9, 1, 5]), 5);
  assert.strictEqual(median([10, 20, 30, 40]), 25);
});
test('tracker keeps the last 5 and takes the median rtt', () => {
  const t = new Tracker(5);
  [10, 12, 200, 11, 13, 14].forEach((r, i) => t.add(1000 * i, 1000 * i + r, 0));
  assert.strictEqual(t.size(), 5);
  assert.strictEqual(t.ping(), 13);                       // 12 200 11 13 14 -> 13
});
test('offset: TV clock 500 ms ahead, symmetric 20 ms trip', () => {
  const t = new Tracker();
  t.add(1000, 1020, 1010 + 500);
  assert.strictEqual(t.offset(), 500);
});
test('offset comes from the shortest trip', () => {
  const t = new Tracker();
  t.add(0, 100, 50 + 300 + 30);                           // slow, lopsided: offset 330 (wrong by 30)
  t.add(1000, 1010, 1005 + 300);                          // fast: offset 300
  assert.strictEqual(t.offset(), 300);
});
test('no TV stamp -> no offset; level bands', () => {
  const t = new Tracker(); t.add(0, 10, null);
  assert.strictEqual(t.offset(), null);
  assert.deepStrictEqual([null, 5, 29, 30, 79, 80].map(level), ['none', 'good', 'good', 'mid', 'mid', 'bad']);
});
