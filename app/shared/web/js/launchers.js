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

function launchRow(l) {
  const thumb = el('img', { className: 'lthumb', src: l.thumb, alt: '' });
  thumb.onerror = () => thumb.classList.add('none');                   // a picture that does not come is no reason to hide the launcher
  const btns = [];
  if (l.in_use) btns.push(el('button', { className: 'small', disabled: true, textContent: T('launcher.in_use') }));
  else if (l.kind === 'play' && !l.installed) btns.push(el('button', { className: 'small primary', disabled: !l.fit, textContent: T('launcher.play'), onclick: () => launchPlay(l) }));
  else btns.push(el('button', { className: 'small primary', disabled: !l.fit || launchRunning, textContent: l.installed ? T('launcher.use') : T('launcher.install'), onclick: () => launchInstall(l) }));
  const notes = launchWords(l.notes);
  return UI.item({
    icon: thumb, name: l.name, sub: launchWords(l.desc),
    extra: [el('div', { className: 'ltags' }, l.in_use ? launchTag(T('launcher.in_use'), 'use') : null, launchTag(l.fit ? T('launcher.fits') : T('launcher.nofit'), l.fit ? '' : 'warn'),
        l.kind === 'play' ? launchTag('▶ ' + T('launcher.badge_play'), 'play') : null, l.version ? launchTag(l.version) : null),
      l.fit ? (notes ? el('small', { textContent: notes }) : null) : el('small', { className: 'warn', textContent: launchWhy(l) }),
      el('small', {}, el('a', { href: l.source, target: '_blank', rel: 'noopener noreferrer', textContent: l.kind === 'play' ? T('launcher.source_play') : T('launcher.source') }), ' · ' + T('launcher.license', { l: l.license }))],
    acts: btns });
}

async function launchLoad() {
  const body = $('#launchbody');
  body.replaceChildren(UI.empty(T('launcher.loading')));
  const r = await api('/api/launchers');
  if (!r) { body.replaceChildren(UI.warn(T('launcher.unreadable'))); return null; }
  body.replaceChildren(UI.list(r.launchers.map(launchRow), T('launcher.none')), UI.note(T('launcher.updated', { date: r.updated })));
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
