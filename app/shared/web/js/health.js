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
// One line on what to do about the Wi-Fi, the usual cause of stutter and a slow remote.
function wifiTip(w) {
  if (w.rssi < -75) return 'Sóng yếu: video dễ giật. Đặt router gần TV hơn, bỏ vật cản kim loại, hoặc cắm dây mạng.';
  if (w.rssi < -67) return 'Sóng tạm được. Video 4K có thể giật; dây mạng hoặc router gần hơn sẽ ổn định hơn.';
  if (w.freq && w.freq < 4000) return 'Sóng tốt nhưng đang ở 2.4 GHz (chậm, dễ nhiễu). Nếu router có mạng 5 GHz, cho TV dùng mạng đó.';
  return 'Sóng tốt, không cần làm gì.';
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
  $('#hhist').hidden = hmode !== 'hist'; $('#hnow').hidden = hmode !== 'now'; $('#herr').hidden = hmode !== 'err';
  if (hmode === 'hist') loadHist(); else if (hmode === 'err') loadErrors(); else loadHealth();
}));

// Errors: Android notes every app that stops or stops answering in its "dropbox" (kept about 3 days); the core sends those notes
// cut to the lines that matter (CRASHES in server.py / Manage.kt), and this groups them per app, newest first.
function parseCrashes(text) {
  const out = []; let e = null, sep = false;
  for (const line of text.split('\n')) {
    if (line.startsWith('=====')) { sep = true; continue; }
    const h = sep && line.match(/^(\d{4}-\d\d-\d\d \d\d:\d\d):\d\d (\S+)/); sep = false;
    if (h) { e = { at: h[1], kind: /anr/.test(h[2]) ? 'anr' : /native/.test(h[2]) ? 'native' : 'crash', sys: h[2].startsWith('system_server'), proc: '', why: '', lines: [] }; out.push(e); continue; }
    if (!e) continue;
    if (line.startsWith('Process: ')) { e.proc = line.slice(9).trim(); continue; }
    if (line.startsWith('Subject: ') || (!line.startsWith('\tat ') && !e.why) || line.startsWith('Abort message')) e.why = line.replace(/^Subject: /, '').trim();
    if (e.lines.length < 20) e.lines.push(line.replace(/^\t/, '    '));
  }
  return out;
}
const crashAt = (at) => at.slice(8, 10) + '/' + at.slice(5, 7) + ' ' + at.slice(11);
const KIND = { crash: 'bị dừng:', anr: 'bị treo:', native: 'bị dừng:' };
const PLAIN = [                                                       // the usual reasons in words; the exact note stays below
  [/^Input dispatching timed out/, 'không phản hồi khi bấm phím'], [/executing service/, 'một dịch vụ chạy quá lâu'],
  [/^Broadcast of Intent/, 'xử lý tin báo hệ thống quá lâu'], [/ContentProvider not responding/, 'không trả lời app khác'],
  [/^signal 11 /, 'lỗi đọc ghi bộ nhớ (SIGSEGV)'], [/^signal 6 /, 'tự dừng vì lỗi (SIGABRT)'], [/^(java\.lang\.)?OutOfMemoryError/, 'hết bộ nhớ'],
];
const plain = (why) => (PLAIN.find(([re]) => re.test(why)) || [])[1] || why;
async function copyText(t) {
  let ok = false;
  try { await navigator.clipboard.writeText(t); ok = true; } catch {}
  if (!ok) { const a = el('textarea', { value: t }); document.body.append(a); a.select(); try { ok = document.execCommand('copy'); } catch {} a.remove(); }  // plain-HTTP pages (the TV's) have no clipboard API
  toast(ok ? 'Đã chép' : 'Không chép được');
}
async function loadErrors() {
  const box = $('#herr');
  box.replaceChildren(el('section', { className: 'card' }, el('div', { className: 'muted', textContent: tr('Đang đọc từ TV...') })));
  const r = await api('/api/crashes'); if (!r || hmode !== 'err') return;
  const groups = new Map();
  for (const e of parseCrashes(r.text)) {
    const key = e.sys ? 'android' : (e.proc || '?').split(':')[0];
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(e);
  }
  const list = [...groups].map(([pkg, es]) => ({ pkg, es: es.sort((a, b) => b.at.localeCompare(a.at)) })).sort((a, b) => b.es[0].at.localeCompare(a.es[0].at));
  const reload = el('button', { type: 'button', textContent: '↻ Đọc lại', onclick: loadErrors });
  const card = el('section', { className: 'card' }, el('div', { className: 'card-head' }, el('h2', { textContent: '🐞 App bị dừng, bị treo' }), reload),
    el('p', { className: 'muted', style: 'margin:0 0 8px', textContent: 'Android tự ghi lại mỗi lần một app trên TV bị dừng hoặc không phản hồi, và giữ trong khoảng 3 ngày.' }));
  if (!list.length) card.append(el('p', { textContent: 'Không có app nào bị dừng hay bị treo gần đây. 👍' }));
  for (const { pkg, es } of list) {
    const n = { crash: 0, anr: 0 }; es.forEach((e) => { n[e.kind === 'anr' ? 'anr' : 'crash']++; });
    const name = el('b', { textContent: pkg === 'android' ? 'Hệ thống Android' : appName(pkg) });
    if (pkg !== 'android') onMeta(pkg, (m) => { if (m.label) name.textContent = m.label; });
    const icon = pkg === 'android' ? el('div', { className: 'appicon placeholder', textContent: '⚙️' }) : appIcon(pkg);
    if (pkg !== 'android') onMeta(pkg, (m) => icon.fill(m));
    const counts = [n.crash ? n.crash + ' lần bị dừng' : '', n.anr ? n.anr + ' lần bị treo' : ''].filter(Boolean).join(' · ');
    const d = el('details', { className: 'crash' }, el('summary', {}, icon,
      el('div', { className: 't' }, name, el('small', { textContent: counts + ' · lần cuối ' + crashAt(es[0].at) })),
      el('span', { className: 'n', textContent: String(es.length) })));
    const whys = new Map();                                            // the same failure again and again: shown once, with how often
    for (const e of es) { const k = e.kind + e.why; if (!whys.has(k)) whys.set(k, { e, n: 0 }); whys.get(k).n++; }
    for (const { e, n: times } of [...whys.values()].slice(0, 5)) {
      d.append(el('div', { className: 'why' }, el('span', { textContent: KIND[e.kind] }), ' ', el('b', { textContent: plain(e.why) || '?' }),
        el('small', {}, ' · ', el('span', { textContent: times + ' lần · lần cuối ' + crashAt(e.at) }))));
      const text = (e.proc ? 'Process: ' + e.proc + '\n' : '') + e.lines.join('\n');
      d.append(el('pre', { className: 'term', textContent: text }));
    }
    d.append(el('button', { type: 'button', textContent: '📋 Chép để gửi hỏi', style: 'margin:0 0 12px;padding:0 14px',
      onclick: () => copyText([...whys.values()].map(({ e, n: t }) => `[${e.at}] ${pkg} ${e.kind} ×${t}\n${e.lines.join('\n')}`).join('\n\n')) }));
    card.append(d);
  }
  box.replaceChildren(card);
}
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
    ['Luồng hình', screenOn ? ($('#screenmode').value === 'snap' ? 'ảnh chụp' : 'trực tiếp · ' + streamFps + ' khung/giây' + ($('#screenmode').value === 'hd' ? ' · nét nhất' : ' · ' + $('#screenmode').value + 'p')) : 'đang không xem'],
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
    w && w.rssi != null ? ['Lời khuyên', wifiTip(w)] : null,
  ]));
  $('#hhw').replaceChildren(...kv([
    ['Máy', (h.maker + ' ' + h.model).trim()], ['Android', h.android + (h.sdk ? ' (SDK ' + h.sdk + ')' : '')], ['Bản vá bảo mật', h.patch],
    ['Chip', h.chip], ['Kiến trúc', h.abi], ['Số nhân CPU', h.cores], ['RAM', h.mem_total ? fmtSize(h.mem_total) : ''],
    ['Màn hình', h.screen + (h.fps ? ' · ' + h.fps + ' Hz' : '')], ['Mật độ điểm ảnh', h.density ? h.density + ' dpi' : ''],
  ]));
}
setInterval(() => { if (currentTab === 'health' && !document.hidden && !stopped) loadHealth(); }, 5000);
