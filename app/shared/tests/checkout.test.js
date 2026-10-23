// node --test app/shared/tests/   Check-out's page side (js/checkout.js): the result token of core/ontv/Checkout.kt, the steps, the app list.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { checkoutParse, checkoutPieces, checkoutStep, checkoutRows } = require('../web/js/checkout.js');

test('the result token is read as Checkout.Result.token() writes it', () => {
  assert.deepEqual(checkoutParse('checkout:c=3,f=1,v=25,w=old,n=0,h=1'), { cleared: 3, failed: 1, volume: 25, web: 'old', forgot: 0, home: true });
  assert.equal(checkoutParse('uninstalled 2'), null);
  assert.equal(checkoutParse(undefined), null);
});

test('the line says an old or missing TV Setup, never leaves it out', () => {
  const ids = (m) => checkoutPieces(m).map((p) => p[0]);
  assert.deepEqual(ids('checkout:c=2,f=0,v=25,w=ok,n=3,h=1'), ['fleet.co_cleared', 'fleet.co_vol', 'fleet.co_web_ok', 'fleet.co_home']);
  assert.deepEqual(ids('checkout:c=2,f=0,v=25,w=ok,n=0,h=1'), ['fleet.co_cleared', 'fleet.co_vol', 'fleet.co_web_0', 'fleet.co_home']);
  assert.ok(ids('checkout:c=2,f=0,v=25,w=old,n=0,h=1').includes('fleet.co_web_old'));
  assert.ok(ids('checkout:c=0,f=0,v=25,w=missing,n=0,h=1').includes('fleet.co_web_missing'));
  assert.ok(ids('checkout:c=0,f=0,v=25,w=failed,n=0,h=1').includes('fleet.co_web_failed'));
  assert.deepEqual(ids('checkout:c=1,f=2,v=25,w=ok,n=1,h=0'), ['fleet.co_cleared', 'fleet.co_failed', 'fleet.co_vol', 'fleet.co_web_ok']);
  assert.equal(checkoutPieces('installed'), null);
});

test('a step under way names its words and counts', () => {
  assert.deepEqual(checkoutStep('checkout clear 2/5'), ['fleet.co_step_clear', { i: 2, n: 5 }]);
  assert.equal(checkoutStep('checkout web')[0], 'fleet.co_step_web');
  assert.equal(checkoutStep('checkout rm -rf'), null);
});

test('the app list: blocked never offered, saved choice wins, else installed suggestions are ticked', () => {
  const tvs = [{ apps: ['com.netflix.ninja', 'dev.tvremote.setup', 'org.x.game'] }, { apps: ['com.netflix.ninja', 'com.spotify.tv.android'] }];
  const blocked = ['dev.tvremote.setup'];
  const suggest = ['com.google.android.youtube.tv', 'com.netflix.ninja', 'com.spotify.tv.android'];
  const never = checkoutRows(tvs, blocked, null, suggest);
  assert.deepEqual(never.map((r) => r.pkg), ['com.netflix.ninja', 'com.spotify.tv.android', 'org.x.game']);
  assert.deepEqual(never.map((r) => r.on), [true, true, false]);
  assert.equal(never[0].count, 2);
  const kept = checkoutRows(tvs, blocked, ['org.x.game'], suggest);                 // the owner unticked Netflix once: remembered
  assert.deepEqual(kept.map((r) => [r.pkg, r.on]), [['org.x.game', true], ['com.netflix.ninja', false], ['com.spotify.tv.android', false]]);
  assert.deepEqual(checkoutRows(tvs, blocked, [], suggest).filter((r) => r.on), []);   // kept as "none": not the suggestions
  // a YouTube that came with the TV is not in `apps` (pm list -3) but in `also`: still offered, once, counted
  const sys = checkoutRows([{ apps: ['org.x.game'], also: ['com.google.android.youtube.tv'] }, { also: ['com.google.android.youtube.tv'] }], [], null, suggest);
  assert.equal(sys.find((r) => r.pkg === 'com.google.android.youtube.tv').count, 2);
  assert.deepEqual(checkoutRows([], blocked, null, suggest), []);
});

test('the page wires check-out in: button, script before fleet.js, endpoint', () => {
  const web = path.join(__dirname, '../web');
  const html = fs.readFileSync(path.join(web, 'index.html'), 'utf8');
  assert.match(html, /id="fleetcheckout"/);
  assert.ok(html.indexOf('js/checkout.js') > 0 && html.indexOf('js/checkout.js') < html.indexOf('js/fleet.js'));
  assert.match(fs.readFileSync(path.join(web, 'js/fleet.js'), 'utf8'), /api\/fleet\/checkout/);
  assert.match(fs.readFileSync(path.join(web, 'js/core.js'), 'utf8'), /checkout: 'core\.need_checkout'/);
});
