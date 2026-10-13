// health tab: a snapshot of the TV (load, memory, Wi-Fi, hardware), refreshed while the tab is open
let healthBusy = false;
const kv = (pairs) => pairs.filter((p) => p && p[1] != null && p[1] !== '').flatMap(([k, v]) => [el('div', { className: 'k', textContent: k }), el('div', { className: 'v', textContent: String(v) })]);
function meter(label, pct, detail) {
  const m = el('div', { className: 'meter' + (pct >= 90 ? ' bad' : pct >= 75 ? ' warn' : '') },
    el('div', { className: 'top' }, el('span', { textContent: label }), el('b', { textContent: detail })), el('div', { className: 'bar' }, el('i')));
  m.querySelector('i').style.width = Math.max(0, Math.min(100, pct)) + '%';
  return m;
}
function fmtUptime(s) {
  const d = Math.floor(s / 86400), h = Math.floor(s % 86400 / 3600), m = Math.floor(s % 3600 / 60);
  return (d ? d + ' ngày ' : '') + (d || h ? h + ' giờ ' : '') + m + ' phút';
}
function signal(rssi) { return rssi >= -55 ? 'rất tốt' : rssi >= -67 ? 'tốt' : rssi >= -75 ? 'trung bình' : 'yếu'; }
$('#freeram').addEventListener('click', async () => {
  const b = $('#freeram'); b.disabled = true; const r = await api('/api/free', {}); b.disabled = false;
  if (r) { toast(tr('Đã lấy lại') + ' ' + r.freed + ' MB · ' + tr('còn trống') + ' ' + r.after + ' MB'); loadHealth(); }
});
// Now: the live figures. History: what the TV Setup app on the TV has written down (drawn by stats.js).
let hmode = 'now';
async function loadHist(days) {
  $('#hhist').replaceChildren(el('div', { className: 'muted', textContent: tr('Đang đọc từ TV...') }));
  const d = await api('/api/stats?days=' + (days || 7)); if (d && hmode === 'hist') renderStats($('#hhist'), d, window.LANG === 'vi', loadHist);
}
document.querySelectorAll('#hmode button').forEach((b) => b.addEventListener('click', () => {
  hmode = b.dataset.m; document.querySelectorAll('#hmode button').forEach((x) => x.classList.toggle('on', x === b));
  $('#hhist').hidden = hmode !== 'hist'; $('#hnow').hidden = hmode !== 'now';
  if (hmode === 'hist') loadHist(); else loadHealth();
}));
async function loadHealth() {
  if (hmode !== 'now') return;
  if (healthBusy || ADB.state !== 'ready') return;
  healthBusy = true;
  const t0 = performance.now(), h = await api('/api/health', null, true);
  healthBusy = false;
  if (!h || currentTab !== 'health') return;
  $('.health').classList.add('ready');
  const r = h.remote || {}, vol = r.volume || {};
  $('#hwhen').textContent = 'cập nhật mỗi 5 giây';
  $('#hsum').replaceChildren(...kv([
    ['Nguồn', !r.connected ? 'không rõ (remote chưa kết nối)' : r.is_on ? 'đang bật' : 'màn hình tắt'],
    ['Ứng dụng đang mở', r.app ? ((typeof appName === 'function' && appName(r.app)) || r.app) : ''],
    ['Âm lượng', vol.max ? vol.level + ' / ' + vol.max + (vol.muted ? ' · đang tắt tiếng' : '') : ''],
    ['Độ trễ tới TV (ADB)', h.ping_ms + ' ms'],
    ['Luồng hình', screenOn ? ($('#screenmode').value === 'snap' ? 'ảnh chụp' : 'trực tiếp · ' + streamFps + ' khung/giây' + ($('#screenmode').value === 'hd' ? ' · nét' : ' · nhẹ')) : 'đang không xem'],
    ['TV chạy liên tục', h.uptime != null ? fmtUptime(h.uptime) : ''],
  ]));
  const load = [];
  if (h.cpu != null) load.push(meter('CPU' + (h.cores ? ' · ' + h.cores + ' nhân' : ''), h.cpu, h.cpu + '%'));
  if (h.mem_total) { const used = h.mem_total - h.mem_free; load.push(meter('RAM', used * 100 / h.mem_total, 'đã dùng ' + fmtSize(used) + ' / ' + fmtSize(h.mem_total))); }
  if (h.disk_total) { const used = h.disk_total - h.disk_free; load.push(meter('Bộ nhớ trong', used * 100 / h.disk_total, 'đã dùng ' + fmtSize(used) + ' / ' + fmtSize(h.disk_total))); }
  $('#hload').replaceChildren(...load);
  $('#htop').replaceChildren(...kv(h.top.map((p) => [(typeof appName === 'function' && META.has(p.name) && appName(p.name)) || p.name, p.cpu + '% CPU · ' + p.mem + '% RAM'])));
  const w = h.wifi;
  $('#hnet').replaceChildren(...kv([
    w ? ['Wi-Fi', w.ssid] : ['Wi-Fi', 'không kết nối'],
    w && w.rssi != null ? ['Tín hiệu', w.rssi + ' dBm · ' + signal(w.rssi)] : null,
    w && w.speed ? ['Tốc độ kết nối', w.speed + ' Mbps'] : null,
    w && w.freq ? ['Băng tần', (w.freq > 4000 ? '5 GHz' : '2.4 GHz') + ' · ' + w.freq + ' MHz'] : null,
    ...h.ips.map((i) => [i.if.startsWith('tun') ? 'IP Tailscale' : i.if.startsWith('eth') ? 'IP dây mạng' : 'IP trong nhà', i.ip]),
  ]));
  $('#hhw').replaceChildren(...kv([
    ['Máy', (h.maker + ' ' + h.model).trim()], ['Android', h.android + (h.sdk ? ' (SDK ' + h.sdk + ')' : '')], ['Bản vá bảo mật', h.patch],
    ['Chip', h.chip], ['Kiến trúc', h.abi], ['Số nhân CPU', h.cores], ['RAM', h.mem_total ? fmtSize(h.mem_total) : ''],
    ['Màn hình', h.screen + (h.fps ? ' · ' + h.fps + ' Hz' : '')], ['Mật độ điểm ảnh', h.density ? h.density + ' dpi' : ''],
  ]));
}
setInterval(() => { if (currentTab === 'health' && !document.hidden && !stopped) loadHealth(); }, 5000);
