// node --test app/shared/tests/   Google Authenticator's secret (js/kids.js): base32 as RFC 4648 and the link the QR carries.
// Totp.kt on the TV reads the same text, so the RFC's own vectors are used.
const test = require('node:test');
const assert = require('node:assert/strict');
const { base32, otpauthUrl, groupSecret } = require('../web/js/kids.js');
const bytes = (s) => [...Buffer.from(s)];

test('base32 gives RFC 4648 without padding', () => {
  assert.equal(base32(bytes('')), '');
  assert.equal(base32(bytes('f')), 'MY');
  assert.equal(base32(bytes('fo')), 'MZXQ');
  assert.equal(base32(bytes('foobar')), 'MZXW6YTBOI');
  assert.equal(base32(bytes('12345678901234567890')), 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ');   // RFC 6238's SHA1 key
});

test('20 bytes make a 32-letter secret, shown in groups of 4', () => {
  const s = base32(new Array(20).fill(255));
  assert.equal(s.length, 32);
  assert.equal(groupSecret(s), '7777 7777 7777 7777 7777 7777 7777 7777');
});

test('the otpauth link names the TV and the issuer', () => {
  assert.equal(otpauthUrl('ABCD', 'Phòng khách'), 'otpauth://totp/TV%20Remote:Ph%C3%B2ng%20kh%C3%A1ch?secret=ABCD&issuer=TV%20Remote');
  assert.match(otpauthUrl('ABCD', ''), /^otpauth:\/\/totp\/TV%20Remote:TV\?/);
});
