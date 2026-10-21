'use strict';
const K = new URLSearchParams(location.search).get('k') || '';
const VI = (navigator.language || '').toLowerCase().startsWith('vi');
const W = VI
  ? { p: 'Người chơi', c: 'Đang kết nối…', rc: 'Đang kết nối lại…', rch: 'Không thấy TV? Nếu TV đổi mạng hoặc địa chỉ, quét lại mã QR trên TV.', rs: 'Quét lại mã QR trên TV', rsh: 'Mã kết nối đã đổi. Quét mã mới để vào lại.', nm: 'Tên của bạn', f: 'Hết chỗ chơi', h: 'Rung', a: 'Thêm game', s: 'Gửi', k: 'Cần xoay thay D-pad', b: 'Quay lại', g: 'TV Games', t: 'Thêm vào Màn hình chính để chơi toàn màn hình',
    s1: 'Bấm nút Chia sẻ của Safari (ô vuông có mũi tên lên)', s2: 'Chọn “Thêm vào Màn hình chính”', s3: 'Mở tay cầm từ biểu tượng mới: toàn màn hình', fs: 'Toàn màn hình' }
  : { p: 'Player', c: 'Connecting…', rc: 'Reconnecting…', rch: 'No TV in sight? If the TV changed network or address, scan its QR code again.', rs: 'Scan the QR code on the TV again', rsh: 'The connection code was changed. Scan the new one to join again.', nm: 'Your name', f: 'Room is full', h: 'Haptics', a: 'Add game', s: 'Send', k: 'Stick instead of D-pad', b: 'Back', g: 'TV Games', t: 'Add to Home Screen to play fullscreen',
    s1: 'Tap Safari’s Share button (the square with an up arrow)', s2: 'Choose “Add to Home Screen”', s3: 'Open the pad from the new icon: fullscreen', fs: 'Fullscreen' };
const COLORS = ['#3b82f6', '#ef4444', '#22c55e', '#eab308'];
const $ = (s) => document.querySelector(s);
const store = {
  get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
  set(k, v) { try { localStorage.setItem(k, v); } catch (e) {} },
};
let cid = store.get('tvg-cid') || '';
if (!cid) { cid = Math.random().toString(36).slice(2, 10) + Date.now().toString(36); store.set('tvg-cid', cid); }
$('#lblHaptic').textContent = W.h; $('#lblAdd').textContent = W.a; $('#tipText').textContent = W.t;
$('#lblBack').textContent = W.b; $('#lblHome').textContent = W.g; $('#shTitle').textContent = W.t; $('#sh1').textContent = W.s1; $('#sh2').textContent = W.s2; $('#sh3').textContent = W.s3; $('#fs').setAttribute('aria-label', W.fs); $('#send').textContent = W.s; $('#lblStick').textContent = W.k;
$('#offTitle').textContent = W.rs; $('#offHint').textContent = W.rsh; $('#nm').placeholder = W.nm; $('#nm').maxLength = 40;
$('#add').href = 'add?k=' + encodeURIComponent(K);

// ---- haptics: vibrate() where there is one, the iOS 17.4+ checkbox-switch tick elsewhere
let haptics = store.get('tvg-haptic') !== '0';
const iosTick = !('vibrate' in navigator) && typeof CSS !== 'undefined' && CSS.supports && CSS.supports('-webkit-touch-callout', 'none');
function buzz(ms) {
  if (!haptics) return;
  if ('vibrate' in navigator) navigator.vibrate(ms);
  else if (iosTick) { try { $('#ios').click(); } catch (e) {} }
}
function setHaptic(on) { haptics = on; store.set('tvg-haptic', on ? '1' : '0'); $('#sw').classList.toggle('on', on); }
setHaptic(haptics);

// ---- the player's name: kept here (localStorage) and sent with every hello, so the same phone is the same player after a reconnect
// or after TV Games was closed and opened again; tapping the chip edits it. Empty = "Player N". Max 16 characters (emoji count one).
let myName = store.get('tvg-name') || '', mySlot = 0;
const clean = (v) => [...String(v || '').replace(/[\u0000-\u001f\u007f-\u009f\u202a-\u202e\u2066-\u2069]/g, ' ').replace(/\s+/g, ' ').trim()].slice(0, 16).join('').trim();
function setName(v, tell) {
  myName = clean(v); store.set('tvg-name', myName);
  if (tell) send('name ' + myName);
  show(mySlot);
}
function editName(on) {
  $('#who').hidden = on; $('#nameform').hidden = !on;
  if (on) { $('#nm').value = myName; try { $('#nm').focus(); $('#nm').select(); } catch (e) {} }
}
$('#nameform').addEventListener('submit', (e) => { e.preventDefault(); setName($('#nm').value, true); editName(false); buzz(12); });
$('#nm').addEventListener('blur', () => { if (!$('#nameform').hidden) { setName($('#nm').value, true); editName(false); } });

// ---- connection
let first_hello = true, ws = null, retry = 400, layout = null, sysName = 'nes', mode = 'nav', lastPong = 0, gaveUp = false, downSince = 0;
// Two modes: the pad (a game is running) and navigate (a remote for TV Games itself: library, start / pause cards). It follows
// the TV (a game starts -> pad, back to the library -> navigate); the 🎮 / 🧭 button flips it by hand.
function setMode(m) {
  mode = m; document.body.classList.toggle('nav', m === 'nav');
  $('#mode').textContent = m === 'nav' ? '🎮' : '🧭';
  releaseAll(); relayout(); lockLandscape();
}

// What the TV is running decides the pad: nes / snes / gba / ps (a system's `pad` in Systems.kt) or web (the look of ps).
// A web game may then change it (tvpad.layout): {choices:[..]} four big answer buttons, {text:true} a field + Send,
// {buttons:[..]} only these, {dpad:false} no D-pad / stick. null = the usual one.
const SYS = {
  nes:  { stick: false, face: 'pair',    sh: [[], []],                    mid: ['SELECT', 'START'] },
  snes: { stick: false, face: 'diamond', sh: [['L'], ['R']],              mid: ['SELECT', 'START'] },
  gba:  { stick: false, face: 'pair',    sh: [['L'], ['R']],              mid: ['SELECT', 'START'] },
  ps:   { stick: true,  face: 'diamond', sh: [['L2', 'L'], ['R2', 'R']],  mid: ['SELECT', 'START'] },
};
SYS.web = SYS.ps;
const FACE = { pair: ['B', 'A'], diamond: ['X', 'A', 'B', 'Y'] };
const SKIN = {                                                // face colours, per look
  snes: { X: '#3b6fe0', Y: '#2fa05a', A: '#e5484d', B: '#e0a100' },
  ps: { X: '#2fa05a', Y: '#d65aa8', A: '#e5484d', B: '#3b6fe0' },
};
const GLYPH = { ps: { X: '△', Y: '□', A: '○', B: '✕', L: 'L1', R: 'R1' } };
const look = () => (sysName === 'web' ? 'ps' : SYS[sysName] ? sysName : 'ps');
let stickOn = false;
const stickKey = () => 'tvg-stick-' + look();
function wantStick() { const v = store.get(stickKey()); return v == null ? SYS[look()].stick : v === '1'; }
function mkBtn(cls, b) {
  const e = document.createElement('button'); e.className = cls; e.dataset.b = b;
  e.textContent = (GLYPH[look()] && GLYPH[look()][b]) || { SELECT: 'Select', START: 'Start' }[b] || b;
  const c = SKIN[look()] && SKIN[look()][b]; if (c && cls.includes('fb')) e.style.setProperty('--bc', c);
  return e;
}
function relayout() {
  const l = layout || {}, base = SYS[look()];
  const ch = Array.isArray(l.choices) && l.choices.length ? l.choices.filter((x) => 'ABCD'.includes(x)) : null;
  $('#choices').hidden = !ch; $('#textrow').hidden = !l.text;
  $('#pad').classList.toggle('hid', !!ch); $('#pad').classList.toggle('txt', !!l.text);
  document.querySelectorAll('#choices [data-b]').forEach((el) => { el.hidden = !ch || !ch.includes(el.dataset.b); });
  const ok = (b) => !Array.isArray(l.buttons) || l.buttons.includes(b);
  const fill = (sel, cls, names) => { const el = $(sel); el.replaceChildren(...names.filter(ok).map((b) => mkBtn(cls, b))); };
  fill('#face', 'btn fb', FACE[base.face]); $('#face').className = base.face;
  fill('#shl', 'btn sb', base.sh[0]); fill('#shr', 'btn sb', base.sh[1]); fill('#mid', 'btn pill', base.mid);
  stickOn = wantStick() && l.dpad !== false && mode === 'pad';
  $('#dpad').classList.toggle('off', stickOn || l.dpad === false);
  $('#sw2').classList.toggle('on', wantStick());
  placeStick(); releaseAll();
}
// ---- the floating stick: a ring that appears where the thumb lands (left half in landscape, upper half in portrait)
const stk = $('#stick'), knob = stk.querySelector('.knob');
let SR = 56, sRest = { x: 0, y: 0 }, sId = null, sOrg = null, lastA = '0 0', lastT = 0, pend = null;
const portrait = () => matchMedia('(orientation:portrait)').matches;
function placeStick() {
  stk.hidden = !stickOn; if (!stickOn) return;
  const r = $('#left').getBoundingClientRect(); SR = Math.max(40, Math.min(64, Math.round(r.width * 0.4)));
  sRest = { x: r.left + r.width / 2, y: r.top + r.height / 2 };
  stk.style.width = stk.style.height = SR * 2 + 'px'; moveStick(sRest.x, sRest.y, 0, 0); stk.style.opacity = sId == null ? '.55' : '1';
}
function moveStick(cx, cy, dx, dy) {
  stk.style.left = cx - SR + 'px'; stk.style.top = cy - SR + 'px';
  knob.style.transform = 'translate(' + dx + 'px,' + dy + 'px)';
}
function sendAxis(ix, iy, force) {
  const v = ix + ' ' + iy; if (v === lastA) return;
  const now = performance.now();
  if (!force && now - lastT < 16) { clearTimeout(pend); pend = setTimeout(() => sendAxis(ix, iy, true), 16 - (now - lastT)); return; }
  clearTimeout(pend); lastA = v; lastT = now; send('a ' + v);
}
function stickStart(t) { sId = t.identifier; sOrg = { x: t.clientX, y: t.clientY }; stk.classList.add('on'); stk.style.opacity = '1'; buzz(8); moveStick(sOrg.x, sOrg.y, 0, 0); }
function stickMove(t) {
  let dx = t.clientX - sOrg.x, dy = t.clientY - sOrg.y; const d = Math.hypot(dx, dy);
  if (d > SR) { dx *= SR / d; dy *= SR / d; }
  moveStick(sOrg.x, sOrg.y, dx, dy); sendAxis(Math.round(dx / SR * 100), Math.round(dy / SR * 100));
}
function stickEnd() { if (sId == null) return; sId = null; stk.classList.remove('on'); sendAxis(0, 0, true); placeStick(); }
window.addEventListener('resize', () => setTimeout(placeStick, 50));
function say(t) { const e = $('#say'); e.textContent = t; e.hidden = !t; clearTimeout(say.t); if (t) say.t = setTimeout(() => { e.hidden = true; }, 6000); }
const held = new Set();
function send(s) { if (ws && ws.readyState === 1) ws.send(s); }
function show(slot) {
  const who = $('#who');
  mySlot = slot > 0 ? slot : mySlot;
  document.body.classList.toggle('offline', slot === 0 && mySlot > 0);
  who.classList.toggle('me', slot > 0);
  if (slot > 0) {
    who.textContent = myName || (W.p + ' ' + slot);
    const r = document.documentElement.style; r.setProperty('--c', COLORS[slot - 1]); r.setProperty('--glow', COLORS[slot - 1] + '77');
  } else who.textContent = slot === -1 ? W.f : mySlot > 0 ? W.rc : W.c;
}
// the TV said "scan again" (new connection code): stop trying, show it
function rescan() { gaveUp = true; $('#off').hidden = false; try { ws.close(); } catch (e) {} }
// after a drop: ask the server with a plain request why: 403 = the code changed (rescan), anything else = just try again
function lost() {
  if (gaveUp) return;
  show(0);
  if (!downSince) downSince = Date.now();
  if (downSince && Date.now() - downSince > 15000 && mySlot > 0) say(W.rch);
  fetch('/layouts.json?k=' + encodeURIComponent(K), { cache: 'no-store' }).then((r) => { if (r.status === 403) rescan(); else again(); }).catch(again);
}
function again() { if (gaveUp) return; setTimeout(connect, retry); retry = Math.min(retry * 1.6, 3000); }
function connect() {
  if (gaveUp) return;
  show(0);
  ws = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws?k=' + encodeURIComponent(K));
  ws.onopen = () => { retry = 400; downSince = 0; lastPong = Date.now(); send('hello ' + cid + (myName ? ' ' + myName : '')); held.forEach((b) => send('d ' + b)); };
  ws.onmessage = (e) => {
    const t = String(e.data);
    if (t[0] === 'p') { pong(t); return; }
    try {
      const m = JSON.parse(t);
      if (m.rescan) { rescan(); return; }
      if (m.full) { show(-1); return; }
      if ('sys' in m && !('slot' in m)) { sysName = m.sys; layout = null; relayout(); return; }
      if ('game' in m && !('slot' in m)) { setMode(m.game ? 'pad' : 'nav'); $('#add').hidden = !!m.game; return; }
      if ('layout' in m) { layout = m.layout; relayout(); return; }
      if ('say' in m) { say(m.say); return; }
      if ('vibrate' in m) { if ('vibrate' in navigator) navigator.vibrate(m.vibrate); else buzz(0); return; }
      if (m.name && !myName) { myName = clean(m.name); store.set('tvg-name', myName); }      // the TV remembers a name this phone lost
      else if (myName && m.name !== myName) send('name ' + myName);
      show(m.slot);
      if (haptics && 'vibrate' in navigator) navigator.vibrate([30, 40, 30]); else buzz(0);   // a stronger one: the TV says the slot is yours
      $('#add').hidden = !!m.game;
      if (m.sys) sysName = m.sys; layout = null; say('');
      if (first_hello || (mode === 'nav') === !!m.game) setMode(m.game ? 'pad' : 'nav'); else relayout();
      first_hello = false;
    } catch (x) {}
  };
  ws.onclose = lost;
  ws.onerror = () => { try { ws.close(); } catch (x) {} };
}
// ---- ping: a stamp out, the TV's reply carries it back with the TV's clock; median of the last 5 (pingmath.js), shown small at the top
const trk = new PingMath.Tracker(5);
function pong(t) {
  const [, t0, tv] = t.split(' '), now = Date.now(); lastPong = now;
  if (!(Number(t0) > 0)) return;
  trk.add(Number(t0), now, tv == null ? null : Number(tv));
  const p = trk.ping(), el = $('#ms'); window.__rtt = p;
  el.textContent = p + ' ms'; el.className = PingMath.level(p);
  send('r ' + p + ' ' + (trk.offset() == null ? 0 : trk.offset()));
  window.__off = trk.offset();
}
setInterval(() => {
  send('p ' + Date.now());
  if (ws && ws.readyState === 1 && Date.now() - lastPong > 7000) { const w = ws; w.onclose = null; try { w.close(); } catch (e) {} lost(); }     // a link that died without a close (Wi-Fi gone, TV off)
}, 2000);
// the phone woke up: do not wait for the next retry
document.addEventListener('visibilitychange', () => { if (!document.hidden && !gaveUp && (!ws || ws.readyState > 1)) { retry = 400; connect(); } });

// ---- presses
function press(b, on) {
  if (on === held.has(b)) return;
  if (on) { held.add(b); buzz(12); } else held.delete(b);
  send((on ? 'd ' : 'u ') + b + ' ' + Date.now());               // the stamp lets the TV measure the delay of this press
  document.querySelectorAll('[data-b="' + b + '"]').forEach((el) => el.classList.toggle('on', on));
  const arm = { UP: 'u', DOWN: 'd', LEFT: 'l', RIGHT: 'r' }[b];
  if (arm && !dp.classList.contains('off')) $('.arm.' + arm).classList.toggle('on', on);
}
function releaseAll() { [...navT.keys()].forEach(navUp); touches.clear(); stickEnd(); [...held].forEach((b) => press(b, false)); }

// Every finger is followed by its identifier; on each move what it is over is looked up again, so a thumb can slide A -> B or
// across the d-pad's arms without lifting. A d-pad finger near a corner holds two arms.
const touches = new Map();           // identifier -> Set of button names
const dp = $('#dpad');
function under(x, y) {
  const out = new Set();
  const r = dp.getBoundingClientRect();
  if (!dp.classList.contains('off') && x >= r.left && x <= r.right && y >= r.top && y <= r.bottom) {
    const nx = (x - r.left - r.width / 2) / (r.width / 2), ny = (y - r.top - r.height / 2) / (r.height / 2);
    if (Math.hypot(nx, ny) >= 0.2) {
      if (nx < -0.34) out.add('LEFT'); else if (nx > 0.34) out.add('RIGHT');
      if (ny < -0.34) out.add('UP'); else if (ny > 0.34) out.add('DOWN');
    }
    return out;
  }
  const el = document.elementFromPoint(x, y), b = el && el.closest && el.closest('[data-b]');
  if (b && !b.hidden) out.add(b.dataset.b);
  return out;
}
function apply() {
  const all = new Set(); touches.forEach((s) => s.forEach((x) => all.add(x)));
  ['UP', 'DOWN', 'LEFT', 'RIGHT', 'A', 'B', 'C', 'D', 'X', 'Y', 'L', 'R', 'L2', 'R2', 'START', 'SELECT'].forEach((b) => press(b, all.has(b)));
}

// ---- navigate mode: a tap sends "n NAME" (the TV presses that key in TV Games); an arrow held repeats like a remote's
const navT = new Map();      // touch id -> { el, timer }
function navSend(el) { send('n ' + el.dataset.n); }
function navDown(id, el) {
  el.classList.add('on'); buzz(12); navSend(el);
  const rec = { el, timer: null };
  if (['UP', 'DOWN', 'LEFT', 'RIGHT'].includes(el.dataset.n)) {
    rec.timer = setTimeout(() => { rec.timer = setInterval(() => navSend(el), 120); }, 380);
  }
  navT.set(id, rec);
}
function navUp(id) { const r = navT.get(id); if (!r) return; clearTimeout(r.timer); clearInterval(r.timer); r.el.classList.remove('on'); navT.delete(id); }

// ---- actions (gear, haptics, add, tip) run on touchstart, not on click
const ACT = {
  fs() { toggleFs(); buzz(12); },
  sheetx() { $('#sheet').hidden = true; },
  mode() { setMode(mode === 'nav' ? 'pad' : 'nav'); buzz(12); },
  who() { if (mySlot > 0) editName(true); },
  gear() { const m = $('#menu'); m.hidden = !m.hidden; },
  haptic() { setHaptic(!haptics); buzz(12); },
  add(el) { location.href = el.href; },
  stick() { const on = !wantStick(); store.set(stickKey(), on ? '1' : '0'); relayout(); buzz(12); },
  tipx() { $('#tip').hidden = true; store.set('tvg-tip', '1'); },
};
function act(el) { const a = el.closest('[data-act]'); if (a) { ACT[a.dataset.act](a); return true; } return false; }
document.addEventListener('click', (e) => { const a = e.target.closest && e.target.closest('[data-act]'); if (a) { e.preventDefault(); if (!a.__t) ACT[a.dataset.act](a); a.__t = false; } });

// ---- first tap: fullscreen, landscape, screen kept awake
let first = true, lock = null;
async function wake() { try { if (navigator.wakeLock && document.visibilityState === 'visible') lock = await navigator.wakeLock.request('screen'); } catch (e) {} }
function firstTap() {
  if (!first) return; first = false;
  if (canFs) { try { const p = enterFs(); if (p && p.catch) p.catch(() => {}); } catch (e) {} }
  wake();
}
// ---- fullscreen button: the Fullscreen API (webkit-prefixed on old Safari / iPad); iPhone Safari has none for pages, so there it shows
// how to add the pad to the Home Screen (the manifest + apple-mobile-web-app-capable make that icon open fullscreen)
const de = document.documentElement;
const canFs = !!(de.requestFullscreen || de.webkitRequestFullscreen);
const fsEl = () => document.fullscreenElement || document.webkitFullscreenElement || null;
const enterFs = () => (de.requestFullscreen ? de.requestFullscreen({ navigationUI: 'hide' }) : de.webkitRequestFullscreen());
const exitFs = () => (document.exitFullscreen ? document.exitFullscreen() : document.webkitExitFullscreen && document.webkitExitFullscreen());
const IC = {
  enter: '<svg viewBox="0 0 24 24"><path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/></svg>',
  exit: '<svg viewBox="0 0 24 24"><path d="M9 4v5H4M15 4v5h5M9 20v-5H4M15 20v-5h5"/></svg>',
};
function fsSync() {
  const on = !!fsEl();
  $('#fs').innerHTML = on ? IC.exit : IC.enter; $('#fs').dataset.state = on ? 'on' : 'off';
  lockLandscape();
}
// landscape only while fullscreen and showing the game's pad (the remote reads fine upright); refusal is normal (iOS, desktop)
function lockLandscape() {
  try {
    const o = screen.orientation; if (!o) return;
    if (fsEl() && mode === 'pad') { const p = o.lock && o.lock('landscape'); if (p && p.catch) p.catch(() => {}); }
    else if (o.unlock) o.unlock();
  } catch (e) {}
}
function toggleFs() {
  if (!canFs) { $('#sheet').hidden = false; return; }
  try { const p = fsEl() ? exitFs() : enterFs(); if (p && p.catch) p.catch(() => {}); } catch (e) {}
}
['fullscreenchange', 'webkitfullscreenchange'].forEach((n) => document.addEventListener(n, fsSync));
if (navigator.standalone || matchMedia('(display-mode: fullscreen)').matches || matchMedia('(display-mode: standalone)').matches) $('#fs').hidden = true;   // already opened from the Home Screen
fsSync();
const ios = /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
if (ios && !navigator.standalone && !store.get('tvg-tip')) $('#tip').hidden = false;
document.addEventListener('visibilitychange', () => { if (document.hidden) releaseAll(); else wake(); });

// ---- touch: nothing may scroll, zoom or select, with any number of fingers
function handle(e) {
  if (e.target.closest && e.target.closest('#textrow,#nameform')) return;      // the text field needs its own touches (keyboard, caret)
  e.preventDefault();
  if (e.type === 'touchstart') {
    firstTap();
    for (const t of e.changedTouches) {
      const el = document.elementFromPoint(t.clientX, t.clientY);
      if (el && act(el)) { const a = el.closest('[data-act]'); a.__t = true; continue; }
      if (stickOn && sId == null && t.clientY > 60 && (portrait() ? t.clientY < innerHeight * 0.5 : t.clientX < innerWidth * 0.5) && !(el && el.closest('[data-b]'))) stickStart(t);
    }
    if (!$('#menu').hidden && !e.target.closest('#menu,#gear')) $('#menu').hidden = true;
  }
  for (const t of e.changedTouches) {
    if (e.type === 'touchstart') { const el = document.elementFromPoint(t.clientX, t.clientY), nb = el && el.closest && el.closest('[data-n]'); if (nb && mode === 'nav') { navDown(t.identifier, nb); continue; } }
    if (navT.has(t.identifier)) { if (e.type === 'touchend' || e.type === 'touchcancel') navUp(t.identifier); continue; }
    if (t.identifier === sId) { if (e.type === 'touchend' || e.type === 'touchcancel') stickEnd(); else stickMove(t); continue; }
    if (e.type === 'touchend' || e.type === 'touchcancel') touches.delete(t.identifier);
    else touches.set(t.identifier, under(t.clientX, t.clientY));
  }
  apply();
}
['touchstart', 'touchmove', 'touchend', 'touchcancel'].forEach((n) => document.addEventListener(n, handle, { passive: false }));
['gesturestart', 'gesturechange', 'gestureend', 'contextmenu', 'dblclick', 'selectstart', 'dragstart'].forEach((n) => document.addEventListener(n, (e) => e.preventDefault(), { passive: false }));
window.addEventListener('blur', releaseAll);
$('#textrow').addEventListener('submit', (e) => { e.preventDefault(); const v = $('#txt').value.trim(); if (v) { send('t ' + v); $('#txt').value = ''; buzz(12); } });
setMode('nav');
// the site's overrides of a look (buttons, face, sh, mid per system: docs/games/games.json "layouts"): a pad fix without an app release
fetch('/layouts.json?k=' + encodeURIComponent(K)).then((r) => r.json()).then((o) => {
  for (const k of Object.keys(o || {})) if (SYS[k] && o[k] && typeof o[k] === 'object') { const {face, sh, mid} = o[k]; Object.assign(SYS[k], JSON.parse(JSON.stringify({face, sh, mid}))); }
  SYS.web = SYS.ps; relayout();
}).catch(() => {});
connect();
