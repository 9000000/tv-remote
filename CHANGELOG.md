# Changelog / Nhật ký thay đổi

Ba số phiên bản, mỗi số chỉ tăng khi phần của nó đổi: `VERSION` (bản phát hành và app máy tính), `app/android/VERSION` (app điện thoại), `app/tv/VERSION` (app TV). Bản phát hành kèm file cài nằm ở [Releases](https://github.com/HoangTran0410/tv-remote/releases); các app tự kiểm tra bản mới ở đó.

Three version numbers, each bumped only when its own part changes: `VERSION` (the release and the computer app), `app/android/VERSION` (phone app), `app/tv/VERSION` (TV app). Releases with the install files are on the [Releases](https://github.com/HoangTran0410/tv-remote/releases) page; the apps check there for updates.

## Unreleased

**Tiếng Việt**
- 📱 App điện thoại: Quét Wi-Fi đi thẳng ra Wi-Fi khi Tailscale (hay VPN khác) đang bật, để vẫn tìm được TV trong nhà. Chỉ có 4G thì báo chưa nối Wi-Fi, không quét nhầm mạng của nhà mạng.
- Mất kết nối với TV giữa chừng (vd lúc cập nhật TV Setup) giờ báo bằng lời dễ hiểu thay vì “stream closed”.
- 📱 App điện thoại: chép tệp, cài app, cập nhật TV Setup vẫn chạy khi chuyển sang app khác (có thông báo); rời TV giữa chừng thì hỏi trước.

**English**
- 📱 Phone app: Scan the Wi-Fi goes straight onto the Wi-Fi while Tailscale (or another VPN) is on, so TVs at home are still found. On mobile data only, it says the phone is not on Wi-Fi instead of scanning the carrier's network.
- Losing the TV halfway (while updating TV Setup, say) is said in plain words instead of “stream closed”.
- 📱 Phone app: copying a file, installing an app or updating TV Setup goes on while you use another app (with a notification); leaving the TV halfway asks first.

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
