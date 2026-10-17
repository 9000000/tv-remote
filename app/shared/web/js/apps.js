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
// The remote's "now open" line: the app on the TV's screen, from its remote link or ADB; hidden when the TV does not say.
let nowApp = '';
function showNowApp(id, label) {
  if (id + '|' + (label || '') === nowApp) return; nowApp = id + '|' + (label || '');
  const box = $('#nowapp'); box.hidden = !id; if (!id) return;
  const pkg = /^[\w]+(\.[\w]+)+$/.test(id) && !label, icon = appIcon(id), name = el('b', { textContent: label || appName(id) });
  box.replaceChildren(el('small', { textContent: 'Đang mở' }), icon, name);
  if (!pkg) icon.fill({ label: label || id });                         // an LG / Roku id: its name's first letter
  if (pkg) onMeta(id, (m) => { if (nowApp.startsWith(id + '|')) { icon.fill(m); name.textContent = appName(id); } });
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
    const img = el('img', { src: '/api/app/icon?pkg=' + encodeURIComponent(pkg), alt: '' });   // not lazy: hidden until it loads, a lazy image never would (Chrome)
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
let quickLoaded = false, quickApps = [];                              // quickApps: the TV's apps, for the Open on the TV menu too
async function loadQuick() {
  quickLoaded = true;
  const r = await api('/api/apps', null, true);
  if (!r) { quickLoaded = false; return; }
  seedMeta(r.apps); quickApps = r.apps.map((a) => a.pkg);
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
let appsBoot = new Set();                                             // the apps that start by themselves when the TV turns on
let appsFilter = 'all';
// the chips above the list: each narrows it to one kind of app, with how many there are. The same words and colour mark
// those apps in the list (APP_TAGS), so a colour on a row says what the chip says.
const APPS_FILTERS = [
  ['all', 'Tất cả', () => true],
  ['boot', '🚀 Tự chạy', (p, i) => !i.off && appsBoot.has(p), 'Tự chạy mỗi khi bật TV'],
  ['keep', '🔒 Cần giữ', (p, i) => i.keep, 'TV cần app này: đừng tắt hay gỡ'],
  ['off', '⏸ Đã tắt', (p, i) => i.off, 'Đã tắt: bật lại trong nút ⋯ của app'],
];
const appTags = (p, i) => APPS_FILTERS.filter((f) => f[0] !== 'all' && f[2](p, i))
  .map(([key, label, , why]) => el('span', { className: 'tag ' + key, textContent: tr(label), title: tr(why) }));
async function loadApps() {
  $('#appslist').replaceChildren(el('div', { className: 'muted', textContent: 'Đang tải...' }));
  const r = await api('/api/apps' + ($('#appssys').checked ? '?system=1' : ''));
  if (!r) { $('#appslist').replaceChildren(); return; }
  appsLoaded = true; appsAll = r.apps.map((a) => a.pkg); appsInfo = new Map(r.apps.map((a) => [a.pkg, { off: !!a.off, keep: !!a.keep }])); seedMeta(r.apps); renderApps();
  const b = await api('/api/apps/boot', null, true);                 // after the list: a tag is worth no wait
  if (b) { appsBoot = bootRead(b.text); renderApps(); }
}
function renderApps() {
  const q = $('#appsq').value.trim().toLowerCase();
  lazyMeta.disconnect();
  const fit = APPS_FILTERS.find((f) => f[0] === appsFilter) || APPS_FILTERS[0];
  const found = appsAll.filter((p) => !q || p.toLowerCase().includes(q) || appName(p).toLowerCase().includes(q));
  const sys = $('#appssys').checked;
  $('#appsfilter').replaceChildren(...APPS_FILTERS.map(([key, label, ok, why]) => el('button', { className: key + (key === fit[0] ? ' on' : ''), type: 'button', title: why ? tr(why) : '',
    onclick: () => { appsFilter = key; renderApps(); } }, tr(label), el('b', { textContent: found.filter((p) => ok(p, appsInfo.get(p) || {})).length }))),
    el('button', { className: 'sys' + (sys ? ' on' : ''), type: 'button', ariaPressed: String(sys), title: tr('Hiện cả app hệ thống'),   // which apps are listed at all: a switch, not a kind
      onclick: () => { $('#appssys').checked = !sys; loadApps(); } }, (sys ? '✓ ' : '') + tr('⚙️ App hệ thống')));
  const rows = found.filter((p) => fit[2](p, appsInfo.get(p) || {}))
    .sort((a, b) => (appsInfo.get(a)?.off ? 1 : 0) - (appsInfo.get(b)?.off ? 1 : 0) || appName(a).localeCompare(appName(b))).map((pkg) => {   // turned off: last
    const run = async (name, label) => {
      if (name === 'uninstall' && !await ask('Gỡ cài đặt ' + appName(pkg) + ' khỏi TV?', { ok: 'Gỡ', danger: true })) return;
      if (name === 'disable' && !await ask('Tắt ' + appName(pkg) + '? App biến khỏi TV cho tới khi bật lại ở mục “App đã tắt” cuối danh sách này.', { ok: 'Tắt app' })) return;
      if (name === 'clear' && !await ask('Xóa toàn bộ dữ liệu của ' + appName(pkg) + '? App trở lại như mới cài: mất đăng nhập, cài đặt và nội dung đã tải trong app. Không hoàn tác được.', { ok: 'Xóa dữ liệu', danger: true })) return;
      const r = await api('/api/app/' + name, { pkg }); if (r) { toast(label + ': ' + appName(pkg)); if (!['launch', 'stop', 'clear'].includes(name)) { quickLoaded = false; loadApps(); loadQuick(); } }
    };
    const icon = appIcon(pkg), title = el('span', { textContent: pkg.split('.').pop() });
    const info = appsInfo.get(pkg) || {};
    // one button for what is wanted most, everything else behind ⋯
    const more = el('button', { className: 'small iconbtn', title: 'Thêm thao tác', ariaLabel: 'Thêm thao tác', textContent: '⋯', onclick: (e) => popover(e.currentTarget, [
      info.off ? ['↩', 'Bật lại', () => run('enable', 'Bật lại')] : ['▶', 'Mở', () => run('launch', 'Mở')],
      ['ℹ️', 'Chi tiết & quyền', () => appDetails(pkg)],
      ['📋', 'Chép tên gói', () => copyText(pkg)],
      info.off ? null : ['⏹', 'Dừng', () => run('stop', 'Dừng')],
      info.off ? null : ['📜', 'Xem log', () => appLog(pkg)],
      ['💾', 'Sao lưu (tải về)', () => appBackup(pkg)],
      info.keep || info.off ? null : ['🧽', 'Xóa dữ liệu', () => run('clear', 'Xóa dữ liệu')],
      info.keep || info.off ? null : ['⏸', 'Tắt app', () => run('disable', 'Tắt app')],
      ['🗑', 'Gỡ', () => run('uninstall', 'Gỡ'), 'danger'],
    ]) });
    const row = el('div', { className: 'item' + (info.off ? ' off' : '') }, icon, el('div', { className: 'name' },
      el('div', { className: 'nameline' }, title, ...appTags(pkg, info)), el('small', { className: 'pkg', textContent: pkg, title: pkg })),
      el('div', { className: 'acts' }, more));
    row.loadMeta = () => onMeta(pkg, (m) => { icon.fill(info.off ? { label: m.label } : m); if (m.label) title.textContent = m.label; });   // a disabled app has no picture to give
    if (META.has(pkg)) row.loadMeta(); else lazyMeta.observe(row);
    row.off = !!info.off;
    return row;
  });
  // the disabled apps (Optimize TV's, or "Disable" here) fold away at the end, so "Turn on" is at hand but out of the way
  const fold0 = fit[0] === 'all';                                    // a chip picked: its apps, all in plain sight
  const on = fold0 ? rows.filter((r) => !r.off) : rows, off = fold0 ? rows.filter((r) => r.off) : [];
  const none = !$('#appssys').checked && (fit[0] === 'keep' || fit[0] === 'boot') ? 'Không có app nào. Bấm “⚙️ App hệ thống” để thấy cả app hệ thống.' : 'Không có app nào.';
  const fold = off.length ? [el('details', { className: 'appsoff', open: !!q || appsOffOpen, ontoggle: (e) => { if (!q) appsOffOpen = e.target.open; } },
    el('summary', { textContent: '⏸ App đã tắt (' + off.length + ')' }), el('p', { className: 'muted', textContent: 'Đã tắt bằng Tối ưu TV hoặc nút Tắt app. Bấm ↩ để bật lại.' }), ...off)] : [];
  $('#appslist').replaceChildren(...(on.length || off.length ? on : [el('div', { className: 'muted', textContent: none })]), ...fold);
}
$('#appsq').addEventListener('input', renderApps);
$('#appsf').addEventListener('submit', (e) => e.preventDefault());
$('#appssys').addEventListener('change', loadApps);
$('#appsreload').addEventListener('click', loadApps);

// ℹ️ one app in detail: version, dates, memory, start at boot, and the permissions a person can give or take back
async function appDetails(pkg) {
  const body = $('#infobody');
  $('#infotitle').textContent = 'ℹ️ ' + appName(pkg); body.replaceChildren(el('p', { className: 'muted', textContent: 'Đang đọc từ TV...' }));
  if (!$('#infodlg').open) $('#infodlg').showModal();
  const r = await api('/api/app/info?pkg=' + encodeURIComponent(pkg));
  if (!r || !$('#infodlg').open) return;
  const a = appInfoRead(r.text), keep = (appsInfo.get(pkg) || {}).keep;
  if (!a.found) { body.replaceChildren(el('p', { className: 'muted', textContent: 'TV không còn app này.' })); return; }
  const kv = el('dl', { className: 'kv' });
  const add = (k, v) => { if (v) kv.append(el('dt', { textContent: k }), el('dd', { className: 'v', textContent: v })); };
  add('Phiên bản', a.version + (a.code ? ' (' + a.code + ')' : ''));
  add('Cần Android', a.minSdk ? androidName(a.minSdk) + ' trở lên' : '');
  add('Viết cho Android', a.targetSdk ? androidName(a.targetSdk) : '');
  add('Cài lúc', a.installed.slice(0, 16));
  add('Cập nhật lúc', a.updated !== a.installed ? a.updated.slice(0, 16) : '');
  add('Loại', a.system ? (a.updatedSystem ? 'App hệ thống, đã cập nhật' : 'App hệ thống') : 'Tự cài');
  add('Cài từ', a.installer === 'com.android.vending' ? 'Google Play' : a.installer ? appName(a.installer) : '');
  add('Đang chạy', a.pids.length ? 'Có' + (a.ramKb ? ', dùng ' + fmtSize(a.ramKb * 1024) + ' RAM' : '') : 'Không');
  add('Tự chạy khi bật TV', a.boot ? 'Có' : 'Không');
  const parts = [kv];
  if (a.perms.length) {
    parts.push(el('h3', { textContent: 'Quyền' }));
    if (keep) parts.push(el('p', { className: 'muted', textContent: 'TV cần app này để chạy: quyền của nó chỉ xem, không đổi ở đây.' }));
    for (const p of a.perms) {
      const seg = el('div', { className: 'seg mini' }, ...[[true, 'Cho phép'], [false, 'Không']].map(([on, label]) => el('button', {
        className: p.granted === on ? 'on' : '', textContent: label, disabled: keep || p.locked,
        onclick: async () => {
          if (p.granted === on) return;
          const x = await api('/api/app/perm', { pkg, perm: p.name, on });
          if (!x) return;
          if (/^ok$/m.test(x.text)) toast((on ? 'Đã cho phép: ' : 'Đã tắt quyền: ') + tr(permName(p.name))); else toast('TV không đổi quyền này: ' + friendly(x.text.trim()));
          appDetails(pkg);
        } })));
      parts.push(el('div', { className: 'permrow' }, el('span', { textContent: permName(p.name), title: p.name }),
        p.locked ? el('small', { className: 'muted', textContent: 'hệ thống giữ' }) : null, seg));
    }
  } else parts.push(el('p', { className: 'muted', textContent: 'App này không xin quyền nào cần người dùng cho phép (micro, vị trí, tệp...).' }));
  body.replaceChildren(...parts);
}
$('#infoclose').addEventListener('click', () => $('#infodlg').close());

// 💾 the app as a .tar: its APKs and its folders in shared storage. What it keeps in its private folder needs root to read.
function appBackup(pkg) {
  const a = el('a', { href: '/api/app/backup?pkg=' + encodeURIComponent(pkg) }); document.body.append(a); a.click(); a.remove();
  toast('Đang sao lưu ' + appName(pkg) + ': tệp .tar sẽ tải về máy này');
}
// 📥 a backup back onto this TV (or onto another one: that is how an app moves between TVs)
$('#restorefile').addEventListener('change', async (e) => {
  const f = e.target.files[0]; e.target.value = ''; if (!f) return;
  if (!/\.tar$/i.test(f.name)) { toast('Chọn tệp .tar đã sao lưu bằng nút 💾'); return; }
  if (await ask('Khôi phục ' + f.name + ' lên TV? App được cài lại (giữ dữ liệu nếu đang có trên TV), kèm thư mục dữ liệu trong bộ nhớ chung.', { ok: 'Khôi phục' }))
    upload(f, 'restore', '', () => { quickLoaded = false; loadApps(); loadQuick(); }, e.target.closest('label'));
});
// 🧹 the system's own cache cleaner for every app: nothing anyone would miss, apps just rebuild what they need
$('#appsclean').addEventListener('click', async () => {
  const r = await api('/api/clean', {});
  if (!r) return;
  const freed = cleanFreed(r.text);
  toast(freed === null ? 'Đã dọn bộ nhớ đệm' : freed > 1e6 ? 'Đã dọn bộ nhớ đệm, lấy lại ' + fmtSize(freed) : 'Bộ nhớ đệm đã sạch, không còn gì để dọn');
});

// 📜 an app's own log, live: every 1.5 s the lines since the last one shown
let logPkg = '', logTimer = 0, logSt = null, logPaused = false;
function appLog(pkg) {
  logPkg = pkg; logSt = { last: '', seen: new Set() }; logPaused = false;
  $('#logtitle').textContent = '📜 ' + appName(pkg); $('#logpre').replaceChildren(); $('#lognote').hidden = true;
  setLabel($('#logpause'), '⏸', 'Tạm dừng'); $('#logdlg').showModal(); pollLog();
}
async function pollLog() {
  clearTimeout(logTimer);
  if (!$('#logdlg').open) return;
  const pkg = logPkg;
  if (!logPaused && !document.hidden) {                               // a log left open in a hidden tab costs the TV nothing
    const r = await api('/api/log?pkg=' + encodeURIComponent(pkg) + '&since=' + encodeURIComponent(logSt.last), null, true);
    if (pkg !== logPkg || !$('#logdlg').open) return;
    if (r) {
      const { pid, lines } = logNext(logSt, r.text), pre = $('#logpre');
      $('#lognote').hidden = !!pid;
      const end = pre.scrollTop + pre.clientHeight >= pre.scrollHeight - 30;   // follow the end unless the reader scrolled up
      pre.append(...lines.map((l) => el('div', { className: 'l' + logLevel(l), textContent: l })));
      while (pre.childElementCount > 3000) pre.firstElementChild.remove();
      if (end) pre.scrollTop = pre.scrollHeight;
    }
  }
  logTimer = setTimeout(pollLog, 1500);
}
$('#logpause').addEventListener('click', () => { logPaused = !logPaused; setLabel($('#logpause'), logPaused ? '▶' : '⏸', logPaused ? 'Chạy tiếp' : 'Tạm dừng'); });
$('#logcopy').addEventListener('click', async () => {
  const text = [...$('#logpre').children].map((d) => d.textContent).join('\n');
  try { await navigator.clipboard.writeText(text); toast('Đã sao chép log'); } catch { toast('Không sao chép được: chọn chữ rồi sao chép tay'); }
});
$('#logopen').addEventListener('click', async () => { if (await api('/api/app/launch', { pkg: logPkg })) toast('Mở: ' + appName(logPkg)); });
$('#logclose').addEventListener('click', () => $('#logdlg').close());
$('#logdlg').addEventListener('close', () => clearTimeout(logTimer));


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
    'Cuối câu trả lời, ghi riêng danh sách app id nên tắt (mỗi dòng một id) trong một khối code, để mình dán vào ô "Bật / tắt / gỡ hàng loạt".',
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
    'At the end, list the app ids to disable on their own (one per line) in a code block, so I can paste them into "Turn on / off / remove in bulk".',
    ours.length ? `Do not suggest disabling ${ours.join(', ')}: I use them to control the TV.` : '',
    '', '== Apps using the most memory right now ==', mem,
    '', `== User-installed apps (${r.user.length}) ==`, r.user.map(named).join('\n'),
    '', `== System apps that are on (${r.system.length}) ==`, r.system.join('\n'),
    '', `== Apps already disabled (${r.disabled.length}) ==`, r.disabled.join('\n') || '(none)',
  ]).filter((l, i, a) => l !== '' || a[i - 1] !== '').join('\n');
}
$('#aiapps').addEventListener('click', async (e) => {                // the TV's memory survey takes a few seconds: the button says so until the text is ready
  const b = e.currentTarget, was = [...b.childNodes];
  b.disabled = true; b.replaceChildren(el('span', { className: 'minispin' }), el('span', { textContent: 'Đang lấy danh sách app...' }));
  try {
    const r = await api('/api/apps/report'); if (!r) return;
    $('#aitext').value = aiPrompt(r); $('#aidlg').showModal();
  } finally { b.disabled = false; b.replaceChildren(...was); }
});
$('#aiclose').addEventListener('click', () => $('#aidlg').close());
$('#aicopy').addEventListener('click', async () => {
  const t = $('#aitext'); let ok = false;
  try { await navigator.clipboard.writeText(t.value); ok = true; } catch {}
  if (!ok) { t.focus(); t.select(); try { ok = document.execCommand('copy'); } catch {} }   // plain-HTTP pages (the TV's) have no clipboard API
  toast(ok ? 'Đã chép. Dán vào ChatGPT, Claude hay Gemini.' : 'Không chép tự động được: chữ đã được bôi đen, bấm giữ để chép.');
});

// 📝 in bulk: paste package names (or a whole AI answer: anything shaped like com.abc.xyz counts), see where each one stands,
// then turn them all off, back on, or remove them. One app after another through the same calls as the buttons on each row.
let bulkKnown = null;                                                // pkg -> { off, keep } for every package on the TV
const bulkPkgs = () => [...new Set(($('#bulktext').value.match(/\b[a-zA-Z][\w]*(?:\.[a-zA-Z_][\w]*)+\b/g) || [])
  .filter((p) => p.split('.').every((part) => part.length >= 2)))];   // "e.g" is not a package; anything not on the TV shows as such
async function bulkStatus(force) {
  if (bulkKnown && !force) return bulkKnown;
  const r = await api('/api/apps?system=1', null, true); if (!r) return null;
  seedMeta(r.apps); bulkKnown = new Map(r.apps.map((a) => [a.pkg, { off: !!a.off, keep: !!a.keep }]));
  return bulkKnown;
}
function bulkWhere(pkg) {
  const k = bulkKnown && bulkKnown.get(pkg);
  return !bulkKnown ? ['', '…'] : !k ? ['bad', 'không có trên TV'] : k.keep ? ['warn', 'TV cần app này: bỏ qua'] : k.off ? ['', '⏸ đã tắt'] : ['ok', 'đang bật'];
}
function renderBulk() {
  const pkgs = bulkPkgs();
  $('#bulklist').replaceChildren(...pkgs.map((p) => {
    const [cls, word] = bulkWhere(p);
    return el('div', { className: 'item bulkrow' }, el('div', { className: 'name' }, el('span', { textContent: appName(p) }), el('small', { textContent: p })), el('span', { className: 'chip ' + cls, textContent: word }));
  }));
  ['#bulkoff', '#bulkon', '#bulkdel'].forEach((id) => { $(id).disabled = !pkgs.length; });
  if (!pkgs.length) $('#bulklog').textContent = $('#bulktext').value.trim() ? 'Không thấy app id nào trong đoạn chữ này.' : '';
}
let bulkT = 0;
$('#bulktext').addEventListener('input', () => { clearTimeout(bulkT); bulkT = setTimeout(async () => { renderBulk(); await bulkStatus(); renderBulk(); }, 300); });
$('#bulkopen').addEventListener('click', async () => { $('#bulkdlg').showModal(); renderBulk(); await bulkStatus(true); renderBulk(); });
$('#bulkclose').addEventListener('click', () => $('#bulkdlg').close());
async function bulkRun(action, label) {
  await bulkStatus();
  const pkgs = bulkPkgs().filter((p) => { const k = bulkKnown.get(p); return k && !(action === 'disable' && (k.keep || k.off)) && !(action === 'enable' && !k.off); });
  if (!pkgs.length) { $('#bulklog').textContent = 'Không có app nào cần ' + label.toLowerCase() + '.'; return; }
  if (action === 'uninstall' && !await ask('Gỡ ' + pkgs.length + ' app khỏi TV? Gỡ rồi phải cài lại mới có (app hệ thống thì không gỡ được, hãy tắt).', { ok: 'Gỡ', danger: true })) return;
  if (action === 'disable' && !await ask('Tắt ' + pkgs.length + ' app? Bật lại được bất cứ lúc nào ở đây hoặc ở mục “App đã tắt”.', { ok: 'Tắt app' })) return;
  const btns = ['#bulkoff', '#bulkon', '#bulkdel'].map((id) => $(id)); btns.forEach((b) => { b.disabled = true; });
  let ok = 0; const bad = [];
  for (let i = 0; i < pkgs.length; i++) {
    $('#bulklog').textContent = label + ' ' + (i + 1) + '/' + pkgs.length + ': ' + appName(pkgs[i]) + '...';
    const r = await api('/api/app/' + action, { pkg: pkgs[i] }, true);
    if (r) ok++; else bad.push(pkgs[i]);
  }
  $('#bulklog').textContent = label + ': xong ' + ok + '/' + pkgs.length + (bad.length ? ' · không được: ' + bad.join(', ') : '');
  btns.forEach((b) => { b.disabled = false; });
  await bulkStatus(true); renderBulk(); quickLoaded = false; loadQuick(); if (appsLoaded) loadApps();
}
$('#bulkoff').addEventListener('click', () => bulkRun('disable', 'Tắt'));
$('#bulkon').addEventListener('click', () => bulkRun('enable', 'Bật lại'));
$('#bulkdel').addEventListener('click', () => bulkRun('uninstall', 'Gỡ'));

