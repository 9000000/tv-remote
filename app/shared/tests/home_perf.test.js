// node --test app/shared/tests/   The card's one line about how TV Home's video plays (js/home.js homePerf) follows the TV's verdict
// (wall/Videos.kt Verdict: the thresholds live there, the page only reads `level` / `off`).
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const src = fs.readFileSync(path.join(__dirname, '../web/js/home.js'), 'utf8');
const from = src.indexOf('function homePerf'), to = src.indexOf('// A video or GIF as the background');
const sandbox = {};
vm.createContext(sandbox);
vm.runInContext(src.slice(from, to) + '\nthis.homePerf = homePerf;', sandbox);
const perf = (o) => ({ id: 'vid:a', fps: 30, want: 30, cpu: 6, level: 'ok', off: false, ...o });

test('a smooth video, a choppy one, one the TV gave up on', () => {
  assert.equal(JSON.stringify(sandbox.homePerf(perf({}), 'vid:a')), JSON.stringify(['home.v_perf_ok', { fps: 30, want: 30, cpu: 6 }]));
  assert.equal(sandbox.homePerf(perf({ fps: 14, level: 'choppy' }), 'vid:a')[0], 'home.v_perf_bad');
  assert.equal(sandbox.homePerf(perf({ fps: 5, level: 'slow' }), 'vid:a')[0], 'home.v_perf_bad');
  assert.equal(sandbox.homePerf(perf({ fps: 5, level: 'slow', off: true }), 'vid:a')[0], 'home.v_perf_off');
});

test('nothing to say for another wall or before a measurement', () => {
  assert.equal(sandbox.homePerf(perf({}), 'vid:b'), null);
  assert.equal(sandbox.homePerf(null, 'vid:a'), null);
  assert.equal(sandbox.homePerf(perf({}), 'night'), null);
});

test('its words exist in both languages', () => {
  for (const l of ['vi', 'en']) {
    const w = JSON.parse(fs.readFileSync(path.join(__dirname, `../web/i18n/${l}.json`), 'utf8'));
    for (const k of ['home.v_perf_ok', 'home.v_perf_bad', 'home.v_perf_off']) assert.ok(w[k], `${l} ${k}`);
  }
});
