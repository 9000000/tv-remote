// node --test app/web/tests/   Reading what the TV printed for the Apps tab and the TV card (js/tvread.js).
const test = require('node:test');
const assert = require('node:assert/strict');
const { cleanFreed, logNext, logLevel, readTvset, densityChoices } = require('../../shared/web/js/tvread.js');

test('bytes freed by the cache clean', () => {
  assert.equal(cleanFreed('1291592 4096 1291597 4096\n'), 5 * 4096);
  assert.equal(cleanFreed('100 4096 90 4096'), 0);                    // apps wrote meanwhile: nothing came back
  assert.equal(cleanFreed('stat: not found'), null);
});

test('the TV card from the Google TV emulator', () => {
  const t = readTvset(['sdk 34', 'dns_mode null', 'dns_host null', 'sleep 1200000', 'saver 1', 'saver_after 2147483647', 'Physical density: 320',
    'home com.google.android.apps.tv.launcherx/.home.HomeActivity', 'launcher com.google.android.apps.tv.launcherx/.home.HomeActivity',
    'launcher com.android.tv.settings/.system.FallbackHome'].join('\n'));
  assert.equal(t.sdk, 34); assert.equal(t.dnsMode, ''); assert.equal(t.dnsHost, '');
  assert.equal(t.sleep, 1200000); assert.equal(t.saver, true); assert.equal(t.saverAfter, 2147483647);
  assert.equal(t.physical, 320); assert.equal(t.override, 0);
  assert.deepEqual(t.launchers, ['com.google.android.apps.tv.launcherx/.home.HomeActivity']);   // FallbackHome is not a choice
});

test('a Sony with a filter, a bigger size and two launchers', () => {
  const t = readTvset('sdk 28\r\ndns_mode hostname\r\ndns_host dns.adguard-dns.com\r\nsleep -1\r\nsaver 0\r\nsaver_after 900000\r\n' +
    'Physical density: 320\r\nOverride density: 352\r\nhome com.spocky.projengmenu/.ui.home.MainActivity\r\n' +
    'launcher me.efesser.flauncher/.MainActivity\r\nlauncher com.spocky.projengmenu/.ui.home.MainActivity\r\nlauncher com.sony.dtv.x/.FallbackHome\r\n');
  assert.equal(t.dnsMode, 'hostname'); assert.equal(t.dnsHost, 'dns.adguard-dns.com');
  assert.equal(t.sleep, -1); assert.equal(t.saver, false); assert.equal(t.override, 352);
  assert.equal(t.launchers.length, 2); assert.equal(t.home, 'com.spocky.projengmenu/.ui.home.MainActivity');
});

test('sizes come from the screen density', () => {
  assert.deepEqual(densityChoices(320), [['Nhỏ', 288], ['Vừa', 'reset'], ['Lớn', 352], ['Rất lớn', 384]]);
  assert.deepEqual(densityChoices(0), []);
});

test('each log line is shown once across polls', () => {
  const st = { last: '', seen: new Set() };
  const a = logNext(st, 'pid 1243\n--------- beginning of main\n10-15 04:30:06.707  1243  1400 E Tag: one\n10-15 04:30:07.960  1243  1399 W Tag: two\n');
  assert.equal(a.pid, '1243'); assert.equal(a.lines.length, 2);
  // the next poll asks from 04:30:07.960 on: that line comes again, with a new one at the same stamp
  const b = logNext(st, 'pid 1243\n10-15 04:30:07.960  1243  1399 W Tag: two\n10-15 04:30:07.960  1243  1399 I Tag: three\n10-15 04:30:08.001  1243  1399 D Tag: four');
  assert.deepEqual(b.lines.map((l) => l.slice(-5)), ['three', ' four']);
  assert.equal(logNext(st, 'pid\n').pid, '');                          // the app is not running
  assert.equal(logNext({ last: '', seen: new Set() }, 'pid 1 2\n').pid, '1');
  assert.equal(logLevel(a.lines[0]), 'E'); assert.equal(logLevel(a.lines[1]), 'W'); assert.equal(logLevel('junk'), '');
});

test('the log goes on across New Year', () => {
  const st = { last: '12-31 23:59:59.900', seen: new Set() };
  assert.equal(logNext(st, 'pid 9\n01-01 00:00:00.100  9  9 I Tag: happy\n').lines.length, 1);
});
