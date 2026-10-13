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
  if (r && r.version) $('#aboutver').textContent = r.version;
});
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
  if (currentTab === 'tv') { loadTvs(); loadSteps(); }
}
async function selectTv(host) {
  const r = await api('/api/tv/select', { host }); if (!r) return;
  toast('Đang kết nối ' + host + '...'); await refresh(); loadTvs(); loadSteps();
}
async function loadTvs() {
  const r = await api('/api/tvs', null, true); if (!r) return;
  const rows = r.tvs.map((t) => el('div', { className: 'item' },
    el('div', { className: 'name' }, el('span', { textContent: t.name || t.host }), t.host === r.current ? el('span', { className: 'badge ok', textContent: 'đang dùng' }) : null,
      el('small', { textContent: t.host + (t.lan && t.lan !== t.host ? ' · trong nhà ' + t.lan : '') })),
    el('div', { className: 'acts' },
      t.host === r.current ? null : setLabel(el('button', { className: 'small primary', onclick: () => selectTv(t.host) }), '✓', 'Dùng'),
      setLabel(el('button', { className: 'small danger', onclick: async () => { if (confirm('Xóa "' + (t.name || t.host) + '" khỏi danh sách? (Không đổi gì trên TV)')) { await api('/api/tv/remove', { host: t.host }); await refresh(); loadTvs(); loadSteps(); } } }), '🗑', 'Xóa'))));
  $('#tvlist').replaceChildren(...(rows.length ? rows : [el('div', { className: 'muted', textContent: 'Chưa có TV nào. Bấm “Quét mạng” hoặc nhập IP ở bên cạnh.' })]));
}
$('#scan').addEventListener('click', async () => {
  const b = $('#scan'); b.disabled = true; b.textContent = '⏳ Đang quét...'; $('#scanlist').replaceChildren();
  const r = await api('/api/discover');
  b.disabled = false; b.textContent = '🔍 Quét mạng'; if (!r) return;
  $('#scaninfo').textContent = (r.networks.length ? 'Đã quét ' + r.networks.join(', ') : 'Không thấy mạng nội bộ nào') + ' và Tailscale. ' + (r.found.length ? '' : 'Không thấy TV nào: TV có bật và cùng Wi-Fi với máy này không? Hoặc nhập IP bên dưới.');
  $('#scanlist').replaceChildren(...r.found.map((f) => el('div', { className: 'item' },
    el('div', { className: 'name' }, el('span', { textContent: f.name || f.ip }),
      el('span', { className: 'badge' + (f.remote ? ' ok' : ''), textContent: f.remote ? 'Android TV' : 'chỉ có ADB' }), f.adb ? el('span', { className: 'badge ok', textContent: 'ADB đã bật' }) : el('span', { className: 'badge', textContent: 'ADB chưa bật' }),
      el('small', { textContent: f.ip + (f.via === 'tailscale' ? ' · qua Tailscale' : ' · cùng mạng') })),
    el('div', { className: 'acts' }, setLabel(el('button', { className: 'small primary', onclick: () => selectTv(f.ip) }), '✓', 'Chọn')))));
});
$('#ipf').addEventListener('submit', (e) => { e.preventDefault(); const ip = $('#ipin').value.trim(); if (ip) selectTv(ip); });
$('#setuprefresh').addEventListener('click', () => loadSteps());

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
    : step(1, 'todo', 'Bật “Gỡ lỗi ADB” trên TV', 'Trên TV: Cài đặt → Tùy chọn thiết bị → Giới thiệu → bấm OK 7 lần vào “Bản dựng”. Rồi Tùy chọn nhà phát triển → bật “Gỡ lỗi ADB” (hoặc “Gỡ lỗi USB”). Việc này phải làm bằng remote của TV.',
        act('🔌 Đã bật, kết nối', async () => { await api('/api/adb/connect', {}); await loadSteps(); }, 'primary')));
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
      name, act('💾 Lưu', async () => { const x = await api('/api/tv/save', { name: name.value }); if (x) { toast(x.moved ? 'Đã lưu, chuyển sang ' + x.tv.host : 'Đã lưu'); await refresh(); loadTvs(); await loadSteps(); } }, 'primary')));
  }
  // 5. optimise
  out.push(step(5, s.flauncher ? 'ok' : 'todo', 'Tối ưu TV (tùy chọn)', s.flauncher ? 'Đã có FLauncher. Chạy lại để áp dụng đủ các bước, hoặc Hoàn tác ở tab Tải / Cài.' : 'Giữ Tailscale sống, giảm hiệu ứng, thay màn hình chính bằng FLauncher, tắt app hệ thống không dùng. Hoàn tác được.',
    ready ? act('⚡ Tối ưu TV', async () => { showTab('install'); await runSetup('apply'); }) : null));
  if (document.activeElement && $('#steps').contains(document.activeElement) && document.activeElement.tagName === 'INPUT') return;   // don't wipe what is being typed
  $('#steps').replaceChildren(...out);
}
setInterval(() => { if (currentTab === 'tv' && !document.hidden && !stopped) loadSteps(); }, 6000);

const startTab = location.hash.slice(1);
if (['remote', 'apps', 'files', 'install', 'health', 'tv'].includes(startTab)) showTab(startTab);
applyAdb(); refresh(); setInterval(refresh, 4000);
