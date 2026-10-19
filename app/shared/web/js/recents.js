// "Đang mở", a chip of the Apps list (apps.js): the TV's own recents list (most TVs have no screen for it), newest first. Each row
// says when it was last used and its memory (once /api/procs has answered), Switch goes back to it, ✕ closes it (it leaves the
// list and stops; what the TV needs only leaves the list). Under the rows, what runs without being opened (Tailscale, the
// keyboard...) with its memory, and Android's own folded away: what the Health tab used to show.
let recentsAgain = false, recentsOpen = new Map(), recentsMb = new Map(), recentsBg = [], recentsAt = 0, recentsBusy = false;
const procState = () => ({ front: T('health.proc_front'), bg: T('health.proc_bg'), home: T('health.proc_home'), cached: T('health.proc_idle'), system: T('health.proc_system') });
const procKeep = () => ({ us: T('health.keep_us'), home: T('health.keep_home'), keyboard: T('health.keep_keyboard'), system: T('health.keep_android') });
const recentsAgo = (s) => s < 60 ? T('recents.just_now') : s < 3600 ? T('recents.min_ago', { n: Math.round(s / 60) }) : s < 86400 ? T('recents.h_ago', { n: Math.round(s / 3600) }) : T('recents.d_ago', { n: Math.round(s / 86400) });
/** The open apps again (cheap); their memory too while the chip is picked (dumpsys meminfo: a second or more, so after). */
async function recentsNow(now) {
  if (recentsBusy) { if (now) recentsAgain = true; return; }        // asked while reading: once more after, or a closed app stays
  if (!now && Date.now() - recentsAt < 5000) return;
  recentsBusy = true; recentsAt = Date.now();
  try {
    const mem = appsFilter === 'open' && api('/api/procs', null, true);   // both at once: the memory may come a moment later
    const r = await api('/api/recents', null, true); if (!r || !r.apps) return;
    recentsOpen = new Map(r.apps.map((a) => [a.pkg, a]));
    if (appsLoaded) renderApps();
    const p = mem && await mem; if (!p || !p.apps) return;
    recentsMb = new Map(p.apps.map((a) => [a.pkg, a.mb]));
    recentsBg = p.apps.filter((a) => !recentsOpen.has(a.pkg));
    if (appsLoaded) renderApps();
  } finally { recentsBusy = false; if (recentsAgain) { recentsAgain = false; recentsNow(true); } }
}
/** The open apps in their order, those whose name or package has [q]. */
const recentsList = (q) => [...recentsOpen.keys()].filter((p) => !q || p.toLowerCase().includes(q) || appName(p).toLowerCase().includes(q));
/** A row's line under its name: on screen or when, and its memory. */
function recentsSub(pkg) {
  const a = recentsOpen.get(pkg), mb = recentsMb.get(pkg);
  return (a.top ? T('recents.on_screen') : recentsAgo(a.ago)) + (mb ? ' · ' + mb + ' MB' : recentsMb.size ? ' · ' + T('recents.not_running') : '');   // in the list, its process long gone
}
/** Beside an open app's name: Must keep (no ✕), as the old Running apps had it; the same tag as the All list's. */
function recentsTag(pkg) {
  const a = recentsOpen.get(pkg);
  return a.keep ? [el('span', { className: 'tag keep', textContent: T('health.must_keep'), title: procKeep()[a.keep] || '' })] : [];
}
/** A row's buttons: Switch, and ✕. */
function recentsActs(pkg) {
  const a = recentsOpen.get(pkg), name = appName(pkg);
  const close = a.keep ? null : el('button', { type: 'button', className: 'small iconbtn', textContent: '✕', title: T('recents.close'), ariaLabel: T('recents.close'), onclick: async () => {
      if (a.top && !await ask(T('health.stop_ask', { name }), { ok: T('health.stop') })) return;
      if (await api('/api/recents', { pkg })) { recentsOpen.delete(pkg); recentsBg = recentsBg.filter((b) => b.pkg !== pkg); renderApps(); setTimeout(() => recentsNow(true), 800); }
    } });
  return [a.top ? null : setLabel(act('', async () => { if (await api('/api/app/launch', { pkg })) { toast(T('recents.switched', { name })); setTimeout(() => recentsNow(true), 1200); } }), '▶', T('recents.switch')),   // its icon alone on a phone: the row stays one line
    close].filter(Boolean);
}
/** Under the open apps: Close all, then what runs in the background. */
function recentsTail() {
  const sys = recentsBg.filter((a) => a.keep === 'system'), bg = recentsBg.filter((a) => a.keep !== 'system');
  const all = el('button', { type: 'button', className: 'small', textContent: T('recents.close_all'), disabled: ![...recentsOpen.values()].some((a) => !a.top && !a.keep), onclick: async () => {
    if (!await ask(T('recents.close_all_ask'), { ok: T('recents.close_all') })) return;
    const r = await api('/api/recents', { all: true });
    if (r) { toast(T('recents.closed_n', { n: r.closed })); recentsNow(true); }
  } });
  return [el('div', { className: 'actions' }, all), ...(bg.length || sys.length ? [el('h3', { textContent: T('recents.background') }), ...bg.map(procRow),
    ...(sys.length ? [el('details', { className: 'appsoff' }, el('summary', { textContent: T('health.android_system_n', { n: sys.length }) }), ...sys.map(procRow))] : [])] : [])];
}
function procRow(a) {
  const icon = appIcon(a.pkg), name = META.get(a.pkg)?.label || a.label;
  const title = el('span', { textContent: name });
  if (a.keep === 'system') icon.fill({ label: name });                   // Android's own: no picture worth asking for
  else onMeta(a.pkg, (m) => { icon.fill(m.label ? m : { ...m, label: name }); if (m.label) title.textContent = m.label; });
  const stop = async () => { if (await api('/api/procs/kill', { pkg: a.pkg })) { toast(T('health.stopped_app', { name })); recentsBg = recentsBg.filter((b) => b !== a); renderApps(); setTimeout(() => recentsNow(true), 800); } };   // gone at once: a service may start it again, the next read says so
  return el('div', { className: 'item proc' }, icon, el('div', { className: 'name' }, title,
    el('small', { textContent: a.mb + ' MB · ' + (procState()[a.state] || a.state) })),
    el('div', { className: 'acts' }, a.keep ? el('span', { className: 'tag keep', textContent: T('health.must_keep'), title: procKeep()[a.keep] || '' }) : act(T('health.stop_btn'), stop)));
}
onTvChange(() => { recentsOpen = new Map(); recentsMb = new Map(); recentsBg = []; recentsAt = 0; });
