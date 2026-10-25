// 🔒 Health › Riêng tư (privacy check): which apps can listen, watch or track, and what was seen.
// The scan runs on the TV (core/ontv/Privacy.kt: one low-priority shell stream, a row per app as it goes); this file polls the core
// about once a second and draws what came in together, so rows arrive in groups and the page is not redrawn per line.
// It says what was SEEN ("used the microphone 12 minutes ago"), never that an app IS listening.
let priv = null;                                    // { id, state, total, n, at, took, msg, rows: Map(pkg -> row), seq }
let privTimer = 0, privGen = 0;
const privIcons = new Map();                        // a row keeps its icon across redraws (a new one would flash its letter)
const PRIV_ICON = { mic: '🎤', cam: '📷', loc: '📍', a11y: '👁', notif: '🔔', acr: '📺', track: '📡', ads: '📢', admin: '🔑', overlay: '🔲', usage: '📊',
  assist: '🗣', contacts: '👤', phone: '📞', nearby: '📶', boot: '⚡' };
const PRIV_LOUD = new Set(['mic', 'cam', 'a11y', 'acr']), PRIV_MID = new Set(['loc', 'notif', 'track', 'ads', 'admin', 'overlay', 'assist']);   // the colour of a tag
let privLowOpen = false, privLaterT = 0;
const privLater = () => { clearTimeout(privLaterT); privLaterT = setTimeout(privDraw, 500); }   // names arrive one by one: drawn once they have
onTvChange(() => { privGen++; clearTimeout(privTimer); priv = null; privIcons.clear(); privDraw(); });

const privTagWords = () => ({ mic: T('priv.t_mic'), cam: T('priv.t_cam'), loc: T('priv.t_loc'), a11y: T('priv.t_a11y'), notif: T('priv.t_notif'), acr: T('priv.t_acr'), track: T('priv.t_track'), ads: T('priv.t_ads'), admin: T('priv.t_admin'), overlay: T('priv.t_overlay'), usage: T('priv.t_usage'), assist: T('priv.t_assist'), contacts: T('priv.t_contacts'), phone: T('priv.t_phone'), nearby: T('priv.t_nearby'), boot: T('priv.t_boot') });
const privHintWords = () => ({ mic: T('priv.h_mic'), cam: T('priv.h_cam'), loc: T('priv.h_loc'), a11y: T('priv.h_a11y'), notif: T('priv.h_notif'), acr: T('priv.h_acr'), track: T('priv.h_track'), ads: T('priv.h_ads'), admin: T('priv.h_admin'), overlay: T('priv.h_overlay'), usage: T('priv.h_usage'), assist: T('priv.h_assist'), contacts: T('priv.h_contacts'), phone: T('priv.h_phone'), nearby: T('priv.h_nearby'), boot: T('priv.h_boot') });
const privEvWords = (a) => ({ mic_now: T('priv.ev_mic_now', a), mic_used: T('priv.ev_mic_used', a), mic_idle: T('priv.ev_mic_idle', a), cam_now: T('priv.ev_cam_now', a), cam_used: T('priv.ev_cam_used', a), cam_idle: T('priv.ev_cam_idle', a), loc_now: T('priv.ev_loc_now', a), loc_used: T('priv.ev_loc_used', a), loc_idle: T('priv.ev_loc_idle', a), a11y: T('priv.ev_a11y', a), notif: T('priv.ev_notif', a), admin: T('priv.ev_admin', a), overlay: T('priv.ev_overlay', a), usage: T('priv.ev_usage', a), assist: T('priv.ev_assist', a), acr: T('priv.ev_acr', a), track: T('priv.ev_track', a), ads: T('priv.ev_ads', a) });
function privAgo(s) {
  return s < 90 ? T('priv.ago_s', { n: Math.max(1, s) }) : s < 5400 ? T('priv.ago_m', { n: Math.round(s / 60) })
    : s < 129600 ? T('priv.ago_h', { n: Math.round(s / 3600) }) : T('priv.ago_d', { n: Math.round(s / 86400) });
}
const privSay = (e) => privEvWords({ ago: e.n != null ? privAgo(e.n) : '', name: e.name || '' })[e.id] || e.id;
const privActive = () => hmode === 'priv' && currentTab === 'health' && !document.hidden;

function privOpen() { if (!priv) privPoll(true); else { privDraw(); if (priv.state === 'running') privPoll(); } }
async function privPoll(first) {
  clearTimeout(privTimer);
  const gen = privGen;
  const r = await api('/api/privacy?id=' + (priv ? priv.id : -1) + '&since=' + (priv && !first ? priv.seq : 0), null, true);
  if (gen !== privGen) return;
  if (!r) { if (privActive()) privTimer = setTimeout(privPoll, 3000); return; }
  if (!priv || priv.id !== r.id || first) priv = { id: r.id, rows: new Map(), seq: 0 };
  for (const row of r.rows) priv.rows.set(row.pkg, row);
  Object.assign(priv, { state: r.state, total: r.total, at: r.at, took: r.took, msg: r.msg, seq: r.n });
  privDraw();
  if (r.state === 'running' && privActive()) privTimer = setTimeout(privPoll, 1000);
}
async function privScan() {
  const r = await api('/api/privacy/scan', {}); if (!r) return;
  privGen++; priv = { id: r.id, rows: new Map(), seq: 0, state: 'running', total: 0, at: r.at };
  privDraw(); privTimer = setTimeout(privPoll, 600);
}
async function privStopScan() { await api('/api/privacy/stop', {}); privPoll(); }

// what the scan found, in one line: the things worth knowing first
function privSummary(rows) {
  rows = rows.filter((r) => !r.own);                                    // this project's apps are not news
  const has = (r, id) => r.ev.some((e) => e.id === id), recent = (r, t) => r.ev.some((e) => e.id === t + '_now' || (e.id === t + '_used' && e.n <= 86400));
  const n = (f) => rows.filter(f).length;
  const parts = [[n((r) => recent(r, 'mic')), 'priv.sum_mic'], [n((r) => recent(r, 'cam')), 'priv.sum_cam'], [n((r) => has(r, 'acr')), 'priv.sum_acr'],
    [n((r) => has(r, 'a11y')), 'priv.sum_a11y'], [n((r) => has(r, 'track') || has(r, 'ads')), 'priv.sum_track']].filter((p) => p[0]);
  return parts.length ? parts.map(([c, id]) => T(id, { n: c })).join(' · ') : T('priv.sum_none');
}

function privItem(r) {
  const keep = !!r.keep || !!r.own,                          // own: what it holds is its work (the group's note says what)
    hint = privHintWords(), word = privTagWords();
  const tags = [...r.tags.map((t) => el('span', { className: 'tag ' + (PRIV_LOUD.has(t) ? 'p-hi' : PRIV_MID.has(t) ? 'p-md' : 'p-lo'), title: hint[t], textContent: PRIV_ICON[t] + ' ' + word[t] })),
    ...r.gone.map((t) => el('span', { className: 'tag p-gone', title: T('priv.revoked_chip'), textContent: PRIV_ICON[t] + ' ' + word[t] })),
    r.off ? el('span', { className: 'tag off', textContent: T('priv.off_chip') }) : null].filter(Boolean);
  if (!privIcons.has(r.pkg)) { const i = appIcon(r.pkg); privIcons.set(r.pkg, i); onMeta(r.pkg, (m) => { i.fill(m); privLater(); }); }
  const act = async (path, ok) => { const x = await api(path, { pkg: r.pkg }); if (!x) return; if (x.row) priv.rows.set(r.pkg, x.row); toast(ok(x)); privDraw(); };
  const name = appName(r.pkg), acts = [];
  if (!keep && r.perms.length) acts.push(el('button', { className: 'small', textContent: T('priv.revoke'), title: T('priv.revoke_tip'),
    onclick: () => act('/api/privacy/revoke', (x) => x.failed ? T('priv.revoked_some', { n: x.done, name }) : T('priv.revoked', { n: x.done, name })) }));
  if (!keep && r.revoked.length) acts.push(el('button', { className: 'small', textContent: T('priv.regrant'),
    onclick: () => act('/api/privacy/grant', (x) => T('priv.regranted', { name })) }));
  if (!keep && r.tags.includes('a11y')) acts.push(el('button', { className: 'small', textContent: T('priv.a11y_off'), title: T('priv.a11y_off_tip'),
    onclick: async () => {
      if (!await ask(T('priv.a11y_off_ask', { name }), { ok: T('priv.a11y_off') })) return;
      act('/api/privacy/a11y-off', () => T('priv.a11y_done', { name }));
    } }));
  if (!keep && !r.sys) acts.push(el('button', { className: 'small', textContent: T('apps.remove'),
    onclick: async () => {
      if (!await ask(T('apps.uninstall_ask', { name }), { ok: T('apps.remove') })) return;
      if (!await api('/api/app/uninstall', { pkg: r.pkg })) return;
      priv.rows.delete(r.pkg); toast(T('apps.what_name', { what: T('apps.remove'), name })); privDraw();
    } }));
  if (!keep) acts.push(el('button', { className: 'small', textContent: T(r.off ? 'apps.turn_on' : 'apps.disable_app'),
    onclick: async () => {
      if (!r.off && !await ask(T('apps.disable_ask', { name }), { ok: T('apps.disable_app') })) return;
      act(r.off ? '/api/privacy/on' : '/api/privacy/off', () => T('apps.what_name', { what: T(r.off ? 'apps.turn_on' : 'apps.disable_app'), name }));
    } }));
  acts.push(el('button', { className: 'small iconbtn', title: T('page.more_actions'), ariaLabel: T('page.more_actions'), textContent: '⋯',   // the Apps tab's menu: details & one permission at a time (also for an app the TV needs), log, backup
    onclick: (e) => appMenu(e.currentTarget, r.pkg, { keep, off: r.off }, () => {}, ['enable', 'disable', 'uninstall']) }));   // those three are the buttons above, which keep the scan's row true
  const lines = r.ev.filter((e) => e.id !== 'boot').map(privSay);
  if (keep && !r.own) lines.push(T('priv.keep'));
  return UI.item({ icon: privIcons.get(r.pkg), name, sub: lines.join(' · '), extra: [el('div', { className: 'ptags' }, ...tags)], acts });
}

function privDraw() {
  const box = $('#hpriv'); if (!box) return;
  const run = !!priv && priv.state === 'running';
  const rows = priv ? [...priv.rows.values()].filter((r) => r.tags.length || r.gone.length || r.ev.length) : [];
  const scanBtn = el('button', { className: 'small primary', textContent: T(priv && priv.state && priv.state !== 'none' ? 'priv.rescan' : 'priv.scan'), disabled: run, onclick: privScan });
  const stopBtn = run ? el('button', { className: 'small', textContent: T('priv.stop'), onclick: privStopScan }) : null;
  let status = null;
  if (run) {
    const pct = priv.total ? Math.min(100, Math.round(100 * priv.rows.size / priv.total)) : 0;
    const bar = el('div', { className: 'bar' + (priv.total ? '' : ' busy') }, el('i')); bar.firstChild.style.width = pct + '%';
    status = el('div', { className: 'group' }, el('p', { className: 'note', textContent: priv.total ? T('priv.progress', { n: priv.rows.size, total: priv.total }) : T('priv.preparing') }), bar);
  } else if (priv && priv.state === 'done') {
    status = UI.note(T('priv.done_at', { time: new Date(priv.at + (priv.took || 0)).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }), n: rows.length, sec: Math.max(1, Math.round(priv.took / 1000)) }));
  } else if (priv && priv.state === 'stopped') status = UI.note(T('priv.stopped', { n: priv.rows.size, total: priv.total }));
  else if (priv && priv.state === 'error') status = UI.warn(T('priv.failed', { why: friendly(priv.msg) }));
  else status = UI.note(T('priv.never'));
  const head = UI.card(T('priv.title'), { tools: [stopBtn, scanBtn].filter(Boolean) }, UI.note(T('priv.note')), status,
    rows.length || (priv && priv.state === 'done') ? UI.state(privSummary(rows)) : null);
  const by = (g) => rows.filter((r) => r.grade === g).sort((a, b) => a.sys - b.sys || appName(a.pkg).localeCompare(appName(b.pkg)));
  const hi = by('high'), md = by('med'), lo = by('low'), own = by('own');
  const group = (title, list, fold, note) => {
    if (!list.length) return null;
    const c = UI.card(title + ' · ' + list.length, { fold }, note ? UI.note(note) : null, UI.list(list.map(privItem), ''));
    if (fold) { c.open = privLowOpen; c.addEventListener('toggle', () => { privLowOpen = c.open; }); }
    return c;
  };
  box.replaceChildren(...[head, group(T('priv.g_high'), hi), group(T('priv.g_med'), md), group(T('priv.g_low'), lo, true),
    group(T('priv.g_own'), own, true, T('priv.own_note'))].filter(Boolean));
}
