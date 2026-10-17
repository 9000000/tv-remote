// The building blocks of every tab but the remote (whose keys are laid by hand, to be hit without looking), so the tabs
// read the same: the markup in index.html follows the same shapes. Checked by tests/ui.test.js.
//
//   card        section.card: a head (h2, its tools on the right), then the body; cards space their own children
//   fold card   details.card: a summary (plain words, 14px), opened to the body
//   say         a card with one line: still reading, nothing there, cannot
//   group       div.group: h3 then its controls, one setting or one part of a card
//   note        p.note: a short line of explanation, under a title or a control
//   state       p.state: what is on now ("TV turns off at 23:30")
//   empty       p.empty: the line an empty list shows
//   warn        p.warnline: something to fix
//   list        div.list of div.item: icon, name (+ small line), acts
//   line        div.line: a field and the button beside it (--ctl)
//   chips       div.places: a choice among several, the current one .on (--chip)
//   actions     div.actions: a card's main buttons (--ctl); tools in a head and acts in a list are .small (--ctl-sm)
//
// No style="" or style: in markup: a look that is needed twice is a class here and in style.css ("UI kit").
const UI = {
  card(title, o = {}, ...body) {
    const c = el(o.fold ? 'details' : 'section', { className: 'card' + (o.cls ? ' ' + o.cls : '') });
    if (o.id) c.id = o.id;
    if (o.fold) c.append(el('summary', { textContent: title }));
    else c.append(UI.head(title, ...(o.tools || [])));
    c.append(...body.filter((k) => k != null));
    return c;
  },
  head: (title, ...tools) => el('div', { className: 'card-head' }, typeof title === 'string' ? el('h2', { textContent: title }) : title,
    tools.length ? el('div', { className: 'tools' }, ...tools) : null),
  /** A card with only a line in it: still reading, or nothing to show */
  say: (text, cls = 'note') => el('section', { className: 'card' }, el('p', { className: cls, textContent: text })),
  group: (title, ...body) => el('div', { className: 'group' }, title ? el('h3', { textContent: title }) : null, ...body),
  note: (text) => el('p', { className: 'note', textContent: text }),
  state: (text) => el('p', { className: 'state', textContent: text }),
  empty: (text) => el('p', { className: 'empty', textContent: text }),
  warn: (text) => el('p', { className: 'warnline', textContent: text }),
  list: (rows, emptyText) => rows.length ? el('div', { className: 'list' }, ...rows) : UI.empty(emptyText),
  /** {icon, name, sub, extra (more under the name), acts: [buttons]} */
  item: (o) => el('div', { className: 'item' }, o.icon || null,
    el('div', { className: 'name' }, el('span', { textContent: o.name }), o.sub ? el('small', { textContent: o.sub }) : null, ...(o.extra || [])),
    o.acts && o.acts.length ? el('div', { className: 'acts' }, ...o.acts) : null),
  line: (...kids) => el('div', { className: 'line' }, ...kids),
  /** [{label, on, fn, title, disabled}] */
  chips: (choices) => el('div', { className: 'places' }, ...choices.map((c) =>
    el('button', { className: c.on ? 'on' : '', textContent: c.label, onclick: c.fn, title: c.title || '', disabled: !!c.disabled }))),
  actions: (...btns) => el('div', { className: 'actions' }, ...btns.filter(Boolean)),
  /** A button that is greyed while its work runs. kind: 'primary', 'danger', 'small', or several */
  btn: (label, fn, kind = '') => el('button', { className: kind, textContent: label, onclick: async (e) => {
    const b = e.currentTarget; b.disabled = true; try { await fn(e); } finally { b.disabled = false; } } }),
};

// ---------- icons: the page is written with emoji (short, and i18n.js keys on them), drawn as line icons from the sprite
// (dev/gen_icons.py) in titles, tabs and buttons, where an emoji looks different on every system and an old TV has some
// missing. A title's icon takes its own colour (--h, a hue), a button's the button's. Emoji not listed here stay emoji;
// so do the remote's keys, app tiles, scene icons and running text.
const ICON = {
  '🎮': ['gamepad', 220], '📱': ['apps', 265], '📁': ['folder', 38], '🩺': ['pulse', 350], '⏰': ['alarm', 20], '📺': ['tv', 195],
  '🧭': ['compass', 175], '🖥️': ['monitor', 210], '🚀': ['rocket', 265], '⌨️': ['keyboard', 230], '🎬': ['scene', 300], '🎞️': ['scene', 300],
  '🌙': ['moon', 250], '😴': ['timer', 280], '⏳': ['hourglass', 38], '🔊': ['vol_up', 160], '🔇': ['mute', 160], '⚡': ['zap', 45],
  '🌐': ['globe', 200], '🛡️': ['shield', 150], '👁️': ['eye', 210], '🔁': ['refresh', 200], '🔄': ['refresh', 200], '↻': ['refresh', 200],
  '⚙️': ['gauge', 30], '📊': ['chart', 210], '📶': ['wifi', 190], '🧩': ['cpu', 275], '⬇️': ['download', 205], '⬇': ['download', 205],
  '⬆️': ['upload', 205], '📥': ['upload', 205], '📦': ['package', 30], '🖼️': ['image', 330], '🔠': ['type', 230], '🏠': ['home', 140],
  '💤': ['power', 260], '🗂️': ['files', 38], '❓': ['help', 0], 'ℹ️': ['info', 210], '✏️': ['pen', 40], '✎': ['pen', 40],
  '🔍': ['search', 210], '🗑': ['trash', 0], '📋': ['clipboard', 210], '🧹': ['sparkle', 180], '🔥': ['flame', 15], '💾': ['save', 210],
  '🔗': ['link', 210], '🎧': ['headphones', 290], '🐞': ['bug', 0], '🤖': ['bot', 265], '📝': ['list', 210], '🔒': ['lock', 0],
  '📜': ['log', 210], '🔌': ['plug', 45], '💬': ['message', 200], '🎤': ['mic', 0], '🛒': ['store', 140], '🔧': ['wrench', 30],
  '📲': ['phone', 265], '🧽': ['eraser', 0], '⏸': ['pause', 210], '⏹': ['stop', 0], '☀️': ['wake', 45], '⏱️': ['timer', 280],
  '🛠️': ['settings', 210], '🔔': ['bell', 45], '▶': ['play', 210], '↩': ['undo', 210], '✅': ['check', 145], '🌡️': ['thermometer', 15], '🔵': ['bluetooth', 220],
};
const TAB_HUE = { remote: 220, apps: 265, files: 38, health: 350, auto: 20, tv: 175 };
// where: titles (coloured) and buttons; not inside these
const ICON_TITLES = 'h2, h3, details > summary, .setrow > b, .step b, .qr > b, .tabs .ico';
const ICON_BTNS = 'button, label.filebtn, a.linkbtn';
const ICON_SKIP = '.rc, .tiles, .scenebtns, #scenetpl, #sceneicon, #steps .acts code, .item .ic0';
const ICON_KEYS = Object.keys(ICON).sort((a, b) => b.length - a.length);
UI.icon = (name, hue) => {
  const s = el('span', { className: 'ui-ic' + (hue != null ? ' hue' : '') });
  if (hue != null) s.style.setProperty('--h', hue);
  s.innerHTML = '<svg class="ic" aria-hidden="true"><use href="#i-' + name + '"/></svg>';
  return s;
};
function iconize(e) {
  if (e.closest(ICON_SKIP) || e.querySelector(':scope > .ui-ic')) return;
  const title = e.matches(ICON_TITLES);
  const w = document.createTreeWalker(e, NodeFilter.SHOW_TEXT);
  for (let t; (t = w.nextNode());) {
    const s = t.nodeValue.trimStart(); if (!s) continue;
    const k = ICON_KEYS.find((k) => s.startsWith(k)); if (!k) return;   // the first words do not start with a known emoji
    const tab = e.closest('.tabs [data-tab]'), [name, hue] = ICON[k];
    t.nodeValue = s.slice(k.length).replace(/^️/, '').trimStart();
    if (t.parentNode.classList.contains('ico') && !t.nodeValue) t.parentNode.replaceChildren(UI.icon(name, tab ? TAB_HUE[tab.dataset.tab] : null));
    else t.parentNode.insertBefore(UI.icon(name, title ? hue : null), t);
    return;
  }
}
function iconizeIn(n) {
  if (n.nodeType === 3) n = n.parentElement;
  if (!n || n.nodeType !== 1) return;
  const own = n.closest(ICON_TITLES + ', ' + ICON_BTNS); if (own) iconize(own);
  n.querySelectorAll(ICON_TITLES + ', ' + ICON_BTNS).forEach(iconize);
}
// after i18n.js (loaded first, so its observer runs first): it translates with the emoji still there
iconizeIn(document.body);
new MutationObserver((list) => { for (const m of list) m.type === 'characterData' ? iconizeIn(m.target) : m.addedNodes.forEach(iconizeIn); })
  .observe(document.body, { subtree: true, childList: true, characterData: true });
