// node --test app/shared/tests/   Reading what the TV printed for the Apps tab and the TV card (js/tvread.js).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
// the page's T(): the words of i18n/<LANG>.json (vi unless a test says otherwise), {name} filled in
const book = (l) => JSON.parse(fs.readFileSync(path.join(__dirname, '../web/i18n/' + l + '.json'), 'utf8'));
const books = { vi: book('vi'), en: book('en') };
globalThis.LANG = 'vi';
globalThis.T = (id, a) => { const s = books[globalThis.LANG][id] ?? books.vi[id]; return s == null ? id : a ? s.replace(/\{(\w+)\}/g, (m, k) => (k in a ? a[k] : m)) : s; };
const inEn = (f) => { globalThis.LANG = 'en'; try { return f(); } finally { globalThis.LANG = 'vi'; } };
const { cleanFreed, logNext, logLevel, readTvset, densityChoices, capsRead, sizeName, appInfoRead, permName, bootRead, settingsRead, settingInfo } = require('../web/js/tvread.js');

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

test('what the TV can play (Caps.kt), the best decoder of each format', () => {
  const c = capsRead(['sdk 28 9', 'codec dec sw OMX.google.h264.decoder video/avc 2048x2048 -', 'codec dec hw OMX.MS.AVC.Decoder video/avc 4096x2304 secure',
    'codec dec hw OMX.MS.HEVC.Decoder video/hevc 4096x2304 10bit,hdr10', 'codec dec hw OMX.MS.HEVC.Decoder.secure video/hevc 4096x2304 10bit,secure',
    'codec enc hw OMX.MS.AVC.Encoder video/avc 1920x1080 -', 'codec dec sw c2.android.ac3 audio/ac3 - -', 'hdr hdr10 hlg',
    'mode 3840x2160@60.0', 'mode 3840x2160@60.0', 'mode 1920x1080@50.0', 'audio hdmi pcm ac3 eac3', 'feature android.software.leanback', 'nonsense'].join('\n'));
  assert.equal(c.sdk, 28); assert.equal(c.release, '9');
  assert.deepEqual(c.video.map((v) => [v.name, v.hw, v.w]), [['H.264', true, 4096], ['H.265 (HEVC)', true, 4096]]);   // the encoder is not a player
  assert.deepEqual(c.video[1].flags, ['10bit', 'hdr10', 'secure']);
  assert.deepEqual(c.audio, [{ mime: 'audio/ac3', name: 'Dolby Digital', hw: false }]);
  assert.deepEqual(c.hdr, ['hdr10', 'hlg']); assert.deepEqual(c.modes, ['3840x2160@60.0', '1920x1080@50.0']);
  assert.deepEqual(c.outputs, [{ dev: 'hdmi', enc: ['PCM', 'Dolby Digital', 'Dolby Digital Plus'] }]);
  assert.deepEqual(c.features, ['android.software.leanback']);
  assert.equal(sizeName(4096, 2304), '4K'); assert.equal(sizeName(2160, 3840), '4K'); assert.equal(sizeName(1920, 1088), '1080p'); assert.equal(sizeName(0, 0), '');
  assert.deepEqual(capsRead(''), { sdk: 0, release: '', video: [], audio: [], hdr: [], modes: [], outputs: [], features: [] });
});

// trimmed from a Sony BRAVIA (Android 9): YouTube is an updated system app, with its factory copy listed after it
const SONY_YT = `Packages:
  Package [com.google.android.youtube.tv] (7d6bd5a):
    userId=10131
    versionCode=530340320 minSdk=24 targetSdk=34
    versionName=5.30.340
    flags=[ SYSTEM HAS_CODE ALLOW_CLEAR_USER_DATA UPDATED_SYSTEM_APP ALLOW_BACKUP ]
    firstInstallTime=2020-10-12 19:46:22
    lastUpdateTime=2026-09-18 09:55:12
    installerPackageName=com.android.vending
    requested permissions:
      android.permission.INTERNET
      android.permission.RECORD_AUDIO
      android.permission.ACCESS_COARSE_LOCATION
      android.permission.RECEIVE_BOOT_COMPLETED
    install permissions:
      android.permission.INTERNET: granted=true
      android.permission.RECEIVE_BOOT_COMPLETED: granted=true
    User 0: ceDataInode=-4294836662 installed=true hidden=false suspended=false stopped=false notLaunched=false enabled=0
      gids=[3003]
      runtime permissions:
        android.permission.RECORD_AUDIO: granted=true
      enabledComponents:
        androidx.work.impl.background.systemalarm.RescheduleReceiver

Hidden system packages:
  Package [com.google.android.youtube.tv] (f5c5fc9):
    versionCode=21017320 minSdk=21 targetSdk=28
    versionName=2.10.17
#~#
4242 4243
#~#
        TOTAL    50064     5284     9684    30465    54744    42798    29292     9498
#~#
com.google.android.youtube.tv/androidx.work.impl.background.systemalarm.RescheduleReceiver
`;

test('one app in detail (tvshell.app_info)', () => {
  const a = appInfoRead(SONY_YT);
  assert.equal(a.found, true); assert.equal(a.version, '5.30.340'); assert.equal(a.code, '530340320');   // not the factory copy's
  assert.equal(a.minSdk, 24); assert.equal(a.targetSdk, 34);
  assert.equal(a.installed, '2020-10-12 19:46:22'); assert.equal(a.updated, '2026-09-18 09:55:12');
  assert.equal(a.system, true); assert.equal(a.updatedSystem, true); assert.equal(a.installer, 'com.android.vending');
  assert.deepEqual(a.pids, ['4242', '4243']); assert.equal(a.ramKb, 50064); assert.equal(a.boot, true);
  // asked for but never given: Android 9 does not list it under runtime permissions
  assert.deepEqual(a.perms, [{ name: 'android.permission.ACCESS_COARSE_LOCATION', granted: false, locked: false },
    { name: 'android.permission.RECORD_AUDIO', granted: true, locked: false }]);
  const b = appInfoRead(SONY_YT.replace('RECORD_AUDIO: granted=true', 'RECORD_AUDIO: granted=false, flags=[ SYSTEM_FIXED|USER_SENSITIVE_WHEN_GRANTED]')
    .replace('4242 4243', '').replace(/TOTAL.*\n/, '').replace(/com\.google[^\n]*Receiver\n$/, ''));
  assert.deepEqual(b.perms[1], { name: 'android.permission.RECORD_AUDIO', granted: false, locked: true });
  assert.deepEqual([b.pids, b.ramKb, b.boot], [[], 0, false]);
  assert.equal(appInfoRead('Unable to find package: x').found, false);
  assert.equal(permName('android.permission.RECORD_AUDIO'), 'Micro'); assert.equal(permName('android.permission.READ_MEDIA_VIDEO'), 'Tệp, ảnh và video');
  assert.equal(permName('com.x.permission.ODD'), 'ODD');
});

test('apps that start with the TV, and the settings list', () => {
  assert.deepEqual([...bootRead('com.a.b/.Boot\ncom.a.b/.Other\n  dev.x/y.Z\nNo receivers\n')], ['com.a.b', 'dev.x']);
  assert.deepEqual(settingsRead('global adb_enabled=1\nsecure sleep_timeout=-1\nsystem a.b=x=y\nnot a line\nglobal =1\n'),
    [{ table: 'global', name: 'adb_enabled', value: '1' }, { table: 'secure', name: 'sleep_timeout', value: '-1' }, { table: 'system', name: 'a.b', value: 'x=y' }]);
});

test('settings in words', () => {
  const i = (name, value, en = false) => en ? inEn(() => settingInfo({ table: 'x', name, value })) : settingInfo({ table: 'x', name, value });
  assert.deepEqual(i('adb_enabled', '1'), { group: 'dev', groupName: 'Nhà phát triển & ADB', title: 'Gỡ lỗi ADB', value: 'Bật', known: true, order: i('adb_enabled', '1').order });
  assert.ok(i('screen_off_timeout', '1').order < i('sleep_timeout', '1').order);
  assert.equal(i('adb_enabled', '0', true).value, 'Off');
  assert.equal(i('sleep_timeout', '1200000').value, '20 phút'); assert.equal(i('sleep_timeout', '-1').value, 'Không bao giờ');
  assert.equal(i('screen_off_timeout', '2147483647', true).value, 'Never'); assert.equal(i('screen_off_timeout', '3600000').value, '1 giờ');
  assert.equal(i('screen_off_timeout', '15000').value, '15 giây');
  assert.equal(i('window_animation_scale', '0').value, 'Tắt'); assert.equal(i('window_animation_scale', '0.5').value, '0.5×');
  assert.equal(i('screen_brightness', '102').value, '40%'); assert.equal(i('private_dns_mode', 'opportunistic').value, 'Tự động');
  assert.equal(i('default_input_method', 'com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME').value, 'com.google.android.inputmethod.latin');
  assert.equal(i('display_density_forced', '').value, 'Mặc định'); assert.equal(i('stay_on_while_plugged_in', '3').value, 'Bật');
  assert.deepEqual(i('ble_scan_balanced_interval_ms', '730'), { group: 'other', groupName: 'Khác (kỹ thuật)', title: 'Ble scan balanced interval ms', value: '730', known: false, order: 0 });
});

test('ADB and Wireless debugging: on, never off (TvShell.ON_ONLY)', () => {
  const { settingEdit } = require('../web/js/tvread.js');
  assert.deepEqual(settingEdit({ table: 'global', name: 'adb_wifi_enabled', value: '0' }), { kind: 'turnon', on: '1' });
  assert.deepEqual(settingEdit({ table: 'global', name: 'adb_wifi_enabled', value: '1' }), { locked: true });
  assert.deepEqual(settingEdit({ table: 'global', name: 'adb_enabled', value: '0' }), { kind: 'turnon', on: '1' });
  assert.deepEqual(settingEdit({ table: 'global', name: 'adb_allowed_connection_time', value: '0' }), { locked: true });
});
