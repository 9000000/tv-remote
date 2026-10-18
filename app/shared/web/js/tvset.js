// 📺 the TV card on the Setup tab: restart, DNS filter, sleep, screensaver, size and home screen. Read back from the TV after
// every change, so the choice that is lit is what the TV really has. The commands are in TvShell.kt.

// Each filter is tried in turn until one answers on this network: a DNS that does not leaves the TV with no Internet, so the
// TV checks, and puts the old setting back by itself when names stop resolving.
const DNS_SETS = {
  ads: ['dns.adguard-dns.com', 'adblock.dns.mullvad.net', 'p2.freedns.controld.com'],
  family: ['family.adguard-dns.com', 'family.dns.mullvad.net', 'family.cloudflare-dns.com'],
};
// The well-known ones, offered by name in "Tự nhập...". Android's private DNS takes a host name only (DNS-over-TLS): the
// addresses people know (8.8.8.8) are turned into their names.
const DNS_LIST = [
  { host: 'dns.google', brand: 'Google', ips: ['8.8.8.8', '8.8.4.4'] },
  { host: 'one.one.one.one', brand: 'Cloudflare', ips: ['1.1.1.1', '1.0.0.1'] },
  { host: 'security.cloudflare-dns.com', brand: 'Cloudflare', kind: T('tvset.dns_malware'), ips: ['1.1.1.2', '1.0.0.2'] },
  { host: 'family.cloudflare-dns.com', brand: 'Cloudflare', kind: T('tvset.family'), ips: ['1.1.1.3', '1.0.0.3'] },
  { host: 'dns.quad9.net', brand: 'Quad9', kind: T('tvset.dns_malware'), ips: ['9.9.9.9', '149.112.112.112'] },
  { host: 'dns.adguard-dns.com', brand: 'AdGuard', kind: T('tvset.dns_ads'), ips: ['94.140.14.14', '94.140.15.15'] },
  { host: 'family.adguard-dns.com', brand: 'AdGuard', kind: T('tvset.family'), ips: ['94.140.14.15', '94.140.15.16'] },
  { host: 'adblock.dns.mullvad.net', brand: 'Mullvad', kind: T('tvset.dns_ads'), ips: [] },
  { host: 'family.dns.mullvad.net', brand: 'Mullvad', kind: T('tvset.family'), ips: [] },
  { host: 'p2.freedns.controld.com', brand: 'Control D', kind: T('tvset.dns_ads'), ips: [] },
  { host: 'dns.nextdns.io', brand: 'NextDNS', ips: [] },
];
const DNS_BY_IP = Object.fromEntries(DNS_LIST.flatMap((d) => d.ips.map((ip) => [ip, d.host])));
const dnsBrand = (host) => (DNS_LIST.find((d) => d.host === host) || {}).brand;
const dnsLabel = (d) => d.brand + (d.kind ? ' · ' + d.kind : '') + ' — ' + d.host + (d.ips.length ? ' (' + d.ips[0] + ')' : '');
const dnsWho = (hosts) => hosts.map(dnsBrand).join(', ');
/** What was typed as the host name to set, or null after saying why it cannot be one. */
function dnsHost(typed) {
  const words = typed.toLowerCase().replace(/^\w+:\/\//, '').split(/[\s,;/]+/).filter(Boolean);   // "8.8.8.8 / 8.8.4.4", a pasted URL
  const ip = words.find((w) => DNS_BY_IP[w]);
  if (ip) return DNS_BY_IP[ip];
  if (words.some((w) => /^[\d.]+$|:/.test(w))) { toast(T('tvset.dns_name_only')); return null; }
  const h = words[0] || '';
  if (!/^[a-z0-9-]+(\.[a-z0-9-]+)+$/.test(h)) { toast(T('tvset.dns_bad_name')); return null; }
  return h;
}
const MIN = 60000;
const fmtWait = (ms) => ms % (60 * MIN) === 0 ? T('tvset.hours', { n: ms / 60 / MIN }) : T('tvset.minutes', { n: Math.round(ms / MIN) });
let tvsetBusy = false;

async function loadTvset() {
  if (ADB.state !== 'ready' || tvsetBusy) return;
  const r = await api('/api/tvset', null, true);
  if (r) drawTvset(readTvset(r.text));
}

function drawTvset(t) {
  const choice = (label, on, fn) => el('button', { className: on ? 'on' : '', textContent: label, onclick: fn });
  const row = (title, choices, note) => el('div', { className: 'setrow' }, el('b', { textContent: title }), el('div', { className: 'places' }, ...choices),
    note ? el('p', { className: 'muted', textContent: note }) : null);
  const rows = [];

  const dnsOn = t.dnsMode === 'hostname' && t.dnsHost;
  const kind = !dnsOn ? 'off' : DNS_SETS.ads.includes(t.dnsHost) ? 'ads' : DNS_SETS.family.includes(t.dnsHost) ? 'family' : 'own';
  const dnsNote = el('p', { className: 'muted', textContent: t.sdk && t.sdk < 28 ? T('tvset.dns_old_android')
    : (dnsOn ? T('tvset.dns_using', { host: t.dnsHost + (dnsBrand(t.dnsHost) ? ' (' + dnsBrand(t.dnsHost) + ')' : '') }) + ' ' : '') + T('tvset.dns_applies') });
  const dnsRow = row(T('tvset.dns_filter'), [
    choice(T('tvset.none'), kind === 'off', () => setDns([''])),
    Object.assign(choice(T('tvset.dns_ads'), kind === 'ads', () => setDns(DNS_SETS.ads)), { title: dnsWho(DNS_SETS.ads) }),
    Object.assign(choice(T('tvset.family'), kind === 'family', () => setDns(DNS_SETS.family)), { title: dnsWho(DNS_SETS.family) }),
    choice(kind === 'own' ? t.dnsHost : T('tvset.type_own'), kind === 'own', async () => {
      const typed = await ask(T('tvset.dns_own_ask'), { value: kind === 'own' ? t.dnsHost : '',
        choices: DNS_LIST.map((d) => ({ value: d.host, label: dnsLabel(d) })) });
      const h = typed && typed.trim() && dnsHost(typed);
      if (h) setDns([h]);
    }),
  ]);
  dnsRow.append(dnsNote);
  const off = t.dnsOff && new Date(t.dnsOff.at), p2 = (n) => String(n).padStart(2, '0');
  if (!dnsOn && off && Date.now() - t.dnsOff.at < 7 * 86400000) dnsRow.append(el('p', { className: 'warnline',
    textContent: T('tvset.dns_turned_off', { host: t.dnsOff.host, time: p2(off.getHours()) + ':' + p2(off.getMinutes()), date: p2(off.getDate()) + '/' + p2(off.getMonth() + 1) }) }));
  if (t.sdk && t.sdk < 28) dnsRow.querySelectorAll('button').forEach((b) => { b.disabled = true; });
  rows.push(dnsRow);

  const sleeps = [[-1, T('tvset.sleep_never')], [30 * MIN, fmtWait(30 * MIN)], [60 * MIN, fmtWait(60 * MIN)], [240 * MIN, fmtWait(240 * MIN)], [720 * MIN, fmtWait(720 * MIN)], [1440 * MIN, fmtWait(1440 * MIN)]];
  const ownSleep = t.sleep !== null && !sleeps.some(([v]) => v === t.sleep);
  rows.push(row(T('tvset.sleep_title'), [...sleeps.map(([v, label]) => choice(label, t.sleep === v, () => setTv('sleep', v, T('tvset.sleep_set', { label })))),
    choice(ownSleep ? fmtWait(t.sleep) : T('tvset.type_own'), ownSleep, async () => {
      const v = await askWait(ownSleep ? t.sleep : null);
      if (v) setTv('sleep', v, T('tvset.sleep_set', { label: fmtWait(v) }));
    })]));

  // the screensaver comes on after this long without a key; "never" is how some TVs (Sony) say off
  const saverOn = t.saver && t.saverAfter !== null && t.saverAfter < 86400000;
  const savers = [['off', T('tvset.none')], [5 * MIN, fmtWait(5 * MIN)], [15 * MIN, fmtWait(15 * MIN)], [30 * MIN, fmtWait(30 * MIN)]];
  if (saverOn && !savers.some(([v]) => v === t.saverAfter)) savers.push([t.saverAfter, fmtWait(t.saverAfter)]);
  rows.push(row(T('tvset.saver_title'), savers.map(([v, label]) => choice(label, v === 'off' ? !saverOn : saverOn && t.saverAfter === v, () => setTv('saver', v, T('tvset.saver_set', { label }))))));

  const sizes = densityChoices(t.physical);
  if (sizes.length) rows.push(row(T('tvset.size_title'), sizes.map(([label, v]) => choice(label, v === 'reset' ? !t.override || t.override === t.physical : t.override === v,
    () => setTv('density', v, T('tvset.size_set', { label })))), T('tvset.size_note')));

  if (t.launchers.length) {
    const homes = t.launchers.map((c) => {
      const pkg = c.split('/')[0], b = choice(appName(pkg), c === t.home, () => setTv('home', c, T('tvset.home_set', { name: appName(pkg) })));
      onMeta(pkg, (m) => { if (m.label) b.textContent = m.label; });
      return b;
    });
    rows.push(row(T('tvset.home_title'), homes, t.launchers.length < 2 ? T('tvset.home_one') : T('tvset.home_many')));
  }
  $('#tvset').replaceChildren(...rows);
}

async function askWait(now) {
  const m = await askTime(T('tvset.sleep_after_ask'), now ? now / MIN : 0);
  return m ? m * MIN : null;
}

async function setTv(key, value, done) {
  const r = await api('/api/tvset', { key, value });
  if (!r) return;
  const out = r.text.trim();
  if (key === 'home' && !out.endsWith(value)) toast(T('tvset.home_unchanged', { why: out.replace(/^home\s*/, '') || T('tvset.no_reason') }));
  else if (/^ok$/m.test(out) || key === 'home') toast(done);
  else toast(T('tvset.error_msg', { msg: friendly(out || 'không đổi được') }));
  loadTvset();
}

// One host after another until the TV can look up names through it; the buttons wait meanwhile (up to 30 s a host).
async function setDns(hosts) {
  const btns = [...$('#tvset').querySelectorAll('.setrow:first-child button')];
  tvsetBusy = true; btns.forEach((b) => { b.disabled = true; });
  const note = $('#tvset').querySelector('.setrow:first-child .muted');
  let ok = '';
  try {
    for (const h of hosts) {
      if (h) note.textContent = T('tvset.dns_trying', { host: h });
      const r = await call('/api/tvset', { key: 'dns', value: h });
      if (!r) break;
      if (/^ok$/m.test(r.text)) { ok = h || 'off'; break; }
    }
  } finally { tvsetBusy = false; }
  toast(ok === 'off' ? T('tvset.dns_off') : ok ? T('tvset.dns_on', { host: ok }) : T('tvset.dns_failed'));
  loadTvset();
}

// 🔁 Restart: the page waits for the TV to go down and come back, then reconnects. The TV's ADB comes back by itself on the
// TVs this works with (the vendor's "ADB debugging" switch survives a restart); after 3 minutes the page says what to check.
let rebootT0 = 0;
$('#tvreboot').addEventListener('click', async () => {
  if (!await ask(T('tvset.restart_ask'), { ok: T('tvset.restart_ok') })) return;
  if (!await api('/api/reboot', {})) return;
  rebootT0 = Date.now();
  put('#reboottitle', 'textContent', T('rebootdlg.the_tv_is_restarting')); put('#rebootspin', 'hidden', false); $('#rebootdlg').showModal();
  watchReboot(rebootT0);
});
$('#rebootclose').addEventListener('click', () => $('#rebootdlg').close());
async function watchReboot(t0) {
  let down = false;
  while (rebootT0 === t0 && Date.now() - t0 < 10 * 60000) {             // 10 minutes: by then the note says what to do
    await sleep(3000);
    const s = await fetch('/api/state').then((r) => r.ok ? r.json() : null).catch(() => null);
    const up = !!(s && s.adb && s.adb.state === 'ready'), secs = Math.round((Date.now() - t0) / 1000);
    if (!up) down = true;
    // ADB answers early in the boot: done once the TV's settings answer too (or it never went down: nothing to wait for)
    const booted = up && (down || secs > 60) && /^sleep -?\d/m.test((await fetch('/api/tvset').then((r) => r.ok ? r.json() : {}).catch(() => ({}))).text || '');
    if (booted) {
      rebootT0 = 0; $('#rebootdlg').close(); toast(T('tvset.restarted')); bannerAgain(); refresh(); loadTvset(); return;
    }
    const clock = Math.floor(secs / 60) + ':' + String(secs % 60).padStart(2, '0');
    put('#rebootstep', 'textContent', secs < 180 ? (down ? T('tvset.booting', { clock }) : T('tvset.shutting_down', { clock }))
      : (CORE === 'tv' ? T('tvset.slow_tv', { clock }) : T('tvset.slow_adb', { clock })));
  }
}
