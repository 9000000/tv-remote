// app names, icons and the apps tab
// ---------- app names + icons (read from the APK on the TV, cached by the server) ----------
const META = new Map(), metaWaiters = new Map(), metaQueue = [];
let metaBusy = 0;
function appName(pkg) { const m = META.get(pkg); return (m && m.label) || pkg; }
function pumpMeta() {
  while (metaBusy < 4 && metaQueue.length) {
    const pkg = metaQueue.shift(); metaBusy++;
    api('/api/app/meta?pkg=' + encodeURIComponent(pkg), null, true).then((m) => {
      metaBusy--; META.set(pkg, m || {}); (metaWaiters.get(pkg) || []).forEach((fn) => fn(m || {})); metaWaiters.delete(pkg); pumpMeta();
    });
  }
}
function onMeta(pkg, fn) {
  if (META.has(pkg)) { fn(META.get(pkg)); return; }
  if (!metaWaiters.has(pkg)) { metaWaiters.set(pkg, []); metaQueue.push(pkg); }
  metaWaiters.get(pkg).push(fn); pumpMeta();
}
function seedMeta(apps) { apps.forEach((a) => { if (a.meta && !META.has(a.pkg)) META.set(a.pkg, a.meta); }); }
// An app's picture, or a placeholder until there is one (and for good when there is none, or it fails to load): the first
// letter of its name on a colour taken from the package name, so apps without a logo still look apart.
function appIcon(pkg) {
  const box = el('div', { className: 'appicon placeholder' });
  const hue = [...pkg].reduce((h, c) => (h * 31 + c.charCodeAt(0)) % 360, 7);
  const letter = (name) => {
    box.replaceChildren(); box.className = 'appicon placeholder'; box.style.background = `hsl(${hue} 45% 32%)`;
    box.textContent = ((name || pkg.split('.').pop()).match(/\p{L}|\p{N}/u) || ['?'])[0].toUpperCase();
  };
  letter('');
  box.fill = (m) => {
    if (!m.icon) { letter(m.label); return; }
    const img = el('img', { src: '/api/app/icon?pkg=' + encodeURIComponent(pkg), alt: '', loading: 'lazy' });
    img.onerror = () => letter(m.label);
    img.onload = () => {
      box.replaceChildren(img); box.className = 'appicon'; box.style.background = m.bg || '';
      box.classList.toggle('adaptive', !!m.adaptive); box.classList.toggle('wide', !!m.banner);
    };
    letter(m.label); box.append(img); img.style.display = 'none';            // shown once it has loaded
    img.addEventListener('load', () => { img.style.display = ''; });
  };
  return box;
}
const lazyMeta = new IntersectionObserver((entries) => entries.forEach((e) => { if (e.isIntersecting) { lazyMeta.unobserve(e.target); e.target.loadMeta(); } }), { rootMargin: '300px' });

// quick launch tiles on the Remote tab
let quickLoaded = false;
async function loadQuick() {
  quickLoaded = true;
  const r = await api('/api/apps', null, true);
  if (!r) { quickLoaded = false; return; }
  seedMeta(r.apps);
  const missing = r.apps.filter((a) => !META.has(a.pkg));
  if (missing.length) {                                               // first run: wait for every name/icon, then draw once so tiles never jump
    let done = 0;
    const note = el('div', { className: 'muted' }); $('#quick').replaceChildren(note);
    const tick = () => { note.textContent = `Đang đọc tên và logo app từ TV (${done}/${missing.length}), chỉ lần đầu...`; };
    tick(); await Promise.all(missing.map((a) => new Promise((ok) => onMeta(a.pkg, () => { done++; tick(); ok(); }))));
  }
  const tiles = r.apps.slice().sort((a, b) => appName(a.pkg).localeCompare(appName(b.pkg))).map((a) => {
    const icon = appIcon(a.pkg); icon.fill(META.get(a.pkg) || {});
    return el('button', { className: 'tile', title: a.pkg, onclick: () => openOnTv(a.pkg, appName(a.pkg)) }, icon, el('span', { textContent: appName(a.pkg) }));
  });
  const gear = el('div', { className: 'appicon', textContent: '⚙' });
  tiles.push(el('button', { className: 'tile', onclick: () => openOnTv('settings', 'Cài đặt') }, gear, el('span', { textContent: 'Cài đặt TV' })));
  $('#quick').replaceChildren(...tiles); filterQuick();
}

// ---------- apps tab ----------
let appsLoaded = false, appsAll = [], appsInfo = new Map(), appsOffOpen = false;          // pkg -> { off, keep }
async function loadApps() {
  $('#appslist').replaceChildren(el('div', { className: 'muted', textContent: 'Đang tải...' }));
  const r = await api('/api/apps' + ($('#appssys').checked ? '?system=1' : ''));
  if (!r) { $('#appslist').replaceChildren(); return; }
  appsLoaded = true; appsAll = r.apps.map((a) => a.pkg); appsInfo = new Map(r.apps.map((a) => [a.pkg, { off: !!a.off, keep: !!a.keep }])); seedMeta(r.apps); renderApps();
}
function renderApps() {
  const q = $('#appsq').value.trim().toLowerCase();
  lazyMeta.disconnect();
  const rows = appsAll.filter((p) => !q || p.toLowerCase().includes(q) || appName(p).toLowerCase().includes(q))
    .sort((a, b) => (appsInfo.get(a)?.off ? 1 : 0) - (appsInfo.get(b)?.off ? 1 : 0) || appName(a).localeCompare(appName(b))).map((pkg) => {   // turned off: last
    const act = (name, icon, label, cls = '') => setLabel(el('button', { title: label, className: 'small ' + cls, onclick: async () => {
      if (name === 'uninstall' && !confirm('Gỡ cài đặt ' + appName(pkg) + ' khỏi TV?')) return;
      if (name === 'disable' && !confirm('Tắt ' + appName(pkg) + '? App biến khỏi TV cho tới khi bật lại ở mục “App đã tắt” cuối danh sách này.')) return;
      const r = await api('/api/app/' + name, { pkg }); if (r) { toast(label + ': ' + appName(pkg)); if (name !== 'launch' && name !== 'stop') { quickLoaded = false; loadApps(); loadQuick(); } }
    } }), icon, label);
    const icon = appIcon(pkg), title = el('span', { textContent: pkg.split('.').pop() });
    const info = appsInfo.get(pkg) || {};
    const row = el('div', { className: 'item' + (info.off ? ' off' : '') }, icon, el('div', { className: 'name' }, title, el('small', { textContent: (info.off ? '⏸ đã tắt · ' : '') + pkg })),
      el('div', { className: 'acts' }, ...(info.off ? [act('enable', '↩', 'Bật lại', 'primary')]
        : [act('launch', '▶', 'Mở', 'primary'), act('stop', '⏹', 'Dừng'), ...(info.keep ? [] : [act('disable', '⏸', 'Tắt app')])]),
        act('uninstall', '🗑', 'Gỡ', 'danger')));
    row.loadMeta = () => onMeta(pkg, (m) => { icon.fill(info.off ? { label: m.label } : m); if (m.label) title.textContent = m.label; });   // a disabled app has no picture to give
    if (META.has(pkg)) row.loadMeta(); else lazyMeta.observe(row);
    row.off = !!info.off;
    return row;
  });
  // the disabled apps (Optimize TV's, or "Disable" here) fold away at the end, so "Turn on" is at hand but out of the way
  const on = rows.filter((r) => !r.off), off = rows.filter((r) => r.off);
  const fold = off.length ? [el('details', { className: 'appsoff', open: !!q || appsOffOpen, ontoggle: (e) => { if (!q) appsOffOpen = e.target.open; } },
    el('summary', { textContent: '⏸ App đã tắt (' + off.length + ')' }), el('p', { className: 'muted', textContent: 'Đã tắt bằng Tối ưu TV hoặc nút Tắt app. Bấm ↩ để bật lại.' }), ...off)] : [];
  $('#appslist').replaceChildren(...(on.length ? on : [el('div', { className: 'muted', textContent: 'Không có app nào.' })]), ...fold);
}
$('#appsq').addEventListener('input', renderApps);
$('#appsf').addEventListener('submit', (e) => e.preventDefault());
$('#appssys').addEventListener('change', loadApps);
$('#appsreload').addEventListener('click', loadApps);


// the search box above the quick-launch grid: tiles whose name does not contain the words are hidden
function filterQuick() { const q = $('#quickq').value.trim().toLowerCase(); document.querySelectorAll('#quick .tile').forEach((b) => { b.hidden = !!q && !b.textContent.toLowerCase().includes(q); }); }
$('#quickq').addEventListener('input', filterQuick);

// 🤖 "which apps can go?": the TV's app lists and memory as a ready question for an AI (or a knowledgeable friend). Nobody can
// tell a Sony service from bloatware by its package name; an AI that knows Android TV mostly can. The text stays editable.
function aiPrompt(r) {
  const vi = window.LANG !== 'en';
  const named = (p) => r.labels && r.labels[p] ? `${p} (${r.labels[p]})` : p;
  const ours = ['dev.tvremote.setup', 'com.tailscale.ipn'].filter((p) => r.user.includes(p));
  const mem = r.memory.map((m) => `${m.pkg}: ${m.mb} MB`).join('\n');
  return (vi ? [
    `Mình có TV ${r.device}, Android ${r.android}, RAM ${r.ram_mb} MB. Mình muốn TV chạy mượt hơn bằng cách tắt (disable) hoặc gỡ những app không cần.`,
    'Xem danh sách bên dưới và chia thành 3 nhóm, mỗi app kèm một lý do ngắn:',
    '1. An toàn để tắt/gỡ (bloatware, demo, quảng cáo, app trùng chức năng)',
    '2. Tắt được nếu không dùng tính năng đó (nói rõ sẽ mất tính năng gì)',
    '3. Không được đụng (hệ thống, Google Play Services, bàn phím, launcher đang dùng, Chromecast, cài đặt, nguồn phát TV...)',
    'Ưu tiên "tắt" (pm disable-user --user 0) vì bật lại được; chỉ đề nghị gỡ với app tự cài. Cuối cùng xếp các app nên tắt theo mức RAM lấy lại được.',
    'Người nhà vẫn dùng: (điền vào, vd YouTube, Netflix, tìm kiếm bằng giọng nói, xem truyền hình bằng ăng-ten)',
    ours.length ? `Đừng đề xuất tắt ${ours.join(', ')}: mình dùng để điều khiển TV.` : '',
    '', '== App đang tốn RAM nhất lúc này ==', mem,
    '', `== App tự cài (${r.user.length}) ==`, r.user.map(named).join('\n'),
    '', `== App hệ thống đang bật (${r.system.length}) ==`, r.system.join('\n'),
    '', `== App đã tắt sẵn (${r.disabled.length}) ==`, r.disabled.join('\n') || '(không có)',
  ] : [
    `I have a ${r.device} TV, Android ${r.android}, ${r.ram_mb} MB of RAM. I want it to run smoother by disabling or uninstalling apps it does not need.`,
    'Look at the lists below and sort the apps into 3 groups, each with a short reason:',
    '1. Safe to disable/uninstall (bloatware, demos, ads, duplicates)',
    '2. Can be disabled if that feature is not used (say which feature is lost)',
    '3. Do not touch (system, Google Play Services, keyboard, the launcher in use, Chromecast, settings, TV inputs...)',
    'Prefer "disable" (pm disable-user --user 0), which can be undone; suggest uninstalling only for user-installed apps. Finally, rank the apps to disable by the memory they would give back.',
    'My family still uses: (fill in, e.g. YouTube, Netflix, voice search, antenna TV)',
    ours.length ? `Do not suggest disabling ${ours.join(', ')}: I use them to control the TV.` : '',
    '', '== Apps using the most memory right now ==', mem,
    '', `== User-installed apps (${r.user.length}) ==`, r.user.map(named).join('\n'),
    '', `== System apps that are on (${r.system.length}) ==`, r.system.join('\n'),
    '', `== Apps already disabled (${r.disabled.length}) ==`, r.disabled.join('\n') || '(none)',
  ]).filter((l, i, a) => l !== '' || a[i - 1] !== '').join('\n');
}
$('#aiapps').addEventListener('click', async () => {
  toast('Đang lấy danh sách app...');
  const r = await api('/api/apps/report'); if (!r) return;
  $('#aitext').value = aiPrompt(r); $('#aidlg').showModal();
});
$('#aiclose').addEventListener('click', () => $('#aidlg').close());
$('#aicopy').addEventListener('click', async () => {
  const t = $('#aitext'); let ok = false;
  try { await navigator.clipboard.writeText(t.value); ok = true; } catch {}
  if (!ok) { t.focus(); t.select(); try { ok = document.execCommand('copy'); } catch {} }   // plain-HTTP pages (the TV's) have no clipboard API
  toast(ok ? 'Đã chép. Dán vào ChatGPT, Claude hay Gemini.' : 'Không chép tự động được: chữ đã được bôi đen, bấm giữ để chép.');
});

