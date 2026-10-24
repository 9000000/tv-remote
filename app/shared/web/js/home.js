// 🏠 TV Home, the project's own launcher (app/home), set up from here (Home.kt → HomeReceiver on the TV): favourite apps in
// their order, apps left off its grid, the background (a built-in one or a photo from this device), the clock. Each change
// is sent at once and shows on the TV's screen as it comes.
let homeNow = null;
// Where the settings are read and written (js/home_link.js): through the app that serves this page, over ADB, or straight to TV Home's own
// page (home.html sets HOME_DIRECT). Everything below asks homeLink, so both ways are the same page.
const homeLink = homeLinkFor(!!window.HOME_DIRECT, (path, data, quiet) => api(path, data, quiet), homeSend);
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
// Remote keys that can open an app (HomeConfig.KEY_COLOURS / KEY_DEAD / KEY_NUMBERS), with their words; a dead key is listed only
// when TV Setup found it dead on this TV (config.keysDead)
const HOME_KEY_WORDS = { 183: 'home.key_red', 184: 'home.key_green', 185: 'home.key_yellow', 186: 'home.key_blue', 170: 'home.key_tv', 178: 'home.key_input',
  190: 'home.key_btn3', 191: 'home.key_btn4', 195: 'home.key_btn8', 192: 'home.key_btn5', 284: 'home.key_allapps', 176: 'home.key_settings' };
const homeKeyName = (code) => HOME_KEY_WORDS[code] ? T(HOME_KEY_WORDS[code]) : String(code - 7);   // KEYCODE_0 is 7
const HOME_THEME_WORDS = { calm: 'home.theme_calm', cinema: 'home.theme_cinema', day: 'home.theme_day', nature: 'home.theme_nature', tech: 'home.theme_tech', minimal: 'home.theme_minimal' };   // HomeConfig.BUILTIN
const HOME_THEMES_MAX = 12;                                           // HomeConfig.THEMES_MAX
const HOME_PHOTO_MAX = 10;                                            // HomeConfig.PHOTO_MAX
const homeThumbs = {};                                                // a photo's small picture by id + its stamp: asked of the TV once
const HOME_WIDGETS = ['system', 'usage', 'calendar', 'lunar', 'timer', 'weather', 'clock'];          // HomeConfig.WIDGETS, in the order drawn
// A TV Home with "wsizes" sizes each card (big / small) and packs them (homePack); one older than that has only the preset layouts below
const HOME_BIG_KINDS = ['system', 'usage', 'calendar', 'weather', 'qr1', 'qr2', 'qr3', 'qr4'];     // HomeConfig.defaultSize: these are big until chosen otherwise
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
  const r = ADB.state === 'ready' ? await homeLink.read(false, true) : null;
  homeNow = r && r.installed ? r : null;
  line.hidden = btn.hidden = !homeNow;
  if (homeNow) put(line, 'textContent', T(homeNow.home ? 'home.state_on' : 'home.state_off', { v: homeNow.version }));
  const upd = $('#homeupd');
  upd.hidden = !(homeNow && homeNow.latest);
  if (!upd.hidden) put(upd, 'textContent', T('home.update', { v: homeNow.latest }));
  lookOffline(upd, !!(homeNow && homeNow.offline), homeCard);
}

// a newer TV Home from the latest release, put over the one on the TV: its settings stay
async function homeUpdate() {
  const n = await homeLink.update();
  if (n && n.installed) toast(T('home.updated', { v: n.version }));
  homeCard();
}

async function homeOpen() {
  if (!homeLink.direct && await needAdbSoon()) return;                // TV Home's own page has no ADB to wait for
  let r = await homeLink.read(true); if (!r) return;
  const keepThumbs = (n) => { for (const [id, b] of Object.entries(n.thumbs || {})) { const p = (n.photos || []).find((x) => x.id === id) || ((n.video || {}).items || []).map((v) => ({ id: v.id, v: v.added })).find((x) => x.id === id); if (p) homeThumbs[id + ':' + p.v] = b; } };
  keepThumbs(r);
  if (!r.installed) { homeCard(); toast(T('home.missing')); goTo('tv', 'launchcard'); return; }   // from the remote's More: where to get it
  const apps = () => r.apps.map((a) => a.pkg);
  const send = async (change) => {
    const n = await homeLink.write(change);
    if (n && n.installed) { r = n; homeNow = n; draw(); }
  };
  const after = async (n) => { r = n; homeNow = n; draw(); const t = await homeLink.read(true); if (t && t.installed) { keepThumbs(t); r = t; homeNow = t; draw(); } };   // a photo came or went: its small picture
  const dropPhoto = async (id) => { const n = await homeLink.drop(id); if (n && n.installed) await after(n); };
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
    const icon = Target.kind(pkg) === 'app' ? appIcon(pkg) : qkLogo(r.config, pkg), name = el('span', { textContent: appLabel(pkg) });
    if (!isTile(pkg) && Target.kind(pkg) === 'app') onMeta(pkg, (m) => { icon.fill(m); put(name, 'textContent', appLabel(pkg)); });   // a live tile is no app: TV Setup names it
    return el('div', { className: 'item' }, icon, el('div', { className: 'name' }, name), el('div', { className: 'acts' }, ...acts));
  };
  const isTile = (p) => /^tile:[^/]+\/[a-z][a-z0-9_]{0,15}$/.test(p);
  const appLabel = (pkg) => { if (Target.kind(pkg) === 'pin') return qkLabel(r.config, r.apps, pkg); if (isTile(pkg)) { const t = (r.config.tilesAvail || []).find((x) => x.key === pkg); return (t && t.title) || pkg.split('/').pop(); } const a = r.apps.find((x) => x.pkg === pkg); return appName(pkg) !== pkg ? appName(pkg) : (a && a.label) || pkg; };
  const tool = (text, title, fn, off) => el('button', { type: 'button', className: 'small iconbtn', textContent: text, title, 'aria-label': title, disabled: !!off, onclick: fn });

  // live tiles (HomeConfig "tiles"): what TV Setup offers, each on or off the app grid; only a TV Home that reports "tilesOk"
  const tilesHead = el('h3', { textContent: T('home.tiles_head') }), tilesNote = el('p', { className: 'note' }), tilesList = el('div', { className: 'list' });
  // no favourites any more: TV Home is one grid, and its order is edited as that grid (home_order.js), saved in one go
  const hidden = el('div', { className: 'list' });
  const orderHead = el('h3', { textContent: T('home.order') }), orderBox = el('div', { className: 'group' });
  const orderFace = (k) => { if (Target.kind(k) !== 'app') return qkLogo(r.config, k); const i = appIcon(k); onMeta(k, (m) => i.fill(m)); return i; };
  const orderDraw = homeOrderBox(orderBox, orderFace, (k) => appLabel(k), (change) => send(change));
  const addHidden = el('button', { type: 'button', className: 'small', textContent: T('home.hide'), onclick: async () => {
    const p = await chooseApp(T('home.pick_hide'), apps().filter((x) => !r.config.hidden.includes(x)));
    if (p) send({ hidden: [...r.config.hidden, p] });
  } });
  const walls = el('div', { className: 'fchips homewalls' }), holdNote = el('p', { className: 'note', textContent: T('home.photo_hold'), hidden: true });
  // right over the pictures, filled: a tile among thirty went unseen, and in the head over "Change automatically" nobody
  // looked back up for it (eyes go down to the pictures)
  const upload = el('button', { type: 'button', className: 'primary', textContent: T('home.upload'), onclick: () => window.ONTV && homeLink.canQr ? homeLink.qr() : file.click() });   // on the TV: TV Home's QR, a photo comes from a phone
  // a video or GIF as the background (HomeConfig vid:..., vsound / vvol / vfps / vres; app/home wall/Videos.kt): the media library
  const vfile = el('input', { type: 'file', accept: 'video/mp4,video/webm,image/gif,.mp4,.webm,.gif', hidden: true, onchange: async () => {
    const f = vfile.files[0]; vfile.value = ''; if (!f) return;
    const mx = (r.video || {}).max || 0;
    if (mx && f.size > mx) { toast(T('home.v_big', { mb: Math.round(mx / 1048576) })); return; }
    const back = UI.busy(vupload, T('home.v_up', { pct: 0 }));
    try { const n = await homeVideo(f, (t) => put(vstate, 'textContent', t)); if (n) await after(n); } finally { back(); put(vstate, 'textContent', ''); }
  } });
  const vupload = el('button', { type: 'button', className: 'primary', textContent: T('home.vupload'), onclick: () => window.ONTV && homeLink.canQr ? homeLink.qr() : vfile.click() });
  const vstate = el('p', { className: 'state', role: 'status' }), vperf = el('p', { className: 'state', role: 'status' }), vnote = el('p', { className: 'note', textContent: T('home.v_note') });
  const vsound = el('input', { type: 'checkbox', onchange: () => send({ vsound: vsound.checked }) });
  const vvolOut = el('output'); let vvolWait = 0;
  const vvolIn = el('input', { type: 'range', min: 0, max: 100, step: 5, ariaLabel: T('home.v_vol'), oninput: () => {
    vvolOut.textContent = vvolIn.value + '%';
    clearTimeout(vvolWait); vvolWait = setTimeout(() => send({ vvol: +vvolIn.value }), 150);
  } });
  const vvol = el('div', { className: 'vol boost' }, vvolIn, vvolOut);
  const vfpsHead = el('h3', { textContent: T('home.v_fps') }), vfps = el('div', { className: 'fchips' });
  const vresHead = el('h3', { textContent: T('home.v_res') }), vres = el('div', { className: 'fchips' });
  const vHead = el('h3', { textContent: T('home.v_head') });
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
  const power = el('input', { type: 'checkbox', onchange: () => send({ power: power.checked }) });
  const anim = el('input', { type: 'checkbox', onchange: () => send({ anim: anim.checked }) });
  const dream = el('input', { type: 'checkbox', onchange: () => send({ dream: dream.checked }) });
  // remote keys that open an app, and the app that opens each time the TV turns on (HomeConfig keyon / keyapps / autoon / autoapp).
  // Both default off. TV Home answers a key on its own screen; TV Setup does it everywhere and the power-on app: said under each
  const keyOn = el('input', { type: 'checkbox', onchange: () => send({ keyon: keyOn.checked }) });
  const autoOn = el('input', { type: 'checkbox', onchange: () => send({ autoon: autoOn.checked }) });
  const keysHead = el('h3', { textContent: T('home.keys') }), keysNote = el('p', { className: 'note' }), keysList = el('div', { className: 'list' });
  const keysOld = el('button', { type: 'button', className: 'small', onclick: homeUpdate, textContent: T('home.keys_old') });   // a TV Home from before them
  const autoNote = el('p', { className: 'note' });
  const powerOld = el('button', { type: 'button', className: 'small', onclick: homeUpdate, textContent: T('home.power_old') });   // a TV Home from before the button: the switch stays, with how to get it
  // the cards over the favourites, in the order they have on the TV (the first on the left); usage and timer read TV Setup, so a
  // TV Setup too old for them is offered first. Chosen: a list to reorder; the others: a switch each, which adds one at the end
  const wTurnOn = async (w) => {
    if ((w === 'usage' || w === 'timer') && !await homeLink.setup('home_usage')) return false;
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
  // QR cards (HomeConfig "qrs", home_qr.js): a code of anything, up to four, each its own card among the others (order, size, off in the list above)
  const qrAdd = el('button', { type: 'button', className: 'small', textContent: T('home.qr_add'), onclick: async () => {
    const k = HOME_QR_KEYS.find((x) => !(r.config.qrs || {})[x]);
    const card = k && await homeQrAsk(null);
    if (card) send({ qrs: { ...r.config.qrs, [k]: card }, widgets: [...r.config.widgets, k] });
  } });
  const qrHead = el('div', { className: 'card-head' }, el('h3', { textContent: T('home.qr') }), qrAdd), qrList = el('div', { className: 'list' }), qrNote = el('p', { className: 'note', textContent: T('home.qr_note') });
  // a web page as the background and the screensaver (HomeConfig.wurl, http or https): asked in its own box when its tile is pressed
  // (a field at the end of thirty tiles went unseen), tried first (homeWebAsk); while it shows, its address and a way to change it
  const webAsk = async () => { const u = await homeWebAsk(r.config.wurl || ''); if (u) send({ wurl: u, wall: 'web' }); };
  const webNow = el('span'), webEdit = el('button', { type: 'button', className: 'small', textContent: T('home.web_change'), onclick: webAsk });
  const webOff = el('button', { type: 'button', className: 'small', textContent: T('home.web_clear'), onclick: () => send({ wurl: '' }) });
  const webHead = el('h3', { textContent: T('home.web') }), webLine = el('div', { className: 'item' }, el('div', { className: 'name' }, webNow), el('div', { className: 'acts' }, webEdit, webOff));
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

  // "Add to home screen": a picker of the apps (hidden ones too), their shortcuts and the live tiles; a press puts one on the grid or
  // takes it off and the list stays open. Shortcuts and live tiles are in config "tiles" (the order they have on the grid), an app is
  // on the grid unless it is in "hidden"
  const addHome = el('button', { type: 'button', className: 'small', textContent: T('home.qk_addhome'), onclick: async () => {
    const has = (t) => { const c = r.config; return Target.kind(t) === 'app' ? !c.hidden.includes(t) : (c.tiles || []).includes(t); };
    const toggle = (t) => {
      const c = r.config;
      if (Target.kind(t) === 'app') send(c.hidden.includes(t) ? { hidden: c.hidden.filter((x) => x !== t) } : { hidden: [...c.hidden, t], dock: c.dock.filter((x) => x !== t) });
      else send((c.tiles || []).includes(t) ? { tiles: c.tiles.filter((x) => x !== t) } : { tiles: [...(c.tiles || []), t] });
    };
    pickTarget(T('home.qk_addhome'), '', r.config, r.apps, { toggle, has });
  } });
  function drawTiles(c) {
    const on = !!c.tilesOk || !!c.pinsOk; tilesHead.hidden = tilesNote.hidden = tilesList.hidden = addHome.hidden = !on;
    if (!on) return;
    const have = c.tiles || [], offer = c.tilesAvail || [];
    tilesNote.textContent = T(c.tilesState === 'untrusted' ? 'home.tiles_untrusted' : offer.length || have.length || c.pinsOk ? 'home.tiles_note' : 'home.tiles_missing');
    tilesList.replaceChildren(...have.map((k) => row(k, [tool('✕', T('home.tile_remove'), () => send({ tiles: have.filter((x) => x !== k) }))])));   // a tile whose provider is away stays, to take off
  }
  function draw() {
    const c = r.config;
    drawTiles(c);
    drawThemes(c);
    orderHead.hidden = orderBox.hidden = c.order === undefined;      // a TV Home older than its own order
    if (c.order !== undefined) orderDraw(c, r.apps);
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
    const vOk = !!r.video;                                        // a TV Home from before video has no library
    vupload.hidden = vstate.hidden = !vOk;
    const pl = homePerf((r.video || {}).perf, c.wall); vperf.hidden = !pl;
    if (pl) put(vperf, 'textContent', T(pl[0], pl[1]));
    for (const x of [vHead, vsound.parentNode || vsound, vvol, vfpsHead, vfps, vresHead, vres, vnote]) x.hidden = !vOk;
    vsound.checked = !!c.vsound;
    if (document.activeElement !== vvolIn) { vvolIn.value = String(c.vvol ?? 50); vvolOut.textContent = (c.vvol ?? 50) + '%'; }
    vvol.hidden = !vOk || !c.vsound;
    const chips = (box, key, list, word) => box.replaceChildren(...list.map((k) => el('button', { type: 'button', className: (c[key] || '') === k ? 'on' : '', 'aria-pressed': String((c[key] || '') === k), textContent: word(k), onclick: () => send({ [key]: k }) })));
    chips(vfps, 'vfps', ['', '60', '30', '24', '15'], (k) => k ? T('home.v_fps_n', { n: k }) : T('home.v_orig'));
    chips(vres, 'vres', ['', '1080', '720', '480'], (k) => k ? T('home.v_res_n', { n: k }) : T('home.v_res_screen'));
    everyHead.hidden = every.hidden = !c.wrotate; if (!c.wrotate) everyNote.hidden = true;
    clock.checked = c.clock24; greet.checked = c.greet; anim.checked = c.anim !== false;
    dream.checked = !!c.dream;
    power.checked = c.power !== false;
    const noPower = c.power === undefined;                           // a TV Home older than the power button: shown off, with its update
    power.disabled = noPower; powerOld.hidden = !noPower;
    if (noPower) power.checked = false;
    dream.closest('label').hidden = c.dream === undefined;           // a TV Home older than the screensaver
    const ka = c.keyapps || {}, kOk = !!c.keysOk;                     // an older TV Home has neither: shown, with its update
    keyOn.checked = !!c.keyon; autoOn.checked = !!c.autoon;
    keyOn.disabled = autoOn.disabled = !kOk; keysOld.hidden = kOk;
    keysNote.textContent = kOk ? T(!c.keysSetup ? 'home.keys_need_setup' : 'home.keys_everywhere') : '';
    autoNote.textContent = kOk ? T(!c.keysSetup ? 'home.auto_need_setup' : 'home.auto_note') : '';
    keysList.hidden = !kOk;
    if (kOk) quickKeysDraw(keysList, c, r.apps, send);              // the slots: 1-9 and 0, the colour keys, the power-on app
    const w = c.widgets;                                              // absent: a TV Home older than the cards
    const qrs = c.qrs || {};                                          // the QR cards' fields, by key (absent: a TV Home older than them)
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
      el('div', { className: 'name' }, el('span', { textContent: HOME_WIDGET_WORDS[k] ? T(HOME_WIDGET_WORDS[k]) : qrs[k] ? homeQrName(qrs[k]) : T('home.w_newer', { k }) }), ...(waits(k, i) ? [el('small', { textContent: waits(k, i) })] : [])),
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
    webHead.hidden = webLine.hidden = !(c.wrotate && c.wweb && c.wurl);   // a TV Home older than the web page; none set yet: its tile asks
    const onWeb = c.wall === 'web' && c.wmode !== undefined, snap = c.wmode === 'snap';   // a TV Home older than the modes has none
    webModeHead.hidden = webMode.hidden = webModeNote.hidden = !onWeb;
    webEveryHead.hidden = webEvery.hidden = webKeep.hidden = !(onWeb && snap);
    webMode.replaceChildren(...Object.keys(HOME_WEBMODE).map((m) => el('button', { type: 'button', className: c.wmode === m ? 'on' : '', 'aria-pressed': String(c.wmode === m), textContent: T(HOME_WEBMODE[m]), onclick: () => send({ wmode: m }) })));
    webEvery.replaceChildren(...[1, 5, 15, 30, 60].map((n) => el('button', { type: 'button', className: c.wsnap === n ? 'on' : '', 'aria-pressed': String(c.wsnap === n), textContent: T('home.web_every_n', { n }), onclick: () => send({ wsnap: n }) })));   // HomeConfig.WSNAPS
    webKeepBox.checked = !!c.wkeep;
    webModeNote.textContent = T(!snap ? 'home.web_mode_note_live' : c.wkeep ? 'home.web_mode_note_keep' : 'home.web_mode_note_snap');
    put(webNow, 'textContent', c.wurl || '');
    placeHead.hidden = placeLine.hidden = c.place === undefined || !has('weather');   // a TV Home older than the weather; and only while the card is on
    if (document.activeElement !== place) place.value = c.place || '';
    placeAuto.disabled = !c.place;
    const qrKeys = HOME_QR_KEYS.filter((k) => qrs[k]);
    qrHead.hidden = qrNote.hidden = !c.qrOk;                          // a TV Home older than the QR cards
    qrList.hidden = !c.qrOk || !qrKeys.length;
    qrAdd.disabled = qrKeys.length >= HOME_QR_KEYS.length;
    qrList.replaceChildren(...qrKeys.map((k) => el('div', { className: 'item' },
      el('div', { className: 'name' }, el('span', { textContent: homeQrName(qrs[k]) }), ...(has(k) ? [] : [el('small', { textContent: T('home.qr_hidden_now') })])),
      el('div', { className: 'acts' },
        tool('✎', T('home.qr_edit'), async () => { const card = await homeQrAsk(qrs[k]); if (card) send({ qrs: { ...qrs, [k]: card } }); }),
        el('button', { type: 'button', className: 'small', textContent: T(has(k) ? 'home.qr_hide' : 'home.qr_show'), onclick: () => send({ widgets: has(k) ? w.filter((x) => x !== k) : [...w, k] }) }),
        tool('✕', T('home.remove'), () => { const n = { ...qrs }; delete n[k]; send({ qrs: n, widgets: w.filter((x) => x !== k) }); })))));
    noteHead.hidden = noteLine.hidden = c.note === undefined;     // a TV Home older than the note
    if (document.activeElement !== note) note.value = c.note || '';
    unpin.disabled = !c.note;
    sums(c);
  }

  // A TV Home that rotates (config.wrotate): the scenes drawn here by the same recipe the TV uses, the colours, Bing's picture
  // and the person's photos, each a tile. Rotation off: one is chosen. On: every tile is in or out of the set (at least one stays).
  function drawRotation(c) {
    const multi = c.wevery !== 'off', set = c.walls || [c.wall];
    const isOn = (k) => multi ? set.includes(k) : c.wall === k;
    const pick = (k) => {
      if (k === 'web') return webAsk();                               // always the box with the address (the one in use filled in, checked again): a line for it under the pictures was out of sight
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
      const del = el('button', { type: 'button', className: 'small iconbtn wallx', textContent: '✕', title: themeUses(c, p.id) ? T('home.v_in_use') : T('home.photo_remove'), 'aria-label': T('home.photo_remove'), disabled: themeUses(c, p.id), onclick: () => dropPhoto(p.id) });
      const cell = tile(p.id, face, T('home.wall_photo_n', { n: i + 1 }), del);
      if (window.ONTV) { del.hidden = true; holdDrop(cell.firstChild, p.id); }
      return cell;
    });
    const videos = ((r.video || {}).items || []).map((v) => {
      const t = homeThumbs[v.id + ':' + v.added], face = el('i', {}, t ? el('img', { src: 'data:image/jpeg;base64,' + t, alt: '' }) : '🎬');
      const used = isOn(v.id) || c.wall === v.id || set.includes(v.id) || themeUses(c, v.id);         // TV Home refuses to delete one in use; the button says so before
      const del = el('button', { type: 'button', className: 'small iconbtn wallx', textContent: '✕', title: used ? T('home.v_in_use') : T('home.v_remove'), 'aria-label': T('home.v_remove'), disabled: used, onclick: () => dropPhoto(v.id) });
      const cell = tile(v.id, face, v.name || T('home.wall_video'), del);
      if (window.ONTV) { del.hidden = true; if (!used) holdDrop(cell.firstChild, v.id); }
      return cell;
    });
    holdNote.hidden = !(window.ONTV && (photos.length || videos.length));
    walls.replaceChildren(bing, ...webTile, ...photos, ...videos, ...art, ...plain);         // as on the TV (HomeConfig.WALLS): what changes by itself and the person's own first
    every.replaceChildren(...Object.keys(HOME_EVERY).map((e) => el('button', { type: 'button', className: c.wevery === e ? 'on' : '', 'aria-pressed': String(c.wevery === e), textContent: T(HOME_EVERY[e]), onclick: () => send({ wevery: e }) })));
    put(everyNote, 'textContent', multi ? T(set.length < 2 && set[0] !== 'bing' ? 'home.rotate_pick' : 'home.rotate_set') : '');   // Bing alone turns through its last 8 days
    everyNote.hidden = !multi;
  }

  // TV Home's own page (home.html) has nothing else to show: the settings are the page itself, not a box over an empty one.
  // Seven groups as on the TV (Customize.kt): a row each with what is set now under its name, a page of controls behind it. All
  // at once was a page of forty controls nobody found anything in. On TV Home's own page the phone's Back goes back to the list.
  // Themes (HomeConfig "theme"; ready-made ones and the person's, kept on the TV): one tap switches the background and its set, the veil, the cards,
  // the grid, the focus, the motion and the clock at once. A saved one keeps its photo / video (the TV refuses to delete one a theme shows).
  const themeReady = el('div', { className: 'fchips homewalls themes' }), themeMineHead = el('h3', { textContent: T('home.theme_mine') }), themeMine = el('div', { className: 'fchips homewalls themes' });
  const themeName = el('input', { type: 'text', maxLength: 24, placeholder: T('home.theme_name_ph'), 'aria-label': T('home.theme_name') });
  const themeSave = el('button', { type: 'button', className: 'primary', textContent: T('home.theme_save'), onclick: () => { const n = themeName.value.trim(); if (n) { send({ theme: { save: n } }); themeName.value = ''; } } });
  themeName.addEventListener('keydown', (e) => { if (e.key === 'Enter') themeSave.click(); });
  const themeNote = el('p', { className: 'note', textContent: T('home.theme_note') });
  const themeHead = el('h3', { textContent: T('home.theme_save_head') }), themeLine = UI.line(themeName, themeSave);
  const wallFace = (k) => {
    if (HOME_WALLS[k]) { const dot = el('i'); dot.style.setProperty('--c', 'linear-gradient(135deg,' + HOME_WALLS[k] + ')'); return dot; }
    if (HOME_ART_IDS.includes(k)) { const cv = el('canvas', { width: 128, height: 72 }); homeArtPaint(cv.getContext('2d'), 128, 72, k); return el('i', {}, cv); }
    const p = (r.photos || []).find((x) => x.id === k) || ((r.video || {}).items || []).map((v) => ({ id: v.id, v: v.added })).find((x) => x.id === k), t = p && homeThumbs[k + ':' + p.v];
    return el('i', t ? {} : { className: 'add' }, t ? el('img', { src: 'data:image/jpeg;base64,' + t, alt: '' }) : k === 'bing' ? '🌐' : k === 'web' ? '🖥️' : /^vid:/.test(k) ? '🎬' : '🖼️');
  };
  const themeUses = (c, id) => (c.themes || []).some((t) => t.conf.wall === id || (t.conf.walls || []).includes(id));   // HomeConfig.themeUses
  function drawThemes(c) {
    const tile = (id, name, conf, mine) => {
      const on = c.theme === id;
      const b = el('button', { type: 'button', className: on ? 'on' : '', 'aria-pressed': String(on), title: name, onclick: () => send({ theme: { apply: id } }) }, wallFace(conf.wall), el('span', { textContent: name }));
      if (!mine) return b;
      const del = el('button', { type: 'button', className: 'small iconbtn wallx', textContent: '✕', title: T('home.theme_del'), 'aria-label': T('home.theme_del'),
        onclick: async () => { if (await ask(T('home.theme_del_q'), { ok: T('home.theme_del'), danger: true })) send({ theme: { del: id } }); } });
      return el('span', { className: 'wallcell' }, b, del);
    };
    themeReady.replaceChildren(...(c.themesBuiltin || []).map((t) => tile(t.id, T(HOME_THEME_WORDS[t.id]), t.conf, false)));
    themeMine.replaceChildren(...(c.themes || []).map((t) => tile(t.id, t.name, t.conf, true)));
    themeMineHead.hidden = themeMine.hidden = !(c.themes || []).length;
    themeSave.disabled = (c.themes || []).length >= HOME_THEMES_MAX;
    put(themeNote, 'textContent', T(themeSave.disabled ? 'home.theme_full' : 'home.theme_note'));
  }

  const page = homeLink.direct;
  const check = (box, words) => el('label', { className: 'check' }, box, el('span', { textContent: T(words) }));
  const GROUPS = [
    ['themes', 'star', 45, 'home.themes', [themeReady, themeMineHead, themeMine, themeHead, themeLine, themeNote]],
    ['bg', 'image', 330, 'home.wall', [everyHead, every, everyNote, el('div', { className: 'actions' }, upload, vupload), vstate, vperf, walls, holdNote, webHead, webLine, webModeHead, webMode, webEveryHead, webEvery, webKeep, webModeNote, shadeHead, shade, shadeNote, vHead, check(vsound, 'home.v_sound'), vvol, vfpsHead, vfps, vresHead, vres, vnote, file, vfile, galHead, gal, credit]],
    ['look', 'sparkle', 180, 'home.g_look', [focusHead, focus, motionHead, motion, check(anim, 'home.anim'), check(clock, 'home.clock24'), check(greet, 'home.greet')]],
    ['layout', 'apps', 265, 'home.g_layout', [colsHead, cols, rowsHead, rowsLine, rowsNote, orderHead, orderBox, tilesHead, tilesNote, addHome, tilesList,
      el('div', { className: 'card-head' }, el('h3', { textContent: T('home.hidden') }), addHidden), hidden]],
    ['widgets', 'chart', 210, 'home.g_widgets', [wHead, wList, ...wLines, packHead, packBox, packNote, layHead, lay, layNote, placeHead, placeLine, noteHead, noteLine, qrHead, qrList, qrNote]],
    ['system', 'power', 260, 'home.g_system', [check(power, 'home.power'), powerOld, check(dream, 'home.dream')]],
    ['keys', 'keyboard', 230, 'home.keys', [keysOld, check(keyOn, 'home.keys_on'), keysNote, keysList, check(autoOn, 'home.auto_on'), autoNote]],
    ['lang', 'globe', 200, 'home.lang', [langs]],
  ];
  keysHead.hidden = langHead.hidden = true;                           // the page's own title says it
  const title = el('h2', { textContent: T('home.title') });
  const backBtn = el('button', { type: 'button', className: 'small', textContent: T('common.back'), hidden: true, onclick: () => page ? history.back() : go(null) });
  const subs = {}, rows = {}, pages = {};
  const menu = el('div', { className: 'homemenu' }, ...GROUPS.map(([k, icon, hue, words]) => {
    subs[k] = el('small');
    return (rows[k] = el('button', { type: 'button', className: 'mitem', onclick: () => go(k) },
      el('span', { className: 'mi' }, UI.icon(icon, hue)), el('span', { className: 'mt' }, el('b', { textContent: T(words) }), subs[k]), el('span', { className: 'mgo', textContent: '›' })));
  }));
  const body = el('div', { className: 'homepages' }, menu, ...GROUPS.map(([k, , , , kids]) => (pages[k] = el('div', { className: 'homepage', hidden: true }, ...kids))));
  let at = null;
  function go(k, push = true) {
    const from = at; at = k;
    menu.hidden = !!k; for (const [g, v] of Object.entries(pages)) v.hidden = g !== k;
    put(title, 'textContent', k ? T(GROUPS.find((g) => g[0] === k)[3]) : T('home.title'));
    backBtn.hidden = !k;
    if (page && push && k) history.pushState({ homeGroup: k }, '');
    (page ? document.scrollingElement : d).scrollTop = 0;
    const first = k ? pages[k].querySelector('button:not([hidden]):not(:disabled), input:not(:disabled)') : rows[from];
    if (first && (window.ONTV || !k)) first.focus({ preventScroll: true });   // back on the list: on the row it came from; a phone gets no keyboard popping up
  }
  const onPop = () => { if (at) go(null, false); };
  if (page) addEventListener('popstate', onPop);
  const onOff = (v) => T(v ? 'home.sum_on' : 'home.sum_off');
  const wallWord = (c) => c.wall === 'web' ? T('home.wall_web') : /^photo\d*$/.test(c.wall) ? T('home.wall_photo_n', { n: (+c.wall.slice(5) || 1) })
    : /^vid:/.test(c.wall) ? (((r.video || {}).items || []).find((v) => v.id === c.wall) || {}).name || T('home.wall_video')
    : c.wall === 'bing' ? T('home.wall_bing') : HOME_WALL_WORDS[c.wall] ? T(HOME_WALL_WORDS[c.wall]) : c.wall;
  // the line under each group's name, as the TV's Customize writes it (summary())
  function sums(c) {
    rows.themes.hidden = !c.themesOk;                                // a TV Home older than the themes
    put(subs.themes, 'textContent', !c.themesOk ? '' : !c.theme ? T('home.theme_custom') : ((c.themes || []).find((t) => t.id === c.theme) || {}).name || T(HOME_THEME_WORDS[c.theme]));
    const set = c.wrotate && c.wevery && c.wevery !== 'off' && c.wall !== 'web';
    put(subs.bg, 'textContent', [set ? T('home.sum_bg_set', { n: (c.walls || []).length }) : wallWord(c), c.shadeOk ? T('home.sum_shade', { n: c.shade ?? 50 }) : ''].filter(Boolean).join(' · '));
    put(subs.look, 'textContent', [c.focus !== undefined ? T(c.focus === 'ring' ? 'home.focus_ring' : 'home.focus_glass') : '',
      c.anim === false ? T('home.sum_still') : c.motion !== undefined ? T(c.motion === 'calm' ? 'home.motion_calm' : c.motion === 'full' ? 'home.motion_full' : 'home.motion_auto') : '',
      c.clock24 ? '24h' : '12h'].filter(Boolean).join(' · '));
    put(subs.layout, 'textContent', [c.colsOk ? T('home.sum_cols', { n: c.cols || 5 }) : '', c.rowsOk ? (c.rows >= 1 ? T('home.sum_rows', { n: c.rows }) : T('home.sum_rows_auto')) : '',
      c.hidden.length ? T('home.sum_hidden', { n: c.hidden.length }) : ''].filter(Boolean).join(' · '));
    put(subs.widgets, 'textContent', c.widgets ? T('home.sum_widgets', { n: c.widgets.length }) : '');
    put(subs.system, 'textContent', T('home.sum_system', { power: onOff(c.power !== false), dream: onOff(!!c.dream) }));
    put(subs.keys, 'textContent', T('home.sum_keys', { keys: onOff(!!c.keyon), auto: onOff(!!c.autoon) }));
    put(subs.lang, 'textContent', T(c.lang === 'vi' ? 'home.lang_vi' : c.lang === 'en' ? 'home.lang_en' : 'home.lang_tv'));
    rows.lang.hidden = c.lang === undefined;                        // a TV Home older than the choice
    rows.widgets.hidden = !c.widgets;
  }
  const d = el('dialog', { className: 'ask homeask' + (page ? ' page' : '') }, el('div', { className: 'card' },
    el('div', { className: 'card-head' }, title, el('div', { className: 'tools' }, backBtn,
      page ? null : el('button', { type: 'button', className: 'small iconbtn', textContent: '✕', title: T('common.close'), 'aria-label': T('common.close'), onclick: () => d.close() }))),
    body));
  d.addEventListener('cancel', (e) => { if (at && !page) { e.preventDefault(); go(null); } });   // Esc, or the TV's Back: to the list first
  d.addEventListener('close', () => { d.remove(); if (page) { put('main', 'hidden', false); removeEventListener('popstate', onPop); } homeCard(); });
  draw();
  document.body.append(d);
  if (page) { put('main', 'hidden', true); d.show(); } else d.showModal();
  if (!window.ONTV) document.activeElement?.blur();                   // opening focuses the first row: ringed, it read as picked; the D-pad needs it
}

// The web page for the background, in a box of its own: the address, then the TV (or, on a host's page, this device) tries it
// (WebCheck.kt) and says what it found, the page's title when it opened. Use is pressed once: it tries first, and a page that
// failed asks to be used anyway once the reason shows (a page that needs the TV's own login, a host not on the TV's Wi-Fi).
// The address, or null.
const HOME_WEB_WHY = { bad: 'home.web_why_bad', dns: 'home.web_why_dns', refused: 'home.web_why_refused', timeout: 'home.web_why_timeout', ssl: 'home.web_why_ssl',
  cleartext: 'home.web_why_cleartext', status: 'home.web_why_status', loop: 'home.web_why_loop', type: 'home.web_why_type', net: 'home.web_why_net' };   // WebCheck.kt's "why"
function homeWebAsk(now) {
  return new Promise((done) => {
    let tried = '', good = false;
    const input = el('input', { type: 'url', inputMode: 'url', maxLength: 500, value: now, placeholder: T('home.web_ph'), 'aria-label': T('home.web'), autocomplete: 'off' });
    const state = el('p', { className: 'state', hidden: true }), warn = el('p', { className: 'warnline', hidden: true });
    const tryBtn = el('button', { type: 'button', textContent: T('home.web_try') });
    const use = el('button', { type: 'button', className: 'primary', textContent: T('home.web_set') });
    const cancel = el('button', { type: 'button', textContent: T('common.cancel'), onclick: () => d.close() });
    const d = el('dialog', { className: 'ask homeweb' }, el('div', { className: 'card' },
      el('h2', { textContent: T('home.web') }),
      el('p', { className: 'note', textContent: T('home.web_note') }),
      UI.line(input, tryBtn), state, warn,
      el('p', { className: 'note', textContent: T(homeLink.direct ? 'home.web_from_tv' : 'home.web_from_here') }),
      el('div', { className: 'actions' }, cancel, use)));
    let answer = null;
    const clean = () => input.value.trim();
    const shown = (ok, words) => { state.hidden = !ok; warn.hidden = ok; put(ok ? state : warn, 'textContent', words); };
    const test = async () => {
      const u = clean();
      if (!/^https?:\/\/[^\s/?#]+\S*$/i.test(u)) { shown(false, T('home.web_bad')); input.focus(); return false; }
      const back = UI.busy(tryBtn, T('home.web_trying')); use.disabled = true;
      let a;
      try { a = await homeLink.check(u); } finally { back(); use.disabled = false; }
      tried = u; good = !!(a && a.ok);
      if (!a) shown(false, T('home.web_why_net'));
      else if (a.ok) shown(true, a.title ? T('home.web_ok_title', { title: a.title }) : T('home.web_ok'));
      else shown(false, T(HOME_WEB_WHY[a.why] || 'home.web_why_net', { code: a.code || '', type: a.type || '' }));
      put(use, 'textContent', T(good ? 'home.web_set' : 'home.web_anyway'));
      return good;
    };
    tryBtn.onclick = test;
    use.onclick = async () => {
      const u = clean();
      if (u !== tried && !await test()) return;                       // failed just now: the reason shows, and the button is Use anyway
      answer = u; d.close();
    };
    input.addEventListener('input', () => { if (clean() !== tried) { state.hidden = warn.hidden = true; put(use, 'textContent', T('home.web_set')); } });
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); use.click(); } });
    d.addEventListener('close', () => { d.remove(); done(answer); });
    document.body.append(d); d.showModal();
    if (!window.ONTV) input.focus();
    if (now) test();                                                  // the page in use: is it still there
  });
}

// The wallpaper gallery (docs/home/wallpapers.json, signed): small pictures to choose from; the one chosen is downloaded here,
// checked against the list and sent to the TV like a photo of the person's own. The credit shows under the pictures.
async function homeGallery(box, credit, head, done) {
  const g = await homeLink.walls(true);                              // none on TV Home's own page: the hosts download the gallery
  if (!g || !g.walls || !g.walls.length) { box.hidden = credit.hidden = head.hidden = true; return; }   // no list (offline): no empty heading either
  const lang = (document.documentElement.lang || 'en').slice(0, 2);
  const note = (w) => T('home.credit', { name: w.name[lang] || w.name.en, credit: w.credit, license: w.license, kb: w.kb });
  box.replaceChildren(...g.walls.map((w) => {
    const pic = el('img', { src: w.thumb, alt: '', loading: 'lazy' });
    const b = el('button', { type: 'button', title: note(w), onclick: async () => {
      if (box.dataset.busy) return;
      box.dataset.busy = '1'; box.classList.add('busy'); toast(T('home.sending_photo'));
      const n = await homeLink.wall(w.id);
      delete box.dataset.busy; box.classList.remove('busy');
      if (n && n.installed) { done(n); toast(T('home.photo_done')); }
    } }, pic, el('span', { textContent: w.name[lang] || w.name.en }));
    const show = () => put(credit, 'textContent', note(w));
    b.addEventListener('pointerenter', show); b.addEventListener('focus', show);
    return b;
  }));
}

// The TV's real shape for a preview, from what TV Home reports (snapshot "display": the panel's physical size): 1920 wide, as tall as
// the panel is. Without it (an older TV Home) 16:9.
function homeShape() {
  const d = homeNow && homeNow.display;
  return d && d.w > 0 && d.h > 0 ? [1920, Math.max(1, Math.round(1920 * d.h / d.w))] : [1920, 1080];
}

// The one line about how the video plays on THIS TV (TV Home measures it for 20 s after each start: wall/Videos.kt Verdict, `video.perf`
// {id, fps, want, cpu, level: ok|choppy|slow, off}): [id, words' values] or null (nothing measured for the wall on now).
function homePerf(perf, wall) {
  if (!perf || !wall || perf.id !== wall) return null;
  const a = { fps: perf.fps, want: perf.want, cpu: perf.cpu };
  return perf.off ? ['home.v_perf_off', a] : perf.level === 'ok' ? ['home.v_perf_ok', a] : ['home.v_perf_bad', a];
}

// A video or GIF as the background: shown in the TV's frame first (the TV covers the screen with it, as this box does), then sent
// (progress by the bytes), then waited for while the TV makes it light (progress by TV Home's job). [say] writes the state line.
async function homeVideo(f, say) {
  try {
    if (!await homeVideoAsk(f)) return null;
    return await homeVideoWait(await homeLink.video(f, (pct) => say(T('home.v_up', { pct }))), say);
  } catch (e) { toast(T('common.error_msg', { msg: friendly(e.message) })); return null; }
}

// Until TV Home's job ends: the answer's video.job {pct, error}. A few failed reads in a row end the wait (the TV went away).
async function homeVideoWait(n, say) {
  let lost = 0;
  while (n && n.video && n.video.job && n.video.job.error == null && n.video.job.pct < 100 && lost < 5) {
    say(T('home.v_make', { pct: n.video.job.pct }));
    await new Promise((ok) => setTimeout(ok, 1500));
    const t = await homeLink.read(false, true);
    if (t) { n = t; lost = 0; } else lost++;
  }
  const j = n && n.video && n.video.job;
  if (j && j.error) throw new Error('home:video_' + j.error);
  toast(T('home.v_done'));
  return n;
}

function homeVideoAsk(f) {
  return new Promise((ok) => {
    const [W, H] = homeShape(), url = URL.createObjectURL(f);
    const gif = /\.gif$/i.test(f.name) || f.type === 'image/gif';
    const m = gif ? el('img', { src: url, alt: '' }) : el('video', { src: url, loop: true, playsInline: true });
    if (!gif) { m.muted = true; m.autoplay = true; m.play().catch(() => {}); }
    const box = el('div', { className: 'vidprev' }, m); box.style.setProperty('--ar', W + ' / ' + H);
    let yes = false;
    const d = el('dialog', { className: 'ask' }, el('div', { className: 'card' },
      el('h2', { textContent: T('home.v_title') }), box, el('p', { className: 'note', textContent: T('home.v_hint') }),
      el('div', { className: 'actions' },
        el('button', { type: 'button', textContent: T('common.cancel'), onclick: () => d.close() }),
        el('button', { type: 'button', className: 'primary', textContent: '🎬 ' + T('home.crop_send'), onclick: () => { yes = true; d.close(); } }))));
    d.addEventListener('close', () => { URL.revokeObjectURL(url); d.remove(); ok(yes); });
    document.body.append(d); d.showModal();
  });
}

// A photo as the background: made the TV's size here (a phone's 12 MP photo would take a while over ADB and fill the
// TV's memory), shown in the TV's frame first so the crop can be moved (photofit.js, the same as TV Home's own phone page), then sent
async function homePhoto(f, done) {
  try {
    const img = await photoOpen(f);
    const [W, H] = homeShape();
    const view = await homeCropAsk(img, W, H);
    if (!view) return false;                                          // cancelled: nothing sent, nothing said
    toast(T('home.sending_photo'));
    const blob = await photoBlob(img, W, H, view, 0.85);
    const n = await homeLink.photo(blob);
    done(n); toast(T('home.photo_done'));
    return true;
  } catch (e) { toast(T('common.error_msg', { msg: friendly(e.message) })); return false; }
}

// A picture put to [path] (the JPEG the crop made); the answer is TV Home's settings. The X-Tvh header is what a page of another site
// cannot send without a preflight: TV Home's own page takes no cross-site post.
async function homeSend(path, blob, onPct) {
  if (onPct) return homeSendProgress(path, blob, onPct);
  const x = await fetch(path, { method: 'POST', headers: { 'Content-Type': 'application/octet-stream', 'X-Tvh': '1' }, body: blob });
  const n = await x.json().catch(() => ({}));
  if (!x.ok) throw new Error(n.error || x.statusText);
  return n;
}

// Same with the bytes counted (fetch cannot say how far an upload is): a video of 100 MB takes a while. The direct page's refusals
// come as bare words (size, full, space, busy, type): said as the hosts say them, so one table of words (friendly) serves both.
function homeSendProgress(path, blob, onPct) {
  return new Promise((ok, no) => {
    const x = new XMLHttpRequest();
    x.open('POST', path);
    x.setRequestHeader('Content-Type', 'application/octet-stream'); x.setRequestHeader('X-Tvh', '1');
    x.upload.onprogress = (e) => { if (e.lengthComputable) onPct(Math.min(99, Math.round(100 * e.loaded / e.total))); };
    x.onerror = () => no(new Error('network'));
    x.onload = () => {
      let n = {}; try { n = JSON.parse(x.responseText); } catch (e) { /* not JSON */ }
      if (x.status >= 200 && x.status < 300) ok(n);
      else no(new Error(/^(size|full|space|busy|type)$/.test(n.error || '') ? 'home:video_' + n.error : n.error || x.statusText));
    };
    x.send(blob);
  });
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
        el('button', { type: 'button', className: 'primary', textContent: '🖼️ ' + T('home.crop_send'), onclick: () => { view = pv; d.close(); } }))));
    d.addEventListener('close', () => { d.remove(); ok(view); });
    document.body.append(d); d.showModal();
    const pv = photoCrop(cv, img);
  });
}

$('#homeopen')?.addEventListener('click', homeOpen);                  // the hosts' page has the buttons; TV Home's own opens the settings at once (homeweb.js)
$('#homeupd')?.addEventListener('click', homeUpdate);
