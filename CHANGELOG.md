# Changelog / Nhật ký thay đổi

Ba số phiên bản, mỗi số chỉ tăng khi phần của nó đổi: `VERSION` (bản phát hành và app máy tính), `app/android/VERSION` (app điện thoại), `app/tv/VERSION` (app TV). Bản phát hành kèm file cài nằm ở [Releases](https://github.com/HoangTran0410/tv-remote/releases); các app tự kiểm tra bản mới ở đó.

Three version numbers, each bumped only when its own part changes: `VERSION` (the release and the computer app), `app/android/VERSION` (phone app), `app/tv/VERSION` (TV app). Releases with the install files are on the [Releases](https://github.com/HoangTran0410/tv-remote/releases) page; the apps check there for updates.

## Unreleased

**Tiếng Việt**
- 📺 TV Setup → Quản lý trên TV: mở luôn giao diện quản lý (Ứng dụng, Tệp, Sức khỏe, Tự động, Thiết lập) trên màn hình TV, điều khiển bằng remote của TV, không cần điện thoại hay máy tính. Chạy được cả khi đã tắt remote qua web.
- 📲 Chia sẻ (Thêm phím → Chia sẻ): mã QR cho điện thoại khác quét là vào thẳng remote của TV, không cần gõ mã (dùng 1 lần, trong 10 phút), kèm mã QR tải app điện thoại. Đang nối TV qua Tailscale thì có cảnh báo; TV chưa có TV Setup thì chỉ hiện mã tải app. Cần TV Setup mới.
- ▶️ Đang phát: bấm tạm dừng / chạy tiếp / qua bài thì nút quay chờ tới khi TV làm xong; bấm liên tục không còn làm nút và TV lệch nhau.
- Tự động: lúc TV Setup đang cập nhật, trang không còn báo nhầm "TV Setup là bản cũ" kèm nút Cập nhật; nó chờ TV Setup chạy lại rồi tự hiện.

**English**
- 📺 TV Setup → Manage on this TV: the manage screens (Apps, Files, Health, Automation, Setup) right on the TV, driven with the TV's remote, no phone or computer needed. Works with the web remote turned off too.
- 📲 Share (More keys → Share): a QR code another phone scans to open the TV's remote straight away, no code to type (once, within 10 minutes), with a QR code for the phone app. A warning when this device reaches the TV through Tailscale; without TV Setup only the app's code shows. Needs the new TV Setup.
- ▶️ Now playing: pause / play / next show a spinner until the TV has done it; pressing fast no longer leaves the button and the TV out of step.
- Automation: while TV Setup is being updated, the page no longer says "TV Setup is too old" with an Update button; it waits for TV Setup to start again and shows itself.

## 0.4.1 - 2026-10-16

Web 0.4.1 · app điện thoại 0.4.1 · app TV 0.4.1

**Tiếng Việt**
- Cài đặt: các phần thẳng một lề trái; nút Theo máy / Sáng / Tối có icon. 📱 App điện thoại: icon nét thay emoji trong Cài đặt, Giới thiệu và các nút; icon trên nền xanh luôn trắng (nút ẩn hình, 🎧, Thêm phím). Chữ xám, xanh, xanh lá đậm hơn trên giao diện sáng cho dễ đọc.
- 📱 App điện thoại: chép tệp, cài app, cập nhật TV Setup giờ hiện trên thanh thông báo kèm phần trăm (trước đó Android 13+ ẩn đi vì app chưa xin quyền thông báo; giờ xin ngay lúc việc bắt đầu).
- Sức khỏe → Lịch sử: chú thích dưới biểu đồ gọn lại như trước. TV có đồng hồ từng nhảy sai (vd box FPT Play) giờ vẫn ghi được app đang mở; khoảng đã ghi trước đó thì không có tên app.
- Tab Tệp: đổi tên, xóa, tải về nằm trong nút ⋯ (mỗi tệp một dòng trên điện thoại); các thư mục nhanh nằm một dòng vuốt ngang, đường dẫn gọn.
- Sức khỏe → Lịch sử: bảng dưới biểu đồ hiện sẵn số liệu mới nhất, chạm vào biểu đồ để xem lúc khác.
- 📱 🎧 Nghe tiếng TV chạy trên kết nối riêng: đưa app xuống nền, mở lại, đổi chất lượng hình không còn làm tiếng ngắt vài giây; hình không còn làm nghẽn tiếng khi mạng chậm (4G, Tailscale). Qua Tailscale tiếng giữ sẵn 0,4 giây ngay từ đầu (trễ hơn hình một chút, đổi lại bớt giật). Khi mạng khựng, app chờ gom đủ tiếng rồi mới phát tiếp: một khoảng ngắt gọn thay vì tiếng lụp bụp.
- Thiết lập → Lọc DNS → Tự nhập: gõ IP quen thuộc (8.8.8.8, 1.1.1.1, 9.9.9.9, AdGuard) cũng được, app đổi sang tên máy chủ tương ứng, vì Android chỉ nhận tên.

- 🌙 Giờ đi ngủ có nhiều khoảng giờ (vd 22:00–06:30 và 13:00–14:00 giờ ngủ trưa): thêm, xóa từng khoảng. Cần TV Setup mới.
- Lọc DNS → Tự nhập: chọn sẵn từ danh sách DNS nổi tiếng (Google, Cloudflare, Quad9, AdGuard, Mullvad, Control D, NextDNS), có ghi rõ nhà cung cấp, tên máy chủ và IP. Rê chuột lên “Chặn quảng cáo” / “Gia đình” để xem dùng DNS của ai.
- Các tab (trừ Remote) chung một khuôn: thẻ nào cũng mở bằng tiêu đề, khoảng cách, cỡ nút và dòng chú thích giống nhau. Hộp Tối ưu TV không còn hiện chữ “null”.
- Tiêu đề, các tab và nút dùng icon nét thay cho emoji (mỗi tab một màu riêng), trông giống nhau trên mọi máy, kể cả TV cũ thiếu emoji.
- Cập nhật an toàn hơn: mọi bản cập nhật (app điện thoại, TV Setup, app máy tính) và lần cài TV Setup đầu tiên đều được kiểm SHA-256 theo danh sách có chữ ký của dự án; file không khớp bị xóa, không cài.
- Chữ dễ đọc hơn: nút và tab đang chọn màu xanh đậm hơn với chữ trắng, chữ xanh / xanh lá / cam / đỏ trên nền sáng đậm hơn.
- 🔒 Remote qua web của TV có HTTPS (cổng 8766, chứng chỉ tự ký): màn hình TV và mã QR hiện địa chỉ https cùng dấu vân tay ngắn để so với trình duyệt; cookie ghép nối ở đó có `Secure` và không dùng được qua HTTP. App điện thoại / máy tính nhận dấu vân tay qua ADB và chỉ nói chuyện với đúng chứng chỉ đó (sai thì từ chối, không lùi về HTTP). Cổng 8765 (HTTP) vẫn chạy cho máy cũ.
- 📱 Thiết bị đã ghép có hạn dùng: trình duyệt giữ 30 ngày kể từ lần dùng cuối (dùng thì tự gia hạn), TV chỉ lưu mã băm; Thiết lập → “Thiết bị đã ghép” liệt kê từng máy (tên, IP, lần cuối) để quên từng máy hoặc gỡ tất cả. Cần TV Setup mới.
- 🛠️ Nút Cài đặt trên đầu trang (app máy tính, web của TV) gộp Ngôn ngữ, Giới thiệu và ⏻ Tắt; thêm Giao diện: Theo máy / Sáng / Tối. App điện thoại có Giao diện Tự động / Sáng / Tối trong Cài đặt (màn remote cũng có giao diện sáng).
- TV Setup: “Thiết bị đã ghép (n)” mở danh sách từng máy, có giờ ghép, lần dùng cuối và nút Hủy ghép riêng; giờ đi ngủ / hẹn giờ nằm trong thẻ trạng thái; các ô Thao tác nhỏ gọn hơn. Pass của app điện thoại / máy tính mới chỉ dùng được qua HTTPS.

**English**
- Settings: every part on one left edge; System / Light / Dark have icons. 📱 Phone app: line icons instead of emoji in Settings, About and buttons; an icon on blue is always white (hide picture, 🎧, More keys). Grey, blue and green words darker on the light theme, easier to read.
- 📱 Phone app: copying a file, installing an app or updating TV Setup now shows in the notification shade with its percentage (Android 13+ hid it before, the app never having asked for notifications; it asks as the work starts).
- Health → History: the legend under the chart is compact again. A TV whose clock once jumped (the FPT Play box, say) records the open app again; what was recorded before has no app names.
- Files tab: rename, delete and download sit behind ⋯ (one line per file on a phone); the quick folders on one row you swipe, a tighter path.
- Health → History: the table under the chart shows the latest figures; touch the chart for another moment.
- 📱 🎧 Listening to the TV runs on a connection of its own: putting the app in the background, opening it again or changing the picture quality no longer cuts the sound for seconds, and the picture no longer holds up the sound on a slow network (4G, Tailscale). Through Tailscale 0.4 s of sound is kept queued from the start (a little behind the picture, much less stutter). When the network stalls, the app waits until enough sound is in before playing on: one clean gap instead of crackling.
- Setup → DNS filter → Type one: well-known IPs (8.8.8.8, 1.1.1.1, 9.9.9.9, AdGuard) work too, turned into their server names, as Android takes names only.
- 🌙 Bedtime takes several time ranges (e.g. 22:00–06:30 and a 13:00–14:00 nap): add or remove each. Needs the new TV Setup.
- DNS filter → Type one: pick from a list of well-known DNS (Google, Cloudflare, Quad9, AdGuard, Mullvad, Control D, NextDNS) showing the provider, server name and IP. Hover “Block ads” / “Family” to see whose DNS they use.
- The tabs (all but Remote) share one pattern: every card opens with its title, with the same spacing, button sizes and notes. The Optimize TV box no longer shows “null”.
- Titles, tabs and buttons use line icons instead of emoji (each tab its own colour), the same on every device, old TVs missing emoji included.
- Safer updates: every update (phone app, TV Setup, computer app) and the first install of TV Setup is checked against the project's signed list of SHA-256 checksums; a file that does not match is deleted, not installed.
- Easier to read: buttons and the selected tab are a deeper blue with white text; blue, green, orange and red words on the light theme are darker.
- 🔒 The TV's web remote has HTTPS (port 8766, self-signed certificate): the TV screen and QR show the https address with a short fingerprint to compare with the browser's; the pairing cookie there is `Secure` and does not work over HTTP. The phone / computer app learns the fingerprint over ADB and only talks to that exact certificate (a mismatch is refused, no fallback to HTTP). Port 8765 (HTTP) still runs for older devices.
- 📱 Paired devices expire: a browser lasts 30 days from its last use (renewed while used), and the TV keeps only a hash of each pass; Setup → “Paired devices” lists each one (name, IP, last seen) to forget one or remove all. Needs the new TV Setup.
- 🛠️ A Settings button at the top of the page (computer app, the TV's web page) holds Language, About and ⏻ Quit, plus a Theme: System / Light / Dark. The phone app has Appearance Auto / Light / Dark in Settings (the remote screen has a light theme too).
- TV Setup: “Paired devices (n)” opens a list of each device with when it was paired, when last used and its own Unpair; the bedtime / sleep timer sit in the status card; smaller action tiles. A pass of the new phone / computer app works over HTTPS only.

## 0.3.0 - 2026-10-16

Web 0.3.0 · app điện thoại 0.3.0 · app TV 0.3.0

**Tiếng Việt**
- 📱 App điện thoại: Quét Wi-Fi đi thẳng ra Wi-Fi khi Tailscale (hay VPN khác) đang bật, để vẫn tìm được TV trong nhà. Chỉ có 4G thì báo chưa nối Wi-Fi, không quét nhầm mạng của nhà mạng.
- Mất kết nối với TV giữa chừng (vd lúc cập nhật TV Setup) giờ báo bằng lời dễ hiểu thay vì “stream closed”.
- 📱 App điện thoại: chép tệp, cài app, cập nhật TV Setup vẫn chạy khi chuyển sang app khác (có thông báo); rời TV giữa chừng thì hỏi trước.
- Sức khỏe → Lịch sử: dải màu dưới biểu đồ RAM/CPU cho biết app nào đang mở, có vạch ở mỗi lần chuyển app, rê chuột hay chạm thì làm nổi khoảng app đó; lúc TV ngủ tô xám. Sóng Wi-Fi và các lần mất mạng (vạch đỏ) nằm chung biểu đồ đó; bấm vào chú thích để ẩn/hiện từng đường; số liệu tại điểm đang chỉ hiện thành bảng.
- Mất ADB giữa chừng (vd khi đưa app xuống nền): tab đang xem giữ nguyên dữ liệu và chỗ đang cuộn, chỉ phủ một lớp “Đang kết nối ADB…”; không nối lại được thì báo lỗi ngay trên lớp đó, kèm nút Thử lại.
- Các tab Ứng dụng, Tệp, Sức khỏe, Tự động, Thiết lập dùng chung một cỡ cho nút, ô nhập và lựa chọn; trên điện thoại ô lọc tệp có dòng riêng.
- Đang phát: app không gửi tên phim (vd FPT Play) giờ hiện tên app thay vì “null”, và tạm dừng không làm mất thanh nữa. Đang tạm dừng mà chuyển sang app khác thì thanh tự ẩn.
- Xem hình TV: video không chiếu lên được (chip của TV, vd box FPT Play, hay phim có bản quyền) thì báo ngay trên khung hình, thay vì để màn hình đen như bị lỗi.
- 📱 🎧 Nghe tiếng TV: hiện trong điều khiển media của điện thoại (thanh thông báo, màn hình khóa) với tên phim TV đang phát, ⏮ ⏯ ⏭ cho TV và ✕ để thôi nghe; app xin quyền thông báo lần đầu. Mạng chập chờn (4G, Tailscale) thì tự giữ nhiều tiếng hơn để bớt giật.

**English**
- 📱 Phone app: Scan the Wi-Fi goes straight onto the Wi-Fi while Tailscale (or another VPN) is on, so TVs at home are still found. On mobile data only, it says the phone is not on Wi-Fi instead of scanning the carrier's network.
- Losing the TV halfway (while updating TV Setup, say) is said in plain words instead of “stream closed”.
- 📱 Phone app: copying a file, installing an app or updating TV Setup goes on while you use another app (with a notification); leaving the TV halfway asks first.
- Health → History: a coloured strip under the RAM/CPU chart shows which app was open, with a line at each switch, and hovering or touching lights up that app's stretch; the TV's sleep is greyed out. The Wi-Fi signal and each network loss (a red band) are drawn on the same chart; press a legend entry to hide or show its line; the figures at the cursor show as a table.
- ADB dropping halfway (putting the app in the background, say): the open tab keeps its data and scroll position under a “Connecting ADB…” layer; if it cannot reconnect, the error and a Retry button show on that layer.
- The Apps, Files, Health, Automation and Setup tabs share one size for buttons, fields and choices; on a phone the file filter gets its own line.
- Now playing: an app that sends no title (FPT Play, say) shows its name instead of “null”, and pausing no longer hides the bar; a paused app left for another hides it.
- Watching the TV: when its video cannot be shown (the TV's chip, as on the FPT Play box, or a copy-protected film), the picture says so instead of staying black like a fault.
- 📱 🎧 Listening to the TV: shown in the phone's media controls (notification shade, lock screen) with what the TV plays, ⏮ ⏯ ⏭ for the TV and ✕ to stop listening; the app asks for notifications the first time. On a shaky network (4G, Tailscale) it keeps more sound queued so it stutters less.

## 0.2.1 - 2026-10-16

Web 0.2.1 · app điện thoại 0.2.0 · app TV 0.2.0

**Tiếng Việt**
- 💻 App máy tính: sửa lỗi không mở được khi chưa lưu TV nào (máy mới cài).

**English**
- 💻 Computer app: fixed it not starting when no TV was saved yet (a fresh install).

## 0.2.0 - 2026-10-16

Web 0.2.0 · app điện thoại 0.2.0 · app TV 0.2.0

**Tiếng Việt**
- 🧪 Thử nghiệm: điều khiển được TV LG (webOS) và Samsung (Tizen, từ 2016) bằng app điện thoại và app máy tính: phím, gõ chữ, mở app, Wake-on-LAN, cảnh; LG thêm chỉnh âm lượng theo số và tắt màn hình (nút hẹn giờ). Việc chỉ Android TV làm được thì làm mờ, bấm vào để xem giới thiệu. Đã đối chiếu với thư viện Home Assistant dùng (aiowebostv, samsungtvws) nhưng chưa thử trên TV thật.
- 📋 Gửi nhật ký (Giới thiệu, trong cả ba app và web của TV): app làm sai thì gửi các lệnh đã gửi tới TV, câu TV trả lời, kết nối ADB / remote / xem hình và các lỗi đã hiện; app máy tính và web của TV mở sẵn trang báo lỗi. Không có mã ghép đôi, địa chỉ MAC hay chữ đã gõ.
- Tab Remote: khi không xem hình, cột bên trái gọn lại cạnh remote thay vì kéo hết màn hình.

**English**
- 🧪 Beta: LG (webOS) and Samsung (Tizen, 2016 on) TVs work in the phone app and the computer app: keys, typing, opening apps, Wake-on-LAN, scenes; on LG also the volume as a number and the screen off (the sleep timer button). What only Android TV can do is greyed out; press it to see what it is. Checked against the libraries Home Assistant uses (aiowebostv, samsungtvws), not yet on a real TV.
- 📋 Send the log (About, in every app and the TV web page): when something goes wrong, send what was sent to the TV, what it answered, the ADB / remote / picture connections and the errors shown; the computer app and the TV web page open a bug report for you. No pairing keys, MAC addresses or typed text.
- Remote tab: with no picture showing, the left column keeps a sensible width beside the remote instead of filling the screen.

## 0.1.0 - 2026-10-16

Web 0.1.0 · app điện thoại 0.1.0 · app TV 0.1.0

Bản thử nghiệm đầu tiên. Máy đã cài bản 2.x trước đây: cài đè app mới một lần (Downloader `2973135` cho điện thoại, `2397346` cho TV), sau đó các app tự cập nhật.

**Tiếng Việt**
- 📱 App điện thoại (Android), 💻 app máy tính (Mac, Windows, Linux) và 🌐 web của TV (mở bằng mọi trình duyệt, cả iPhone, sau mã 6 số), nói chuyện thẳng với TV, không qua máy chủ nào.
- Remote đầy đủ: D-pad, âm lượng, kênh, phím số, phím media, Thêm phím; gửi phím được cả khi TV chưa cho phép ADB.
- Xem và chạm màn hình TV (scrcpy), chọn chất lượng; 🎧 nghe tiếng TV trên máy mình, cả khi tắt màn hình điện thoại.
- ✏️ Vẽ lên TV, 🖼️ đưa ảnh và 💬 tin nhắn lên TV; tắt màn hình TV mà vẫn phát tiếng.
- ▶️ Đang phát: tên bài/phim, tua, tạm dừng, qua bài; 📋 clipboard của TV.
- Gõ chữ từ điện thoại/máy tính; 🎤 lệnh bằng giọng nói.
- Mở ứng dụng trên TV bằng logo; Cảnh: một nút làm nhiều việc (widget, ô Cài đặt nhanh, lối tắt trên điện thoại).
- Tự động trên TV (chạy cả khi mọi máy khác tắt): hẹn giờ tắt, giờ đi ngủ, giới hạn app, 🔊 giới hạn âm lượng theo giờ.
- Quản lý: Ứng dụng (cài, gỡ, tắt), Tệp và cài APK, Sức khỏe (bộ nhớ, RAM, thống kê dùng app), Cài đặt TV.
- ⚡ Tối ưu TV (chọn việc cần làm, có Hoàn tác); TV Setup trên TV tự bật lại ADB, tắt DNS lọc làm TV mất mạng.
- Nhiều TV: tìm TV trong mạng, lưu nhiều TV, làm cùng lúc trên nhiều TV; bật TV đã tắt hẳn bằng Wake-on-LAN.
- Dùng khi ở ngoài nhà qua Tailscale (cài và đăng nhập trên TV bằng một nút, mã QR).
- "Cho phép gỡ lỗi USB?" được tự trả lời chỉ cho đúng máy của mình, không cần ai ngồi ở TV.
- Tiếng Việt và tiếng Anh; các app tự cập nhật từ GitHub.

**English**
- 📱 Phone app (Android), 💻 computer app (Mac, Windows, Linux) and 🌐 the TV web page (any browser, iPhone included, after a 6-digit code), talking straight to the TV with no server in between.
- A full remote: D-pad, volume, channels, number keys, media keys, More keys; keys work even before the TV allows ADB.
- See and touch the TV's screen (scrcpy), with a quality choice; 🎧 listen to the TV on your device, with the phone's screen off too.
- ✏️ Draw on the TV, 🖼️ put a picture or 💬 a message on it; turn the TV's screen off while the sound plays on.
- ▶️ Now playing: title, seek, pause, next; 📋 the TV's clipboard.
- Type from the phone or computer; 🎤 spoken commands.
- Open apps on the TV by their logos; Scenes: one button, several things (widget, Quick Settings tiles, shortcuts on the phone).
- Automation on the TV itself (works with every other device off): sleep timer, bedtime, app limits, 🔊 volume limits by time of day.
- Manage: Apps (install, uninstall, turn off), Files and APK installs, Health (storage, RAM, app usage), TV settings.
- ⚡ Optimize TV (pick what to do, with Undo); TV Setup on the TV turns ADB back on and turns off a DNS filter that cut the TV off.
- Several TVs: find TVs on the network, save several, act on many at once; wake a fully-off TV with Wake-on-LAN.
- Away from home over Tailscale (installed and signed in on the TV with one button and a QR code).
- "Allow USB debugging?" is answered automatically, only for your own device's key, with nobody at the TV.
- Vietnamese and English; the apps update themselves from GitHub.
