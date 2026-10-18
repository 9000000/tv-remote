// Automation tab: TV Setup's sleep timer, bedtime and daily app limits (they run on the TV itself, see app/tv .../Rules.kt),
// and scenes, one button that does several things (each remote keeps its own list, see Scenes.kt).
const SETUP_APK = 'https://github.com/HoangTran0410/tv-remote/releases/latest/download/tv-setup.apk';
let RULES = null, rulesSkew = 0, rulesBusy = false;
const pad2 = (n) => String(n).padStart(2, '0');
const clock = (t) => { const d = new Date(t - rulesSkew); return pad2(d.getHours()) + ':' + pad2(d.getMinutes()); };   // a moment on the TV's clock, in this device's
const mins = (m) => m < 60 ? T('auto.min_n', { n: m }) : m % 60 ? T('auto.hours_min_n', { h: Math.floor(m / 60), m: m % 60 }) : T('auto.hours_n', { h: Math.floor(m / 60) });

async function loadRules(change, quiet) {
  if (rulesBusy && !change) return;
  rulesBusy = true;
  const r = await api('/api/rules', change || null, quiet);
  rulesBusy = false;
  if (!r) return null;
  RULES = r; if (r.now) rulesSkew = r.now - Date.now();
  renderRules(); return r;
}

function renderRules() {
  const r = RULES || {};
  const box = $('#autosetup');
  if (r.setup === 'busy') {                                            // being updated or starting: no button, asked again soon
    put('#rulecards', 'hidden', true);
    box.replaceChildren(UI.say(T('auto.restarting')));
    clearTimeout(renderRules.again); renderRules.busy = (renderRules.busy || 0) + 1;   // an am broadcast each time: 4 s, slower as it goes on, and only while this tab is looked at
    renderRules.again = setTimeout(() => { if (currentTab === 'auto' && !document.hidden) loadRules(null, true); }, Math.min(30000, 4000 * renderRules.busy));
    return;
  }
  renderRules.busy = 0;
  if (r.setup || r.error) {                                            // no TV Setup on the TV (or one too old): say what it brings and offer it
    put('#rulecards', 'hidden', true);
    box.replaceChildren(UI.card(T('auto.card_title'), {},
      el('p', { textContent: r.error ? T('auto.setup_error', { why: friendly(r.error) })
        : r.setup === 'old' ? T('auto.setup_old', { min: r.min || '0.1.0' })
        : T('auto.setup_missing') }),
      el('div', { className: 'actions' }, act(T(r.setup === 'old' ? 'auto.update_setup' : 'auto.install_setup'), r.setup === 'old' ? updateSetup : installSetupApk, 'primary'))));
    return;
  }
  box.replaceChildren(); put('#rulecards', 'hidden', false);
  // sleep timer
  const left = r.sleep_at ? Math.max(0, Math.round((r.sleep_at - r.now) / 60000)) : 0;
  put('#sleepstate', 'textContent', r.sleep_at ? T('auto.sleep_at', { at: clock(r.sleep_at), left: mins(left) }) : T('auto.no_timer_set'));
  $('#sleepstate').classList.toggle('on', !!r.sleep_at);
  $('#sleepbtns').replaceChildren(...[15, 30, 45, 60, 90, 120].map((m) => el('button', { className: 'small', textContent: mins(m), onclick: () => setSleep(m) })));
  $('#sleepacts').replaceChildren(...(r.sleep_at ? [act(T('auto.plus_15'), async () => { await loadRules({ do: 'sleep_add', minutes: 15 }); }), act(T('auto.cancel_timer'), async () => setSleep(0), 'danger')] : []));
  // bedtime
  const b = r.bedtime || {}, beds = bedRanges();
  if (!$('#rulecards').contains(document.activeElement)) $('#bedon').checked = !!b.on;
  $('#beds').replaceChildren(...beds.map((v, i) => el('div', { className: 'item' },
    el('div', { className: 'name' }, el('span', { textContent: v.from + ' – ' + v.to })),
    el('div', { className: 'acts' },
      act('🗑', async () => { if (await ask(T('auto.bed_remove_ask', { from: v.from, to: v.to }), { ok: T('auto.drop') })) await saveBeds(beds.filter((x, j) => j !== i)); }, 'danger')))));
  const snoozed = r.snooze_until > r.now;
  put('#bedstate', 'textContent', !b.on ? '' : snoozed ? T('auto.bed_snoozed', { at: clock(r.snooze_until) }) : r.in_bedtime ? T('auto.bed_now')
    : T('auto.bed_on', { ranges: beds.map((v) => v.from + ' – ' + v.to).join(', ') }));
  $('#bedacts').replaceChildren(...(!b.on ? [] : snoozed ? [act(T('auto.bed_resume'), async () => { await loadRules({ do: 'snooze', minutes: 0 }); })]
    : r.in_bedtime ? [act(T('auto.bed_snooze'), async () => { await loadRules({ do: 'snooze', minutes: 30 }); })] : []));
  // app limits
  const rows = (r.limits || []).map((l) => {
    const total = l.minutes + (l.extra || 0), pct = Math.min(100, Math.round(l.used * 100 / Math.max(1, total)));
    const icon = appIcon(l.pkg); onMeta(l.pkg, (m) => icon.fill(m));
    const meter = el('div', { className: 'bar' + (pct >= 100 ? ' full' : '') }, el('i')); meter.firstChild.style.width = pct + '%';
    const name = l.label || appName(l.pkg);
    return el('div', { className: 'item' }, icon, el('div', { className: 'name' }, el('span', { textContent: name }),
      el('small', { textContent: l.extra ? T('auto.limit_used_extra', { used: mins(l.used), total: mins(total), extra: mins(l.extra) }) : T('auto.limit_used', { used: mins(l.used), total: mins(total) }) }), meter),
      el('div', { className: 'acts' },
        act(T('auto.plus_30_today'), async () => { await loadRules({ do: 'extra', pkg: l.pkg, minutes: 30 }); toast(T('auto.plus_30_done', { name })); }),
        act('✏️', async () => { const v = await askTime(T('auto.limit_how_long', { name }), l.minutes); if (v) await loadRules({ do: 'limit', pkg: l.pkg, minutes: v }); }),
        act('🗑', async () => { if (await ask(T('auto.limit_drop_ask', { name }), { ok: T('auto.drop_limit') })) await loadRules({ do: 'limit', pkg: l.pkg, minutes: 0 }); }, 'danger')));
  });
  $('#limits').replaceChildren(...(rows.length ? rows : [UI.empty(T('auto.no_limits'))]));
  // volume limits; a TV Setup older than them has no "volume" in its answer
  const vols = r.volume || [];
  $('#vols').replaceChildren(...(vols.length ? vols.map((v, i) => el('div', { className: 'item' },
    el('div', { className: 'name' }, el('span', { textContent: v.from + ' – ' + v.to }), el('small', { textContent: T('auto.vol_max', { max: v.max }) })),
    el('div', { className: 'acts' },
      act('🗑', async () => { if (await ask(T('auto.vol_drop_ask', { from: v.from, to: v.to }), { ok: T('auto.drop_limit') })) await saveVols(vols.filter((x, j) => j !== i)); }, 'danger'))))
    : [UI.empty(T('auto.no_vol_limits'))]));
  const now = r.volume_now;
  put('#volstate', 'textContent', r.volume === undefined ? '' : r.volume_fixed ? T('auto.vol_fixed')
    : now ? T('auto.vol_now', { max: now.max, to: now.to }) : '');
  // the memory notice; a TV Setup older than it has no "ram" in its answer
  const ram = r.ram || { on: false, pct: 90 };
  if (document.activeElement !== $('#ramon')) $('#ramon').checked = !!ram.on;
  $('#rampct').replaceChildren(...[80, 85, 90, 95].map((p) => el('button', { type: 'button', className: p === ram.pct ? 'on' : '', textContent: p + '%',
    onclick: () => saveRam(true, p) })));
  put('#ramstate', 'textContent', ram.on ? T('auto.ram_on', { pct: ram.pct }) : '');
  if (CORE === 'tv' && r.adb && r.adb !== 'ready') $('#bedstate').append(el('span', { className: 'warnline', textContent: T('auto.setup_no_adb') }));
}

// "40" is minutes from now, "23:30" / "23h30" / "23h" a time of day (the next one); the same reading as sleepMinutes in Automation.kt.
function sleepMinutes(v, now = new Date()) {
  v = v.trim().toLowerCase();
  if (/^\d+$/.test(v)) return +v >= 1 && +v <= 1440 ? +v : 0;
  const m = /^(\d{1,2})\s*[:hg.]\s*(\d{2})?$/.exec(v);
  if (!m || +m[1] > 23 || +(m[2] || 0) > 59) return 0;
  const at = new Date(now); at.setHours(+m[1], +(m[2] || 0), 0, 0);
  if (at <= now) at.setDate(at.getDate() + 1);
  return Math.max(1, Math.round((at - now) / 60000));
}
$('#sleepf').addEventListener('submit', async (e) => {
  e.preventDefault();
  const m = sleepMinutes($('#sleepin').value);
  if (!m) return toast(T('auto.sleep_bad'));
  $('#sleepin').value = ''; await setSleep(m);
});
async function setSleep(m) {
  const r = await loadRules({ do: 'sleep', minutes: m });
  if (r) toast(m ? T('auto.sleep_set', { at: clock(r.sleep_at) }) : T('auto.sleep_cancelled'));
}

// a TV Setup older than several ranges answers with from and to only
const bedRanges = () => { const b = (RULES && RULES.bedtime) || {}; return b.ranges || (b.from ? [{ from: b.from, to: b.to }] : []); };
async function saveBeds(list, on = list.length > 0) {
  const r = await loadRules({ do: 'bedtime', on: on && list.length > 0, ranges: list, from: list.length ? list[0].from : '22:00', to: list.length ? list[0].to : '06:30' });
  if (r && !r.bedtime.on) toast(T('auto.bed_off'));
  return r;
}
// ＋ Thêm opens its form in a dialog: the cards keep to what is set
document.querySelectorAll('[data-open]').forEach((b) => b.addEventListener('click', () => $('#' + b.dataset.open).showModal()));
document.querySelectorAll('dialog [data-close]').forEach((b) => b.addEventListener('click', () => b.closest('dialog').close()));
$('#bedf').addEventListener('submit', async (e) => {
  e.preventDefault();
  const v = { from: $('#bedfrom').value || '22:00', to: $('#bedto').value || '06:30' };
  if (v.from === v.to) return toast(T('auto.times_differ'));
  const b = (RULES && RULES.bedtime) || {};
  const keep = b.on ? bedRanges() : [];                                // turned off, the old ranges are not wanted back
  if (keep.length && !b.ranges && !await setupFor('bedtimes')) return;
  if (await saveBeds([...keep, v])) $('#beddlg').close(), toast(T('auto.bed_added', { from: v.from, to: v.to }));
});
$('#bedon').addEventListener('change', async () => {
  const list = bedRanges().length ? bedRanges() : [{ from: $('#bedfrom').value || '22:00', to: $('#bedto').value || '06:30' }];
  const r = await saveBeds(list, $('#bedon').checked);
  if (r && r.bedtime.on) toast(T('auto.bed_enabled'));
});
async function saveRam(on, pct) {
  if (RULES && RULES.ram === undefined && !await setupFor('ram')) { $('#ramon').checked = false; return null; }
  return loadRules({ do: 'ram', on, pct: pct || (RULES && RULES.ram && RULES.ram.pct) || 90 });
}
$('#ramon').addEventListener('change', () => saveRam($('#ramon').checked));
async function saveVols(list) {
  if (RULES && RULES.volume === undefined && !await setupFor('volume')) return null;
  return loadRules({ do: 'volume', ranges: list });
}
$('#volf').addEventListener('submit', async (e) => {
  e.preventDefault();
  const v = { from: $('#volfrom').value || '22:00', to: $('#volto').value || '06:00', max: +$('#volmax').value };
  if (v.from === v.to) return toast(T('auto.times_differ'));
  if (await saveVols([...((RULES && RULES.volume) || []), v])) $('#voldlg').close(), toast(T('auto.vol_added', { max: v.max, from: v.from, to: v.to }));
});
$('#limitf').addEventListener('submit', async (e) => {
  e.preventDefault(); const pkg = $('#limitpkg').value; if (!pkg) return;
  if (await loadRules({ do: 'limit', pkg, minutes: +$('#limitmin').value })) $('#limitdlg').close(), toast(T('auto.limit_added', { name: appName(pkg), time: mins(+$('#limitmin').value) }));
});

// the apps to choose from: the ones with an icon on the TV's home screen
let pickApps = [];
async function loadPickApps() {
  const r = await api('/api/apps', null, true); if (!r) return;
  seedMeta(r.apps); pickApps = r.apps.map((a) => a.pkg);
  const fill = () => {
    const keep = $('#limitpkg').value;
    $('#limitpkg').replaceChildren(el('option', { value: '', textContent: T('auto.pick_app') }), ...pickApps.slice().sort((a, b) => appName(a).localeCompare(appName(b))).map((p) => el('option', { value: p, textContent: appName(p) })));
    $('#limitpkg').value = keep;
  };
  fill(); pickApps.filter((p) => !(META.get(p) || {}).label).forEach((p) => onMeta(p, () => fill()));
}

async function installSetupApk() {
  toast(T('auto.downloading_setup'));
  let j = await api('/api/install', { url: SETUP_APK }); if (!j) return;
  for (let misses = 0; j && j.status === 'running' && misses < 10;) { await sleep(1500); const r = await api('/api/job?id=' + j.id, null, true); if (r) { j = r; misses = 0; } else misses++; }
  if (!j || j.status !== 'done') { toast(T('auto.install_failed', { why: friendly(j ? j.msg : 'mất kết nối') })); return; }
  await api('/api/app/launch', { pkg: 'dev.tvremote.setup' }, true);    // opened once, it starts its service and keeps it across restarts
  toast(T('auto.setup_installed')); await sleep(2500); loadRules(null, true);
}

// ---------- TV Setup's own updates (SetupUpdate.kt): offered on every remote; the TV puts the old version back if a new one does not work ----------
let setupLater = '', setupBusy = false, setupChecked = '';           // setupChecked: the TV whose TV Setup version was looked at
onTvChange(() => { setupChecked = ''; put('#setupupd', 'hidden', true); });
const seenUndo = () => { try { return localStorage.getItem('setupUndo') || ''; } catch (e) { return ''; } };
async function checkSetup() {
  const host = setupChecked = curHost || '-';
  const r = await fetch('/api/setup/version').then((x) => x.ok ? x.json() : null).catch(() => null);
  if (host !== (curHost || '-')) return null;                         // another TV was picked meanwhile: not its banner
  if (!setupBusy) showSetupUpd(r);
  return r;
}
function showSetupUpd(r) {
  const b = $('#setupupd');
  const undone = r && r.state === 'rolledback' && Date.now() - r.at < 3 * 86400000 && seenUndo() !== String(r.at);
  b.hidden = picking || !r || !(undone || r.latest && r.latest !== setupLater);
  if (b.hidden) return;
  b.className = 'banner ' + (undone ? 'warn' : 'ok');
  if (undone) {
    put('#suptitle', 'textContent', T('auto.sup_broke', { v: r.version }));
    put('#supnote', 'textContent', T('auto.sup_rolled_back', { from: r.from }));
    put('#supgo', 'hidden', true); put('#suplater', 'textContent', T('page.got_it'));
    $('#suplater').onclick = () => { try { localStorage.setItem('setupUndo', String(r.at)); } catch (e) { } r.state = ''; showSetupUpd(r); };   // then the update, if there is one
    return;
  }
  put('#suptitle', 'textContent', T('auto.sup_new', { latest: r.latest, installed: r.installed }));
  put('#supnote', 'textContent', T(CORE === 'tv' ? 'auto.sup_half_minute_tv' : 'auto.sup_half_minute'));
  put('#supgo', 'hidden', false); put('#suplater', 'textContent', T('common.later'));
  $('#suplater').onclick = () => { setupLater = r.latest; b.hidden = true; };
}
$('#supgo').addEventListener('click', () => updateSetup());
async function updateSetup() {
  if (setupBusy) return;
  setupBusy = true; put('#supacts', 'hidden', true);
  const b = $('#setupupd'); b.hidden = false; b.className = 'banner ok';   // also from the Automation tab, with the banner put away
  if (!$('#suptitle').textContent) put('#suptitle', 'textContent', T('auto.sup_updating'));   // or from the phone's remote screen, never shown
  const note = (t) => { put('#supnote', 'textContent', t); };
  try {
    note(T('auto.starting'));
    let j = await api('/api/setup/update', {}); if (!j) return;
    for (let misses = 0; j && j.status === 'running' && misses < 4;) {
      note(j.msg === 'download' ? T('auto.step_download', { pct: j.pct }) : j.msg === 'installing' ? T('auto.step_installing') : j.msg === 'checking' ? T('auto.step_checking') : T('auto.step_default'));
      await sleep(1500);
      const r = await fetch('/api/job?id=' + j.id).then((x) => x.ok ? x.json() : null).catch(() => null);
      if (r) { j = r; misses = 0; } else misses++;
    }
    if (j && j.status === 'done') { toast(T('auto.setup_updated', { v: j.version })); put('#setupupd', 'hidden', true); loadRules(null, true); return; }
    if (j && j.status === 'error') {
      if (!/^setup:(rolledback|broken)/.test(j.msg)) { note(j.msg === 'setup:slow' ? T('auto.update_slow') : T('auto.update_failed', { why: friendly(j.msg) })); return; }
    } else {                                                           // lost: on the TV's own page the update closes the server serving it
      note(T('auto.update_reconnect'));
      for (let i = 0; i < 60; i++) {
        await sleep(3000);
        const r = await fetch('/api/setup/version').then((x) => x.ok ? x.json() : null).catch(() => null);
        if (r && r.state !== 'installing' && r.state !== 'checking') { if (CORE === 'tv' && r.state === 'ok') { location.reload(); return; } break; }
      }
    }
    setupBusy = false;
    const r = await checkSetup();                                       // ok, or put back: the banner says which
    if (r && r.state === 'ok' && !r.latest) { toast(T('auto.setup_updated', { v: r.installed })); put('#setupupd', 'hidden', true); }
  } finally {
    setupBusy = false; put('#supacts', 'hidden', false);
  }
}

// ---------- scenes ----------
let SCENES = [];
const STEP_KEYS = [['MUTE', T('auto.key_mute')], ['PLAY_PAUSE', T('remote.play_pause')], ['HOME', 'Home'], ['BACK', T('auto.key_back')], ['VOL_UP', T('remote.volume_up')], ['VOL_DOWN', T('remote.volume_down')],
  ['OK', 'OK'], ['UP', T('remote.up')], ['DOWN', T('remote.down')], ['LEFT', T('remote.left')], ['RIGHT', T('remote.right')], ['INPUT', T('auto.key_input')], ['SUBTITLE', T('auto.key_subtitle')], ['CH_UP', T('remote.ch')], ['CH_DOWN', T('remote.ch_3')]];
function stepText(s) {
  switch (s.do) {
    case 'wake': return T('auto.step_wake');
    case 'sleep': return T('auto.step_sleep');
    case 'app': return T('auto.step_app', { name: appName(s.pkg) });
    case 'volume': return T('auto.step_volume', { level: s.level });
    case 'key': return T('auto.step_key', { key: (STEP_KEYS.find((k) => k[0] === s.key) || [0, s.key])[1] });
    case 'link': return '🔗 ' + s.url.replace(/^https?:\/\//, '').slice(0, 40);
    case 'wait': return T('auto.step_wait', { s: s.s });
    case 'input': return T('auto.step_input', { name: s.label || s.id });
    case 'notify': return T('auto.step_notify', { text: (s.text || s.title || '').slice(0, 30) });
    case 'timer': return s.min ? T('auto.step_timer', { time: mins(s.min) }) : T('auto.step_timer_off');
  }
  return s.do;
}
const TEMPLATES = [
  { icon: '🎬', name: T('auto.tpl_youtube'), steps: [{ do: 'wake' }, { do: 'wait', s: 2 }, { do: 'app', pkg: 'com.google.android.youtube.tv' }] },
  { icon: '😴', name: T('auto.tpl_bedtime'), steps: [{ do: 'volume', level: 5 }, { do: 'timer', min: 30 }] },
  { icon: '🔇', name: T('auto.tpl_quiet'), steps: [{ do: 'key', key: 'MUTE' }] },
  { icon: '🌙', name: T('auto.tpl_tv_off'), steps: [{ do: 'sleep' }] },
];

async function loadScenes() { const r = await api('/api/scenes', null, true); if (r) { SCENES = r.scenes || []; renderScenes(); } }
async function saveScenes(list) { const r = await api('/api/scenes', { scenes: list }); if (!r) return false; SCENES = r.scenes || []; renderScenes(); return true; }
async function runScene(sc) {
  toast(T('auto.running', { name: sc.name }));
  const r = await api('/api/scene/run', sc.id ? { id: sc.id } : { steps: sc.steps });
  if (r && !(r.failed && needSetup(r.failed))) toast(r.ok ? T('auto.done', { name: sc.name }) : T('auto.done_but', { why: friendly(r.failed) }));
  setTimeout(refresh, 800); if (currentTab === 'auto') loadRules(null, true);
}
let sceneNamesAsked = false;
function renderScenes() {
  const unnamed = [...new Set(SCENES.flatMap((sc) => sc.steps.filter((x) => x.do === 'app' && !META.has(x.pkg)).map((x) => x.pkg)))];
  if (unnamed.length && !sceneNamesAsked) {                          // the apps' names come from the TV: draw again once they are known
    sceneNamesAsked = true;
    api('/api/apps', null, true).then((r) => {
      if (r) seedMeta(r.apps);
      let waiting = unnamed.length;
      unnamed.forEach((pkg) => onMeta(pkg, () => { if (--waiting === 0) renderScenes(); }));
    });
  }
  put('#sceneexport', 'hidden', !SCENES.length);
  $('#scenelist').replaceChildren(...(SCENES.length ? SCENES.map((sc) => el('div', { className: 'item' },
    el('div', { className: 'ficon', textContent: sc.icon || '🎬' }),
    el('div', { className: 'name' }, el('span', { textContent: sc.name }), el('small', { textContent: sc.steps.map(stepText).join(' → ') })),
    el('div', { className: 'acts' }, setLabel(el('button', { className: 'small primary', onclick: () => runScene(sc) }), '▶', T('auto.run')),
      setLabel(el('button', { className: 'small', onclick: () => editScene(sc) }), '✏️', T('auto.edit')))))
    : [UI.empty(T('auto.no_scenes'))]));
  $('#scenebtns').replaceChildren(...(SCENES.length ? SCENES.map((sc) => el('button', { className: 'scene', title: sc.steps.map(stepText).join(' → '), onclick: () => runScene(sc) },
    el('span', { className: 'ico', textContent: sc.icon || '🎬' }), el('span', { textContent: sc.name })))
    : can('full') ? [el('button', { className: 'scene add', onclick: () => { showTab('auto'); editScene(null); } }, el('span', { className: 'ico', textContent: '＋' }), el('span', { textContent: T('auto.scene_add') }))] : []));
  put('#scenecard', 'hidden', !SCENES.length && !can('full'));            // a guest runs the owner's scenes, and makes none
}

// the editor: a name, an emoji, and steps added one by one
let editing = null, draft = [];
function editScene(sc) {
  editing = sc; draft = sc ? JSON.parse(JSON.stringify(sc.steps)) : [];
  put('#scenetitle', 'textContent', T(sc ? 'auto.scene_edit_title' : 'auto.scene_new_title'));
  $('#scenename').value = sc ? sc.name : ''; $('#sceneicon').value = sc ? sc.icon || '' : '';
  put('#scenedel', 'hidden', !sc);
  $('#scenetpl').replaceChildren(...(sc ? [] : [el('span', { className: 'muted', textContent: T('auto.examples') }), ...TEMPLATES.map((t) => el('button', { className: 'small', textContent: t.icon + ' ' + t.name, onclick: () => {
    $('#scenename').value = t.name; $('#sceneicon').value = t.icon; draft = JSON.parse(JSON.stringify(t.steps)); drawSteps();
  } }))]));
  drawSteps(); stepArgs();
  if (!pickApps.length) loadPickApps().then(stepArgs);
  $('#scenedlg').showModal();
}
function drawSteps() {
  $('#scenesteps').replaceChildren(...(draft.length ? draft.map((s, i) => el('div', { className: 'steprow' },
    el('span', { textContent: (i + 1) + '. ' + stepText(s) }),
    el('button', { className: 'small iconbtn', textContent: '↑', disabled: i === 0, title: T('remote.up'), onclick: () => { [draft[i - 1], draft[i]] = [draft[i], draft[i - 1]]; drawSteps(); } }),
    el('button', { className: 'small iconbtn', textContent: '↓', disabled: i === draft.length - 1, title: T('remote.down'), onclick: () => { [draft[i + 1], draft[i]] = [draft[i], draft[i + 1]]; drawSteps(); } }),
    el('button', { className: 'small iconbtn danger', textContent: '✕', title: T('auto.drop_step'), onclick: () => { draft.splice(i, 1); drawSteps(); } })))
    : [UI.empty(T('auto.no_steps'))]));
}
// what the chosen kind of step needs: an app, a key, a number, a link
function stepArgs() {
  const k = $('#stepkind').value, box = $('#stepargs');
  const num = (v, min, max, unit) => el('label', { className: 'num' }, el('input', { type: 'number', value: v, min, max, id: 'steparg' }), unit ? ' ' + unit : '');
  if (k === 'app') box.replaceChildren(el('select', { id: 'steparg' }, ...pickApps.slice().sort((a, b) => appName(a).localeCompare(appName(b))).map((p) => el('option', { value: p, textContent: appName(p) }))));
  else if (k === 'key') box.replaceChildren(el('select', { id: 'steparg' }, ...STEP_KEYS.map(([v, t]) => el('option', { value: v, textContent: t }))));
  else if (k === 'volume') box.replaceChildren(num(10, 0, 100, ''));
  else if (k === 'wait') box.replaceChildren(num(2, 0, 30, T('auto.unit_sec')));
  else if (k === 'timer') box.replaceChildren(num(30, 0, 720, T('auto.unit_min')));
  else if (k === 'input') {                                          // the TV's own list, read now
    const sel = el('select', { id: 'steparg' }, el('option', { value: '', textContent: T('auto.reading_inputs') })); box.replaceChildren(sel);
    api('/api/inputs', null, true).then((r) => {
      if (box.firstChild !== sel) return;
      const list = (r && r.inputs) || [];
      sel.replaceChildren(...(list.length ? list.map((i) => el('option', { value: i.id, textContent: i.label })) : [el('option', { value: '', textContent: T(r ? 'auto.no_inputs' : 'auto.inputs_failed') })]));
    });
  }
  else if (k === 'link') box.replaceChildren(el('input', { id: 'steparg', placeholder: 'https://youtu.be/...', inputMode: 'url' }));
  else if (k === 'notify') {                                         // the corner and the seconds start from what the Gửi thông báo dialog last used (notify.js)
    const p = notifyPrefs();
    box.replaceChildren(el('input', { id: 'steparg', placeholder: T('auto.notify_text'), maxLength: 300 }),
      el('select', { id: 'stepcorner', 'aria-label': T('notify.screen_corner') }, ...NOTIFY_CORNERS.map(([v]) => el('option', { value: v, textContent: notifyCornerName(v), selected: v === p.corner }))),
      el('select', { id: 'stepsecs', 'aria-label': T('notify.stays_for') }, ...NOTIFY_SECONDS.map((n) => el('option', { value: n, textContent: T('notify.seconds_n', { n }), selected: n === p.seconds }))));
  }
  else box.replaceChildren();
}
$('#stepkind').addEventListener('change', stepArgs);
$('#stepadd').addEventListener('click', () => {
  const k = $('#stepkind').value, a = $('#steparg'), v = a ? a.value.trim() : '';
  const s = { do: k };
  if (k === 'app') { if (!v) return toast(T('auto.pick_one_app')); s.pkg = v; }
  if (k === 'key') s.key = v;
  if (k === 'volume') s.level = Math.max(0, Math.min(100, Math.round(+v || 0)));
  if (k === 'wait') s.s = Math.max(0, Math.min(30, +v || 1));
  if (k === 'timer') s.min = Math.max(0, Math.min(720, Math.round(+v || 0)));
  if (k === 'input') { if (!v) return toast(T('auto.pick_one_input')); s.id = v; s.label = a.selectedOptions[0].textContent; }
  if (k === 'notify') { if (!v) return toast(T('auto.enter_notify')); s.text = v.slice(0, 300); s.corner = notifyCorner($('#stepcorner').value); s.seconds = notifySeconds($('#stepsecs').value); }
  if (k === 'link') { const l = normalizeLink(v); if (!l) return toast(T('auto.enter_link')); s.url = l; }
  if (draft.length >= 30) return toast(T('auto.max_steps'));
  draft.push(s); drawSteps();
});
function draftScene() {
  const name = $('#scenename').value.trim();
  if (!name) { toast(T('auto.name_scene')); $('#scenename').focus(); return null; }
  if (!draft.length) { toast(T('auto.add_step')); return null; }
  return { id: editing ? editing.id : 's' + Date.now().toString(36), name, icon: $('#sceneicon').value.trim(), steps: draft };
}
$('#scenesave').addEventListener('click', async () => {
  const sc = draftScene(); if (!sc) return;
  const list = editing ? SCENES.map((x) => x.id === editing.id ? sc : x) : [...SCENES, sc];
  if (await saveScenes(list)) { $('#scenedlg').close(); toast(T('auto.scene_saved', { name: sc.name })); }
});
$('#scenetry').addEventListener('click', () => { const sc = draftScene(); if (sc) runScene({ name: sc.name, steps: sc.steps }); });
$('#scenedel').addEventListener('click', async () => { if (editing && await ask(T('auto.scene_delete_ask', { name: editing.name }), { ok: T('auto.delete'), danger: true }) && await saveScenes(SCENES.filter((x) => x.id !== editing.id))) $('#scenedlg').close(); });
$('#sceneclose').addEventListener('click', () => $('#scenedlg').close());
$('#scenenew').addEventListener('click', () => editScene(null));

// backup: "Export" is a plain download from the server (works in the phone app's web view too); "Import" reads the file
// here and merges it: a scene with the same id is replaced, the others are added. The server checks every step again.
function mergeScenes(old, incoming) {
  const out = old.slice();
  for (const sc of incoming) {
    const i = sc && sc.id ? out.findIndex((o) => o.id === sc.id) : -1;
    if (i >= 0) out[i] = sc; else out.push(sc);
  }
  return out;
}
$('#sceneexport').addEventListener('click', () => {                  // the server names the file and says "download"
  const a = el('a', { href: 'api/scenes?download=1', download: '' }); document.body.append(a); a.click(); a.remove();
});
$('#sceneimport').addEventListener('click', () => $('#scenefile').click());
$('#scenefile').addEventListener('change', async (e) => {
  const f = e.target.files[0]; e.target.value = '';
  if (!f) return;
  let list;
  try {
    const d = JSON.parse(await f.text());
    list = Array.isArray(d) ? d : d && Array.isArray(d.scenes) ? d.scenes : null;
  } catch (err) { list = null; }
  if (!list || !list.length) { toast(T('auto.import_none')); return; }
  if (!await saveScenes(mergeScenes(SCENES, list))) return;
  const kept = list.filter((sc) => sc && SCENES.some((s) => s.id === sc.id || (!sc.id && s.name === String(sc.name || '').trim()))).length;
  toast(kept < list.length ? T('auto.imported_skipped', { n: kept, skip: list.length - kept }) : T('auto.imported', { n: kept }));
});
$('#sceneedit').addEventListener('click', () => showTab('auto'));

// voice and the "Ask the TV" box: what the remote can do by itself (voice.js), before the TV's Assistant is asked
async function doCommand(c) {
  if (c.kind === 'scene') return runScene(c.scene);
  if (c.kind === 'app') return openOnTv(c.pkg, c.name);
  if (c.kind === 'timer') return setSleep(c.min);
  if (c.kind === 'key') {
    if (c.key === 'SLEEP' || c.key === 'WAKE') { const r = await api('/api/key', { key: c.key }); if (r) toast(T(c.key === 'SLEEP' ? 'auto.tv_off_done' : 'auto.tv_on_done')); return; }   // never POWER: it would turn a TV that is off on
    for (let i = 0; i < c.times; i++) { await api('/api/key', { key: c.key }); if (c.times > 1) await sleep(150); }
    { const key = (STEP_KEYS.find((k) => k[0] === c.key) || [0, c.key])[1]; toast(c.times > 1 ? T('auto.pressed_times', { key, times: c.times }) : T('auto.pressed', { key })); }
  }
}
const voiceApps = () => [...META.entries()].filter(([, m]) => m && m.label).map(([pkg, m]) => ({ pkg, name: m.label }));

setInterval(() => { if (currentTab === 'auto' && !document.hidden && !stopped && ADB.state === 'ready') loadRules(null, true); }, 20000);
let autoLoaded = false;
onTvChange(() => { autoLoaded = sceneNamesAsked = false; RULES = null; SCENES = []; renderScenes(); });
function autoTab() {
  if (NATIVE()) { loadScenes(); return; }                             // LG / Samsung: scenes only, by their remote link
  if (ADB.state !== 'ready') return; autoLoaded = true; loadRules(null, true); loadScenes(); loadPickApps(); }
