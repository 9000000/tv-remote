# Changelog / Nhật ký thay đổi

Một số phiên bản chung cho cả dự án (web, app điện thoại, app TV), ghi trong file `VERSION`. Bản phát hành kèm file APK nằm ở [Releases](https://github.com/HoangTran0410/tv-remote/releases); hai app tự kiểm tra bản mới ở đó.

One version number for the whole project (web, phone app, TV app), kept in `VERSION`. Releases with the APK files are on the [Releases](https://github.com/HoangTran0410/tv-remote/releases) page; both apps check there for updates.

## 1.5.0 - 2026-10-13

**Tiếng Việt**
- App điện thoại: nút bật/tắt hình TV (góc trên bên phải màn remote, 🙈 / 📺). Tắt hình thì chỉ còn nút điều khiển, TV không phải nén hình nên không tốn thêm CPU. App nhớ lựa chọn này.
- Sửa lỗi: sau khi app điện thoại ngắt kết nối, tiến trình lấy hình có thể còn chạy sót trên TV và làm TV chậm dần. Giờ TV tự dừng nó trong vòng 25 giây kể cả khi app bị tắt đột ngột hay mất mạng.

**English**
- Phone app: a switch for the TV picture (top right of the remote screen, 🙈 / 📺). With the picture off only the buttons remain and the TV encodes nothing, so it costs the TV no extra CPU. The choice is remembered.
- Fix: after the phone app disconnected, the picture process could keep running on the TV and slow it down over time. The TV now stops it within 25 seconds, also when the app is killed or the network drops.

## 1.4.0 - 2026-10-13

**Tiếng Việt**
- Màn Giới thiệu: tác giả, link GitHub, bản phát hành, báo lỗi, ghi công scrcpy. Web: nút ℹ️ trên đầu trang. App điện thoại: ⚙️ → Giới thiệu. App TV: dòng dưới tiêu đề.

**English**
- About screen: author, GitHub link, releases, issue reporting, scrcpy credit. Web: the ℹ️ button in the header. Phone app: ⚙️ → About. TV app: a line under the title.

## 1.3.1 - 2026-10-13

- App điện thoại: hộp thoại "Bản mới" không còn hiện ký hiệu Markdown `**` trong ghi chú phát hành. / Phone app: the "New version" dialog no longer shows Markdown `**` marks in the release notes.

## 1.3.0 - 2026-10-13

**Tiếng Việt**
- App điện thoại và app TV tự kiểm tra bản mới trên GitHub và cập nhật ngay trong app (điện thoại: Android hỏi xác nhận cài; TV: cài qua ADB của chính TV). Tắt được trong Cài đặt của app điện thoại.
- Web: thêm emoji cho tab, tiêu đề và nút; màn hình hẹp thì chỗ chật chỉ hiện emoji.
- Web: hỏi lại trước khi tắt TV bằng nút Nguồn.

**English**
- The phone app and the TV app check GitHub for a new version and update from inside the app (phone: Android asks to confirm the install; TV: installed through the TV's own ADB). Can be turned off in the phone app's Settings.
- Web: emoji on tabs, headings and buttons; on narrow screens tight spots show the emoji only.
- Web: asks before turning the TV off with the Power button.

## 1.2 - 2026-10-13

- App điện thoại: phần lớn nút dùng icon thay cho chữ. / Phone app: icons instead of words on most buttons.

## 1.1 - 2026-10-13

- App điện thoại: nút Đánh thức cho TV đã lưu (bật màn hình qua ADB khi TV còn mạng, Wake-on-LAN khi cùng Wi-Fi). / Phone app: Wake button for saved TVs (screen on over ADB while the TV is still online, Wake-on-LAN on the same Wi-Fi).
- App điện thoại: màn Cài đặt, chọn Tiếng Việt / English. / Phone app: Settings screen with a language choice.

## 1.0 - 2026-10-13

- Web remote chạy trên máy tính: nút điều khiển, xem màn hình TV và bấm chuột lên hình, ứng dụng, tệp, cài APK, tìm và cài đặt TV lần đầu, Tiếng Việt / English.
- App TV Setup (cài trên TV) và app TV Remote cho điện thoại Android.
- Web remote on a computer: remote buttons, live TV screen with mouse, apps, files, APK install, finding and first-time setup of a TV, Vietnamese / English.
- TV Setup app (on the TV) and the TV Remote app for Android phones.
