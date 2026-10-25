// node --test app/shared/tests/   a theme file's own photos and videos (js/home_media.js): the plan, the key remap, the rollback, the badges.
const test = require('node:test');
const assert = require('node:assert/strict');
const M = require('../web/js/home_media.js');

const jpg = 'data:image/jpeg;base64,' + Buffer.alloc(300).toString('base64');
const room = { photos: 5, videos: 2, videoMax: 200 * 1048576 };

test('keys: the wall then its rotation, own ones only, each once', () => {
  assert.deepEqual(M.homeMediaKeys({ wall: 'photo2', walls: ['photo', 'photo2', 'night', 'vid:0123abcd'] }), ['photo2', 'photo', 'vid:0123abcd']);
  assert.deepEqual(M.homeMediaKeys({ wall: 'night' }), []);
});

test('parse: data urls by kind, https only on the allowed hosts', () => {
  assert.equal(M.homeMediaParse(jpg, 'photo').bytes, 300);
  assert.equal(M.homeMediaParse(jpg, 'video'), null);
  assert.ok(M.homeMediaParse('https://upload.wikimedia.org/a.jpg', 'photo').url);
  assert.equal(M.homeMediaParse('https://evil.example/a.jpg', 'photo'), null);
  assert.equal(M.homeMediaParse('http://upload.wikimedia.org/a.jpg', 'photo'), null);
  assert.equal(M.homeMediaParse('https://images.pexels.com/x.mp4', 'video'), null);   // videos.pexels.com is the video host
});

test('plan: size cap, room, bad item', () => {
  const conf = { wall: 'photo', walls: ['photo', 'photo2'] };
  assert.equal(M.homeMediaPlan(conf, { photo: jpg, photo2: jpg }, room).items.length, 2);
  assert.equal(M.homeMediaPlan(conf, { photo: jpg, photo2: jpg }, { ...room, photos: 1 }).error.id, 'home.theme_media_room');
  assert.equal(M.homeMediaPlan(conf, { photo: 'junk' }, room).error.id, 'home.theme_media_bad');
  const big = 'data:video/mp4;base64,' + 'A'.repeat(Math.ceil(61 * 1048576 * 4 / 3));
  assert.equal(M.homeMediaPlan({ wall: 'vid:0123abcd' }, { 'vid:0123abcd': big }, room).error.id, 'home.theme_media_big');
  assert.deepEqual(M.homeMediaPlan(conf, undefined, room).items, []);
});

test('remap: keys follow, a missing item becomes the default background', () => {
  const r = M.homeMediaRemap({ wall: 'photo2', walls: ['photo2', 'vid:0123abcd'], x: 1 }, { photo2: 'photo', 'vid:0123abcd': 'vid:ffffffff' });
  assert.deepEqual(r.conf, { wall: 'photo', walls: ['photo', 'vid:ffffffff'], x: 1 }); assert.equal(r.lost, false);
  const l = M.homeMediaRemap({ wall: 'photo', walls: ['photo', 'night'] }, {});
  assert.deepEqual(l.conf, { wall: 'night', walls: ['night'] }); assert.equal(l.lost, true);
});

test('import: uploads in order and takes back what it added when one fails', async () => {
  const items = [{ key: 'photo', kind: 'photo' }, { key: 'photo2', kind: 'photo' }];
  const have = []; const dropped = [];
  const d = (failAt) => ({
    cancelled: () => false, say() {}, blobOf: async () => new Blob(['x'], { type: 'image/jpeg' }), toJpeg: async (b) => b,
    read: async () => ({ photos: have.map((id) => ({ id })) }), drop: async (id) => { dropped.push(id); },
    photo: async () => { if (have.length === failAt) throw new Error('boom'); have.push('p' + have.length); return { photos: have.map((id) => ({ id })) }; },
  });
  assert.deepEqual(await M.homeMediaImport(items, d(9)), { photo: 'p0', photo2: 'p1' });
  have.length = 0;
  await assert.rejects(M.homeMediaImport(items, d(1)), /boom/);
  assert.deepEqual(dropped, ['p0']);
});

test('cancel stops before the next item and rolls back', async () => {
  let n = 0; const dropped = [];
  const d = { cancelled: () => n >= 1, say() {}, blobOf: async () => new Blob(['x'], { type: 'image/jpeg' }), read: async () => ({ photos: [] }), drop: async (id) => dropped.push(id),
    photo: async () => { n++; return { photos: [{ id: 'photo' }] }; } };
  await assert.rejects(M.homeMediaImport([{ key: 'photo', kind: 'photo' }, { key: 'photo2', kind: 'photo' }], d), /cancelled/);
  assert.deepEqual(dropped, ['photo']);
});

test('file parts read back as the same theme', async () => {
  const parts = M.homeMediaFile('N', { wall: 'photo' }, { photo: 'data:image/jpeg;base64,QUJD' });
  const d = JSON.parse(await new Blob(parts).text());
  assert.deepEqual(d, { tvtheme: 1, name: 'N', conf: { wall: 'photo' }, media: { photo: 'data:image/jpeg;base64,QUJD' } });
  assert.deepEqual(JSON.parse(M.homeMediaFile('N', { wall: 'night' }, {}).join('')), { tvtheme: 1, name: 'N', conf: { wall: 'night' } });
});

test('export refuses an item over the cap before reading', async () => {
  await assert.rejects(M.homeMediaExport({ wall: 'photo' }, { photo: 61 * 1048576 }, {}), /media_big/);
});

test('badges: video, rotation, slideshow', () => {
  assert.deepEqual(M.homeMediaBadges({ wall: 'vid:0123abcd' }).map((x) => x.id), ['home.badge_video']);
  assert.deepEqual(M.homeMediaBadges({ wall: 'a', walls: ['a', 'b', 'c'] }), [{ id: 'home.badge_photos', args: { n: 3 } }]);
  assert.deepEqual(M.homeMediaBadges({ wall: 'web', wurl: 'https://tvremote.pages.dev/home/loop.html?s=vietnam' }).map((x) => x.id), ['home.badge_slides']);
  assert.deepEqual(M.homeMediaBadges({ wall: 'web', wurl: 'https://tvremote.pages.dev/home/loop.html?v=x.mp4' }).map((x) => x.id), ['home.badge_video']);
  assert.deepEqual(M.homeMediaBadges({ wall: 'night' }), []);
});

test('weigh: counts and bytes, inexact when a size is missing', () => {
  const w = M.homeMediaWeigh({ wall: 'photo', walls: ['photo', 'vid:0123abcd'] }, { photos: [{ id: 'photo', size: 1000 }], video: { items: [{ id: 'vid:0123abcd' }] } });
  assert.deepEqual([w.photos, w.videos, w.bytes, w.exact], [1, 1, 1000, false]);
});
