// The APK jump alarm's rule (dev/test/apk_sizes.js): 10% and 256 KB, or 1 MB, over the baseline.
const test = require('node:test');
const assert = require('node:assert/strict');
const { verdict } = require('../../../dev/test/apk_sizes.js');

const MB = 1048576;
test('small growth and shrinking pass', () => {
  assert.equal(verdict(1 * MB, 1.05 * MB), 'ok');
  assert.equal(verdict(1 * MB, 0.5 * MB), 'ok');
});
test('10% and 256 KB over is a jump; a tiny app over 10% is not', () => {
  assert.equal(verdict(2 * MB, 2.3 * MB), 'jump');
  assert.equal(verdict(800 * 1024, 800 * 1024 + 100 * 1024), 'ok');
});
test('over 1 MB is a jump whatever the percent', () => {
  assert.equal(verdict(20 * MB, 21.1 * MB), 'jump');
});
