// node --test app/shared/tests/   TV Home's settings page asks one seam (js/home_link.js) where its data is: through the hosting app's
// /api over ADB, or straight to TV Home's own /h/ routes (the page home.html, "Manage from phone"). Both ways must call the right
// endpoints, and what a side does not have must answer null without calling anything.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { HOME_PATHS, homeLinkFor } = require('../web/js/home_link.js');

const rig = (direct) => {
  const calls = [];
  const call = async (p, d, q) => { calls.push([p, d === null || d === undefined ? null : d, !!q]); return { installed: true }; };
  const send = async (p, b) => { calls.push(['PUT ' + p, b]); return { installed: true }; };
  return { calls, link: homeLinkFor(direct, call, send) };
};

test('through the host: the /api/home routes', async () => {
  const { calls, link } = rig(false);
  await link.read(false, true); await link.read(true); await link.write({ clock24: true }); await link.drop('photo2');
  await link.qr(); await link.update(); await link.walls(true); await link.wall('w1'); await link.photo('jpeg'); await link.setup('home_usage');
  assert.deepEqual(calls, [
    ['/api/home', null, true], ['/api/home?thumbs=1', null, false], ['/api/home', { clock24: true }, false], ['/api/home/photo/drop', { id: 'photo2' }, false],
    ['/api/home/photo/qr', {}, false], ['/api/home/update', {}, false], ['/api/home/walls', null, true], ['/api/home/wall', { id: 'w1' }, false],
    ['PUT /api/home/photo', 'jpeg'], ['/api/setup/need?f=home_usage', null, false]]);
  assert.ok(link.canUpdate && link.canGallery && link.canQr && !link.direct);
  assert.equal(link.app.icon, '/api/app/icon?pkg=');
});

test("direct: TV Home's own /h routes, and nothing for what only a host has", async () => {
  const { calls, link } = rig(true);
  await link.read(true); await link.write({ shade: 40 }); await link.drop('photo');
  await link.photo('jpeg');
  assert.deepEqual(calls, [['/h/config?thumbs=1', null, false], ['/h/config', { shade: 40 }, false], ['/h/photo/drop', { id: 'photo' }, false], ['PUT /h/photo', 'jpeg']]);
  calls.length = 0;
  assert.equal(await link.qr(), null); assert.equal(await link.update(), null); assert.equal(await link.walls(true), null); assert.equal(await link.wall('w1'), null);
  assert.equal(await link.setup('home_usage'), true);                  // no TV Setup to ask: the card says what it lacks
  assert.deepEqual(calls, [], 'no call for a route this side does not have');
  assert.ok(link.direct && !link.canUpdate && !link.canGallery && !link.canQr);
  assert.equal(link.app.meta, '/h/app/meta?pkg=');
});

test('every route TV Home answers is one its server has', () => {
  // ManageServer.kt (app/home) names each /h/ route in its `when`; a route the page asks and the TV lacks would be a 404 in the field
  const kt = fs.readFileSync(path.join(__dirname, '../../home/src/main/kotlin/dev/tvremote/home/manage/ManageServer.kt'), 'utf8');
  const d = HOME_PATHS.direct;
  for (const p of [d.read, d.drop, d.photo, d.app.meta.split('?')[0], d.app.icon.split('?')[0]]) assert.ok(kt.includes('"' + p + '"'), p + ' is not served by ManageServer.kt');
});

test('home.html loads only files that exist, and home.js asks no /api path itself', () => {
  const web = path.join(__dirname, '../web');
  const html = fs.readFileSync(path.join(web, 'home.html'), 'utf8');
  const files = [...html.matchAll(/(?:src|href)="((?:js|css)\/[^"?]+)"/g)].map((m) => m[1]);
  assert.ok(files.length > 8);
  for (const f of files) assert.ok(fs.existsSync(path.join(web, f)), f);
  assert.ok(files.indexOf('js/home_link.js') < files.indexOf('js/homeweb.js') && files.indexOf('js/homeweb.js') < files.indexOf('js/appicon.js'), 'order: link, glue, app icons');
  assert.ok(files.indexOf('js/home.js') > files.indexOf('js/homeweb.js'));
  assert.ok(!/['"`]\/api\//.test(fs.readFileSync(path.join(web, 'js/home.js'), 'utf8')), 'home.js goes through homeLink');
});
