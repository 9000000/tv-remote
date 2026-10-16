// shared helpers, status chips, ADB gating
// The same page is served by three cores, and they answer the same /api calls:
//   mac    the program on the computer (app/desktop): many TVs, remote buttons without ADB, first-time setup
//   tv     the TV Setup app on the TV itself: one TV, opened from any browser after typing a code shown on the TV
//   phone  the phone app's Manage screen: apps, files, health and terminal only (the app has its own remote)
// What one core cannot do is marked data-for="..." in index.html and hidden by style.css; the few differences in behaviour are asked here.
const CORE = document.documentElement.dataset.core || 'pc';
document.documentElement.dataset.core = CORE;
const $ = (s) => document.querySelector(s);
// icon + words as separate spans, so narrow screens can show the icon alone (see .lbl in style.css)
// asks: its handler asks before doing anything, so the icon-only question below is not asked as well
const setLabel = (b, icon, text, asks) => { if (asks) b.dataset.asks = ''; b.replaceChildren(el('span', { className: 'ico', textContent: icon }), el('span', { className: 'lbl', textContent: ' ' + text })); if (!b.title) b.title = text; return b; };
const el = (tag, props = {}, ...kids) => { const e = Object.assign(document.createElement(tag), props); kids.forEach((k) => k != null && e.append(k)); return e; };
const fmtSize = (n) => n > 1e9 ? (n / 1e9).toFixed(1) + ' GB' : n > 1e6 ? (n / 1e6).toFixed(1) + ' MB' : n > 1e3 ? Math.round(n / 1e3) + ' KB' : n + ' B';
const sleep = (ms) => new Promise((z) => setTimeout(z, ms));
let toastT;
function toast(m) { const t = $('#toast'); t.textContent = m; t.classList.add('show'); clearTimeout(toastT); toastT = setTimeout(() => t.classList.remove('show'), 2500); }
// Questions asked in the page, not with confirm() / prompt(): they look the same everywhere, and the computer app's window
// (a bare web view) has no dialogs of its own. ask(text) → true / false; ask(text, { value }) → the typed text, or null.
// { ok, danger }: the words and colour of the yes button. One question at a time; the button that asked keeps its spinner.
// askTime(text, minutes, max) → minutes, or null: an hours box and a minutes box, so nobody guesses which unit a number is.
let askQ = Promise.resolve();
function ask(text, o = {}) {
  const from = clicked;
  const run = () => new Promise((done) => {
    const typed = o.value !== undefined || !!o.fields;
    const boxes = o.fields ? o.fields.map((f) => el('input', { type: 'number', inputMode: 'numeric', min: 0, max: f.max, value: f.value, placeholder: '0' })) : null;
    const input = o.fields ? el('div', { className: 'fields' }, ...o.fields.flatMap((f, i) => [el('label', {}, boxes[i], el('span', { textContent: tr(f.label) }))]))
      : typed ? el('input', { value: o.value, spellcheck: false }) : null;
    const yes = el('button', { type: 'submit', className: o.danger ? 'danger' : 'primary', textContent: tr(o.ok || 'OK') });
    const no = el('button', { type: 'button', textContent: tr('Hủy'), onclick: () => d.close() });
    const d = el('dialog', { className: 'ask' }, el('form', { className: 'card', onsubmit: (e) => { e.preventDefault(); d.returnValue = 'yes'; d.close(); } },
      el('p', { textContent: tr(text) }), input, el('div', { className: 'acts' }, no, yes)));
    d.addEventListener('close', () => {
      d.remove();
      const ok = d.returnValue === 'yes';
      if (ok) { clicked = from; clickedAt = performance.now(); }       // its call starts now: the spinner goes on the button that asked
      done(typed ? (ok ? (boxes ? boxes.map((b) => b.value) : input.value) : null) : ok);
    });
    document.body.append(d); d.showModal();
    if (boxes) boxes[0].select(); else if (input) input.select(); else yes.focus();
  });
  const p = askQ.then(run); askQ = p.catch(() => {}); return p;
}
async function askTime(text, minutes, max = 1440) {
  const m = Math.round(minutes || 0);
  const v = await ask(text, { fields: [{ label: 'giờ', value: m ? String(Math.floor(m / 60)) : '', max: Math.floor(max / 60) }, { label: 'phút', value: m ? String(m % 60) : '', max: 59 }] });
  if (!v) return null;
  const total = Math.round((+v[0] || 0) * 60 + (+v[1] || 0));
  if (total < 1 || total > max) { toast('Chọn từ 1 phút tới ' + max / 60 + ' giờ.'); return null; }
  return total;
}

// A menu whose choices look like what they are: buttons (the phone app's menu() in Ui.kt, which the remote's menus mirror).
// items: ['@icon|Words', 'a line saying what it does', fn]; primary = the index drawn lit, or -1. The menu closes first, then fn
// runs, so fn can ask the next question.
function menuDlg(title, primary, items) {
  const icon = (name) => el('span', { className: 'mi', innerHTML: '<svg class="ic" aria-hidden="true"><use href="#i-' + name + '"/></svg>' });
  const d = el('dialog', { className: 'ask menu' }, el('div', { className: 'card' },
    el('div', { className: 'card-head' }, el('h2', { textContent: title }), el('button', { className: 'small iconbtn', textContent: '✕', title: 'Đóng', 'aria-label': 'Đóng', onclick: () => d.close() })),
    ...items.map(([token, sub, fn], i) => {
      const bar = token.indexOf('|'), ic = token[0] === '@' && bar > 0;
      return el('button', { className: 'mitem' + (i === primary ? ' primary' : ''), onclick: () => { d.close(); fn(); } },
        ic ? icon(token.slice(1, bar)) : null,
        el('span', { className: 'mt' }, el('b', { textContent: ic ? token.slice(bar + 1) : token }), sub ? el('small', { textContent: sub }) : null),
        el('span', { className: 'mgo', textContent: '›' }));
    })));
  d.addEventListener('close', () => d.remove());
  document.body.append(d); d.showModal();
  return d;
}

// On a narrow screen some buttons show their icon alone (.lbl folded away, style.css). A tap there first says what it does, so
// nobody acts on a guess at an icon; yes then does it. Not for ✕ and ⋯ (they say it by themselves), nor for buttons whose
// own handler asks already (data-asks): one question is enough.
let iconSure = false;
document.addEventListener('click', async (e) => {
  if (iconSure || !e.isTrusted || e.target.tagName === 'INPUT') return;   // a person's tap only: not our own click once sure, nor a label passing its click on
  const b = e.target.closest && e.target.closest('button, label.linkbtn'); if (!b || b.disabled || 'asks' in b.dataset) return;
  if (b.closest('.tabs')) return;                                      // a tab only shows a page: nothing to ask
  const ls = [...b.querySelectorAll(':scope > .lbl')], l = ls[0];      // the tv tab has a word per core, one of them hidden
  if (!l || ls.some((x) => x.getBoundingClientRect().width > 1)) return;
  const name = l.textContent.trim(); if (!name || /^(✕|⋯)$/.test(b.textContent.trim())) return;
  e.preventDefault(); e.stopImmediatePropagation();
  const ico = (b.querySelector(':scope > .ico') || {}).textContent || '', why = b.title && b.title.trim() !== name ? b.title.trim() : '';
  if (!await ask((ico + ' ' + (why || name)).trim() + '?', { ok: name, danger: b.classList.contains('danger') })) return;
  const input = b.tagName === 'LABEL' && b.querySelector('input');
  iconSure = true; try { (input || b).click(); } finally { iconSure = false; }
}, true);

// The computer's page is read fresh from disk, its server's code only at start: a newer page's calls then answer 404.
const STALE = 'Trang mới hơn phần đang chạy: bấm ⏻ Tắt rồi mở lại TV Remote.';
// Every button that waits on the server shows it: the one just clicked spins until its call returns (style.css .busy, shown
// only past 150 ms so quick calls do not flicker). Counted from the end of the click handler, or from the yes to an ask() in it.
let clicked = null, clickedAt = Infinity;
document.addEventListener('click', (e) => { clicked = e.target.closest && e.target.closest('button'); clickedAt = Infinity; setTimeout(() => { clickedAt = performance.now(); }, 0); }, true);
async function api(path, data, quiet) {
  const b = clicked && !clicked.dataset.busy && performance.now() - clickedAt < 1000 ? clicked : null;
  if (b) { b.classList.add('busy'); b.busyN = (b.busyN || 0) + 1; }
  try { return await call(path, data, quiet); } finally { if (b && !--b.busyN) b.classList.remove('busy'); }
}
async function call(path, data, quiet) {
  try {
    const r = await fetch(path, data ? { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) } : undefined);
    if (r.status === 401 && CORE === 'tv') { refresh(); return null; }   // this browser is no longer paired with the TV
    if (!r.ok) {
      let e = r.statusText; try { e = (await r.json()).error || e; } catch {}
      if (!quiet && needSetup(e)) return null;                         // asked as a question with its button, not a toast
      if (r.status === 404 && CORE === 'pc') e = STALE;
      if (r.status === 503 && /^adb/.test(e)) { e = /allow/.test(e) ? 'TV đang hỏi cho phép gỡ lỗi: nhìn lên TV, chọn “Luôn cho phép”' : 'chưa nối được ADB tới TV'; setTimeout(refresh, 300); }
      if (!quiet) toast('Lỗi: ' + friendly(e)); return null;
    }
    return await r.json();
  } catch { if (!quiet) toast('Không gọi được server'); return null; }
}

// A small menu under a button (⋯ on each app): items are [icon, words, fn, class]; null items are left out. A click elsewhere,
// Esc or scrolling closes it; the same button again too.
let popEl = null;
function closePop() { if (popEl) { popEl.remove(); popEl = null; } }
function popover(anchor, items) {
  const again = popEl && popEl.anchor === anchor; closePop(); if (again) return;
  const m = el('div', { className: 'pop', role: 'menu' }, ...items.filter(Boolean).map(([icon, text, fn, cls]) =>
    setLabel(el('button', { className: cls || '', role: 'menuitem', onclick: () => { closePop(); fn(); } }), icon, text)));
  m.anchor = anchor; document.body.append(m); popEl = m;
  const r = anchor.getBoundingClientRect(), w = m.offsetWidth, h = m.offsetHeight;
  m.style.left = scrollX + Math.max(8, Math.min(innerWidth - w - 8, r.right - w)) + 'px';
  m.style.top = scrollY + (r.bottom + h + 8 > innerHeight && r.top > h + 8 ? r.top - h - 4 : r.bottom + 4) + 'px';
  m.querySelector('button').focus({ preventScroll: true });
}
document.addEventListener('click', (e) => { if (popEl && !popEl.contains(e.target) && !popEl.anchor.contains(e.target)) closePop(); }, true);
document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && popEl) { const a = popEl.anchor; closePop(); a.focus(); } });
addEventListener('scroll', (e) => { if (popEl && !popEl.contains(e.target)) closePop(); }, true);
addEventListener('resize', closePop);

// A phone or computer can be newer than TV Setup on the TV. What needs which TV Setup is SetupNeed.kt's table; any call that
// meets one (its error says "setup:old|missing:<feature>:<min>:<have>") asks to update or install TV Setup, with the button.
// A page that knows before starting asks setupFor(feature). The same names as SetupNeed.name.
const SETUP_NEEDS = { rules: 'Hẹn giờ, giờ đi ngủ, giới hạn app', draw: 'Chức năng vẽ lên TV', pic: 'Đưa ảnh lên TV', volume: 'Giới hạn âm lượng' };
const SETUP_RE = /setup:(old|missing):(\w+):([0-9.]*):([0-9.]*)/;
const setupSay = (m) => m[1] === 'missing' ? (SETUP_NEEDS[m[2]] || m[2]) + ' cần app TV Setup trên TV. Cài TV Setup?'
  : (SETUP_NEEDS[m[2]] || m[2]) + ' cần TV Setup ' + m[3] + ' trở lên (TV đang có ' + m[4] + '). Cập nhật TV Setup?';
function needSetup(why) {
  const m = String(why || '').match(SETUP_RE); if (!m) return false;
  const old = m[1] === 'old';
  ask(setupSay(m), { ok: old ? '⬆️ Cập nhật' : '⚡ Cài TV Setup' }).then((yes) => { if (yes) (old ? updateSetup : installSetupApk)(); });
  return true;
}
const setupFor = async (feature) => !!await api('/api/setup/need?f=' + feature);

// The errors people actually meet, from any core , in words they can act on; anything else as it came.
const FRIENDLY = [
  [SETUP_RE, setupSay],
  [/no YouTube and no Google search/, 'TV này không có YouTube hay tìm kiếm của Google để tìm câu này.'],
  [/^unsupported: (LG|Samsung)$/, (m) => 'TV ' + m[1] + ' không hỗ trợ'],
  [/^refused$/, 'TV đã từ chối. Bấm Hỏi lại TV ở tab Thiết lập rồi chọn Cho phép.'],
  [/^not accepted$/, 'Chưa ai chọn Cho phép trên TV. Bấm Hỏi lại TV ở tab Thiết lập.'],
  [/SSLHandshake|CertPathValidator|Chain validation|CERTIFICATE_VERIFY|not yet valid/i, 'Không mở được kết nối bảo mật. Ngày giờ trên TV có đúng không? (Cài đặt → Hệ thống → Ngày và giờ)'],
  [/UnknownHost|Name or service not known|nodename nor servname|getaddrinfo/i, 'TV (hoặc máy này) chưa vào được Internet.'],
  [/SocketTimeout|timed out|ConnectException|Connection reset/i, 'Mạng chậm hoặc vừa mất kết nối. Thử lại sau ít phút.'],
  [/stopped answering|stopped taking data/i, 'TV ngừng trả lời giữa chừng. Thử lại; vẫn vậy thì khởi động lại TV.'],
  [/copy to the TV incomplete/i, 'Chép tệp lên TV không trọn vẹn. Thử lại.'],
  [/stream closed|socket closed|broken pipe/i, 'Mất kết nối với TV giữa chừng. Thử lại.'],   // ADB cut: another TV chosen, or the network dropped
  [/no sign-in link/i, 'Không đọc được link đăng nhập trên màn hình TV. Mở Tailscale trên TV và quét mã QR nó hiện.'],
  [/INSUFFICIENT_STORAGE|No space left/i, 'TV hết bộ nhớ trống.'],
  [/no app that can open/i, 'TV không có app mở loại tệp này (cài VLC rồi thử lại).'],
  [/cannot be opened \(no launcher screen\)|no launcher activity/i, 'App này không có màn hình để mở: nó chỉ chạy nền, hoặc là một phần của app khác.'],
  [/not a backup/i, 'Tệp này không phải bản sao lưu của TV Remote.'],
  [/^protected: the TV needs this app/i, 'App này cần cho TV chạy, không tắt hay xóa dữ liệu được.'],
  [/removed by the TV right after install/i, 'TV báo cài xong nhưng tự gỡ app này ngay sau đó. Box của nhà mạng (FPT Play...) chặn một số app; app này không dùng được trên TV này.'],
  // the check before installing (apk_fits / Manage.apkFits), then what pm says when it was not caught there
  [/apk needs sdk (\d+), the tv has sdk (\d+)/i, (m) => 'APK này cần Android ' + androidName(m[1]) + ' trở lên; TV đang chạy Android ' + androidName(m[2]) + '. Tìm bản cũ hơn của app (trang APKMirror ghi “Min: Android ...”).'],
  [/apk is built for (\S+), the tv runs (\S+)/i, (m) => 'APK này dành cho chip ' + m[1].replace(/,/g, ', ') + ', còn TV dùng ' + m[2].replace(/,/g, ', ') + '. Tải bản cho đúng loại chip (thường là armeabi-v7a cho TV).'],
  [/not a changeable permission|SYSTEM_FIXED|POLICY_FIXED|cannot (grant|revoke) system/i, 'TV không cho đổi quyền này: hệ thống giữ nó.'],
  [/has not requested permission|Unknown permission/i, 'App này không xin quyền đó.'],
  [/INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match/i, 'TV đã có app này nhưng từ nguồn khác (khác chữ ký). Gỡ bản đang có rồi cài lại; dữ liệu của app sẽ mất.'],
  [/INSTALL_FAILED_VERSION_DOWNGRADE/i, 'TV đang có bản mới hơn của app này. Gỡ bản đang có nếu muốn cài bản cũ.'],
  [/INSTALL_FAILED_OLDER_SDK/i, 'APK này cần Android mới hơn bản trên TV. Tìm bản cũ hơn của app.'],
  [/TV Setup is up to date/i, 'TV Setup trên TV đã là bản mới nhất.'],
  [/an update of TV Setup is already running/i, 'TV Setup đang tự cập nhật. Chờ khoảng một phút.'],
  [/TV Setup is not installed/i, 'TV chưa có app TV Setup.'],
  [/the update did not start on the TV/i, 'TV chưa bắt đầu cập nhật được. Thử lại; vẫn vậy thì khởi động lại TV.'],
  [/could not write the update script/i, 'Không ghi được tệp cập nhật lên TV. TV còn bộ nhớ trống không?'],
  [/INSTALL_FAILED_NO_MATCHING_ABIS/i, 'APK này dành cho loại chip khác với TV. Tải bản cho đúng loại chip (thường là armeabi-v7a).'],
];
const ANDROID = { 21: '5.0', 22: '5.1', 23: '6', 24: '7', 25: '7.1', 26: '8', 27: '8.1', 28: '9', 29: '10', 30: '11', 31: '12', 32: '12L', 33: '13', 34: '14', 35: '15', 36: '16' };
const androidName = (sdk) => ANDROID[sdk] || 'SDK ' + sdk;
const friendly = (m) => { m = String(m || ''); const f = FRIENDLY.find(([re]) => re.test(m)); return !f ? m : typeof f[1] === 'function' ? f[1](m.match(f[0])) : f[1]; };

// ---------- status ----------
let curHost = null;
// The list of TVs comes first, as in the phone app: shown whenever no TV is chosen, or when "Change TV" asks for it.
// It always opens on the list; "Change TV" comes back to it, and only then is there a TV to go back to.
let picking = false, wantPick = CORE === 'pc' && !(window.LANG_KEEP && !window.LANG_KEEP.picking), cameFromTv = false, justPicked = null, pickedAt = 0, scannedOnce = false;
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
// a few words on the chip, so the header stays on one line on a phone; the whole sentence on hover
const ADB_TEXT = {
  ready: ['ok', 'ADB ✓', 'ADB: sẵn sàng'],
  unauthorized: ['warn', 'ADB: chờ', 'ADB: chờ TV cho phép'],
  closed: ['bad', 'ADB đóng', 'ADB: cổng đóng'],
  offline: ['warn', 'ADB offline', 'ADB: offline'],
  error: ['bad', 'ADB lỗi', 'ADB: lỗi'],
  unknown: ['', 'ADB ...', 'ADB: ...'],
};
function setChip(id, cls, text, long) { const c = $(id); c.className = 'chip ' + cls; c.lastElementChild.textContent = tr(text); c.title = tr(long || text); }

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
  if (s) applyKind(s);
  if (!s) {
    setChip('#chipTv', 'bad', CORE === 'pc' ? 'Server tắt' : 'Mất kết nối', CORE === 'pc' ? 'Server tắt' : 'TV: mất kết nối'); setChip('#chipAdb', '', 'ADB ?');
    $('#info').textContent = CORE === 'pc' ? 'Không gọi được server. Mở lại TV Remote trên máy này.' : 'Không gọi được TV: TV đang bật và cùng mạng với máy này chứ?'; return;
  }
  const adbUp = s.adb && s.adb.state === 'ready';                   // over ADB the TV answers even when the remote-button service does not
  setChip('#chipTv', s.connected && s.is_on ? 'ok' : s.connected || adbUp ? 'warn' : 'bad', s.connected ? (s.is_on ? 'TV bật' : 'Màn hình tắt') : adbUp ? 'Phím qua ADB' : 'Mất kết nối',
    s.connected ? (s.is_on ? 'TV: đang bật' : 'TV: màn hình tắt') : adbUp ? 'Nút bấm: đi qua ADB' : 'TV: mất kết nối');
  ADB = s.adb || { state: 'unknown' };
  const [cls, txt, long] = ADB_TEXT[ADB.state] || ADB_TEXT.unknown; setChip('#chipAdb', cls, txt, long);
  $('#chipAdb').hidden = NATIVE();                                    // no ADB on this kind of TV: nothing to report
  if (NATIVE() && !s.connected) setChip('#chipTv', s.asking ? 'warn' : 'bad', s.asking ? 'Chờ TV cho phép' : s.paired === false ? 'Chưa ghép' : 'Mất kết nối');
  const tvName = s.name || (s.device && s.device.model) || (s.host ? 'TV ' + s.host : '');
  if (CORE === 'pc') setPicking(!s.host || wantPick, tvName);
  if (s.version) $('#ver').textContent = (s.app_name || 'TV Remote') + ' ' + s.version + (s.released ? ' · ' + s.released : '');
  document.title = tvName && !picking ? tvName + ' · Remote' : 'TV Remote';
  $('#name').textContent = picking ? 'TV Remote' : tvName;
  $('#info').textContent = picking ? tr('Chọn TV để điều khiển') : [CORE === 'pc' ? s.host : '', appName(s.app), s.asking ? 'nhìn lên TV: chọn Cho phép' : s.paired === false ? 'chưa ghép remote (tab Thiết lập)' : s.error ? 'nút remote chưa kết nối được (tab Thiết lập)' : ''].filter(Boolean).join(' · ') || 'đã kết nối';
  if (justPicked && s.host === justPicked) {                          // straight to the remote when the TV is ready, to the first-time steps when it is not
    const ready = s.connected || (s.adb && s.adb.state === 'ready') || s.asking;   // LG / Samsung asking: the remote tab says what to do
    if (ready || Date.now() - pickedAt > 6000) { justPicked = null; showTab(ready ? 'remote' : 'tv'); }   // a TV takes a few seconds to answer
    else { setTimeout(refresh, 1000); if (s.name) $('#conname').textContent = s.name; }
  }
  if (!justPicked && $('#connecting').open) $('#connecting').close();
  if (s.host !== curHost) { const first = curHost === null; curHost = s.host; if (!first) tvChanged(); }
  if (s.volume && Date.now() > volHold) {
    $('#volv').textContent = s.volume.level;
    $('#r-mute').classList.toggle('on', !!s.volume.muted); $('#r-mute').title = s.volume.muted ? 'Đang tắt tiếng, bấm để bật' : 'Tắt tiếng';
  }
  applyAdb(); allowAlert(); checkAsk();
  if ($('#assist').open && (ADB.state === 'ready' || ADB.state === 'unauthorized')) {   // the TV has opened its ADB port: nothing left to help with
    $('#assist').close(); toast(ADB.state === 'ready' ? 'ADB đã kết nối' : 'ADB đã bật: nhìn lên TV, chọn “Luôn cho phép”');
  }
}

// ---------- the kind of TV: Android TV, LG or Samsung (Remote.kt: Kind) ----------
// An LG or Samsung TV is driven through its own remote link and has no ADB: what needs a capability it lacks is marked
// data-needs="cap ..." and hidden (cards), greyed (buttons, keys it has no name for: a press says why) or, for a tab, kept
// greyed with a card of what it does on an Android TV ([data-kindintro], KIND_INTRO): a feature to find out about, not a gap.
let TV_KIND = 'android', CAPS = null, kindSig = '';
const NATIVE = () => TV_KIND !== 'android';
const can = (cap) => !CAPS || CAPS.has(cap);
const KIND_NAME = { android: 'Android TV', lg: 'LG', samsung: 'Samsung' };
const kindWhy = () => 'Chỉ có trên Android TV / Google TV';
function unsupported(e, off) {
  if (e.matches('[data-tab]')) { e.toggleAttribute('data-kind-tab', off); return; }
  if (e.matches('input, select, fieldset')) { e.disabled = off; return; }
  if (!e.matches('button')) { e.classList.toggle('kind-off', off); return; }
  if (off && !('kindOff' in e.dataset)) { e.dataset.kindOff = e.title; e.title = kindWhy(); e.setAttribute('aria-disabled', 'true'); }
  else if (!off && 'kindOff' in e.dataset) { e.title = e.dataset.kindOff; if (!e.title) e.removeAttribute('title'); delete e.dataset.kindOff; e.removeAttribute('aria-disabled'); }
}
// a greyed button still answers a press, with why: not disabled, so the press reaches here before its own handlers
for (const ev of ['pointerdown', 'click']) document.addEventListener(ev, (e) => {
  if (!e.target.closest?.('[data-kind-off]')) return;
  e.preventDefault(); e.stopImmediatePropagation();
  if (ev === 'click') toast(kindWhy());
}, true);
const KIND_INTRO = {
  screen: ['🖥️ Màn hình TV', ['Xem và chạm màn hình TV trên máy này', '🎧 Nghe tiếng TV', '✏️ Vẽ, 🖼️ ảnh, 💬 lời nhắn lên TV', '▶️ Đang phát, 📋 clipboard của TV']],
  apps: ['📱 Ứng dụng', ['Mọi app đã cài trên TV', 'Mở, buộc dừng, tắt hoặc gỡ app', 'Xóa dữ liệu app']],
  files: ['📁 Tệp · Cài', ['Duyệt tệp trên TV, gửi lên, tải về', 'Tải file từ link thẳng về TV', '📦 Cài APK từ máy này']],
  health: ['🩺 Sức khỏe', ['Bộ nhớ, RAM, tải hệ thống', 'Mạng và phần cứng của TV', 'Giải phóng RAM bằng một nút']],
  rules: ['⏰ Tự động trên TV', ['😴 Hẹn giờ tắt TV', '🌙 Giờ đi ngủ', '⏳ Giới hạn app mỗi ngày', '🔊 Giới hạn âm lượng theo giờ']],
};
function kindIntro(key) {
  const [title, items] = KIND_INTRO[key];
  return el('section', { className: 'card kintro' },
    el('div', { className: 'card-head' }, el('h2', {}, title, key === 'screen' ? el('span', { className: 'badge', textContent: 'thử nghiệm' }) : ''),
      el('span', { className: 'badge', textContent: kindWhy() })),
    el('ul', {}, ...items.map((t) => el('li', { textContent: t }))));
}
// What the TVs said and the errors shown (WireLog.kt): what a real TV did is what tells a bug apart. Copied whole (the GitHub
// link holds only its end) and a new issue opened with it.
async function sendWireLog() {
  let text;
  try { const r = await fetch('/api/wirelog'); if (!r.ok) throw 0; text = await r.text(); } catch { toast('Không đọc được nhật ký'); return; }
  let ok = false;
  try { await navigator.clipboard.writeText(text); ok = true; } catch {}
  if (!ok) { const a = el('textarea', { value: text }); document.body.append(a); a.select(); try { ok = document.execCommand('copy'); } catch {} a.remove(); }
  const head = text.split('\n\n')[0], tail = text.slice(-5000);
  const body = '<!-- ' + tr('Mô tả: bấm gì, TV làm gì') + ' -->\n\n```\n' + (tail.length < text.length ? head + '\n…\n' + tail.slice(tail.indexOf('\n') + 1) : text) + '\n```';
  window.open('https://github.com/HoangTran0410/tv-remote/issues/new?title=' + encodeURIComponent(head.split('\n')[0]) + '&body=' + encodeURIComponent(body), '_blank');
  toast(ok ? 'Đã chép nhật ký' : 'Không chép được nhật ký');
}
function applyKind(s) {
  const kind = s.kind || 'android', sig = kind + '|' + (s.caps || []).join() + '|' + (s.keys || []).join();
  if (sig === kindSig) return;
  kindSig = sig; TV_KIND = kind; CAPS = s.caps ? new Set(s.caps) : null;
  document.documentElement.dataset.kind = kind;
  const keys = kind !== 'android' && s.keys ? new Set(s.keys) : null;
  document.querySelectorAll('[data-needs]').forEach((e) => unsupported(e, !e.dataset.needs.split(' ').every(can)));
  document.querySelectorAll('[data-k]').forEach((b) => unsupported(b, !!keys && !keys.has(b.dataset.k)));
  document.querySelectorAll('[data-kindintro]').forEach((b) => { b.hidden = !NATIVE(); b.replaceChildren(...NATIVE() ? [kindIntro(b.dataset.kindintro)] : []); });
  if (currentTab) showTab(currentTab);                                // a tab now greyed shows its card, and back
}

// ---------- ADB gating + banners ----------
// The page from the TV works through the TV Setup app's own ADB session: without it nothing works, and there is no other machine to blame.
const BANNERS_TV = {
  closed: ['bad', 'App trên TV chưa nối được ADB', 'Các nút chưa hoạt động. Trên TV: bật “Gỡ lỗi ADB” (hoặc “Gỡ lỗi USB”) trong Tùy chọn nhà phát triển, rồi mở app TV Setup.'],
  unauthorized: ['warn', 'TV đang hỏi cho phép gỡ lỗi', 'Nhìn lên TV: tích “Luôn cho phép” rồi chọn OK. Trang này tự chạy tiếp.'],
  unknown: ['warn', 'Đang kết nối ADB…', ''],
};
BANNERS_TV.error = BANNERS_TV.offline = BANNERS_TV.closed;
const BANNERS = {
  closed: ['bad', 'Cổng ADB đang đóng', 'TV đang tắt/standby sâu, vừa khởi động lại, hoặc chưa bật “Gỡ lỗi ADB”. Remote vẫn dùng được nếu TV còn mạng.'],
  unauthorized: ['warn', 'TV chưa cho phép máy này', 'Nhìn lên TV, chọn “Luôn cho phép” ở hộp thoại gỡ lỗi USB rồi bấm “Thử kết nối lại”.'],
  offline: ['warn', 'ADB đang offline', 'Thiết bị có trong danh sách nhưng không trả lời. Thử kết nối lại, hoặc tắt/bật “Gỡ lỗi ADB” trên TV.'],
  error: ['bad', 'Không kết nối được ADB', 'Thử kết nối lại. Nếu vẫn lỗi, kiểm tra TV còn mạng và đã bật “Gỡ lỗi ADB”.'],
  unknown: ['warn', 'Đang kết nối ADB…', ''],
};
let bannerKey = '', scenesLoaded = false, setupChecked = '';         // setupChecked: the TV whose TV Setup version was looked at (auto.js)
function buildBanner() {
  if (ADB.state === 'none') return el('span');                        // LG / Samsung: no ADB to turn on
  const set = CORE === 'pc' ? BANNERS : BANNERS_TV, [cls, title, text] = set[ADB.state] || set.unknown;
  const retry = el('button', { className: 'small primary', textContent: '🔄 Thử kết nối lại', onclick: async (e) => {
    const b = e.currentTarget, label = b.textContent; b.disabled = true; b.textContent = 'Đang kết nối...';
    const r = await api('/api/adb/connect', {}); if (r) { ADB = r; bannerKey = ''; applyAdb(); refresh(); }
    b.disabled = false; b.textContent = label;
  } });
  if (ADB.state === 'unknown' || !ADB.state) {                       // still connecting: a quiet line, Retry only once it drags on
    retry.className = 'small'; retry.textContent = 'Thử lại'; retry.hidden = true;
    setTimeout(() => { retry.hidden = false; }, 8000);
    return el('div', { className: 'banner wait' }, el('span', { className: 'minispin' }), el('span', { textContent: title }), retry);
  }
  if (CORE !== 'pc') return el('div', { className: 'banner ' + cls }, el('b', { textContent: title }), el('p', { textContent: text }), el('div', { className: 'actions' }, retry));
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
    if (ready && currentTab === 'tv' && typeof tsState === 'function') { tsState(); loadTvset(); }
  }
  // a tab already shown with its data keeps it, scroll and all, under a veil that says ADB is coming back (or why not, with Retry);
  // the banner in place of the tab only when nothing was shown yet
  const key = curHost || '-';
  document.querySelectorAll('.panel').forEach((p) => {
    if (p.dataset.panel === 'remote' || p.dataset.panel === 'tv' || p.dataset.panel === 'kind') return;
    if (ready && !p.hidden) p.dataset.seen = key;
    const held = !ready && ADB.state !== 'none' && !NATIVE() && p.dataset.seen === key;
    p.classList.toggle('held', held);
    p.querySelectorAll('[data-adb-banner]').forEach((b) => { b.hidden = held; });
    let v = p.querySelector(':scope > .veil');
    if (!held) { if (v) v.remove(); return; }
    if (!v) { v = el('div', { className: 'veil' }); p.append(v); }
    if (v.dataset.k !== ADB.state) { v.dataset.k = ADB.state; v.replaceChildren(el('div', { className: 'veilcard' }, buildBanner())); }
  });
  document.querySelectorAll('[data-adb-gated]').forEach((g) => {   // data-native: works without ADB on LG / Samsung
    g.hidden = !ready && !(NATIVE() && 'native' in g.dataset) && !(g.tagName !== 'BUTTON' && g.closest('.panel.held'));
  });
  const noadb = !ready && ADB.state !== 'unknown' && !NATIVE();         // not while still connecting; LG / Samsung hide those tabs instead
  $('.tabs').classList.toggle('noadb', noadb);
  document.querySelectorAll('.tabs [data-needs-adb]').forEach((b) => { if (noadb) b.title = tr('Cần ADB: bấm vào để xem cách bật'); else b.removeAttribute('title'); });
  const typing = ready || NATIVE() && can('text');                    // typing goes through ADB, or LG's / Samsung's own keyboard link
  $('#kbd').disabled = !typing; $('#kbdoff').hidden = typing;         // no half-working box without it
  $('#screenbtn').disabled = !ready && !screenOn;
  if (!ready && screenOn) stopScreen('ADB chưa sẵn sàng, đã dừng xem màn hình.');
  if (ready && autoView) { autoView = false; startScreen(); }           // bookmark ?view=1 to start watching right away
  if (ready && !picking && setupChecked !== (curHost || '-')) checkSetup();   // a newer TV Setup: offered once per TV (auto.js)
  if (NATIVE() && STATE.connected) { if (!quickLoaded && can('app_list')) loadQuick(); if (!scenesLoaded) { scenesLoaded = true; loadScenes(); } }   // the TV's apps from its remote link
  if (ready) { if (!quickLoaded) loadQuick(); if (currentTab === 'apps' && !appsLoaded) loadApps(); if (currentTab === 'files' && !filesLoaded) loadFiles(cwd); if (!scenesLoaded) { scenesLoaded = true; loadScenes(); } if (currentTab === 'auto' && !autoLoaded) autoTab(); if ($('#setlist').open) loadSettings(); }
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
  if (yes && !await ask(tr('Cho phép thiết bị có mã này điều khiển toàn bộ TV qua ADB?') + '\n\n' + fp, { ok: 'Cho phép' })) return;
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
