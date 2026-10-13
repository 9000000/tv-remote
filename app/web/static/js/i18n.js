// Vietnamese is the source language of the page; this file turns it into English when the viewer picks EN.
// It works on the rendered page (text nodes, a few attributes, confirm/prompt), so index.html needs no changes when
// a string is added: untranslated text simply stays Vietnamese until it is listed here.
(() => {
  let stored = null;
  try { stored = localStorage.getItem('lang'); } catch {}
  const LANG = stored || ((navigator.language || 'vi').toLowerCase().startsWith('vi') ? 'vi' : 'en');
  window.LANG = LANG;
  window.setLang = (l) => { try { localStorage.setItem('lang', l); } catch {} location.reload(); };
  document.documentElement.lang = LANG;
  if (LANG === 'vi') { window.tr = (s) => s; return; }

  const EN = {
    // header, tabs
    'đang kết nối...': 'connecting...', 'Tắt': 'Quit', 'Dừng chương trình trên máy tính này (TV không bị ảnh hưởng)': 'Stop the program on this computer (the TV is not affected)',
    'Ứng dụng': 'Apps', 'Tệp': 'Files', 'Tệp · Cài': 'Files · Install', 'Tải / Cài': 'Download / Install', 'cần ADB': 'needs ADB', '· cần ADB': '· needs ADB', 'thêm': 'add', 'cài đặt': 'setup',
    'ADB: sẵn sàng': 'ADB: ready', 'ADB: chờ TV cho phép': 'ADB: waiting for the TV', 'ADB: cổng đóng': 'ADB: port closed', 'ADB: lỗi': 'ADB: error',
    'Server tắt': 'Server stopped', 'Không gọi được server. Chạy lại start-mac.command.': 'Cannot reach the server. Run start-mac.command again.', 'Không gọi được server': 'Cannot reach the server',
    'TV: mất kết nối': 'TV: disconnected', 'TV: đang bật': 'TV: on', 'TV: màn hình tắt': 'TV: screen off', 'Chưa chọn TV': 'No TV chosen',
    'Mở tab “TV” để tìm và thêm TV': 'Open the “TV” tab to find and add a TV', 'chưa ghép remote (tab TV)': 'remote not paired (TV tab)', 'đã kết nối': 'connected',
    // screen
    'Màn hình TV': 'TV screen', 'Chế độ xem': 'View mode', 'Trực tiếp · nhẹ': 'Live · light', 'Trực tiếp · nét': 'Live · sharp', 'Ảnh chụp': 'Screenshots',
    'Toàn màn hình': 'Full screen', 'Xem': 'View', 'Dừng': 'Stop', 'Về Remote': 'Back to Remote', 'Trực tiếp': 'Live', 'đang chờ hình': 'waiting for a picture',
    'chế độ nét làm TV chậm hơn rõ rệt': 'sharp mode slows the TV noticeably', 'chế độ nhẹ cho TV': 'light on the TV',
    'Bấm “Xem” để xem màn hình TV. Khi đang xem: bấm chuột lên hình để chạm, cuộn để cuộn, chuột phải = Back.': 'Press “View” to see the TV screen. While viewing: click the picture to tap, scroll to scroll, right-click = Back.',
    'Ảnh chụp, làm mới khoảng 1,5 giây/ảnh. Video có bản quyền (Netflix...) hiện màu đen.': 'Screenshots, refreshed about every 1.5 seconds. Copy-protected video (Netflix...) shows as black.',
    'Đang mở luồng trực tiếp...': 'Opening the live stream...', 'Luồng trực tiếp bị ngắt, chuyển sang ảnh chụp': 'Live stream dropped, switching to screenshots', 'Đang lấy hình...': 'Getting the picture...',
    'ADB chưa sẵn sàng, đã dừng xem màn hình.': 'ADB is not ready, stopped viewing.',
    // remote
    'Mở ứng dụng': 'Open an app', 'Danh sách app hiện khi ADB sẵn sàng.': 'Apps appear when ADB is ready.', 'Điều hướng': 'Navigation', 'Lên': 'Up', 'Xuống': 'Down', 'Trái': 'Left', 'Phải': 'Right',
    'Cài đặt của TV': 'TV settings', 'Quay lại': 'Back', 'Âm lượng': 'Volume', 'Tắt tiếng': 'Mute', 'Tắt / bật tiếng': 'Mute / unmute', 'Đang tắt tiếng, bấm để bật': 'Muted, press to unmute',
    'Giảm âm lượng': 'Volume down', 'Tăng âm lượng': 'Volume up', '⏻ Nguồn': '⏻ Power', '☀ Đánh thức': '☀ Wake', 'Wake-on-LAN khi TV standby sâu': 'Wake-on-LAN when the TV is in deep standby',
    'Đưa TV về chế độ chờ?\nNếu TV tắt cả mạng thì chỉ mở lại được bằng nút Đánh thức (cần một máy ở nhà đang bật) hoặc remote của TV.': 'Put the TV in standby?\nIf its network goes off too, only Wake (needs a computer at home that is on) or the TV’s own remote can turn it back on.',
    'Thêm phím': 'More keys', 'Ngõ vào': 'Input', 'Cài đặt': 'Settings', 'Thông tin': 'Info', 'Lịch phát': 'Guide', 'Phụ đề': 'Subtitles', 'Kênh +': 'CH +', 'Kênh −': 'CH −', 'Kênh': 'CH', 'Tua lại': 'Rewind', 'Dừng': 'Stop', 'Tua tới': 'Forward', 'Chụp màn hình': 'Screenshot',
    '🧹 Giải phóng RAM': '🧹 Free up RAM', 'Đã lấy lại': 'Got back', 'còn trống': 'free',
    'Hiện tại': 'Now', 'Lịch sử': 'History', 'TV đang làm việc vừa yêu cầu, chờ vài giây rồi bấm lại': 'The TV is still doing what was just asked; wait a few seconds, then press again', '⏳ Đang kiểm tra...': '⏳ Checking...', '✓ Đã cài xong': '✓ Installed', 'Đang mở Downloader trên TV để kiểm tra...': 'Opening Downloader on the TV to check...', '📤 Phát ảnh, video, nhạc từ máy này lên TV': '📤 Play a photo, video or song from this computer on the TV',
    'Bật ADB giúp tôi': 'Turn ADB on for me', 'Bật ADB cho TV này': 'Turn on ADB for this TV', 'Đóng': 'Close', 'Tự động: cài TV Setup lên TV': 'Automatic: install TV Setup on the TV',
    'Trang này cho TV tự tải và mở app TV Setup; app đó mở thẳng Tùy chọn nhà phát triển. Bạn chỉ xác nhận trên TV.': 'This page has the TV fetch and open the TV Setup app, which opens Developer options directly. You only confirm on the TV.',
    'Tự làm: mở Cài đặt TV': 'By hand: open TV Settings', 'Thử mọi cách mở Cài đặt, rồi chỉ các bước bật Gỡ lỗi ADB.': 'Tries every way to open Settings, then shows the steps to turn ADB on.',
    'Cài màn hình chính khác': 'Install another home screen', 'Khi TV giấu Cài đặt: mở qua một launcher từ CH Play.': 'When the TV hides Settings: go through a launcher from the Play Store.',
    'Mở CH Play trên TV': 'Open the Play Store on the TV', 'Mở app theo tên gói': 'Open an app by package name', 'Đã cài thì mở app, chưa cài thì mở trang CH Play của nó.': 'Opens the app, or its Play Store page when not installed.',
    'Khi TV bật ADB xong, trang này tự nhận ra và kết nối. Các nút mũi tên, OK ở tab Remote vẫn dùng được trong lúc làm.': 'Once the TV has ADB on, this page notices and connects. The arrows and OK on the Remote tab keep working meanwhile.',
    'Bật Gỡ lỗi ADB trên TV': 'Turn on ADB debugging on the TV', 'Đã bật, kết nối': 'It is on, connect', 'Các cách khác': 'Other ways', 'Thấy rồi': 'Found it', 'TV không mở Cài đặt theo lệnh': 'The TV did not open Settings when asked',
    'Mở Cài đặt TV': 'Open TV Settings', 'Đang thử mở Cài đặt trên TV...': 'Trying to open Settings on the TV...', 'TV có mở màn hình Cài đặt không?': 'Did the TV open its Settings?', 'Có': 'Yes', 'Không': 'No',
    'Không có · Cài màn hình chính khác': 'Not there · Install another home screen', 'Mở Cài đặt qua màn hình chính khác': 'Reach Settings through another home screen', 'Đang mở trên TV': 'Open on the TV now',
    'Đã cài thì mở, chưa cài thì mở trang CH Play.': 'Opens it, or its Play Store page.', 'Các bước bật ADB': 'ADB steps', 'Mở lại': 'Open again', 'Chọn cái khác': 'Pick another',
    'Cài TV Setup lên TV': 'Install TV Setup on the TV', 'Đang bắt đầu...': 'Starting...', 'Đã cài xong': 'Installed', 'Dừng': 'Stop', 'Nhìn lên TV': 'Look at the TV', 'Mở TV Setup': 'Open TV Setup', 'Làm lại': 'Again', 'Thử lại': 'Try again', 'Không làm được.': 'That did not work.',
    'Đang gửi đường dẫn tải cho TV...': 'Sending the download link to the TV...', 'TV đang hỏi “Mở bằng”: đang chọn giúp...': 'The TV asks “Open with”: choosing for you...', 'Đang đưa đường dẫn tải cho Downloader...': 'Handing the download link to Downloader...', 'TV đang tải TV Setup...': 'The TV is downloading TV Setup...',
    'TV chưa có app Downloader: đang cài từ CH Play. Nếu TV không tự cài, chọn “Cài đặt” trên màn hình TV. Khi TV báo cài xong, bấm “Đã cài xong”.': 'The TV has no Downloader app yet: installing it from the Play Store. If it does not start by itself, choose “Install” on the TV. When the TV says it is installed, press “Installed”.',
    'TV này không cho biết đang mở gì nên không tự làm được. Làm theo cách “Tự làm” bên dưới.': 'This TV does not say what is on its screen, so this cannot run by itself. Use “By hand”.',
    'Tới bước ghép remote': 'Go to remote pairing', 'ADB đã kết nối': 'ADB connected', 'ADB đã bật: nhìn lên TV, chọn “Luôn cho phép”': 'ADB is on: look at the TV and choose “Always allow”',
    '🔒 Gõ chữ cần ADB. TV này chưa mở ADB nên phần này tạm khóa; các nút mũi tên, OK, Back vẫn dùng được.': '🔒 Typing needs ADB. This TV has ADB closed, so this part is locked; the arrows, OK and Back still work.',
    'App Android': 'Android app', 'Quét bằng điện thoại để tải app.': 'Scan with a phone to download the app.', 'Remote trên điện thoại': 'Remote on a phone',
    'Trang remote do chính TV phục vụ: quét khi điện thoại cùng Wi-Fi hoặc cùng Tailscale với TV.': 'The remote page served by the TV itself: scan when the phone is on the same Wi-Fi or Tailscale as the TV.', 'Chất lượng hình': 'Picture quality', 'Nét nhất · nặng TV': 'Sharpest · heavy on the TV', 'Mở trên TV': 'Open on the TV', 'link, CH Play': 'link, Play Store', 'CH Play': 'Play Store',
    'Tìm app trên CH Play, hoặc app id (vd org.videolan.vlc)': 'Search the Play Store, or an app id (e.g. org.videolan.vlc)', 'Chất lượng hình: số nhỏ thì nhẹ cho TV và mạng': 'Picture quality: a smaller number is lighter on the TV and the network',
    'Chọn TV để điều khiển': 'Choose a TV to control', 'Chọn TV khác': 'Choose another TV', 'Chưa chọn TV': 'No TV chosen', 'Quay lại': 'Back to', 'Đổi TV': 'Change TV', 'Điều khiển': 'Control', 'Thiết lập': 'Set-up', 'lần đầu': 'first time',
    'Bấm vào một TV bên dưới. Lần đầu: bấm “Quét mạng” để tìm TV trong cùng Wi-Fi, hoặc nhập IP của TV.': 'Click a TV below. First time: press “Scan” to find TVs on this Wi-Fi, or type the TV’s IP.',
    'chưa ghép remote (tab Thiết lập)': 'remote not paired (Set-up tab)', 'Chọn TV khác, tìm TV mới': 'Choose another TV, find a new one',
    'Lệnh đã chạy...': 'Earlier commands...',
    'Bài trước': 'Previous', 'Phát/Dừng': 'Play/Pause', 'Bài sau': 'Next', 'Bàn phím': 'Keyboard', 'Xóa ký tự': 'Delete a character', 'Gửi phím Enter': 'Send Enter',
    'Giữ nút = nhấn giữ trên TV · Bàn phím máy tính gõ thẳng lên TV: chữ, ⌫ xóa, mũi tên, Enter, Esc = Back': 'Hold a button = long press on the TV · The computer keyboard types straight onto the TV: letters, ⌫ delete, arrows, Enter, Esc = Back',
    'Bấm vào đây rồi gõ, chữ hiện ngay trên TV': 'Click here and type; the text appears on the TV',
    'Gõ chữ rồi bấm Enter để gửi': 'Type, then press Enter to send', 'Gõ trực tiếp / gõ xong rồi gửi': 'Live typing / type then send', 'Bàn phím số': 'Number keys',
    'Không cần bấm vào ô này: cứ gõ là chữ lên TV (TV phải đang ở một ô nhập chữ). Ô này dành cho gõ tiếng Việt có dấu. Bàn phím ảo trên TV không bấm bằng chuột được, hãy gõ từ đây.': 'You do not need to click this box: just type and it goes to the TV (the TV must be in a text field). Use this box for accented text. The TV’s on-screen keyboard cannot be clicked with the mouse; type from here.',
    'Mở link trên TV': 'Open a link on the TV', 'Mở': 'Open', 'Cài đặt TV': 'TV settings', 'Cài đặt': 'Settings',
    'TV đã nhận nút này, nhưng màn hình hiện tại không phản hồi': 'The TV got the key, but the current screen did not react',
    'Đã gửi lệnh, nhưng ADB đang đóng nên TV có thể bỏ qua': 'Sent, but ADB is closed so the TV may ignore it',
    'Đang đánh thức TV...': 'Waking the TV...', 'TV đã sẵn sàng': 'The TV is ready', 'TV chưa lên. Kiểm tra “Khởi động từ xa” trên TV và laptop ở nhà còn bật': 'The TV did not come up. Check “Remote start” on the TV and that the relay computer at home is on',
    'Đang đọc tên và logo app từ TV': 'Reading app names and icons from the TV',
    // ADB banners
    'Cổng ADB đang đóng': 'The ADB port is closed', 'TV đang tắt/standby sâu, vừa khởi động lại, hoặc chưa bật “Gỡ lỗi ADB”. Remote vẫn dùng được nếu TV còn mạng.': 'The TV is off / in deep standby, has just restarted, or “ADB debugging” is off. The remote still works if the TV is online.',
    'TV chưa cho phép máy này': 'The TV has not allowed this computer', 'Nhìn lên TV, chọn “Luôn cho phép” ở hộp thoại gỡ lỗi USB rồi bấm “Thử kết nối lại”.': 'On the TV choose “Always allow” in the USB debugging dialog, then press “Retry connection”.',
    'ADB đang offline': 'ADB is offline', 'Thiết bị có trong danh sách nhưng không trả lời. Thử kết nối lại, hoặc tắt/bật “Gỡ lỗi ADB” trên TV.': 'The device is listed but does not answer. Retry, or toggle “ADB debugging” on the TV.',
    'Không kết nối được ADB': 'Cannot connect ADB', 'Thử kết nối lại. Nếu vẫn lỗi, kiểm tra TV còn mạng và đã bật “Gỡ lỗi ADB”.': 'Retry. If it still fails, check the TV is online and “ADB debugging” is on.',
    'Đang kiểm tra ADB...': 'Checking ADB...', 'Thử kết nối lại': 'Retry connection', 'Đang kết nối...': 'Connecting...', 'Mất chức năng:': 'Lost:',
    'xem màn hình, mở app/link, danh sách app, tệp, tải link về TV, cài APK.': 'live screen, opening apps/links, app list, files, download to TV, installing APKs.', 'Cách bật ADB': 'How to enable ADB',
    // apps
    'Ứng dụng đã cài': 'Installed apps', 'Tải lại': 'Reload', 'Tìm theo tên app': 'Search by app name', 'Hiện cả app hệ thống': 'Show system apps too', 'Đang tải...': 'Loading...',
    'Gỡ': 'Uninstall', 'Không có app nào.': 'No apps.',
    // files
    'Lọc trong thư mục này': 'Filter this folder', 'Sắp xếp': 'Sort', 'Theo tên': 'By name', 'Mới nhất': 'Newest', 'Lớn nhất': 'Largest', '＋ Thư mục': '＋ Folder',
    '＋ Kéo thả file vào đây, hoặc bấm để chọn file gửi lên thư mục này': '＋ Drop files here, or click to choose files to send to this folder',
    'Bộ nhớ TV': 'TV storage', 'Phim': 'Movies', 'Ảnh': 'Pictures', 'Nhạc': 'Music', 'Thư mục': 'Folder', 'Cài APK này lên TV': 'Install this APK on the TV', 'Mở trên TV': 'Open on the TV',
    'Tải về máy này': 'Download to this computer', 'Đổi tên': 'Rename', 'Tên mới:': 'New name:', 'Đã đổi tên': 'Renamed', 'Xóa': 'Delete', 'Đã xóa': 'Deleted',
    'Không có mục nào khớp.': 'Nothing matches.', 'Thư mục trống.': 'Empty folder.', 'Tên thư mục mới:': 'New folder name:', 'Đã tạo thư mục': 'Folder created', 'Xem trước': 'Preview',
    // install tab
    'Tải file từ link về TV': 'Download a file from a link to the TV', 'Tải': 'Download', 'Lưu vào thư mục Download của TV. Máy này tải về trước rồi đẩy sang TV.': 'Saved to the TV’s Download folder. This computer downloads it first, then pushes it to the TV.',
    'Cài APK': 'Install an APK', 'Link tới file .apk': 'Link to an .apk file', 'Cài': 'Install', 'Hoặc chọn file .apk từ máy này': 'Or choose an .apk file from this computer',
    'Chỉ cài APK bạn tin tưởng. Cài đè bản cũ được (giữ dữ liệu). File .apk đã nằm trên TV thì cài từ tab Tệp.': 'Only install APKs you trust. Installing over an older version keeps its data. An .apk already on the TV can be installed from the Files tab.',
    'Tối ưu TV': 'Optimise the TV', 'Một lần bấm, chạy lại bao nhiêu lần cũng được, không gỡ cài đặt gì:': 'One press, safe to run again, nothing is uninstalled:',
    'Giữ Tailscale sống qua standby và khởi động lại': 'Keep Tailscale alive through standby and restarts', 'Tắt hiệu ứng chuyển cảnh cho TV nhanh hơn': 'Turn off transition animations so the TV feels faster',
    'Thay màn hình chính Google bằng FLauncher (chỉ lưới app, nhẹ CPU)': 'Replace the Google home screen with FLauncher (just a grid of apps, light on CPU)',
    'Tắt app hệ thống không dùng (chế độ khách sạn/trưng bày của Sony, Samba, Sony Select, Play Games, Play Movies, YouTube Music)': 'Turn off unused system apps (Sony hotel/demo modes, Samba, Sony Select, Play Games, Play Movies, YouTube Music)',
    '⚡ Tối ưu TV': '⚡ Optimise the TV', 'Hoàn tác': 'Undo', 'Tiến trình tải lên / cài đặt': 'Uploads / installs', 'đang gửi lên server...': 'sending to the server...',
    'Chạy tối ưu TV? Màn hình chính của TV sẽ là FLauncher.': 'Optimise the TV? Its home screen will become FLauncher.',
    'Hoàn tác? Bật lại màn hình chính Google, các app đã tắt và hiệu ứng như cũ.': 'Undo? This turns the Google home screen, the disabled apps and normal animations back on.',
    'Đang chạy...': 'Running...', 'Đã xong': 'Done',
    // health
    'Sức khỏe': 'Health', 'Tổng quan': 'Overview', 'Tải hệ thống': 'System load', 'Tiến trình tốn CPU nhất': 'Top CPU processes', 'Mạng': 'Network', 'Phần cứng': 'Hardware',
    'cập nhật mỗi 5 giây': 'refreshes every 5 seconds', 'Nguồn': 'Power', 'không rõ (remote chưa kết nối)': 'unknown (remote not connected)', 'đang bật': 'on', 'màn hình tắt': 'screen off',
    'Ứng dụng đang mở': 'Current app', 'Độ trễ tới TV (ADB)': 'Latency to the TV (ADB)', 'Luồng hình': 'Picture stream', 'đang không xem': 'not viewing', 'ảnh chụp': 'screenshots',
    'TV chạy liên tục': 'Uptime', 'Bộ nhớ trong': 'Internal storage', 'không kết nối': 'not connected', 'Tín hiệu': 'Signal', 'Tốc độ kết nối': 'Link speed', 'Băng tần': 'Band',
    'IP Tailscale': 'Tailscale IP', 'IP dây mạng': 'Ethernet IP', 'IP trong nhà': 'Home IP', 'Máy': 'Device', 'Bản vá bảo mật': 'Security patch', 'Kiến trúc': 'Architecture',
    'Số nhân CPU': 'CPU cores', 'Mật độ điểm ảnh': 'Pixel density',
    'Màn hình': 'Screen', 'Đánh thức': 'Wake', 'Đánh thức TV (Wake-on-LAN khi TV standby sâu)': 'Wake the TV (Wake-on-LAN when it is in deep standby)',
    'Chạy lệnh': 'Run', 'Chạy lệnh với quyền shell của TV (như adb shell). Mỗi lệnh chạy riêng, tối đa 30 giây; lệnh chờ gõ tiếp như top, logcat không dùng được. Phím ↑ ↓ gọi lại lệnh cũ.': 'Runs commands as the TV’s shell user (like adb shell). Each command runs on its own, for at most 30 seconds; interactive ones such as top or logcat do not work. ↑ ↓ recall earlier commands.',
    'Mở Google Assistant trên TV': 'Open Google Assistant on the TV', 'Hỏi / tìm trên TV, vd: mở YouTube': 'Ask / search on the TV, e.g. open YouTube', 'Nói bằng micro của máy này': 'Speak into this computer’s microphone',
    'Gửi câu này cho Google Assistant của TV': 'Send this sentence to the TV’s Google Assistant', 'trình duyệt chưa được phép dùng micro': 'the browser is not allowed to use the microphone', 'TV này không có tìm kiếm / Assistant của Google': 'This TV has no Google search / Assistant',
    'Đang đọc từ TV...': 'Reading from the TV...',
    // about
    'Giới thiệu': 'About', 'Điều khiển Android TV / Google TV từ máy tính và điện thoại: nút remote, xem màn hình, ứng dụng, tệp, cài APK.': 'Control an Android TV / Google TV from a computer and a phone: remote buttons, live screen, apps, files, APK install.',
    'Tác giả:': 'Author:', 'Mã nguồn:': 'Source code:', 'Các bản phát hành': 'Releases', 'Nhật ký thay đổi': 'Change log', 'Báo lỗi': 'Report a problem', 'Đóng': 'Close',
    'Hình trực tiếp và chuột dùng': 'The live picture and mouse use', '(Genymobile, Apache-2.0). Nút remote dùng thư viện androidtvremote2.': '(Genymobile, Apache-2.0). The remote buttons use the androidtvremote2 library.',
    // quit
    'Tắt chương trình remote trên máy tính này?\nTV không bị ảnh hưởng. Muốn dùng lại thì bấm đúp start-mac.command / start-windows.cmd.': 'Stop the remote program on this computer?\nThe TV is not affected. To use it again, double-click start-mac.command / start-windows.cmd.',
    'Đã tắt': 'Stopped', 'Chương trình remote trên máy tính này đã dừng. Bấm đúp start-mac.command (Windows: start-windows.cmd) để mở lại. Có thể đóng tab này.': 'The remote program on this computer has stopped. Double-click start-mac.command (Windows: start-windows.cmd) to start it again. You can close this tab.',
    // TV tab
    'TV đã lưu': 'Saved TVs', 'Tìm TV': 'Find a TV', 'Quét mạng': 'Scan', 'Đang quét...': 'Scanning...', 'Chọn': 'Choose', 'Dùng': 'Use', 'đang dùng': 'in use', 'cùng mạng': 'same network', 'qua Tailscale': 'via Tailscale',
    'Tìm TV trong cùng Wi-Fi với máy này và các thiết bị Android trong Tailscale của bạn.': 'Looks for TVs on the same Wi-Fi as this computer and Android devices on your Tailscale network.',
    'Hoặc nhập IP của TV, vd 192.168.1.88': 'Or type the TV’s IP, e.g. 192.168.1.88', 'Xem IP trên TV: Cài đặt → Mạng và Internet → bấm vào tên Wi-Fi đang kết nối.': 'To see the IP on the TV: Settings → Network & Internet → select the connected Wi-Fi.',
    'Chưa có TV nào. Bấm “Quét mạng” hoặc nhập IP ở bên cạnh.': 'No TVs yet. Press “Scan” or type an IP.', 'Không thấy TV nào: TV có bật và cùng Wi-Fi với máy này không? Hoặc nhập IP bên dưới.': 'No TV found: is it on and on the same Wi-Fi as this computer? Or type its IP below.',
    'chỉ có ADB': 'ADB only', 'ADB đã bật': 'ADB on', 'ADB chưa bật': 'ADB off', 'Cài đặt lần đầu ·': 'First-time setup ·', 'Kiểm tra lại': 'Check again', 'chưa chọn TV': 'no TV chosen',
    'Chọn một TV ở trên (Quét mạng hoặc nhập IP) để bắt đầu.': 'Choose a TV above (Scan, or type an IP) to begin.',
    'ADB đã kết nối': 'ADB connected', 'Máy này đã được TV cho phép.': 'The TV has allowed this computer.', 'TV đang chờ bạn cho phép': 'The TV is waiting for your permission',
    'Nhìn lên TV: tích “Luôn cho phép từ máy tính này” rồi chọn OK.': 'On the TV: tick “Always allow from this computer”, then OK.', 'Đã bấm, thử lại': 'Done, retry',
    'Bật “Gỡ lỗi ADB” trên TV': 'Turn on “ADB debugging” on the TV',
    'Trên TV: Cài đặt → Tùy chọn thiết bị → Giới thiệu → bấm OK 7 lần vào “Bản dựng”. Rồi Tùy chọn nhà phát triển → bật “Gỡ lỗi ADB” (hoặc “Gỡ lỗi USB”). Việc này phải làm bằng remote của TV.': 'On the TV: Settings → Device Preferences → About → press OK 7 times on “Build”. Then Developer options → turn on “ADB debugging” (or “USB debugging”). This has to be done with the TV’s own remote.',
    'Đã bật, kết nối': 'It is on, connect', 'Tailscale (để điều khiển khi không ở nhà)': 'Tailscale (to control the TV away from home)',
    'Làm sau khi ADB đã kết nối. Chỉ dùng trong cùng Wi-Fi thì bỏ qua bước này được.': 'Do this once ADB is connected. Skip it if you only use the TV on the same Wi-Fi.',
    'Tailscale đã vào mạng': 'Tailscale is connected', 'Cài Tailscale lên TV': 'Install Tailscale on the TV', 'Cài Tailscale': 'Install Tailscale',
    'Để điều khiển TV khi không ở cùng mạng. Tải bản chính thức (khoảng 100 MB), có kiểm checksum. Chỉ dùng trong cùng Wi-Fi thì bỏ qua được.': 'To control the TV from another network. Downloads the official build (about 100 MB) and verifies its checksum. Skip it if you only use the TV on the same Wi-Fi.',
    'Thêm TV vào Tailscale của bạn': 'Add the TV to your Tailscale network',
    'Mở Tailscale trên TV rồi xem màn hình TV ngay tại đây: TV hiện một mã (và mã QR). Không cần gõ tài khoản trên TV: mở login.tailscale.com/admin/machines trên máy này hoặc điện thoại → Add device → nhập mã đó (hoặc quét QR bằng điện thoại).': 'Open Tailscale on the TV and watch its screen here: the TV shows a code (and a QR code). No need to type an account on the TV: open login.tailscale.com/admin/machines on this computer or a phone → Add device → enter that code (or scan the QR with a phone).',
    'Mở Tailscale trên TV': 'Open Tailscale on the TV', 'Đã thêm, kiểm tra': 'Added, check', 'Nút remote đã ghép': 'Remote buttons paired', 'Các nút điều khiển dùng được.': 'The remote buttons work.',
    'Mã 6 ký tự trên TV': '6-character code on the TV', 'Ghép nút remote': 'Pair the remote buttons', 'TV đang hiện mã 6 ký tự. Gõ mã vào đây.': 'The TV is showing a 6-character code. Type it here.',
    'Bấm “Ghép”, TV sẽ hiện mã 6 ký tự.': 'Press “Pair”; the TV will show a 6-character code.', 'Xác nhận': 'Confirm', 'Đã ghép': 'Paired', 'Lấy mã mới': 'Get a new code', 'Ghép': 'Pair', 'Nút remote': 'Remote buttons', 'Thử ghép': 'Try pairing',
    'Chưa kết nối được tới dịch vụ remote của TV (cổng 6466): TV đang bật và cùng mạng chứ? Thiết bị này có phải Android TV / Google TV không?': 'Cannot reach the TV’s remote service (port 6466): is the TV on and on the same network? Is this device an Android TV / Google TV?',
    'Lần sau mở lên là dùng TV này.': 'This TV is used the next time you start.', 'Đã ghi địa chỉ MAC để đánh thức TV.': 'Its MAC address is recorded for waking it.',
    'Lần sau mở lên là dùng TV này. Đã ghi địa chỉ MAC để đánh thức TV.': 'This TV is used the next time you start. Its MAC address is recorded for waking it.',
    'Tên gọi, vd TV phòng khách': 'A name, e.g. Living-room TV', 'Lưu và chuyển sang IP Tailscale': 'Save and switch to the Tailscale IP', 'Lưu TV này': 'Save this TV', 'Lưu': 'Save', 'Đã lưu': 'Saved',
    'Để lần sau mở lên là dùng ngay, không phải tìm lại.': 'So it is ready the next time you start, without searching again.', 'Tối ưu TV (tùy chọn)': 'Optimise the TV (optional)',
    
    'Giữ Tailscale sống, giảm hiệu ứng, thay màn hình chính bằng FLauncher, tắt app hệ thống không dùng. Hoàn tác được.': 'Keep Tailscale alive, reduce animations, replace the home screen with FLauncher, turn off unused system apps. Can be undone.',
    // help
    'Chức năng nào cần ADB (cổng 5555)?': 'Which features need ADB (port 5555)?', '✅ Không cần ADB, chỉ cần Remote (cổng 6466)': '✅ No ADB needed, just the remote (port 6466)',
    'Các nút điều khiển: mũi tên, OK, Back, Home, nguồn, tắt tiếng': 'Remote buttons: arrows, OK, Back, Home, power, mute', 'Âm lượng (không có ADB thì thanh trượt chỉnh từng nấc, chậm hơn)': 'Volume (without ADB the slider moves one step at a time, slower)',
    'Đánh thức TV khi standby (Wake-on-LAN qua laptop ở nhà)': 'Waking the TV from standby (Wake-on-LAN through a computer at home)', '🔌 Cần ADB, mất khi ADB đóng': '🔌 Needs ADB, lost when ADB is closed',
    'Xem màn hình TV (trực tiếp và ảnh chụp)': 'The TV screen (live and screenshots)', 'Chuột trên hình (bấm, kéo, cuộn; chuột phải = Back) và gõ bằng bàn phím máy tính': 'Mouse on the picture (click, drag, scroll; right-click = Back) and typing with the computer keyboard',
    'Mở app, mở link (TV này bỏ qua lệnh mở app gửi qua Remote)': 'Opening apps and links (this TV ignores the remote protocol’s launch command)', 'Danh sách app kèm logo, dừng / gỡ app': 'App list with icons, stop / uninstall apps',
    'Duyệt, tải về, đổi tên, xóa tệp trên TV; mở tệp trên TV': 'Browse, download, rename, delete files on the TV; open a file on the TV', 'Tải file từ link về TV, cài APK': 'Download a file from a link to the TV, install APKs',
    'ADB đóng khi nào?': 'When is ADB closed?', 'TV tắt hẳn hoặc đang standby sâu (mất Wi-Fi), TV vừa khởi động lại và chưa mở lại cổng, hoặc đã tắt “Gỡ lỗi ADB”. Chức năng Remote vẫn dùng được nếu TV còn mạng.': 'The TV is fully off or in deep standby (no Wi-Fi), it has just restarted and not reopened the port, or “ADB debugging” was turned off. The remote functions still work if the TV is online.',
    'Bật lại:': 'To turn it back on:', 'Cài đặt → Tùy chọn thiết bị → Giới thiệu → bấm OK 7 lần vào “Bản dựng” → Tùy chọn nhà phát triển → bật “Gỡ lỗi ADB”. Lần đầu TV hỏi cho phép thì chọn “Luôn cho phép”.': 'Settings → Device Preferences → About → press OK 7 times on “Build” → Developer options → turn on “ADB debugging”. The first time the TV asks, choose “Always allow”.',
    // server messages that are in Vietnamese
    'sai mã, thử lại': 'wrong code, try again',
  };

  // Strings with a variable part. Captured pieces are passed through tr() again where they can be text of ours.
  const RULES = [
    [/^Lỗi: ([\s\S]*)$/, (m) => 'Error: ' + tr(m[1])],
    [/^Xong: ([\s\S]*)$/, (m) => 'Done: ' + tr(m[1])],
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
    [/^Đã mở trên TV: (.*)$/, (m) => 'Opened on the TV: ' + m[1]],
    [/^Đã mở (.*)$/, (m) => 'Opened ' + tr(m[1])],
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
    [/^(Đã quét .+|Không thấy mạng nội bộ nào) và Tailscale\. ?(.*)$/, (m) => (m[1].startsWith('Đã quét') ? 'Scanned ' + m[1].slice(8) : 'No local network found') + ' and Tailscale. ' + tr(m[2])],
    [/^IP Tailscale của TV: (\S+)\. Nên làm thêm: trong trang quản trị Tailscale, mở menu ⋯ của TV → Disable key expiry, để TV không bị đăng xuất sau khoảng 180 ngày\.$/, (m) => 'The TV’s Tailscale IP: ' + m[1] + '. Recommended: in the Tailscale admin console open the TV’s ⋯ menu → Disable key expiry, so the TV is not signed out after about 180 days.'],
    [/^Đã lưu: (.*)$/, (m) => 'Saved: ' + m[1]],
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
    if (out === null && k.includes(' · ')) {                          // "a · b · c": translate the pieces we know
      const parts = k.split(' · ').map((p) => core(p.trim()));
      if (parts.some((p) => p !== null)) out = k.split(' · ').map((p, i) => parts[i] === null ? p : parts[i]).join(' · ');
    }
    return out === null ? s : s.replace(k, () => out);
  }
  window.tr = tr;

  const ATTRS = ['placeholder', 'title', 'aria-label', 'alt'];
  function visit(node) {
    if (node.nodeType === 3) { const v = node.nodeValue, t = tr(v); if (t !== v) node.nodeValue = t; return; }
    if (node.nodeType !== 1 || node.tagName === 'SCRIPT' || node.tagName === 'STYLE') return;
    for (const a of ATTRS) { const v = node.getAttribute(a); if (v) { const t = tr(v); if (t !== v) node.setAttribute(a, t); } }
    for (const c of node.childNodes) visit(c);
  }
  const start = () => {
    visit(document.body);
    new MutationObserver((list) => {
      for (const m of list) {
        if (m.type === 'characterData') visit(m.target);
        else if (m.type === 'attributes') { const v = m.target.getAttribute(m.attributeName); if (v) { const t = tr(v); if (t !== v) m.target.setAttribute(m.attributeName, t); } }
        else m.addedNodes.forEach(visit);
      }
    }).observe(document.body, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: ATTRS });
  };
  if (document.body) start(); else document.addEventListener('DOMContentLoaded', start);

  const nativeConfirm = window.confirm.bind(window), nativePrompt = window.prompt.bind(window);
  window.confirm = (msg) => nativeConfirm(tr(msg));
  window.prompt = (msg, def) => nativePrompt(tr(msg), def);
})();
