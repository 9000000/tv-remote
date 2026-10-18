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

    'YAML cho Home Assistant': 'YAML for Home Assistant',
    
    
    
    // js/diagnose.js: the self-check after the TV stays out of reach (Diagnose.kt words its own conclusion in the asked language)
    
    // one page for the three cores: pairing with the page from the TV, the allow-debugging screen
    
    
    
    
    
    
    
    'TV đang hỏi “Cho phép gỡ lỗi USB?”': 'The TV is asking “Allow USB debugging?”',
    'Chọn OK (Cho phép)': 'Choose OK (Allow)',
    'Để sau': 'Later', 'Đang kết nối': 'Connecting',
    'Tối ưu': 'Optimize',
    
    
    // header, tabs
    'đang kết nối...': 'connecting...', 'Tắt': 'Quit',
    'Ứng dụng': 'Apps', 'Tệp': 'Files', 'cần ADB': 'needs ADB', 'thêm': 'add', 'cài đặt': 'setup',
    
    'Mất kết nối': 'Disconnected',
    'chưa ghép remote (tab TV)': 'remote not paired (TV tab)', 'đã kết nối': 'connected',
    // screen
    'Hoặc': 'Or', '⚙️ Hình nâng cao': '⚙️ Advanced picture', 'Mặc định': 'Default',
    'Màn hình TV': 'TV screen', 'Trực tiếp · nhẹ': 'Live · light', 'Trực tiếp · nét': 'Live · sharp', 
    'Toàn màn hình': 'Full screen', 'Xem': 'Watch', 
    'chế độ nét làm TV chậm hơn rõ rệt': 'sharp mode slows the TV noticeably', 'chế độ nhẹ cho TV': 'light on the TV',
    'TV không cho chiếu hình lên đây': 'The TV does not let its picture show here', '(do chip của TV hoặc phim có bản quyền)': '(the TV\'s chip, or a copy-protected film)',
    
    // remote
    'Mở ứng dụng': 'Open an app', 'Lên': 'Up', 'Xuống': 'Down', 'Trái': 'Left', 'Phải': 'Right',
    'Cài đặt của TV': 'TV settings', 'Quay lại': 'Back', 'Âm lượng': 'Volume', 'Tắt tiếng': 'Mute', 
    'Giảm âm lượng': 'Volume down', 'Tăng âm lượng': 'Volume up', '☀ Đánh thức': '☀ Wake', 'Wake-on-LAN khi TV standby sâu': 'Wake-on-LAN when the TV is in deep standby',
    'Thêm phím': 'More keys', 'Trợ lý': 'Assistant', 'Nói': 'Speak', 'Độ to': 'Loudness', 'Nhân tiếng TV gửi sang, có bộ giới hạn để không rè. Vẫn nhỏ: tăng âm lượng trên TV.': 'Multiplies the sound the TV sends, with a limiter so it does not crack. Still quiet: turn the TV up.', 'Về ×1': 'Back to ×1', 'Về Tự chọn': 'Back to Auto', 'Ngõ vào': 'Input', 'Phụ đề': 'Subtitles', 'Kênh +': 'CH +', 'Kênh −': 'CH −', 'Kênh': 'CH',
    'Đã lấy lại': 'Got back', 'còn trống': 'free',
    
    'Đã chép': 'Copied', 'TV đang làm việc vừa yêu cầu, chờ vài giây rồi bấm lại': 'The TV is still doing what was just asked; wait a few seconds, then press again', 'Đang mở Downloader trên TV để kiểm tra...': 'Opening Downloader on the TV to check...', '📤 Phát ảnh, video, nhạc từ máy này lên TV': '📤 Play a photo, video or song from this computer on the TV',
    'Bật ADB cho TV này': 'Turn on ADB for this TV', 'Tự động: cài TV Setup lên TV': 'Automatic: install TV Setup on the TV',
    'Tự làm: mở Cài đặt TV': 'By hand: open TV Settings', 
    'Cài màn hình chính khác': 'Install another home screen', 
    'Mở CH Play trên TV': 'Open the Play Store on the TV', 'Mở app theo tên gói': 'Open an app by package name', 
    'Bật Gỡ lỗi ADB trên TV': 'Turn on ADB debugging on the TV', 'Cách khác': 'Other ways', 'Rung': 'Vibrate', 'Cài qua Downloader': 'Install through Downloader', 'Thấy rồi': 'Found it', 'TV không mở Cài đặt theo lệnh': 'The TV did not open Settings when asked',
    'Đang thử mở Cài đặt trên TV...': 'Trying to open Settings on the TV...', 'TV có mở màn hình Cài đặt không?': 'Did the TV open its Settings?', 'Có': 'Yes', 'Không': 'No',
    'Không có · Cài màn hình chính khác': 'Not there · Install another home screen', 'Mở Cài đặt qua màn hình chính khác': 'Reach Settings through another home screen', 'Đang mở trên TV': 'Open on the TV now', 'Đang mở': 'Now open',
    'Các bước bật ADB': 'ADB steps', 'Mở lại': 'Open again', 'Chọn cái khác': 'Pick another',
    'Cài TV Setup lên TV': 'Install TV Setup on the TV', 'Đã cài xong': 'Installed', 'Dừng': 'Stop', 'Nhìn lên TV': 'Look at the TV', 'Mở TV Setup': 'Open TV Setup', 'Thử lại': 'Try again', 
    'Đang gửi đường dẫn tải cho TV...': 'Sending the download link to the TV...', 'Đang mở Downloader trên TV...': 'Opening Downloader on the TV...', 'Gửi link cài thẳng cho TV': 'Send the install link to the TV', 'Nhìn lên TV: nếu TV hỏi tải tv-setup.apk, chọn OK, rồi chọn Cài đặt (Install). Cài xong bấm “Đã cài xong”.\n\nCách này chưa ổn định: có TV chỉ tải về mà không tự cài, hoặc đứng ở trang trắng. Khi đó bấm “Cách khác”.': 'Look at the TV: if it asks to download tv-setup.apk, choose OK, then Install. Once installed, press “Installed”.\n\nThis way is not reliable: some TVs only download the file and do not install it, or stay on a white page. Then press “Other ways”.', 'TV chưa cài được?': 'Not installed yet?', 'TV đang hỏi “Mở bằng”: đang chọn giúp...': 'The TV asks “Open with”: choosing for you...', 'Đang đưa đường dẫn tải cho Downloader...': 'Handing the download link to Downloader...', 'TV đang tải TV Setup...': 'The TV is downloading TV Setup...', 'Đừng bấm remote của TV cho tới khi xong.': 'Leave the TV\'s remote alone until this is done.', 'Màn hình TV đã đổi giữa chừng, có ai vừa bấm remote của TV? Để yên remote rồi thử lại.': 'The TV\'s screen changed in the middle: did someone press the TV\'s remote? Leave it alone and try again.',
    'TV chưa có app Downloader: đang cài từ CH Play. Nếu TV không tự cài, chọn “Cài đặt” trên màn hình TV. Khi TV báo cài xong, bấm “Đã cài xong”.': 'The TV has no Downloader app yet: installing it from the Play Store. If it does not start by itself, choose “Install” on the TV. When the TV says it is installed, press “Installed”.',
    'TV này không cho biết đang mở gì nên không tự làm được. Làm theo cách “Tự làm” bên dưới.': 'This TV does not say what is on its screen, so this cannot run by itself. Use “By hand”.',
    'App Android': 'Android app', 
    'Chất lượng hình': 'Picture quality', 'Nét nhất': 'Sharpest', 'CH Play': 'Play Store',
    'Tìm app trên CH Play, hoặc app id (vd org.videolan.vlc)': 'Search the Play Store, or an app id (e.g. org.videolan.vlc)',
    'Chọn TV khác': 'Choose another TV', 'Điều khiển': 'Control', 'Thiết lập': 'Setup', 'lần đầu': 'first time',
    'Bấm vào một TV bên dưới. Lần đầu: bấm “Quét mạng” để tìm TV trong cùng Wi-Fi, hoặc nhập IP của TV.': 'Click a TV below. First time: press “Scan” to find TVs on this Wi-Fi, or type the TV’s IP.',
    
    'Bàn phím': 'Keyboard',
    'Bàn phím số': 'Number keys',
    'Mở': 'Open', 'Cài đặt TV': 'TV settings', 'Cài đặt': 'Settings',
    'TV đã nhận nút này, nhưng màn hình hiện tại không phản hồi': 'The TV got the key, but the current screen did not react',
    'Đã gửi lệnh, nhưng ADB đang đóng nên TV có thể bỏ qua': 'Sent, but ADB is closed so the TV may ignore it',
    
    // ADB banners
    
    
    'Đang kết nối...': 'Connecting...', 
    // apps
    'Ứng dụng đã cài': 'Installed apps', 
    'Gỡ': 'Uninstall', 
    // the ⋯ menu on each app, the cache clean, backups, an app's log
    
    
    'Đang phát': 'Now playing', 'Bài trước': 'Previous', 'Bài sau': 'Next', 'Tua': 'Seek', 'Clipboard của TV': 'The TV’s clipboard', 'Clipboard của TV đang trống. Gõ hoặc dán chữ vào đây để gửi lên TV.': 'The TV’s clipboard is empty. Type or paste text here to send it to the TV.', 'Chưa có chữ': 'No text yet', 'Đã dán trên TV': 'Pasted on the TV', 'Đã chép lên TV': 'Copied to the TV', 'Đã sao chép vào máy này': 'Copied to this device', 
    'Tạm dừng': 'Pause', 
    'Tệp này không phải bản sao lưu của TV Remote.': 'This file is not a TV Remote backup.',
    'App này cần cho TV chạy, không tắt hay xóa dữ liệu được.': 'The TV needs this app: it cannot be disabled or cleared.',
    // the TV card on the Setup tab
    'Khởi động lại TV': 'Restart the TV',
    'Lọc DNS': 'DNS filter', 'Tự nhập...': 'Custom...',
    
    
    
    'giờ': 'h',

    'Nhỏ': 'Small', 'Vừa': 'Normal', 'Lớn': 'Large', 'Rất lớn': 'Larger',
    'Màn hình chính': 'Home screen', 
    'không đổi được': 'could not change it',
    'TV đang tắt': 'The TV is shutting down', 
    
    // files

    'Mở trên TV': 'Open on the TV',
    'Xóa': 'Delete', 
    // install tab
    'Tải file về TV': 'Download to the TV', 'Tải': 'Download',
    'Cài APK': 'Install an APK', 'Cài': 'Install',
    // the ADB assistant on the computer, and a few lines that had no English ye
    
    
    
    
    'Tên gói hoặc đường dẫn': 'Package name or link', 
    // answering the TV's question for another device

    
    // choosing what Optimize TV does
    'Chạy tối ưu': 'Optimize', 'Hủy': 'Cancel', 'Đang xem TV có gì để tối ưu...': 'Looking at what this TV has...', 
    'Tắt hiệu ứng chuyển cảnh': 'Turn off transition animations', 'Đang tắt sẵn': 'Already off',
    'Màn hình chính FLauncher (đang dùng)': 'FLauncher home screen (in use)', 'Dùng FLauncher làm màn hình chính': 'Use FLauncher as the home screen',
    'Chỉ lưới app, nhẹ, không quảng cáo; màn hình chính Google được tắt · tải 10 MB': 'Just a grid of apps, light, no ads; Google’s home screen is turned off · 10 MB download',
    
    // Tailscale in one button
    'Không mở được kết nối bảo mật. Ngày giờ trên TV có đúng không? (Cài đặt → Hệ thống → Ngày và giờ)': 'Could not open a secure connection. Is the TV\'s date and time right? (Settings → System → Date & time)',
    'TV (hoặc máy này) chưa vào được Internet.': 'The TV (or this device) cannot reach the Internet.', 'Mạng chậm hoặc vừa mất kết nối. Thử lại sau ít phút.': 'The network is slow or dropped. Try again in a few minutes.',
    'TV ngừng trả lời giữa chừng. Thử lại; vẫn vậy thì khởi động lại TV.': 'The TV stopped answering midway. Try again; if it keeps happening, restart the TV.',
    'Đang bật VPN nên không tới được TV trong nhà. TV có đang bật và cùng Wi-Fi không? Nếu có, trong app VPN bật cho phép mạng LAN (Local network / Bypass LAN), hoặc tắt VPN.': 'A VPN is on, so the TV at home is out of reach. Is the TV on and on the same Wi-Fi? If so, allow the local network in the VPN app (Local network / Bypass LAN), or turn the VPN off.',
    '■ Dừng': '■ Stop', 'Đã dừng': 'Stopped', 
    'Chép tệp lên TV không trọn vẹn. Thử lại.': 'The file did not reach the TV whole. Try again.', 'Mất kết nối với TV giữa chừng. Thử lại.': 'Lost the TV halfway. Try again.', 'TV hết bộ nhớ trống.': 'The TV is out of storage.',
    'TV đã có app này nhưng từ nguồn khác (khác chữ ký). Gỡ bản đang có rồi cài lại; dữ liệu của app sẽ mất.': 'The TV already has this app from another source (a different signature). Uninstall it first, then install again; the app’s data will be lost.',
    'TV đang có bản mới hơn của app này. Gỡ bản đang có nếu muốn cài bản cũ.': 'The TV has a newer version of this app. Uninstall it first to install an older one.',
    'APK này cần Android mới hơn bản trên TV. Tìm bản cũ hơn của app.': 'This APK needs a newer Android than the TV has. Look for an older version of the app.',
    'APK này dành cho loại chip khác với TV. Tải bản cho đúng loại chip (thường là armeabi-v7a).': 'This APK is for a different kind of chip than the TV’s. Get the build for the right chip (usually armeabi-v7a).',
    'TV báo cài xong nhưng tự gỡ app này ngay sau đó. Box của nhà mạng (FPT Play...) chặn một số app; app này không dùng được trên TV này.': 'The TV said it installed the app, then removed it right away. Operator boxes (FPT Play...) block some apps; this one cannot be used on this TV.',
    'Ẩn': 'Hide', 'App này không có màn hình để mở: nó chỉ chạy nền, hoặc là một phần của app khác.': 'This app has no screen to open: it only runs in the background, or is part of another app.', 'Nhập số phút (vd 40) hoặc giờ tắt (vd 23:30)': 'Type minutes (e.g. 40) or a time to turn off (e.g. 23:30)',
    'TV không có app mở loại tệp này (cài VLC rồi thử lại).': 'The TV has no app for this kind of file (install VLC, then retry).',
    'Không đọc được link đăng nhập trên màn hình TV. Mở Tailscale trên TV và quét mã QR nó hiện.': 'Could not read the sign-in link off the TV\'s screen. Open Tailscale on the TV and scan the QR code it shows.',
    
    'Tailscale · điều khiển khi không ở nhà': 'Tailscale · control away from home',
    'Cài và đăng nhập Tailscale': 'Install and sign in to Tailscale', 'Tailscale cho TV': 'Tailscale for the TV',
    
    'Đang làm...': 'Working...',
    
    
    
    
    'TV đã vào Tailscale': 'The TV is in Tailscale', 'Xong': 'Done',
    'Tối ưu TV': 'Optimize TV',
    'Tắt hiệu ứng chuyển cảnh cho TV nhanh hơn': 'Turn off transition animations so the TV feels faster',
    'Thay màn hình chính Google bằng FLauncher (chỉ lưới app, nhẹ CPU)': 'Replace the Google home screen with FLauncher (just a grid of apps, light on CPU)',
    'Tắt app hệ thống không dùng (chế độ khách sạn/trưng bày của Sony, Samba, Sony Select, Play Games, Play Movies, YouTube Music)': 'Turn off unused system apps (Sony hotel/demo modes, Samba, Sony Select, Play Games, Play Movies, YouTube Music)',
    // ✏️ drawing on the TV's screen (draw.js)
    'Vẽ lên màn hình TV: chỉ chỗ cho người đang xem': 'Draw on the TV screen: show the person watching where to look', 'Vẽ lên màn hình TV': 'Draw on the TV screen',
    'Màu': 'Colour', 'Hẹn giờ, giờ đi ngủ, giới hạn app': 'The sleep timer, bedtime and app limits', 'Đưa ảnh lên TV': 'A picture on the TV', 'Nét': 'Line size',
    'Bỏ nét vừa vẽ': 'Take back the last line', 
    'TV này không cho app vẽ đè lên màn hình.': 'This TV does not let apps draw over its screen.',
    'Hoàn tác': 'Undo', 'đang tải về điện thoại...': 'downloading to the phone...', 
    'Chạy tối ưu TV? Màn hình chính của TV sẽ là FLauncher.': 'Optimize TV? Its home screen will become FLauncher.',
    
    // health
    'Sức khỏe': 'Health', 'Tổng quan': 'Overview', 'Tải hệ thống': 'System load', 'Tiến trình tốn CPU nhất': 'Top CPU processes', 'Mạng': 'Network', 'Phần cứng': 'Hardware',
    'Nguồn': 'Power', 
    
    'bình thường': 'normal', 'ấm': 'warm', 'đang tắt': 'off', 'đã ghép': 'paired', 'Thiết bị': 'Devices',
    
    
    'IP Tailscale': 'Tailscale IP', 'Máy': 'Device', 
    'Màn hình': 'Screen', 'Đánh thức': 'Wake',
    'Mở Google Assistant trên TV': 'Open Google Assistant on the TV', 
    'TV này không có YouTube hay tìm kiếm của Google để tìm câu này.': 'This TV has neither YouTube nor Google search to look this up.', 'TV này không có tìm kiếm / Assistant của Google': 'This TV has no Google search / Assistant',
    // settings, abou
    'Theo máy': 'System', 'Sáng': 'Light', 'Tối': 'Dark', 'Ngôn ngữ': 'Language',
    'Giới thiệu': 'About',
    'Báo lỗi': 'Report a problem', 'Đóng': 'Close',
    'Hình trực tiếp và chuột dùng': 'The live picture and mouse use', '(Genymobile, Apache-2.0). Nút remote dùng giao thức Android TV Remote.': '(Genymobile, Apache-2.0). The remote buttons use the Android TV Remote protocol.',
    // qui
    'Đã tắt': 'Stopped', 
    'Khởi động lại': 'Restart',
    // TV tab
    'TV đã lưu': 'Saved TVs', 'Tìm TV': 'Find a TV', 'Đang quét...': 'Scanning...', 'Chọn': 'Choose', 'Kết nối': 'Connect', 'Dùng': 'Use', 'đang dùng': 'in use', 'cùng mạng': 'same network', 'qua Tailscale': 'via Tailscale',
    'Chưa có TV nào. Bấm “Quét mạng” hoặc nhập IP ở bên cạnh.': 'No TVs yet. Press “Scan” or type an IP.', 
    'ADB đã bật': 'ADB on', 'ADB chưa bật': 'ADB off', 'Cài đặt lần đầu ·': 'First-time setup ·', 'Kiểm tra lại': 'Check again', 
    'TV đang chờ bạn cho phép': 'The TV is waiting for your permission',
    
    'Trên TV: Cài đặt → Tùy chọn thiết bị → Giới thiệu → bấm OK 7 lần vào “Bản dựng”. Rồi Tùy chọn nhà phát triển → bật “Gỡ lỗi ADB” (hoặc “Gỡ lỗi USB”). Việc này phải làm bằng remote của TV.': 'On the TV: Settings → Device Preferences → About → press OK 7 times on “Build”. Then Developer options → turn on “ADB debugging” (or “USB debugging”). This has to be done with the TV’s own remote.',
    
    'Cài Tailscale lên TV': 'Install Tailscale on the TV', 'Cài Tailscale': 'Install Tailscale',
    'Để điều khiển TV khi không ở cùng mạng. Tải bản chính thức (khoảng 100 MB), có kiểm checksum. Chỉ dùng trong cùng Wi-Fi thì bỏ qua được.': 'To control the TV from another network. Downloads the official build (about 100 MB) and verifies its checksum. Skip it if you only use the TV on the same Wi-Fi.',
    'Mở Tailscale trên TV rồi xem màn hình TV ngay tại đây: TV hiện một mã (và mã QR). Không cần gõ tài khoản trên TV: mở login.tailscale.com/admin/machines trên máy này hoặc điện thoại → Add device → nhập mã đó (hoặc quét QR bằng điện thoại).': 'Open Tailscale on the TV and watch its screen here: the TV shows a code (and a QR code). No need to type an account on the TV: open login.tailscale.com/admin/machines on this computer or a phone → Add device → enter that code (or scan the QR with a phone).',
    'Mở Tailscale trên TV': 'Open Tailscale on the TV', 'TV Hisense này cần chứng chỉ từ app “VIDAA Smart TV” của Hisense. Vào tab Thiết lập để chọn file .apk của app đó.': 'This Hisense TV needs the certificate from Hisense’s “VIDAA Smart TV” app. On the Setup tab, pick that app’s .apk.', 'Không tìm thấy chứng chỉ trong file này. Chọn file .apk của app “VIDAA Smart TV”, hoặc file .p12.': 'No certificate in this file. Pick the .apk of the “VIDAA Smart TV” app, or a .p12 file.', 'Chọn file': 'Choose a file', 'Mã 4 số trên TV': '4-digit code on the TV', 
    'Mã 6 ký tự trên TV': '6-character code on the TV', 
    'Đã ghép': 'Paired', 'Ghép': 'Pair', 
    
    'Lần sau mở lên là dùng TV này. Đã ghi địa chỉ MAC để đánh thức TV.': 'This TV is used the next time you start. Its MAC address is recorded for waking it.',
    'Đã lưu': 'Saved',
    'Tối ưu TV (tùy chọn)': 'Optimize TV (optional)',
    'Giữ Tailscale sống, giảm hiệu ứng, thay màn hình chính bằng FLauncher, tắt app hệ thống không dùng. Hoàn tác được.': 'Keep Tailscale alive, reduce animations, replace the home screen with FLauncher, turn off unused system apps. Can be undone.',
    // help
    'Chức năng nào cần ADB?': 'Which features need ADB?',

    // server messages that are in Vietnamese
    'sai mã, thử lại': 'wrong code, try again',
    'Nhập sai mã nhiều lần: đợi 15 phút rồi thử lại.': 'Too many wrong codes: wait 15 minutes and try again.',
    // automation, scenes, the TV's sound, voice commands, the home screen icon
    '✨ Chất lượng cao: tắt': '✨ High quality: off', '✨ Chất lượng cao: bật': '✨ High quality: on',
    '🎧 Chỉ máy này': '🎧 Only this device', 
    '🔈 Cả TV và máy này': '🔈 TV and this device', 
    'TV này dưới Android 13 nên chỉ nghe riêng được: loa TV tạm im.': 'This TV is older than Android 13, so only private listening works: the TV’s speakers are quiet.', 
    'Bật / tắt / gỡ hàng loạt': 'Turn on / off / remove in bulk',
    'Tắt app': 'Disable', 'đã tắt': 'disabled',
    '🚀 Tự chạy khi bật TV': '🚀 Starts with the TV', 
    
    'App đang chạy': 'Running apps', 
    'Hệ thống': 'System', 
    'TV đang bật': 'The TV is on', 
    'Số lần khởi động': 'Boots', 
    'có': 'yes', 'không': 'no',
    'vẫn giữ': 'kept on', 
    'không có': 'nothing',
    'bấm nút nguồn': 'power key', 
    'cắm điện': 'plugged in',
    
    'Giữ kết nối khi tắt TV': 'Stay reachable when the TV is off', 'Đọc lịch sử, giấc ngủ trên TV này': 'Reading the history and sleep on this TV',
    'chưa cài': 'not installed', 'đang chạy': 'running', 'không chạy': 'not running',
    
    'Báo khi RAM đầy': 'Full memory notice',
    'Không có app nào. Bật “Hiện cả app hệ thống” để thấy cả app hệ thống.': 'No apps. Turn on “Show system apps too” to see system apps as well.',

    'đang bật': 'on',
    
    
    'Thêm remote này vào màn hình chính': 'Add this remote to your home screen',
    'Đã hiểu': 'Got it',
    
    'Tự động': 'Automation',
    'Nghe tiếng TV trên máy này (loa TV tạm im)': 'Hear the TV on this device (the TV’s speakers go quiet)',
    'Nghe': 'Listen',
    'Đang nghe': 'Listening',
    'Cảnh': 'Scenes',
    'Chạy': 'Run',
    'Thử': 'Try',
    'Thêm': 'Add', 'Thêm giờ đi ngủ': 'Add a bedtime', 'Thêm giới hạn app': 'Add an app limit', 'Thêm giới hạn âm lượng': 'Add a volume limit',
    'hết bộ nhớ': 'out of memory',
    'Tắt màn hình, vẫn nghe': 'Screen off, keep listening',
    'Giới hạn âm lượng': 'Volume limits',
    'Âm lượng tối đa': 'Highest volume',
    'Đến': 'To',
    
    'Bấm phím bất kỳ để bật lại': 'Any key turns it back on',
    // the remote's "More keys" menus and the listening bar: the same words as the phone app's (Media.kt, Automation.kt, Listen.kt)
    'Tìm app trên CH Play': 'Search the Play Store',
    'Tìm': 'Search', 'App id (tên gói)': 'App id (package name)', 'Đường dẫn': 'Link',
    'Tắt TV sau bao lâu?': 'Turn the TV off when?', 'Hẹn': 'Set',
    'Cách nghe tiếng TV': 'How to hear the TV', 
    'Máy này ▾': 'This device ▾', 'Cả hai ▾': 'Both ▾', 'Tiếng chất lượng cao (160 kbit/s)': 'High quality sound (160 kbit/s)', 'Đều': 'Even', 'Tiếng đều hơn: trễ nửa giây nhưng không giật': 'Steadier sound: half a second late, no stutter',
    
    'Máy này đang nối TV qua Tailscale: điện thoại quét phải ở cùng Wi-Fi với TV.': "This device reaches the TV through Tailscale: the phone that scans must be on the TV's Wi-Fi.",
    'TV không có địa chỉ Wi-Fi: điện thoại quét cần bật Tailscale.': "The TV has no Wi-Fi address: the phone that scans needs Tailscale on.",
    'TV chưa có TV Setup: chỉ chia sẻ được app.': "The TV has no TV Setup: only the app can be shared.",
    
    'Toàn quyền': 'Full access',
    
    
    'Người quét được làm gì': 'What the scanner can do',
    'Điện thoại phải cùng Wi-Fi với TV': "The phone must be on the TV's Wi-Fi",
    'Dùng 1 lần, trong 10 phút': 'Once, within 10 minutes',
    'Chỉ remote': 'Remote only',
    'Chỉ xem': 'View only',
    
    'Quét để tải app.': "Scan to get the app.",
    'Chia sẻ remote bằng QR': "Sharing the remote by QR",

    'Từ': 'From',
    'App': 'App',
    'Tạo cảnh': 'New scene',

    'Các bước': 'Steps',
    'Bật TV': 'Turn the TV on',
    'Mở app': 'Open an app',
    'Đặt âm lượng': 'Set the volume',
    // inputs (remote.js inputsMenu, the scene step)
    'Chuyển ngõ vào': 'Switch input', 'Bảng chọn của TV': 'The TV’s own picker', 'Chưa cắm': 'Nothing plugged in', 'TV này không có ngõ vào để chọn': 'This TV has no inputs to choose',
    'Chưa đọc được ngõ vào của TV': 'Could not read the TV’s inputs', 
    'this TV did not take the switch': 'The TV did not switch input', 'this TV cannot switch inputs from here': 'This TV cannot switch inputs from here', 'bad input': 'Unknown input',
    'Bấm phím': 'Press a key',
    'Mở link': 'Open a link',
    'Chờ': 'Wait',
    'Tắt TV': 'Turn the TV off',
    
    
    'Cập nhật TV Setup': 'Update TV Setup',
    
    'Cập nhật': 'Update', 'Kiểm tra bản mới': 'Check for a new version',
    'Đang kiểm tra...': 'Checking...', 
    
    
    
    'Đã hủy hẹn giờ tắt': 'Sleep timer cancelled',
    'Bỏ': 'Remove', 'Nhiều khoảng giờ đi ngủ': 'Several bedtimes',
    
    'Phát / Dừng': 'Play / Pause',
    'Đi ngủ': 'Bedtime',
    
    
    
    
    'giây': 's',
    'phút': 'min',
    
    
    
    
    
    
    'loa TV tạm im': 'TV speakers quiet',
    'chất lượng cao': 'high quality',
    
    
    
    
    'App hệ thống': 'System app',
    
    'bảo mật': 'secure',
    
    
    'cần giữ': 'must keep',
    
    'Nhiều TV cùng lúc': 'Several TVs at once',
    'Cài APK lên TV đã chọn': 'Install APK on the TVs picked',
    'Đang hỏi từng TV...': 'Asking each TV...',
    'đang chờ lượt': 'waiting its turn', 'đang kết nối': 'connecting', 
    'đã cài': 'installed', 'đã gỡ': 'removed', 'đang khởi động lại': 'restarting', 'xong': 'done',
    'TV tắt, khác mạng hoặc chưa bật ADB': 'TV off, on another network, or ADB not on',
    'Ghép bằng Gỡ lỗi không dây': 'Pair with Wireless debugging', 
    'Trên TV: Cài đặt → Tùy chọn nhà phát triển → Gỡ lỗi không dây → Ghép nối thiết bị bằng mã ghép nối.': 'On the TV: Settings → Developer options → Wireless debugging → Pair device with pairing code.',
    'IP:cổng ghép nối': 'Pairing IP:port', 'Mã 6 số': '6-digit code', 
    'Nhập IP:cổng và mã 6 số hiện trên TV.': 'Type the IP:port and the 6-digit code the TV shows.', 'Đang ghép nối...': 'Pairing...',
    
    'Mã ghép nối không đúng hoặc đã hết hạn. Xem mã mới trên TV rồi nhập lại.': 'The pairing code is wrong or has expired. Read the new code on the TV and type it again.',
    'Cổng này không phải cổng ghép nối. Dùng đúng địa chỉ IP và cổng trong hộp “Ghép nối thiết bị” trên TV.': 'That is not the pairing port. Use the IP address and port from the TV’s “Pair device” box.',
    'TV chưa ghép với app này. Ghép nối bằng Gỡ lỗi không dây trước.': 'The TV is not paired with this app yet. Pair through Wireless debugging first.',
    'App này không ghép nối được bằng Gỡ lỗi không dây trên thiết bị này.': 'This app cannot pair through Wireless debugging on this device.',
    'TV đang hỏi “Cho phép gỡ lỗi USB?”: bấm Cho phép trên TV': 'The TV asks “Allow USB debugging?”: press Allow on the TV',
    
    'Chọn ít nhất một TV': 'Pick at least one TV',
    
    'Gỡ app nào?': 'Remove which app?',
    'Các TV đã chọn không có app tự cài nào.': 'The TVs picked have no apps you installed.',
    'Cho phép': 'Allow',
    'Đang chạy': 'Running',
    
    
    'hệ thống giữ': 'fixed by the system',
    
    
    'mục': 'items',
    
    
    
    
    
    

    'Đã tắt màn hình TV. Bấm phím bất kỳ để bật lại.': 'The TV’s screen is off. Any key turns it back on.',
    'Lời nhắn hiện trên TV': 'A message for the TV',
    'Tiếp': 'Next',
    'Nhỏ lại': 'Smaller',
    'To lên': 'Bigger',
    'Không mở được ảnh này.': 'Could not open this picture.',
    
    
    'Phiên bản': 'Version',
    
    'tự chạy khi bật TV': 'starts with the TV',
    
    
    
    'Micro': 'Microphone',
    'Máy ảnh': 'Camera',
    'Vị trí': 'Location',
    'Tệp, ảnh và video': 'Files, photos and videos',
    'Thông báo': 'Notifications',
    'Danh bạ và tài khoản': 'Contacts and accounts',
    'Điện thoại': 'Phone',
    'Tin nhắn': 'Messages',
    'Lịch': 'Calendar',
    'Thiết bị ở gần': 'Nearby devices',
    'Ghép lúc': 'Paired', 'dùng': 'used', 'Hủy ghép': 'Unpair', 'Thiết bị cũ': 'Older device', 'thiết bị này': 'this device', 
    'App điện thoại': 'Phone app', 'App máy tính': 'Computer app',
    'Cảm biến và hoạt động': 'Sensors and activity',

    'Loa TV': 'TV speakers',
    'Chép': 'Copy',
    'TV không cho đổi quyền này: hệ thống giữ nó.': 'The TV does not let this permission change: the system keeps it.',
    'App này không xin quyền đó.': 'This app does not ask for that permission.',
    'Mất khoảng nửa phút. Trang này tự nối lại sau đó.': 'About half a minute. This page reconnects afterwards.',
    'Đang cập nhật...': 'Updating...', 'Đang cài bản mới...': 'Installing the new version...',
    'Đã cài, đang xem bản mới có chạy tốt không...': 'Installed; checking that the new version runs well...',
    
    'TV Setup trên TV đã là bản mới nhất.': 'TV Setup on the TV is already the latest version.',
    'TV Setup đang tự cập nhật. Chờ khoảng một phút.': 'TV Setup is updating itself. Wait about a minute.',
    'TV chưa có app TV Setup.': 'The TV does not have the TV Setup app.',
    'TV chưa bắt đầu cập nhật được. Thử lại; vẫn vậy thì khởi động lại TV.': 'The TV did not start the update. Try again; if it still does not, restart the TV.',
    'Không ghi được tệp cập nhật lên TV. TV còn bộ nhớ trống không?': 'Could not write the update file on the TV. Does it have free storage?',
    // LG and Samsung TVs: no ADB, the TV itself asks whether to let this device in
    
    'Nhìn lên TV: chọn Cho phép': 'Look at the TV: choose Allow',
    
    'TV đã cho phép': 'The TV allowed this device',
    'TV đã từ chối': 'The TV said no',
    'Chưa ai chọn Cho phép trên TV': 'Nobody chose Allow on the TV',
    'Hỏi lại TV': 'Ask the TV again',
    'TV đã từ chối. Bấm Hỏi lại TV ở tab Thiết lập rồi chọn Cho phép.': 'The TV said no. Press Ask the TV again on the Setup tab, then choose Allow.',
    'Chưa ai chọn Cho phép trên TV. Bấm Hỏi lại TV ở tab Thiết lập.': 'Nobody chose Allow on the TV. Press Ask the TV again on the Setup tab.',
    'TV LG': 'LG TV',
    'TV Samsung': 'Samsung TV',
    'TV LG không hỗ trợ': 'LG TVs cannot do this',
    'TV Samsung không hỗ trợ': 'Samsung TVs cannot do this',
    'TV Roku': 'Roku TV', 'TV Hisense VIDAA': 'Hisense VIDAA TV', 'TV Hisense VIDAA không hỗ trợ': 'Hisense VIDAA TVs cannot do this', 'TV Roku không hỗ trợ': 'Roku TVs cannot do this', 'Roku đang chặn điều khiển': 'The Roku blocks control',
    'Roku đang chặn điều khiển từ điện thoại, máy tính. Trên Roku: Settings → System → Advanced system settings → Control by mobile apps → chọn Enabled (hoặc Permissive).': 'The Roku blocks control from phones and computers. On the Roku: Settings → System → Advanced system settings → Control by mobile apps → choose Enabled (or Permissive).',
    'Chỉ có trên Android TV / Google TV': 'Only on Android TV / Google TV', 'Gửi nhật ký': 'Send the log', 'Tự chọn': 'Auto', 'Phần cứng': 'Hardware', 'Phần mềm': 'Software', 
    'thử nghiệm': 'beta',
    'Nghe tiếng TV': 'Listen to the TV',
    
    
    'Gửi thông báo lên TV': 'Notify the TV', 'Tiêu đề (không bắt buộc)': 'Title (optional)',
    'Tiêu đề': 'Title', 'Nội dung': 'Text', 'Nội dung ngắn, vd Xuống ăn cơm!': 'A short message, e.g. Dinner is ready!', 
    '↖ Trên trái': '↖ Top left', '↗ Trên phải': '↗ Top right', '↙ Dưới trái': '↙ Bottom left',
    '↘ Dưới phải': '↘ Bottom right', '3 giây': '3 s', '10 giây': '10 s', 'Gõ nội dung cần gửi': 'Type what to send',
    'Đã gửi lên TV': 'Sent to the TV', 'Gửi': 'Send',
    'TV này không cho app hiện thông báo đè lên màn hình.': 'This TV does not let apps show a notification over its screen.',
    'Hẹn giờ tắt TV': 'Sleep timer', 'Giờ đi ngủ': 'Bedtime',
    
  };

  // Strings with a variable part. Captured pieces are passed through tr() again where they can be text of ours.
  const RULES = [
    [/^Chưa thấy hình sau 10 giây\. TV này có thể không chạy được bộ mã hóa (.+)\. Chuyển về Tự chọn\?$/, (m) => 'No picture after 10 seconds. This TV may not run the ' + tr(m[1]).toLowerCase() + ' encoder. Back to Auto?'],
    [/^📍 Dùng địa chỉ (.+)$/, (m) => '📍 Use the address ' + m[1]],
    [/^Bước (\d+)\/(\d+)$/, (m) => 'Step ' + m[1] + '/' + m[2]],
    [/^Mạng chậm: hạ hình xuống (.+) cho kịp\.$/, (m) => 'Slow network: the picture went down to ' + tr(m[1]) + ' to keep up.'],
    [/^Mạng ổn lại: tăng hình lên (.+)\.$/, (m) => 'Network is fine again: picture back up to ' + tr(m[1]) + '.'],
    [/^Đợi (\d+) giây rồi thử lại\.$/, (m) => 'Wait ' + m[1] + ' seconds and try again.'],
    [/^(.+) cần TV Setup (\S+) trở lên \(TV đang có (\S*)\)\. Cập nhật TV Setup\?$/, (m) => tr(m[1]) + ' needs TV Setup ' + m[2] + ' or newer (the TV has ' + m[3] + '). Update TV Setup?'],
    [/^(.+) cần app TV Setup trên TV\. Cài TV Setup\?$/, (m) => tr(m[1]) + ' needs the TV Setup app on the TV. Install TV Setup?'],
    [/^TV Setup trên TV là bản cũ\. Cần bản (\S+) trở lên: mở TV Setup trên TV → Cập nhật, hoặc bấm nút dưới\.$/, (m) => 'TV Setup on the TV is too old. ' + m[1] + ' or newer is needed: open TV Setup on the TV → Update, or press the button below.'],
    [/^🆕 TV Setup trên TV có bản mới (\S+) \(đang dùng (\S+)\)$/, (m) => '🆕 TV Setup on the TV has a new version ' + m[1] + ' (it has ' + m[2] + ')'],
    [/^⚠️ TV đã tự tắt lọc DNS (\S+) lúc (\S+) (\S+), vì với nó TV không tra được tên miền nào \(YouTube và các app báo mất mạng\)\. Hay gặp khi TV có VPN như Tailscale\. Chọn lại một bộ lọc để thử lại\.$/,
      (m) => '⚠️ The TV turned the DNS filter ' + m[1] + ' off at ' + m[2] + ' on ' + m[3] + ': with it the TV could not look up any name (YouTube and other apps said there was no Internet). This happens on TVs with a VPN such as Tailscale. Pick a filter again to try once more.'],
    [/^⚠️ TV Setup (\S+) chạy lỗi trên TV này$/, (m) => '⚠️ TV Setup ' + m[1] + ' did not work on this TV'],
    [/^TV đã tự quay về bản (\S+)\. Hẹn giờ, ghép nối và cài đặt vẫn còn nguyên\. Có bản sau thì nó hiện ở đây\.$/, (m) => 'The TV went back to version ' + m[1] + ' by itself. Timers, pairings and settings are all still there. The next version shows up here when it comes out.'],
    [/^Đang tải bản mới về TV (\d+)%\.\.\.$/, (m) => 'Downloading the new version to the TV ' + m[1] + '%...'],
    [/^Đã cập nhật TV Setup lên (\S+)$/, (m) => 'TV Setup updated to ' + m[1]],
    [/^Chưa cập nhật được: ([\s\S]*)$/, (m) => 'Could not update: ' + tr(m[1])],
    [/^Có, dùng (.+) RAM$/, (m) => 'Yes, using ' + m[1] + ' of RAM'],
    [/^· (\d+) TV$/, (m) => '· ' + m[1] + ' TVs'], [/^trống ([\d.,]+ \S*B \/ .+)$/, (m) => 'free ' + m[1]],
    [/^🆕 Có bản mới (\S+) \(đang dùng (\S+)\)$/, (m) => '🆕 New version ' + m[1] + ' (you have ' + m[2] + ')'], [/^Đang tải bản mới (\d+)%$/, (m) => 'Downloading the new version ' + m[1] + '%'],
    [/^Chưa tải được bản mới \((.*)\)\. Bấm Cập nhật để thử lại\.$/s, (m) => 'Could not download the new version (' + m[1] + '). Press Update to try again.'],
    [/^Cập nhật lên (\S+)\? TV Remote sẽ đóng lại rồi tự mở bản mới\.([\s\S]*)$/, (m) => 'Update to ' + m[1] + '? TV Remote closes and opens the new version by itself.' + m[2]],
    [/^Không kiểm tra được: (.*)$/, (m) => 'Could not check: ' + m[1]], [/^Có bản mới (\S+): xem thanh trên cùng trang\.$/, (m) => 'New version ' + m[1] + ': see the bar at the top of the page.'],
    [/^Đang dùng bản mới nhất \((\S+)\)\.$/, (m) => 'You have the latest version (' + m[1] + ').'],
    [/^đang cài (\d+)%$/, (m) => 'installing ' + m[1] + '%'], [/^đang cài$/, () => 'installing'],
    [/^Gỡ (\d+) app khỏi các TV này\?\n([\s\S]*)$/, (m) => 'Remove ' + m[1] + ' apps from these TVs?\n' + m[2]],
    [/^Cài (.+) lên các TV này\?$/, (m) => 'Install ' + m[1] + ' on these TVs?'], [/^Gỡ (.+) khỏi các TV này\?$/, (m) => 'Remove ' + m[1] + ' from these TVs?'],
    [/^Xong: (\d+) TV ✓, (\d+) TV ✗$/, (m) => 'Done: ' + m[1] + ' TVs ✓, ' + m[2] + ' TVs ✗'], [/^Xong trên cả (\d+) TV$/, (m) => 'Done on all ' + m[1] + ' TVs'],
    [/^(\S+) trở lên$/, (m) => m[1] + ' or newer'],
    [/^Đã cho phép: (.+)$/, (m) => 'Allowed: ' + tr(m[1])], [/^Đã tắt quyền: (.+)$/, (m) => 'Permission off: ' + tr(m[1])],
    [/^TV không đổi quyền này: ([\s\S]+)$/, (m) => 'The TV did not change this permission: ' + tr(m[1])],
    [/^Màn hình xuất tới (.+)$/, (m) => 'Screen output up to ' + m[1]],
    [/^Đưa âm thanh (.+) ra loa\/amply qua HDMI$/, (m) => m[1] + ' sound out to a soundbar/receiver over HDMI'],
    [/^(\d+) \/ (\d+) mục$/, (m) => m[1] + ' / ' + m[2] + ' items'],
    [/^TV chỉ còn trống (.+), không đủ cài APK (.+) \(cần trống ít nhất (.+): bản chép và app sau khi cài\)\. Xóa bớt app hoặc tệp \(tab Sức khỏe \/ Tệp\) rồi thử lại\.$/, (m) => `The TV has only ${m[1]} free, not enough to install this ${m[2]} APK (it needs at least ${m[3]}: the copy and the installed app). Remove some apps or files (Health / Files tab), then try again.`],
    [/^TV chỉ còn trống (.+), không đủ chép tệp (.+) \(cần trống ít nhất (.+)\)\. Xóa bớt app hoặc tệp \(tab Sức khỏe \/ Tệp\) rồi thử lại\.$/, (m) => `The TV has only ${m[1]} free, not enough for this ${m[2]} file (it needs at least ${m[3]}). Remove some apps or files (Health / Files tab), then try again.`],
    [/^APK này cần Android (\S+) trở lên; TV đang chạy Android (\S+)\. Tìm bản cũ hơn của app \(trang APKMirror ghi “Min: Android \.\.\.”\)\.$/, (m) => 'This APK needs Android ' + m[1] + ' or newer; the TV runs Android ' + m[2] + '. Look for an older version of the app (APKMirror lists “Min: Android ...”).'],
    [/^APK này dành cho chip (.+), còn TV dùng (.+)\. Tải bản cho đúng loại chip \(thường là armeabi-v7a cho TV\)\.$/, (m) => 'This APK is for ' + m[1] + ' chips, the TV uses ' + m[2] + '. Get the build for the right chip (usually armeabi-v7a on TVs).'],
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
    [/^Tắt sau ([^·→]+)$/, (m) => 'Off in ' + tr(m[1])],
    [/^TV tự tắt lúc ([^·→]+)$/, (m) => 'The TV turns off at ' + m[1]],
    [/^TV sẽ tự tắt lúc ([^·→]+)$/, (m) => 'The TV turns off at ' + m[1]],
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
    [/^Đã chuyển sang (.+)$/, (m) => 'Switched to ' + m[1]],
    [/^📺 Ngõ vào (.+)$/, (m) => '📺 Input ' + m[1]],
    [/^🔔 Thông báo: (.*)$/, (m) => '🔔 Notification: ' + m[1]],
    [/^Hẹn giờ tắt ([^·→]+)$/, (m) => 'Sleep timer ' + tr(m[1])],
    [/^Hủy hẹn giờ tắt$/, () => 'Cancel the sleep timer'],
    [/^Đang chạy: ([^·→]+)$/, (m) => 'Running: ' + m[1]],
    [/^Xong: ([^·→]+)$/, (m) => 'Done: ' + m[1]],
    [/^Xong, nhưng có bước không làm được: ([^·→]*)$/, (m) => 'Done, but some steps did not work: ' + tr(m[1])],
    [/^Đã lưu cảnh: ([^·→]+)$/, (m) => 'Scene saved: ' + m[1]],
    [/^Xóa cảnh “([\s\S]+)”\?$/, (m) => `Delete the scene “${m[1]}”?`],
    [/^Đã bấm: ([^·→×]+?)( × \d+)?$/, (m) => 'Pressed: ' + tr(m[1]) + (m[2] || '')],
    [/^Không lấy được tiếng TV: ([^·→]*)$/, (m) => 'Could not get the TV’s sound: ' + tr(m[1])],
    [/^＋([^·→]+)$/, (m) => '+' + tr(m[1])],
    [/^(\d+) KB\/s$/, (m) => m[0]],
    [/^Không vẽ được lên TV: ([\s\S]*)$/, (m) => 'Could not draw on the TV: ' + tr(m[1])],
    [/^Lỗi: ([\s\S]*)$/, (m) => 'Error: ' + tr(m[1])],
    [/^Xong: ([\s\S]*)$/, (m) => 'Done: ' + tr(m[1])],
    [/^Không làm được: ([\s\S]*)$/, (m) => 'Could not do it: ' + tr(m[1])],
    [/^Mất kết nối với TV giữa chừng, thử lại: ([\s\S]*)$/, (m) => 'Lost the TV midway, try again: ' + m[1]],
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
    [/^Đã gửi cho TV: (.*)$/, (m) => 'Sent to the TV: ' + m[1]],
    [/^Đang tìm trên YouTube: (.*)$/, (m) => 'Searching YouTube for: ' + m[1]],
    [/^Đã mở trên TV: (.*)$/, (m) => 'Opened on the TV: ' + m[1]],
    [/^Đã mở (.*)$/, (m) => 'Opened ' + tr(m[1])],
    [/^(Xóa dữ liệu|Tắt app|Bật lại): (.*)$/, (m) => ({ 'Xóa dữ liệu': 'Data cleared', 'Tắt app': 'Disabled', 'Bật lại': 'Turned on' })[m[1]] + ': ' + m[2]],
    [/^Xóa toàn bộ dữ liệu của (.+)\? App trở lại như mới cài: mất đăng nhập, cài đặt và nội dung đã tải trong app\. Không hoàn tác được\.$/, (m) => `Clear all of ${m[1]}’s data? The app goes back to as just installed: sign-ins, settings and what it downloaded are lost. This cannot be undone.`],
    [/^Đang sao lưu (.+): tệp \.tar sẽ tải về máy này$/, (m) => `Backing up ${m[1]}: a .tar file will download to this device`],
    [/^Khôi phục (.+) lên TV\? App được cài lại \(giữ dữ liệu nếu đang có trên TV\), kèm thư mục dữ liệu trong bộ nhớ chung\.$/, (m) => `Restore ${m[1]} to the TV? The app is installed again (keeping its data if it is on the TV), with its folders in shared storage.`],
    [/^Đã dọn bộ nhớ đệm, lấy lại (.+)$/, (m) => 'Caches cleaned, ' + m[1] + ' freed'],
    [/^Đang dùng (\S+(?: \([^)]+\))?)\. (.*)$/, (m) => 'Using ' + m[1] + '. ' + tr(m[2])],
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
    [/^Đang tìm TV ở (.+)\.\.\.$/, (m) => `Looking for the TV at ${m[1]}...`],
    [/^Đang kết nối (.+)\.\.\.$/, (m) => `Connecting to ${m[1]}...`],
    [/^IP Tailscale của TV: (\S+)\. Nên làm thêm: trong trang quản trị Tailscale, mở menu ⋯ của TV → Disable key expiry, để TV không bị đăng xuất sau khoảng 180 ngày\.$/, (m) => 'The TV’s Tailscale IP: ' + m[1] + '. Recommended: in the Tailscale admin console open the TV’s ⋯ menu → Disable key expiry, so the TV is not signed out after about 180 days.'],
    [/^Đã lưu: (.*)$/, (m) => 'Saved: ' + m[1]],
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
    [/^ghép thất bại, bấm Ghép lại: (.*)$/, (m) => 'pairing failed, press Pair again: ' + m[1]],
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
