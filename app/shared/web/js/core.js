// shared helpers, status chips, ADB gating
// The same page is served by three cores, and they answer the same /api calls:
//   mac    the program on the computer (app/web/server.py): many TVs, remote buttons without ADB, first-time setup
//   tv     the TV Setup app on the TV itself: one TV, opened from any browser after typing a code shown on the TV
//   phone  the phone app's Manage screen: apps, files, health and terminal only (the app has its own remote)
// What one core cannot do is marked data-for="..." in index.html and hidden by style.css; the few differences in behaviour are asked here.
const CORE = document.documentElement.dataset.core || 'mac';
document.documentElement.dataset.core = CORE;
const $ = (s) => document.querySelector(s);
// icon + words as separate spans, so narrow screens can show the icon alone (see .lbl in style.css)
const setLabel = (b, icon, text) => { b.replaceChildren(el('span', { className: 'ico', textContent: icon }), el('span', { className: 'lbl', textContent: ' ' + text })); if (!b.title) b.title = text; return b; };
const el = (tag, props = {}, ...kids) => { const e = Object.assign(document.createElement(tag), props); kids.forEach((k) => k != null && e.append(k)); return e; };
const fmtSize = (n) => n > 1e9 ? (n / 1e9).toFixed(1) + ' GB' : n > 1e6 ? (n / 1e6).toFixed(1) + ' MB' : n > 1e3 ? Math.round(n / 1e3) + ' KB' : n + ' B';
const sleep = (ms) => new Promise((z) => setTimeout(z, ms));
let toastT;
function toast(m) { const t = $('#toast'); t.textContent = m; t.classList.add('show'); clearTimeout(toastT); toastT = setTimeout(() => t.classList.remove('show'), 2500); }

async function api(path, data, quiet) {
  try {
    const r = await fetch(path, data ? { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) } : undefined);
    if (r.status === 401 && CORE === 'tv') { refresh(); return null; }   // this browser is no longer paired with the TV
    if (!r.ok) {
      let e = r.statusText; try { e = (await r.json()).error || e; } catch {}
      if (r.status === 503 && /^adb/.test(e)) { e = /allow/.test(e) ? 'TV đang hỏi cho phép gỡ lỗi: nhìn lên TV, chọn “Luôn cho phép”' : 'chưa nối được ADB tới TV'; setTimeout(refresh, 300); }
      if (!quiet) toast('Lỗi: ' + e); return null;
    }
    return await r.json();
  } catch { if (!quiet) toast('Không gọi được server'); return null; }
}

// The errors people actually meet, from any core (Kotlin or Python words), in words they can act on; anything else as it came.
const FRIENDLY = [
  [/SSLHandshake|CertPathValidator|Chain validation|CERTIFICATE_VERIFY|not yet valid/i, 'Không mở được kết nối bảo mật. Ngày giờ trên TV có đúng không? (Cài đặt → Hệ thống → Ngày và giờ)'],
  [/UnknownHost|Name or service not known|nodename nor servname|getaddrinfo/i, 'TV (hoặc máy này) chưa vào được Internet.'],
  [/SocketTimeout|timed out|ConnectException|Connection reset/i, 'Mạng chậm hoặc vừa mất kết nối. Thử lại sau ít phút.'],
  [/stopped answering|stopped taking data/i, 'TV ngừng trả lời giữa chừng. Thử lại; vẫn vậy thì khởi động lại TV.'],
  [/copy to the TV incomplete/i, 'Chép tệp lên TV không trọn vẹn. Thử lại.'],
  [/no sign-in link/i, 'Không đọc được link đăng nhập trên màn hình TV. Mở Tailscale trên TV và quét mã QR nó hiện.'],
  [/INSUFFICIENT_STORAGE|No space left/i, 'TV hết bộ nhớ trống.'],
  [/no app that can open/i, 'TV không có app mở loại tệp này (cài VLC rồi thử lại).'],
];
const friendly = (m) => { m = String(m || ''); const f = FRIENDLY.find(([re]) => re.test(m)); return f ? f[1] : m; };

// ---------- status ----------
let curHost = null;
// The list of TVs comes first, as in the phone app: shown whenever no TV is chosen, or when "Change TV" asks for it.
// It always opens on the list; "Change TV" comes back to it, and only then is there a TV to go back to.
let picking = false, wantPick = CORE === 'mac', cameFromTv = false, justPicked = null, pickedAt = 0, scannedOnce = false;
function setPicking(on, tvName) {
  const b = $('#pickback'), back = cameFromTv && !!tvName;
  b.hidden = !back; if (back) b.textContent = '← ' + tr('Về lại') + ' ' + tvName;
  if (on === picking) return;
  picking = on; document.querySelector('main').classList.toggle('picking', on);
  if (on) {
    if (screenOn) stopScreen();
    document.querySelector('main').classList.remove('wide');
    loadTvs();
    if (!scannedOnce) { scannedOnce = true; $('#scan').click(); }       // look around without being asked, once
  } else showTab(currentTab);
}
let ADB = { state: 'unknown' }, autoView = new URLSearchParams(location.search).has('view');
const ADB_TEXT = {
  ready: ['ok', 'ADB: sẵn sàng'],
  unauthorized: ['warn', 'ADB: chờ TV cho phép'],
  closed: ['bad', 'ADB: cổng đóng'],
  offline: ['warn', 'ADB: offline'],
  error: ['bad', 'ADB: lỗi'],
  unknown: ['', 'ADB: ...'],
};
function setChip(id, cls, text) { const c = $(id); c.className = 'chip ' + cls; c.lastElementChild.textContent = text; }

let STATE = {};
let tvOn = false;                                                    // the power button asks before turning a TV that is on off
let volHold = 0;                                                      // don't yank the slider while the user drags it
async function refresh() {
  if (stopped) return;
  let locked = false;
  const s = await fetch('/api/state').then((r) => { locked = r.status === 401; return r.json(); }).catch(() => null);
  if (CORE === 'tv') setLocked(locked, s);
  if (locked) return;
  tvOn = !!(s && s.connected && s.is_on); STATE = s || {};
  if (!s) {
    setChip('#chipTv', 'bad', CORE === 'mac' ? 'Server tắt' : 'TV: mất kết nối'); setChip('#chipAdb', '', 'ADB: ?');
    $('#info').textContent = CORE === 'mac' ? 'Không gọi được server. Chạy lại start-mac.command.' : 'Không gọi được TV: TV đang bật và cùng mạng với máy này chứ?'; return;
  }
  const adbUp = s.adb && s.adb.state === 'ready';                   // over ADB the TV answers even when the remote-button service does not
  setChip('#chipTv', s.connected && s.is_on ? 'ok' : s.connected || adbUp ? 'warn' : 'bad', s.connected ? (s.is_on ? 'TV: đang bật' : 'TV: màn hình tắt') : adbUp ? 'Nút remote: chưa nối' : 'TV: mất kết nối');
  ADB = s.adb || { state: 'unknown' };
  const [cls, txt] = ADB_TEXT[ADB.state] || ADB_TEXT.unknown; setChip('#chipAdb', cls, txt);
  const tvName = s.name || (s.device && s.device.model) || (s.host ? 'TV ' + s.host : '');
  if (CORE === 'mac') setPicking(!s.host || wantPick, tvName);
  if (s.version) $('#ver').textContent = (s.app_name || 'TV Remote') + ' ' + s.version + (s.released ? ' · ' + s.released : '');
  document.title = tvName && !picking ? tvName + ' · Remote' : 'TV Remote';
  $('#name').textContent = picking ? 'TV Remote' : tvName;
  $('#info').textContent = picking ? tr('Chọn TV để điều khiển') : [CORE === 'mac' ? s.host : '', appName(s.app), s.paired === false ? 'chưa ghép remote (tab Thiết lập)' : s.error ? 'nút remote chưa kết nối được (tab Thiết lập)' : ''].filter(Boolean).join(' · ') || 'đã kết nối';
  if (justPicked && s.host === justPicked) {                          // straight to the remote when the TV is ready, to the first-time steps when it is not
    const ready = s.connected || (s.adb && s.adb.state === 'ready');
    if (ready || Date.now() - pickedAt > 6000) { justPicked = null; showTab(ready ? 'remote' : 'tv'); }   // a TV takes a few seconds to answer
    else { setTimeout(refresh, 1000); if (s.name) $('#conname').textContent = s.name; }
  }
  if (!justPicked && $('#connecting').open) $('#connecting').close();
  if (s.host !== curHost) { const first = curHost === null; curHost = s.host; if (!first) tvChanged(); }
  if (s.volume && Date.now() > volHold) {
    $('#volv').textContent = s.volume.level;
    $('#mute').classList.toggle('on', !!s.volume.muted); $('#mute').title = s.volume.muted ? 'Đang tắt tiếng, bấm để bật' : 'Tắt tiếng';
  }
  applyAdb(); allowAlert(); checkAsk();
  if ($('#assist').open && (ADB.state === 'ready' || ADB.state === 'unauthorized')) {   // the TV has opened its ADB port: nothing left to help with
    $('#assist').close(); toast(ADB.state === 'ready' ? 'ADB đã kết nối' : 'ADB đã bật: nhìn lên TV, chọn “Luôn cho phép”');
  }
}

// ---------- ADB gating + banners ----------
// The page from the TV works through the TV Setup app's own ADB session: without it nothing works, and there is no other machine to blame.
const BANNERS_TV = {
  closed: ['bad', 'App trên TV chưa nối được ADB', 'Các nút chưa hoạt động. Trên TV: bật “Gỡ lỗi ADB” (hoặc “Gỡ lỗi USB”) trong Tùy chọn nhà phát triển, rồi mở app TV Setup.'],
  unauthorized: ['warn', 'TV đang hỏi cho phép gỡ lỗi', 'Nhìn lên TV: tích “Luôn cho phép” rồi chọn OK. Trang này tự chạy tiếp.'],
  unknown: ['warn', 'Đang kết nối ADB...', ''],
};
BANNERS_TV.error = BANNERS_TV.offline = BANNERS_TV.closed;
const BANNERS = {
  closed: ['bad', 'Cổng ADB đang đóng', 'TV đang tắt/standby sâu, vừa khởi động lại, hoặc chưa bật “Gỡ lỗi ADB”. Remote vẫn dùng được nếu TV còn mạng.'],
  unauthorized: ['warn', 'TV chưa cho phép máy này', 'Nhìn lên TV, chọn “Luôn cho phép” ở hộp thoại gỡ lỗi USB rồi bấm “Thử kết nối lại”.'],
  offline: ['warn', 'ADB đang offline', 'Thiết bị có trong danh sách nhưng không trả lời. Thử kết nối lại, hoặc tắt/bật “Gỡ lỗi ADB” trên TV.'],
  error: ['bad', 'Không kết nối được ADB', 'Thử kết nối lại. Nếu vẫn lỗi, kiểm tra TV còn mạng và đã bật “Gỡ lỗi ADB”.'],
  unknown: ['warn', 'Đang kiểm tra ADB...', ''],
};
let bannerKey = '', scenesLoaded = false;
function buildBanner() {
  const set = CORE === 'mac' ? BANNERS : BANNERS_TV, [cls, title, text] = set[ADB.state] || set.unknown;
  const retry = el('button', { className: 'small primary', textContent: '🔄 Thử kết nối lại', onclick: async (e) => {
    const b = e.currentTarget; b.disabled = true; b.textContent = 'Đang kết nối...';
    const r = await api('/api/adb/connect', {}); if (r) { ADB = r; bannerKey = ''; applyAdb(); refresh(); }
    b.disabled = false; b.textContent = '🔄 Thử kết nối lại';
  } });
  if (CORE !== 'mac') return el('div', { className: 'banner ' + cls }, el('b', { textContent: title }), el('p', { textContent: text }), el('div', { className: 'actions' }, retry));
  const helpMe = ADB.state === 'closed' || ADB.state === 'error' ? el('button', { className: 'small primary', textContent: '🧭 Bật ADB giúp tôi', onclick: () => adbHelp() }) : null;
  if (helpMe) retry.className = 'small';
  const loss = el('p'); loss.append(el('b', { textContent: 'Mất chức năng: ' }), 'xem màn hình, mở app/link, danh sách app, tệp, tải link về TV, cài APK.');
  return el('div', { className: 'banner ' + cls }, el('b', { textContent: title }), el('p', { textContent: text }), loss,
    el('div', { className: 'actions' }, helpMe, retry, el('button', { className: 'small', textContent: '❓ Cách bật ADB', onclick: () => { const h = $('#help'); h.open = true; h.scrollIntoView({ behavior: 'smooth', block: 'center' }); } })));
}
function applyAdb() {
  const ready = ADB.state === 'ready';
  if (bannerKey !== ADB.state) {                                      // rebuild only on change so buttons are not replaced mid-click
    bannerKey = ADB.state;
    document.querySelectorAll('[data-adb-banner]').forEach((b) => b.replaceChildren(...(ready ? [] : [buildBanner()])));
    if (ready && currentTab === 'tv' && typeof tsState === 'function') tsState();
  }
  document.querySelectorAll('[data-adb-gated]').forEach((g) => { g.hidden = !ready; });
  $('#kbd').disabled = !ready; $('#kbdoff').hidden = ready;           // typing goes through ADB: no half-working box without it
  $('#screenbtn').disabled = !ready && !screenOn;
  if (!ready && screenOn) stopScreen('ADB chưa sẵn sàng, đã dừng xem màn hình.');
  if (ready && autoView) { autoView = false; startScreen(); }           // bookmark ?view=1 to start watching right away
  if (ready) { if (!quickLoaded) loadQuick(); if (currentTab === 'apps' && !appsLoaded) loadApps(); if (currentTab === 'files' && !filesLoaded) loadFiles(cwd); if (!scenesLoaded) { scenesLoaded = true; loadScenes(); } if (currentTab === 'auto' && !autoLoaded) autoTab(); }
  if (!ready && listenOn) stopListen('ADB chưa sẵn sàng, đã dừng nghe tiếng TV.');
}


// ---------- the TV asks "Allow USB debugging?" for another device: answer it from here (for its key only) ----------
let askSeen = '', askAt = 0;
async function checkAsk() {
  if (ADB.state !== 'ready' || document.hidden || Date.now() - askAt < 7000) return;
  askAt = Date.now();
  const r = await fetch('/api/adb/prompt').then((x) => x.ok ? x.json() : null).catch(() => null);
  const fp = (r && r.fingerprint) || '';
  $('#askother').hidden = !fp || picking; if (fp !== askSeen) { askSeen = fp; $('#askfp').textContent = fp; }
}
async function answerAsk(yes) {
  const fp = askSeen; if (!fp) return;
  if (yes && !confirm(tr('Cho phép thiết bị có mã này điều khiển toàn bộ TV qua ADB?') + '\n\n' + fp)) return;
  $('#askyes').disabled = $('#askno').disabled = true;
  const r = await api(yes ? '/api/adb/allow' : '/api/adb/deny', { fingerprint: fp });
  $('#askyes').disabled = $('#askno').disabled = false;
  if (r) toast(yes ? (r.result === 'allowed' ? 'Đã cho phép thiết bị đó' : 'Không cho phép được: hộp hỏi trên TV đã đổi') : 'Đã từ chối');
  askAt = 0; checkAsk();
}
$('#askyes').addEventListener('click', () => answerAsk(true));
$('#askno').addEventListener('click', () => answerAsk(false));

// ---------- "Look at the TV": full screen while the TV is asking whether to allow debugging ----------
let allowLater = false;                                               // "Later" was pressed: not again for this round of asking
function allowAlert() {
  const d = $('#allow'), asking = ADB.state === 'unauthorized';
  if (!asking) { allowLater = false; if (d.open) d.close(); return; }
  if (d.open || allowLater || picking) return;
  if ($('#assist').open) $('#assist').close();
  d.showModal();
}
$('#allowlater').addEventListener('click', () => { allowLater = true; $('#allow').close(); });
$('#allow').addEventListener('cancel', () => { allowLater = true; });   // Esc

// ---------- the page from the TV: one code, shown on the TV, lets this browser in for good ----------
function setLocked(on, s) {
  const m = document.querySelector('main'); if (on === m.classList.contains('locked')) return;
  m.classList.toggle('locked', on); $('#pair').hidden = !on;
  if (on) { if (screenOn) stopScreen(); m.classList.remove('wide'); $('#name').textContent = (s && s.name) || 'TV Remote'; $('#info').textContent = tr('Chưa ghép với TV này'); $('#pairf').hidden = true; $('#pairerr').textContent = ''; }
  else showTab(currentTab);
}
$('#pairstart').addEventListener('click', async () => {
  if (!await api('/api/pair/start', {})) return;
  $('#pairf').hidden = false; $('#pairerr').textContent = tr('Nhìn lên TV: mã có hiệu lực trong 2 phút.'); $('#paircode').value = ''; $('#paircode').focus();
});
$('#pairf').addEventListener('submit', async (e) => {
  e.preventDefault();
  const r = await fetch('/api/pair/finish', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ code: $('#paircode').value }) }).then(async (x) => ({ ok: x.ok, ...(await x.json().catch(() => ({}))) })).catch(() => ({}));
  if (r.ok) { $('#pairerr').textContent = ''; refresh(); return; }
  $('#pairerr').textContent = tr(r.error === 'wrong' ? 'Sai mã, thử lại.' : 'Mã đã hết hạn: bấm “Hiện mã trên TV” để lấy mã mới.'); if (r.error !== 'wrong') $('#pairf').hidden = true; $('#paircode').select();
});

// ---------- the TV's page on a phone: it can live on the home screen like an app (manifest.json) ----------
// Said once; a browser that is already showing it as an app, or a computer, is not told.
function homeHint() {
  const standalone = matchMedia('(display-mode: standalone)').matches || navigator.standalone;
  let seen = false; try { seen = !!localStorage.getItem('homehint'); } catch {}
  if (CORE !== 'tv' || standalone || seen || !matchMedia('(pointer:coarse)').matches) return;
  const ios = /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  $('#homehow').textContent = ios ? 'Trong Safari: bấm nút Chia sẻ (ô vuông có mũi tên lên) → “Thêm vào MH chính”. Mở từ biểu tượng đó, trang hỏi mã ghép một lần nữa (iPhone giữ dữ liệu riêng cho nó).'
    : 'Trong Chrome: bấm menu ⋮ → “Thêm vào màn hình chính”. Lần sau mở thẳng từ biểu tượng, như một app.';
  $('#homehint').hidden = false;
}
$('#homeok').addEventListener('click', () => { $('#homehint').hidden = true; try { localStorage.setItem('homehint', '1'); } catch {} });
