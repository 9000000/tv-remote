// health tab: a snapshot of the TV (load, memory, Wi-Fi, hardware), refreshed while the tab is open
let healthBusy = false;
const kv = (pairs) => pairs.filter((p) => p && p[1] != null && p[1] !== '').flatMap(([k, v]) => [el('div', { className: 'k', textContent: k }), el('div', { className: 'v', textContent: String(v) })]);
// The network and hardware cards hardly change: redrawn only when their figures do, so an IP or SSID can be selected and copied.
const drawn = new Map();
function redrawIfChanged(sel, pairs) {
  const sig = JSON.stringify(pairs) + window.LANG; if (drawn.get(sel) === sig) return;
  drawn.set(sel, sig); $(sel).replaceChildren(...kv(pairs));
}
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
  if (r) { toast(tr('Đã lấy lại') + ' ' + r.freed + ' MB · ' + tr('còn trống') + ' ' + r.after + ' MB'); loadHealth(); loadProcs(true); }
});
// The task manager: the apps holding memory, what each is doing, and Stop where stopping cannot hurt the TV (the server
// decides which: Manage.keepWhy). dumpsys meminfo is slow on a TV, so at most every 15 s while the tab is open.
const PROC_STATE = { front: 'Đang hiện', bg: 'Chạy ngầm', home: 'Màn hình chính', cached: 'Đang nghỉ', system: 'Hệ thống' };
const PROC_KEEP = { us: 'TV Remote cần app này', home: 'Màn hình chính của TV', keyboard: 'Bàn phím của TV', system: 'Một phần của Android' };
let procsAt = 0, procsBusy = false;
async function loadProcs(now) {
  if (procsBusy || (!now && Date.now() - procsAt < 45000)) return;   // dumpsys meminfo costs the TV a second or more: ↻ asks at once
  procsBusy = true; procsAt = Date.now();
  const r = await api('/api/procs', null, true);
  procsBusy = false;
  if (!r || !r.apps) return;
  const row = (a) => {
    const icon = appIcon(a.pkg), name = META.get(a.pkg)?.label || a.label;
    const title = el('span', { textContent: name });
    if (a.keep === 'system') icon.fill({ label: name });                 // Android's own: no picture worth asking for
    else onMeta(a.pkg, (m) => { icon.fill(m.label ? m : { ...m, label: name }); if (m.label) title.textContent = m.label; });
    const stop = async () => {
      if (a.state === 'front' && !await ask('Dừng ' + name + '? App đang hiện trên TV.', { ok: 'Dừng' })) return;
      if (await api('/api/procs/kill', { pkg: a.pkg })) { toast('Đã dừng app: ' + name); setTimeout(() => loadProcs(true), 800); }
    };
    return el('div', { className: 'item' }, icon, el('div', { className: 'name' }, title,
      el('small', { textContent: a.mb + ' MB · ' + tr(PROC_STATE[a.state] || a.state) })),
      el('div', { className: 'acts' }, a.keep ? el('span', { className: 'tag keep', textContent: tr('🔒 Cần giữ'), title: tr(PROC_KEEP[a.keep] || '') }) : act('⏹ Dừng', stop)));
  };
  const apps = r.apps.filter((a) => a.keep !== 'system'), sys = r.apps.filter((a) => a.keep === 'system');   // Android's own, folded away
  $('#procs').replaceChildren(...apps.map(row), ...(sys.length ? [el('details', { className: 'appsoff' },
    el('summary', { textContent: tr('Hệ thống Android') + ' (' + sys.length + ')' }), ...sys.map(row))] : []));
}
$('#procsre').addEventListener('click', () => loadProcs(true));

// Now: the live figures. History: what the TV Setup app on the TV has written down (drawn by stats.js).
let hmode = 'now';
async function loadHist(days) {
  $('#hhist').replaceChildren(el('div', { className: 'muted', textContent: tr('Đang đọc từ TV...') }));
  const d = await api('/api/stats?days=' + (days || 7)); if (d && hmode === 'hist') renderStats($('#hhist'), d, window.LANG === 'vi', loadHist);
}
document.querySelectorAll('#hmode button').forEach((b) => b.addEventListener('click', () => {
  hmode = b.dataset.m; document.querySelectorAll('#hmode button').forEach((x) => x.classList.toggle('on', x === b));
  $('#hhist').hidden = hmode !== 'hist'; $('#hnow').hidden = hmode !== 'now'; $('#herr').hidden = hmode !== 'err'; $('#hcaps').hidden = hmode !== 'caps';
  if (hmode === 'hist') loadHist(); else if (hmode === 'err') loadErrors(); else if (hmode === 'caps') loadCaps(); else loadHealth();
}));

// What the TV can play and do (Caps.kt asks the TV's own Android): first in plain words, then the details, and a copy as text
// for a forum or a shop ("will this TV play Dolby Vision?").
let capsFor = '', capsText = '';
async function loadCaps() {
  const box = $('#hcaps');
  if (capsFor === (STATE.host || '') && capsText) return;
  box.replaceChildren(UI.say(tr('Đang hỏi TV, lần đầu mất vài giây...')));
  const r = await api('/api/caps'); if (hmode !== 'caps') return;
  if (!r) { box.replaceChildren(); return; }
  if (!r.text.trim()) {
    box.replaceChildren(UI.say(CORE === 'pc'
      ? 'Bản TV Remote trên máy tính này chưa đọc được khả năng TV. Cập nhật lên bản mới nhất rồi mở lại.' : 'TV không trả lời câu hỏi này. Thử lại sau.'));
    return;
  }
  capsFor = (STATE.host || ''); capsText = r.text; drawCaps(capsRead(r.text));
}
function drawCaps(c) {
  const vid = (mime) => c.video.find((v) => v.mime === mime), feat = (f) => c.features.includes(f);
  const biggest = c.modes.map((m) => m.split('@')[0].split('x').map(Number)).reduce((a, b) => (b[0] * b[1] > a[0] * a[1] ? b : a), [0, 0]);
  const fourK = (v) => v && v.hw && Math.max(v.w, v.h) >= 3840;
  const hdrs = { dv: 'Dolby Vision', hdr10: 'HDR10', 'hdr10+': 'HDR10+', hlg: 'HLG' };
  const pass = [...new Set(c.outputs.filter((o) => /hdmi|arc|earc|optical/.test(o.dev)).flatMap((o) => o.enc).filter((e) => e !== 'PCM' && e !== 'bitstream'))];
  const yes = (ok, text) => el('li', { className: ok ? 'yes' : 'no', textContent: (ok ? '✓ ' : '✗ ') + tr(text) });
  const plain = el('ul', { className: 'capsplain' },
    biggest[0] ? yes(true, 'Màn hình xuất tới ' + (sizeName(biggest[0], biggest[1]) || biggest.join('x'))) : null,
    yes(fourK(vid('video/x-vnd.on2.vp9')) || fourK(vid('video/av01')), 'YouTube 4K (cần VP9 hoặc AV1 bằng phần cứng)'),
    yes(fourK(vid('video/hevc')), 'Phim 4K H.265 (HEVC): Netflix, phim tải về'),
    yes(!!(vid('video/av01') || {}).hw, 'AV1 bằng phần cứng (YouTube đỡ tốn mạng)'),
    yes(c.hdr.length > 0, c.hdr.length ? 'HDR: ' + c.hdr.map((h) => hdrs[h] || h).join(', ') : 'HDR'),
    yes(c.video.some((v) => v.hw && v.secure), 'Giải mã bảo mật (Netflix, Disney+ chất lượng cao)'),
    yes(pass.length > 0, pass.length ? 'Đưa âm thanh ' + pass.join(', ') + ' ra loa/amply qua HDMI' : 'Đưa âm thanh Dolby/DTS ra loa/amply qua HDMI'),
    c.sdk ? yes(c.sdk >= 30, 'Nghe tiếng TV trên điện thoại (🎧, cần Android 11)') : null,
    c.sdk ? yes(c.sdk >= 28, 'Lọc DNS riêng (cần Android 9)') : null,
    yes(feat('android.software.picture_in_picture'), 'Hình trong hình'),
    yes(feat('android.hardware.hdmi.cec'), 'HDMI-CEC (một remote cho cả TV và đầu thu, loa)'),
    yes(feat('android.hardware.usb.host'), 'Cắm USB (ổ cứng, chuột, bàn phím)'),
    yes(feat('android.hardware.bluetooth'), 'Bluetooth (tai nghe, tay cầm)'));
  const vrow = (v) => [v.name, [tr(v.hw ? 'phần cứng' : 'phần mềm'), sizeName(v.w, v.h), ...v.flags.filter((f) => f !== 'soft' && f !== 'tunnel').map((f) => ({ '10bit': '10-bit', hdr10: 'HDR10', 'hdr10+': 'HDR10+', secure: tr('bảo mật') }[f] || f))].filter(Boolean).join(' · ')];
  const card = (title, ...kids) => UI.card(title, {}, ...kids);
  $('#hcaps').replaceChildren(
    UI.card('🎬 TV này làm được', { tools: [el('button', { className: 'small', onclick: () => copyText(capsSummary(c)) }, el('span', { className: 'ico', textContent: '📋' }), el('span', { className: 'lbl', textContent: ' Chép' }))] }, plain),
    card('🎞️ Giải mã video', el('div', { className: 'kv' }, ...kv(c.video.map(vrow)))),
    card('🔊 Âm thanh', el('div', { className: 'kv' }, ...kv([
      ['Giải mã', c.audio.map((a) => a.name).join(', ')],
      ...c.outputs.filter((o) => o.enc.length).map((o) => [{ hdmi: 'HDMI', arc: 'HDMI ARC', earc: 'HDMI eARC', optical: 'Quang (optical)', speaker: 'Loa TV', bluetooth: 'Bluetooth' }[o.dev] || o.dev, o.enc.join(', ') || '—'])]))),
    c.modes.length ? card('🖥️ Chế độ màn hình', el('p', { className: 'note', textContent: c.modes.map((m) => m.replace(/\.0$/, '') + ' Hz').join(' · ') })) : null);
}
// the copy: the same plain lines, then the details, so it reads well pasted anywhere
function capsSummary(c) {
  const lines = [...$('#hcaps .capsplain').children].map((li) => li.textContent);
  return [tr('Khả năng TV') + (c.release ? ' · Android ' + c.release : ''), ...lines, '',
    ...c.video.map((v) => v.name + ': ' + (v.hw ? 'hw' : 'sw') + ' ' + v.w + 'x' + v.h + (v.flags.length ? ' ' + v.flags.join(',') : '')),
    'Audio: ' + c.audio.map((a) => a.name).join(', '), ...c.outputs.map((o) => 'Out ' + o.dev + ': ' + o.enc.join(', ')),
    'HDR: ' + (c.hdr.join(', ') || '-'), 'Modes: ' + c.modes.join(', ')].join('\n');
}

// 🗂️ all settings, under the Setup tab: read the first time it is opened (again after a failed read), searched as typed
let setRows = [], setBusy = false;
let setFilter = 'all';                                               // all · edit (has a control) · view (locked or nothing to set)
const setOpen = new Set();                                            // groups opened by hand: kept open as the list redraws
async function loadSettings() {
  if (setRows.length || setBusy || ADB.state !== 'ready') return;
  setBusy = true;
  $('#setlistbody').replaceChildren(el('div', { className: 'muted', textContent: tr('Đang đọc từ TV...') }));
  const r = await api('/api/settings'); setBusy = false;
  if (!r) { $('#setlistbody').replaceChildren(); return; }
  setRows = settingsRead(r.text); drawSettings();
}
$('#setlist').addEventListener('toggle', (e) => { if (e.target.open) loadSettings(); });
function drawSettings() {
  const q = $('#setlistq').value.trim().toLowerCase(), vi = window.LANG !== 'en';
  const all = setRows.map((s) => { const e = settingEdit(s); return { s, i: settingInfo(s, vi), edit: !e.locked && !!e.kind, locked: !!e.locked }; })
    .filter(({ s, i }) => !q || (i.title + ' ' + s.name + '=' + s.value + ' ' + i.value).toLowerCase().includes(q));
  const counts = { all: all.length, edit: all.filter((r) => r.edit).length }; counts.view = counts.all - counts.edit;
  document.querySelectorAll('#setfilter button').forEach((b) => { const f = b.dataset.f; b.classList.toggle('on', f === setFilter); b.dataset.n = counts[f]; });
  const rows = all.filter((r) => setFilter === 'all' || (setFilter === 'edit') === r.edit);
  const row = ({ s, i, locked }) => el('div', { className: 'setrow', title: s.table + ' ' + s.name + '=' + s.value,
    onclick: (e) => { e.currentTarget.classList.toggle('full'); if (locked) toast(tr('🔒 Chỉ xem: đổi sai có thể mất kết nối tới TV')); } },   // a long value opens in full on a tap
    el('div', { className: 'setname' }, el('span', { textContent: i.title }), el('small', { className: 'mono', textContent: s.name })),
    setControl(s, i, vi));
  const named = (div, pkgs) => pkgs.forEach((p) => onMeta(p, (m) => {   // "com.google.android.apps.tv.dreamx" -> the app's own name
    if (m && m.label && m.label !== p.slice(p.lastIndexOf('.') + 1)) { div.textContent = div.textContent.split(', ').map((x) => x === p ? m.label : x).join(', '); div.classList.toggle('long', div.textContent.length > 24); }
  }));
  const parts = [];
  for (const g of Object.keys(SET_GROUPS)) {
    const mine = rows.filter((r) => r.i.group === g); if (!mine.length) continue;
    // every group folded, so the list fits and the search box stays in reach; a search opens the groups it finds something in
    const made = g !== 'other' ? mine.sort((a, b) => a.i.order - b.i.order).map((r) => { const d = row(r); if (r.i.pkgs) named(d.lastChild, r.i.pkgs); return d; })
      : mine.slice(0, 400).map(row);                                 // Android's hundreds of internal keys
    const box = el('details', { className: 'setgroupbox', open: !!q || setOpen.has(g) }, el('summary', { textContent: mine[0].i.groupName + ' · ' + mine.length }), ...made);
    box.addEventListener('toggle', () => { if (!q) { if (box.open) setOpen.add(g); else setOpen.delete(g); } });
    parts.push(box);
  }
  $('#setlistbody').replaceChildren(...parts);
}
// The value, or what changes it: a toggle, a choice, ✎ for a name or a number; 🔒 where a change could cut the TV off
function setControl(s, i, vi) {
  const e = settingEdit(s), shown = el('div', { className: 'setval' + (i.known ? '' : ' mono') + (i.value.length > 24 ? ' long' : ''), textContent: i.value });
  const stop = (n) => { n.addEventListener('click', (ev) => ev.stopPropagation()); return n; };
  const back = s.was !== undefined && s.was !== s.value && s.was !== 'null'
    ? stop(el('button', { type: 'button', className: 'small', title: tr('Trả lại') + ': ' + s.was, textContent: '↺', onclick: () => setSetting(s, s.was) })) : null;
  let ctl = null;
  if (e.locked) { shown.textContent = '🔒 ' + i.value; shown.title = tr('Chỉ xem: đổi sai có thể mất kết nối tới TV'); }
  else if (e.kind === 'bool') {
    const on = s.value !== e.off && s.value !== 'null';
    ctl = el('button', { type: 'button', className: 'small' + (on ? ' primary' : ''), textContent: settingWord(s, on ? e.on : e.off, vi), onclick: () => setSetting(s, on ? e.off : e.on) });
  } else if (e.kind === 'pick') {
    const vals = e.values.some((v) => Number(v) === Number(s.value)) ? e.values : [s.value, ...e.values];
    ctl = el('select', { className: 'setpick', onchange: (ev) => setSetting(s, ev.target.value) },
      ...vals.map((v) => el('option', { value: v, textContent: settingWord(s, v, vi), selected: Number(v) === Number(s.value) })));
  } else if (e.kind === 'text') {
    ctl = el('button', { type: 'button', className: 'small', title: tr('Đổi'), textContent: (s.value === 'null' ? '—' : i.value) + ' ✎', onclick: async () => {
      const v = await ask(e.tech ? tr('Giá trị mới (mục kỹ thuật: đổi sai có thể làm TV chạy lạ)') : tr('Giá trị mới'), { value: s.value === 'null' ? '' : s.value, ok: 'Đổi' });
      if (v !== null && v.trim() !== s.value) setSetting(s, v.trim());
    } });
  }
  if (!ctl) return back ? el('div', { className: 'setctl' }, back, shown) : shown;
  return el('div', { className: 'setctl' }, ...(back ? [back] : []), stop(ctl));
}
async function setSetting(s, value) {
  if (!/^[A-Za-z0-9 ._:+-]{0,100}$/.test(value)) { toast(tr('Chỉ dùng chữ không dấu, số và . _ : + -')); return drawSettings(); }
  const r = await api('/api/settings', { table: s.table, name: s.name, value });
  if (!r || !/\bok\b/.test(r.text || '')) { if (r) toast('Lỗi: ' + (r.text || '').trim()); return drawSettings(); }
  if (s.was === undefined) s.was = s.value;
  s.value = value; drawSettings(); toast(tr('✓ Đã đổi'));
}
$('#setlistq').addEventListener('input', drawSettings);
$('#setfilter').addEventListener('click', (e) => { const b = e.target.closest('button'); if (b) { setFilter = b.dataset.f; drawSettings(); } });
$('#setlistf').addEventListener('submit', (e) => e.preventDefault());
$('#setlistcopy').addEventListener('click', () => {
  const q = $('#setlistq').value.trim().toLowerCase();
  copyText(setRows.filter((s) => !q || (s.name + '=' + s.value).toLowerCase().includes(q)).map((s) => s.table + ' ' + s.name + '=' + s.value).join('\n'));
});

// Errors: Android notes every app that stops or stops answering in its "dropbox" (kept about 3 days); the core sends those notes
// cut to the lines that matter (CRASHES in Manage.kt), and this groups them per app, newest first.
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
  box.replaceChildren(UI.say(tr('Đang đọc từ TV...')));
  const r = await api('/api/crashes'); if (!r || hmode !== 'err') return;
  const groups = new Map();
  for (const e of parseCrashes(r.text)) {
    const key = e.sys ? 'android' : (e.proc || '?').split(':')[0];
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(e);
  }
  const list = [...groups].map(([pkg, es]) => ({ pkg, es: es.sort((a, b) => b.at.localeCompare(a.at)) })).sort((a, b) => b.es[0].at.localeCompare(a.es[0].at));
  const reload = el('button', { type: 'button', className: 'small', textContent: '↻ Đọc lại', onclick: loadErrors });
  const card = UI.card('🐞 App bị dừng, bị treo', { tools: [reload] });
  if (!list.length) card.append(el('p', { className: 'note', textContent: 'Không có app nào bị dừng hay bị treo gần đây. 👍' }));
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
    d.append(el('button', { type: 'button', className: 'small crashcopy', textContent: '📋 Chép để gửi hỏi',
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
  loadProcs();
  const cell = (cls, text) => el('div', { className: cls, textContent: text });   // CPU and RAM said once, as column heads
  $('#htop').replaceChildren(...(h.top.length ? [cell('h', 'Tiến trình'), cell('h n', 'CPU'), cell('h n', 'RAM')] : []),
    ...h.top.flatMap((p) => [cell('k', (typeof appName === 'function' && META.has(p.name) && appName(p.name)) || p.name), cell('v n', p.cpu + '%'), cell('v n', p.mem + '%')]));
  const w = h.wifi;
  redrawIfChanged('#hnet', [
    w ? ['Wi-Fi', w.ssid] : ['Wi-Fi', 'không kết nối'],
    w && w.rssi != null ? ['Tín hiệu', w.rssi + ' dBm · ' + signal(w.rssi)] : null,
    w && w.speed ? ['Tốc độ kết nối', w.speed + ' Mbps'] : null,
    w && w.freq ? ['Băng tần', (w.freq > 4000 ? '5 GHz' : '2.4 GHz') + ' · ' + w.freq + ' MHz'] : null,
    ...h.ips.map((i) => [i.if.startsWith('tun') ? 'IP Tailscale' : i.if.startsWith('eth') ? 'IP dây mạng' : 'IP trong nhà', i.ip]),
    w && w.rssi != null ? ['Lời khuyên', wifiTip(w)] : null,
  ]);
  redrawIfChanged('#hhw', [
    ['Máy', (h.maker + ' ' + h.model).trim()], ['Android', h.android + (h.sdk ? ' (SDK ' + h.sdk + ')' : '')], ['Bản vá bảo mật', h.patch],
    ['Chip', h.chip], ['Kiến trúc', h.abi], ['Số nhân CPU', h.cores], ['RAM', h.mem_total ? fmtSize(h.mem_total) : ''],
    ['Màn hình', h.screen + (h.fps ? ' · ' + h.fps + ' Hz' : '')], ['Mật độ điểm ảnh', h.density ? h.density + ' dpi' : ''],
  ]);
}
setInterval(() => { if (currentTab === 'health' && !document.hidden && !stopped) loadHealth(); }, 5000);
