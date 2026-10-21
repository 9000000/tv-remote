'use strict';
// Pad test for the TV Games app: each connected phone gets the same pad the phone draws; a press lights the button here at once
// and its delay (the phone's time stamp against the TV's clock, window.tvpad `lat`) and the phone's ping are shown. WEBGAMES.md.
const VI = (navigator.language || '').toLowerCase().startsWith('vi');
const T = VI ? {
  title: 'Kiểm tra tay cầm', hint: 'Bấm nút trên điện thoại: nút sáng ở đây ngay, kèm độ trễ. Select + Start cùng lúc để đặt lại số.',
  join: 'Quét mã QR trên TV để vào, rồi bấm thử các nút', p: (n) => `Người chơi ${n}`, last: 'Vừa rồi', med: 'Trung vị', max: 'Lớn nhất', n: 'Số lần', ping: 'Ping',
  nogame: 'Cần mở trong app TV Games',
} : {
  title: 'Pad test', hint: 'Press buttons on the phone: they light up here at once, with their delay. Select + Start together reset the numbers.',
  join: 'Scan the QR code on the TV to join, then press buttons', p: (n) => `Player ${n}`, last: 'Last', med: 'Median', max: 'Max', n: 'Presses', ping: 'Ping',
  nogame: 'Open this in the TV Games app',
};
const PCOL = ['#3b82f6', '#ef4444', '#22c55e', '#eab308'];
const FACE = { X: ['△', '#2fa05a', 0, -12], A: ['○', '#e5484d', 12, 0], B: ['✕', '#3b6fe0', 0, 12], Y: ['□', '#d65aa8', -12, 0] };   // the phone's "ps" look
const SH = { L2: ['L2', 2], L: ['L1', 16], R: ['R1', 71], R2: ['R2', 85] };
const $ = (s, r) => (r || document).querySelector(s);
const h = (t, c, x) => { const e = document.createElement(t); if (c) e.className = c; if (x != null) e.textContent = x; return e; };
$('#title').textContent = T.title; $('#hint').textContent = T.hint;
const pad = window.tvpad, root = $('#players');
if (!pad) { root.append(h('p', 'empty', T.nogame)); throw new Error('no tvpad'); }

const P = {};                                   // player -> { el, keys, held, lats, max, count, ... }
// positions in units of the box's width (100 wide, 56 high): percentages, so the box can be any size
const at = (e, x, y, w, h) => { e.style.cssText = `left:${x}%;top:${y / 0.56}%` + (w ? `;width:${w}%;height:${h / 0.56}%` : ''); return e; };
function key(box, cls, name, text, x, y, w, hh) {
  const e = h('div', 'k ' + cls, text); e.dataset.b = name; at(e, x, y, w, hh); box.append(e); return e;
}
const nameOf = (n) => { try { return pad.name(n) || T.p(n); } catch (e) { return T.p(n); } };
function build(n) {
  const el = h('section', 'pl'); el.style.setProperty('--c', PCOL[n - 1]);
  const hd = h('div', 'hd'); const title = h('b', '', nameOf(n)); hd.append(title); const ping = h('span', 'ping', '– ms'); hd.append(ping);
  const box = h('div', 'box'), keys = {};
  for (const b in SH) keys[b] = key(box, 'sh', b, SH[b][0], SH[b][1], 0, 13, 7);
  const ring = h('div', 'ring'); const knob = h('i', 'knob'); ring.append(knob); ring.style.cssText = `left:7%;top:${19 / 0.56}%;width:26%`;
  const arm = (name, x, y, w, hh, g) => { keys[name] = key(box, 'arm', name, g, 8 + x, 20 + y, w, hh); };
  arm('UP', 8, 0, 8, 10, '▲'); arm('DOWN', 8, 14, 8, 10, '▼'); arm('LEFT', 0, 8, 10, 8, '◀'); arm('RIGHT', 14, 8, 10, 8, '▶');
  box.append(ring);                                              // over the arms: the knob must not hide behind them
  keys.SELECT = key(box, 'pill', 'SELECT', 'Select', 36, 40, 13, 5); keys.START = key(box, 'pill', 'START', 'Start', 51, 40, 13, 5);
  for (const b in FACE) { const [g, c, dx, dy] = FACE[b]; const e = key(box, 'fb', b, g, 80 + dx - 5.5, 32 + dy - 5.5, 11, 11); e.style.setProperty('--bc', c); keys[b] = e; }
  const stats = h('div', 'stats'), body = h('div', 'body');
  body.append(box, stats); el.append(hd, body);
  el.dataset.p = n; root.append(el);
  [...root.children].filter((c) => c.dataset.p).sort((a, b) => a.dataset.p - b.dataset.p).forEach((c) => root.append(c));   // by player number, not by who pressed first
  P[n] = { el, keys, ping, title, hd, body, box, knob, ring, stats, held: new Set(), lats: [], max: null, count: 0, last: null, ms: null };
  layout(); stat(n); return P[n];
}
// The join QR: big in the middle while nobody is in, small in a corner from the first player on, gone once 4 are in.
// Players: 1-2 side by side, 3-4 in a 2x2 grid (the free fourth cell holds the QR); sizes are worked out here so any TV size fits.
let qrDone = false;
function qrShow(mode) {
  const q = $('#qr');
  if (!qrDone) {
    qrDone = true;
    let url = '', src = '';
    try { url = pad.joinUrl() || ''; src = pad.qr ? pad.qr(480) : ''; } catch (e) {}
    if (src) $('#qrimg').src = src; else $('#qrimg').hidden = true;
    $('#qrlink').textContent = url.replace(/^https?:\/\//, '').split('/')[0];
    $('#qrtxt').textContent = T.join;
  }
  q.hidden = !mode; q.className = mode || '';
}
function layout() {
  const ids = Object.keys(P).map(Number).sort(), n = ids.length;
  $('#hint').hidden = false;
  qrShow(n === 0 ? 'mid' : n === 3 ? 'cell' : n >= 4 ? '' : 'corner');
  if (!n) return;
  const sw = $('#stage').clientWidth, sh = $('#stage').clientHeight, gap = innerWidth * 0.016, vh = innerHeight / 100;
  const cols = n >= 3 ? 2 : n, rows = n >= 3 ? 2 : 1, cmp = n >= 3;
  const cw = Math.min((sw - gap * (cols - 1)) / cols, cmp ? 88 * vh : 60 * vh), ch = (sh - gap * (rows - 1)) / rows;
  ids.forEach((i) => {
    const p = P[i];
    p.el.classList.toggle('cmp', cmp);
    p.el.style.width = cw + 'px'; p.el.style.height = cmp ? ch + 'px' : 'auto';
    const padx = p.el.offsetWidth - p.el.clientWidth, inW = cw - 2 * 0.012 * innerWidth - 1 * vh;           // inside the border and padding
    let bw = inW;
    if (cmp) {
      const inH = ch - p.hd.offsetHeight - 3.4 * vh - 1.6 * vh;
      bw = Math.min(inW - 24 * vh - gap, inH * 100 / 56);
    }
    p.box.style.width = bw + 'px'; p.box.style.setProperty('--u', bw / 100 + 'px');                        // text and glows scale with the box
  });
}
addEventListener('resize', layout);
function stat(n) {
  const p = P[n], f = (v) => (v == null ? '–' : v + ' ms');
  p.stats.replaceChildren();
  const a = h('div', 'big'); a.append(`${T.last} `, h('b', '', f(p.last))); p.stats.append(a);
  const l = h('div', ''); l.append(h('span', '', `${T.med} ${f(PingMath.median(p.lats))}`), h('span', 'sep', ' · '), h('span', '', `${T.max} ${f(p.max)}`), h('span', 'sep', ' · '), h('span', '', `${T.n} ${p.count}`));
  p.stats.append(l);
}
function pingSet(n, ms) {
  const p = P[n] || build(n);
  if (ms == null || ms < 0) return;
  p.ms = ms; p.ping.textContent = ms + ' ms'; p.ping.className = 'ping ' + PingMath.level(ms);
}
pad.on('join', (e) => { const p = P[e.player] || build(e.player); p.title.textContent = nameOf(e.player); pingSet(e.player, pad.ping(e.player)); });
pad.on('leave', (e) => { const p = P[e.player]; if (p) { p.el.remove(); delete P[e.player]; layout(); } });
pad.on('rename', (e) => { const p = P[e.player]; if (p) p.title.textContent = nameOf(e.player); });
pad.on('ping', (e) => pingSet(e.player, e.ms));
pad.on('button', (e) => {
  const p = P[e.player] || build(e.player), k = p.keys[e.btn];
  if (k) k.classList.toggle('on', e.down);                     // the light first, the numbers after
  e.down ? p.held.add(e.btn) : p.held.delete(e.btn);
  if (e.down && p.held.has('START') && p.held.has('SELECT')) { p.lats = []; p.max = null; p.count = 0; p.last = null; stat(e.player); return; }
  if (e.down) {
    p.count++;
    if (e.lat != null) { p.last = e.lat; p.lats.push(e.lat); if (p.lats.length > 30) p.lats.shift(); p.max = Math.max(p.max == null ? 0 : p.max, e.lat); }
    stat(e.player);
  }
});
pad.on('axis', (e) => {
  const p = P[e.player] || build(e.player);
  p.knob.style.transform = `translate(${e.x * 93}%,${e.y * 93}%)`;
  p.ring.classList.toggle('act', Math.hypot(e.x, e.y) > 0.1);
});
layout();
