// 📺 Several TVs (computer only, app/desktop Fleet.kt): every saved TV in one list to compare, the ones ticked get the same APK,
// lose the same app, or restart, each with its own progress line. The card sits under the saved TVs on the picker, once there are two.
let fleetTvs = [], fleetPick = new Set(), fleetRow = {};
const FLEET_STEP = { waiting: T('fleet.step_waiting'), connecting: T('fleet.step_connecting'), 'installing on TV': T('fleet.step_installing'), 'checking the TV kept it': T('fleet.step_checking'),
  installed: T('fleet.step_installed'), 'launcher download': T('fleet.step_ldownload'), 'launcher install': T('fleet.step_linstall'),
  'launcher swap': T('fleet.step_lswap'), 'launcher kept': T('fleet.step_lkept'), uninstalled: T('fleet.step_removed'), restarting: T('fleet.step_restarting'), set: T('fleet.step_welcome_set'), off: T('fleet.step_welcome_off'), done: T('fleet.step_done') };
const FLEET_ADB = { closed: T('fleet.adb_closed'), unauthorized: T('fleet.adb_unauthorized'),
  offline: T('fleet.adb_offline'), error: T('fleet.adb_error') };

function fleetCheck(n) {                                              // called by loadTvs with the number of saved TVs
  put('#fleet', 'hidden', n < 2); put('#fleetcount', 'textContent', n ? T('fleet.count', { n }) : '');
  if (n >= 2 && $('#fleet').open) fleetLoad();
}
async function fleetLoad() {
  $('#fleetlist').replaceChildren(el('div', { className: 'muted', textContent: T('fleet.asking_each') }));
  const r = await api('/api/fleet'); if (!r) return;
  fleetTvs = r.tvs;
  if (!fleetPick.size) fleetTvs.forEach((t) => { if (t.state === 'ready') fleetPick.add(t.host); });   // the ones that can do it, to start with
  fleetDraw();
}
function fleetDraw() {
  fleetRow = {};
  $('#fleetlist').replaceChildren(...fleetTvs.map((t) => {
    const box = el('input', { type: 'checkbox', checked: fleetPick.has(t.host), onchange: (e) => { if (e.target.checked) fleetPick.add(t.host); else fleetPick.delete(t.host); } });
    const facts = t.state !== 'ready' ? FLEET_ADB[t.state] || t.state
      : [t.model, t.android ? 'Android ' + t.android : '', t.disk_total ? T('fleet.disk_free', { free: fmtSize(t.disk_free), total: fmtSize(t.disk_total) }) : '',
        t.ram ? 'RAM ' + fmtSize(t.ram) : '', t.setup ? 'TV Setup ' + t.setup : ''].filter(Boolean).join(' · ');
    const step = el('small', { className: 'fleetstep' });
    fleetRow[t.host] = step;
    return el('label', { className: 'fleetrow' + (t.state === 'ready' ? '' : ' off') }, box,
      el('div', { className: 'name' }, el('b', { textContent: t.host === STATE.host ? T('fleet.name_in_use', { name: t.name || 'TV' }) : (t.name || 'TV') }),
        el('small', { textContent: t.host + ' · ' + facts }), step));
  }));
}
$('#fleet').addEventListener('toggle', (e) => { if (e.target.open) fleetLoad(); });

const fleetChosen = () => fleetTvs.filter((t) => fleetPick.has(t.host));
async function fleetAsk(what) {
  const tvs = fleetChosen();
  if (!tvs.length) { toast(T('fleet.pick_one')); return null; }
  return await ask(what + '\n\n' + tvs.map((t) => '• ' + (t.name || t.host)).join('\n')) ? tvs : null;
}
// one line per TV under its name, until every TV is done
let fleetStop = null;                                                  // what ■ Dừng does now: abort the upload, or tell the computer to stop the install on every TV
$('#fleetstop').addEventListener('click', () => { if (fleetStop) fleetStop(); put('#fleetstop', 'disabled', true); });
const fleetStopShow = (fn) => { fleetStop = fn; put('#fleetstop', 'hidden', !fn); put('#fleetstop', 'disabled', false); };
async function fleetFollow(j) {
  const id = j.id;
  if (/^(install|welcome |launcher)/.test(j.title)) fleetStopShow(() => api('/api/fleet/cancel', { id }, true));   // only an install or a file copy has a half way to stop at
  try { await fleetFollowing(j); } finally { fleetStopShow(null); }
}
async function fleetFollowing(j) {
  while (j && j.tvs) {
    for (const [host, p] of Object.entries(j.tvs)) {
      const s = fleetRow[host]; if (!s) continue;
      s.className = 'fleetstep ' + p.status;
      s.textContent = p.status === 'cancelled' ? T('fleet.stopped_line') : p.status === 'error' ? '✗ ' + (/^adb:/.test(p.msg) ? FLEET_ADB[p.msg.slice(4)] || p.msg : friendly(p.msg))
        : (/^uninstalled \d+$/.test(p.msg) ? T('fleet.step_removed_n', { n: +p.msg.slice(12) }) : FLEET_STEP[p.msg] || p.msg) + (p.status === 'running' && p.pct ? ' ' + p.pct + '%' : '');
    }
    if (j.status === 'cancelled') { toast(T('fleet.stopped')); return; }   // every part says "Đã dừng" above
    if (j.status === 'done') {
      const parts = Object.values(j.tvs), bad = parts.filter((p) => p.status === 'error').length;
      toast(bad ? T('fleet.done_some', { ok: parts.length - bad, bad }) : T('fleet.done_all', { n: parts.length }));
      return;
    }
    await sleep(1000);
    j = await api('/api/fleet/job?id=' + j.id, null, true);
  }
}

$('#fleetapk input').addEventListener('change', async (e) => {
  const f = e.target.files[0]; e.target.value = ''; if (!f) return;
  const tvs = await fleetAsk(T('fleet.install_ask', { name: f.name })); if (!tvs) return;
  const lbl = $('#fleetapk'), xhr = new XMLHttpRequest();
  xhr.open('POST', '/api/fleet/install?name=' + encodeURIComponent(f.name) + '&hosts=' + encodeURIComponent(tvs.map((t) => t.host).join(',')));
  xhr.setRequestHeader('Content-Type', 'application/octet-stream');
  xhr.upload.onprogress = (ev) => { if (ev.lengthComputable) lbl.dataset.busy = '⏳ ' + Math.round(ev.loaded * 100 / ev.total) + '%'; };
  xhr.onloadend = () => {
    delete lbl.dataset.busy;
    if (xhr.status === 0) { fleetStopShow(null); return; }               // aborted by ■ Dừng (the computer drops the copy it had)
    let r = {}; try { r = JSON.parse(xhr.responseText); } catch {}
    if (xhr.status !== 200) { toast(T('fleet.error_msg', { msg: friendly(r.error || xhr.statusText || T('core.no_server_reply')) })); return; }
    fleetFollow(r);
  };
  lbl.dataset.busy = '⏳'; fleetStopShow(() => { xhr.abort(); toast(T('fleet.stopped')); }); xhr.send(f);
});
async function fleetDo(action, pkg, what) {
  const tvs = await fleetAsk(what); if (!tvs) return;
  const r = await api('/api/fleet/do', { action, pkg, hosts: tvs.map((t) => t.host) });
  if (r) fleetFollow(r);
}
$('#fleetmore').addEventListener('click', (e) => popover(e.currentTarget, [
  ['🏠', T('fleet.menu_welcome'), () => fleetWelcomeFile.click()],
  ['💤', T('fleet.menu_welcome_off'), () => fleetDo('welcome_off', '', T('fleet.ask_welcome_off'))],
  ['🗑', T('fleet.menu_remove'), () => fleetUninstall()],
  ['🖼️', T('fleet.menu_launcher'), () => fleetLauncher()],
  ['🔁', T('fleet.menu_restart'), () => fleetDo('reboot', '', T('fleet.ask_restart'))],
  ['☑️', T('fleet.menu_select_all'), () => { fleetTvs.forEach((t) => fleetPick.add(t.host)); fleetDraw(); }],
  ['↻', T('fleet.menu_reread'), () => fleetLoad()],
]));
// the apps the user installed on the TVs ticked, most common first: one row each (its picture, its name, on how many TVs), tick the
// ones to remove everywhere, then one question. TV Setup and Tailscale are not offered.
function fleetUninstall() {
  const all = fleetChosen(), tvs = all.filter((t) => t.apps);
  if (!tvs.length) { toast(T('fleet.pick_one')); return; }
  const count = new Map();
  tvs.forEach((t) => t.apps.forEach((p) => count.set(p, (count.get(p) || 0) + 1)));
  const apps = [...count].filter(([p]) => !/^(dev\.tvremote\.setup|com\.tailscale\.ipn)$/.test(p)).sort((a, b) => b[1] - a[1] || appName(a[0]).localeCompare(appName(b[0])));
  put('#infotitle', 'textContent', T('fleet.which_apps'));
  if (!apps.length) { $('#infobody').replaceChildren(UI.empty(T('fleet.no_apps'))); $('#infodlg').showModal(); return; }
  const picked = new Set(), rows = new Map();
  const go = el('button', { className: 'danger', type: 'button', disabled: true });
  const toggle = el('button', { className: 'small', type: 'button' });
  const none = UI.empty(T('fleet.no_match'));
  none.hidden = true;
  const shown = () => [...rows].filter(([, r]) => !r.hidden).map(([p]) => p);
  const sync = () => {
    go.disabled = !picked.size; go.textContent = T('fleet.remove') + (picked.size ? ' (' + picked.size + ')' : '');
    const vis = shown(); toggle.disabled = !vis.length;
    toggle.textContent = vis.length && vis.every((p) => picked.has(p)) ? T('fleet.pick_none') : T('fleet.pick_all');
    none.hidden = !!vis.length;
  };
  const row = ([p, n]) => {
    const icon = appIcon(p), name = el('span', { textContent: appName(p) }), sub = el('small');
    const info = (m) => {                                               // a name the TV did not give is the package: shown, so two unnamed apps are not alike
      const named = m && m.label && m.label !== p && m.label !== p.split('.').pop();
      put(name, 'textContent', appName(p)); sub.textContent = T('fleet.on_tvs', { n, total: tvs.length }) + (named ? '' : ' · ' + p);
    };
    info(META.get(p)); onMeta(p, (m) => { icon.fill(m); info(m); });
    const box = el('input', { type: 'checkbox', onchange: (e) => { e.target.checked ? picked.add(p) : picked.delete(p); r.classList.toggle('on', e.target.checked); sync(); } });
    const r = el('label', { className: 'item pick', title: p }, icon, el('div', { className: 'name' }, name, sub), box);
    r.box = box; r.key = (appName(p) + ' ' + p).toLowerCase();
    rows.set(p, r); return r;
  };
  const list = el('div', { className: 'list fleetapps' }, ...apps.map(row), none);
  const q = el('input', { type: 'search', placeholder: T('common.search_by_app_name'), autocomplete: 'off', oninput: () => {
    const k = q.value.trim().toLowerCase(); rows.forEach((r) => { r.hidden = !!k && !r.key.includes(k); }); sync(); } });
  toggle.onclick = () => {
    const vis = shown(), every = vis.every((p) => picked.has(p));
    vis.forEach((p) => { const r = rows.get(p); r.box.checked = !every; every ? picked.delete(p) : picked.add(p); r.classList.toggle('on', !every); }); sync();
  };
  go.onclick = () => {
    const ps = [...picked]; $('#infodlg').close();
    fleetDo('uninstall', ps.join(','), (ps.length === 1 ? T('fleet.remove_one_ask', { name: appName(ps[0]) }) : T('fleet.remove_many_ask', { n: ps.length })) + (ps.length > 1 ? '\n' + ps.map((p) => '• ' + appName(p)).join('\n') : ''));
  };
  const skipped = all.length - tvs.length;
  $('#infobody').replaceChildren(
    UI.note(T('fleet.remove_from', { tvs: all.map((t) => t.name || t.host).join(' · ') }) + (skipped ? ' ' + T('fleet.remove_skipped', { n: skipped }) : '')),
    el('div', { className: 'line fleetpick' }, apps.length > 6 ? q : null, toggle),
    list,
    el('div', { className: 'acts fleetgo' }, el('button', { type: 'button', textContent: T('common.cancel'), onclick: () => $('#infodlg').close() }), go));
  sync(); $('#infodlg').showModal();
}

// The welcome screen on every TV ticked (welcome.js, Welcome.kt): the file goes up from here once, the computer copies it to each TV,
// and the choices are set on each. A TV Setup from before the welcome screen says so on its own line; the others go on.
const fleetWelcomeFile = el('input', { type: 'file', hidden: true, accept: 'video/mp4,video/webm,video/x-matroska,image/jpeg,image/png,image/webp,.mp4,.m4v,.webm,.mkv,.jpg,.jpeg,.png,.webp' });
document.body.append(fleetWelcomeFile);
fleetWelcomeFile.addEventListener('change', () => {
  const f = fleetWelcomeFile.files[0]; fleetWelcomeFile.value = ''; if (!f) return;
  if (!WELCOME_FILE.test(f.name)) { toast(T('core.fr_welcome_kind')); return; }
  if (f.size > WELCOME_MAX) { toast(T('core.fr_welcome_toobig', { max: fmtSize(WELCOME_MAX) })); return; }
  const tvs = fleetChosen(); if (!tvs.length) { toast(T('fleet.pick_one')); return; }
  const cfg = { on: true, when: 'every', seconds: 10 }, video = !/\.(jpe?g|png|webp)$/i.test(f.name);
  const go = el('button', { className: 'primary', type: 'button', textContent: T('fleet.welcome_set', { n: tvs.length }), onclick: () => { $('#infodlg').close(); fleetWelcomeSend(f, tvs, cfg); } });
  const chips = (list, key) => UI.chips(list.map(([v, label, title]) => ({ label, title, on: cfg[key] === v, fn: (ev) => { cfg[key] = v; const c = ev.currentTarget; c.parentNode.querySelectorAll("button").forEach((b) => b.classList.toggle("on", b === c)); } })));
  put('#infotitle', 'textContent', T('welcome.title'));
  $('#infobody').replaceChildren(...[
    UI.item({ name: f.name, sub: fmtSize(f.size) }),
    UI.group(T('welcome.when'), chips([['every', T('welcome.every'), T('welcome.every_tip')], ['boot', T('welcome.boot'), T('welcome.boot_tip')]], 'when')),
    video ? null : UI.group(T('welcome.seconds'), chips(WELCOME_SECONDS.map((n) => [n, T('welcome.seconds_n', { n })]), 'seconds')),
    UI.note(tvs.map((t) => '• ' + (t.name || t.host)).join('\n')),
    el('div', { className: 'acts fleetgo' }, el('button', { type: 'button', textContent: T('common.cancel'), onclick: () => $('#infodlg').close() }), go)].filter(Boolean));
  $('#infodlg').showModal();
});
function fleetWelcomeSend(f, tvs, cfg) {
  const xhr = new XMLHttpRequest(), lbl = $('#fleetmore');
  xhr.open('POST', '/api/fleet/welcome?name=' + encodeURIComponent(f.name) + '&hosts=' + encodeURIComponent(tvs.map((t) => t.host).join(',')) + '&cfg=' + encodeURIComponent(JSON.stringify(cfg)));
  xhr.setRequestHeader('Content-Type', 'application/octet-stream');
  xhr.upload.onprogress = (ev) => { if (ev.lengthComputable) lbl.dataset.busy = '⏳ ' + Math.round(ev.loaded * 100 / ev.total) + '%'; };
  xhr.onloadend = () => {
    delete lbl.dataset.busy;
    if (xhr.status === 0) { fleetStopShow(null); return; }
    let r = {}; try { r = JSON.parse(xhr.responseText); } catch {}
    if (xhr.status !== 200) { toast(T('fleet.error_msg', { msg: friendly(r.error || xhr.statusText || T('core.no_server_reply')) })); return; }
    fleetFollow(r);
  };
  lbl.dataset.busy = '⏳'; fleetStopShow(() => { xhr.abort(); toast(T('fleet.stopped')); }); xhr.send(f);
}

// a home screen from the gallery on every TV ticked: one download, each TV checked and put back on its own if its home does not hold
async function fleetLauncher() {
  if (!fleetChosen().length) { toast(T('fleet.pick_one')); return; }
  const g = await api('/api/launchers', null, true); if (!g) return;
  const list = g.launchers || [];   // a Play one too: it is used on the TVs that already have it, the others are told to install it there
  put('#infotitle', 'textContent', T('fleet.which_launcher'));
  $('#infobody').replaceChildren(
    el('p', { className: 'note', textContent: T('fleet.launcher_note') }),
    UI.list(list.map((l) => launchRow(l,   // the gallery's own row (launchers.js): picture, words, Source
      el('button', { type: 'button', className: 'small primary', textContent: l.kind === 'play' ? T('launcher.use') : T('fleet.launcher_use'), onclick: () => { $('#infodlg').close(); fleetDo('launcher', l.id, T(l.kind === 'play' ? 'fleet.launcher_ask_play' : 'fleet.launcher_ask', { name: l.name })); } }))), T('launcher.none')),
    el('div', { className: 'acts fleetgo' }, el('button', { type: 'button', textContent: T('common.cancel'), onclick: () => $('#infodlg').close() })));
  $('#infodlg').showModal();
}
