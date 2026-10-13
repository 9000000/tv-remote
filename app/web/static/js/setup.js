// optimise, language, quit, and the TV tab (saved TVs, scan, first-time setup)
// ---------- one-click TV setup (cli/tv_setup.py) ----------
async function runSetup(mode) {
  const ask = mode === 'apply' ? 'Chạy tối ưu TV? Màn hình chính của TV sẽ là FLauncher.' : 'Hoàn tác? Bật lại màn hình chính Google, các app đã tắt và hiệu ứng như cũ.';
  if (!confirm(ask)) return;
  const btns = [$('#setupapply'), $('#setupundo')], log = $('#setuplog');
  btns.forEach((b) => { b.disabled = true; }); log.hidden = false; log.textContent = 'Đang chạy...';
  let j = await api('/api/setup', { mode });
  while (j && j.status === 'running') { await sleep(1000); j = await api('/api/job?id=' + j.id); if (j) { log.textContent = j.log || 'Đang chạy...'; log.scrollTop = log.scrollHeight; } }
  if (j) { log.textContent = (j.log || '') + (j.status === 'done' ? '\n✅ Xong.' : '\n❌ Lỗi: ' + j.msg); toast(j.status === 'done' ? 'Đã xong' : 'Lỗi: ' + j.msg); quickLoaded = appsLoaded = false; applyAdb(); }
  btns.forEach((b) => { b.disabled = false; });
}
$('#setupapply').addEventListener('click', () => runSetup('apply'));
$('#setupundo').addEventListener('click', () => runSetup('undo'));

// ---------- language: Vietnamese is the source, i18n.js renders English ----------
$('#lang').textContent = LANG === 'vi' ? '🌐 EN' : '🌐 VI';
$('#lang').addEventListener('click', () => setLang(LANG === 'vi' ? 'en' : 'vi'));

// ---------- about ----------
$('#aboutbtn').addEventListener('click', async () => {
  $('#about').showModal();
  const r = await fetch('/api/about').then((x) => x.json()).catch(() => null);
  if (r && r.version) $('#aboutver').textContent = r.version + (r.released ? ' · ' + r.released : '');
  // scan with a phone: the Android app, and (when the TV Setup app is serving it) this TV's own remote page
  const code = (title, note, link) => { const s = qrSvg(link, 170); return s ? el('div', { className: 'qr' }, s, el('b', { textContent: title }), el('span', { className: 'muted', textContent: note }), el('br'), el('a', { href: link, target: '_blank', rel: 'noopener', textContent: link.replace(/^https?:\/\//, '') })) : null; };
  $('#qrs').replaceChildren(...[code('📱 App Android', 'Quét bằng điện thoại để tải app.', 'https://github.com/HoangTran0410/tv-remote/releases/latest/download/tv-remote.apk'),
    r && r.tv_web ? code('🌐 Remote trên điện thoại', 'Trang remote do chính TV phục vụ: quét khi điện thoại cùng Wi-Fi hoặc cùng Tailscale với TV.', r.tv_web) : null].filter(Boolean));
});
fetch('/api/about').then((x) => x.json()).then((r) => { if (r.version) $('#ver').textContent = 'TV Remote ' + r.version + (r.released ? ' · ' + r.released : ''); }).catch(() => {});
$('#aboutclose').addEventListener('click', () => $('#about').close());
$('#about').addEventListener('click', (e) => { if (e.target === $('#about')) $('#about').close(); });   // click outside the box

// ---------- stop the local server from the page ----------
let stopped = false;
$('#quit').addEventListener('click', async () => {
  if (!confirm('Tắt chương trình remote trên máy tính này?\nTV không bị ảnh hưởng. Muốn dùng lại thì bấm đúp start-mac.command / start-windows.cmd.')) return;
  if (screenOn) stopScreen();
  if (!await api('/api/shutdown', {})) return;
  stopped = true;
  document.querySelector('main').replaceChildren(el('section', { className: 'card', style: 'text-align:center;padding:40px 16px' },
    el('h1', { textContent: 'Đã tắt' }), el('p', { className: 'muted', textContent: 'Chương trình remote trên máy tính này đã dừng. Bấm đúp start-mac.command (Windows: start-windows.cmd) để mở lại. Có thể đóng tab này.' })));
});

// ---------- TVs: saved list, discovery, first-time setup ----------
function tvChanged() {                                                // another TV: forget everything cached about the old one
  if (screenOn) stopScreen();
  quickLoaded = appsLoaded = filesLoaded = false; META.clear(); cwd = '/sdcard/Download'; bannerKey = '';
  $('#quick').replaceChildren(el('div', { className: 'muted', textContent: 'Danh sách app hiện khi ADB sẵn sàng.' }));
  $('#appslist').replaceChildren(); $('#fileslist').replaceChildren();
  if (currentTab === 'tv') loadSteps();
  if (picking) loadTvs();
}
async function selectTv(host) {
  const r = await api('/api/tv/select', { host }); if (!r) return;
  toast('Đang kết nối ' + host + '...'); wantPick = false; cameFromTv = false; justPicked = host; pickedAt = Date.now(); showTab('remote'); await sleep(1200); await refresh(); loadSteps();
}
$('#switchtv').addEventListener('click', () => { wantPick = true; cameFromTv = true; refresh(); });
$('#pickback').addEventListener('click', () => { wantPick = false; cameFromTv = false; refresh(); });
const openTv = (host, current) => { if (host === current) { wantPick = false; cameFromTv = false; justPicked = host; pickedAt = Date.now(); refresh(); } else selectTv(host); };
async function loadTvs() {
  const r = await api('/api/tvs', null, true); if (!r) return;
  const rows = r.tvs.map((t) => el('div', { className: 'item', onclick: (e) => { if (!e.target.closest('button')) openTv(t.host, r.current); } },
    el('div', { className: 'name' }, el('span', { textContent: t.name || t.host }), t.host === r.current && cameFromTv ? el('span', { className: 'badge ok', textContent: 'đang dùng' }) : null,
      el('small', { textContent: t.host + (t.lan && t.lan !== t.host ? ' · trong nhà ' + t.lan : '') })),
    el('div', { className: 'acts' },
      setLabel(el('button', { className: 'small primary', onclick: () => openTv(t.host, r.current) }), '▶', 'Điều khiển'),
      setLabel(el('button', { className: 'small danger', onclick: async () => { if (confirm('Xóa "' + (t.name || t.host) + '" khỏi danh sách? (Không đổi gì trên TV)')) { await api('/api/tv/remove', { host: t.host }); await refresh(); loadTvs(); loadSteps(); } } }), '🗑', 'Xóa'))));
  $('#tvlist').replaceChildren(...rows);
  $('#savedcard').hidden = !rows.length; $('#pickcols').classList.toggle('solo', !rows.length);   // nothing saved: no empty box, just the search
}
$('#scan').addEventListener('click', async () => {
  const b = $('#scan'); b.disabled = true; b.textContent = '⏳ Đang quét...'; $('#scanlist').replaceChildren();
  const r = await api('/api/discover');
  b.disabled = false; b.textContent = '🔍 Quét mạng'; if (!r) return;
  $('#scaninfo').textContent = (r.networks.length ? 'Đã quét ' + r.networks.join(', ') : 'Không thấy mạng nội bộ nào') + ' và Tailscale. ' + (r.found.length ? '' : 'Không thấy TV nào: TV có bật và cùng Wi-Fi với máy này không? Hoặc nhập IP bên dưới.');
  $('#scanlist').replaceChildren(...r.found.map((f) => el('div', { className: 'item', onclick: (e) => { if (!e.target.closest('button')) selectTv(f.ip); } },
    el('div', { className: 'name' }, el('span', { textContent: f.name || f.ip }),
      el('span', { className: 'badge' + (f.remote ? ' ok' : ''), textContent: f.remote ? 'Android TV' : 'chỉ có ADB' }), f.adb ? el('span', { className: 'badge ok', textContent: 'ADB đã bật' }) : el('span', { className: 'badge', textContent: 'ADB chưa bật' }),
      el('small', { textContent: f.ip + (f.via === 'tailscale' ? ' · qua Tailscale' : ' · cùng mạng') })),
    el('div', { className: 'acts' }, setLabel(el('button', { className: 'small primary', onclick: () => selectTv(f.ip) }), '✓', 'Chọn')))));
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
      row(act('🔗 Tới bước ghép remote', async () => { $('#assist').close(); showTab('tv'); }, 'primary')), say('Hoặc tự làm bằng remote của TV:\n' + ADB_STEPS));
    return;
  }
  assistShow('Bật ADB cho TV này',
    choice('⚡ Tự động: cài TV Setup lên TV', 'Trang này cho TV tự tải và mở app TV Setup; app đó mở thẳng Tùy chọn nhà phát triển. Bạn chỉ xác nhận trên TV.', installSetup, 'primary'),
    choice('🔧 Tự làm: mở Cài đặt TV', 'Thử mọi cách mở Cài đặt, rồi chỉ các bước bật Gỡ lỗi ADB.', findSettings),
    choice('🏠 Cài màn hình chính khác', 'Khi TV giấu Cài đặt: mở qua một launcher từ CH Play.', otherHome),
    choice('🛒 Mở CH Play trên TV', '', () => assist('open', { what: 'https://play.google.com/store/apps' })),
    choice('📦 Mở app theo tên gói', 'Đã cài thì mở app, chưa cài thì mở trang CH Play của nó.', () => { const p = prompt('Tên gói hoặc đường dẫn', 'com.example.app'); if (p && p.trim()) assist('open', { what: p.trim() }); }),
    say('Khi TV bật ADB xong, trang này tự nhận ra và kết nối. Các nút mũi tên, OK ở tab Remote vẫn dùng được trong lúc làm.'));
}
const ADB_STEPS = '1. Trong Cài đặt: Tùy chọn thiết bị (hoặc Hệ thống) → Giới thiệu → bấm OK 7 lần vào “Bản dựng”.\n2. Quay lại, vào Tùy chọn nhà phát triển → bật “Gỡ lỗi USB” (hoặc “Gỡ lỗi ADB”).\n3. Trên TV tích “Luôn cho phép” rồi chọn OK.\n\nNếu TV đòi “mật khẩu ADB” khi bấm 7 lần: dùng cách “Tự động: cài TV Setup lên TV”.';
function adbSteps() {
  assistShow('Bật Gỡ lỗi ADB trên TV', say('Dùng các nút mũi tên và OK ở tab Remote, hoặc remote của TV:\n\n' + ADB_STEPS),
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
  const msg = say('Đang bắt đầu...'); msg.style.minHeight = '5em';       // the words change as it goes; the buttons below must not jump
  const done = el('button', { className: 'small', textContent: '✓ Đã cài xong', onclick: async () => { setDone(true); await assist('installed'); } });
  const setDone = (busy) => { done.disabled = busy; done.textContent = tr(busy ? '⏳ Đang kiểm tra...' : '✓ Đã cài xong'); };
  assistShow('Cài TV Setup lên TV', msg, row(done, act('■ Dừng', async () => { await assist('stop'); await sleep(400); adbHelp(); })));
  const gen = assistGen;
  for (;;) {
    await sleep(1000); if (gen !== assistGen) return;
    const r = await api('/api/assist', null, true); if (gen !== assistGen) return;
    if (!r || r.status === 'running') { if (r) { if (r.msg) msg.textContent = tr(r.msg); setDone(!!r.checking); } continue; }
    if (r.status === 'look') return assistShow('Nhìn lên TV', say('TV đã nhận đường dẫn và tải TV Setup.\n\n1. TV hỏi cài đặt: chọn Cài đặt (Install). Nếu TV hỏi quyền cài từ nguồn này: bật cho app đang tải, bấm Quay lại, rồi chọn Install.\n2. Cài xong chọn Mở (hoặc bấm “Mở TV Setup” bên dưới).\n3. TV Setup tự mở Tùy chọn nhà phát triển: bật “Gỡ lỗi USB”.\n4. TV hỏi cho phép gỡ lỗi: tích “Luôn cho phép”, chọn OK. Trang này tự kết nối.'),
      row(act('📺 Mở TV Setup', async () => { await assist('open', { what: 'dev.tvremote.setup' }); }, 'primary'), act('↻ Làm lại', async () => installSetup()), act('← Các cách khác', async () => adbHelp())));
    return assistShow('Cài TV Setup lên TV', say(tr(r.msg) || 'Không làm được.'), row(act('↻ Thử lại', async () => installSetup(), 'primary'), act('← Các cách khác', async () => adbHelp())));
  }
}

let stepsBusy = false, pairing = false;
function step(n, state, title, text, ...actions) {
  return el('div', { className: 'step ' + state }, el('div', { className: 'mark', textContent: state === 'ok' ? '✓' : n }),
    el('div', { className: 'body' }, el('b', { textContent: title }), el('p', { textContent: text }), actions.filter(Boolean).length ? el('div', { className: 'actions' }, ...actions.filter(Boolean)) : null));
}
const act = (label, fn, cls = '') => el('button', { className: 'small ' + cls, textContent: label, onclick: async (e) => { const b = e.currentTarget; b.disabled = true; try { await fn(); } finally { b.disabled = false; } } });
async function loadSteps() {
  if (stepsBusy) return; stepsBusy = true;
  const s = await api('/api/setup/status', null, true); stepsBusy = false;
  if (!s) return;
  $('#setuphost').textContent = s.host ? (s.model ? s.model + ' · ' : '') + s.host : 'chưa chọn TV';
  if (!s.host) { $('#steps').replaceChildren(el('p', { className: 'muted', textContent: 'Chọn một TV ở trên (Quét mạng hoặc nhập IP) để bắt đầu.' })); return; }
  const adb = s.adb.state, ready = adb === 'ready', out = [];
  // 1. ADB
  out.push(ready ? step(1, 'ok', 'ADB đã kết nối', 'Máy này đã được TV cho phép.')
    : adb === 'unauthorized' ? step(1, 'wait', 'TV đang chờ bạn cho phép', 'Nhìn lên TV: tích “Luôn cho phép từ máy tính này” rồi chọn OK.', act('🔄 Đã bấm, thử lại', async () => { await api('/api/adb/connect', {}); await loadSteps(); }, 'primary'))
    : step(1, 'todo', 'Bật “Gỡ lỗi ADB” trên TV', 'Bấm “Bật ADB giúp tôi” để trang này tự làm phần lớn việc. Hoặc tự làm trên TV: Cài đặt → Tùy chọn thiết bị → Giới thiệu → bấm OK 7 lần vào “Bản dựng”, rồi Tùy chọn nhà phát triển → bật “Gỡ lỗi ADB” (hoặc “Gỡ lỗi USB”).',
        act('🧭 Bật ADB giúp tôi', async () => adbHelp(), 'primary'), act('🔌 Đã bật, kết nối', async () => { await api('/api/adb/connect', {}); await loadSteps(); })));
  // 2. Tailscale
  if (!ready) out.push(step(2, 'todo', 'Tailscale (để điều khiển khi không ở nhà)', 'Làm sau khi ADB đã kết nối. Chỉ dùng trong cùng Wi-Fi thì bỏ qua bước này được.'));
  else if (s.tailscale_ip) out.push(step(2, 'ok', 'Tailscale đã vào mạng', 'IP Tailscale của TV: ' + s.tailscale_ip + '. ' + 'Nên làm thêm: trong trang quản trị Tailscale, mở menu ⋯ của TV → Disable key expiry, để TV không bị đăng xuất sau khoảng 180 ngày.'));
  else if (!s.tailscale_installed) out.push(step(2, 'todo', 'Cài Tailscale lên TV', 'Để điều khiển TV khi không ở cùng mạng. Tải bản chính thức (khoảng 100 MB), có kiểm checksum. Chỉ dùng trong cùng Wi-Fi thì bỏ qua được.',
    act('⬇️ Cài Tailscale', async () => { const j = await api('/api/tailscale/install', {}); if (j) { await trackJob(j, 'Cài Tailscale'); await loadSteps(); } }, 'primary')));
  else out.push(step(2, 'wait', 'Thêm TV vào Tailscale của bạn', 'Mở Tailscale trên TV rồi xem màn hình TV ngay tại đây: TV hiện một mã (và mã QR). Không cần gõ tài khoản trên TV: mở login.tailscale.com/admin/machines trên máy này hoặc điện thoại → Add device → nhập mã đó (hoặc quét QR bằng điện thoại).',
    act('📺 Mở Tailscale trên TV', async () => { if (await api('/api/tailscale/open', {})) { showTab('remote'); if (!screenOn) startScreen(); } }, 'primary'), act('🔄 Đã thêm, kiểm tra', loadSteps)));
  // 3. remote pairing
  const r = s.remote;
  if (r.connected) out.push(step(3, 'ok', 'Nút remote đã ghép', 'Các nút điều khiển dùng được.'));
  else if (r.paired === false || pairing) {
    const code = el('input', { placeholder: 'Mã 6 ký tự trên TV', maxLength: 6, autocomplete: 'off' });
    out.push(step(3, 'wait', 'Ghép nút remote', pairing ? 'TV đang hiện mã 6 ký tự. Gõ mã vào đây.' : 'Bấm “Ghép”, TV sẽ hiện mã 6 ký tự.',
      pairing ? code : null,
      pairing ? act('✓ Xác nhận', async () => { const x = await api('/api/pair/finish', { code: code.value.trim() }); if (x) { pairing = false; toast('Đã ghép'); await refresh(); await loadSteps(); } }, 'primary') : null,
      act(pairing ? '🔄 Lấy mã mới' : '🔗 Ghép', async () => { if (await api('/api/pair/start', {})) { pairing = true; await loadSteps(); } }, pairing ? '' : 'primary')));
  } else out.push(step(3, 'todo', 'Nút remote', r.error ? 'Chưa kết nối được tới dịch vụ remote của TV (cổng 6466): TV đang bật và cùng mạng chứ? Thiết bị này có phải Android TV / Google TV không?' : 'Đang kết nối...', act('🔗 Thử ghép', async () => { if (await api('/api/pair/start', {})) { pairing = true; await loadSteps(); } })));
  // 4. save
  const move = s.tailscale_ip && s.tailscale_ip !== s.host;
  if (s.saved && !move) out.push(step(4, 'ok', 'Đã lưu: ' + s.saved.name, 'Lần sau mở lên là dùng TV này. ' + (s.saved.mac ? 'Đã ghi địa chỉ MAC để đánh thức TV.' : '')));
  else {
    const name = el('input', { placeholder: 'Tên gọi, vd TV phòng khách', value: (s.saved && s.saved.name) || s.model || '' });
    out.push(step(4, 'todo', move ? 'Lưu và chuyển sang IP Tailscale' : 'Lưu TV này', move ? 'TV đã có IP Tailscale ' + s.tailscale_ip + '. Lưu để từ nay kết nối qua IP đó, dùng được cả khi không ở nhà.' : 'Để lần sau mở lên là dùng ngay, không phải tìm lại.',
      name, act('💾 Lưu', async () => { const x = await api('/api/tv/save', { name: name.value }); if (x) { toast(x.moved ? 'Đã lưu, chuyển sang ' + x.tv.host : 'Đã lưu'); await refresh(); await loadSteps(); } }, 'primary')));
  }
  if (document.activeElement && $('#steps').contains(document.activeElement) && document.activeElement.tagName === 'INPUT') return;   // don't wipe what is being typed
  $('#steps').replaceChildren(...out);
}
setInterval(() => { if (currentTab === 'tv' && !document.hidden && !stopped) loadSteps(); }, 6000);

const startTab = location.hash.slice(1);
if (['remote', 'apps', 'files', 'health', 'term', 'tv'].includes(startTab)) showTab(startTab); else if (startTab === 'install') showTab('files');
applyAdb(); refresh(); setInterval(refresh, 4000);
