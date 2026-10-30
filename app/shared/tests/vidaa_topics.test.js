// node --test app/shared/tests/   Hisense Vidaa's MQTT topics are the TV's own names: "/remoteapp/tv/…" is what the TV listens to,
// not a path of this repo (the app folder rename, 2026-10-30, rewrote "app/tv" inside it to "app/tvsetup" and every test passed).
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');

test('Vidaa sends to the TV\'s own topics', () => {
  const kt = fs.readFileSync(path.join(__dirname, '../src/main/kotlin/dev/tvremote/core/remote/Vidaa.kt'), 'utf8');
  for (const t of ['/remoteapp/tv/ui_service/', '/remoteapp/tv/platform_service/', '/remoteapp/tv/remote_service/', '/remoteapp/mobile/'])
    assert.ok(kt.includes(t), 'Vidaa.kt lost ' + t);
  assert.deepStrictEqual(kt.match(/\/remoteapp\/(?!tv\/|mobile\/)[\w$]+\//g), null, 'a topic that is neither /remoteapp/tv/ nor /remoteapp/mobile/');
});
