# Changelog / Nhật ký thay đổi

Có ba số phiên bản, mỗi số chỉ tăng khi phần của nó đổi: `VERSION` (bản phát hành và web trên máy tính), `app/android/VERSION` (app điện thoại), `app/tv/VERSION` (app TV). Tiêu đề mỗi mục dưới đây là số của bản phát hành. Bản phát hành kèm file APK nằm ở [Releases](https://github.com/HoangTran0410/tv-remote/releases); hai app tự kiểm tra bản mới ở đó.

Three version numbers, each bumped only when its own part changes: `VERSION` (the release and the web UI on the computer), `app/android/VERSION` (phone app), `app/tv/VERSION` (TV app). The headings below are release numbers. Releases with the APK files are on the [Releases](https://github.com/HoangTran0410/tv-remote/releases) page; both apps check there for updates.

## 2.3.0 - 2026-10-13

Web 2.3.0 · app điện thoại 2.3.0 · app TV 2.3.0

**Tiếng Việt**
- Hai app Android (điện thoại và TV) viết lại bằng **Kotlin**, build bằng Gradle; mở thư mục `app/` bằng Android Studio là làm việc được. Chức năng và giao diện giữ nguyên.
- Danh sách ứng dụng và tệp gọn hơn: mỗi dòng còn một nút chính và nút **⋯**; bấm ⋯ mới hiện bảng chọn thao tác. Logo nhỏ lại.
- Nút **Cài đặt của TV** giờ mở thẳng màn Cài đặt của TV (trước đây gửi phím "settings", nhiều TV không phản hồi).

**English**
- Both Android apps (phone and TV) are rewritten in **Kotlin** and built with Gradle; open the `app/` folder in Android Studio to work on them. Features and looks are unchanged.
- Tidier app and file lists: each row keeps one main button and a **⋯** button; the other actions appear in a sheet when ⋯ is tapped. Smaller icons.
- The **TV settings** button now opens the TV's Settings screen directly (it used to send the "settings" key, which many TVs ignore).

## 2.2.0 - 2026-10-13

Web 2.2.0 · app điện thoại 2.2.0 · app TV 2.2.0

**Tiếng Việt**
- App điện thoại và trang remote do TV phục vụ có thêm phần **quản lý TV**: Ứng dụng (mở, dừng, gỡ), Tệp (duyệt, tải về, gửi lên, đổi tên, xóa, mở trên TV, cài file .apk), Cài APK từ link hoặc từ máy, tải file từ link về TV, Sức khỏe TV. Trên điện thoại: bấm nút ô lưới ở màn remote.
- Tab **Terminal** ở cả ba nơi (web máy tính, app điện thoại, trang trên TV): chạy lệnh với quyền shell của TV.
- **Google Assistant:** nút micro mở Assistant của TV. Thêm "hỏi TV": gõ hoặc nói một câu (vd "mở YouTube") rồi gửi cho Assistant của TV. Trên điện thoại: bấm nút micro để nói, giữ lâu để mở Assistant của TV. Trên web máy tính: ô "Hỏi / tìm trên TV" và nút 🎤 (Chrome).
- Sửa lỗi: app đôi khi báo "refused" ngay khi kết nối dù TV vẫn bình thường.
- Sửa lỗi: tải file lớn từ TV không còn dồn hết vào bộ nhớ khi máy nhận chậm.

**English**
- The phone app and the remote page served by the TV gain **TV management**: Apps (open, stop, uninstall), Files (browse, download, upload, rename, delete, open on the TV, install an .apk), APK install from a link or from the device, download a link to the TV, TV health. On the phone: tap the grid button on the remote screen.
- A **Terminal** tab in all three places (web on the computer, phone app, the page on the TV): run commands as the TV's shell user.
- **Google Assistant:** the microphone button opens the TV's Assistant. New "ask the TV": type or say a sentence (e.g. "open YouTube") and it is handed to the TV's Assistant. On the phone: tap the microphone to speak, long-press to open the TV's Assistant. On the computer web: the "Ask / search on the TV" box and the 🎤 button (Chrome).
- Fix: the app sometimes reported "refused" right after connecting although the TV was fine.
- Fix: downloading a large file from the TV no longer piles up in memory when the receiver is slow.

## 2.1.0 - 2026-10-13

Web 2.1.0 · app điện thoại 2.1.0 · app TV 2.1.0

**Tiếng Việt**
- Sắp xếp lại thư mục: `app/web` (web trên máy tính), `app/android` (app điện thoại), `app/tv` (app TV), `app/shared` (dùng chung), `dev` (công cụ phát hành). Thư mục gốc chỉ còn ba file để bấm: `start-mac.command`, `start-windows.cmd`, `start-linux.sh`. Ai đang dùng file `start.command` / `start.cmd` cũ thì đổi sang tên mới; `install-windows.cmd` đã gộp vào `start-windows.cmd`.
- Mỗi app có số phiên bản riêng. Từ bản này, app chỉ báo cập nhật khi chính nó có thay đổi.

**English**
- Folders reorganised: `app/web` (web on the computer), `app/android` (phone app), `app/tv` (TV app), `app/shared`, `dev` (release tools). The top folder has only three files to click: `start-mac.command`, `start-windows.cmd`, `start-linux.sh`. If you used the old `start.command` / `start.cmd`, switch to the new names; `install-windows.cmd` is now part of `start-windows.cmd`.
- Each app has its own version number. From this release on, an app only offers an update when it changed itself.

## 2.0.0 - 2026-10-13

**Tiếng Việt**
- **Remote qua web chạy ngay trên TV.** App TV Setup giờ tự mở một trang remote ở `http://<IP của TV>:8765`. Điện thoại hay máy tính nào cũng mở được bằng trình duyệt, không cần cài gì và không cần máy tính bật. Lần đầu TV hiện mã 6 số để ghép. Có nút điều khiển (giữ nút = nhấn giữ), âm lượng, gõ chữ, mở app kèm logo. Tự chạy lại khi TV khởi động. Bật/tắt và hủy ghép ngay trong app trên TV.
- Web trên máy tính: giao diện remote mới (vòng điều hướng tròn, bộ icon nét mảnh) và tab **Sức khỏe**: nguồn, app đang mở, âm lượng, độ trễ, luồng hình, CPU, RAM, bộ nhớ, tiến trình tốn CPU, Wi-Fi, phần cứng.
- App điện thoại: màn remote mới cùng kiểu; màn đầu gọn lại, hướng dẫn kết nối chỉ hiện khi bấm "Xem cách kết nối".

**English**
- **A web remote served by the TV itself.** The TV Setup app now serves a remote page at `http://<TV's IP>:8765`. Any phone or computer opens it in a browser, with nothing to install and no computer left running. The first time, the TV shows a 6-digit code to pair. It has the remote buttons (hold = long press), volume, typing, and apps with their logos. It starts again when the TV boots. Turn it on/off and unpair devices in the app on the TV.
- Web on the computer: a new remote layout (round D-pad, one set of line icons) and a **Health** tab: power, current app, volume, latency, stream, CPU, RAM, storage, top processes, Wi-Fi, hardware.
- Phone app: the same new remote layout; a cleaner first screen, with the connection guide shown only when you tap "How to connect".

## 1.6.0 - 2026-10-13

**Tiếng Việt**
- App điện thoại: chọn chất lượng hình trong ⚙️ để tiết kiệm băng thông: Tiết kiệm (480 px, tối đa khoảng 40 KB/s), Vừa (720 px, khoảng 100 KB/s, mặc định mới), Nét (960 px, khoảng 250 KB/s, như trước đây). Hình không bao giờ gửi lớn hơn màn hình điện thoại.
- App điện thoại: dòng trạng thái hiện lượng dữ liệu đang dùng thật (KB/s).

**English**
- Phone app: picture quality under ⚙️ to save data: Data saver (480 px, up to about 40 KB/s), Balanced (720 px, about 100 KB/s, the new default), Sharp (960 px, about 250 KB/s, as before). The picture is never sent larger than the phone's screen.
- Phone app: the status line shows the data actually being used (KB/s).

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
