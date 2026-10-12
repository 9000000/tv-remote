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
function appIcon(pkg) {
  const box = el('div', { className: 'appicon', textContent: pkg.split('.').pop()[0].toUpperCase() });
  box.fill = (m) => {
    if (!m.icon) { if (m.label) box.textContent = m.label[0].toUpperCase(); return; }
    box.textContent = ''; box.classList.toggle('adaptive', !!m.adaptive); if (m.bg) box.style.background = m.bg;
    box.append(el('img', { src: '/api/app/icon?pkg=' + encodeURIComponent(pkg), alt: '', loading: 'lazy' }));
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
  $('#quick').replaceChildren(...tiles);
}

// ---------- apps tab ----------
let appsLoaded = false, appsAll = [];
async function loadApps() {
  $('#appslist').replaceChildren(el('div', { className: 'muted', textContent: 'Đang tải...' }));
  const r = await api('/api/apps' + ($('#appssys').checked ? '?system=1' : ''));
  if (!r) { $('#appslist').replaceChildren(); return; }
  appsLoaded = true; appsAll = r.apps.map((a) => a.pkg); seedMeta(r.apps); renderApps();
}
function renderApps() {
  const q = $('#appsq').value.trim().toLowerCase();
  lazyMeta.disconnect();
  const rows = appsAll.filter((p) => !q || p.toLowerCase().includes(q) || appName(p).toLowerCase().includes(q))
    .sort((a, b) => appName(a).localeCompare(appName(b))).map((pkg) => {
    const act = (name, label, cls = '') => el('button', { textContent: label, className: 'small ' + cls, onclick: async () => {
      if (name === 'uninstall' && !confirm('Gỡ cài đặt ' + appName(pkg) + ' khỏi TV?')) return;
      const r = await api('/api/app/' + name, { pkg }); if (r) { toast(label + ': ' + appName(pkg)); if (name === 'uninstall') { quickLoaded = false; loadApps(); loadQuick(); } }
    } });
    const icon = appIcon(pkg), title = el('span', { textContent: pkg.split('.').pop() });
    const row = el('div', { className: 'item' }, icon, el('div', { className: 'name' }, title, el('small', { textContent: pkg })),
      el('div', { className: 'acts' }, act('launch', 'Mở', 'primary'), act('stop', 'Dừng'), act('uninstall', 'Gỡ', 'danger')));
    row.loadMeta = () => onMeta(pkg, (m) => { icon.fill(m); if (m.label) title.textContent = m.label; });
    if (META.has(pkg)) row.loadMeta(); else lazyMeta.observe(row);
    return row;
  });
  $('#appslist').replaceChildren(...(rows.length ? rows : [el('div', { className: 'muted', textContent: 'Không có app nào.' })]));
}
$('#appsq').addEventListener('input', renderApps);
$('#appsf').addEventListener('submit', (e) => e.preventDefault());
$('#appssys').addEventListener('change', loadApps);
$('#appsreload').addEventListener('click', loadApps);

