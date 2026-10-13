// shared helpers, status chips, ADB gating
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
    if (!r.ok) { let e = r.statusText; try { e = (await r.json()).error || e; } catch {} if (!quiet) toast('Lỗi: ' + e); return null; }
    return await r.json();
  } catch { if (!quiet) toast('Không gọi được server'); return null; }
}

// ---------- status ----------
let curHost = null, sentToSetup = false;
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

let tvOn = false;                                                    // the power button asks before turning a TV that is on off
let volHold = 0;                                                      // don't yank the slider while the user drags it
async function refresh() {
  if (stopped) return;
  const s = await fetch('/api/state').then((r) => r.json()).catch(() => null);
  tvOn = !!(s && s.connected && s.is_on);
  if (!s) { setChip('#chipTv', 'bad', 'Server tắt'); setChip('#chipAdb', '', 'ADB: ?'); $('#info').textContent = 'Không gọi được server. Chạy lại start-mac.command.'; return; }
  setChip('#chipTv', s.connected && s.is_on ? 'ok' : s.connected ? 'warn' : 'bad', !s.connected ? 'TV: mất kết nối' : s.is_on ? 'TV: đang bật' : 'TV: màn hình tắt');
  ADB = s.adb || { state: 'unknown' };
  const [cls, txt] = ADB_TEXT[ADB.state] || ADB_TEXT.unknown; setChip('#chipAdb', cls, txt);
  $('#name').textContent = s.name || (s.device && s.device.model) || (s.host ? 'TV ' + s.host : 'Chưa chọn TV');
  $('#info').textContent = !s.host ? 'Mở tab “TV” để tìm và thêm TV' : [s.host, appName(s.app), s.paired === false ? 'chưa ghép remote (tab TV)' : s.error].filter(Boolean).join(' · ') || 'đã kết nối';
  if (!s.host && !sentToSetup) { sentToSetup = true; showTab('tv'); }
  if (s.host !== curHost) { const first = curHost === null; curHost = s.host; if (!first) tvChanged(); }
  if (s.volume && Date.now() > volHold) {
    $('#volr').max = s.volume.max || 100; $('#volr').value = s.volume.level; $('#volv').textContent = s.volume.level;
    $('#mute').classList.toggle('on', !!s.volume.muted); $('#mute').title = s.volume.muted ? 'Đang tắt tiếng, bấm để bật' : 'Tắt tiếng';
  }
  applyAdb();
}

// ---------- ADB gating + banners ----------
const BANNERS = {
  closed: ['bad', 'Cổng ADB đang đóng', 'TV đang tắt/standby sâu, vừa khởi động lại, hoặc chưa bật “Gỡ lỗi ADB”. Remote vẫn dùng được nếu TV còn mạng.'],
  unauthorized: ['warn', 'TV chưa cho phép máy này', 'Nhìn lên TV, chọn “Luôn cho phép” ở hộp thoại gỡ lỗi USB rồi bấm “Thử kết nối lại”.'],
  offline: ['warn', 'ADB đang offline', 'Thiết bị có trong danh sách nhưng không trả lời. Thử kết nối lại, hoặc tắt/bật “Gỡ lỗi ADB” trên TV.'],
  error: ['bad', 'Không kết nối được ADB', 'Thử kết nối lại. Nếu vẫn lỗi, kiểm tra TV còn mạng và đã bật “Gỡ lỗi ADB”.'],
  unknown: ['warn', 'Đang kiểm tra ADB...', ''],
};
let bannerKey = '';
function buildBanner() {
  const [cls, title, text] = BANNERS[ADB.state] || BANNERS.unknown;
  const retry = el('button', { className: 'small primary', textContent: '🔄 Thử kết nối lại', onclick: async (e) => {
    const b = e.currentTarget; b.disabled = true; b.textContent = 'Đang kết nối...';
    const r = await api('/api/adb/connect', {}); if (r) { ADB = r; bannerKey = ''; applyAdb(); refresh(); }
    b.disabled = false; b.textContent = '🔄 Thử kết nối lại';
  } });
  const loss = el('p'); loss.append(el('b', { textContent: 'Mất chức năng: ' }), 'xem màn hình, mở app/link, danh sách app, tệp, tải link về TV, cài APK.');
  return el('div', { className: 'banner ' + cls }, el('b', { textContent: title }), el('p', { textContent: text }), loss,
    el('div', { className: 'actions' }, retry, el('button', { className: 'small', textContent: '❓ Cách bật ADB', onclick: () => { const h = $('#help'); h.open = true; h.scrollIntoView({ behavior: 'smooth', block: 'center' }); } })));
}
function applyAdb() {
  const ready = ADB.state === 'ready';
  if (bannerKey !== ADB.state) {                                      // rebuild only on change so buttons are not replaced mid-click
    bannerKey = ADB.state;
    document.querySelectorAll('[data-adb-banner]').forEach((b) => b.replaceChildren(...(ready ? [] : [buildBanner()])));
  }
  document.querySelectorAll('[data-adb-gated]').forEach((g) => { g.hidden = !ready; });
  $('#screenbtn').disabled = !ready && !screenOn;
  if (!ready && screenOn) stopScreen('ADB chưa sẵn sàng, đã dừng xem màn hình.');
  if (ready && autoView) { autoView = false; startScreen(); }           // bookmark ?view=1 to start watching right away
  if (ready) { if (!quickLoaded) loadQuick(); if (currentTab === 'apps' && !appsLoaded) loadApps(); if (currentTab === 'files' && !filesLoaded) loadFiles(cwd); }
}

