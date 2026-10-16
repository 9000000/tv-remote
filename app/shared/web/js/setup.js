// optimise, language, quit, and the TV tab (saved TVs, scan, first-time setup)
// ---------- one-click TV setup (Setup.kt) ----------
async function runSetup(mode, choice) {
  if (mode === 'undo' && !await ask('Hoàn tác? Bật lại màn hình chính Google, các app đã tắt và hiệu ứng như cũ.', { ok: 'Hoàn tác' })) return;
  const btns = [$('#setupapply'), $('#setupundo')], log = $('#setuplog');
  btns.forEach((b) => { b.disabled = true; }); log.hidden = false; log.textContent = 'Đang chạy...';
  let j = await api('/api/setup', { mode, lang: LANG, ...(choice || {}) });
  for (let misses = 0; j && j.status === 'running' && misses < 10;) {          // ten failed polls in a row: give up waiting, not the TV
    await sleep(1000); const r = await api('/api/job?id=' + j.id, null, true);
    if (!r) { misses++; continue; }
    misses = 0; j = r; log.textContent = j.log || 'Đang chạy...'; log.scrollTop = log.scrollHeight;
  }
  if (j && j.status === 'running') j = null;
  if (j) { log.textContent = (j.log || '') + (j.status === 'done' ? '\n✅ Xong.' : '\n❌ Lỗi: ' + friendly(j.msg)); toast(j.status === 'done' ? 'Đã xong' : 'Lỗi: ' + friendly(j.msg)); quickLoaded = appsLoaded = false; applyAdb(); }
  else log.textContent += '\n❌ ' + tr('mất kết nối, không rõ đã xong chưa');
  btns.forEach((b) => { b.disabled = false; });
}
// "Optimize TV" asks first what to do: what this TV has, suggested apps ticked, any other app the person wants off
function optRow(name, sub, checked, data) {
  const box = el('input', { type: 'checkbox', checked }); Object.assign(box.dataset, data);
  return el('label', { className: 'optrow' }, box, el('span', {}, el('b', { textContent: name }), sub ? el('small', { textContent: sub }) : null));
}
async function chooseOptimize() {
  const body = $('#optbody'); body.replaceChildren(el('p', { className: 'muted', textContent: 'Đang xem TV có gì để tối ưu...' }));
  $('#optgo').disabled = true; $('#optdlg').showModal();
  const p = await api('/api/setup/plan');
  if (!p) { body.replaceChildren(el('p', { textContent: 'Không đọc được TV. Thử lại.' })); return; }
  const usingFl = p.home === 'me.efesser.flauncher';
  const parts = [el('h3', { textContent: 'Chung' }),
    optRow('Tắt hiệu ứng chuyển cảnh', p.anim ? 'Mở app, chuyển màn hình nhanh hơn' : 'Đang tắt sẵn', true, { what: 'anim' }),
    optRow(usingFl ? 'Màn hình chính FLauncher (đang dùng)' : 'Dùng FLauncher làm màn hình chính', 'Chỉ lưới app, nhẹ, không quảng cáo; màn hình chính Google được tắt' + (p.flauncher ? '' : ' · tải 10 MB'), true, { what: 'home' })];
  if (p.suggest.length) parts.push(el('h3', { textContent: 'Tắt app đề xuất' }), ...p.suggest.map((a) => optRow(a.label, a.pkg, true, { pkg: a.pkg })));
  if (p.apps.length) {
    const list = el('div', { className: 'optbox' }, ...p.apps.map((a) => optRow(a.label, a.pkg, false, { pkg: a.pkg })));
    const filter = el('input', { className: 'filter', type: 'search', placeholder: 'Lọc theo tên...', oninput: () => {
      const q = filter.value.trim().toLowerCase(); list.querySelectorAll('.optrow').forEach((r) => { r.hidden = q && !r.textContent.toLowerCase().includes(q); });
    } });
    parts.push(el('h3', { textContent: 'Tắt thêm app khác' }), el('p', { className: 'note', textContent: 'Tắt là tạm ngưng, không gỡ; app biến khỏi TV cho tới khi Hoàn tác.' }), p.apps.length > 7 ? filter : null, list);
  }
  body.replaceChildren(...parts.filter(Boolean)); $('#optgo').disabled = false;
}
$('#optgo').addEventListener('click', () => {
  const boxes = [...$('#optbody').querySelectorAll('input[type=checkbox]')], on = (w) => boxes.some((b) => b.dataset.what === w && b.checked);
  const choice = { anim: on('anim'), home: on('home'), disable: boxes.filter((b) => b.dataset.pkg && b.checked).map((b) => b.dataset.pkg) };
  $('#optdlg').close(); runSetup('apply', choice);
});
$('#optclose').addEventListener('click', () => $('#optdlg').close());
$('#optcancel').addEventListener('click', () => $('#optdlg').close());
$('#setupapply').addEventListener('click', () => chooseOptimize());
$('#setupundo').addEventListener('click', () => runSetup('undo'));

// ---------- settings: theme (js/theme.js) and language (Vietnamese is the source, i18n.js renders English) ----------
function showPicks() {
  $('#themepick').querySelectorAll('button').forEach((b) => b.classList.toggle('on', b.dataset.v === THEME));
  $('#langpick').querySelectorAll('button').forEach((b) => b.classList.toggle('on', b.dataset.v === LANG));
}
$('#themepick').addEventListener('click', (e) => { const b = e.target.closest('button'); if (b) { setTheme(b.dataset.v); showPicks(); } });
$('#langpick').addEventListener('click', (e) => { const b = e.target.closest('button'); if (b && b.dataset.v !== LANG) setLang(b.dataset.v); });
showPicks();
if (window.LANG_KEEP && window.LANG_KEEP.settings) setTimeout(() => $('#aboutbtn').click(), 0);   // it reloaded for the new language: back where it was

// ---------- about ----------
$('#aboutbtn').addEventListener('click', async () => {
  showPicks(); $('#about').showModal();
  const r = await fetch('/api/about').then((x) => x.json()).catch(() => null);
  if (r && r.version) $('#aboutver').textContent = r.version + (r.released ? ' · ' + r.released : '');
  // scan with a phone: the Android app, and (when the TV Setup app is serving it) this TV's own remote page
  $('#qrs').replaceChildren(...[qrBlock('📱 App Android', 'Quét bằng điện thoại để tải app.', APP_APK),
    r && r.tv_web ? qrBlock('🌐 Remote trên điện thoại', 'Quét khi điện thoại chung mạng với TV.', r.tv_web) : null].filter(Boolean));
});
const APP_APK = 'https://github.com/HoangTran0410/tv-remote/releases/latest/download/tv-remote.apk';
// a QR with its title and line; the link under it unless [hide] (an invite is for scanning, not for reading out)
const qrBlock = (title, note, link, hide) => { const s = qrSvg(link, 170); return s ? el('div', { className: 'qr' }, s, el('b', { textContent: title }), el('span', { className: 'muted', textContent: note }),
  hide ? null : el('br'), hide ? null : el('a', { href: link, target: '_blank', rel: 'noopener', textContent: link.replace(/^https?:\/\//, '').replace(/^github\.com\/.*\//, '') })) : null; };   // the file's name: the whole release link is wider than the box

// ---------- share (⋯ More keys → Chia sẻ): a QR that opens this TV's web page already paired, and one for the phone app ----------
// The invite is one-time and lasts ten minutes (Invite.kt); without TV Setup (or an older one) only the app's QR is shown.
// It carries what the one who scans may do (Scope.kt in TV Setup); picking another makes a new QR, as one invite is out at a time.
const SCOPE_WORDS = {
  full: ['🛡️ Toàn quyền', 'Như chủ TV: cả Quản lý, cài app, tệp, chia sẻ'],
  remote: ['🎮 Chỉ remote', 'Bấm phím, gõ chữ, xem hình, mở app; không vào được Quản lý'],
  view: ['👁️ Chỉ xem', 'Xem hình và bài đang phát, không bấm được gì'],
};
const scopeChips = (now, fn) => UI.chips(Object.keys(SCOPE_WORDS).map((k) => ({ label: SCOPE_WORDS[k][0], title: SCOPE_WORDS[k][1], on: k === now, fn: () => fn(k) })));
// two tabs: the TV (a one-time QR, with what the scanner may do) and the app (the APK's QR), as on the phone (Share.kt)
async function shareDialog() {
  let scope = 'remote';                                               // what a guest on the sofa needs; the owner can pick more
  const r = await api('/api/share', { scope }) || {};
  const qr = el('div', { className: 'qrs' }), pick = el('div');
  const warn = { tailscale: 'Máy này đang nối TV qua Tailscale: điện thoại quét phải ở cùng Wi-Fi với TV.', tailscale_only: 'TV không có địa chỉ Wi-Fi: điện thoại quét cần bật Tailscale.' }[r.warn];
  const tv = el('div', { className: 'sharetab' },
    warn ? UI.warn(warn) : null,
    r.setup === 'missing' ? UI.note('TV chưa có TV Setup: chỉ chia sẻ được app.') : null,
    r.setup === 'old' ? UI.note('Cập nhật TV Setup để chia sẻ remote.') : null,
    r.setup === 'old' ? UI.actions(UI.btn('⬆️ Cập nhật TV Setup', () => { d.close(); return updateSetup(); }, 'small')) : null,
    r.url ? UI.note('Người quét được làm gì') : null, r.url ? pick : null, qr);
  const app = el('div', { className: 'sharetab', hidden: true }, el('div', { className: 'qrs' }, qrBlock('📱 App Android', 'Quét để tải app.', APP_APK)));
  const tabs = el('div', { className: 'seg' });
  const show = (k) => {
    tabs.replaceChildren(...[['tv', '📺 Chia sẻ TV'], ['app', '📱 Chia sẻ app']].map(([id, label]) =>
      el('button', { className: id === k ? 'on' : '', textContent: label, onclick: () => show(id) })));
    tv.hidden = k !== 'tv'; app.hidden = k !== 'app';
  };
  const d = el('dialog', { className: 'ask share' }, el('div', { className: 'card' },
    UI.head('📲 Chia sẻ', el('button', { className: 'small iconbtn', textContent: '✕', title: 'Đóng', 'aria-label': 'Đóng', onclick: () => d.close() })),
    tabs, tv, app));
  const wifi = r.warn !== 'tailscale_only';                           // the link is the TV's Wi-Fi address: say so, unless only Tailscale reaches it (warned above)
  const draw = (url) => qr.replaceChildren(...[url ? qrBlock(scope === 'view' ? '🌐 Xem TV' : '🌐 Mở remote', scope === 'view'
    ? (wifi ? 'Quét để xem TV này, không cần mã. Điện thoại quét phải cùng Wi-Fi với TV. Dùng 1 lần, trong 10 phút.' : 'Quét để xem TV này, không cần mã. Dùng 1 lần, trong 10 phút.')
    : (wifi ? 'Quét để điều khiển TV này, không cần mã. Điện thoại quét phải cùng Wi-Fi với TV. Dùng 1 lần, trong 10 phút.' : 'Quét để điều khiển TV này, không cần mã. Dùng 1 lần, trong 10 phút.'), url, true) : null].filter(Boolean));
  const choose = async (k) => {
    if (k === scope) return; scope = k; pick.replaceChildren(scopeChips(scope, choose));
    const n = await api('/api/share', { scope }); draw(n && n.url);   // on a failure no QR rather than the last one under the wrong choice
  };
  pick.replaceChildren(scopeChips(scope, choose)); draw(r.url); show('tv');
  d.addEventListener('close', () => d.remove());
  document.body.append(d); d.showModal();
}
if (CORE === 'pc') fetch('/api/about').then((x) => x.json()).then((r) => {
  if (r.version) $('#ver').textContent = 'TV Remote ' + r.version + (r.released ? ' · ' + r.released : '');
  if (r.stale) $('#stale').hidden = false;                            // its code was updated after it started: new buttons would answer "not found"
}).catch(() => {});

// ---------- updates of the computer app (Updater.kt): it checks and downloads by itself; this shows it and starts the swap ----------
let updLater = false, updPoll = null;
function showUpdate(u) {
  const b = $('#update');
  b.hidden = !u || !u.version || (updLater && u.state !== 'installing');
  if (b.hidden) return;
  $('#updtitle').textContent = '🆕 Có bản mới ' + u.version + ' (đang dùng ' + u.current + ')';
  $('#updnote').textContent = u.state === 'downloading' ? 'Đang tải bản mới ' + u.pct + '%'
    : u.state === 'installing' ? 'Đang cập nhật: TV Remote đóng lại rồi tự mở bản mới.'
    : u.state === 'error' ? 'Chưa tải được bản mới (' + friendly(u.error) + '). Bấm Cập nhật để thử lại.'
    : !u.auto ? 'App nằm ở thư mục không tự thay được: tải bản mới, giải nén, rồi thay cho bản đang dùng.'
    : 'Bấm Cập nhật: TV Remote đóng lại rồi tự mở bản mới, mất vài giây. Để sau thì bản mới tự cài khi đóng app.';
  $('#updgo').textContent = u.auto ? '⬆️ Cập nhật' : '⬇️ Tải bản mới';
  $('#updacts').hidden = u.state === 'installing' || u.state === 'downloading' && !updPoll;
  clearTimeout(updPoll); updPoll = null;
  if (u.state === 'downloading' || u.state === 'installing') updPoll = setTimeout(pollUpdate, 1000);
}
const pollUpdate = () => fetch('/api/update').then((x) => x.json()).then(showUpdate).catch(() => { updPoll = setTimeout(pollUpdate, 2000); });   // gone while it swaps
if (CORE === 'pc') { pollUpdate(); setInterval(() => { if (!updPoll) pollUpdate(); }, 30 * 60000); }
$('#updlater').addEventListener('click', () => { updLater = true; $('#update').hidden = true; });
$('#updgo').addEventListener('click', async () => {
  const u = await api('/api/update'); if (!u) return;
  if (!u.auto) { window.open(u.url, '_blank'); return; }
  const notes = (u.notes || '').replace(/\*\*/g, '').replace(/\r/g, '');
  if (!await ask('Cập nhật lên ' + u.version + '? TV Remote sẽ đóng lại rồi tự mở bản mới.' + (notes ? '\n\n' + (notes.length > 900 ? notes.slice(0, 900) + '...' : notes) : ''), { ok: 'Cập nhật' })) return;
  const r = await api('/api/update', { do: 'install' }); if (r) showUpdate(r);
});
$('#updcheck').addEventListener('click', async () => {
  $('#updcheckmsg').textContent = 'Đang kiểm tra...';
  const u = await api('/api/update', { do: 'check' });
  if (!u) { $('#updcheckmsg').textContent = ''; return; }
  updLater = false; showUpdate(u);
  $('#updcheckmsg').textContent = !u.packaged ? 'Bản chạy từ mã nguồn: cập nhật bằng git pull.' : u.error ? 'Không kiểm tra được: ' + friendly(u.error)
    : u.version ? 'Có bản mới ' + u.version + ': xem thanh trên cùng trang.' : 'Đang dùng bản mới nhất (' + u.current + ').';
});
$('#aboutclose').addEventListener('click', () => $('#about').close());
$('#sendlog').addEventListener('click', sendWireLog);
$('#about').addEventListener('click', (e) => { if (e.target === $('#about')) $('#about').close(); });   // click outside the box

// ---------- stop the local server from the page ----------
let stopped = false;
$('#quit').addEventListener('click', async () => {
  if (!await ask('Tắt TV Remote trên máy tính này?\nTV không bị ảnh hưởng. Muốn dùng lại thì mở lại app TV Remote.', { ok: 'Tắt' })) return;
  $('#about').close();
  if (screenOn) stopScreen();
  if (!await api('/api/shutdown', {})) return;
  stopped = true;
  document.querySelector('main').replaceChildren(el('section', { className: 'card stopped' },
    el('h1', { textContent: 'Đã tắt' }), el('p', { className: 'muted', textContent: 'TV Remote trên máy tính này đã dừng. Mở lại app TV Remote để dùng tiếp. Có thể đóng trang này.' })));
});

// ---------- TVs: saved list, discovery, first-time setup ----------
function tvChanged() {                                                // another TV: forget everything cached about the old one
  if (screenOn) stopScreen();
  if (listenOn) stopListen();
  quickLoaded = appsLoaded = filesLoaded = scenesLoaded = autoLoaded = sceneNamesAsked = false; META.clear(); cwd = '/sdcard/Download'; bannerKey = ''; RULES = null; SCENES = []; setRows = []; $('#setlistbody').replaceChildren(); renderScenes(); setupChecked = ''; $('#setupupd').hidden = true;
  $('#quick').replaceChildren(el('div', { className: 'muted', textContent: 'Danh sách app hiện khi ADB sẵn sàng.' }));
  $('#appslist').replaceChildren(); $('#fileslist').replaceChildren();
  if (currentTab === 'tv') loadSteps();
  if (picking) loadTvs();
}
function connecting(name, host) {                                   // covers the remote until the TV answers (see refresh)
  $('#conname').textContent = name; $('#constep').textContent = 'Đang tìm TV ở ' + host + '...';
  if (!$('#connecting').open) $('#connecting').showModal();
}
$('#concancel').addEventListener('click', () => { justPicked = null; $('#connecting').close(); wantPick = true; refresh(); });
$('#connecting').addEventListener('cancel', (e) => { e.preventDefault(); $('#concancel').click(); });   // Esc: the same as Cancel
async function selectTv(host, name, kind) {
  const r = await api('/api/tv/select', kind ? { host, kind } : { host }); if (!r) return;
  connecting(name || host, host); wantPick = false; cameFromTv = false; justPicked = host; pickedAt = Date.now(); showTab('remote'); await sleep(1200); await refresh(); loadSteps();
}
$('#switchtv').addEventListener('click', () => { wantPick = true; cameFromTv = true; refresh(); });
$('#pickback').addEventListener('click', () => { wantPick = false; cameFromTv = false; refresh(); });
const openTv = (host, current, name) => { if (host === current) { wantPick = false; cameFromTv = false; justPicked = host; pickedAt = Date.now(); refresh(); } else selectTv(host, name); };
let savedTvs = [];                                                     // the saved list, for the scan to know a TV's other address
async function loadTvs() {
  const r = await api('/api/tvs', null, true); if (!r) return;
  savedTvs = r.tvs;
  const rows = r.tvs.map((t) => el('div', { className: 'item', onclick: (e) => { if (!e.target.closest('button')) openTv(t.host, r.current, t.name); } },
    el('div', { className: 'name' }, el('span', { textContent: t.name || t.host }), t.host === r.current && cameFromTv ? el('span', { className: 'badge ok', textContent: 'đang dùng' }) : null,
      el('small', { textContent: t.host + (t.lan && t.lan !== t.host ? ' · trong nhà ' + t.lan : '') })),
    el('div', { className: 'acts' },
      setLabel(el('button', { className: 'small primary', onclick: () => openTv(t.host, r.current) }), '▶', 'Điều khiển'),
      setLabel(el('button', { className: 'small danger', onclick: async () => { if (await ask('Xóa "' + (t.name || t.host) + '" khỏi danh sách? (Không đổi gì trên TV)', { ok: 'Xóa', danger: true })) { await api('/api/tv/remove', { host: t.host }); await refresh(); loadTvs(); loadSteps(); } } }), '🗑', 'Xóa', true))));
  $('#tvlist').replaceChildren(...rows);
  $('#savedcard').hidden = !rows.length; $('#pickcols').classList.toggle('solo', !rows.length);   // nothing saved: no empty box, just the search
  fleetCheck(rows.length);
}
function scanRow(f) {
  const native = f.kind && f.kind !== 'android';                      // LG / Samsung: found by SSDP, no ADB to report
  const row = el('div', { className: 'item', onclick: (e) => { if (!e.target.closest('button')) selectTv(f.ip, f.name, f.kind); } },
    el('div', { className: 'name' }, el('span', { textContent: f.name || f.ip }),
      el('div', { className: 'badges' },                              // a line of their own: wrapped beside a long name they looked lopsided
      el('span', { className: 'badge' + (f.remote ? ' ok' : ''), textContent: native ? 'TV ' + KIND_NAME[f.kind] : f.remote ? 'Android TV' : 'chỉ có ADB' }),
      native ? el('span', { className: 'badge', textContent: 'thử nghiệm' }) : null,
      native ? null : f.adb ? el('span', { className: 'badge ok', textContent: 'ADB đã bật' }) : el('span', { className: 'badge', textContent: 'ADB chưa bật' })),
      el('small', { textContent: f.ip + (f.via === 'tailscale' ? ' · qua Tailscale' : ' · cùng mạng') + (f.tailscale ? ' · Tailscale ' + f.tailscale : '') })),
    el('div', { className: 'acts' }, setLabel(el('button', { className: 'small primary', onclick: () => selectTv(f.ip, f.name, f.kind) }), '▶', 'Điều khiển')));
  row.dataset.ip = f.ip; return row;
}
// The scan runs on the computer; asked again every 400 ms, each TV shows up as soon as it answers instead of after the
// whole network (and a second pass when the first finds nothing)
$('#scan').addEventListener('click', async () => {
  const b = $('#scan'), list = $('#scanlist'); b.disabled = true; b.textContent = '⏳ Đang quét...'; list.replaceChildren(); $('#scaninfo').textContent = '';
  let r = await api('/api/discover?new=1');
  while (r) {
    for (const f of r.found) {                                        // one row per TV: a saved one is known by both its addresses
      const tv = savedTvs.find((t) => t.host === f.ip || t.lan === f.ip || (f.tailscale && t.host === f.tailscale));
      const key = tv ? tv.host : f.ip;
      if (!list.querySelector(`[data-tv="${key}"]`)) { const row = scanRow(f); row.dataset.tv = key; list.append(row); }
    }
    if (r.done) break;
    await sleep(400);
    r = await api('/api/discover', null, true);
  }
  b.disabled = false; b.textContent = '🔍 Quét mạng'; if (!r) return;
  $('#scaninfo').textContent = r.found.length ? '' : 'Không thấy TV nào. TV có bật và chung Wi-Fi không?';
});
$('#ipf').addEventListener('submit', (e) => { e.preventDefault(); const ip = $('#ipin').value.trim(); if (ip) selectTv(ip); });
$('#setuprefresh').addEventListener('click', () => loadSteps());

// ---------- turning ADB on for a TV that has it off, with the remote buttons alone (the phone app's assistant) ----------
const HOMES = [['Projectivy Launcher', 'com.spocky.projengmenu'], ['ATV Launcher', 'ca.dstudio.atvlauncher.free'], ['FLauncher', 'me.efesser.flauncher']];
let assistGen = 0;
function assistShow(title, ...kids) {
  assistGen++; $('#assisttitle').textContent = title; $('#assistbody').replaceChildren(...kids.filter(Boolean));
  if (!$('#assist').open) $('#assist').showModal();
}
const choice = (title, sub, fn, cls = '') => el('button', { className: 'choice ' + cls, onclick: fn }, el('b', { textContent: title }), sub ? el('small', { textContent: sub }) : null);
const say = (text) => el('p', { textContent: text });
const row = (...kids) => el('div', { className: 'assistrow' }, ...kids.filter(Boolean));
const assist = (do_, extra) => api('/api/assist', { do: do_, ...extra });
$('#assistclose').addEventListener('click', () => $('#assist').close());
$('#assist').addEventListener('close', () => { assistGen++; api('/api/assist', { do: 'stop' }, true); });   // closed any way (button, Esc): nothing keeps running unseen

function adbHelp() {
  if (!STATE.connected) {                                             // everything here goes through the remote buttons
    assistShow('Bật ADB cho TV này', say(STATE.paired === false ? 'Trước hết cần ghép nút remote với TV (TV hiện mã 6 ký tự, gõ vào đây). Sau đó trang này tự làm giúp phần còn lại.'
      : 'Chưa kết nối được nút remote tới TV (cổng 6466): TV đang bật và cùng mạng với máy này chứ?'),
      row(act('🔗 Tới bước ghép remote', async () => { $('#assist').close(); showTab('tv'); }, 'primary')), say('Hoặc tự làm bằng remote của TV:'), say(ADB_STEPS), say(DL_TIP));
    return;
  }
  assistShow('Bật ADB cho TV này',
    choice('⚡ Tự động: cài TV Setup lên TV', 'Trang này cho TV tự tải và mở app TV Setup; app đó mở thẳng Tùy chọn nhà phát triển. Bạn chỉ xác nhận trên TV.', installSetup, 'primary'),
    choice('🔧 Tự làm: mở Cài đặt TV', 'Thử mọi cách mở Cài đặt, rồi chỉ các bước bật Gỡ lỗi ADB.', findSettings),
    choice('🏠 Cài màn hình chính khác', 'Khi TV giấu Cài đặt: mở qua một launcher từ CH Play.', otherHome),
    choice('🛒 Mở CH Play trên TV', '', () => assist('open', { what: 'https://play.google.com/store/apps' })),
    choice('📦 Mở app theo tên gói', 'Đã cài thì mở app, chưa cài thì mở trang CH Play của nó.', async () => { const p = await ask('Tên gói hoặc đường dẫn', { value: 'com.example.app' }); if (p && p.trim()) assist('open', { what: p.trim() }); }),
    say(DL_TIP),
    say('Khi TV bật ADB xong, trang này tự nhận ra và kết nối. Các nút mũi tên, OK ở tab Remote vẫn dùng được trong lúc làm.'));
}
const DL_TIP = 'Cài TV Setup bằng tay: trên TV cài app Downloader từ CH Play, mở nó, nhập mã 2397346 rồi Go. TV Setup tự mở chỗ bật Gỡ lỗi ADB.';
const ADB_STEPS = '1. Trong Cài đặt: Tùy chọn thiết bị (hoặc Hệ thống) → Giới thiệu → bấm OK 7 lần vào “Bản dựng”.\n2. Quay lại, vào Tùy chọn nhà phát triển → bật “Gỡ lỗi USB” (hoặc “Gỡ lỗi ADB”).\n3. Trên TV tích “Luôn cho phép” rồi chọn OK.\n\nNếu TV đòi “mật khẩu ADB” khi bấm 7 lần: dùng cách “Tự động: cài TV Setup lên TV”.';
function adbSteps() {
  assistShow('Bật Gỡ lỗi ADB trên TV', say('Dùng các nút mũi tên và OK ở tab Remote, hoặc remote của TV:'), say(ADB_STEPS),
    row(act('🔌 Đã bật, kết nối', async () => { const r = await api('/api/adb/connect', {}); if (r) { ADB = r; bannerKey = ''; await refresh(); } }, 'primary'), act('← Các cách khác', async () => adbHelp())));
}
function lookOnHome() {
  assistShow('TV không mở Cài đặt theo lệnh', say('Tìm trên màn hình chính của TV, dùng các nút mũi tên ở tab Remote: biểu tượng hồ sơ/tài khoản hoặc bánh răng (thường ở góc trên) → Cài đặt → “Cài đặt thiết bị”.\n\nBox FPT Play: mở bảng Quản lý (hồ sơ) → Cài đặt → Cài đặt thiết bị.'),
    row(act('✓ Thấy rồi', async () => adbSteps(), 'primary'), act('Không có · Cài màn hình chính khác', async () => otherHome()), act('← Các cách khác', async () => adbHelp())));
}
async function findSettings() {
  assistShow('Mở Cài đặt TV', say('Đang thử mở Cài đặt trên TV...')); const gen = assistGen;
  const r = await assist('settings'); if (gen !== assistGen) return;
  if (!r) return adbHelp();
  if (r.ok) return adbSteps();
  if (r.blind) return assistShow('Mở Cài đặt TV', say('TV có mở màn hình Cài đặt không?'), row(act('Có', async () => adbSteps(), 'primary'), act('Không', async () => lookOnHome())));
  lookOnHome();
}
function otherHome() {
  assistShow('Mở Cài đặt qua màn hình chính khác', ...HOMES.map(([name, pkg]) => choice(name, STATE.app === pkg ? 'Đang mở trên TV' : 'Đã cài thì mở, chưa cài thì mở trang CH Play.', () => {
    assist('open', { what: pkg });
    assistShow(name, say('Nhìn lên TV.\n\n• Thấy trang CH Play: chọn Cài đặt, xong bấm “Mở lại” bên dưới.\n• CH Play báo không tương thích hoặc không tìm thấy: bấm “Chọn cái khác”.\n• Thấy màn hình chính mới: tìm biểu tượng bánh răng → Cài đặt Android, rồi bấm “Các bước bật ADB”.\n\nCH Play cần TV đã đăng nhập tài khoản Google.'),
      row(act('Các bước bật ADB', async () => adbSteps(), 'primary'), act('↻ Mở lại', async () => { await assist('open', { what: pkg }); }), act('Chọn cái khác', async () => otherHome())));
  })), row(act('← Các cách khác', async () => adbHelp())));
}
async function installSetup() {
  if (!await assist('setup')) return;
  const msg = say('Đang bắt đầu...'); msg.classList.add('setupmsg');   // the words change as it goes; the buttons below must not jump
  const done = el('button', { className: 'small', textContent: '✓ Đã cài xong', onclick: async () => { setDone(true); await assist('installed'); } });
  const setDone = (busy) => { done.disabled = busy; done.textContent = tr(busy ? '⏳ Đang kiểm tra...' : '✓ Đã cài xong'); };
  assistShow('Cài TV Setup lên TV', msg, row(done, act('■ Dừng', async () => { await assist('stop'); await sleep(400); adbHelp(); })));
  const gen = assistGen; let misses = 0;
  for (;;) {
    await sleep(1000); if (gen !== assistGen) return;
    const r = await api('/api/assist', null, true); if (gen !== assistGen) return;
    misses = r ? 0 : misses + 1;                                      // ten in a row: say so, instead of waiting for ever
    if (misses >= 10) return assistShow('Cài TV Setup lên TV', say('Không gọi được server'), row(act('↻ Thử lại', async () => installSetup(), 'primary'), act('← Các cách khác', async () => adbHelp())));
    if (!r || r.status === 'running') { if (r) { if (r.msg) msg.textContent = tr(r.msg); setDone(!!r.checking); } continue; }
    if (r.status === 'look') return assistShow('Nhìn lên TV', say('TV đã nhận đường dẫn và tải TV Setup.\n\n1. TV hỏi cài đặt: chọn Cài đặt (Install). Nếu TV hỏi quyền cài từ nguồn này: bật cho app đang tải, bấm Quay lại, rồi chọn Install.\n2. Cài xong chọn Mở (hoặc bấm “Mở TV Setup” bên dưới).\n3. TV Setup tự mở Tùy chọn nhà phát triển: bật “Gỡ lỗi USB”.\n4. TV hỏi cho phép gỡ lỗi: tích “Luôn cho phép”, chọn OK. Trang này tự kết nối.'),
      row(act('📺 Mở TV Setup', async () => { await assist('open', { what: 'dev.tvremote.setup' }); }, 'primary'), act('↻ Làm lại', async () => installSetup()), act('← Các cách khác', async () => adbHelp())));
    return assistShow('Cài TV Setup lên TV', say(tr(r.msg) || 'Không làm được.'), row(act('↻ Thử lại', async () => installSetup(), 'primary'), act('← Các cách khác', async () => adbHelp())));
  }
}

// ---------- Tailscale: one button installs it on the TV, switches it on and fetches its sign-in link (Tailscale.kt) ----------
// Nobody types on the TV: the link opens in a browser that is signed in to Tailscale, where one click adds the TV.
const TS_PITCH = 'Trang này cài Tailscale lên TV, bạn chỉ cần bấm “Connect”.';
const TS_STEP = { download: 'Đang tải Tailscale bản chính thức (khoảng 100 MB)...', install: 'Đang cài Tailscale lên TV...', start: 'Đang bật Tailscale trên TV...' };
let tsGen = 0;
function tsShow(...kids) { $('#tsbody').replaceChildren(...kids.filter(Boolean)); if (!$('#ts').open) $('#ts').showModal(); }
async function tailscaleGo() {
  const gen = ++tsGen, msg = el('span', { textContent: 'Đang bắt đầu...' }), bar = el('div', { className: 'bar' }, el('i'));
  tsShow(el('p', { className: 'tswait' }, el('span', { className: 'minispin' }), msg), bar, el('p', { className: 'muted tsnote', textContent: 'Đóng được: TV vẫn làm tiếp.' }));
  let j = await api('/api/tailscale/start', {}), misses = 0;
  while (j && j.status === 'running' && misses < 10) {
    const k = Object.keys(TS_STEP).find((w) => (j.msg || '').startsWith(w)); msg.textContent = k ? TS_STEP[k] : 'Đang làm...'; bar.firstChild.style.width = (j.pct || 0) + '%';
    await sleep(1000); if (gen !== tsGen) return;
    const r = await api('/api/job?id=' + j.id, null, true); if (r) { j = r; misses = 0; } else misses++;
  }
  if (gen !== tsGen) return;
  if (!j || j.status !== 'done') return tsShow(el('p', { textContent: 'Không làm được: ' + (j && j.status === 'error' ? friendly(j.msg) : 'mất kết nối tới TV') }), row(act('↻ Thử lại', async () => tailscaleGo(), 'primary')));
  if (j.ip) return tsDone(j.ip);
  // the QR code and two short steps; what each step means in full sits in its tooltip
  const qr = qrSvg(j.url, 168), step = (n, text, tip) => el('li', { title: tip }, el('b', { textContent: n }), el('span', { textContent: text }));
  tsShow(el('div', { className: 'tslogin' },
    qr ? el('div', { className: 'tsqr', title: 'Quét bằng điện thoại đã đăng nhập Tailscale. TV cũng đang hiện đúng mã này.' }, qr, el('span', { textContent: 'Quét bằng điện thoại' })) : null,
    el('div', { className: 'tsside' },
      el('ol', { className: 'tssteps' },
        step('1', 'Đăng nhập Tailscale', 'Chưa có tài khoản thì tạo miễn phí bằng Google, Microsoft hoặc GitHub.'),
        step('2', 'Bấm “Connect”', 'Tailscale hỏi có thêm TV này vào tài khoản không.')),
      el('a', { className: 'go primary', href: j.url, target: '_blank', rel: 'noopener', textContent: 'Mở trang đăng nhập' }))),
    el('p', { className: 'tswait' }, el('span', { className: 'minispin' }), el('span', { textContent: 'Đang chờ TV vào Tailscale...' })));
  for (;;) {
    await sleep(2500); if (gen !== tsGen || !$('#ts').open) return;
    const s = await api('/api/tailscale', null, true); if (s && s.ip) return tsDone(s.ip);
  }
}
function tsDone(ip) {
  tsShow(el('div', { className: 'tsdone' }, el('div', { className: 'tsok', textContent: '✓' }), el('b', { textContent: 'TV đã vào Tailscale' }), el('code', { textContent: ip })),
    el('p', { className: 'muted tsnote', textContent: CORE === 'pc' ? 'Bấm “Lưu” ở bước cuối để dùng IP này cả khi không ở nhà.' : 'Ở ngoài nhà: bật Tailscale trên máy này (cùng tài khoản) rồi mở TV bằng IP này.' }),
    el('p', { className: 'tstip', title: 'Nên làm thêm: trong trang quản trị Tailscale, mở menu ⋯ của TV → Disable key expiry, để TV không bị đăng xuất sau khoảng 180 ngày.' },
      el('span', { textContent: '💡 Tắt “key expiry” cho TV để không bị đăng xuất sau 180 ngày.' })),
    row(el('a', { className: 'linkbtn small', href: 'https://login.tailscale.com/admin/machines', target: '_blank', rel: 'noopener', textContent: '↗ Trang quản trị' }), act('✓ Xong', async () => $('#ts').close(), 'primary')));
  tsState(); if (CORE === 'pc') loadSteps();
}
async function tsState() {                                             // the card on the tv / phone cores (the computer has its step 2)
  if (CORE === 'pc' || ADB.state !== 'ready') return;
  const s = await api('/api/tailscale', null, true); if (!s) return;
  $('#tsstate').textContent = s.ip ? 'Đã vào Tailscale · IP ' + s.ip + '. Ở ngoài nhà, máy điều khiển bật Tailscale (cùng tài khoản) rồi dùng IP này.'
    : s.installed ? 'Tailscale đã cài nhưng chưa đăng nhập hoặc đang tắt. Bấm nút dưới để bật và đăng nhập.' : TS_PITCH;
  $('#tsgo').hidden = !!s.ip; $('#tsgo').textContent = s.installed ? '⚡ Bật và đăng nhập Tailscale' : '⚡ Cài và đăng nhập Tailscale';
}
$('#tsgo').addEventListener('click', tailscaleGo);
$('#tsclose').addEventListener('click', () => $('#ts').close());
$('#ts').addEventListener('close', () => { tsGen++; });

let stepsBusy = false, pairing = false;
function step(n, state, title, text, ...actions) {
  return el('div', { className: 'step ' + state }, el('div', { className: 'mark', textContent: state === 'ok' ? '✓' : n }),
    el('div', { className: 'body' }, el('b', { textContent: title }), el('p', { textContent: text }), actions.filter(Boolean).length ? el('div', { className: 'actions' }, ...actions.filter(Boolean)) : null));
}
const act = (label, fn, cls = '') => el('button', { className: 'small ' + cls, textContent: label, onclick: async (e) => { const b = e.currentTarget; b.disabled = true; try { await fn(); } finally { b.disabled = false; } } });
async function loadSteps() {
  if (stepsBusy || CORE !== 'pc') return; stepsBusy = true;
  const s = await api('/api/setup/status', null, true); stepsBusy = false;
  if (!s) return;
  $('#setuphost').textContent = s.host ? (s.model ? s.model + ' · ' : '') + s.host : 'chưa chọn TV';
  if (!s.host) { $('#steps').replaceChildren(el('p', { className: 'muted', textContent: 'Chọn một TV ở trên (Quét mạng hoặc nhập IP) để bắt đầu.' })); return; }
  if (s.kind && s.kind !== 'android') { showSteps(nativeSteps(s)); return; }
  const adb = s.adb.state, ready = adb === 'ready', out = [];
  // 1. ADB
  out.push(ready ? step(1, 'ok', 'ADB đã kết nối', 'Máy này đã được TV cho phép.')
    : adb === 'unauthorized' ? step(1, 'wait', 'TV đang chờ bạn cho phép', 'Nhìn lên TV: tích “Luôn cho phép từ máy tính này” rồi chọn OK.', act('🔄 Đã bấm, thử lại', async () => { await api('/api/adb/connect', {}); await loadSteps(); }, 'primary'))
    : step(1, 'todo', 'Bật “Gỡ lỗi ADB” trên TV', 'Bấm “Bật ADB giúp tôi” để trang này tự làm phần lớn việc. Hoặc tự làm trên TV: Cài đặt → Tùy chọn thiết bị → Giới thiệu → bấm OK 7 lần vào “Bản dựng”, rồi Tùy chọn nhà phát triển → bật “Gỡ lỗi ADB” (hoặc “Gỡ lỗi USB”).',
        act('🧭 Bật ADB giúp tôi', async () => adbHelp(), 'primary'), act('🔌 Đã bật, kết nối', async () => { await api('/api/adb/connect', {}); await loadSteps(); })));
  // 2. Tailscale
  if (!ready) out.push(step(2, 'todo', 'Tailscale (để điều khiển khi không ở nhà)', 'Làm sau khi ADB đã kết nối. Chỉ dùng trong cùng Wi-Fi thì bỏ qua bước này được.'));
  else if (s.tailscale_ip) out.push(step(2, 'ok', 'Tailscale đã vào mạng', 'IP Tailscale của TV: ' + s.tailscale_ip + '. ' + 'Nên làm thêm: trong trang quản trị Tailscale, mở menu ⋯ của TV → Disable key expiry, để TV không bị đăng xuất sau khoảng 180 ngày.'));
  else out.push(step(2, 'todo', 'Tailscale (để điều khiển khi không ở nhà)', TS_PITCH, act('⚡ Cài và đăng nhập Tailscale', async () => tailscaleGo(), 'primary')));
  // 3. remote pairing
  const r = s.remote;
  if (r.connected) out.push(step(3, 'ok', 'Nút remote đã ghép', 'Các nút điều khiển dùng được.'));
  else if (r.paired === false || pairing) {
    const code = el('input', { placeholder: 'Mã 6 ký tự trên TV', maxLength: 6, autocomplete: 'off' });
    out.push(step(3, 'wait', 'Ghép nút remote', pairing ? 'TV đang hiện mã 6 ký tự. Gõ mã vào đây.' : 'Bấm “Ghép”, TV sẽ hiện mã 6 ký tự.',
      pairing ? code : null,
      pairing ? act('✓ Xác nhận', async () => { const x = await api('/api/pair/finish', { code: code.value.trim() }); if (x) { pairing = false; toast('Đã ghép'); await refresh(); await loadSteps(); } }, 'primary') : null,
      act(pairing ? '🔄 Lấy mã mới' : '🔗 Ghép', async () => { if (await api('/api/pair/start', {})) { pairing = true; await loadSteps(); } }, pairing ? '' : 'primary')));
  } else out.push(step(3, 'todo', 'Nút remote', r.error ? 'Chưa kết nối được (cổng 6466). TV có bật và chung mạng không?' : 'Đang kết nối...', act('🔗 Thử ghép', async () => { if (await api('/api/pair/start', {})) { pairing = true; await loadSteps(); } })));
  // 4. save
  const move = s.tailscale_ip && s.tailscale_ip !== s.host;
  if (s.saved && !move) out.push(step(4, 'ok', 'Đã lưu: ' + s.saved.name, 'Lần sau mở lên là dùng TV này. ' + (s.saved.mac ? 'Đã ghi địa chỉ MAC để đánh thức TV.' : '')));
  else {
    const name = el('input', { placeholder: 'Tên gọi, vd TV phòng khách', value: (s.saved && s.saved.name) || s.model || '' });
    out.push(step(4, 'todo', move ? 'Lưu và chuyển sang IP Tailscale' : 'Lưu TV này', move ? 'TV đã có IP Tailscale ' + s.tailscale_ip + '. Lưu để từ nay kết nối qua IP đó, dùng được cả khi không ở nhà.' : 'Để lần sau mở lên là dùng ngay, không phải tìm lại.',
      name, act('💾 Lưu', async () => { const x = await api('/api/tv/save', { name: name.value }); if (x) { toast(x.moved ? 'Đã lưu, chuyển sang ' + x.tv.host : 'Đã lưu'); await refresh(); await loadSteps(); } }, 'primary')));
  }
  showSteps(out);
}
let stepsSig = '';
function showSteps(out) {
  if (document.activeElement && $('#steps').contains(document.activeElement) && document.activeElement.tagName === 'INPUT') return;   // don't wipe what is being typed
  const sig = out.map((n) => n.outerHTML).join('');                   // the poll often brings the same steps: leave them be
  if (sig === stepsSig && $('#steps').childElementCount === out.length) return;
  stepsSig = sig; $('#steps').replaceChildren(...out);
}
// An LG or Samsung TV: no ADB, no code to type. The TV asks itself whether to let this computer in; then save it.
function nativeSteps(s) {
  const r = s.remote, out = [], again = act('🔄 Hỏi lại TV', async () => { if (await api('/api/pair/start', {})) { toast('Nhìn lên TV'); await sleep(800); await loadSteps(); } }, 'primary');
  out.push(r.connected ? step(1, 'ok', 'TV đã cho phép', 'Các nút điều khiển dùng được.')
    : r.asking ? step(1, 'wait', 'Nhìn lên TV: chọn Cho phép', 'TV đang hỏi có cho máy này điều khiển không.')
    : r.error === 'refused' ? step(1, 'todo', 'TV đã từ chối', 'Bấm “Hỏi lại TV” rồi chọn Cho phép trên TV.', again)
    : r.error === 'not accepted' ? step(1, 'todo', 'Chưa ai chọn Cho phép trên TV', 'Bấm “Hỏi lại TV”, rồi chọn Cho phép trong lúc TV hỏi.', again)
    : step(1, 'todo', 'Kết nối TV', r.error ? 'Chưa kết nối được. TV có bật và chung mạng không?' : 'Đang kết nối...', r.error ? again : null));
  if (s.saved) out.push(step(2, 'ok', 'Đã lưu: ' + s.saved.name, 'Lần sau mở lên là dùng TV này. ' + (s.saved.mac ? 'Đã ghi địa chỉ MAC để đánh thức TV.' : '')));
  else {
    const name = el('input', { placeholder: 'Tên gọi, vd TV phòng khách', value: s.model || '' });
    out.push(step(2, 'todo', 'Lưu TV này', 'Để lần sau mở lên là dùng ngay, không phải tìm lại.',
      name, act('💾 Lưu', async () => { const x = await api('/api/tv/save', { name: name.value }); if (x) { toast('Đã lưu'); await refresh(); await loadSteps(); } }, 'primary')));
  }
  return out;
}
setInterval(() => { if (currentTab === 'tv' && !document.hidden && !stopped) loadSteps(); }, 6000);

const startTab = (window.LANG_KEEP && window.LANG_KEEP.tab) || location.hash.slice(1) || new URLSearchParams(location.search).get('tab') || (CORE === 'phone' ? 'apps' : '');
if (CORE !== 'pc') $('#wake').parentElement.className = 'rrow three';   // no wake button there: three keys fill the row
if (['remote', 'apps', 'files', 'health', 'auto', 'tv'].includes(startTab)) showTab(startTab); else if (startTab === 'install') showTab('files');
else if (startTab === 'term') { showTab('health'); $('#termcard').open = true; }   // the terminal used to be a tab of its own
document.documentElement.classList.add('tabbed');                     // the right tab is open: show it (theme.js)
// One check at a time, and none while the page is hidden: a TV that is slow to answer must not pile them up.
let polling = false;
applyAdb(); refresh(); setInterval(async () => { if (polling || document.hidden) return; polling = true; try { await refresh(); } finally { polling = false; } }, 4000);
document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
homeHint();
if (window.LANG_KEEP && window.LANG_KEEP.y) setTimeout(() => window.scrollTo(0, window.LANG_KEEP.y), 400);   // after the tab has filled in

// ---------- devices paired with this TV (the TV web page only: TV Setup keeps the list, RemoteService /api/paired) ----------
const agoText = (t) => {
  const m = Math.max(0, (Date.now() - t) / 60000);
  if (m < 2) return 'vừa xong';
  if (m < 60) return Math.round(m) + ' phút trước';
  if (m < 2880) return Math.round(m / 60) + ' giờ trước';
  return Math.round(m / 1440) + ' ngày trước';
};
async function loadPaired() {
  const box = $('#paired'); if (!box || CORE !== 'tv') return;
  const r = await api('/api/paired', null, true); if (!r) return;
  const out = async (body, me) => { if (await api('/api/paired/forget', body)) { if (me) refresh(); else loadPaired(); } };
  const owner = SCOPE === 'full';
  const setScope = async (d, k) => { if (k !== d.scope && await api('/api/paired/scope', { id: d.id, scope: k })) loadPaired(); };
  const rows = r.devices.map((d) => UI.item({
    name: d.name || 'Thiết bị cũ',
    sub: [d.created ? tr('Ghép lúc') + ' ' + new Date(d.created).toLocaleString(LANG === 'vi' ? 'vi-VN' : 'en-GB', { dateStyle: 'short', timeStyle: 'short' }) : '',
      d.me ? tr('thiết bị này') : tr('dùng') + ' ' + tr(agoText(d.seen)), d.ip].filter(Boolean).join(' · '),
    extra: owner && d.kind === 'web' && !d.me ? [scopeChips(d.scope || 'full', (k) => setScope(d, k))] : [],   // what it may do: Scope.kt
    acts: [el('button', { className: 'small danger', textContent: 'Hủy ghép', title: 'Quên thiết bị này',
      onclick: async () => { if (await ask('Quên ' + (d.name || 'thiết bị này') + (d.me ? ' (chính là máy này, sẽ phải nhập mã lại)' : '') + '?', { ok: 'Quên', danger: true })) out({ id: d.id }, d.me); } })],
  }));
  box.replaceChildren(UI.card('📱 Thiết bị đã ghép', { tools: owner && r.devices.length > 1 ? [el('button', { className: 'small danger', textContent: 'Gỡ tất cả', title: 'Quên mọi thiết bị: ai muốn điều khiển phải nhập mã mới',
    onclick: async () => { if (await ask('Gỡ mọi thiết bị đã ghép, cả máy này?', { ok: 'Gỡ tất cả', danger: true })) out({ all: true }, true); } })] : [] },
    UI.list(rows, 'Chưa ghép thiết bị nào')));
}
