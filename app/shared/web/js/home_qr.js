// 🏠 TV Home's QR widgets (HomeConfig "qrs", HomeQr.kt): a template is a small form that builds the text of a code (a Wi-Fi, a link, a
// number …), "Text" is the form with nothing built, the raw payload. The person's fields are kept, so a widget opens again to be
// changed; the TV builds the payload itself from them. homeQrPayload is the rule HomeQr.payload has (tests/home_qr_cases.json holds
// the cases both are tested with).
const HOME_QR_TEMPLATES = ['wifi', 'link', 'text', 'tel', 'sms', 'mail', 'contact'];   // HomeQr.TEMPLATES, in the order offered
const HOME_QR_KEYS = ['qr1', 'qr2', 'qr3', 'qr4'];                    // HomeQr.KEYS: each widget is a "widgets" key of its own
const HOME_QR_MAX = 271, HOME_QR_CAP_MAX = 60, HOME_QR_FIELD_MAX = 250;   // HomeQr.MAX_BYTES / CAP_MAX / FIELD_MAX
const HOME_QR_WORDS = { wifi: 'home.qr_t_wifi', link: 'home.qr_t_link', text: 'home.qr_t_text', tel: 'home.qr_t_tel', sms: 'home.qr_t_sms', mail: 'home.qr_t_mail', contact: 'home.qr_t_contact' };
const HOME_QR_FORM = { wifi: ['ssid', 'pass'], link: ['url'], text: ['text'], tel: ['num'], sms: ['num', 'body'], mail: ['to', 'subject', 'body'], contact: ['name', 'tel', 'email'] };   // the typed fields (the rest of a template's are chips and a switch)
const HOME_QR_FIELDS = { wifi: ['ssid', 'pass', 'sec', 'hidden'], link: ['url'], text: ['text'], tel: ['num'], sms: ['num', 'body'], mail: ['to', 'subject', 'body'], contact: ['name', 'tel', 'email'] };   // HomeQr.FIELDS
const HOME_QR_FIELD_WORDS = { ssid: 'home.qr_f_ssid', pass: 'home.qr_f_pass', url: 'home.qr_f_url', text: 'home.qr_f_text', num: 'home.qr_f_num', body: 'home.qr_f_body', to: 'home.qr_f_to',
  subject: 'home.qr_f_subject', name: 'home.qr_f_name', tel: 'home.qr_f_tel', email: 'home.qr_f_email', sec: 'home.qr_f_sec' };
const HOME_QR_SEC = [['WPA', 'home.qr_sec_wpa'], ['WEP', 'home.qr_sec_wep'], ['nopass', 'home.qr_sec_none']];   // HomeQr.SECURITY

const HOME_QR_SCHEME = /^(?:[A-Za-z][A-Za-z0-9+.-]*:\/\/|(?:mailto|tel|sms|smsto|geo|whatsapp|tg|viber|zalo):)/i;   // HomeQr.SCHEME: an address that already says what it is
const homeQrEsc = (s) => String(s).replace(/([\\;,:"])/g, '\\$1');
// percent-encoded UTF-8 keeping only the letters, digits and - _ . ~ (encodeURIComponent keeps ! ' ( ) * too; HomeQr.pct does not)
const homeQrPct = (s) => [...new TextEncoder().encode(String(s))].map((c) => (c >= 48 && c <= 57) || (c >= 65 && c <= 90) || (c >= 97 && c <= 122) || c === 45 || c === 95 || c === 46 || c === 126
  ? String.fromCharCode(c) : '%' + c.toString(16).toUpperCase().padStart(2, '0')).join('');

// The text a code holds; '' while the template lacks what it cannot do without
function homeQrPayload(tpl, f) {
  const g = (k) => (f && f[k] != null ? String(f[k]) : '');
  const num = (s) => s.replace(/[^0-9+*#]/g, '');
  switch (tpl) {
    case 'wifi': {
      const ssid = g('ssid'), pass = g('pass'), s = g('sec');
      const sec = s === 'nopass' || !pass ? 'nopass' : s === 'WEP' ? 'WEP' : 'WPA';   // no password = an open network, whatever was chosen
      return !ssid ? '' : 'WIFI:T:' + sec + ';S:' + homeQrEsc(ssid) + ';' + (sec === 'nopass' ? '' : 'P:' + homeQrEsc(pass) + ';') + (f.hidden ? 'H:true;' : '') + ';';
    }
    case 'link': { const u = g('url').trim(); return !u ? '' : HOME_QR_SCHEME.test(u) ? u : 'https://' + u; }
    case 'text': return g('text');
    case 'tel': { const n = num(g('num')); return n ? 'tel:' + n : ''; }
    case 'sms': { const n = num(g('num')), b = g('body'); return n ? 'sms:' + n + (b ? '?body=' + homeQrPct(b) : '') : ''; }
    case 'mail': {
      const to = g('to').trim(), q = [['subject', g('subject')], ['body', g('body')]].filter((p) => p[1]).map((p) => p[0] + '=' + homeQrPct(p[1])).join('&');
      return to ? 'mailto:' + to + (q ? '?' + q : '') : '';
    }
    case 'contact': {
      const name = g('name'), tel = g('tel'), mail = g('email');
      return !name && !tel && !mail ? '' : 'MECARD:' + (name ? 'N:' + homeQrEsc(name) + ';' : '') + (tel ? 'TEL:' + homeQrEsc(tel) + ';' : '') + (mail ? 'EMAIL:' + homeQrEsc(mail) + ';' : '') + ';';
    }
    default: return '';
  }
}
const homeQrBytes = (p) => new TextEncoder().encode(p).length;
// a widget the TV keeps: only the template's own fields, the caption trimmed
function homeQrCard(tpl, f, cap, show) {
  const out = {};
  for (const k of HOME_QR_FIELDS[tpl]) out[k] = k === 'hidden' ? !!f[k] : k === 'sec' ? (HOME_QR_SEC.some((s) => s[0] === f[k]) ? f[k] : 'WPA') : String(f[k] || '').slice(0, tpl === 'text' ? HOME_QR_MAX : HOME_QR_FIELD_MAX);
  return { tpl, f: out, cap: String(cap || '').replace(/\s+/g, ' ').trim().slice(0, HOME_QR_CAP_MAX), show: tpl === 'wifi' && !!show };
}
// the widget's name in a list: its caption, else what it is
function homeQrName(c) {
  const f = c.f || {}, what = c.tpl === 'wifi' ? f.ssid : c.tpl === 'link' ? f.url : c.tpl === 'text' ? f.text : '';
  return 'QR · ' + (c.cap || (T(HOME_QR_WORDS[c.tpl]) + (what ? ': ' + String(what).slice(0, 40) : '')));
}

// The form for one widget, filled with [qr] (null: a new one); resolves with the widget to keep, or null when cancelled.
// A live picture of the code beside it, black on white as the TV will draw it.
function homeQrAsk(qr) {
  return new Promise((ok) => {
    let tpl = qr ? qr.tpl : 'wifi', done = null;
    const vals = Object.assign({ sec: 'WPA' }, qr ? qr.f : {}), cap = el('input', { type: 'text', maxLength: HOME_QR_CAP_MAX, placeholder: T('home.qr_cap_ph'), 'aria-label': T('home.qr_cap'), value: qr ? qr.cap : '' });
    const showBox = el('input', { type: 'checkbox', checked: !!(qr && qr.show), onchange: () => redraw() });
    const hiddenBox = el('input', { type: 'checkbox', checked: !!vals.hidden, onchange: () => { vals.hidden = hiddenBox.checked; redraw(); } });
    const tplChips = el('div', { className: 'fchips homeqr-tpl', role: 'group', 'aria-label': T('home.qr_tpl') });
    const form = el('div', { className: 'homeqr-form' }), view = el('div', { className: 'homeqr-view' }), info = el('p', { className: 'note' });
    const save = el('button', { type: 'button', className: 'primary', textContent: '💾 ' + T('home.qr_save'), onclick: () => { done = homeQrCard(tpl, vals, cap.value, showBox.checked); d.close(); } });
    const field = (k) => {
      const area = k === 'text' || k === 'body', input = el(area ? 'textarea' : 'input', { value: vals[k] || '', rows: 3, maxLength: HOME_QR_FIELD_MAX, autocomplete: 'off', autocapitalize: 'off', spellcheck: false,
        inputMode: k === 'num' || (k === 'tel') ? 'tel' : k === 'to' || k === 'email' ? 'email' : k === 'url' ? 'url' : 'text', 'aria-label': T(HOME_QR_FIELD_WORDS[k]) });
      if (!area) input.type = 'text';
      input.addEventListener('input', () => { vals[k] = input.value; redraw(); });
      return el('label', { className: 'homeqr-field' }, el('span', { textContent: T(HOME_QR_FIELD_WORDS[k]) }), input);
    };
    const build = () => {
      tplChips.replaceChildren(...HOME_QR_TEMPLATES.map((t) => el('button', { type: 'button', className: t === tpl ? 'on' : '', 'aria-pressed': String(t === tpl), textContent: T(HOME_QR_WORDS[t]), onclick: () => { tpl = t; build(); redraw(); } })));
      const kids = HOME_QR_FORM[tpl].map(field);
      if (tpl === 'wifi') {
        kids.push(el('div', { className: 'fchips homeqr-sec', role: 'group', 'aria-label': T(HOME_QR_FIELD_WORDS.sec) }, ...HOME_QR_SEC.map(([v, w]) => el('button', { type: 'button', className: vals.sec === v ? 'on' : '', 'aria-pressed': String(vals.sec === v),
          textContent: T(w), onclick: () => { vals.sec = v; build(); redraw(); } }))));
        kids.push(el('label', { className: 'check' }, hiddenBox, el('span', { textContent: T('home.qr_hidden') })));
        kids.push(el('label', { className: 'check' }, showBox, el('span', { textContent: T('home.qr_show_pass') })));
      }
      form.replaceChildren(...kids);
    };
    const redraw = () => {
      const p = homeQrPayload(tpl, vals), n = homeQrBytes(p), svg = p && n <= HOME_QR_MAX ? qrSvg(p, 200) : null;
      view.replaceChildren(...(svg ? [svg] : []));
      view.hidden = !svg;
      info.textContent = !p ? T('home.qr_empty') : n > HOME_QR_MAX ? T('home.qr_long', { n, max: HOME_QR_MAX }) : T('home.qr_size', { n, max: HOME_QR_MAX });
      save.disabled = !svg;
    };
    const d = el('dialog', { className: 'ask homeask homeqr' }, el('div', { className: 'card' },
      el('h2', { textContent: T('home.qr_title') }), tplChips, form, el('label', { className: 'homeqr-field' }, el('span', { textContent: T('home.qr_cap') }), cap), view, info,
      el('div', { className: 'actions' }, el('button', { type: 'button', textContent: T('common.cancel'), onclick: () => d.close() }), save)));
    d.addEventListener('close', () => { d.remove(); ok(done); });
    build(); redraw();
    document.body.append(d); d.showModal();
  });
}

if (typeof module !== 'undefined') module.exports = { homeQrPayload, homeQrCard };
