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
  { host: 'security.cloudflare-dns.com', brand: 'Cloudflare', kind: 'Chặn mã độc', ips: ['1.1.1.2', '1.0.0.2'] },
  { host: 'family.cloudflare-dns.com', brand: 'Cloudflare', kind: 'Gia đình', ips: ['1.1.1.3', '1.0.0.3'] },
  { host: 'dns.quad9.net', brand: 'Quad9', kind: 'Chặn mã độc', ips: ['9.9.9.9', '149.112.112.112'] },
  { host: 'dns.adguard-dns.com', brand: 'AdGuard', kind: 'Chặn quảng cáo', ips: ['94.140.14.14', '94.140.15.15'] },
  { host: 'family.adguard-dns.com', brand: 'AdGuard', kind: 'Gia đình', ips: ['94.140.14.15', '94.140.15.16'] },
  { host: 'adblock.dns.mullvad.net', brand: 'Mullvad', kind: 'Chặn quảng cáo', ips: [] },
  { host: 'family.dns.mullvad.net', brand: 'Mullvad', kind: 'Gia đình', ips: [] },
  { host: 'p2.freedns.controld.com', brand: 'Control D', kind: 'Chặn quảng cáo', ips: [] },
  { host: 'dns.nextdns.io', brand: 'NextDNS', ips: [] },
];
const DNS_BY_IP = Object.fromEntries(DNS_LIST.flatMap((d) => d.ips.map((ip) => [ip, d.host])));
const dnsBrand = (host) => (DNS_LIST.find((d) => d.host === host) || {}).brand;
const dnsLabel = (d) => d.brand + (d.kind ? ' · ' + tr(d.kind) : '') + ' — ' + d.host + (d.ips.length ? ' (' + d.ips[0] + ')' : '');
const dnsWho = (hosts) => hosts.map(dnsBrand).join(', ');
/** What was typed as the host name to set, or null after saying why it cannot be one. */
function dnsHost(typed) {
  const words = typed.toLowerCase().replace(/^\w+:\/\//, '').split(/[\s,;/]+/).filter(Boolean);   // "8.8.8.8 / 8.8.4.4", a pasted URL
  const ip = words.find((w) => DNS_BY_IP[w]);
  if (ip) return DNS_BY_IP[ip];
  if (words.some((w) => /^[\d.]+$|:/.test(w))) { toast('TV chỉ nhận tên máy chủ DNS, không nhận địa chỉ IP: hãy nhập tên, vd dns.google'); return null; }
  const h = words[0] || '';
  if (!/^[a-z0-9-]+(\.[a-z0-9-]+)+$/.test(h)) { toast('Tên máy chủ chưa đúng, vd dns.google'); return null; }
  return h;
}
const MIN = 60000;
const fmtWait = (ms) => ms % (60 * MIN) === 0 ? ms / 60 / MIN + ' giờ' : Math.round(ms / MIN) + ' phút';
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
  const dnsNote = el('p', { className: 'muted', textContent: t.sdk && t.sdk < 28 ? 'TV Android 8 trở xuống chưa có DNS riêng.'
    : (dnsOn ? 'Đang dùng ' + t.dnsHost + (dnsBrand(t.dnsHost) ? ' (' + dnsBrand(t.dnsHost) + ')' : '') + '. ' : '') + 'Áp dụng cho mọi app trên TV. Không chặn được quảng cáo trong YouTube.' });
  const dnsRow = row('🛡️ Lọc DNS', [
    choice('Không dùng', kind === 'off', () => setDns([''])),
    Object.assign(choice('Chặn quảng cáo', kind === 'ads', () => setDns(DNS_SETS.ads)), { title: dnsWho(DNS_SETS.ads) }),
    Object.assign(choice('Gia đình', kind === 'family', () => setDns(DNS_SETS.family)), { title: dnsWho(DNS_SETS.family) }),
    choice(kind === 'own' ? t.dnsHost : 'Tự nhập...', kind === 'own', async () => {
      const typed = await ask('Máy chủ DNS riêng, vd dns.google hoặc 8.8.8.8', { value: kind === 'own' ? t.dnsHost : '',
        choices: DNS_LIST.map((d) => ({ value: d.host, label: dnsLabel(d) })) });
      const h = typed && typed.trim() && dnsHost(typed);
      if (h) setDns([h]);
    }),
  ]);
  dnsRow.append(dnsNote);
  const off = t.dnsOff && new Date(t.dnsOff.at), p2 = (n) => String(n).padStart(2, '0');
  if (!dnsOn && off && Date.now() - t.dnsOff.at < 7 * 86400000) dnsRow.append(el('p', { className: 'warnline',
    textContent: '⚠️ TV đã tự tắt lọc DNS ' + t.dnsOff.host + ' lúc ' + p2(off.getHours()) + ':' + p2(off.getMinutes()) + ' ' + p2(off.getDate()) + '/' + p2(off.getMonth() + 1) + ', vì với nó TV không tra được tên miền nào (YouTube và các app báo mất mạng). Hay gặp khi TV có VPN như Tailscale. Chọn lại một bộ lọc để thử lại.' }));
  if (t.sdk && t.sdk < 28) dnsRow.querySelectorAll('button').forEach((b) => { b.disabled = true; });
  rows.push(dnsRow);

  const sleeps = [[-1, 'Không tự tắt'], [30 * MIN, '30 phút'], [60 * MIN, '1 giờ'], [240 * MIN, '4 giờ'], [720 * MIN, '12 giờ'], [1440 * MIN, '24 giờ']];
  const ownSleep = t.sleep !== null && !sleeps.some(([v]) => v === t.sleep);
  rows.push(row('💤 Tự tắt khi không dùng', [...sleeps.map(([v, label]) => choice(label, t.sleep === v, () => setTv('sleep', v, 'Tự tắt: ' + label))),
    choice(ownSleep ? fmtWait(t.sleep) : 'Tự nhập...', ownSleep, async () => {
      const v = await askWait(ownSleep ? t.sleep : null);
      if (v) setTv('sleep', v, 'Tự tắt: ' + fmtWait(v));
    })]));

  // the screensaver comes on after this long without a key; "never" is how some TVs (Sony) say off
  const saverOn = t.saver && t.saverAfter !== null && t.saverAfter < 86400000;
  const savers = [['off', 'Không dùng'], [5 * MIN, '5 phút'], [15 * MIN, '15 phút'], [30 * MIN, '30 phút']];
  if (saverOn && !savers.some(([v]) => v === t.saverAfter)) savers.push([t.saverAfter, fmtWait(t.saverAfter)]);
  rows.push(row('🖼️ Màn hình chờ', savers.map(([v, label]) => choice(label, v === 'off' ? !saverOn : saverOn && t.saverAfter === v, () => setTv('saver', v, 'Màn hình chờ: ' + label)))));

  const sizes = densityChoices(t.physical);
  if (sizes.length) rows.push(row('🔠 Cỡ giao diện', sizes.map(([label, v]) => choice(label, v === 'reset' ? !t.override || t.override === t.physical : t.override === v,
    () => setTv('density', v, 'Cỡ giao diện: ' + label + '. App đang mở cần mở lại mới đổi theo.'))), 'Chữ và hình trên TV to hay nhỏ hơn. Vừa là cỡ gốc của TV.'));

  if (t.launchers.length) {
    const homes = t.launchers.map((c) => {
      const pkg = c.split('/')[0], b = choice(appName(pkg), c === t.home, () => setTv('home', c, 'Màn hình chính: ' + appName(pkg)));
      onMeta(pkg, (m) => { if (m.label) b.textContent = m.label; });
      return b;
    });
    rows.push(row('🏠 Màn hình chính', homes, t.launchers.length < 2 ? 'TV chỉ có một màn hình chính. Cài thêm FLauncher hay Projectivy (tab Tệp → cài APK) để có lựa chọn khác.' : 'Màn hình hiện ra khi bấm nút Home.'));
  }
  $('#tvset').replaceChildren(...rows);
}

async function askWait(now) {
  const m = await askTime('Tự tắt sau bao lâu không dùng?', now ? now / MIN : 0);
  return m ? m * MIN : null;
}

async function setTv(key, value, done) {
  const r = await api('/api/tvset', { key, value });
  if (!r) return;
  const out = r.text.trim();
  if (key === 'home' && !out.endsWith(value)) toast('TV không đổi màn hình chính: ' + (out.replace(/^home\s*/, '') || 'không rõ lý do'));
  else if (/^ok$/m.test(out) || key === 'home') toast(done);
  else toast('Lỗi: ' + friendly(out || 'không đổi được'));
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
      if (h) note.textContent = 'TV đang thử ' + h + ', tối đa 30 giây...';
      const r = await call('/api/tvset', { key: 'dns', value: h });
      if (!r) break;
      if (/^ok$/m.test(r.text)) { ok = h || 'off'; break; }
    }
  } finally { tvsetBusy = false; }
  toast(ok === 'off' ? 'Đã tắt lọc DNS' : ok ? 'Đã bật lọc DNS: ' + ok : 'DNS này không dùng được trên mạng của TV, đã giữ cài đặt cũ.');
  loadTvset();
}

// 🔁 Restart: the page waits for the TV to go down and come back, then reconnects. The TV's ADB comes back by itself on the
// TVs this works with (the vendor's "ADB debugging" switch survives a restart); after 3 minutes the page says what to check.
let rebootT0 = 0;
$('#tvreboot').addEventListener('click', async () => {
  if (!await ask('Khởi động lại TV? Mất khoảng 1–2 phút; trang tự kết nối lại khi TV lên.', { ok: 'Khởi động lại' })) return;
  if (!await api('/api/reboot', {})) return;
  rebootT0 = Date.now();
  put('#reboottitle', 'textContent', 'TV đang khởi động lại'); put('#rebootspin', 'hidden', false); $('#rebootdlg').showModal();
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
      rebootT0 = 0; $('#rebootdlg').close(); toast('TV đã khởi động lại xong'); bannerKey = ''; refresh(); loadTvset(); return;
    }
    const clock = Math.floor(secs / 60) + ':' + String(secs % 60).padStart(2, '0');
    put('#rebootstep', 'textContent', secs < 180 ? (down ? 'TV đang khởi động' : 'TV đang tắt') + ' · ' + clock
      : clock + ' · ' + (CORE === 'tv' ? 'TV vẫn chưa trả lời. Nếu TV đã lên màn hình chính: mở app TV Setup trên TV.'
        : 'TV vẫn chưa mở lại ADB. Nếu TV đã lên màn hình chính: vào Cài đặt → Tùy chọn nhà phát triển, bật lại “Gỡ lỗi ADB”.'));
  }
}
