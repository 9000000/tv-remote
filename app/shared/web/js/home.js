// 🏠 TV Home, the project's own launcher (app/home), set up from here (Home.kt → HomeReceiver on the TV): favourite apps in
// their order, apps left off its grid, the background (a built-in one or a photo from this device), the clock. Each change
// is sent at once and shows on the TV's screen as it comes.
let homeNow = null;
onTvChange(() => { homeNow = null; homeCard(); });

// the gradients TV Home draws (HomeActivity.wallColors), small enough to tell them apart
const HOME_WALLS = { night: '#0b0f1a,#18223f', ocean: '#03192b,#0a4a66', dusk: '#1a0f2e,#7a3b3b', forest: '#06140f,#133b2d', aurora: '#071a1f,#2b2a6b', black: '#000,#000' };
const HOME_WALL_WORDS = { night: 'home.wall_night', ocean: 'home.wall_ocean', dusk: 'home.wall_dusk', forest: 'home.wall_forest', aurora: 'home.wall_aurora', black: 'home.wall_black' };
const HOME_DOCK_MAX = 6;                                              // HomeConfig.DOCK_MAX

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
  if (needAdb()) return;
  let r = await api('/api/home'); if (!r) return;
  if (!r.installed) { homeCard(); return; }
  const apps = () => r.apps.map((a) => a.pkg);
  const send = async (change) => {
    const n = await api('/api/home', change);
    if (n && n.installed) { r = n; homeNow = n; draw(); }
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
  const walls = el('div', { className: 'fchips homewalls' });
  const file = el('input', { type: 'file', accept: 'image/*', hidden: true, onchange: () => { if (file.files[0]) homePhoto(file.files[0], (n) => { r = n; homeNow = n; draw(); }); file.value = ''; } });
  const clock = el('input', { type: 'checkbox', onchange: () => send({ clock24: clock.checked }) });
  const greet = el('input', { type: 'checkbox', onchange: () => send({ greet: greet.checked }) });

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
    walls.replaceChildren(...Object.keys(HOME_WALLS).map((w) => {
      const dot = el('i'); dot.style.setProperty('--c', 'linear-gradient(135deg,' + HOME_WALLS[w] + ')');
      return el('button', { type: 'button', className: c.wall === w ? 'on' : '', onclick: () => send({ wall: w }) }, dot, el('span', { textContent: T(HOME_WALL_WORDS[w]) }));
    }), r.photo ? el('button', { type: 'button', className: c.wall === 'photo' ? 'on' : '', onclick: () => send({ wall: 'photo' }) }, el('span', { textContent: T('home.wall_photo') })) : null,
    el('button', { type: 'button', onclick: () => file.click() }, el('span', { textContent: T('home.upload') })));
    clock.checked = c.clock24; greet.checked = c.greet;
  }

  const d = el('dialog', { className: 'ask homeask' }, el('div', { className: 'card' },
    el('div', { className: 'card-head' }, el('h2', { textContent: T('home.title') }), el('button', { type: 'button', className: 'small iconbtn', textContent: '✕', title: T('common.close'), 'aria-label': T('common.close'), onclick: () => d.close() })),
    el('div', { className: 'card-head' }, el('h3', { textContent: T('home.dock') }), addDock), dock,
    el('h3', { textContent: T('home.wall') }), walls, file,
    el('label', { className: 'check' }, clock, el('span', { textContent: T('home.clock24') })),
    el('label', { className: 'check' }, greet, el('span', { textContent: T('home.greet') })),
    el('div', { className: 'card-head' }, el('h3', { textContent: T('home.hidden') }), addHidden), hidden));
  d.addEventListener('close', () => { d.remove(); homeCard(); });
  draw();
  document.body.append(d); d.showModal();
}

// A photo as the background: made the TV's size here (a phone's 12 MP photo would take a while over ADB and fill the
// TV's memory), cropped to fill the screen, then sent
async function homePhoto(f, done) {
  toast(T('home.sending_photo'));
  try {
    const img = await createImageBitmap(f);
    const W = 1920, H = 1080, s = Math.max(W / img.width, H / img.height);
    const c = el('canvas', { width: W, height: H });
    c.getContext('2d').drawImage(img, (W - img.width * s) / 2, (H - img.height * s) / 2, img.width * s, img.height * s);
    const blob = await new Promise((ok) => c.toBlob(ok, 'image/jpeg', 0.85));
    const x = await fetch('/api/home/photo', { method: 'POST', headers: { 'Content-Type': 'application/octet-stream' }, body: blob });
    const n = await x.json().catch(() => ({}));
    if (!x.ok) throw new Error(n.error || x.statusText);
    done(n); toast(T('home.photo_done'));
  } catch (e) { toast(T('common.error_msg', { msg: friendly(e.message) })); }
}

$('#homeopen').addEventListener('click', homeOpen);
$('#homeupd').addEventListener('click', homeUpdate);
