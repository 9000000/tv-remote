// 🏠 Launcher gallery (LauncherApi.kt): the list the project keeps in its repo (signed; the app refuses one that is not), each launcher with
// how it fits THIS TV, one tap to install it and make it the home screen. The way back lives on the TV (a question on its screen, a
// watchdog in TV Setup), so a launcher that does not work never stays; "Back to the TV's own" is always one tap.
let launchRunning = false;
onTvChange(() => { launchRunning = false; $('#launchbody').replaceChildren(); put('#launchstate', 'textContent', ''); });

const launchTag = (text, cls) => el('span', { className: 'ltag' + (cls ? ' ' + cls : ''), textContent: text });
const launchWords = (m) => (m && (m[LANG] || m.en)) || '';
// a table of ids, so each one is named where the page can see it
const LAUNCH_WHY = { sdk: 'launcher.why_sdk', abi: 'launcher.why_abi', fire: 'launcher.why_fire', not_tv: 'launcher.why_not_tv', atv: 'launcher.why_atv', gtv: 'launcher.why_gtv' };
const LAUNCH_STEP = { download: 'launcher.step_download', install: 'launcher.step_install', swap: 'launcher.step_swap', confirm: 'launcher.step_confirm' };
const launchWhy = (l) => T(LAUNCH_WHY[l.why] || 'launcher.why_atv', { android: androidName(l.min_sdk) });

// One launcher as a card: its home screen big (what one picks a launcher by), the name, a few tags, the button for what comes next,
// and ⋯ for the rest (details, source or Play page, the APK). [fleet]: the button for several TVs at once (fleet.js), and no fit /
// in use, which are about this TV only.
function launchRow(l, fleet) {
  const thumb = el('img', { src: l.thumb, alt: '', loading: 'lazy' });
  thumb.onerror = () => thumb.classList.add('none');                   // a picture that does not come is no reason to hide the launcher
  let act;
  if (fleet) act = fleet;
  else if (l.in_use) act = el('button', { className: 'small', disabled: true, textContent: T('launcher.in_use') });
  else if (l.kind === 'play' && !l.installed) act = el('button', { className: 'small primary', disabled: !l.fit, textContent: T('launcher.play'), onclick: () => launchPlay(l) });
  else act = el('button', { className: 'small primary', disabled: !l.fit || launchRunning, textContent: l.installed ? T('launcher.use') : T('launcher.install'), onclick: () => launchInstall(l) });
  const page = l.kind === 'play' ? 'https://play.google.com/store/apps/details?id=' + encodeURIComponent(l.pkg) : l.source;
  const more = el('button', { type: 'button', className: 'small iconbtn', textContent: '⋯', title: T('page.more_actions'), 'aria-label': T('page.more_actions'), onclick: (e) => popover(e.currentTarget, [
    ['ℹ️', T('launcher.details'), () => launchDetails(l)],
    ['🔗', T(l.kind === 'play' ? 'launcher.source_play' : 'launcher.source'), () => open(page, '_blank', 'noopener')],
    l.kind === 'play' ? null : ['⬇️', T('launcher.download'), () => open(l.source.replace(/\/$/, '') + '/releases/latest', '_blank', 'noopener')],
  ]) });
  const tags = el('div', { className: 'ltags' },
    l.kind === 'self' ? launchTag(T('launcher.ours'), 'ours') : null,      // TV Home: made here, set up from here
    !fleet && l.in_use ? launchTag(T('launcher.in_use'), 'use') : null,
    fleet || l.in_use ? null : l.fit ? launchTag(T('launcher.fits')) : Object.assign(launchTag(T('launcher.nofit'), 'warn'), { title: launchWhy(l) }),
    l.kind === 'play' ? launchTag('▶ ' + T('launcher.badge_play'), 'play') : null, l.version ? launchTag(l.version) : null);
  return el('div', { className: 'lcard' + (l.in_use && !fleet ? ' on' : '') },
    el('button', { type: 'button', className: 'lshot', title: T('launcher.details'), 'aria-label': l.name, onclick: () => launchDetails(l) }, thumb),
    el('div', { className: 'lbody' }, el('b', { textContent: l.name }), tags,
      fleet || l.fit ? null : el('small', { className: 'warn', textContent: launchWhy(l) }),
      el('div', { className: 'lacts' }, act, more)));
}
const launchGrid = (list, row) => list.length ? el('div', { className: 'lgrid' }, ...list.map(row)) : UI.empty(T('launcher.none'));

// ℹ️: the picture big, what it is, what to know, its licence
function launchDetails(l) {
  const notes = launchWords(l.notes);
  const d = el('dialog', { className: 'ask ldetail' }, el('div', { className: 'card' },
    el('div', { className: 'card-head' }, el('h2', { textContent: l.name }), el('button', { type: 'button', className: 'small iconbtn', textContent: '✕', title: T('common.close'), 'aria-label': T('common.close'), onclick: () => d.close() })),
    el('img', { className: 'lbig', src: l.thumb, alt: '' }),
    el('p', { textContent: launchWords(l.desc) }),
    notes ? el('p', { className: 'note', textContent: notes }) : null,
    l.fit ? null : el('p', { className: 'warnline', textContent: launchWhy(l) }),
    el('p', { className: 'note' }, el('a', { href: l.kind === 'play' ? 'https://play.google.com/store/apps/details?id=' + encodeURIComponent(l.pkg) : l.source, target: '_blank', rel: 'noopener noreferrer', textContent: T(l.kind === 'play' ? 'launcher.source_play' : 'launcher.source') }),
      (l.license ? ' · ' + T('launcher.license', { l: l.license }) : '') + (l.version ? ' · ' + l.version : ''))));
  d.addEventListener('close', () => d.remove());
  document.body.append(d); d.showModal();
}

async function launchLoad() {
  const body = $('#launchbody');
  body.replaceChildren(UI.empty(T('launcher.loading')));
  const r = await api('/api/launchers');
  if (!r) { body.replaceChildren(UI.warn(T('launcher.unreadable'))); return null; }
  body.replaceChildren(launchGrid(r.launchers, (l) => launchRow(l)), UI.note(T('launcher.updated', { date: r.updated })));
  put('#launchstate', 'textContent', r.record === 'pending' ? T('launcher.waiting') : '');
  return r;
}

$('#launchopen').addEventListener('click', async () => { $('#launchdlg').showModal(); await launchLoad(); });
$('#launchclose').addEventListener('click', () => $('#launchdlg').close());

// the install runs on this device (it downloads once, pushes to the TV); the page follows its steps, and the question "Keep it?" is answered here or on the TV
async function launchInstall(l) {
  if (launchRunning) return;
  if (!l.in_use && !await ask(T('launcher.ask', { name: l.name }), { ok: T('launcher.install') })) return;
  launchRunning = true;
  const line = $('#launchstate'), actions = $('#launchact');
  const stop = el('button', { className: 'small danger', textContent: T('page.stop') });
  actions.replaceChildren(stop);
  let j = await api('/api/launcher/install', { id: l.id });
  if (j) {
    stop.onclick = () => { api('/api/cancel', { id: j.id }, true); stop.disabled = true; };
    for (let misses = 0; j && j.status === 'running' && misses < 10;) {
      line.textContent = T(LAUNCH_STEP[j.step] || LAUNCH_STEP.download, { pct: j.pct || 0, s: j.confirm == null ? 30 : j.confirm });
      if (j.step === 'confirm' && !actions.querySelector('.keep')) actions.replaceChildren(
        el('button', { className: 'small primary keep', textContent: T('launcher.keep'), onclick: () => api('/api/launcher/answer', { keep: true }) }),
        el('button', { className: 'small', textContent: T('launcher.go_back'), onclick: () => api('/api/launcher/answer', { keep: false }) }));
      await sleep(1000); const r = await api('/api/job?id=' + j.id, null, true);
      if (!r) { misses++; continue; }
      misses = 0; j = r;
    }
  }
  launchRunning = false; actions.replaceChildren();
  if (!j || j.status === 'running') line.textContent = T('launcher.lost');
  else if (j.status === 'done') { line.textContent = ''; toast(T('launcher.done', { name: l.name })); }
  else if (j.status === 'cancelled') line.textContent = T('launcher.stopped');
  else line.textContent = '❌ ' + friendly(j.msg);
  await launchLoad();
}

async function launchPlay(l) {
  if (await api('/api/launcher/play', { id: l.id })) toast(T('launcher.play_opened'));
}

$('#launchback').addEventListener('click', launchRestore);
async function launchRestore() {
  if (launchRunning || !await ask(T('launcher.restore_ask'), { ok: T('launcher.restore') })) return;
  if (await api('/api/launcher/restore', {})) { toast(T('launcher.restored')); if ($('#launchdlg').open) await launchLoad(); }
}
