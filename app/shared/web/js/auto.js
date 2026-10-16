// Automation tab: TV Setup's sleep timer, bedtime and daily app limits (they run on the TV itself, see app/tv .../Rules.kt),
// and scenes, one button that does several things (each remote keeps its own list, see Scenes.kt).
const SETUP_APK = 'https://github.com/HoangTran0410/tv-remote/releases/latest/download/tv-setup.apk';
let RULES = null, rulesSkew = 0, rulesBusy = false;
const pad2 = (n) => String(n).padStart(2, '0');
const clock = (t) => { const d = new Date(t - rulesSkew); return pad2(d.getHours()) + ':' + pad2(d.getMinutes()); };   // a moment on the TV's clock, in this device's
const mins = (m) => m < 60 ? m + ' phút' : Math.floor(m / 60) + ' giờ' + (m % 60 ? ' ' + m % 60 + ' phút' : '');

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
  if (r.setup || r.error) {                                            // no TV Setup on the TV (or one too old): say what it brings and offer it
    $('#rulecards').hidden = true;
    box.replaceChildren(UI.card('😴 Hẹn giờ tắt · 🌙 Giờ đi ngủ · ⏳ Giới hạn app', {},
      el('p', { textContent: r.error ? 'TV Setup trả lời lỗi: ' + friendly(r.error)
        : r.setup === 'old' ? 'TV Setup trên TV là bản cũ. Cần bản ' + (r.min || '0.1.0') + ' trở lên: mở TV Setup trên TV → Cập nhật, hoặc bấm nút dưới.'
        : 'Các mục này chạy bằng app TV Setup trên TV (để TV tự tắt được cả khi máy này đã tắt). TV chưa có app đó.' }),
      el('div', { className: 'actions' }, act(r.setup === 'old' ? '⬆️ Cập nhật TV Setup' : '⚡ Cài TV Setup lên TV', r.setup === 'old' ? updateSetup : installSetupApk, 'primary'))));
    return;
  }
  box.replaceChildren(); $('#rulecards').hidden = false;
  // sleep timer
  const left = r.sleep_at ? Math.max(0, Math.round((r.sleep_at - r.now) / 60000)) : 0;
  $('#sleepstate').textContent = r.sleep_at ? 'TV tự tắt lúc ' + clock(r.sleep_at) + ' · còn ' + mins(left) : 'Chưa hẹn giờ.';
  $('#sleepstate').classList.toggle('on', !!r.sleep_at);
  $('#sleepbtns').replaceChildren(...[15, 30, 45, 60, 90, 120].map((m) => el('button', { className: 'small', textContent: mins(m), onclick: () => setSleep(m) })));
  $('#sleepacts').replaceChildren(...(r.sleep_at ? [act('＋15 phút', async () => { await loadRules({ do: 'sleep_add', minutes: 15 }); }), act('✕ Hủy hẹn giờ', async () => setSleep(0), 'danger')] : []));
  // bedtime
  const b = r.bedtime || {}, beds = bedRanges();
  if (!$('#rulecards').contains(document.activeElement)) $('#bedon').checked = !!b.on;
  $('#beds').replaceChildren(...beds.map((v, i) => el('div', { className: 'item' },
    el('div', { className: 'name' }, el('span', { textContent: v.from + ' – ' + v.to })),
    el('div', { className: 'acts' },
      act('🗑', async () => { if (await ask('Bỏ giờ đi ngủ ' + v.from + ' – ' + v.to + '?', { ok: 'Bỏ' })) await saveBeds(beds.filter((x, j) => j !== i)); }, 'danger')))));
  const snoozed = r.snooze_until > r.now;
  $('#bedstate').textContent = !b.on ? '' : snoozed ? 'Đang cho xem thêm tới ' + clock(r.snooze_until) + '.' : r.in_bedtime ? 'Đang trong giờ đi ngủ: TV bật lên sẽ tự tắt sau 1 phút.'
    : 'Đang bật: ' + beds.map((v) => v.from + ' – ' + v.to).join(', ') + ' mỗi ngày.';
  $('#bedacts').replaceChildren(...(!b.on ? [] : snoozed ? [act('Thôi, áp dụng lại ngay', async () => { await loadRules({ do: 'snooze', minutes: 0 }); })]
    : r.in_bedtime ? [act('Cho xem thêm 30 phút', async () => { await loadRules({ do: 'snooze', minutes: 30 }); })] : []));
  // app limits
  const rows = (r.limits || []).map((l) => {
    const total = l.minutes + (l.extra || 0), pct = Math.min(100, Math.round(l.used * 100 / Math.max(1, total)));
    const icon = appIcon(l.pkg); onMeta(l.pkg, (m) => icon.fill(m));
    const meter = el('div', { className: 'bar' + (pct >= 100 ? ' full' : '') }, el('i')); meter.firstChild.style.width = pct + '%';
    const name = l.label || appName(l.pkg);
    return el('div', { className: 'item' }, icon, el('div', { className: 'name' }, el('span', { textContent: name }),
      el('small', { textContent: 'Hôm nay đã dùng ' + mins(l.used) + ' / ' + mins(total) + (l.extra ? ' (cho thêm ' + mins(l.extra) + ')' : '') }), meter),
      el('div', { className: 'acts' },
        act('＋30 phút hôm nay', async () => { await loadRules({ do: 'extra', pkg: l.pkg, minutes: 30 }); toast('Đã cho thêm 30 phút: ' + name); }),
        act('✏️', async () => { const v = await askTime('Mỗi ngày được dùng ' + name + ' bao lâu?', l.minutes); if (v) await loadRules({ do: 'limit', pkg: l.pkg, minutes: v }); }),
        act('🗑', async () => { if (await ask('Bỏ giới hạn cho ' + name + '?', { ok: 'Bỏ giới hạn' })) await loadRules({ do: 'limit', pkg: l.pkg, minutes: 0 }); }, 'danger')));
  });
  $('#limits').replaceChildren(...(rows.length ? rows : [UI.empty('Chưa giới hạn app nào.')]));
  // volume limits; a TV Setup older than them has no "volume" in its answer
  const vols = r.volume || [];
  $('#vols').replaceChildren(...(vols.length ? vols.map((v, i) => el('div', { className: 'item' },
    el('div', { className: 'name' }, el('span', { textContent: v.from + ' – ' + v.to }), el('small', { textContent: 'Tối đa ' + v.max + '%' })),
    el('div', { className: 'acts' },
      act('🗑', async () => { if (await ask('Bỏ giới hạn ' + v.from + ' – ' + v.to + '?', { ok: 'Bỏ giới hạn' })) await saveVols(vols.filter((x, j) => j !== i)); }, 'danger'))))
    : [UI.empty('Chưa giới hạn âm lượng.')]));
  const now = r.volume_now;
  $('#volstate').textContent = r.volume === undefined ? '' : r.volume_fixed ? '⚠️ Âm lượng của TV này do loa ngoài / TV giữ (qua HDMI), không giới hạn được.'
    : now ? 'Đang giới hạn tối đa ' + now.max + '% tới ' + now.to + '.' : '';
  if (CORE === 'tv' && r.adb && r.adb !== 'ready') $('#bedstate').append(el('span', { className: 'warnline', textContent: '⚠️ TV Setup chưa nối được ADB của TV nên chưa tự tắt TV / đóng app được.' }));
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
  if (!m) return toast('Nhập số phút (vd 40) hoặc giờ tắt (vd 23:30)');
  $('#sleepin').value = ''; await setSleep(m);
});
async function setSleep(m) {
  const r = await loadRules({ do: 'sleep', minutes: m });
  if (r) toast(m ? 'TV sẽ tự tắt lúc ' + clock(r.sleep_at) : 'Đã hủy hẹn giờ tắt');
}

// a TV Setup older than several ranges answers with from and to only
const bedRanges = () => { const b = (RULES && RULES.bedtime) || {}; return b.ranges || (b.from ? [{ from: b.from, to: b.to }] : []); };
async function saveBeds(list, on = list.length > 0) {
  const r = await loadRules({ do: 'bedtime', on: on && list.length > 0, ranges: list, from: list.length ? list[0].from : '22:00', to: list.length ? list[0].to : '06:30' });
  if (r && !r.bedtime.on) toast('Đã tắt giờ đi ngủ');
  return r;
}
$('#bedf').addEventListener('submit', async (e) => {
  e.preventDefault();
  const v = { from: $('#bedfrom').value || '22:00', to: $('#bedto').value || '06:30' };
  if (v.from === v.to) return toast('Giờ bắt đầu và kết thúc phải khác nhau');
  const b = (RULES && RULES.bedtime) || {};
  const keep = b.on ? bedRanges() : [];                                // turned off, the old ranges are not wanted back
  if (keep.length && !b.ranges && !await setupFor('bedtimes')) return;
  if (await saveBeds([...keep, v])) toast('Đã thêm giờ đi ngủ: ' + v.from + ' – ' + v.to);
});
$('#bedon').addEventListener('change', async () => {
  const list = bedRanges().length ? bedRanges() : [{ from: $('#bedfrom').value || '22:00', to: $('#bedto').value || '06:30' }];
  const r = await saveBeds(list, $('#bedon').checked);
  if (r && r.bedtime.on) toast('Đã bật giờ đi ngủ');
});
async function saveVols(list) {
  if (RULES && RULES.volume === undefined && !await setupFor('volume')) return null;
  return loadRules({ do: 'volume', ranges: list });
}
$('#volf').addEventListener('submit', async (e) => {
  e.preventDefault();
  const v = { from: $('#volfrom').value || '22:00', to: $('#volto').value || '06:00', max: +$('#volmax').value };
  if (v.from === v.to) return toast('Giờ bắt đầu và kết thúc phải khác nhau');
  if (await saveVols([...((RULES && RULES.volume) || []), v])) toast('Đã giới hạn âm lượng: tối đa ' + v.max + '%, ' + v.from + ' – ' + v.to);
});
$('#limitf').addEventListener('submit', async (e) => {
  e.preventDefault(); const pkg = $('#limitpkg').value; if (!pkg) return;
  if (await loadRules({ do: 'limit', pkg, minutes: +$('#limitmin').value })) toast('Đã giới hạn ' + appName(pkg) + ': ' + mins(+$('#limitmin').value) + ' mỗi ngày');
});

// the apps to choose from: the ones with an icon on the TV's home screen
let pickApps = [];
async function loadPickApps() {
  const r = await api('/api/apps', null, true); if (!r) return;
  seedMeta(r.apps); pickApps = r.apps.map((a) => a.pkg);
  const fill = () => {
    const keep = $('#limitpkg').value;
    $('#limitpkg').replaceChildren(el('option', { value: '', textContent: 'Chọn app...' }), ...pickApps.slice().sort((a, b) => appName(a).localeCompare(appName(b))).map((p) => el('option', { value: p, textContent: appName(p) })));
    $('#limitpkg').value = keep;
  };
  fill(); pickApps.filter((p) => !(META.get(p) || {}).label).forEach((p) => onMeta(p, () => fill()));
}

async function installSetupApk() {
  toast('Đang tải TV Setup về TV...');
  let j = await api('/api/install', { url: SETUP_APK }); if (!j) return;
  for (let misses = 0; j && j.status === 'running' && misses < 10;) { await sleep(1500); const r = await api('/api/job?id=' + j.id, null, true); if (r) { j = r; misses = 0; } else misses++; }
  if (!j || j.status !== 'done') { toast('Không cài được: ' + friendly(j ? j.msg : 'mất kết nối')); return; }
  await api('/api/app/launch', { pkg: 'dev.tvremote.setup' }, true);    // opened once, it starts its service and keeps it across restarts
  toast('Đã cài TV Setup'); await sleep(2500); loadRules(null, true);
}

// ---------- TV Setup's own updates (SetupUpdate.kt): offered on every remote; the TV puts the old version back if a new one does not work ----------
let setupLater = '', setupBusy = false;                               // setupChecked: core.js
const SETUP_STEP = { download: 'Đang tải bản mới về TV', installing: 'Đang cài bản mới', checking: 'Đã cài, đang xem bản mới có chạy tốt không' };
const seenUndo = () => { try { return localStorage.getItem('setupUndo') || ''; } catch (e) { return ''; } };
async function checkSetup() {
  setupChecked = curHost || '-';
  const r = await fetch('/api/setup/version').then((x) => x.ok ? x.json() : null).catch(() => null);
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
    $('#suptitle').textContent = '⚠️ TV Setup ' + r.version + ' chạy lỗi trên TV này';
    $('#supnote').textContent = 'TV đã tự quay về bản ' + r.from + '. Hẹn giờ, ghép nối và cài đặt vẫn còn nguyên. Có bản sau thì nó hiện ở đây.';
    $('#supgo').hidden = true; $('#suplater').textContent = '✓ Đã hiểu';
    $('#suplater').onclick = () => { try { localStorage.setItem('setupUndo', String(r.at)); } catch (e) { } r.state = ''; showSetupUpd(r); };   // then the update, if there is one
    return;
  }
  $('#suptitle').textContent = '🆕 TV Setup trên TV có bản mới ' + r.latest + ' (đang dùng ' + r.installed + ')';
  $('#supnote').textContent = 'Mất khoảng nửa phút.'
    + (CORE === 'tv' ? ' Trang này tự nối lại sau đó.' : '');
  $('#supgo').hidden = false; $('#suplater').textContent = 'Để sau';
  $('#suplater').onclick = () => { setupLater = r.latest; b.hidden = true; };
}
$('#supgo').addEventListener('click', () => updateSetup());
async function updateSetup() {
  if (setupBusy) return;
  setupBusy = true; $('#supacts').hidden = true;
  const b = $('#setupupd'); b.hidden = false; b.className = 'banner ok';   // also from the Automation tab, with the banner put away
  if (!$('#suptitle').textContent) $('#suptitle').textContent = '⬆️ Đang cập nhật TV Setup trên TV';   // or from the phone's remote screen, never shown
  const note = (t) => { $('#supnote').textContent = t; };
  try {
    note('Đang bắt đầu...');
    let j = await api('/api/setup/update', {}); if (!j) return;
    for (let misses = 0; j && j.status === 'running' && misses < 4;) {
      note((SETUP_STEP[j.msg] || 'Đang cập nhật') + (j.msg === 'download' ? ' ' + j.pct + '%' : '') + '...');
      await sleep(1500);
      const r = await fetch('/api/job?id=' + j.id).then((x) => x.ok ? x.json() : null).catch(() => null);
      if (r) { j = r; misses = 0; } else misses++;
    }
    if (j && j.status === 'done') { toast('Đã cập nhật TV Setup lên ' + j.version); $('#setupupd').hidden = true; loadRules(null, true); return; }
    if (j && j.status === 'error') {
      if (!/^setup:(rolledback|broken)/.test(j.msg)) { note(j.msg === 'setup:slow' ? 'TV Setup cập nhật lâu hơn thường lệ. Xem lại sau ít phút.' : 'Chưa cập nhật được: ' + friendly(j.msg)); return; }
    } else {                                                           // lost: on the TV's own page the update closes the server serving it
      note('Đang cập nhật TV Setup. Trang này tự nối lại sau ít giây...');
      for (let i = 0; i < 60; i++) {
        await sleep(3000);
        const r = await fetch('/api/setup/version').then((x) => x.ok ? x.json() : null).catch(() => null);
        if (r && r.state !== 'installing' && r.state !== 'checking') { if (CORE === 'tv' && r.state === 'ok') { location.reload(); return; } break; }
      }
    }
    setupBusy = false;
    const r = await checkSetup();                                       // ok, or put back: the banner says which
    if (r && r.state === 'ok' && !r.latest) { toast('Đã cập nhật TV Setup lên ' + r.installed); $('#setupupd').hidden = true; }
  } finally {
    setupBusy = false; $('#supacts').hidden = false;
  }
}

// ---------- scenes ----------
let SCENES = [];
const STEP_KEYS = [['MUTE', 'Tắt tiếng'], ['PLAY_PAUSE', 'Phát / Dừng'], ['HOME', 'Home'], ['BACK', 'Quay lại'], ['VOL_UP', 'Tăng âm lượng'], ['VOL_DOWN', 'Giảm âm lượng'],
  ['OK', 'OK'], ['UP', 'Lên'], ['DOWN', 'Xuống'], ['LEFT', 'Trái'], ['RIGHT', 'Phải'], ['INPUT', 'Ngõ vào'], ['SUBTITLE', 'Phụ đề'], ['CH_UP', 'Kênh +'], ['CH_DOWN', 'Kênh −']];
function stepText(s) {
  switch (s.do) {
    case 'wake': return '☀️ Bật TV';
    case 'sleep': return '🌙 Tắt TV';
    case 'app': return '📱 Mở ' + appName(s.pkg);
    case 'volume': return '🔊 Âm lượng ' + s.level;
    case 'key': return '🎮 Phím ' + ((STEP_KEYS.find((k) => k[0] === s.key) || [0, s.key])[1]);
    case 'link': return '🔗 ' + s.url.replace(/^https?:\/\//, '').slice(0, 40);
    case 'wait': return '⏱️ Chờ ' + s.s + ' giây';
    case 'input': return '📺 Ngõ vào ' + (s.label || s.id);
    case 'timer': return s.min ? '😴 Hẹn giờ tắt ' + mins(s.min) : '😴 Hủy hẹn giờ tắt';
  }
  return s.do;
}
const TEMPLATES = [
  { icon: '🎬', name: 'Xem YouTube', steps: [{ do: 'wake' }, { do: 'wait', s: 2 }, { do: 'app', pkg: 'com.google.android.youtube.tv' }] },
  { icon: '😴', name: 'Đi ngủ', steps: [{ do: 'volume', level: 5 }, { do: 'timer', min: 30 }] },
  { icon: '🔇', name: 'Yên lặng', steps: [{ do: 'key', key: 'MUTE' }] },
  { icon: '🌙', name: 'Tắt TV', steps: [{ do: 'sleep' }] },
];

async function loadScenes() { const r = await api('/api/scenes', null, true); if (r) { SCENES = r.scenes || []; renderScenes(); } }
async function saveScenes(list) { const r = await api('/api/scenes', { scenes: list }); if (!r) return false; SCENES = r.scenes || []; renderScenes(); return true; }
async function runScene(sc) {
  toast('Đang chạy: ' + sc.name);
  const r = await api('/api/scene/run', sc.id ? { id: sc.id } : { steps: sc.steps });
  if (r && !(r.failed && needSetup(r.failed))) toast(r.ok ? '✓ Xong: ' + sc.name : 'Xong, nhưng có bước không làm được: ' + friendly(r.failed));
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
  $('#sceneexport').hidden = !SCENES.length;
  $('#scenelist').replaceChildren(...(SCENES.length ? SCENES.map((sc) => el('div', { className: 'item' },
    el('div', { className: 'ficon', textContent: sc.icon || '🎬' }),
    el('div', { className: 'name' }, el('span', { textContent: sc.name }), el('small', { textContent: sc.steps.map(stepText).join(' → ') })),
    el('div', { className: 'acts' }, setLabel(el('button', { className: 'small primary', onclick: () => runScene(sc) }), '▶', 'Chạy'),
      setLabel(el('button', { className: 'small', onclick: () => editScene(sc) }), '✏️', 'Sửa'))))
    : [UI.empty('Chưa có cảnh nào. Bấm “＋ Tạo cảnh”, có sẵn mẫu để bắt đầu.')]));
  $('#scenebtns').replaceChildren(...(SCENES.length ? SCENES.map((sc) => el('button', { className: 'scene', title: sc.steps.map(stepText).join(' → '), onclick: () => runScene(sc) },
    el('span', { className: 'ico', textContent: sc.icon || '🎬' }), el('span', { textContent: sc.name })))
    : [el('button', { className: 'scene add', onclick: () => { showTab('auto'); editScene(null); } }, el('span', { className: 'ico', textContent: '＋' }), el('span', { textContent: 'Tạo cảnh: một nút làm nhiều việc' }))]));
}

// the editor: a name, an emoji, and steps added one by one
let editing = null, draft = [];
function editScene(sc) {
  editing = sc; draft = sc ? JSON.parse(JSON.stringify(sc.steps)) : [];
  $('#scenetitle').textContent = sc ? '🎬 Sửa cảnh' : '🎬 Cảnh mới';
  $('#scenename').value = sc ? sc.name : ''; $('#sceneicon').value = sc ? sc.icon || '' : '';
  $('#scenedel').hidden = !sc;
  $('#scenetpl').replaceChildren(...(sc ? [] : [el('span', { className: 'muted', textContent: 'Mẫu:' }), ...TEMPLATES.map((t) => el('button', { className: 'small', textContent: t.icon + ' ' + t.name, onclick: () => {
    $('#scenename').value = tr(t.name); $('#sceneicon').value = t.icon; draft = JSON.parse(JSON.stringify(t.steps)); drawSteps();
  } }))]));
  drawSteps(); stepArgs();
  if (!pickApps.length) loadPickApps().then(stepArgs);
  $('#scenedlg').showModal();
}
function drawSteps() {
  $('#scenesteps').replaceChildren(...(draft.length ? draft.map((s, i) => el('div', { className: 'steprow' },
    el('span', { textContent: (i + 1) + '. ' + stepText(s) }),
    el('button', { className: 'small iconbtn', textContent: '↑', disabled: i === 0, title: 'Lên', onclick: () => { [draft[i - 1], draft[i]] = [draft[i], draft[i - 1]]; drawSteps(); } }),
    el('button', { className: 'small iconbtn', textContent: '↓', disabled: i === draft.length - 1, title: 'Xuống', onclick: () => { [draft[i + 1], draft[i]] = [draft[i], draft[i + 1]]; drawSteps(); } }),
    el('button', { className: 'small iconbtn danger', textContent: '✕', title: 'Bỏ bước này', onclick: () => { draft.splice(i, 1); drawSteps(); } })))
    : [UI.empty('Chưa có bước nào: chọn loại bước bên dưới rồi bấm “＋ Thêm bước”.')]));
}
// what the chosen kind of step needs: an app, a key, a number, a link
function stepArgs() {
  const k = $('#stepkind').value, box = $('#stepargs');
  const num = (v, min, max, unit) => el('label', { className: 'num' }, el('input', { type: 'number', value: v, min, max, id: 'steparg' }), unit ? ' ' + unit : '');
  if (k === 'app') box.replaceChildren(el('select', { id: 'steparg' }, ...pickApps.slice().sort((a, b) => appName(a).localeCompare(appName(b))).map((p) => el('option', { value: p, textContent: appName(p) }))));
  else if (k === 'key') box.replaceChildren(el('select', { id: 'steparg' }, ...STEP_KEYS.map(([v, t]) => el('option', { value: v, textContent: t }))));
  else if (k === 'volume') box.replaceChildren(num(10, 0, 100, ''));
  else if (k === 'wait') box.replaceChildren(num(2, 0, 30, 'giây'));
  else if (k === 'timer') box.replaceChildren(num(30, 0, 720, 'phút'));
  else if (k === 'input') {                                          // the TV's own list, read now
    const sel = el('select', { id: 'steparg' }, el('option', { value: '', textContent: 'Đang đọc ngõ vào...' })); box.replaceChildren(sel);
    api('/api/inputs', null, true).then((r) => {
      if (box.firstChild !== sel) return;
      const list = (r && r.inputs) || [];
      sel.replaceChildren(...(list.length ? list.map((i) => el('option', { value: i.id, textContent: i.label })) : [el('option', { value: '', textContent: r ? 'TV này không có ngõ vào để chọn' : 'Chưa đọc được ngõ vào của TV' })]));
    });
  }
  else if (k === 'link') box.replaceChildren(el('input', { id: 'steparg', placeholder: 'https://youtu.be/...', inputMode: 'url' }));
  else box.replaceChildren();
}
$('#stepkind').addEventListener('change', stepArgs);
$('#stepadd').addEventListener('click', () => {
  const k = $('#stepkind').value, a = $('#steparg'), v = a ? a.value.trim() : '';
  const s = { do: k };
  if (k === 'app') { if (!v) return toast('Chọn một app'); s.pkg = v; }
  if (k === 'key') s.key = v;
  if (k === 'volume') s.level = Math.max(0, Math.min(100, Math.round(+v || 0)));
  if (k === 'wait') s.s = Math.max(0, Math.min(30, +v || 1));
  if (k === 'timer') s.min = Math.max(0, Math.min(720, Math.round(+v || 0)));
  if (k === 'input') { if (!v) return toast('Chọn một ngõ vào'); s.id = v; s.label = a.selectedOptions[0].textContent; }
  if (k === 'link') { const l = normalizeLink(v); if (!l) return toast('Nhập một đường dẫn'); s.url = l; }
  if (draft.length >= 30) return toast('Tối đa 30 bước');
  draft.push(s); drawSteps();
});
function draftScene() {
  const name = $('#scenename').value.trim();
  if (!name) { toast('Đặt tên cho cảnh'); $('#scenename').focus(); return null; }
  if (!draft.length) { toast('Thêm ít nhất một bước'); return null; }
  return { id: editing ? editing.id : 's' + Date.now().toString(36), name, icon: $('#sceneicon').value.trim(), steps: draft };
}
$('#scenesave').addEventListener('click', async () => {
  const sc = draftScene(); if (!sc) return;
  const list = editing ? SCENES.map((x) => x.id === editing.id ? sc : x) : [...SCENES, sc];
  if (await saveScenes(list)) { $('#scenedlg').close(); toast('Đã lưu cảnh: ' + sc.name); }
});
$('#scenetry').addEventListener('click', () => { const sc = draftScene(); if (sc) runScene({ name: sc.name, steps: sc.steps }); });
$('#scenedel').addEventListener('click', async () => { if (editing && await ask('Xóa cảnh “' + editing.name + '”?', { ok: 'Xóa', danger: true }) && await saveScenes(SCENES.filter((x) => x.id !== editing.id))) $('#scenedlg').close(); });
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
  if (!list || !list.length) { toast('File này không có cảnh nào (cần file .json xuất từ TV Remote)'); return; }
  if (!await saveScenes(mergeScenes(SCENES, list))) return;
  const kept = list.filter((sc) => sc && SCENES.some((s) => s.id === sc.id || (!sc.id && s.name === String(sc.name || '').trim()))).length;
  toast('Đã nhập ' + kept + ' cảnh' + (kept < list.length ? ' · bỏ qua ' + (list.length - kept) + ' cảnh không hợp lệ' : ''));
});
$('#sceneedit').addEventListener('click', () => showTab('auto'));

// voice and the "Ask the TV" box: what the remote can do by itself (voice.js), before the TV's Assistant is asked
async function doCommand(c) {
  if (c.kind === 'scene') return runScene(c.scene);
  if (c.kind === 'app') return openOnTv(c.pkg, c.name);
  if (c.kind === 'timer') return setSleep(c.min);
  if (c.kind === 'key') {
    if (c.key === 'SLEEP' || c.key === 'WAKE') { const r = await api('/api/key', { key: c.key }); if (r) toast(c.key === 'SLEEP' ? 'Đã tắt TV' : 'Đã bật TV'); return; }   // never POWER: it would turn a TV that is off on
    for (let i = 0; i < c.times; i++) { await api('/api/key', { key: c.key }); if (c.times > 1) await sleep(150); }
    toast('Đã bấm: ' + ((STEP_KEYS.find((k) => k[0] === c.key) || [0, c.key])[1]) + (c.times > 1 ? ' × ' + c.times : ''));
  }
}
const voiceApps = () => [...META.entries()].filter(([, m]) => m && m.label).map(([pkg, m]) => ({ pkg, name: m.label }));

setInterval(() => { if (currentTab === 'auto' && !document.hidden && !stopped && ADB.state === 'ready') loadRules(null, true); }, 20000);
let autoLoaded = false;
function autoTab() {
  if (NATIVE()) { loadScenes(); return; }                             // LG / Samsung: scenes only, by their remote link
  if (ADB.state !== 'ready') return; autoLoaded = true; loadRules(null, true); loadScenes(); loadPickApps(); }
