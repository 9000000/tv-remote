// node --test app/shared/tests/   A game controller (Gamepad API) drives the TV: the mapping, the hold-repeat and the stick deadzone
// of js/gamepad.js, run against a fake navigator.getGamepads().
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const read = (p) => fs.readFileSync(path.join(__dirname, '../', p), 'utf8');
const mk = () => {
  const calls = [], toasts = [], sandbox = {
    window: { listeners: {}, addEventListener(n, f) { this.listeners[n] = f; } }, navigator: { getGamepads: () => [] }, document: { querySelector: () => null },
    T: (id, a) => { let s = JSON.parse(fs.readFileSync(path.join(__dirname, '../i18n/vi.json'), 'utf8'))[id]; if (s == null) return id; return a ? s.replace(/\{(\w+)\}/g, (m, k) => (k in a ? a[k] : m)) : s; },
    currentTab: 'remote', picking: false, typingHere: () => false, can: () => true, screenOn: false,
    api: (u, b) => { calls.push(b.key); return Promise.resolve({}); }, toast: (m) => toasts.push(m), setInterval: () => 1, clearInterval() {}, Date, console,
  };
  vm.createContext(sandbox);
  vm.runInContext(read('web/js/gamepad.js').replace(/^(const|let) (\w+)/gm, 'var $2') + '\nthis.padStep = padStep; this.padPoll = padPoll; this.padSt = padSt;', sandbox);
  const step = sandbox.padStep; sandbox.padStep = (...a) => [...step(...a)];   // arrays made in the vm have another prototype
  return { sandbox, calls, toasts };
};
const pad = (down = [], axes = [0, 0]) => ({ buttons: Array.from({ length: 17 }, (_, i) => ({ pressed: down.includes(i), value: down.includes(i) ? 1 : 0 })), axes });

test('buttons: A OK, B Back, Start Home, Select Menu, shoulders volume, triggers channel', () => {
  const { sandbox } = mk();
  const keyOf = (i, pic) => sandbox.padStep({ down: {}, last: {} }, pad([i]), 0, pic)[0];
  assert.deepEqual([0, 1, 9, 8, 4, 5, 6, 7, 12, 13, 14, 15].map((i) => keyOf(i)),
    ['OK', 'BACK', 'HOME', 'MENU', 'VOL_DOWN', 'VOL_UP', 'CH_DOWN', 'CH_UP', 'UP', 'DOWN', 'LEFT', 'RIGHT']);
  assert.deepEqual([0, 1, 2, 4, 9, 8].map((i) => keyOf(i, true)), ['PAD_A', 'PAD_B', 'PAD_X', 'PAD_L1', 'PAD_START', 'PAD_SELECT']);
  assert.equal(keyOf(12, true), 'UP', 'the d-pad stays an arrow in picture mode');
});

test('one press = one key; a held arrow repeats after 400 ms, a held OK does not; release stops', () => {
  const { sandbox } = mk(); const st = { down: {}, last: {} };
  assert.deepEqual(sandbox.padStep(st, pad([12, 0]), 0), ['OK', 'UP']);
  assert.deepEqual(sandbox.padStep(st, pad([12, 0]), 300), []);
  assert.deepEqual(sandbox.padStep(st, pad([12, 0]), 420), ['UP']);
  assert.deepEqual(sandbox.padStep(st, pad([12, 0]), 480), []);
  assert.deepEqual(sandbox.padStep(st, pad([12, 0]), 560), ['UP']);
  assert.deepEqual(sandbox.padStep(st, pad([]), 600), []);
  assert.deepEqual(sandbox.padStep(st, pad([12]), 650), ['UP'], 'pressed again: a new press');
});

test('left stick: deadzone, dominant axis, repeat', () => {
  const { sandbox } = mk(); const st = { down: {}, last: {} };
  assert.deepEqual(sandbox.padStep(st, pad([], [0.3, -0.4]), 0), [], 'inside the deadzone');
  assert.deepEqual(sandbox.padStep(st, pad([], [0.9, 0.2]), 10), ['RIGHT']);
  assert.deepEqual(sandbox.padStep(st, pad([], [0.9, 0.2]), 450), ['RIGHT']);
  assert.deepEqual(sandbox.padStep(st, pad([], [0.1, -0.8]), 460), ['UP'], 'direction changes: new key at once');
  assert.deepEqual(sandbox.padStep(st, pad([], [0.1, 0.8]), 470), ['DOWN']);
  assert.deepEqual(sandbox.padStep(st, pad([], [-0.7, 0.6]), 480), ['LEFT']);
});

test('poll sends through /api/key, not while typing, and a disconnect clears the state', () => {
  let p = pad([0]); const { sandbox, calls } = mk();
  sandbox.navigator.getGamepads = () => [p];
  sandbox.padPoll(); assert.deepEqual(calls, ['OK']);
  p = pad([]); sandbox.padPoll(); p = pad([1]); sandbox.typingHere = () => true; sandbox.padPoll();
  assert.deepEqual(calls, ['OK'], 'a text field has focus: nothing sent');
  sandbox.typingHere = () => false; p = pad([]); sandbox.padPoll(); p = pad([1]); sandbox.padPoll();
  assert.deepEqual(calls, ['OK', 'BACK']);
  sandbox.navigator.getGamepads = () => [null]; sandbox.padPoll();
  assert.deepEqual(Object.keys(sandbox.padSt.down), [], 'disconnected: state cleared');
});

test('connecting shows the toast once', () => {
  const { sandbox, toasts } = mk();
  sandbox.navigator.getGamepads = () => [pad()];
  sandbox.window.listeners.gamepadconnected(); sandbox.window.listeners.gamepadconnected();
  assert.deepEqual(toasts, ['Đã nối tay cầm']);
});

test('PAD_* names exist in Scenes.KEYS with the Android codes', () => {
  const kt = read('src/main/kotlin/dev/tvremote/core/scenes/Scenes.kt');
  const want = { PAD_A: 96, PAD_B: 97, PAD_X: 99, PAD_Y: 100, PAD_L1: 102, PAD_R1: 103, PAD_START: 108, PAD_SELECT: 109 };
  for (const [k, v] of Object.entries(want)) assert.ok(kt.includes(`"${k}" to ${v}`), k);
  const web = read('web/js/gamepad.js');
  for (const k of Object.keys(want)) assert.ok(web.includes(`'${k}'`), k + ' used by the page');
});
