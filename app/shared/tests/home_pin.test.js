// node --test app/shared/tests/   "Pin from YouTube" (js/home_pin.js): which link is a channel, a video, a playlist or a short, and its canonical form
const test = require('node:test');
const assert = require('node:assert/strict');
const { ytParse, linkParse } = require('../web/js/home_pin.js');

const W = 'https://www.youtube.com/';
test('videos, in every spelling', () => {
  for (const s of ['https://youtu.be/dQw4w9WgXcQ?t=5', 'youtube.com/watch?v=dQw4w9WgXcQ&list=PLabcdefghij', 'https://m.youtube.com/watch?v=dQw4w9WgXcQ', 'https://www.youtube.com/live/dQw4w9WgXcQ',
    'xem cái này https://music.youtube.com/watch?v=dQw4w9WgXcQ nhé'])
    assert.deepEqual(ytParse(s), { kind: 'video', url: W + 'watch?v=dQw4w9WgXcQ', id: 'dQw4w9WgXcQ' }, s);
});
test('playlists, shorts', () => {
  assert.deepEqual(ytParse('https://www.youtube.com/playlist?list=PLabcdefghijk'), { kind: 'playlist', url: W + 'playlist?list=PLabcdefghijk', id: 'PLabcdefghijk' });
  assert.equal(ytParse('https://www.youtube.com/watch?list=PLabcdefghijk').kind, 'playlist');
  assert.deepEqual(ytParse('https://youtube.com/shorts/abcDEF12345?feature=share'), { kind: 'short', url: W + 'shorts/abcDEF12345', id: 'abcDEF12345' });
});
test('channels: handle, id, custom, user; the tab after it is dropped', () => {
  assert.deepEqual(ytParse('https://www.youtube.com/@MrBeast/videos'), { kind: 'channel', url: W + '@MrBeast', id: '@MrBeast' });
  assert.equal(ytParse('https://www.youtube.com/channel/UCX6OQ3DkcsbYNE6H8uQQuVA/featured').url, W + 'channel/UCX6OQ3DkcsbYNE6H8uQQuVA');
  assert.equal(ytParse('https://www.youtube.com/c/Veritasium').kind, 'channel');
  assert.equal(ytParse('https://www.youtube.com/user/google').id, 'google');
});
test('not YouTube', () => {
  for (const s of ['', 'hello', 'https://example.com/watch?v=dQw4w9WgXcQ', 'https://notyoutube.com/@x', 'https://www.youtube.com/', 'https://www.youtube.com/feed/trending', 'https://youtu.be/'])
    assert.equal(ytParse(s), null, s);
});

test('linkParse: any http(s) page and any app deep link, YouTube keeps its canonical form', () => {
  assert.deepEqual(linkParse('https://example.com/a?b=1#c'), { url: 'https://example.com/a?b=1#c' });
  assert.deepEqual(linkParse('example.com/x'), { url: 'https://example.com/x' });
  assert.deepEqual(linkParse('xem https://example.org/z nhé'), { url: 'https://example.org/z' });
  assert.deepEqual(linkParse('vtvgo://channel/1'), { url: 'vtvgo://channel/1' });
  assert.deepEqual(linkParse('NETFLIX://title/80100172'), { url: 'netflix://title/80100172' });
  assert.equal(linkParse('https://youtu.be/dQw4w9WgXcQ').yt.kind, 'video');
});
test('linkParse: a pasted link never reaches an app\'s hidden screens', () => {
  for (const s of ['intent://scan/#Intent;scheme=zxing;end', 'javascript:alert(1)', 'file:///sdcard/a.txt', 'content://media/external/1', 'data:text/html,x', 'FILE://x', 'android-app://com.x', 'about:blank'])
    assert.deepEqual(linkParse(s), { bad: 'scheme' }, s);
  for (const s of ['hello', 'https://user@evil.example/x', 'https://', 'mailto:a@b.c']) assert.equal(linkParse(s).url, undefined, s);
});
