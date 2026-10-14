// node --test app/web/tests/   (Node 18+, nothing to install)
const test = require('node:test');
const assert = require('node:assert/strict');
const { fold, voiceCommand } = require('../../shared/web/js/voice.js');

const APPS = [
  { pkg: 'com.google.android.youtube.tv', name: 'YouTube' },
  { pkg: 'com.netflix.ninja', name: 'Netflix' },
  { pkg: 'com.fptplay.tv', name: 'FPT Play' },
  { pkg: 'org.videolan.vlc', name: 'VLC' },
];
const SCENES = [{ id: 's1', name: 'Đi ngủ' }, { id: 's2', name: 'Xem phim' }];
const cmd = (t) => voiceCommand(t, APPS, SCENES);

test('fold drops accents, case and punctuation', () => {
  assert.equal(fold('  Mở YouTube! '), 'mo youtube');
  assert.equal(fold('Đặt hẹn giờ'), 'dat hen gio');
});

test('opens apps by name, accents or not', () => {
  assert.deepEqual(cmd('mở YouTube'), { kind: 'app', pkg: 'com.google.android.youtube.tv', name: 'YouTube' });
  assert.equal(cmd('mo netflix').pkg, 'com.netflix.ninja');
  assert.equal(cmd('Xem FPT Play').pkg, 'com.fptplay.tv');
  assert.equal(cmd('open vlc').pkg, 'org.videolan.vlc');
  assert.equal(cmd('mở ứng dụng youtube').pkg, 'com.google.android.youtube.tv');
});

test('an app the TV does not have goes to the Assistant', () => {
  assert.equal(cmd('mở Spotify'), null);
  assert.equal(cmd('thời tiết hôm nay'), null);
  assert.equal(cmd(''), null);
});

test('keys', () => {
  assert.deepEqual(cmd('tắt tiếng'), { kind: 'key', key: 'MUTE', times: 1 });
  assert.equal(cmd('Tạm dừng').key, 'PLAY_PAUSE');
  assert.equal(cmd('về màn hình chính').key, 'HOME');
  assert.equal(cmd('tắt TV').key, 'SLEEP');
  assert.equal(cmd('bật tivi').key, 'WAKE');
  assert.equal(cmd('bật tv').key, 'WAKE');                          // not an app called "tv"
});

test('volume steps, 3 by default, capped at 15', () => {
  assert.deepEqual(cmd('tăng âm lượng'), { kind: 'key', key: 'VOL_UP', times: 3 });
  assert.deepEqual(cmd('giảm âm lượng 5'), { kind: 'key', key: 'VOL_DOWN', times: 5 });
  assert.equal(cmd('volume up 99').times, 15);
});

test('sleep timer in minutes or hours, and cancelling it', () => {
  assert.deepEqual(cmd('hẹn giờ tắt 30 phút'), { kind: 'timer', min: 30 });
  assert.deepEqual(cmd('tắt TV sau 2 tiếng'), { kind: 'timer', min: 120 });
  assert.deepEqual(cmd('sleep in 45 minutes'), { kind: 'timer', min: 45 });
  assert.deepEqual(cmd('hủy hẹn giờ'), { kind: 'timer', min: 0 });
});

test('a scene by its name', () => {
  assert.equal(cmd('đi ngủ').scene.id, 's1');
  assert.equal(cmd('cảnh xem phim').scene.id, 's2');
});
