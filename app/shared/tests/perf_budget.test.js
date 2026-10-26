// node --test app/shared/tests/   Speed budgets of the page's heavy pure functions (the twin of PerfBudgetTest.kt: same rule, same wording).
// Each case is the median of several samples after a warm-up, compared with THIS machine's baseline in dev/perf/.local/baseline-node.json
// (gitignored; keyed by OS / CPU / node version: the first run records and passes, a clearly faster run lowers it). Red only when the
// median is over 2x the baseline AND over baseline + the case's floor. Red = find the regression (git log on the function) and fix it;
// only when slower is intended: TVR_PERF_UPDATE=1 node --test app/shared/tests/perf_budget.test.js, and say why in the commit.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { qrModules } = require('../web/js/qr.js');
const { fold, voiceCommand } = require('../web/js/voice.js');
const { homeQrPayload } = require('../web/js/home_qr.js');

const WEB = path.join(__dirname, '..', 'web');
const FILE = path.join(__dirname, '..', '..', '..', 'dev', 'perf', '.local', 'baseline-node.json');
const MACHINE = `${os.type()} ${os.arch()} / ${os.cpus().length} cores / node ${process.version}`;
const UPDATE = process.env.TVR_PERF_UPDATE === '1';
const FASTER = 0.8;                       // a run this much under the baseline pulls it down (never up: that is TVR_PERF_UPDATE=1)
const MIN_SAMPLES = 7, MAX_SAMPLES = 25, BOX_MS = 400;

let all = {};
try { all = JSON.parse(fs.readFileSync(FILE, 'utf8')); } catch (e) { /* first run */ }
const base = all[MACHINE] || {};
const seen = {};

const fmt = (x) => x.toFixed(4);
const now = () => Number(process.hrtime.bigint()) / 1e6;

// [batch]: calls per sample, so a microsecond case is timed over thousands; [floor]: ms over the baseline that still counts as noise
function budget(name, run, { batch = 1, floor = 2 } = {}) {
  test(name, () => {
    for (let i = 0; i < Math.max(2, 20 / Math.min(batch, 10)); i++) for (let j = 0; j < batch; j++) run();   // warm-up: JIT
    const ms = [];
    const t0 = now();
    while (ms.length < MIN_SAMPLES || (ms.length < MAX_SAMPLES && now() - t0 < BOX_MS)) {
      const t = now();
      for (let j = 0; j < batch; j++) run();
      ms.push((now() - t) / batch);
    }
    ms.sort((a, b) => a - b);
    const med = ms[ms.length >> 1];
    const b = base[name];
    if (UPDATE || b === undefined || med < b * FASTER) {
      seen[name] = Number(fmt(med));
      if (b === undefined && !UPDATE) console.log(`perf: recorded '${name}' = ${fmt(med)} ms (first run on this machine)`);
      return;
    }
    if (med > b * 2 && med > b + floor) {
      assert.fail(`perf budget: '${name}' is ${fmt(med)} ms (median of ${ms.length}), baseline ${fmt(b)} ms (this machine): over 2x and over +${fmt(floor)} ms. ` +
        `Find the regression (git log on the function) and fix it; only if slower is intended: TVR_PERF_UPDATE=1 node --test app/shared/tests/perf_budget.test.js, and say why in the commit.`);
    }
  });
}

test.after(() => {
  if (!Object.keys(seen).length) return;
  all[MACHINE] = Object.assign(base, seen);
  fs.mkdirSync(path.dirname(FILE), { recursive: true });
  fs.writeFileSync(FILE, JSON.stringify(all, null, 2) + '\n');
});

const read = (f) => fs.readFileSync(path.join(WEB, f), 'utf8');

// the page's words: both languages are ~200 KB of JSON, read and parsed at every load
budget('i18n.parse.vi', () => { assert.ok(Object.keys(JSON.parse(read('../i18n/vi.json'))).length > 100); }, { floor: 3 });
budget('remote.json.parse', () => { assert.ok(JSON.parse(read('remote.json'))); }, { batch: 20, floor: 0.5 });

// the invite / pairing codes
budget('qr.modules.url', () => { assert.ok(qrModules('http://192.168.1.20:8765/?k=abcdef0123456789')); }, { batch: 20, floor: 1 });
budget('qr.modules.long', () => { assert.ok(qrModules('http://192.168.1.20:8765/invite?code=' + 'abcdef0123456789'.repeat(12))); }, { batch: 10, floor: 2 });

const APPS = [
  { pkg: 'com.google.android.youtube.tv', name: 'YouTube' }, { pkg: 'com.netflix.ninja', name: 'Netflix' },
  { pkg: 'com.fptplay.tv', name: 'FPT Play' }, { pkg: 'org.videolan.vlc', name: 'VLC' },
];
const SCENES = [{ id: 's1', name: 'Đi ngủ' }, { id: 's2', name: 'Xem phim' }];
const SAID = ['Mở YouTube!', 'tắt tiếng', 'hẹn giờ tắt 30 phút', 'kênh VTV1', 'tăng âm lượng 5', 'xem phim', 'mở app không có'];
budget('voice.command', () => { for (const s of SAID) voiceCommand(s, APPS, SCENES); fold('Mở YouTube!'); }, { batch: 100, floor: 0.5 });

budget('homeqr.payload.wifi', () => { assert.ok(homeQrPayload('wifi', { ssid: 'Home;Net', pass: 'p:a,ss"w', sec: 'WPA' })); }, { batch: 2000, floor: 0.02 });
