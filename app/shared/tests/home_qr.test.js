// node --test app/shared/tests/   TV Home's QR templates: js/home_qr.js builds the text of every case of home_qr_cases.json (HomeQrTest.kt
// holds the Kotlin side to the same table), the card sent is the template's own fields, and what the form builds the encoder draws.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const web = path.join(__dirname, '../web/js');
const sandbox = { TextEncoder, T: (id) => id, module: undefined };
vm.createContext(sandbox);
vm.runInContext(fs.readFileSync(path.join(web, 'home_qr.js'), 'utf8').replace(/^(let|const) /gm, 'var ') + '\nthis.payload = homeQrPayload; this.card = homeQrCard; this.bytes = homeQrBytes;', sandbox);
const { cases } = JSON.parse(fs.readFileSync(path.join(__dirname, 'home_qr_cases.json'), 'utf8'));
const { qrModules } = require('../web/js/qr.js');

for (const c of cases) test(c.name, () => assert.equal(sandbox.payload(c.tpl, c.f), c.payload));

test('a card keeps only its template\'s fields, a caption of one line, and "show" only for a Wi-Fi', () => {
  const k = sandbox.card('wifi', { ssid: 'A', pass: 'b', sec: 'bogus', hidden: 1, url: 'x', extra: 1 }, '  my \n  wifi ', true);
  assert.deepEqual(JSON.parse(JSON.stringify(k)), { tpl: 'wifi', f: { ssid: 'A', pass: 'b', sec: 'WPA', hidden: true }, cap: 'my wifi', show: true });
  assert.equal(sandbox.card('link', { url: 'a.b' }, '', true).show, false);
});

test('every case that builds a code fits the encoder, and a Wi-Fi password gets through whole', () => {
  for (const c of cases) if (c.payload && sandbox.bytes(c.payload) <= 271) assert.ok(qrModules(c.payload), c.name);
  assert.equal(qrModules('x'.repeat(272)), null);
});
