# Changelog / Nhật ký thay đổi

Ba số phiên bản, mỗi số chỉ tăng khi phần của nó đổi: `VERSION` (bản phát hành và app máy tính), `app/android/VERSION` (app điện thoại), `app/tv/VERSION` (app TV). Bản phát hành kèm file cài nằm ở [Releases](https://github.com/HoangTran0410/tv-remote/releases); các app tự kiểm tra bản mới ở đó.

Three version numbers, each bumped only when its own part changes: `VERSION` (the release and the computer app), `app/android/VERSION` (phone app), `app/tv/VERSION` (TV app). Releases with the install files are on the [Releases](https://github.com/HoangTran0410/tv-remote/releases) page; the apps check there for updates.

## Unreleased

**Tiếng Việt**
- 📺 Điều khiển được TV LG (webOS) và Samsung (Tizen, từ 2016) bằng app điện thoại và app máy tính: phím, gõ chữ, mở app, Wake-on-LAN, cảnh. Việc chỉ Android TV làm được thì làm mờ, bấm vào để xem giới thiệu.

**English**
- 📺 LG (webOS) and Samsung (Tizen, 2016 on) TVs work in the phone app and the computer app: keys, typing, opening apps, Wake-on-LAN, scenes. What only Android TV can do is greyed out; press it to see what it is.

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
