// node --test app/shared/tests/   The crop of a photo made the TV's shape (js/photofit.js), the one shared by the web UI's upload and
// the page TV Home serves to a phone: it must fill the frame exactly, stay inside the photo, and centre by default.
const test = require('node:test');
const assert = require('node:assert/strict');
const { photoRect } = require('../web/js/photofit.js');

test('a wide photo is cropped at the sides, centred, in the frame\'s 16:9', () => {
  const r = photoRect(4000, 1500, 1920, 1080);
  assert.ok(Math.abs(r.sw / r.sh - 16 / 9) < 1e-9);
  assert.equal(r.sh, 1500);
  assert.equal(r.sx, (4000 - r.sw) / 2);
});
test('a portrait photo is cropped top and bottom', () => {
  const r = photoRect(3000, 4000, 1920, 1080);
  assert.equal(r.sw, 3000);
  assert.equal(r.sy, (4000 - r.sh) / 2);
});
test('zoom narrows the crop and a drag stops at the photo\'s edge', () => {
  const r = photoRect(4000, 3000, 1920, 1080, { z: 2, cx: 5, cy: -5 });
  assert.equal(r.sx + r.sw, 4000);
  assert.equal(r.sy, 0);
  assert.equal(r.sw, photoRect(4000, 3000, 1920, 1080).sw / 2);
});
