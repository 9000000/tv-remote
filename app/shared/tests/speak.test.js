// node --test app/shared/tests/   Speak, the ways it must not go wrong (js/talk.js talk(), js/remote.js hear()): a second click
// while the microphone is asked for starts nothing; a refused microphone or a recorder that cannot be made gives everything
// back and lets Speak start again; Cancel sends no end (the TV would act on half a sentence) but Back; a browser's speech
// that refuses to start leaves Speak unlit.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const js = (f) => fs.readFileSync(path.join(__dirname, '../web/js', f), 'utf8');
const talkJs = js('talk.js');
const remote = js('remote.js');
const hearJs = remote.slice(remote.indexOf('let rec = null;'), remote.indexOf('\n}\n', remote.indexOf('function hear()')) + 3);

function page({ mic = 'ok', ac = 'ok', start = 'ok' } = {}) {
  const els = {}, log = { toasts: [], api: [], fetch: [], stopped: 0, closed: 0 };
  const el = (sel) => els[sel] || (els[sel] = {
    open: false, hidden: false, clientWidth: 100, clientHeight: 20, textContent: '', classList: (() => {
      const c = new Set();
      return { add: (x) => c.add(x), remove: (x) => c.delete(x), toggle: (x, on) => (on ?? !c.has(x)) ? c.add(x) : c.delete(x), contains: (x) => c.has(x) };
    })(),
    addEventListener() {}, showModal() { this.open = true; }, close() { this.open = false; }, getContext: () => ({ clearRect() {}, fillRect() {} }),
  });
  let askMic = [];
  const track = { stop: () => log.stopped++ };
  const stream = { getTracks: () => [track] };
  const node = () => ({ connect() {}, disconnect() {} });
  class AudioContext {
    constructor() { if (ac === 'throws') throw new Error('no 8 kHz'); }
    createMediaStreamSource() { return node(); } createScriptProcessor() { return node(); } createAnalyser() { return { ...node(), getFloatTimeDomainData() {} }; }
    get destination() { return {}; } close() { log.closed++; }
  }
  const recs = [];
  class Speech { constructor() { recs.push(this); } start() { if (start === 'throws') throw new Error('already started'); } stop() { this.onend(); } abort() { this.onend(); } }
  const s = {
    CORE: 'pc', LANG: 'vi', window: { isSecureContext: true }, localStorage: { getItem: () => '1', setItem() {} },
    navigator: { mediaDevices: { getUserMedia: () => new Promise((ok, no) => askMic.push(() => (mic === 'ok' ? ok(stream) : no(new Error('denied'))))) } },
    $: el, put: (e, k, v) => { (typeof e === 'string' ? el(e) : e)[k] = v; }, T: (id) => id, toast: (m) => log.toasts.push(m),
    api: async (p, b) => { log.api.push(p + (b && b.key ? ' ' + b.key : '')); return p.includes('start') ? { id: 7 } : {}; },
    fetch: async (u) => { log.fetch.push(u.replace(/.*do=/, '')); return {}; },
    AudioContext, Speech, performance: { now: () => 0 }, devicePixelRatio: 1, getComputedStyle: () => ({ getPropertyValue: () => '' }),
    requestAnimationFrame: () => 0, cancelAnimationFrame() {}, Int16Array, Uint8Array, Float32Array, Promise, Math, Error,
  };
  vm.createContext(s);
  vm.runInContext((talkJs + '\n' + hearJs.replace('const Speech = window.SpeechRecognition || window.webkitSpeechRecognition;', ''))
    .replace(/^(let|const) /gm, 'var ') + '\nthis.st = () => ({ talking, rec, hearing });', s);
  const settle = () => new Promise((r) => setImmediate(r));
  return { s, log, recs, lit: () => el('#voice').classList.contains('on'), dlg: () => el('#heardlg').open, grant: async () => { askMic.shift()(); await settle(); await settle(); }, asked: () => askMic.length, settle };
}

test('talk: a second click while the microphone is asked for starts nothing', async () => {
  const p = page();
  p.s.talk(); p.s.talk();
  assert.equal(p.asked(), 1);
  await p.grant();
  assert.ok(p.lit() && p.dlg());
});

test('talk: a refused microphone says so and Speak can start again', async () => {
  const p = page({ mic: 'no' });
  p.s.talk(); await p.grant();
  assert.equal(p.s.st().talking, null);
  assert.deepEqual(p.log.toasts, ['remote.error_why']);
  p.s.talk();
  assert.equal(p.asked(), 1);
});

test('talk: no 8 kHz recorder gives the microphone back', async () => {
  const p = page({ ac: 'throws' });
  p.s.talk(); await p.grant();
  assert.equal(p.log.stopped, 1);
  assert.equal(p.s.st().talking, null);
  assert.ok(!p.lit() && !p.dlg());
});

test('talk: Cancel sends Back and no end; Done sends the end', async () => {
  const a = page();
  a.s.talk(); await a.grant(); await a.settle();
  a.s.st().hearing.cancel(); await a.settle();
  assert.ok(a.log.api.includes('/api/key BACK'));
  assert.ok(!a.log.fetch.some((f) => f.startsWith('end')));
  assert.equal(a.log.stopped, 1);
  const b = page();
  b.s.talk(); await b.grant(); await b.settle();
  b.s.st().hearing.done(); await b.settle(); await b.settle();
  assert.ok(b.log.fetch.some((f) => f.startsWith('end')));
  assert.ok(!b.log.api.includes('/api/key BACK'));
});

test('hear: speech that refuses to start leaves Speak unlit and can start again', () => {
  const p = page({ start: 'throws' });
  p.s.hear();
  assert.equal(p.s.st().rec, null);
  assert.ok(!p.lit() && !p.dlg());
  assert.deepEqual(p.log.toasts, ['remote.error_why']);
  p.s.hear();
  assert.equal(p.recs.length, 2);
});

test('hear: the dialog opens and Cancel shuts it', () => {
  const p = page({ mic: 'never' });
  p.s.hear();
  assert.ok(p.lit() && p.dlg());
  p.s.st().hearing.cancel();
  assert.ok(!p.lit() && !p.dlg());
});

test('the keyboard and its fold need control (a view-only guest on the TV page)', () => {
  const html = fs.readFileSync(path.join(__dirname, '../web/index.html'), 'utf8');
  for (const id of ['kbdbtn', 'kbdpane']) assert.match(html.match(new RegExp('<[^>]*id="' + id + '"[^>]*>'))[0], /data-needs="control"/, id);
});
