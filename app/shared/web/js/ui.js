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
