// node --test app/shared/tests/   The page draws QR codes with js/qr.js, the apps with Qr.kt: both must give the codes of
// qr_cases.json (QrTest.kt checks the Kotlin side), so a phone scans the same thing from either
const test = require('node:test');
const assert = require('node:assert/strict');
const { qrModules } = require('../web/js/qr.js');
const { cases } = require('./qr_cases.json');

test('qr.js draws the codes of qr_cases.json', () => {
  for (const c of cases) {
    const m = qrModules(c.text);
    assert.deepEqual(m && m.map((r) => r.map((b) => (b ? 1 : 0)).join('')), c.rows, c.text);
  }
});
