// 🏠 Launcher gallery (LauncherApi.kt): the list the project keeps in its repo (signed; the app refuses one that is not), each launcher with
// how it fits THIS TV, one tap to install it and make it the home screen. The way back lives on the TV (a question on its screen, a
// watchdog in TV Setup), so a launcher that does not work never stays; "Back to the TV's own" is always one tap.
let launchRunning = false;
onTvChange(() => { launchRunning = false; $('#launchbody').replaceChildren(); put('#launchstate', 'textContent', ''); });

const launchTag = (text, cls) => el('span', { className: 'ltag' + (cls ? ' ' + cls : ''), textContent: text });
const launchWords = (m) => (m && (m[LANG] || m.en)) || '';
const CLEANYOURTV = {
  id: 'cleanyourtv', name: 'CleanYourTV', pkg: 'com.gathin.tvhome', kind: 'source',
  source: 'https://github.com/Gathin23/cleanyourtv',
  setup: LANG === 'vi' ? 'https://github.com/Gathin23/cleanyourtv/blob/main/README.vi.md' : 'https://github.com/Gathin23/cleanyourtv/blob/main/README.md',
  themes: 'https://cleanyour.tv/addons#themes',
  thumb: 'https://cdn.jsdelivr.net/gh/Gathin23/cleanyourtv@main/themes/six-columns/screenshot.jpg',
  license: 'MIT', version: '2.6.0', fit: true, installed: false, in_use: false,
};
const SIX_COLUMNS = {
  id: 'cleanyourtv-six-columns', name: T('launcher.six_columns'), pkg: CLEANYOURTV.pkg, kind: 'theme',
  source: CLEANYOURTV.source, themes: CLEANYOURTV.themes,
  thumb: 'https://cdn.jsdelivr.net/gh/Gathin23/cleanyourtv@main/themes/six-columns/screenshot.jpg',
  license: 'MIT', version: '', fit: true, installed: false, in_use: false,
};
// a table of ids, so each one is named where the page can see it
const LAUNCH_WHY = { sdk: 'launcher.why_sdk', abi: 'launcher.why_abi', fire: 'launcher.why_fire', not_tv: 'launcher.why_not_tv', atv: 'launcher.why_atv', gtv: 'launcher.why_gtv' };
const LAUNCH_STEP = { download: 'launcher.step_download', install: 'launcher.step_install', swap: 'launcher.step_swap', confirm: 'launcher.step_confirm' };
// a language's own name in the page's language (Intl knows them all; a WebView without it shows the code)
const langName = (c) => { try { return new Intl.DisplayNames([document.documentElement.lang || 'en'], { type: 'language' }).of(c); } catch (e) { return c; } };
// what it shows when it lacks the TV's language: English (set so, or its own fallback), or another one
const launchLang = (l) => T(l.shows === 'en' ? 'launcher.lang_en' : 'launcher.lang_other', { lang: langName(l.shows) });
const launchWhy = (l) => T(LAUNCH_WHY[l.why] || 'launcher.why_atv', { android: androidName(l.min_sdk) });

// One launcher as a card: its home screen big (what one picks a launcher by), the name, a few tags, the button for what comes next,
// and ⋯ for the rest (details, source or Play page, the APK). [fleet]: the button for several TVs at once (fleet.js), and no fit /
// in use, which are about this TV only.
function launchRow(l, fleet) {
  const thumb = el('img', { src: l.thumb, alt: '', loading: 'lazy' });
  thumb.onerror = () => thumb.classList.add('none');                   // a picture that does not come is no reason to hide the launcher
  let act;
  if (fleet) act = fleet;
  else if (l.kind === 'theme') act = el('button', { className: 'small primary', disabled: !l.installed || launchRunning,
    textContent: T(l.installed ? 'launcher.theme_apply' : 'launcher.theme_requires_launcher'), onclick: () => launchTheme(l) });
  else if (l.in_use) act = el('button', { className: 'small', disabled: true, textContent: T('launcher.in_use') });
  else if (l.kind === 'play' && !l.installed) act = el('button', { className: 'small primary', disabled: !l.fit, textContent: T('launcher.play'), onclick: () => launchPlay(l) });
  else if (l.kind === 'source') act = el('button', { className: 'small primary', textContent: T('launcher.setup'), onclick: () => open(l.setup, '_blank', 'noopener') });
  else act = el('button', { className: 'small primary', disabled: !l.fit || launchRunning, textContent: l.installed ? T('launcher.use') : T('launcher.install'), onclick: () => launchInstall(l) });
  const page = l.kind === 'play' ? 'https://play.google.com/store/apps/details?id=' + encodeURIComponent(l.pkg) : l.source;
  const more = el('button', { type: 'button', className: 'small iconbtn', textContent: '⋯', title: T('page.more_actions'), 'aria-label': T('page.more_actions'), onclick: (e) => popover(e.currentTarget, [
    ['ℹ️', T('launcher.details'), () => launchDetails(l)],
    ['🔗', T(l.kind === 'play' ? 'launcher.source_play' : 'launcher.source'), () => open(page, '_blank', 'noopener')],
    l.themes ? ['🎨', T('launcher.themes'), () => open(l.themes, '_blank', 'noopener')] : null,
    l.kind === 'source' || l.kind === 'theme' || l.kind === 'play' ? null : ['⬇️', T('launcher.download'), () => open(l.source.replace(/\/$/, '') + '/releases/latest', '_blank', 'noopener')],
  ]) });
  const tags = el('div', { className: 'ltags' },
    l.kind === 'source' ? launchTag(T('launcher.source_only')) : null,
    l.kind === 'theme' ? launchTag(T('launcher.theme')) : null,
    l.kind === 'self' ? launchTag(T('launcher.ours'), 'ours') : null,      // TV Home: made here, set up from here
    !fleet && l.in_use ? launchTag(T('launcher.in_use'), 'use') : null,
    fleet || l.in_use || l.kind === 'source' || l.kind === 'theme' ? null : l.fit ? launchTag(T('launcher.fits')) : Object.assign(launchTag(T('launcher.nofit'), 'warn'), { title: launchWhy(l) }),
    l.shows ? Object.assign(launchTag(langName(l.shows), l.shows === 'en' ? '' : 'warn'), { title: launchLang(l) }) : null,   // no words in the TV's language
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
    el('p', { textContent: l.kind === 'source' ? T('launcher.clean_desc') : l.kind === 'theme' ? T('launcher.six_desc') : launchWords(l.desc) }),
    l.kind === 'source' ? el('p', { className: 'note', textContent: T('launcher.clean_note') }) : l.kind === 'theme' ? el('p', { className: 'note', textContent: T('launcher.theme_only') }) : notes ? el('p', { className: 'note', textContent: notes }) : null,
    l.fit ? null : el('p', { className: 'warnline', textContent: launchWhy(l) }),
    l.shows ? el('p', { className: l.shows === 'en' ? 'note' : 'warnline', textContent: launchLang(l) }) : null,
    el('p', { className: 'note' }, el('a', { href: l.kind === 'play' ? 'https://play.google.com/store/apps/details?id=' + encodeURIComponent(l.pkg) : l.source, target: '_blank', rel: 'noopener noreferrer', textContent: T(l.kind === 'play' ? 'launcher.source_play' : 'launcher.source') }),
      (l.license ? ' · ' + T('launcher.license', { l: l.license }) : '') + (l.version ? ' · ' + l.version : '')),
    l.themes ? el('p', { className: 'note' }, el('a', { href: l.themes, target: '_blank', rel: 'noopener noreferrer', textContent: T('launcher.themes') })) : null));
  d.addEventListener('close', () => d.remove());
  document.body.append(d); d.showModal();
}

async function launchLoad() {
  const body = $('#launchbody');
  body.replaceChildren(UI.empty(T('launcher.loading')));
  const r = await api('/api/launchers');
  if (!r) { body.replaceChildren(UI.warn(T('launcher.unreadable'))); return null; }
  CLEANYOURTV.installed = SIX_COLUMNS.installed = r.clean_installed;
  SIX_COLUMNS.in_use = r.home === CLEANYOURTV.pkg;
  body.replaceChildren(launchGrid([...r.launchers, CLEANYOURTV, SIX_COLUMNS], (l) => launchRow(l)), UI.note(T('launcher.updated', { date: r.updated })));
  put('#launchstate', 'textContent', r.record === 'pending' ? T('launcher.waiting') : '');
  return r;
}

$('#launchopen').addEventListener('click', async () => { $('#launchdlg').showModal(); await launchLoad(); });
$('#launchclose').addEventListener('click', () => $('#launchdlg').close());

// the install runs on this device (it downloads once, pushes to the TV); its steps show in the page's Progress card, where the eyes
// go (the gallery closes: a modal would cover it), and the question "Keep it?" is answered there or on the TV
async function launchInstall(l) {
  if (launchRunning) return;
  if (!l.in_use && !await ask(T(l.installed ? 'launcher.use_ask' : 'launcher.ask', { name: l.name }), { ok: T(l.installed ? 'launcher.use' : 'launcher.install'), warn: l.shows ? launchLang(l) : '' })) return;
  let j = await api('/api/launcher/install', { id: l.id });
  if (!j) return;
  launchRunning = true; $('#launchdlg').close();
  const label = T('launcher.job', { name: l.name }), id = j.id, stop = () => api('/api/cancel', { id }, true);
  const show = (x) => showJob({ ...x, id }, label, stop);
  const step = (x) => ({ status: 'running', msg: T(LAUNCH_STEP[x.step] || LAUNCH_STEP.download, { pct: x.pct || 0, s: x.confirm == null ? 30 : x.confirm }),
    pct: (x.step || 'download') === 'download' ? x.pct || 0 : undefined });
  const row = show(step(j)), acts = el('div', { className: 'actions' });
  row.root.append(acts);
  for (let misses = 0; j && j.status === 'running' && misses < 10;) {
    show(step(j));
    if (j.step === 'confirm' && !acts.firstChild) acts.append(
      el('button', { className: 'small primary', textContent: T('launcher.keep'), onclick: () => api('/api/launcher/answer', { keep: true }) }),
      el('button', { className: 'small', textContent: T('launcher.go_back'), onclick: () => api('/api/launcher/answer', { keep: false }) }));
    if (j.step !== 'confirm') acts.replaceChildren();
    await sleep(1000); const r = await api('/api/job?id=' + id, null, true);
    if (!r) { misses++; continue; }
    misses = 0; j = r;
  }
  launchRunning = false; acts.remove();
  if (!j || j.status === 'running') show({ status: 'error', msg: T('launcher.lost'), pct: 0 });
  else if (j.status === 'done') { show({ status: 'done', msg: T('launcher.done', { name: l.name }), pct: 100 }); toast(T('launcher.done', { name: l.name })); }
  else if (j.status === 'cancelled' || /launcher:rolledback:user/.test(j.msg)) show({ status: 'cancelled', said: j.status !== 'cancelled', msg: friendly(j.msg), pct: 0 });   // Go back is an answer, not an error
  else show({ status: 'error', msg: friendly(j.msg), pct: 0 });
  launchLoad();
}

async function launchPlay(l) {
  if (await api('/api/launcher/play', { id: l.id })) toast(T('launcher.play_opened'));
}

async function launchTheme(l) {
  if (launchRunning || !l.installed || !await ask(T('launcher.theme_ask'), { ok: T('launcher.theme_apply') })) return;
  const j = await api('/api/launcher/theme', { id: l.id });
  if (!j) return;
  launchRunning = true; $('#launchdlg').close();
  const id = j.id, label = T('launcher.theme_job'), stop = () => api('/api/cancel', { id }, true);
  const show = (x) => showJob({ ...x, id }, label, stop);
  show(j);
  let misses = 0;
  while (j.status === 'running' && misses < 10) {
    await sleep(1000);
    const r = await api('/api/job?id=' + id, null, true);
    if (!r) { misses++; continue; }
    misses = 0; show(r); Object.assign(j, r);
  }
  launchRunning = false;
  if (j.status === 'done') { show({ ...j, msg: T('launcher.theme_done') }); toast(T('launcher.theme_done')); }
  else if (j.status === 'cancelled') show({ ...j, msg: T('files.stopped') });
  else show({ ...j, status: 'error', msg: friendly(j.msg || 'launcher:theme_restart') });
  await launchLoad();
}

$('#launchback').addEventListener('click', launchRestore);
async function launchRestore() {
  if (launchRunning || !await ask(T('launcher.restore_ask'), { ok: T('launcher.restore') })) return;
  if (await api('/api/launcher/restore', {})) { toast(T('launcher.restored')); if ($('#launchdlg').open) await launchLoad(); }
}
