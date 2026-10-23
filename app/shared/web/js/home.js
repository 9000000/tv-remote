// 🏠 TV Home, the project's own launcher (app/home), set up from here (Home.kt → HomeReceiver on the TV): favourite apps in
// their order, apps left off its grid, the background (a built-in one or a photo from this device), the clock. Each change
// is sent at once and shows on the TV's screen as it comes.
let homeNow = null;
onTvChange(() => { homeNow = null; homeCard(); });

// the gradients TV Home draws (HomeActivity.wallColors), small enough to tell them apart
const HOME_WALLS = { night: '#0b0f1a,#18223f', ocean: '#03192b,#0a4a66', dusk: '#1a0f2e,#7a3b3b', forest: '#06140f,#133b2d', aurora: '#071a1f,#2b2a6b', black: '#000,#000',
  sunset: '#1f0d1c,#a14a4a', ember: '#190b08,#7a2e12', plum: '#150b22,#5a2f7a', midnight: '#050a1a,#14307a', teal: '#041a1a,#0e5f5f', slate: '#0f1218,#3a4558', graphite: '#1c1c1e,#1c1c1e', navy: '#0b1d3a,#0b1d3a', wine: '#3a0f1f,#3a0f1f', moss: '#14261b,#14261b' };
const HOME_WALL_WORDS = { night: 'home.wall_night', ocean: 'home.wall_ocean', dusk: 'home.wall_dusk', forest: 'home.wall_forest', aurora: 'home.wall_aurora', black: 'home.wall_black',
  sunset: 'home.wall_sunset', ember: 'home.wall_ember', plum: 'home.wall_plum', midnight: 'home.wall_midnight', teal: 'home.wall_teal', slate: 'home.wall_slate', graphite: 'home.wall_graphite', navy: 'home.wall_navy', wine: 'home.wall_wine', moss: 'home.wall_moss' };
// The scenes TV Home paints itself (HomeConfig.ART; recipes in home_walls.js), the day's Bing picture, the person's photos (photo, photo2..)
Object.assign(HOME_WALL_WORDS, { dawn: 'home.wall_dawn', peaks: 'home.wall_peaks', dunes: 'home.wall_dunes', waves: 'home.wall_waves', candy: 'home.wall_candy', peach: 'home.wall_peach', meadow: 'home.wall_meadow', mist: 'home.wall_mist', bokeh: 'home.wall_bokeh', canyon: 'home.wall_canyon', bing: 'home.wall_bing' });
const HOME_WEBMODE = { live: 'home.web_mode_live', snap: 'home.web_mode_snap' };   // HomeConfig.WMODES, with its words
const HOME_EVERY = { 'off': 'home.every_off', '30m': 'home.every_30m', '1h': 'home.every_1h', '6h': 'home.every_6h', 'day': 'home.every_day', 'show': 'home.every_show' };   // HomeConfig.EVERY, with its words
const HOME_PHOTO_MAX = 10;                                            // HomeConfig.PHOTO_MAX
const homeThumbs = {};                                                // a photo's small picture by id + its stamp: asked of the TV once
const HOME_DOCK_MAX = 6;                                             // HomeConfig.DOCK_MAX
const HOME_WIDGETS = ['system', 'usage', 'calendar', 'lunar', 'timer', 'weather', 'clock'];          // HomeConfig.WIDGETS, in the order drawn
// A TV Home with "wsizes" sizes each card (big / small) and packs them (homePack); one older than that has only the preset layouts below
const HOME_BIG_KINDS = ['system', 'usage', 'calendar', 'weather'];     // HomeConfig.defaultSize: these are big until chosen otherwise
const HOME_W = { big: 100, cal: 120, small: 90, budget: 415, floor: 190 };   // HomeConfig.W_BIG / W_CAL / W_SMALL / BUDGET / FLOOR
const homeSize = (c, k) => (c.wsize && (c.wsize[k] === 'big' || c.wsize[k] === 'small')) ? c.wsize[k] : (HOME_BIG_KINDS.includes(k) ? 'big' : 'small');
// HomeConfig.pack, the same rule (tests/home_pack_cases.json holds the cases both are tested with): walk the cards in order, a big
// card is a column, two small ones in a row stack in one, a small one alone is a narrow column; the first column past the budget and
// all after it wait. A pinned note is the last small card and keeps its place: the cards before it give way, from the end.
function homePack(keys, big, note) {
  const run = (ks) => {
    const cols = []; let sum = 0, i = 0;
    while (i < ks.length) {
      const k = ks[i], isBig = big.includes(k);
      const group = !isBig && i + 1 < ks.length && !big.includes(ks[i + 1]) ? [k, ks[i + 1]] : [k];
      const w = !isBig ? HOME_W.small : k === 'calendar' ? HOME_W.cal : HOME_W.big;
      if (sum + w > HOME_W.budget) break;
      cols.push({ keys: group, weight: w }); sum += w; i += group.length;
    }
    return { cols, waiting: ks.slice(i) };
  };
  if (!note) return run(keys);
  for (let n = keys.length; n >= 0; n--) {
    const p = run([...keys.slice(0, n), 'note']);
    if (!p.waiting.length) return { cols: p.cols, waiting: keys.slice(n) };
  }
  return { cols: [], waiting: keys };
}
const HOME_LAYOUTS = { row: 'home.layout_row', big: 'home.layout_big', big2: 'home.layout_big2', flip: 'home.layout_flip', quad: 'home.layout_quad' };   // HomeConfig.LAYOUTS
// HomeConfig.COLS: [share of the width %, cards stacked] per column; "row" is drawn as it looks (it has no fixed places)
const HOME_COLS = { row: [[33, 1], [33, 1], [34, 2]], big: [[58, 1], [42, 2]], big2: [[36, 1], [36, 1], [28, 1]], flip: [[42, 2], [58, 1]], quad: [[50, 2], [50, 2]] };
const HOME_OLD_LAYOUTS = ['row', 'big'];                              // a TV Home without "wlayouts" has only these
const HOME_ROW_CARDS = 3, HOME_LARGE = ['system', 'usage', 'calendar', 'weather'];   // HomeConfig.ROW_CARDS, Widgets.BIG: what "row" shows
const homeSlots = (k) => k === 'row' || !HOME_COLS[k] ? 0 : HOME_COLS[k].reduce((n, c) => n + c[1], 0);   // HomeConfig.slots: what a fixed layout shows (a pinned note takes one)
const HOME_WIDGET_WORDS = { system: 'home.w_system', usage: 'home.w_usage', calendar: 'home.w_calendar', lunar: 'home.w_lunar', timer: 'home.w_timer', weather: 'home.w_weather', clock: 'home.w_clock' };
const HOME_STYLE_WORDS = { classic: 'home.style_classic', light: 'home.style_light', minimal: 'home.style_minimal', roman: 'home.style_roman' };   // HomeConfig.STYLES

// The packed layout drawn small: a column per weight, its cards stacked, big ones filled (sizes in css/remote.css)
function packGlyph(p, big) {
  const g = el('div', { className: 'packview', role: 'img', 'aria-label': T('home.pack_alt') });
  const sum = Math.max(HOME_W.floor, p.cols.reduce((n, c) => n + c.weight, 0));
  for (const c of p.cols) {
    const col = el('b'); col.style.setProperty('--w', c.weight / sum * 100);
    for (const k of c.keys) col.append(el('s', { className: big.includes(k) ? 'big' : '' }));
    g.append(col);
  }
  return g;
}

// An older TV Home's layout drawn small: its columns, the cards stacked in each (sizes in css/remote.css)
function layGlyph(k) {
  const g = el('i', { className: 'layglyph', 'aria-hidden': 'true' });
  for (const [pct, n] of HOME_COLS[k]) {
    const col = el('b'); col.style.setProperty('--w', pct);
    for (let i = 0; i < n; i++) col.append(el('s'));
    g.append(col);
  }
  return g;
}

// The 🏠 card's line and its Customize button: only where TV Home is on the TV
async function homeCard() {
  const line = $('#homestate'), btn = $('#homeopen');
  if (!line) return;
  const r = ADB.state === 'ready' ? await api('/api/home', null, true) : null;
  homeNow = r && r.installed ? r : null;
  line.hidden = btn.hidden = !homeNow;
  if (homeNow) put(line, 'textContent', T(homeNow.home ? 'home.state_on' : 'home.state_off', { v: homeNow.version }));
  const upd = $('#homeupd');
  upd.hidden = !(homeNow && homeNow.latest);
  if (!upd.hidden) put(upd, 'textContent', T('home.update', { v: homeNow.latest }));
}

// a newer TV Home from the latest release, put over the one on the TV: its settings stay
async function homeUpdate() {
  const n = await api('/api/home/update', {});
  if (n && n.installed) toast(T('home.updated', { v: n.version }));
  homeCard();
}

async function homeOpen() {
  if (await needAdbSoon()) return;
  let r = await api('/api/home?thumbs=1'); if (!r) return;
  const keepThumbs = (n) => { for (const [id, b] of Object.entries(n.thumbs || {})) { const p = (n.photos || []).find((x) => x.id === id); if (p) homeThumbs[id + ':' + p.v] = b; } };
  keepThumbs(r);
  if (!r.installed) { homeCard(); toast(T('home.missing')); goTo('tv', 'launchcard'); return; }   // from the remote's More: where to get it
  const apps = () => r.apps.map((a) => a.pkg);
  const send = async (change) => {
    const n = await api('/api/home', change);
    if (n && n.installed) { r = n; homeNow = n; draw(); }
  };
  const after = async (n) => { r = n; homeNow = n; draw(); const t = await api('/api/home?thumbs=1'); if (t && t.installed) { keepThumbs(t); r = t; homeNow = t; draw(); } };   // a photo came or went: its small picture
  const dropPhoto = async (id) => { const n = await api('/api/home/photo/drop', { id }); if (n && n.installed) await after(n); };
  // on the TV itself (TV Setup's page) there is no ✕ to aim at with the D-pad: OK held on a photo asks, then deletes, as on TV Home
  const holdDrop = (b, id) => {
    let t = 0, held = false;
    b.addEventListener('keydown', (e) => {
      if (e.key !== 'Enter') return;
      if (e.repeat || held) { e.preventDefault(); return; }
      t = setTimeout(async () => { held = true; if (!await ask(T('home.photo_del_q'), { ok: T('home.photo_remove'), danger: true })) return;
        const at = [...walls.children].findIndex((c) => c.contains(b));
        await dropPhoto(id);
        const near = walls.children[at] || walls.children[at - 1];      // its neighbour, not the top of the dialog
        if (near) (near.querySelector('button') || near).focus();
      }, 600);
    });
    b.addEventListener('keyup', (e) => { if (e.key === 'Enter') clearTimeout(t); });
    b.addEventListener('click', (e) => { if (held) { held = false; e.stopImmediatePropagation(); e.preventDefault(); } }, true);
  };

  const row = (pkg, acts) => {
    const icon = appIcon(pkg), name = el('span', { textContent: appLabel(pkg) });
    onMeta(pkg, (m) => { icon.fill(m); put(name, 'textContent', appLabel(pkg)); });
    return el('div', { className: 'item' }, icon, el('div', { className: 'name' }, name), el('div', { className: 'acts' }, ...acts));
  };
  const appLabel = (pkg) => { const a = r.apps.find((x) => x.pkg === pkg); return appName(pkg) !== pkg ? appName(pkg) : (a && a.label) || pkg; };
  const tool = (text, title, fn, off) => el('button', { type: 'button', className: 'small iconbtn', textContent: text, title, 'aria-label': title, disabled: !!off, onclick: fn });

  const dock = el('div', { className: 'list' }), hidden = el('div', { className: 'list' });
  const addDock = el('button', { type: 'button', className: 'small', textContent: T('home.add'), onclick: async () => {
    const p = await chooseApp(T('home.pick_dock'), apps().filter((x) => !r.config.dock.includes(x)));
    if (p) send({ dock: [...r.config.dock, p] });
  } });
  const addHidden = el('button', { type: 'button', className: 'small', textContent: T('home.hide'), onclick: async () => {
    const p = await chooseApp(T('home.pick_hide'), apps().filter((x) => !r.config.hidden.includes(x) && !r.config.dock.includes(x)));
    if (p) send({ hidden: [...r.config.hidden, p] });
  } });
  const walls = el('div', { className: 'fchips homewalls' }), holdNote = el('p', { className: 'note', textContent: T('home.photo_hold'), hidden: true });
  // right over the pictures, filled: a tile among thirty went unseen, and in the head over "Change automatically" nobody
  // looked back up for it (eyes go down to the pictures)
  const upload = el('button', { type: 'button', className: 'primary', textContent: T('home.upload'), onclick: () => window.ONTV ? api('/api/home/photo/qr', {}) : file.click() });   // on the TV: TV Home's QR, a photo comes from a phone
  const everyHead = el('h3', { textContent: T('home.rotate') }), every = el('div', { className: 'fchips homeevery' }), everyNote = el('p', { className: 'note' });
  const file = el('input', { type: 'file', accept: 'image/*', multiple: true, hidden: true, onchange: async () => {
    // one at a time (the TV takes a photo per ADB push); a TV Home without rotation has room for one only
    const room = r.config.wrotate ? HOME_PHOTO_MAX - (r.photos || []).length : 1;
    const picked = [...file.files]; file.value = '';
    if (picked.length > room) toast(T('home.photos_full', { n: HOME_PHOTO_MAX }));
    const go = picked.slice(0, Math.max(room, 0));
    for (let i = 0; i < go.length; i++) {                             // the button turns and says which one goes, the TV's push takes seconds
      const back = UI.busy(upload, go.length > 1 ? T('home.sending_n', { i: i + 1, n: go.length }) : T('home.sending_photo'));
      try { await homePhoto(go[i], (n) => { r = n; homeNow = n; }); } finally { back(); }
    }
    if (picked.length) after(r);
  } });
  const clock = el('input', { type: 'checkbox', onchange: () => send({ clock24: clock.checked }) });
  const greet = el('input', { type: 'checkbox', onchange: () => send({ greet: greet.checked }) });
  const anim = el('input', { type: 'checkbox', onchange: () => send({ anim: anim.checked }) });
  const dream = el('input', { type: 'checkbox', onchange: () => send({ dream: dream.checked }) });
  // the cards over the favourites, in the order they have on the TV (the first on the left); usage and timer read TV Setup, so a
  // TV Setup too old for them is offered first. Chosen: a list to reorder; the others: a switch each, which adds one at the end
  const wTurnOn = async (w) => {
    if ((w === 'usage' || w === 'timer') && !await setupFor('home_usage')) return false;
    send({ widgets: [...r.config.widgets, w] });
    return true;
  };
  const wLines = HOME_WIDGETS.map((w) => {
    const box = el('input', { type: 'checkbox', onchange: async () => { if (!await wTurnOn(w)) box.checked = false; } });
    return el('label', { className: 'check' }, box, el('span', { textContent: T(HOME_WIDGET_WORDS[w]) }));
  });
  const wHead = el('h3', { textContent: T('home.widgets') }), wList = el('div', { className: 'list homew' });
  const layHead = el('h3', { textContent: T('home.layout') }), lay = el('div', { className: 'fchips laychips' }), layNote = el('p', { className: 'note' });
  // TV Home's words: the TV's language, or one chosen for it (HomeConfig.LANGS); absent from a TV Home older than the choice
  const langHead = el('h3', { textContent: T('home.lang') }), langs = el('div', { className: 'fchips' });
  // how the focused app shows (HomeConfig "focus"); absent from a TV Home older than the glass
  const focusHead = el('h3', { textContent: T('home.focus') }), focus = el('div', { className: 'fchips' });
  // app tiles per row (HomeConfig "cols", 4..8); only a TV Home that reports "colsOk"
  const colsHead = el('h3', { textContent: T('home.cols') }), cols = el('div', { className: 'fchips' });
  // rows of apps on the first screen (HomeConfig "rows", tenths, 0 = by itself), up to "rowsMax" the TV measured; a TV Home that reports "rowsOk"
  const rowsIn = el('input', { type: 'number', min: '1', step: '0.1', inputMode: 'decimal', 'aria-label': T('home.rows') });
  const rowsSet = el('button', { type: 'button', className: 'primary', textContent: T('home.rows_set'), onclick: () => { const v = parseFloat(String(rowsIn.value).replace(',', '.')); if (v >= 1) send({ rows: Math.round(v * 10) / 10 }); } });
  const rowsAuto = el('button', { type: 'button', textContent: T('home.rows_auto'), onclick: () => { rowsIn.value = ''; send({ rows: 0 }); } });
  rowsIn.addEventListener('keydown', (e) => { if (e.key === 'Enter') rowsSet.click(); });
  const rowsHead = el('h3', { textContent: T('home.rows') }), rowsLine = UI.line(rowsIn, rowsSet, rowsAuto), rowsNote = el('p', { className: 'note' });
  // how much moves (HomeConfig "motion"): "" calm on a TV with little memory; absent from a TV Home older than the levels
  const motionHead = el('h3', { textContent: T('home.motion') }), motion = el('div', { className: 'fchips' });
  const packHead = el('h3', { textContent: T('home.pack') }), packBox = el('div', { className: 'packwrap' }), packNote = el('p', { className: 'note', textContent: T('home.pack_note') });
  // the family's note, pinned on the TV's home screen until it is taken down (HomeConfig.NOTE_MAX)
  const note = el('input', { type: 'text', maxLength: 140, placeholder: T('home.note_ph'), 'aria-label': T('home.note') });
  const pin = el('button', { type: 'button', className: 'primary', textContent: T('home.note_pin'), onclick: () => send({ note: note.value.trim() }) });
  const unpin = el('button', { type: 'button', textContent: T('home.note_clear'), onclick: () => { note.value = ''; send({ note: '' }); } });
  note.addEventListener('keydown', (e) => { if (e.key === 'Enter') pin.click(); });
  // the weather card's city (HomeConfig.PLACE_MAX); empty = the TV finds its own place
  const place = el('input', { type: 'text', maxLength: 60, placeholder: T('home.place_ph'), 'aria-label': T('home.place') });
  const placeSet = el('button', { type: 'button', className: 'primary', textContent: T('home.place_set'), onclick: () => send({ place: place.value.trim() }) });
  const placeAuto = el('button', { type: 'button', textContent: T('home.place_auto'), onclick: () => { place.value = ''; send({ place: '' }); } });
  place.addEventListener('keydown', (e) => { if (e.key === 'Enter') placeSet.click(); });
  const placeHead = el('h3', { textContent: T('home.place') }), placeLine = UI.line(place, placeSet, placeAuto);
  const noteHead = el('h3', { textContent: T('home.note') }), noteLine = UI.line(note, pin, unpin);
  // a web page as the background and the screensaver (HomeConfig.wurl, http or https); the TV shows it, so a typo costs nothing but the gradient
  const web = el('input', { type: 'url', maxLength: 500, placeholder: T('home.web_ph'), 'aria-label': T('home.web') });
  const webSet = el('button', { type: 'button', className: 'primary', textContent: T('home.web_set'), onclick: () => {
    const u = web.value.trim();
    if (u && !/^https?:\/\/[^\s/?#]+\S*$/i.test(u)) return toast(T('home.web_bad'));
    send(u ? { wurl: u, wall: 'web' } : { wurl: '' });
  } });
  const webOff = el('button', { type: 'button', textContent: T('home.web_clear'), onclick: () => { web.value = ''; send({ wurl: '' }); } });
  web.addEventListener('keydown', (e) => { if (e.key === 'Enter') webSet.click(); });
  const webHead = el('h3', { textContent: T('home.web') }), webLine = UI.line(web, webSet, webOff), webNote = el('p', { className: 'note', textContent: T('home.web_note') });
  // how the page updates (HomeConfig wmode / wsnap / wkeep): live behind Home, or a picture taken every few minutes (frost and dim as on a photo)
  const webModeHead = el('h3', { textContent: T('home.web_mode') }), webMode = el('div', { className: 'fchips' });
  const webEveryHead = el('h3', { textContent: T('home.web_every') }), webEvery = el('div', { className: 'fchips' });
  const webKeepBox = el('input', { type: 'checkbox', onchange: () => send({ wkeep: webKeepBox.checked }) });
  const webKeep = el('label', { className: 'check' }, webKeepBox, el('span', { textContent: T('home.web_keep') }));
  const webModeNote = el('p', { className: 'note' });
  // the veil over a photo, a scene or the page (HomeConfig "shade", 0..100 % by 5, 50 the usual one): a busy one fights Home's
  // words; the TV follows the slider while it moves (its last place a moment after), as the dimmer does
  const shadeOut = el('output');
  let shadeWait = 0;
  const shadeIn = el('input', { type: 'range', min: 0, max: 100, step: 5, ariaLabel: T('home.shade'), oninput: () => {
    shadeOut.textContent = shadeIn.value + '%';
    clearTimeout(shadeWait); shadeWait = setTimeout(() => send({ shade: +shadeIn.value }), 150);
  } });
  const shadeHead = el('h3', { textContent: T('home.shade') }), shade = el('div', { className: 'vol boost' }, shadeIn, shadeOut);
  const shadeNote = el('p', { className: 'note', textContent: T('home.shade_note') });
  const gal = el('div', { className: 'homegal' }), credit = el('p', { className: 'note', textContent: T('home.gal_note') });
  const galHead = el('h3', { textContent: T('home.gallery') });
  homeGallery(gal, credit, galHead, (n) => { r = n; homeNow = n; draw(); });

  function draw() {
    const c = r.config, d = c.dock;
    const move = (i, j) => { const n = d.slice(); [n[i], n[j]] = [n[j], n[i]]; send({ dock: n }); };
    dock.replaceChildren(...d.map((p, i) => row(p, [
      tool('↑', T('home.up'), () => move(i, i - 1), i === 0),
      tool('↓', T('home.down'), () => move(i, i + 1), i === d.length - 1),
      tool('✕', T('home.remove'), () => send({ dock: d.filter((x) => x !== p) })),
    ])));
    if (!d.length) dock.append(el('p', { className: 'empty', textContent: T('home.none_dock') }));
    addDock.disabled = d.length >= HOME_DOCK_MAX;
    hidden.replaceChildren(...c.hidden.map((p) => row(p, [el('button', { type: 'button', className: 'small', textContent: T('home.show'), onclick: () => send({ hidden: c.hidden.filter((x) => x !== p) }) })])));
    if (!c.hidden.length) hidden.append(el('p', { className: 'empty', textContent: T('home.none_hidden') }));
    upload.disabled = !!c.wrotate && (r.photos || []).length >= HOME_PHOTO_MAX;
    if (c.wrotate) drawRotation(c); else walls.replaceChildren(...(r.photo ? [el('button', { type: 'button', className: c.wall === 'photo' ? 'on' : '', onclick: () => send({ wall: 'photo' }) }, el('i', { className: 'add', textContent: '🖼️' }), el('span', { textContent: T('home.wall_photo') }))] : []),
    ...Object.keys(HOME_WALLS).map((w) => {
      const dot = el('i'); dot.style.setProperty('--c', 'linear-gradient(135deg,' + HOME_WALLS[w] + ')');
      return el('button', { type: 'button', className: c.wall === w ? 'on' : '', onclick: () => send({ wall: w }) }, dot, el('span', { textContent: T(HOME_WALL_WORDS[w]) }));
    }));
    langHead.hidden = langs.hidden = c.lang === undefined;
    langs.replaceChildren(...[['', 'home.lang_tv'], ['vi', 'home.lang_vi'], ['en', 'home.lang_en']].map(([k, id]) =>
      el('button', { type: 'button', className: (c.lang || '') === k ? 'on' : '', onclick: () => send({ lang: k }) }, T(id))));
    focusHead.hidden = focus.hidden = c.focus === undefined;
    focus.replaceChildren(...[['glass', 'home.focus_glass'], ['ring', 'home.focus_ring']].map(([k, id]) =>
      el('button', { type: 'button', className: (c.focus || 'glass') === k ? 'on' : '', onclick: () => send({ focus: k }) }, T(id))));
    colsHead.hidden = cols.hidden = !c.colsOk;
    cols.replaceChildren(...[4, 5, 6, 7, 8].map((n) =>
      el('button', { type: 'button', className: (c.cols || 5) === n ? 'on' : '', 'aria-pressed': String((c.cols || 5) === n), onclick: () => send({ cols: n }) }, String(n))));
    rowsHead.hidden = rowsLine.hidden = rowsNote.hidden = !c.rowsOk;
    if (document.activeElement !== rowsIn) rowsIn.value = c.rows >= 1 ? String(c.rows) : '';
    rowsIn.placeholder = T('home.rows_auto'); if (c.rowsMax >= 1) rowsIn.max = String(c.rowsMax);
    rowsNote.textContent = c.rowsMax >= 1 ? T('home.rows_note', { n: c.rowsMax }) : T('home.rows_note0');
    motionHead.hidden = motion.hidden = c.motion === undefined || c.anim === false;
    motion.replaceChildren(...[['', 'home.motion_auto'], ['calm', 'home.motion_calm'], ['full', 'home.motion_full']].map(([k, id]) =>
      el('button', { type: 'button', className: (c.motion || '') === k ? 'on' : '', onclick: () => send({ motion: k }) }, T(id))));
    shadeHead.hidden = shade.hidden = shadeNote.hidden = !c.shadeOk;
    if (document.activeElement !== shadeIn) { shadeIn.value = String(c.shade ?? 50); shadeOut.textContent = (c.shade ?? 50) + '%'; }
    everyHead.hidden = every.hidden = !c.wrotate; if (!c.wrotate) everyNote.hidden = true;
    clock.checked = c.clock24; greet.checked = c.greet; anim.checked = c.anim !== false;
    dream.checked = !!c.dream;
    dream.closest('label').hidden = c.dream === undefined;           // a TV Home older than the screensaver
    const w = c.widgets;                                              // absent: a TV Home older than the cards
    const has = (k) => !!w && w.includes(k);
    wLines.forEach((l, i) => { l.hidden = !w || has(HOME_WIDGETS[i]) || (HOME_WIDGETS[i] === 'weather' && c.place === undefined); });   // weather: a TV Home older than it has no card
    wHead.hidden = !w;
    const sized = !!c.wsizes;                                         // this TV Home sizes the cards; an older one has the preset layouts
    const bigs = sized && w ? w.filter((k) => homeSize(c, k) === 'big') : [];
    const packed = sized && w ? homePack(w, bigs, !!c.note) : null;
    const places = sized ? 0 : homeSlots(c.wlayout), slots = c.note ? places - 1 : places;
    const waits = (k, i) => sized ? (packed.waiting.includes(k) ? T('home.w_waits_pack') : '')
      : places ? (i >= slots ? T('home.w_waits', { n: slots }) : '')
      : HOME_LARGE.includes(k) && w.slice(0, i).filter((x) => HOME_LARGE.includes(x)).length >= HOME_ROW_CARDS ? T('home.w_waits_row', { n: HOME_ROW_CARDS }) : '';
    const wmove = (i, j) => { const n = w.slice(); [n[i], n[j]] = [n[j], n[i]]; send({ widgets: n }); };
    const wsize = (k, v) => send({ wsize: Object.fromEntries([...new Set([...HOME_WIDGETS, ...(w || [])])].map((x) => [x, x === k ? v : homeSize(c, x)])) });   // a newer TV Home's cards keep theirs
    const sizeChips = (k) => el('span', { className: 'sizechips', role: 'group', 'aria-label': T('home.size') },
      ...['big', 'small'].map((v) => el('button', { type: 'button', className: 'small' + (homeSize(c, k) === v ? ' on' : ''), textContent: T(v === 'big' ? 'home.size_big' : 'home.size_small'), 'aria-pressed': String(homeSize(c, k) === v), onclick: () => wsize(k, v) })));
    // a card with looks (the TV Home lists them in "wstyles"): a row of chips under its name; the first is the default
    const styleOf = (k) => (c.wstyle && c.wstyle[k]) || c.wstyles[k][0];
    const wstyle = (k, v) => send({ wstyle: { ...(c.wstyle || {}), [k]: v } });
    const styleChips = (k) => el('div', { className: 'fchips wstyle', role: 'group', 'aria-label': T('home.style') },
      ...c.wstyles[k].map((v) => el('button', { type: 'button', className: styleOf(k) === v ? 'on' : '', 'aria-pressed': String(styleOf(k) === v), textContent: HOME_STYLE_WORDS[v] ? T(HOME_STYLE_WORDS[v]) : v, onclick: () => wstyle(k, v) })));
    wList.replaceChildren(...(w || []).map((k, i) => el('div', { className: 'item' },
      el('div', { className: 'name' }, el('span', { textContent: HOME_WIDGET_WORDS[k] ? T(HOME_WIDGET_WORDS[k]) : T('home.w_newer', { k }) }), ...(waits(k, i) ? [el('small', { textContent: waits(k, i) })] : [])),
      el('div', { className: 'acts' },
        ...(sized ? [sizeChips(k)] : []),
        tool('↑', T('home.up'), () => wmove(i, i - 1), i === 0),
        tool('↓', T('home.down'), () => wmove(i, i + 1), i === w.length - 1),
        tool('✕', T('home.w_off'), () => send({ widgets: w.filter((x) => x !== k) }))),
      ...(c.wstyles && c.wstyles[k] ? [styleChips(k)] : []))));
    wList.hidden = !w || !w.length;
    packHead.hidden = packBox.hidden = packNote.hidden = !sized || !w || !(w.length || c.note);
    if (packed) packBox.replaceChildren(packGlyph(packed, bigs));
    layHead.hidden = lay.hidden = sized || c.wlayout === undefined || !w;      // a TV Home older than the layouts; one with sizes has no presets
    layNote.hidden = layHead.hidden || !places;
    put(layNote, 'textContent', T('home.layout_note', { n: places }));
    lay.replaceChildren(...Object.keys(HOME_LAYOUTS).filter((k) => (c.wlayouts || HOME_OLD_LAYOUTS).includes(k)).map((k) => el('button', { type: 'button', className: c.wlayout === k ? 'on' : '', onclick: () => send({ wlayout: k }) },
      layGlyph(k), el('span', { textContent: T(HOME_LAYOUTS[k]) }))));
    webHead.hidden = webLine.hidden = webNote.hidden = !(c.wrotate && c.wweb);   // a TV Home older than the web page
    const onWeb = c.wall === 'web' && c.wmode !== undefined, snap = c.wmode === 'snap';   // a TV Home older than the modes has none
    webModeHead.hidden = webMode.hidden = webModeNote.hidden = !onWeb;
    webEveryHead.hidden = webEvery.hidden = webKeep.hidden = !(onWeb && snap);
    webMode.replaceChildren(...Object.keys(HOME_WEBMODE).map((m) => el('button', { type: 'button', className: c.wmode === m ? 'on' : '', 'aria-pressed': String(c.wmode === m), textContent: T(HOME_WEBMODE[m]), onclick: () => send({ wmode: m }) })));
    webEvery.replaceChildren(...[1, 5, 15, 30, 60].map((n) => el('button', { type: 'button', className: c.wsnap === n ? 'on' : '', 'aria-pressed': String(c.wsnap === n), textContent: T('home.web_every_n', { n }), onclick: () => send({ wsnap: n }) })));   // HomeConfig.WSNAPS
    webKeepBox.checked = !!c.wkeep;
    webModeNote.textContent = T(!snap ? 'home.web_mode_note_live' : c.wkeep ? 'home.web_mode_note_keep' : 'home.web_mode_note_snap');
    if (document.activeElement !== web) web.value = c.wurl || '';
    webOff.disabled = !c.wurl;
    placeHead.hidden = placeLine.hidden = c.place === undefined || !has('weather');   // a TV Home older than the weather; and only while the card is on
    if (document.activeElement !== place) place.value = c.place || '';
    placeAuto.disabled = !c.place;
    noteHead.hidden = noteLine.hidden = c.note === undefined;     // a TV Home older than the note
    if (document.activeElement !== note) note.value = c.note || '';
    unpin.disabled = !c.note;
  }

  // A TV Home that rotates (config.wrotate): the scenes drawn here by the same recipe the TV uses, the colours, Bing's picture
  // and the person's photos, each a tile. Rotation off: one is chosen. On: every tile is in or out of the set (at least one stays).
  function drawRotation(c) {
    const multi = c.wevery !== 'off', set = c.walls || [c.wall];
    const isOn = (k) => multi ? set.includes(k) : c.wall === k;
    const pick = (k) => {
      if (k === 'web') return c.wurl ? send({ wall: 'web', wurl: c.wurl }) : web.focus();   // the page is never in a set; the address goes along because the client cleans a change alone (web without one is Night); none yet: ask for it
      if (set.includes('web')) return send({ wall: k });                // leaving the page: the set starts again from this one
      if (!multi) return send({ wall: k });
      const n = set.includes(k) ? set.filter((x) => x !== k) : [...set, k];
      if (n.length) send({ walls: n });
    };
    const tile = (k, face, name, extra) => {
      const b = el('button', { type: 'button', className: isOn(k) ? 'on' : '', 'aria-pressed': String(isOn(k)), onclick: () => pick(k) }, face, el('span', { textContent: name }));
      return extra ? el('span', { className: 'wallcell' }, b, extra) : b;
    };
    const art = HOME_ART_IDS.map((k) => {
      const cv = el('canvas', { width: 128, height: 72 }); homeArtPaint(cv.getContext('2d'), 128, 72, k);
      return tile(k, el('i', {}, cv), T(HOME_WALL_WORDS[k]));
    });
    const plain = Object.keys(HOME_WALLS).map((k) => {
      const dot = el('i'); dot.style.setProperty('--c', 'linear-gradient(135deg,' + HOME_WALLS[k] + ')');
      return tile(k, dot, T(HOME_WALL_WORDS[k]));
    });
    const bing = tile('bing', el('i', { className: 'add', textContent: '🌐' }), T('home.wall_bing'));
    const webTile = c.wweb ? [tile('web', el('i', { className: 'add', textContent: '🖥️' }), T('home.wall_web'))] : [];
    const photos = (r.photos || []).map((p, i) => {
      const t = homeThumbs[p.id + ':' + p.v], face = el('i', {}, t ? el('img', { src: 'data:image/jpeg;base64,' + t, alt: '' }) : '🖼️');
      const del = el('button', { type: 'button', className: 'small iconbtn wallx', textContent: '✕', title: T('home.photo_remove'), 'aria-label': T('home.photo_remove'), onclick: () => dropPhoto(p.id) });
      const cell = tile(p.id, face, T('home.wall_photo_n', { n: i + 1 }), del);
      if (window.ONTV) { del.hidden = true; holdDrop(cell.firstChild, p.id); }
      return cell;
    });
    holdNote.hidden = !(window.ONTV && photos.length);
    walls.replaceChildren(bing, ...webTile, ...photos, ...art, ...plain);         // as on the TV (HomeConfig.WALLS): what changes by itself and the person's own first
    every.replaceChildren(...Object.keys(HOME_EVERY).map((e) => el('button', { type: 'button', className: c.wevery === e ? 'on' : '', 'aria-pressed': String(c.wevery === e), textContent: T(HOME_EVERY[e]), onclick: () => send({ wevery: e }) })));
    put(everyNote, 'textContent', multi ? T(set.length < 2 && set[0] !== 'bing' ? 'home.rotate_pick' : 'home.rotate_set') : '');   // Bing alone turns through its last 8 days
    everyNote.hidden = !multi;
  }

  const d = el('dialog', { className: 'ask homeask' }, el('div', { className: 'card' },
    el('div', { className: 'card-head' }, el('h2', { textContent: T('home.title') }), el('button', { type: 'button', className: 'small iconbtn', textContent: '✕', title: T('common.close'), 'aria-label': T('common.close'), onclick: () => d.close() })),
    el('div', { className: 'card-head' }, el('h3', { textContent: T('home.dock') }), addDock), dock,
    el('h3', { textContent: T('home.wall') }), everyHead, every, everyNote, el('div', { className: 'actions' }, upload), walls, holdNote, webHead, webLine, webNote, webModeHead, webMode, webEveryHead, webEvery, webKeep, webModeNote, shadeHead, shade, shadeNote, file,
    galHead, gal, credit,
    langHead, langs, focusHead, focus, colsHead, cols, rowsHead, rowsLine, rowsNote, motionHead, motion,
    el('label', { className: 'check' }, clock, el('span', { textContent: T('home.clock24') })),
    el('label', { className: 'check' }, greet, el('span', { textContent: T('home.greet') })),
    el('label', { className: 'check' }, anim, el('span', { textContent: T('home.anim') })),
    el('label', { className: 'check' }, dream, el('span', { textContent: T('home.dream') })),
    wHead, wList, ...wLines, packHead, packBox, packNote, layHead, lay, layNote, placeHead, placeLine,
    noteHead, noteLine,
    el('div', { className: 'card-head' }, el('h3', { textContent: T('home.hidden') }), addHidden), hidden));
  d.addEventListener('close', () => { d.remove(); homeCard(); });
  draw();
  document.body.append(d); d.showModal();
}

// The wallpaper gallery (docs/home/wallpapers.json, signed): small pictures to choose from; the one chosen is downloaded here,
// checked against the list and sent to the TV like a photo of the person's own. The credit shows under the pictures.
async function homeGallery(box, credit, head, done) {
  const g = await api('/api/home/walls', null, true);
  if (!g || !g.walls || !g.walls.length) { box.hidden = credit.hidden = head.hidden = true; return; }   // no list (offline): no empty heading either
  const lang = (document.documentElement.lang || 'en').slice(0, 2);
  const note = (w) => T('home.credit', { name: w.name[lang] || w.name.en, credit: w.credit, license: w.license, kb: w.kb });
  box.replaceChildren(...g.walls.map((w) => {
    const pic = el('img', { src: w.thumb, alt: '', loading: 'lazy' });
    const b = el('button', { type: 'button', title: note(w), onclick: async () => {
      if (box.dataset.busy) return;
      box.dataset.busy = '1'; box.classList.add('busy'); toast(T('home.sending_photo'));
      const n = await api('/api/home/wall', { id: w.id });
      delete box.dataset.busy; box.classList.remove('busy');
      if (n && n.installed) { done(n); toast(T('home.photo_done')); }
    } }, pic, el('span', { textContent: w.name[lang] || w.name.en }));
    const show = () => put(credit, 'textContent', note(w));
    b.addEventListener('pointerenter', show); b.addEventListener('focus', show);
    return b;
  }));
}

// A photo as the background: made the TV's size here (a phone's 12 MP photo would take a while over ADB and fill the
// TV's memory), shown in the TV's frame first so the crop can be moved (photofit.js, the same as TV Home's own phone page), then sent
async function homePhoto(f, done) {
  try {
    const img = await photoOpen(f);
    const W = 1920, H = 1080;
    const view = await homeCropAsk(img, W, H);
    if (!view) return false;                                          // cancelled: nothing sent, nothing said
    toast(T('home.sending_photo'));
    const blob = await photoBlob(img, W, H, view, 0.85);
    const x = await fetch('/api/home/photo', { method: 'POST', headers: { 'Content-Type': 'application/octet-stream' }, body: blob });
    const n = await x.json().catch(() => ({}));
    if (!x.ok) throw new Error(n.error || x.statusText);
    done(n); toast(T('home.photo_done'));
    return true;
  } catch (e) { toast(T('common.error_msg', { msg: friendly(e.message) })); return false; }
}

// The photo in the TV's shape, to move and widen before it is sent; resolves with the crop's view, or null when cancelled.
function homeCropAsk(img, W, H) {
  return new Promise((ok) => {
    let view = null;
    const cv = el('canvas', { className: 'photocrop', width: 640, height: Math.round(640 * H / W) });
    const d = el('dialog', { className: 'ask' }, el('div', { className: 'card' },
      el('h2', { textContent: T('home.crop_title') }), cv, el('p', { className: 'note', textContent: T('home.crop_hint') }),
      el('div', { className: 'actions' },
        el('button', { type: 'button', textContent: T('common.cancel'), onclick: () => d.close() }),
        el('button', { type: 'button', className: 'primary', textContent: T('home.crop_send'), onclick: () => { view = pv; d.close(); } }))));
    d.addEventListener('close', () => { d.remove(); ok(view); });
    document.body.append(d); d.showModal();
    const pv = photoCrop(cv, img);
  });
}

$('#homeopen').addEventListener('click', homeOpen);
$('#homeupd').addEventListener('click', homeUpdate);
