// node --test app/shared/tests/   This device slept (js/core.js afterWake): a stream that broke just after waking gets one more
// go, once per sleep and per stream; a stream that broke while the device stayed awake is the TV's doing, said as before.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const core = fs.readFileSync(path.join(__dirname, '../web/js/core.js'), 'utf8');
const from = core.indexOf('let tickAt'), to = core.indexOf('\n}\n', core.indexOf('function afterWake')) + 3;

function page() {
  const clock = { wall: 1e12, mono: 1000 }, ticks = [];
  const sandbox = { Date: { now: () => clock.wall }, performance: { now: () => clock.mono }, setInterval: (fn) => ticks.push(fn) };
  vm.createContext(sandbox);
  vm.runInContext(core.slice(from, to).replace(/^(let|const) /gm, 'var ') + '\nthis.afterWake = afterWake;', sandbox);
  const pass = (ms, asleep) => {                                     // asleep: the wall clock runs, the timers and performance.now do not
    for (let t = 0; t < ms; t += 2000) { clock.wall += 2000; if (!asleep) { clock.mono += 2000; ticks.forEach((f) => f()); } }
  };
  return { afterWake: sandbox.afterWake, pass };
}

test('awake all along: the stream ended on its own', () => {
  const p = page();
  p.pass(60000);
  assert.equal(p.afterWake('screen'), false);
});

test('a stream that breaks on waking, before the tick sees it: one more go, once', () => {
  const p = page();
  p.pass(10000); p.pass(120000, true);
  assert.equal(p.afterWake('screen'), true);
  assert.equal(p.afterWake('screen'), false);                       // it failed again: now it is the TV
});

test('the picture and the sound each get their own go', () => {
  const p = page();
  p.pass(120000, true); p.pass(4000);
  assert.equal(p.afterWake('screen'), true);
  assert.equal(p.afterWake('listen'), true);
});

test('long after waking it is the TV again', () => {
  const p = page();
  p.pass(120000, true); p.pass(30000);
  assert.equal(p.afterWake('screen'), false);
});

test('each sleep gives a new go', () => {
  const p = page();
  p.pass(120000, true); p.pass(2000);
  assert.equal(p.afterWake('screen'), true);
  p.pass(60000); p.pass(120000, true); p.pass(2000);
  assert.equal(p.afterWake('screen'), true);
});
