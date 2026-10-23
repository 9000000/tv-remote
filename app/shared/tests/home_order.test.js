// node --test app/shared/tests/   TV Home's grid order as the page draws it (js/home_order.js): the same order HomeActivity.show
// lays out (the old favourites first, then "order", the rest by name), and what Save sends.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const web = path.join(__dirname, '../web/js');
const sandbox = { module: undefined };
vm.createContext(sandbox);
const load = (f) => vm.runInContext(fs.readFileSync(path.join(web, f), 'utf8').replace(/^(let|const) /gm, 'var '), sandbox);
load('target.js'); load('home_order.js');
const apps = [{ pkg: 'com.b.tube', label: 'YouTube' }, { pkg: 'com.a.flix', label: 'Netflix' }, { pkg: 'com.c.kodi', label: 'Kodi' }, { pkg: 'com.d.spot', label: 'Spotify' }];
const order = (c) => JSON.parse(JSON.stringify(sandbox.homeGridOrder({ hidden: [], dock: [], order: [], tiles: [], ...c }, apps)));

test('nothing set: by name, as the TV sorts its apps', () => {
  assert.deepEqual(order({}), ['com.c.kodi', 'com.a.flix', 'com.d.spot', 'com.b.tube']);
});

test('the old favourites first, then the order, the apps it does not list after it by name; hidden ones not at all', () => {
  const g = order({ dock: ['com.b.tube', 'gone.app'], order: ['com.d.spot'], hidden: ['com.c.kodi'] });
  assert.deepEqual(g, ['com.b.tube', 'com.d.spot', 'com.a.flix']);
});

test('live tiles and shortcuts on the grid take a place like an app', () => {
  const g = order({ tiles: ['tile:dev.tvremote.setup/cpu', 'pin:com.x/abc'], tilesAvail: [{ key: 'tile:dev.tvremote.setup/cpu', title: 'CPU' }], order: ['tile:dev.tvremote.setup/cpu'] });
  assert.equal(g[0], 'tile:dev.tvremote.setup/cpu');
  assert.equal(g.length, 6); assert.ok(g.includes('pin:com.x/abc'));
});

test('Save sends the whole order and empties the favourites', () => {
  assert.deepEqual(JSON.parse(JSON.stringify(sandbox.homeOrderChange(['a.b', 'c.d']))), { dock: [], order: ['a.b', 'c.d'] });
});
