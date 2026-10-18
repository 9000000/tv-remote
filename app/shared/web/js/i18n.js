// The page's words, in the language the viewer picked. Each is an id in i18n/<lang>.json: vi.json is the source and lists every
// one, en.json and any other language the same ids (missing ones fall back to English, then Vietnamese). In index.html an
// element names its id (data-i18n, data-i18n-html for words with <b> or <a> in them, data-i18n-title / -aria-label /
// -placeholder / -alt) and keeps its Vietnamese words, so nothing flashes; the scripts ask T('id', { n: 3 }).
// A new language: i18n/<code>.json and its line in LANGS. tests/i18n.test.js keeps the files and the page in step.
// Words not moved to an id yet still go through EN and RULES below (tr()), on the rendered page; that list only shrinks.
const LANGS = { vi: 'Tiếng Việt', en: 'English' };
(() => {
  let stored = null;
  try { stored = localStorage.getItem('lang'); } catch {}
  let asked = null;                                                  // the phone app says which language its own screens use
  try { asked = new URLSearchParams(location.search).get('lang'); } catch {}
  const own = ((navigator.language || 'vi').toLowerCase().split('-')[0]);
  const LANG = LANGS[asked] ? asked : LANGS[stored] ? stored : LANGS[own] ? own : 'en';
  window.LANG = LANG;
  // The page reloads to switch language, so where the viewer is (tab, whether the TV list was open, scroll) is handed over once
  // through sessionStorage: without it the computer app opened on the TV list again, kicking the viewer off the TV.
  window.LANG_KEEP = null;
  try { window.LANG_KEEP = JSON.parse(sessionStorage.getItem('langKeep')); sessionStorage.removeItem('langKeep'); } catch {}
  window.setLang = (l) => {
    try { localStorage.setItem('lang', l); } catch {}
    try { sessionStorage.setItem('langKeep', JSON.stringify({ tab: typeof currentTab === 'string' ? currentTab : '', picking: typeof picking !== 'undefined' && picking, settings: !!document.querySelector('#about[open]') })); } catch {}
    location.reload();
  };
  document.documentElement.lang = LANG;
  try { document.cookie = 'tvr-lang=' + LANG + '; path=/; max-age=31536000; SameSite=Strict'; } catch {}   // the server's words come in it (Words.kt)

  // read at once, as remote.json is: the page is drawn in its words from the first frame. ?v= of this script: an update brings new words
  const v = (() => { try { return new URL(document.currentScript.src).search; } catch { return ''; } })();
  const load = (l) => {
    try { const x = new XMLHttpRequest(); x.open('GET', 'i18n/' + l + '.json' + v, false); x.send(); return x.status === 200 ? JSON.parse(x.responseText) : {}; } catch { return {}; }
  };
  const books = [...new Set([LANG, 'en', 'vi'])].map(load);
  let rule = null;
  try { rule = new Intl.PluralRules(LANG); } catch {}
  /** The words of [id] in the viewer's language; {name} in them is args.name; a { one, other } entry is picked by args.n. */
  window.T = (id, args) => {
    let s;
    for (const b of books) if (Object.prototype.hasOwnProperty.call(b, id)) { s = b[id]; break; }
    if (s == null) return id;
    if (typeof s === 'object') s = s[rule && args && args.n != null ? rule.select(Number(args.n)) : 'other'] ?? s.other;
    return args ? s.replace(/\{(\w+)\}/g, (m, k) => (k in args ? String(args[k]) : m)) : s;
  };
  const SLOTS = ['title', 'aria-label', 'placeholder', 'alt'];
  /** Its own words: the last of its own lines of text (an icon or a field beside it stays). */
  const words = (e, s) => {
    let slot = null;
    for (const n of e.childNodes) if (n.nodeType === 3 && n.nodeValue.trim()) slot = n;
    if (!slot) { e.append(s); return; }
    const m = /^(\s*)[\s\S]*?(\s*)$/.exec(slot.nodeValue);
    slot.nodeValue = m[1] + s + m[2];
  };
  const done = new WeakSet();                                         // once each: a script may write new words and add it again
  const put = (e) => {
    if (done.has(e)) return;
    done.add(e);
    const id = e.getAttribute('data-i18n'); if (id) words(e, T(id));
    const h = e.getAttribute('data-i18n-html'); if (h) e.innerHTML = T(h);
    for (const a of SLOTS) { const k = e.getAttribute('data-i18n-' + a); if (k) e.setAttribute(a, T(k)); }
  };
  const SEL = '[data-i18n], [data-i18n-html], ' + SLOTS.map((a) => '[data-i18n-' + a + ']').join(', ');
  /** Puts the words of every element with an id under [root] (and root itself): the page at once, a template's copy as it is added. */
  window.i18nPut = (root) => { if (LANG === 'vi' || !root || root.nodeType !== 1) return; if (root.matches(SEL)) put(root); root.querySelectorAll(SEL).forEach(put); };
  if (typeof document !== 'undefined' && document.documentElement && document.documentElement.querySelectorAll) i18nPut(document.documentElement);

  if (LANG === 'vi') { window.tr = (s) => s; return; }

  const EN = {
    'Home Assistant · Phím tắt': 'Home Assistant · Shortcuts',
    // header, tabs
    'Tắt': 'Quit',
    'cài đặt': 'setup',
    // screen
    'Mặc định': 'Default',
    'Xem': 'Watch',
    // remote
    'Xuống': 'Down',
    'Thêm phím': 'More keys', 'Ngõ vào': 'Input',
    'còn trống': 'free',
    'Không': 'No',
    'Đang mở': 'Now open',
    'Dừng': 'Stop',
    'Nét nhất': 'Sharpest', 'CH Play': 'Play Store',
    'Mở': 'Open',
    // the TV card on the Setup tab
    'Tự nhập...': 'Custom...',
    'giờ': 'h',
    // files
    'Mở trên TV': 'Open on the TV',
    // Tailscale in one button
    '■ Dừng': '■ Stop', 'Đã dừng': 'Stopped',
    // ✏️ drawing on the TV's screen (draw.js)
    'Nét': 'Line size',
    // TV tab
    'đang dùng': 'in use',
    // automation, scenes, the TV's sound, voice commands, the home screen icon
    'Bật / tắt / gỡ hàng loạt': 'Turn on / off / remove in bulk',
    'đã tắt': 'disabled',
    'có': 'yes', 'không': 'no',
    'không có': 'nothing',
    'đang bật': 'on',
    'Tự động': 'Automation',
    'Cảnh': 'Scenes',
    'Thêm': 'Add',
    // the remote's "More keys" menus and the listening bar: the same words as the phone app's (Media.kt, Automation.kt, Listen.kt)
    'Hẹn': 'Set',
    'Đều': 'Even',
    'App': 'App',
    // inputs (remote.js inputsMenu, the scene step)
    'this TV did not take the switch': 'The TV did not switch input', 'this TV cannot switch inputs from here': 'This TV cannot switch inputs from here', 'bad input': 'Unknown input',
    'Cập nhật': 'Update',
    'giây': 's',
    'phút': 'min',
    'App hệ thống': 'System app',
    'dùng': 'used',
    // LG and Samsung TVs: no ADB, the TV itself asks whether to let this device in
    'Gửi nhật ký': 'Send the log',
    '10 giây': '10 s',
    'Gửi': 'Send',
  };

  // Strings with a variable part. Captured pieces are passed through tr() again where they can be text of ours.
  const RULES = [
    [/^📍 Dùng địa chỉ (.+)$/, (m) => '📍 Use the address ' + m[1]],
    [/^Mạng chậm: hạ hình xuống (.+) cho kịp\.$/, (m) => 'Slow network: the picture went down to ' + tr(m[1]) + ' to keep up.'],
    [/^Mạng ổn lại: tăng hình lên (.+)\.$/, (m) => 'Network is fine again: picture back up to ' + tr(m[1]) + '.'],
    [/^(.+) cần TV Setup (\S+) trở lên \(TV đang có (\S*)\)\. Cập nhật TV Setup\?$/, (m) => tr(m[1]) + ' needs TV Setup ' + m[2] + ' or newer (the TV has ' + m[3] + '). Update TV Setup?'],
    [/^TV Setup trên TV là bản cũ\. Cần bản (\S+) trở lên: mở TV Setup trên TV → Cập nhật, hoặc bấm nút dưới\.$/, (m) => 'TV Setup on the TV is too old. ' + m[1] + ' or newer is needed: open TV Setup on the TV → Update, or press the button below.'],
    [/^🆕 TV Setup trên TV có bản mới (\S+) \(đang dùng (\S+)\)$/, (m) => '🆕 TV Setup on the TV has a new version ' + m[1] + ' (it has ' + m[2] + ')'],
    [/^⚠️ TV đã tự tắt lọc DNS (\S+) lúc (\S+) (\S+), vì với nó TV không tra được tên miền nào \(YouTube và các app báo mất mạng\)\. Hay gặp khi TV có VPN như Tailscale\. Chọn lại một bộ lọc để thử lại\.$/,
      (m) => '⚠️ The TV turned the DNS filter ' + m[1] + ' off at ' + m[2] + ' on ' + m[3] + ': with it the TV could not look up any name (YouTube and other apps said there was no Internet). This happens on TVs with a VPN such as Tailscale. Pick a filter again to try once more.'],
    [/^Đang tải bản mới về TV (\d+)%\.\.\.$/, (m) => 'Downloading the new version to the TV ' + m[1] + '%...'],
    [/^Đã cập nhật TV Setup lên (\S+)$/, (m) => 'TV Setup updated to ' + m[1]],
    [/^Chưa cập nhật được: ([\s\S]*)$/, (m) => 'Could not update: ' + tr(m[1])],
    [/^Có, dùng (.+) RAM$/, (m) => 'Yes, using ' + m[1] + ' of RAM'],
    [/^· (\d+) TV$/, (m) => '· ' + m[1] + ' TVs'], [/^trống ([\d.,]+ \S*B \/ .+)$/, (m) => 'free ' + m[1]],
    [/^🆕 Có bản mới (\S+) \(đang dùng (\S+)\)$/, (m) => '🆕 New version ' + m[1] + ' (you have ' + m[2] + ')'], [/^Đang tải bản mới (\d+)%$/, (m) => 'Downloading the new version ' + m[1] + '%'],
    [/^Chưa tải được bản mới \((.*)\)\. Bấm Cập nhật để thử lại\.$/s, (m) => 'Could not download the new version (' + m[1] + '). Press Update to try again.'],
    [/^Cập nhật lên (\S+)\? TV Remote sẽ đóng lại rồi tự mở bản mới\.([\s\S]*)$/, (m) => 'Update to ' + m[1] + '? TV Remote closes and opens the new version by itself.' + m[2]],
    [/^Gỡ (\d+) app khỏi các TV này\?\n([\s\S]*)$/, (m) => 'Remove ' + m[1] + ' apps from these TVs?\n' + m[2]],
    [/^Cài (.+) lên các TV này\?$/, (m) => 'Install ' + m[1] + ' on these TVs?'], [/^Gỡ (.+) khỏi các TV này\?$/, (m) => 'Remove ' + m[1] + ' from these TVs?'],
    [/^Xong: (\d+) TV ✓, (\d+) TV ✗$/, (m) => 'Done: ' + m[1] + ' TVs ✓, ' + m[2] + ' TVs ✗'], [/^Xong trên cả (\d+) TV$/, (m) => 'Done on all ' + m[1] + ' TVs'],
    [/^(\S+) trở lên$/, (m) => m[1] + ' or newer'],
    [/^Đã cho phép: (.+)$/, (m) => 'Allowed: ' + tr(m[1])], [/^Đã tắt quyền: (.+)$/, (m) => 'Permission off: ' + tr(m[1])],
    [/^TV không đổi quyền này: ([\s\S]+)$/, (m) => 'The TV did not change this permission: ' + tr(m[1])],
    [/^Màn hình xuất tới (.+)$/, (m) => 'Screen output up to ' + m[1]],
    [/^Đưa âm thanh (.+) ra loa\/amply qua HDMI$/, (m) => m[1] + ' sound out to a soundbar/receiver over HDMI'],
    [/^(\d+) \/ (\d+) mục$/, (m) => m[1] + ' / ' + m[2] + ' items'],
    // automation, scenes, the TV's sound
    [/^Không có app nào cần (.+)\.$/, (m) => `Nothing to ${({ 'tắt': 'disable', 'bật lại': 'turn on', 'gỡ': 'remove' })[m[1]] || m[1]}.`],
    [/^Gỡ (\d+) app khỏi TV\? Gỡ rồi phải cài lại mới có \(app hệ thống thì không gỡ được, hãy tắt\)\.$/, (m) => `Remove ${m[1]} apps from the TV? They are gone until reinstalled (system apps cannot be removed: disable them).`],
    [/^Tắt (\d+) app\? Bật lại được bất cứ lúc nào ở đây hoặc ở mục “App đã tắt”\.$/, (m) => `Disable ${m[1]} apps? Turn them back on any time here or under “Disabled apps”.`],
    [/^Dừng (.+)\? App đang hiện trên TV\.$/, (m) => `Stop ${m[1]}? It is on the TV's screen.`],
    [/^Đã dừng app: (.+)$/, (m) => `Stopped: ${m[1]}`],
    [/^Đang bật: báo khi RAM dùng từ (\d+)%\.$/, (m) => `On: a notice when ${m[1]}% of the memory is in use.`],
    [/^(Tắt|Bật lại|Gỡ) (\d+)\/(\d+): (.+)\.\.\.$/, (m) => `${({ 'Tắt': 'Disabling', 'Bật lại': 'Turning on', 'Gỡ': 'Removing' })[m[1]]} ${m[2]}/${m[3]}: ${m[4]}...`],
    [/^(Tắt|Bật lại|Gỡ): xong (\d+)\/(\d+)(?: · không được: (.+))?$/, (m) => `${({ 'Tắt': 'Disable', 'Bật lại': 'Turn on', 'Gỡ': 'Remove' })[m[1]]}: done ${m[2]}/${m[3]}` + (m[4] ? ` · failed: ${m[4]}` : '')],
    [/^⏸ App đã tắt \((\d+)\)$/, (m) => `⏸ Disabled apps (${m[1]})`],
    [/^Tắt (.+)\? App biến khỏi TV cho tới khi bật lại ở mục “App đã tắt” cuối danh sách này\.$/, (m) => `Disable ${m[1]}? It disappears from the TV until you turn it back on under “Disabled apps” at the end of this list.`],
    [/^⏸ đã tắt · (.+)$/, (m) => '⏸ disabled · ' + m[1]],
    [/^Đã nhập (\d+) cảnh$/, (m) => `Imported ${m[1]} scene${m[1] === '1' ? '' : 's'}`],
    [/^bỏ qua (\d+) cảnh không hợp lệ$/, (m) => `skipped ${m[1]} invalid`],
    [/^(\d+) phút$/, (m) => m[1] + ' min'],
    [/^(\d+) giờ(?: (\d+)(?: phút)?)?$/, (m) => m[1] + ' h' + (m[2] ? ' ' + m[2] + ' min' : '')],
    [/^(\d+) phút hôm nay$/, (m) => m[1] + ' min today'],
    [/^còn ([^·→]+)$/, (m) => tr(m[1]) + ' left'],
    [/^TV Setup trả lời lỗi: ([^·→]*)$/, (m) => 'TV Setup answered with an error: ' + tr(m[1])],
    [/^Đang cho xem thêm tới ([^·→]+)\.$/, (m) => `Extra time until ${m[1]}.`],
    [/^Đang bật: ([^·→]+) – ([^·→]+) mỗi ngày\.$/, (m) => `On: ${m[1]} – ${m[2]} every day.`],
    [/^Hôm nay đã dùng ([^·→]+) \/ ([^(]+?)(?: \(cho thêm ([^·→]+)\))?$/, (m) => `Used today: ${tr(m[1])} / ${tr(m[2])}` + (m[3] ? ` (${tr(m[3])} extra)` : '')],
    [/^Mỗi ngày được dùng (.+) bao lâu\?$/, (m) => 'How long a day for ' + m[1] + '?'],
    [/^Chọn từ 1 phút tới (\d+) giờ\.$/, (m) => 'Choose from 1 minute to ' + m[1] + ' hours.'],
    [/^Bỏ giới hạn cho ([^·→]+)\?$/, (m) => `Remove the limit for ${m[1]}?`],
    [/^Tối đa (\d+)%$/, (m) => `At most ${m[1]}%`],
    [/^Bỏ giới hạn (\d\d:\d\d – \d\d:\d\d)\?$/, (m) => `Remove the limit for ${m[1]}?`],
    [/^Đã giới hạn âm lượng: tối đa (\d+)%, (.+)$/, (m) => `Volume limited: at most ${m[1]}%, ${m[2]}`],
    [/^Đang giới hạn tối đa (\d+)% tới (\S+)\.$/, (m) => `Limited to ${m[1]}% until ${m[2]}.`],
    [/^Đã lưu giờ đi ngủ: ([^·→]+)$/, (m) => 'Bedtime saved: ' + m[1]],
    [/^Đã thêm giờ đi ngủ: ([^·→]+)$/, (m) => 'Bedtime added: ' + m[1]],
    [/^Bỏ giờ đi ngủ (\d\d:\d\d – \d\d:\d\d)\?$/, (m) => `Remove the bedtime ${m[1]}?`],
    [/^Đã giới hạn ([^·→]+): ([^·→]+) mỗi ngày$/, (m) => `Limited ${m[1]}: ${tr(m[2])} a day`],
    [/^Đã cho thêm ([^·→]+): ([^·→]+)$/, (m) => `Granted ${tr(m[1])} more: ${m[2]}`],
    [/^Không cài được: ([^·→]*)$/, (m) => 'Could not install: ' + tr(m[1])],
    [/^Mở ([^·→]+)$/, (m) => 'Open ' + m[1]],
    [/^Âm lượng (\d+)$/, (m) => 'Volume ' + m[1]],
    [/^Phím ([^·→]+)$/, (m) => 'Key ' + tr(m[1])],
    [/^Chờ ([^·→]+) giây$/, (m) => `Wait ${m[1]} s`],
    [/^📺 Ngõ vào (.+)$/, (m) => '📺 Input ' + m[1]],
    [/^🔔 Thông báo: (.*)$/, (m) => '🔔 Notification: ' + m[1]],
    [/^Hẹn giờ tắt ([^·→]+)$/, (m) => 'Sleep timer ' + tr(m[1])],
    [/^Xong: ([^·→]+)$/, (m) => 'Done: ' + m[1]],
    [/^Xong, nhưng có bước không làm được: ([^·→]*)$/, (m) => 'Done, but some steps did not work: ' + tr(m[1])],
    [/^Đã lưu cảnh: ([^·→]+)$/, (m) => 'Scene saved: ' + m[1]],
    [/^Xóa cảnh “([\s\S]+)”\?$/, (m) => `Delete the scene “${m[1]}”?`],
    [/^Không lấy được tiếng TV: ([^·→]*)$/, (m) => 'Could not get the TV’s sound: ' + tr(m[1])],
    [/^＋([^·→]+)$/, (m) => '+' + tr(m[1])],
    [/^(\d+) KB\/s$/, (m) => m[0]],
    [/^Xong: ([\s\S]*)$/, (m) => 'Done: ' + tr(m[1])],
    [/^Không làm được: ([\s\S]*)$/, (m) => 'Could not do it: ' + tr(m[1])],
    [/^Đã vào Tailscale · IP (\S+)\. Ở ngoài nhà, máy điều khiển bật Tailscale \(cùng tài khoản\) rồi dùng IP này\.$/, (m) => `In Tailscale · IP ${m[1]}. Away from home, turn on Tailscale on the controlling device (same account) and use this IP.`],
    [/^(\d+) khung\/giây$/, (m) => m[1] + ' fps'],
    [/^(\d+) mục$/, (m) => m[1] + ' items'],
    [/^đã dùng (.+) \/ (.+)$/, (m) => `${m[1]} used of ${m[2]}`],
    [/^(\d+) \/ (\d+) · đang tắt tiếng$/, (m) => `${m[1]} / ${m[2]} · muted`],
    [/^(-?\d+ dBm) · (rất tốt|tốt|trung bình|yếu)$/, (m) => m[1] + ' · ' + ({ 'rất tốt': 'excellent', 'tốt': 'good', 'trung bình': 'fair', 'yếu': 'weak' })[m[2]]],
    [/^CPU · (\d+) nhân$/, (m) => `CPU · ${m[1]} cores`],
    [/^trực tiếp · (\d+) khung\/giây · (nét|nhẹ)$/, (m) => `live · ${m[1]} fps · ${m[2] === 'nét' ? 'sharp' : 'light'}`],
    [/^(?:(\d+) ngày )?(?:(\d+) giờ )?(\d+) phút$/, (m) => (m[1] ? m[1] + ' d ' : '') + (m[2] ? m[2] + ' h ' : '') + m[3] + ' min'],
    [/^Còn trống ([^·]+) \/ ([^·]+)$/, (m) => `${m[1]} free of ${m[2]}`],
    [/^trong nhà (.+)$/, (m) => 'at home ' + m[1]],
    [/^Đang đọc tên và logo app từ TV \((\d+)\/(\d+)\), chỉ lần đầu\.\.\.$/, (m) => `Reading app names and icons from the TV (${m[1]}/${m[2]}), first time only...`],
    [/^Đã mở trên TV: (.*)$/, (m) => 'Opened on the TV: ' + m[1]],
    [/^(Xóa dữ liệu|Tắt app|Bật lại): (.*)$/, (m) => ({ 'Xóa dữ liệu': 'Data cleared', 'Tắt app': 'Disabled', 'Bật lại': 'Turned on' })[m[1]] + ': ' + m[2]],
    [/^Xóa toàn bộ dữ liệu của (.+)\? App trở lại như mới cài: mất đăng nhập, cài đặt và nội dung đã tải trong app\. Không hoàn tác được\.$/, (m) => `Clear all of ${m[1]}’s data? The app goes back to as just installed: sign-ins, settings and what it downloaded are lost. This cannot be undone.`],
    [/^Đang sao lưu (.+): tệp \.tar sẽ tải về máy này$/, (m) => `Backing up ${m[1]}: a .tar file will download to this device`],
    [/^Khôi phục (.+) lên TV\? App được cài lại \(giữ dữ liệu nếu đang có trên TV\), kèm thư mục dữ liệu trong bộ nhớ chung\.$/, (m) => `Restore ${m[1]} to the TV? The app is installed again (keeping its data if it is on the TV), with its folders in shared storage.`],
    [/^Đã dọn bộ nhớ đệm, lấy lại (.+)$/, (m) => 'Caches cleaned, ' + m[1] + ' freed'],
    [/^TV đang thử (\S+), tối đa 30 giây\.\.\.$/, (m) => `The TV is trying ${m[1]}, up to 30 seconds...`],
    [/^Đã bật lọc DNS: (.+)$/, (m) => 'DNS filter on: ' + m[1]],
    [/^(Tự tắt|Màn hình chờ|Màn hình chính): (.+)$/, (m) => ({ 'Tự tắt': 'Turn off when idle', 'Màn hình chờ': 'Screensaver', 'Màn hình chính': 'Home screen' })[m[1]] + ': ' + tr(m[2])],
    [/^Cỡ giao diện: (.+)\. App đang mở cần mở lại mới đổi theo\.$/, (m) => 'Display size: ' + tr(m[1]) + '. Apps already open change once opened again.'],
    [/^TV không đổi màn hình chính: (.+)$/, (m) => 'The TV did not change its home screen: ' + tr(m[1])],
    [/^(Mở|Dừng|Gỡ): (.*)$/, (m) => ({ 'Mở': 'Opened', 'Dừng': 'Stopped', 'Gỡ': 'Uninstalled' })[m[1]] + ': ' + m[2]],
    [/^Gỡ cài đặt (.*) khỏi TV\?$/, (m) => `Uninstall ${m[1]} from the TV?`],
    [/^Cài APK từ link này lên TV\?\n([\s\S]*)$/, (m) => 'Install the APK from this link on the TV?\n' + m[1]],
    [/^Cài (.*) lên TV\?$/, (m) => `Install ${m[1]} on the TV?`],
    [/^Xóa thư mục \(và mọi thứ bên trong\) (.*)\?$/, (m) => `Delete the folder (and everything in it) ${m[1]}?`],
    [/^Xóa "(.*)" khỏi danh sách\? \(Không đổi gì trên TV\)$/, (m) => `Remove "${m[1]}" from the list? (Nothing changes on the TV)`],
    [/^Xóa (.*)\?$/, (m) => `Delete ${m[1]}?`],
    [/^đang gửi (.+) \/ (.+)$/, (m) => `sending ${m[1]} / ${m[2]}`],
    [/^Gửi (.+)$/, (m) => 'Send ' + m[1]],
    [/^Cài (.+)$/, (m) => 'Install ' + m[1]],
    [/^Đang kết nối (.+)\.\.\.$/, (m) => `Connecting to ${m[1]}...`],
    [/^IP Tailscale của TV: (\S+)\. Nên làm thêm: trong trang quản trị Tailscale, mở menu ⋯ của TV → Disable key expiry, để TV không bị đăng xuất sau khoảng 180 ngày\.$/, (m) => 'The TV’s Tailscale IP: ' + m[1] + '. Recommended: in the Tailscale admin console open the TV’s ⋯ menu → Disable key expiry, so the TV is not signed out after about 180 days.'],
    [/^(\d+) lần bị (dừng|treo)$/, (m) => m[1] + (m[2] === 'dừng' ? ' stop' : ' freeze') + (m[1] === '1' ? '' : 's')],
    [/^(\d+) lần$/, (m) => m[1] + (m[1] === '1' ? ' time' : ' times')],
    [/^(.+ phút) trước$/, (m) => tr(m[1]) + ' ago'],
    [/^(.+ phút) \((\d+)%\)$/, (m) => tr(m[1]) + ' (' + m[2] + '%)'],
    [/^Số lần ngủ sâu \((.+)\)$/, (m) => 'Deep sleeps (' + m[1] + ')'],
    [/^bật (\d+) lần$/, (m) => 'on ' + m[1] + (m[1] === '1' ? ' time' : ' times')],
    [/^Tailscale mất (\d+) lần$/, (m) => 'Tailscale lost ' + m[1] + (m[1] === '1' ? ' time' : ' times')],
    [/^(\d+) (phút|giờ|ngày) trước$/, (m) => m[1] + (m[2] === 'phút' ? ' min' : m[2] === 'giờ' ? ' h' : m[1] === '1' ? ' day' : ' days') + ' ago'],
    [/^Quên (.+) \(chính là máy này, sẽ phải nhập mã lại\)\?$/, (m) => 'Forget ' + tr(m[1]) + ' (this is the device you are on: it will need the code again)?'],
    [/^Quên (.+)\?$/, (m) => 'Forget ' + tr(m[1]) + '?'],
    [/^lần cuối (.+)$/, (m) => 'last ' + m[1]],
    [/^TV đã có IP Tailscale (.*)\. Lưu để từ nay kết nối qua IP đó, dùng được cả khi không ở nhà\.$/, (m) => `The TV has the Tailscale IP ${m[1]}. Save to connect through it from now on, so it also works away from home.`],
    [/^Đã lưu, chuyển sang (.*)$/, (m) => 'Saved, switched to ' + m[1]],
    [/^([\s\S]*)\n✅ Xong\.$/, (m) => m[1] + '\n✅ Done.'],
    [/^([\s\S]*)\n❌ Lỗi: ([\s\S]*)$/, (m) => m[1] + '\n❌ Error: ' + tr(m[2])],
    [/^không tới được dịch vụ remote của TV \(cổng 6467\): (.*)$/, (m) => 'cannot reach the TV’s remote service (port 6467): ' + m[1]],
  ];

  function core(k) {
    if (Object.prototype.hasOwnProperty.call(EN, k)) return EN[k];
    for (const [re, fn] of RULES) { const m = re.exec(k); if (m) return fn(m); }
    const m = /^([^\p{L}\p{N}\s·“"(]+\s+)([\s\S]+)$/u.exec(k);         // "🔍 Quét mạng": keep the icon, translate the words
    if (m) { const rest = core(m[2]); if (rest !== null) return m[1] + rest; }
    return null;
  }
  function tr(s) {
    if (typeof s !== 'string' || !s.trim()) return s;
    const k = s.trim();
    let out = core(k);
    for (const sep of [' · ', ' → ']) {                               // "a · b · c", the steps of a scene: translate the pieces we know
      if (out !== null || !k.includes(sep)) continue;
      const parts = k.split(sep).map((p) => core(p.trim()));
      if (parts.some((p) => p !== null)) out = k.split(sep).map((p, i) => parts[i] === null ? p : parts[i]).join(sep);
    }
    return out === null ? s : s.replace(k, () => out);
  }
  window.tr = tr;

  const ATTRS = ['placeholder', 'title', 'aria-label', 'alt'];
  // words an id already gave are left alone (a rule like "＋X" would take its English for Vietnamese); new words a script
  // writes there later are still Vietnamese and translated here
  const given = (e, attr, v) => { const id = e && e.getAttribute(attr); return !!id && T(id).trim() === v.trim(); };
  const old = (v, e, attr) => !given(e, attr, v) && !(e && e.closest('[data-i18n-html]'));
  function visit(node) {
    if (node.nodeType === 3) { const v = node.nodeValue; if (!old(v, node.parentElement, 'data-i18n')) return; const t = tr(v); if (t !== v) node.nodeValue = t; return; }
    if (node.nodeType !== 1 || node.tagName === 'SCRIPT' || node.tagName === 'STYLE') return;
    for (const a of ATTRS) { const v = node.getAttribute(a); if (v && !given(node, 'data-i18n-' + a, v)) { const t = tr(v); if (t !== v) node.setAttribute(a, t); } }
    for (const c of node.childNodes) visit(c);
  }
  const start = () => {
    visit(document.body);
    new MutationObserver((list) => {
      for (const m of list) {
        if (m.type === 'characterData') visit(m.target);
        else if (m.type === 'attributes') { const v = m.target.getAttribute(m.attributeName); if (v && !given(m.target, 'data-i18n-' + m.attributeName, v)) { const t = tr(v); if (t !== v) m.target.setAttribute(m.attributeName, t); } }
        else m.addedNodes.forEach((n) => { i18nPut(n); visit(n); });
      }
    }).observe(document.body, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ATTRS });
  };
  if (document.body) start(); else document.addEventListener('DOMContentLoaded', start);

})();
