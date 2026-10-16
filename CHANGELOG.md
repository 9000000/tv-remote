# Changelog / Nhật ký thay đổi

Có ba số phiên bản, mỗi số chỉ tăng khi phần của nó đổi: `VERSION` (bản phát hành và web trên máy tính), `app/android/VERSION` (app điện thoại), `app/tv/VERSION` (app TV). Tiêu đề mỗi mục dưới đây là số của bản phát hành. Bản phát hành kèm file APK nằm ở [Releases](https://github.com/HoangTran0410/tv-remote/releases); hai app tự kiểm tra bản mới ở đó.

Three version numbers, each bumped only when its own part changes: `VERSION` (the release and the web UI on the computer), `app/android/VERSION` (phone app), `app/tv/VERSION` (TV app). The headings below are release numbers. Releases with the APK files are on the [Releases](https://github.com/HoangTran0410/tv-remote/releases) page; both apps check there for updates.

## Unreleased

**Tiếng Việt**
- ▶️ Đang phát: trên remote hiện TV đang phát gì (tên, kênh hoặc ca sĩ), kéo thanh để tua, bấm để tạm dừng hoặc qua bài. Có trên cả ba: app điện thoại, app máy tính, web của TV.
- 📋 Clipboard của TV (Mở trên TV → Clipboard của TV): xem chữ TV đang sao chép, chép về máy mình, gửi chữ lên TV hoặc dán vào ô đang gõ.
- Một TV lưu bằng cả IP nhà lẫn IP Tailscale giờ chỉ là một dòng; quét mạng cũng chỉ hiện TV đó một lần.
- Quét mạng: TV nào trả lời là hiện ngay, không chờ quét xong.
- Remote trên web có Thêm phím giống app điện thoại: Mở trên TV, Hẹn giờ tắt, Cảnh, Vẽ lên TV. 🎧 bắt đầu nghe ngay.
- Hộp FPT (hoặc hộp nhà mạng) có mật khẩu ADB: TV Setup tự mở Tùy chọn nhà phát triển để vượt qua.
- Đổi ngôn ngữ vẫn giữ TV, tab và vị trí đang xem.
- App điện thoại: ô nhập chữ trong các hộp thoại to và rõ hơn; nút Chia sẻ link tải app không còn bị hẹp.

**English**
- ▶️ Now playing: the remote shows what the TV is playing (title, channel or artist); drag the bar to seek, tap to pause or skip. On all three: the phone app, the computer app and the TV web page.
- 📋 The TV's clipboard (Open on the TV → The TV's clipboard): see the text the TV has copied, copy it to your device, send text to the TV or paste it where the TV is typing.
- A TV saved under both its home IP and its Tailscale IP is now one row; a scan shows it once too.
- Scan: each TV shows up as soon as it answers, without waiting for the whole scan.
- The web remote has More keys like the phone app: Open on the TV, Sleep timer, Scenes, Draw on the TV. 🎧 starts listening at once.
- FPT (or other operator) boxes with an ADB password: TV Setup opens Developer options itself to get past it.
- Switching the language keeps the TV, tab and scroll position.
- Phone app: text boxes in dialogs are bigger and clearer; the Share the download link button is no longer squashed.

## 2.24.2 - 2026-10-15

Web 2.24.2 · app điện thoại 2.22.2 · app TV 2.21.3

**Tiếng Việt**
- TV Setup tự bật lại Gỡ lỗi USB khi bị tắt (trẻ con, bản cập nhật TV, đặt lại Tùy chọn nhà phát triển), để hẹn giờ, giờ đi ngủ, giới hạn app và điện thoại vẫn chạy. Tắt được trong TV Setup → Gỡ lỗi ADB.
- Sức khỏe: tiến trình tốn CPU nhất hiện thành bảng, CPU và RAM là tiêu đề cột.
- Tự tắt khi không dùng và giới hạn app: nhập thời gian bằng hai ô giờ và phút.
- Tất cả cài đặt: các nhóm gập lại được, danh sách cuộn bên trong (ô tìm luôn ở trên), lọc Chỉnh được / Chỉ xem; giá trị dài gọn trên một dòng, bấm để xem đủ; bấm dòng có 🔒 để biết vì sao bị khóa.
- TV Setup: nút đang chọn không còn bị cắt mép khi phóng to.
- Giờ đi ngủ: cho xem thêm từ điện thoại hay máy tính thì cảnh báo trên TV tắt ngay, không còn đếm tới hết.
- App điện thoại: bỏ nút ⋯. Dưới khung hình: chất lượng hình và toàn màn hình; khi đang nghe thì thêm cách nghe (chỉ điện thoại / cả TV) và nút HQ. 🎧 trên thanh trên bấm là nghe hoặc dừng ngay.
- Vẽ lên TV: nút màu nằm bên phải cùng các nút khác; ảnh đưa lên có nút − / + (giữ để đổi dần), từ 0.1x.
- Biểu tượng Cài đặt là bánh răng.
- Nói / hỏi TV: câu không phải lệnh giờ được tìm trên YouTube (Google Assistant trên TV không hiểu tiếng Việt, box FPT Play quay mãi); TV không có YouTube thì vẫn gửi cho tìm kiếm của TV. App điện thoại: nút 🎤 mở hộp để nói hoặc gõ.
- App điện thoại: nút ‹ ở góc trên màn remote để về danh sách TV.
- Thanh tab trên cùng không còn khe hở để thấy nội dung cuộn bên dưới.
- Tối ưu TV: TV chỉ chạy app 64-bit (Google TV mới) giờ được cài FLauncher bản 64-bit, thay vì báo không cài được.
- "Đang kết nối ADB…" giờ là một dòng nhỏ có vòng quay, nút Thử lại chỉ hiện khi chờ lâu.

**English**
- TV Setup turns USB debugging back on when it is switched off (by a child, a TV update, a reset of Developer options), so the sleep timer, bedtime, app limits and the phone keep working. It can be turned off in TV Setup → Debugging.
- Health: the top CPU processes are a table, with CPU and RAM as column heads.
- Turn off when unused and app limits: the time is typed in two boxes, hours and minutes.
- All settings: groups fold, the list scrolls inside (the search box stays on top), Editable / View only filters; a long value on one line, in full on a tap; tapping a 🔒 row says why it is locked.
- TV Setup: the focused tile is no longer cut at its edge as it grows.
- Bedtime: more time given from a phone or computer closes the notice on the TV at once, instead of it counting down to the end.
- Phone app: no more ⋯. Under the picture: picture quality and full screen; while listening, also how to hear the TV (phone only / both) and an HQ switch. 🎧 on the top bar starts or stops listening at once.
- Drawing on the TV: the colour button sits on the right with the others; a picture being placed has − / + (hold to keep going), down to 0.1x.
- The Settings icon is a gear.
- Speak / ask the TV: a sentence that is not a command is now searched on YouTube (the TV's Google Assistant knows no Vietnamese, and an FPT Play box spun for good); a TV without YouTube still gets it in its own search. Phone app: 🎤 opens a box to speak or type.
- Phone app: a ‹ button at the top of the remote goes back to the list of TVs.
- The top tab bar no longer has gaps that show the page scrolling under it.
- Optimize TV: a TV that runs 64-bit apps only (newer Google TV) now gets the 64-bit FLauncher instead of an error.
- "Connecting to ADB…" is now a small line with a spinner; Retry shows only when it takes long.

## 2.24.1 - 2026-10-15

Web 2.24.1 · app điện thoại 2.22.1 · app TV 2.21.1

**Tiếng Việt**
- Sửa: cập nhật TV Setup báo "Không ghi được tệp cập nhật lên TV" trên vài TV (Sony, Android 9). Tệp gửi lên TV giờ chỉ đóng khi TV đã ghi đủ.

**English**
- Fixed: updating TV Setup said "Could not write the update file on the TV" on some TVs (Sony, Android 9). A file sent to the TV is now closed only once the TV has written all of it.

## 2.24.0 - 2026-10-15

Web 2.24.0 · app điện thoại 2.22.0 · app TV 2.21.0

**Tiếng Việt**
- **🖼️ Đưa ảnh lên TV** khi đang ✏️ vẽ: chọn một ảnh trong máy, kéo để đặt chỗ, hai ngón (hay con lăn chuột) để phóng to thu nhỏ, bấm ✓ thì TV hiện ảnh đè lên màn hình. Hợp để gửi mã QR cho người nhà quét. Ảnh được thu nhỏ còn tối đa 1280px trước khi gửi (PNG giữ nguyên nét). Cần TV Setup 2.21.0.
- ✏️ Vẽ: một nút màu, bấm mở 10 màu cùng thanh trượt cỡ nét (chấm tròn bên cạnh cho thấy cỡ). Thanh công cụ vừa màn điện thoại.
- Chức năng nào cần TV Setup mới hơn bản đang có trên TV (hoặc TV chưa cài TV Setup) thì hỏi ngay, kèm nút **⬆️ Cập nhật** / **⚡ Cài TV Setup**, thay vì báo lỗi sau khi đã làm xong. Hiện có: vẽ ✏️, ảnh 🖼️, hẹn giờ trong cảnh. Có trên cả ba app.
- Logo TV Remote ở đầu trang danh sách TV và trang remote (cả ba app).
- App điện thoại: biểu tượng mới giống app máy tính. Thanh trên trang remote gọn một dòng: tên TV (dài thì cắt bớt) và trạng thái bên dưới, nút 👁 và ⋯ (🎧 nghe, chất lượng hình, toàn màn hình).
- Sửa: 🎧 Nghe tiếng TV trên app điện thoại không ra tiếng (từ bản 2.18.0).
- Nút bật/tắt trên app điện thoại sáng lên khi đang bật: ✏️ Vẽ, 👁 Xem hình, 🎧 Nghe, Dùng touchpad.

**English**
- **🖼️ Put a picture on the TV** while ✏️ drawing: pick a picture, drag to place it, pinch (or the mouse wheel) to size it, tap ✓ and the TV shows it over its screen. Handy for a QR code someone at home should scan. Pictures are shrunk to at most 1280 px before sending (PNGs stay sharp). Needs TV Setup 2.21.0.
- ✏️ Draw: one colour button opening 10 colours and a line-size slider (a dot beside it shows the size). The toolbar fits a phone.
- Any feature that needs a newer TV Setup than the TV has (or a TV without TV Setup) asks at once, with **⬆️ Update** / **⚡ Install TV Setup**, instead of an error after the work is done. For now: drawing ✏️, pictures 🖼️, the timer in a scene. In all three apps.
- The TV Remote logo at the head of the list of TVs and the remote (all three apps).
- Phone app: the new icon, the same as the computer app's. The remote's top bar is one line: the TV's name (cut short when long) with its state under it, then 👁 and ⋯ (🎧 listen, picture quality, full screen).
- Fixed: 🎧 Hear the TV in the phone app played nothing (since 2.18.0).
- On/off buttons in the phone app light up while on: ✏️ Draw, 👁 Picture, 🎧 Listen, Use the touchpad.

## 2.23.0 - 2026-10-15

Web 2.23.0 · app điện thoại 2.21.0 · app TV 2.20.0

**Tiếng Việt**
- **Tất cả cài đặt** (chuyển từ tab Sức khỏe sang cuối tab Thiết lập) giờ sửa được các mục đơn giản: thời gian tắt màn hình, ngủ, hiệu ứng, cỡ chữ, độ sáng, âm thanh khi bấm, kiểu giờ, tên thiết bị... Nút ↺ trả lại giá trị cũ. Mục có thể làm mất kết nối hay hỏng TV (ADB, Wi-Fi, DNS, bàn phím, trợ năng, cài app...) vẫn chỉ xem, có 🔒.
- Sửa tab Thiết lập trên màn hình hẹp: chữ tên mục bị bóp thành cột dọc, và bấm vào tab hiện hộp hỏi xác nhận.

**English**
- **All settings** (moved from the Health tab to the end of the Setup tab) can now change the simple ones: screen off and sleep times, animations, font size, brightness, click sounds, time format, device name... ↺ puts the old value back. Settings that could cut the TV off or break it (ADB, Wi-Fi, DNS, keyboard, accessibility, app installs...) stay view-only, with a 🔒.
- Fixed the Setup tab on narrow screens: row titles squeezed into a column, and tapping the tab asked for confirmation.

## 2.22.0 - 2026-10-15

Web 2.22.0 · app điện thoại 2.20.0 · app TV 2.19.0

**Tiếng Việt**
- TV Setup tự cập nhật trên mọi TV, cả Android 9 trở xuống (như TV Sony). Trên Android 10 trở lên, bản mới chạy lỗi thì tự quay về bản cũ.
- Ô **Cập nhật** trên TV có menu: Cập nhật ngay, Tự cập nhật bật/tắt, **Gỡ TV Setup** (bật lại các app đã tắt bằng Tối ưu TV rồi mới gỡ). Khung nhật ký rộng hơn.
- Bớt chữ trên các màn: bỏ các đoạn giải thích luôn hiện, chữ ngắn hơn.

**English**
- TV Setup updates itself on every TV, Android 9 and older too (Sony TVs, for one). On Android 10 and up, a new version that fails is undone by itself.
- The **Update** tile on the TV has a menu: Update now, Automatic updates on/off, **Remove TV Setup** (apps turned off by Optimize TV come back on first). A larger activity box.
- Less text on every screen: no always-shown explanations, shorter words.

## 2.21.0 - 2026-10-15

Web 2.21.0 · app điện thoại 2.19.0 · app TV 2.18.0

**Tiếng Việt**
- **Danh sách app hiện nhanh hơn trên điện thoại và máy tính:** để đọc tên và logo app, trước đây phải chép cả app điện thoại (1,1 MB) lên TV mỗi khi app được cập nhật. Giờ chỉ cần chép một tệp 6 KB.
- **Cài đặt hệ thống dễ đọc hơn:** các cài đặt hay cần được chia theo nhóm (Màn hình & chờ, Mạng, Nhà phát triển & ADB, App & bàn phím, Âm thanh, Ngôn ngữ & giờ, Trợ năng, Thiết bị), mỗi mục có tên dễ hiểu và giá trị đọc được (“Bật”, “20 phút”, “Gboard” thay cho `1`, `1200000`, `com.google.android.inputmethod.latin/...`). Hàng trăm khóa kỹ thuật còn lại được gom vào mục “Khác”, mở khi cần hoặc khi tìm kiếm.
- **TV Setup tự cập nhật** (Android 10 trở lên; tắt được bằng nút “Tự cập nhật” trên màn hình TV Setup): app tìm bản mới vài giờ một lần và chỉ cài khi không ai đang dùng. Nghĩa là không ai bấm remote trên web trong 30 phút gần đây, không có hình hay phiên điều khiển nào đang mở (kể cả từ điện thoại hay máy tính), không đang ghép mã, màn hình TV Setup không mở, hẹn giờ tắt không sắp hết và không trong giờ đi ngủ khi TV đang bật. Những gì đang chiếu trên TV không bị ảnh hưởng.
- **Tự sửa khi bản mới bị lỗi:** sau khi cài, TV kiểm tra bản mới có chạy được không. Nếu bản mới không chạy hoặc bị crash, TV tự trả về bản cũ và giữ nguyên dữ liệu (luật hẹn giờ, giới hạn app, thiết bị đã ghép, khóa ADB). Bản lỗi đó không bao giờ được đề nghị lại trên TV này, và TV cũng như trang web đều báo cho người dùng biết.
- **Báo bản TV Setup mới ở cả ba nơi** (máy tính, trang của TV Setup, màn Quản lý trong app điện thoại): có bản mới thì đầu trang hiện “Cập nhật”, bấm một lần là xong, có báo từng bước (đang tải, đang cài, đang kiểm tra).
- Sửa lỗi: điện thoại và máy tính giờ khởi động lại được dịch vụ của TV Setup khi nó bị dừng.
- **App điện thoại không còn bị đẩy về danh sách TV** khi chuyển qua app khác rồi quay lại. Nếu kết nối bị rớt lúc app đang ở nền, app tự nối lại khi mở lên. Nếu mạng chưa kịp có (Wi-Fi vừa thức dậy), app thử lại vài lần trong khoảng 15 giây rồi mới báo lỗi. Cách này áp dụng cả cho chế độ chỉ bấm phím, và cả khi Android tự đóng app lúc nó ở nền.
- **Màn remote của app điện thoại báo khi TV Setup trên TV có bản mới**, kèm nút cập nhật ngay tại đó. Nếu bản mới chạy lỗi và TV đã tự quay về bản cũ, màn remote cũng báo.
- **Sửa lỗi lọc DNS làm YouTube và các app khác báo mất mạng.** Lúc bật, app kiểm tra bằng một tên miền nhưng TV vẫn còn nhớ sẵn tên đó, nên một máy chủ DNS không dùng được vẫn qua; về sau TV mới mất mạng (gặp trên TV Sony có Tailscale). Giờ app kiểm tra bằng một tên ngẫu nhiên mỗi lần. TV Setup cũng kiểm tra lại định kỳ và mỗi khi mạng thay đổi: nếu TV vẫn ra Internet mà không tra được tên miền nào thì nó tự tắt bộ lọc, và mục 🛡️ Lọc DNS ghi rõ lý do.
- **Tải tệp từ TV về điện thoại có thanh tiến độ** (bao nhiêu MB, bao nhiêu %), hiện chung trong khung “⏳ Tiến trình” với việc gửi tệp lên TV và cài APK. Trên điện thoại, khung này là một dải nằm dọc đáy màn hình; khi thu gọn, tiêu đề vẫn hiện phần trăm.
- **Tab Ứng dụng gọn hơn:** có chip lọc nhanh (Tất cả · Tự chạy khi bật TV · Cần giữ · Đã tắt, kèm số lượng). “Bật / tắt / gỡ hàng loạt” mở trong cửa sổ riêng. Mỗi app chỉ còn nút ⋯, trong đó “Mở” nằm đầu tiên.
- **Giao diện gọn hơn:** các nút đóng chỉ còn ✕; trạng thái ở đầu trang ngắn lại (“ADB ✓”, “TV bật”), di chuột vào để xem đầy đủ, nên trên điện thoại cả hàng nằm vừa một dòng.
- **✏️ Vẽ lên màn hình TV** để chỉ chỗ cho người đang xem TV (“bấm vào đây”, khoanh một nút). Trên máy tính và trang của TV Setup: nút ✏️ cạnh hình TV. Trên app điện thoại: “Vẽ lên TV” trong Thêm phím. Vẽ bằng ngón tay hoặc chuột lên hình, TV hiện nét vẽ ngay, đè lên mọi app. Có 5 màu, nét to, bỏ nét vừa vẽ, xóa hết. Người xem vẫn dùng TV bình thường bên dưới nét vẽ. Nét vẽ tự mất sau 10 phút nếu không ai xóa. Cần TV Setup bản mới (2.18.0) và ADB.
- **Trên màn hình hẹp, nút chỉ còn biểu tượng sẽ hỏi trước khi làm:** bấm vào thì trang hiện câu nói rõ nút đó làm gì (“🧹 Dọn bộ nhớ đệm của mọi app?”), đồng ý mới làm. Áp dụng cho mọi nút gập chữ, trừ ✕ và ⋯, và trừ những nút vốn đã tự hỏi.
- **Danh sách app dễ nhìn hơn:** “Tự chạy”, “Cần giữ”, “Đã tắt” thành huy hiệu màu ngay cạnh tên app (tím, xanh lá, xám), cùng màu với chip lọc phía trên. Tên gói nằm riêng một dòng. “Hiện cả app hệ thống” thành chip “⚙️ App hệ thống” chung hàng với các chip lọc. Nút “🤖 Hỏi AI” và “📝 Hàng loạt” ghi ngắn, giải thích nằm trong cửa sổ mở ra. Menu ⋯ của mỗi app có thêm “📋 Chép tên gói”.
- **Gỡ nhiều app trên nhiều TV một lần:** hộp “Gỡ app nào?” có ô tích, nút Hủy và Gỡ (số app đã chọn), trên máy tính và app điện thoại. Nút “Nhiều TV ›” trên app điện thoại nằm gọn cạnh tiêu đề “TV đã dùng”.

**English**
- **The app list shows faster on the phone and the computer:** to read app names and logos, the whole phone app (1.1 MB) used to be copied to the TV after every update. Now a 6 KB file is enough.
- **System settings easier to read:** the settings people look for are grouped (Screen & sleep, Network, Developer & ADB, Apps & keyboard, Sound, Language & time, Accessibility, Device), each with a plain name and a readable value (“On”, “20 min”, “Gboard” instead of `1`, `1200000`, `com.google.android.inputmethod.latin/...`). The hundreds of other technical keys sit under “Other”, opened when needed or when searching.
- **TV Setup updates itself** (Android 10 and up; it can be turned off with the “Automatic updates” button on TV Setup's screen): it looks for a new version every few hours and installs it only when nobody is using it. That means no one used the web remote in the last 30 minutes, no picture or control session is open (including from a phone or computer), no pairing is under way, TV Setup's screen is not open, the sleep timer is not about to end, and it is not bedtime with the TV on. Whatever is playing on the TV is not affected.
- **Self-repair when a new version is broken:** after installing, the TV checks that the new version runs. If it does not start or keeps crashing, the TV puts the old version back with all its data (timer rules, app limits, paired devices, ADB key). That version is never offered again on this TV, and both the TV and the web page tell the user what happened.
- **A new TV Setup version is offered everywhere** (computer, TV Setup's page, the phone app's Manage screen): when one exists, the top of the page shows “Update”. One click does it all, with each step shown (downloading, installing, checking).
- Fix: the phone and the computer can now restart TV Setup's service when it has stopped.
- **The phone app no longer drops back to the list of TVs** when you switch to another app and back. A connection lost while the app was in the background is opened again on return. If the network is not ready yet (Wi-Fi just waking up), the app tries again a few times over about 15 seconds before showing an error. This also covers keys-only mode, and the case where Android closed the app in the background.
- **The phone app's remote screen says when TV Setup on the TV has a new version**, with an update button right there. If a new version failed and the TV went back to the old one, the remote screen says so too.
- **Fix: a DNS filter could make YouTube and other apps say there was no Internet.** When turning it on, the app checked with a name the TV still remembered, so a DNS server that did not work could pass; the TV lost the Internet later (seen on a Sony TV with Tailscale). Now the app checks with a new random name each time. TV Setup also checks again from time to time and whenever the network changes: if the TV reaches the Internet but cannot look up any name, it turns the filter off, and the 🛡️ DNS filter row says why.
- **Downloading a file from the TV to the phone shows its progress** (MB and %), in the same “⏳ Progress” panel as uploads and APK installs. On a phone the panel is a strip along the bottom of the screen; when folded, its title still shows the percentage.
- **Tidier Apps tab:** quick filter chips (All · Starts with the TV · Must keep · Turned off, with counts). “Turn on / off / remove in bulk” opens in its own window. Each app keeps only its ⋯ button, with “Open” first in that menu.
- **Tidier layout:** close buttons are just ✕; the states at the top are shorter (“ADB ✓”, “TV on”), with the full text on hover, so the whole row fits on one line on a phone.
- **✏️ Draw on the TV's screen** to show the person watching where to go (“press here”, a circle around a button). On the computer and TV Setup's page: the ✏️ button next to the TV picture. In the phone app: “Draw on the TV” under More keys. Draw on the picture with a finger or the mouse and the TV shows it at once, over every app. Five colours, a thick line, take back the last line, clear all. The person watching keeps using the TV under the drawing. It goes away by itself after 10 minutes if nobody clears it. Needs the new TV Setup (2.18.0) and ADB.
- **On a narrow screen, a button showing only its icon asks first:** tapping it says what it does (“🧹 Clear the cache of every app?”) and acts only on yes. This holds for every button whose words are folded away, except ✕ and ⋯ and buttons that already ask.
- **A clearer app list:** “Autostart”, “Must keep” and “Turned off” are coloured tags next to the app's name (purple, green, grey), the same colours as the filter chips above. The package name has its own line. “Show system apps too” is now the “⚙️ System apps” chip, in the row of filter chips. The “🤖 Ask AI” and “📝 In bulk” buttons say it short; the window they open explains. Each app's ⋯ menu can “📋 Copy package name”.
- **Remove several apps from several TVs at once:** “Remove which apps?” has checkboxes, Cancel and Remove (with how many are picked), on the computer and in the phone app. The phone app's “Several TVs ›” button sits next to the “Your TVs” heading.

## 2.20.0 - 2026-10-15

Web 2.20.0 · app điện thoại 2.18.0 · app TV 2.17.0

**Tiếng Việt**
- **App TV Remote cho máy tính** (Mac, Windows, Linux): tải một tệp ở Releases, giải nén là chạy, có cửa sổ riêng. Không còn phải tải mã nguồn, cài Python, adb hay ffmpeg. App dùng chung lõi với app điện thoại và TV Setup nên làm được đúng những việc như hai app đó. Link sang trang khác mở bằng trình duyệt, ảnh chụp và tệp sao lưu lưu vào thư mục Tải về, đóng cửa sổ là app dừng hẳn. **Tự cập nhật** như app điện thoại: có bản mới thì app tự tải sẵn và báo ở đầu trang, bấm “Cập nhật” là app tự thay bản mới và mở lại; bấm “Để sau” thì bản mới được cài khi đóng app. Bản Python cũ (tải mã nguồn, `start-windows.cmd`...) đã bỏ; lần đầu mở app mới cần chọn lại TV và ghép lại nút remote.
- **Làm trên nhiều TV cùng lúc** (homestay, khách sạn, nhà nhiều TV): ở danh sách TV đã lưu trên máy tính và trong app điện thoại, chọn các TV rồi cài cùng một APK lên tất cả, gỡ một app hoặc khởi động lại. Mỗi TV có một dòng báo tiến độ riêng; TV tắt hay chưa bật ADB thì báo rõ, các TV khác vẫn làm tiếp. Máy tính còn hiện model, Android, bộ nhớ trống, RAM và bản TV Setup của từng TV. (TV Setup chỉ quản lý chính TV của nó nên không có phần này.)
- **Kiểm tra APK trước khi cài:** APK cần Android mới hơn TV hay dành cho loại chip khác thì báo ngay bằng lời dễ hiểu, không đợi lỗi `INSTALL_FAILED_...` sau khi chép lên TV.
- **Chi tiết & quyền của app** (⋯ → ℹ️): phiên bản, ngày cài, dung lượng, nguồn cài, và bật/tắt từng quyền (micro, vị trí, bộ nhớ...).
- **Nhãn trong danh sách app:** “cần giữ” cho app TV cần, “tự chạy khi bật TV” cho app khởi động cùng TV.
- **Khả năng của TV** (Sức khỏe → Khả năng): độ phân giải, HDR, Dolby/DTS, giải mã video, Widevine, Bluetooth, USB... có nút sao chép.
- **Cài đặt hệ thống · chỉ xem** (Sức khỏe): danh sách các cài đặt của TV, có tìm kiếm và sao chép.
- **An toàn hơn khi tự bấm “Cho phép gỡ lỗi USB?”:** nếu hộp hỏi của thiết bị mình vừa biến mất và TV hiện ngay hộp của một khóa lạ vào đúng chỗ đó, giờ không bao giờ bấm Cho phép cho khóa lạ (trước đây có một khe rất nhỏ). Khi đăng nhập Tailscale, chỉ bấm khi màn hình của Tailscale đang ở trên cùng.
- **Web trên máy tính an toàn hơn:** trang web khác đang mở trong trình duyệt không còn lén mở được tiếng hay hình của TV (trước đây chỉ chặn các lệnh bấm, chưa chặn việc xem/nghe).
- **Hộp hỏi lại nằm ngay trong trang** (gỡ app, xóa tệp, đổi tên, tắt TV...): cùng kiểu với trang, nút ghi rõ việc sẽ làm (“Gỡ”, “Xóa”) và nút nguy hiểm màu đỏ, thay cho hộp thoại xám của trình duyệt.
- **“Tắt TV” trên máy tính giờ tắt được cả TV Sony:** phím ngủ đi qua ADB khi có, vì TV Sony bỏ qua phím này khi gửi bằng giao thức remote.

**English**
- **The TV Remote computer app** (Mac, Windows, Linux): download one file from Releases, unzip, run; it has its own window. No more downloading the source or installing Python, adb or ffmpeg. It runs on the same core as the phone app and TV Setup, so it does exactly what they do. Links to other sites open in the browser, screenshots and backups go to the Downloads folder, closing the window quits it. **It updates itself** like the phone app: a new version is downloaded in the background and shown at the top of the page; “Update” swaps it in and opens it again, “Later” puts it in place when the app closes. The old Python version (the source download, `start-windows.cmd`...) is gone; the first time, pick the TV and pair the remote buttons again.
- **Several TVs at once** (homestays, hotels, homes with many TVs): on the list of saved TVs, on the computer and in the phone app, pick TVs, then install one APK on all of them, remove an app or restart them. Each TV gets its own progress line; a TV that is off or has ADB off is named plainly and the others carry on. The computer also shows each TV's model, Android, free storage, RAM and TV Setup version. (TV Setup manages only its own TV, so it has no such list.)
- **APK checked before installing:** an APK that needs a newer Android than the TV has, or is built for another chip, is reported at once in plain words instead of an `INSTALL_FAILED_...` after copying it over.
- **App details & permissions** (⋯ → ℹ️): version, install date, size, installer, and each permission (microphone, location, storage...) on or off.
- **Tags in the app list:** “must keep” for apps the TV needs, “starts with the TV” for apps that start at boot.
- **TV capabilities** (Health → Capabilities): resolutions, HDR, Dolby/DTS, video decoders, Widevine, Bluetooth, USB... with a copy button.
- **System settings, read only** (Health): the TV's settings in one list, with search and copy.
- **Safer automatic answer to “Allow USB debugging?”:** if the question about our own device goes away and the TV puts a stranger's key's question up in its place, Allow is now never pressed for the stranger (there used to be a very small gap). When signing in to Tailscale, buttons are only pressed while Tailscale's own screen is in front.
- **Safer web on the computer:** another website open in the browser can no longer quietly start the TV's sound or picture (only commands were blocked before, not watching or listening).
- **Questions asked inside the page** (removing an app, deleting a file, renaming, turning the TV off...): in the page's own style, with buttons that say what they do (“Uninstall”, “Delete”) and dangerous ones in red, instead of the browser's grey box.
- **“Turn the TV off” from the computer now works on Sony TVs too:** the sleep key goes over ADB when there is ADB, because Sony ignores it over the remote protocol.

## 2.19.0 - 2026-10-15

Web 2.19.0 · app điện thoại 2.17.0 · app TV 2.16.0

**Tiếng Việt**
- **Wi-Fi trong Lịch sử (tab Sức khỏe):** TV Setup ghi độ mạnh sóng Wi-Fi 5 phút một lần và mỗi lần TV mất mạng rồi có lại. Lịch sử vẽ đường sóng theo ngày, kể các lần mất mạng khi đang dùng TV (bỏ qua lúc TV ngủ), và khuyên cách cải thiện khi sóng yếu. Phần “Hiện tại” cũng có thêm một dòng lời khuyên về Wi-Fi.
- **Hết lỗi sập trên TV khi đọc tên và logo app:** máy tính, điện thoại và TV Setup từng dùng chung một tệp tạm trên TV và ghi đè lên nhau, làm TV ghi lại hàng loạt lỗi sập. Giờ mỗi bản dùng tệp riêng, ghi xong mới đổi tên; nếu lỗi thì 5 phút sau mới thử lại, không thử lại cho từng app.
- **Logo app hiện nhanh hơn trên app điện thoại:** tên và logo được giữ trên điện thoại cho từng TV, mở Quản lý → Ứng dụng là hiện ngay; điện thoại chỉ hỏi lại TV ở chế độ nền để thấy app mới cài hay mới cập nhật.
- **Tự tắt khi không dùng:** thêm 12 giờ, 24 giờ và “Tự nhập...” (số phút, hoặc số giờ như “6 giờ”).
- **Nút Hiện tại / Lịch sử / Lỗi app** gọn thành một hàng, không còn xuống dòng trên điện thoại.
- **App điện thoại:** các bảng chọn (Mở trên TV, Hẹn giờ, Cảnh, Nghe tiếng TV, Bật ADB) có icon trước mỗi mục để nhìn là biết, không cần đọc hết chữ.
- **TV Setup không còn bị sập** khi có máy trong mạng gửi một địa chỉ lạ (ví dụ `?a=%zz`) hay mở cả trăm kết nối rồi bỏ đó; thêm giới hạn số kết nối cùng lúc.
- **Ghép đôi an toàn hơn:** bấm “Hiện mã trên TV” lần nữa vẫn giữ mã cũ; nhập sai 15 lần thì khóa 15 phút, không còn đoán mã mãi được.
- **Không xóa nhầm cả bộ nhớ:** tab Tệp từ chối xóa hay đổi tên `/storage/emulated/0` và `/storage/self/primary` (trước chỉ chặn `/sdcard`).
- **Ít treo hơn khi TV chậm hay mất mạng:** lệnh ADB quá giờ bị dừng hẳn thay vì chạy ngầm; trang không dồn thêm lượt kiểm tra khi lượt trước chưa xong, và không hỏi TV khi tab đang ẩn; chờ khởi động lại tối đa 10 phút; kéo trên màn hình qua mạng chậm không còn trễ dần.
- **App điện thoại:** đóng màn hình (đổi giao diện sáng/tối, cỡ chữ, ngôn ngữ) thì tắt luôn âm thanh và kết nối cũ; lỗi giải mã âm thanh chỉ tắt âm thanh, không làm sập app; cảnh chạy từ widget có bước chờ dài không còn bị cắt ở giây 15; câu nói “hẹn giờ tắt 99999999999 phút” không làm sập app.
- **Thanh tab gọn hơn:** bỏ dòng chữ “cần ADB” dưới mỗi tab; khi chưa có ADB, các tab cần ADB chỉ mờ đi, vẫn bấm vào được và bên trong hướng dẫn cách bật.
- Sao lưu app và tải tệp: ngắt giữa chừng không còn để lại tiến trình chạy trên TV; danh sách TV (`tvs.json`) được ghi trọn vẹn hay không ghi gì.

**English**
- **TV Setup no longer crashes** when a device on the network sends an odd address (such as `?a=%zz`) or opens a hundred connections and leaves them; concurrent connections are capped.
- **Safer pairing:** pressing “Show the code on the TV” again keeps the same code; 15 wrong codes lock pairing for 15 minutes, so codes can't be guessed forever.
- **No deleting a whole storage by mistake:** the Files tab refuses to delete or rename `/storage/emulated/0` and `/storage/self/primary` (before, only `/sdcard` was blocked).
- **Fewer hangs when the TV is slow or offline:** timed-out ADB commands are stopped instead of running on; the page doesn't pile up checks while one is pending and doesn't poll the TV from a hidden tab; waiting for a restart stops after 10 minutes; dragging on the screen over a slow link no longer lags more and more.
- **Phone app:** when the screen is recreated (light/dark switch, font size, language) the old sound and connection end with it; an audio decoding error stops the sound instead of crashing the app; scenes run from the widget with long waits are no longer cut at 15 s; saying “sleep in 99999999999 minutes” no longer crashes the app.
- **A lighter tab bar:** no more “needs ADB” line under each tab; while ADB is off the tabs that need it are only dimmed; they still open and show how to turn it on.
- App backups and file downloads: stopping midway no longer leaves a process running on the TV; the TV list (`tvs.json`) is written whole or not at all.
- **Wi-Fi in History (Health tab):** TV Setup records the Wi-Fi signal every 5 minutes and every time the TV loses and regains the network. History draws the signal per day, lists the network losses while the TV was in use (not while it slept), and suggests what to do about a weak signal. “Now” gains a Wi-Fi advice line too.
- **No more crashes on the TV while reading app names and logos:** the computer, the phone and TV Setup used one shared temporary file on the TV and overwrote each other, so the TV logged a burst of crashes. Each build now has its own file, renamed into place once complete; after a failure the next try waits 5 minutes instead of retrying for every app.
- **App logos show faster in the phone app:** names and logos are kept on the phone per TV, so Manage → Apps shows them at once; the phone asks the TV again in the background only to catch apps installed or updated since.
- **Turn off when idle:** 12 hours, 24 hours and “Custom...” (minutes, or hours such as “6 h”).
- **The Now / History / App errors switch** fits on one line and no longer wraps on phones.
- **Phone app:** the choice lists (Open on the TV, Sleep timer, Scenes, Hear the TV, Turn on ADB) show an icon before each item, so they can be scanned without reading every line.

## 2.18.0 - 2026-10-15

Web 2.18.0 · app điện thoại 2.16.0 · app TV 2.15.0

**Tiếng Việt**
- **Nút ⋯ cho từng app (tab Ứng dụng):** mỗi app chỉ còn nút Mở (hoặc Bật lại) và nút ⋯; bấm ⋯ để chọn Dừng, Xem log, Sao lưu, Xóa dữ liệu, Tắt app, Gỡ.
- **🧹 Dọn bộ nhớ đệm:** dọn cache của mọi app một lần, báo lấy lại được bao nhiêu. **🧽 Xóa dữ liệu** một app (hỏi lại trước khi làm; app cần cho TV chạy thì không cho).
- **💾 Sao lưu / 📥 Khôi phục app:** tải về một tệp .tar gồm APK (cả bản tách nhiều tệp) và thư mục dữ liệu trong bộ nhớ chung (Android/data, obb); khôi phục lên cùng TV hay TV khác. Dữ liệu riêng của app (đăng nhập...) Android không cho lấy ra nếu không root.
- **📜 Xem log của app** ngay trên trang, cập nhật liên tục, tô màu lỗi/cảnh báo, tạm dừng và chép được.
- **Thẻ 📺 TV (tab Thiết lập):**
  - 🔁 Khởi động lại TV. Trang chờ TV lên rồi tự kết nối lại. Tailscale được đặt làm VPN luôn bật trước đó, để vẫn điều khiển được từ xa sau khi TV khởi động lại.
  - 🛡️ Lọc DNS (chặn quảng cáo, gia đình, tự nhập). TV thử từng máy chủ, không dùng được thì tự trả lại cài đặt cũ, kể cả khi mất kết nối giữa chừng.
  - 💤 Tự tắt khi không dùng, 🖼️ màn hình chờ, 🔠 cỡ giao diện, 🏠 chọn màn hình chính.
- Có ở cả ba nơi: web máy tính, TV Setup, mục Quản lý của app điện thoại.

**English**
- **A ⋯ button per app (Apps tab):** each app keeps only Open (or Turn on) and ⋯, which holds Stop, View log, Back up, Clear data, Disable and Uninstall.
- **🧹 Clean caches:** clears every app's cache at once and says how much came back. **🧽 Clear data** for one app (asks first; refused for apps the TV needs).
- **💾 Back up / 📥 Restore apps:** downloads a .tar with the APK (split APKs too) and the app's folders in shared storage (Android/data, obb); restore it to the same TV or another. Android keeps an app's private data (sign-ins...) out of reach without root.
- **📜 An app's live log** right on the page, coloured by level, with pause and copy.
- **📺 TV card (Setup tab):**
  - 🔁 Restart the TV. The page waits for it and reconnects. Tailscale is made the always-on VPN first, so the TV stays reachable from away after the restart.
  - 🛡️ DNS filter (block ads, family, custom). The TV tries each server and puts the old setting back when one does not work, even if the link drops meanwhile.
  - 💤 Turn off when idle, 🖼️ screensaver, 🔠 display size, 🏠 home screen choice.
- In all three clients: the computer web, TV Setup and the phone app's Manage screen.

## 2.17.2 - 2026-10-15

Web 2.17.2 · app điện thoại 2.15.2 · app TV 2.14.2

**Tiếng Việt**
- **Sửa tab Tệp · Cài không chạy (2.17.1):** chấm tròn chỗ bấm trên hình dùng trùng một tên với tab Tệp, làm cả tab đó và việc đổi TV báo lỗi.
- **Sửa lỗi 500 khi tải danh sách app (web máy tính):** chọn hoặc đổi TV đúng lúc trang đang tải danh sách app làm server trộn hai TV và báo lỗi. Chọn lại đúng TV đang dùng cũng không còn làm rớt kết nối.
- **Sức khỏe → Lỗi app:** xem app nào trên TV bị dừng, bị treo trong khoảng 3 ngày qua (Android tự ghi lại), gom theo app, có số lần, lần cuối, lý do bằng lời dễ hiểu và nguyên văn lỗi, nút chép để gửi người khác hỏi. Có ở cả ba nơi.

**English**
- **Fixed the Files · Install tab not working (2.17.1):** the tap dot on the picture reused a name from the Files tab, which broke that tab and switching TVs.
- **Fixed a 500 when loading the app list (computer web):** choosing or changing the TV while the page loaded the apps mixed two TVs up. Choosing the TV already in use no longer drops its connections either.
- **Health → App errors:** see which apps on the TV stopped or froze over the last 3 days or so (Android keeps the record), grouped per app with how often, when last, the reason in plain words and the exact error, and a button to copy it for someone to look at. In all three clients.

## 2.17.1 - 2026-10-15

Web 2.17.1 · app điện thoại 2.15.1 · app TV 2.14.1

**Tiếng Việt**
- **Sửa nút remote "chưa nối" sau khi nghe tiếng TV:** trên vài TV (box FPT Play, Android 14), dịch vụ remote của Google bị lỗi khi TV đang bị thu tiếng, và Android chờ tới vài giờ mới bật lại nó. Giờ tắt nghe là dịch vụ được bật lại ngay; trong lúc nghe, các nút đi qua ADB (nhãn "Nút bấm: đi qua ADB") nên vẫn bấm được.
- **Sửa "🎧 Chỉ máy này" không có tiếng** (box FPT Play): trên TV Android 13+ chế độ này giờ thu tiếng kiểu mới như "Cả TV và máy này", loa TV vẫn tạm im.
- **Tắt nghe không còn làm tiếng TV khựng ~2 giây:** việc thu tiếng được dừng gọn thay vì bị ngắt ngang.
- **Nghe tiếng ít trễ hơn:** trước đây mỗi lần mạng khựng, độ trễ tăng thêm và giữ luôn (tới 0,6 giây); giờ trang tự đuổi kịp, giữ khoảng 0,1 giây. App điện thoại giữ tối đa 0,15 giây thay vì 0,3.
- **Thấy chỗ mình bấm trên hình TV:** bấm/chạm lên hình hiện một chấm tròn, kéo thì chấm đi theo, nhả ra thì loang rồi mờ dần; cuộn thì không hiện (web và app điện thoại).
- **🎧 Chất lượng cao:** nút bật/tắt trong khung chọn nghe (và menu 🎧 của app điện thoại): Opus 160 kbit/s thay vì 64, nghe nhạc rõ hơn; trên trang mở từ TV là âm thanh gốc 48 kHz stereo thay vì 24 kHz mono.
- **Web máy tính: lần quét mạng đầu tiên không thấy TV,** phải bấm quét lại mới ra. Giờ các địa chỉ trả lời lỗi được hỏi lại một lần, và lần quét nào không thấy gì thì trang tự quét thêm lần nữa trước khi báo.
- README có link tới trang giới thiệu tvremote.pages.dev.

**English**
- **Fixed the remote buttons showing "not connected" after listening to the TV:** on some TVs (FPT Play box, Android 14), Google's remote service crashes while the TV's sound is captured, and Android waits up to hours to restart it. It is now started again as soon as listening stops; while listening, buttons go over ADB (labelled "Buttons: over ADB"), so they keep working.
- **Fixed "🎧 Only this device" being silent** (FPT Play box): on Android 13+ TVs this mode now uses the same newer capture as "TV and this device"; the TV's speakers still go quiet.
- **Stopping listening no longer makes the TV's sound stutter for ~2 s:** the capture is ended cleanly instead of being cut off.
- **Less delay when listening:** every network hiccup used to add delay that stayed (up to 0.6 s); the page now catches up to about 0.1 s. The phone app keeps at most 0.15 s instead of 0.3.
- **See where you click on the TV picture:** a click or touch shows a dot that follows a drag and fades out in a ring when let go; scrolling shows none (web and phone app).
- **🎧 High quality:** a switch in the listening picker (and the phone app's 🎧 menu): Opus at 160 kbit/s instead of 64, clearer music; on the page served by the TV, the original 48 kHz stereo instead of 24 kHz mono.
- **Computer web: the first network scan found no TV,** and only a second scan did. Addresses that answered with an error are now asked once more, and a scan that finds nothing is repeated once before saying so.
- The README links to the product page tvremote.pages.dev.

## 2.17.0 - 2026-10-14

Web 2.17.0 · app điện thoại 2.15.0 · app TV 2.14.0

**Tiếng Việt**
- **Hẹn giờ tắt tự chọn:** ngoài các nút 15 phút … 2 giờ, nhập số phút (vd `40`) hoặc giờ tắt (vd `23:30`, `23h30`). App điện thoại: mục "Tự chọn..." trong menu 😴.
- **Mọi nút đều có phản hồi:** nút nào phải chờ TV trả lời thì hiện vòng xoay ngay trên nút cho tới khi xong. Cài APK hiện % ngay trên nút đã bấm, ngoài thẻ tiến trình (giờ nổi ở góc dưới bên phải, có nút ✕).
- **Cài APK kiểm tra app còn trên TV:** vài box nhà mạng (FPT Play...) gỡ một số app ngay sau khi cài; trước đây vẫn báo "đã cài", giờ báo rõ TV đã tự gỡ.
- Web máy tính: nút "Mở" app nhanh hơn (tra màn hình chính của app trước, không dùng `monkey`); app không có màn hình để mở thì báo bằng câu dễ hiểu.
- **Nghe tiếng TV:** khung chọn "Chỉ máy này / Cả TV và máy này" hiện ngay dưới nút 🎧, không còn bị hình TV che.
- **Mac: app `TV Remote`** để bấm đúp thay cho `start-mac.command`: không qua Terminal (oh-my-zsh hỏi cập nhật có thể nuốt lệnh), kéo vào Applications hay Dock vẫn chạy, mở lần nữa chỉ mở trang.
- **Nút Tắt tắt hẳn:** server dừng, cổng 8765 đóng, adb do chương trình bật cũng tắt theo (adb của chương trình khác thì chỉ ngắt kết nối tới TV). Đóng cửa sổ Terminal hay Ctrl+C cũng vậy.
- **TV Setup tự bật lại Tailscale sau khi CH Play cập nhật nó:** trước đây bản cập nhật tắt Tailscale và không ai bật lại, TV mất kết nối từ xa cho tới khi có người ở nhà mở app. Tailscale đang tắt sẵn thì vẫn để tắt.
- **Web máy tính tự tải `adb` và `ffmpeg`** lần đầu chạy (Mac, Windows, Linux) nếu máy chưa có: chỉ cần Python, không còn phải gõ `brew install` hay `winget install`. adb lấy từ Google, ffmpeg được kiểm tra checksum.
- Web máy tính: nếu code vừa được cập nhật mà server chưa chạy lại, trang hiện dải vàng nhắc mở lại, thay vì các nút báo "Not found".
- Trang giới thiệu sản phẩm (`docs/`, GitHub Pages), ảnh README chuyển sang WebP (nhẹ hơn khoảng 8 lần). Thư mục `app/` gọn lại: cấu hình Gradle vào `app/gradle/`.

**English**
- **Custom sleep timer:** besides the 15 min … 2 h buttons, type minutes (e.g. `40`) or a time (e.g. `23:30`, `23h30`). Phone app: "Custom..." in the 😴 menu.
- **Every button gives feedback:** a button waiting on the TV spins until the answer comes. Installing an APK shows its % on the button pressed, besides the jobs card (now floating bottom-right, with ✕).
- **APK installs check the app stayed:** some operator boxes (FPT Play...) remove certain apps right after install; that used to say "installed", now it says the TV removed it.
- Computer web: "Open" on an app is faster (looks up the app's screen first instead of `monkey`); an app with no screen to open says so in plain words.
- **Listen to the TV:** the "Only this device / TV and this device" choice now appears right under 🎧, no longer hidden by the picture.
- **Mac: a `TV Remote` app** to double-click instead of `start-mac.command`: no Terminal (an oh-my-zsh update prompt could eat the command), works from Applications or the Dock, opening it again only opens the page.
- **Quit really quits:** the server stops, port 8765 closes, and an adb it started goes too (someone else's adb only drops the TVs). Same for closing the Terminal window or Ctrl+C.
- **TV Setup switches Tailscale back on after the Play Store updates it:** an update used to leave it off, and the TV unreachable from away until someone at home opened it. A Tailscale switched off on purpose stays off.
- **The computer web fetches `adb` and `ffmpeg` itself** on first run (Mac, Windows, Linux) when missing: only Python is needed, no more `brew install` or `winget install`. adb comes from Google; ffmpeg is checksum-verified.
- Computer web: when the code was updated but the server not restarted, a yellow banner asks to reopen it, instead of buttons failing with "Not found".
- A product page (`docs/`, GitHub Pages); README images are WebP now (about 8x lighter). `app/` is tidier: the Gradle setup moved to `app/gradle/`.

## 2.16.3 - 2026-10-14

Web 2.16.3 · app điện thoại 2.14.3 · app TV 2.13.3

**Tiếng Việt**
- **Bật / tắt / gỡ hàng loạt** (tab Ứng dụng): dán danh sách app id, hoặc dán nguyên câu trả lời của AI, trang tự nhặt mọi chuỗi dạng com.abc.xyz, hiện từng app đang bật / đã tắt / TV cần (bỏ qua) / không có trên TV, rồi tắt, bật lại hay gỡ cả loạt. Câu hỏi cho AI giờ dặn AI ghi riêng danh sách id để dán vào đây.
- Sửa: màn Quản lý hay kẹt ở "Đang kết nối ADB..." (lần đầu mở, hoặc quay lại từ app khác). Kết nối chết lặng (điện thoại ngủ, đổi Wi-Fi / VPN) giờ được phát hiện và nối lại ngay; lần nối nào treo quá 25 giây thì báo lỗi thay vì chờ mãi; nút "Thử kết nối lại" thật sự mở kết nối mới.
- Trang do TV phục vụ: danh sách Ứng dụng có cả TV Setup (trước đây tự ẩn chính nó).
- App điện thoại: khi đang xem hình (4 nút ở thanh trên), dòng tên TV có hàng riêng, các nút xuống dưới, không còn bị ép thành cột chữ hẹp.
- Nút 🤖 Hỏi AI hiện vòng xoay "Đang lấy danh sách app..." ngay trên nút cho tới khi popup mở (trước đây chỉ có toast 2 giây rồi im lặng). Lấy danh sách cũng nhanh hơn: các lệnh trên TV chạy một lượt thay vì lần lượt.

**English**
- **Turn on / off / remove in bulk** (Apps tab): paste app ids, or a whole AI answer; the page picks out everything shaped like com.abc.xyz, shows each one as on / disabled / needed by the TV (skipped) / not on this TV, then disables, turns on or removes them all. The AI question now asks for that list on its own, ready to paste.
- Fixed: the Manage screen often stuck at "Connecting ADB..." (on first open, or back from another app). A connection that died quietly (phone asleep, Wi-Fi / VPN changed) is now noticed and dialled again at once; a dial stuck over 25 seconds reports an error instead of waiting for ever; "Try again" really opens a fresh connection.
- Page served by the TV: the Apps list includes TV Setup (it used to hide itself).
- Phone app: while the picture is on (four buttons in the top bar), the TV's name gets its own line and the buttons go under it, instead of squeezing the words into a narrow column.
- The 🤖 Ask an AI button shows a spinner and "Getting the app list..." until the window opens (it used to show a 2-second toast, then nothing). Getting the list is faster too: the commands on the TV run in one go instead of one after another.

## 2.16.2 - 2026-10-14

Web 2.16.2 · app điện thoại 2.14.2 · app TV 2.13.2

**Tiếng Việt**
- Tab Ứng dụng: các app đã tắt (bởi Tối ưu TV hoặc nút Tắt app) gom vào mục gập **“⏸ App đã tắt (n)”** ở cuối, mặc định đóng, thay vì lẫn vào danh sách chính.
- App không có logo (hoặc logo tải lỗi) hiện ô chữ cái đầu trên nền màu riêng, không còn ảnh vỡ.

**English**
- Apps tab: apps turned off (by Optimize TV or the Disable button) are folded into a **“⏸ Disabled apps (n)”** section at the end, closed by default, instead of mixing with the main list.
- Apps without a logo (or whose logo fails to load) show their first letter on a colour of their own instead of a broken picture.

## 2.16.1 - 2026-10-14

Web 2.16.1 · app điện thoại 2.14.1 · app TV 2.13.1

**Tiếng Việt**
- Sửa: tab Tự động (và các thẻ xếp hai cột) tràn ngang trên điện thoại, phải kéo sang phải mới thấy hết. Thẻ giờ luôn vừa màn hình; ô chọn app của Giới hạn app xuống hàng trên màn rất hẹp.

**English**
- Fixed: the Automation tab (and other two-column cards) overflowed sideways on phones, needing a sideways scroll. Cards now always fit the screen; the app picker of App limits wraps on very narrow screens.

## 2.16.0 - 2026-10-14

Web 2.16.0 · app điện thoại 2.14.0 · app TV 2.13.0

**Tiếng Việt**
- **🎧 Nghe tiếng TV trên điện thoại / trình duyệt**, chọn mỗi lần bấm: **Chỉ máy này** (loa TV tạm im, cắm tai nghe để xem khuya; dừng nghe là TV có tiếng lại) hoặc **Cả TV và máy này** (TV vẫn phát loa, cần TV Android 13 trở lên; máy cầm tay trễ hơn TV một chút, vài app như Netflix chặn thu tiếng kiểu này; TV cũ hơn tự về chế độ đầu và báo lại). App điện thoại và web máy tính dùng Opus 64 kbps, khoảng 8 KB/s, nhẹ hơn hình TV cỡ 30 lần và TV chỉ tốn vài % một nhân CPU. Trang do TV phục vụ (HTTP, trình duyệt không giải mã Opus được) dùng PCM 24 kHz mono, khoảng 48 KB/s, TV không phải mã hóa gì. App điện thoại nghe tiếp khi khóa màn hình (lúc đã tắt hình), có thông báo với nút Dừng. Cần TV Android 11 trở lên.
- **Tab mới ⏰ Tự động** (thay chỗ Terminal; Terminal chuyển xuống cuối tab Sức khỏe):
  - **Hẹn giờ tắt TV:** 15 phút đến 2 giờ. Còn 1 phút, TV hiện thông báo kèm nút "+15 phút" / "Tắt ngay" / "Hủy".
  - **Giờ đi ngủ:** trong khoảng giờ đã đặt, TV bật lên sẽ tự tắt sau 1 phút.
  - **Giới hạn app mỗi ngày:** còn 5 phút TV báo trước, hết giờ app tự đóng; cho thêm 30 phút ngay trên trang.
  - Ba mục này chạy ngay trên TV trong app TV Setup (bản 2.13.0 trở lên), không cần điện thoại hay máy tính bật. Trang có nút cài / cập nhật TV Setup. Đây không phải khóa trẻ em tuyệt đối: người rành có thể gỡ TV Setup.
  - **Cảnh:** một nút làm nhiều việc (bật/tắt TV, mở app, đặt âm lượng, bấm phím, mở link, chờ, hẹn giờ tắt), có sẵn mẫu. Cảnh hiện thành nút trên tab Remote. **Xuất file sao lưu / nhập từ file** (.json; nhập thì gộp với danh sách đang có, trùng thì thay). Mỗi nơi giữ danh sách cảnh riêng: máy tính trong `tvs.json`, TV Setup trên TV, app điện thoại trong app; dùng xuất/nhập để chép qua lại.
- **Nói với TV thông minh hơn:** "mở YouTube", "tắt tiếng", "tăng âm lượng 5", "hẹn giờ tắt 30 phút", "tắt TV", tên một cảnh... được làm ngay không qua Assistant (nhanh hơn, chạy cả trên TV không có Assistant và khi app chỉ có nút bấm). Câu khác vẫn gửi cho TV như trước.
- **App điện thoại:** nút 🎧 trên thanh trên; nút `⋯` có thêm 😴 Hẹn giờ tắt và 🎬 Cảnh. **Widget** màn hình chính (nguồn, tắt tiếng, âm lượng, 3 cảnh đầu), ô **Cài đặt nhanh** "Nguồn TV" và "Cảnh TV", **phím tắt** khi giữ biểu tượng app: điều khiển TV dùng gần nhất mà không cần mở app.
- **iPhone / Android: thêm trang vào màn hình chính** (PWA): có biểu tượng, mở toàn màn hình như app. Trang hiện hướng dẫn một lần. Trên iPhone, mở từ biểu tượng phải nhập mã ghép thêm một lần.
- **Tab Ứng dụng:** nút **Tắt app / Bật lại** (tắt được app hệ thống không dùng, bật lại lúc nào cũng được; app đã tắt luôn hiện mờ ở cuối danh sách; không cho tắt các app TV cần như Google Play Services, bàn phím, launcher, Cài đặt). Nút **🤖 Hỏi AI nên tắt / gỡ app nào**: chép sẵn một câu hỏi kèm model TV, Android, RAM, app tự cài, app hệ thống, app đang tốn RAM, để dán vào ChatGPT / Claude / Gemini.
- Đặt âm lượng giờ đúng cả trên TV bỏ qua lệnh đặt mức (HDMI-CEC): đặt xong đọc lại, chưa đúng thì bấm phím +/− cho đủ.
- App điện thoại: **Cài đặt → Nút âm lượng của điện thoại** cho chọn nút âm lượng chỉnh TV (như trước) hay chỉnh điện thoại. Lúc đang 🎧 nghe tiếng TV, nút luôn chỉnh điện thoại.
- App điện thoại: hàng nút đầu (Nguồn, Nói, Quản lý, Thêm phím) có chữ dưới icon như các hàng khác.
- Thêm unit test: Python (`app/web/tests`), JavaScript (`node --test`), Kotlin (`:shared` và `:tv`).

**English**
- **🎧 Hear the TV on the phone / in the browser**, picked each time: **Only this device** (the TV's speakers go quiet, plug in headphones for late nights; stop and the TV has its sound back) or **TV and this device** (the TV keeps playing, needs Android 13 or newer on the TV; the device in your hand lags the TV a little, and some apps such as Netflix block this kind of capture; older TVs fall back to the first way and say so). The phone app and the computer's web use Opus 64 kbps, about 8 KB/s, some 30 times lighter than the picture, costing the TV a few % of one CPU core. The page served by the TV (plain HTTP, where browsers cannot decode Opus) uses 24 kHz mono PCM, about 48 KB/s, with no encoding on the TV. The phone app keeps playing with the phone locked (with the picture off), with a notification and a Stop button. Needs Android 11 or newer on the TV.
- **New ⏰ Automation tab** (where Terminal was; Terminal moved to the bottom of the Health tab):
  - **Sleep timer:** 15 minutes to 2 hours. With a minute left the TV shows a notice with "+15 min" / "Turn off now" / "Cancel".
  - **Bedtime:** between the set hours, a TV that is turned on goes back to standby a minute later.
  - **Daily app limits:** 5 minutes before, the TV says so; then the app closes. Grant 30 more minutes right on the page.
  - These three run on the TV itself in TV Setup (2.13.0 or newer), with no phone or computer on. The page can install / update TV Setup. It is not a hard parental lock: someone who knows how can uninstall TV Setup.
  - **Scenes:** one button, several things (wake/sleep the TV, open an app, set the volume, press keys, open a link, wait, sleep timer), with examples to start from. Scenes show as buttons on the Remote tab. **Export a backup file / import from a file** (.json; importing merges with the list, same id replaces). Each place keeps its own scenes: the computer in `tvs.json`, TV Setup on the TV, the phone app in the app; use export/import to copy between them.
- **Smarter talking to the TV:** "open YouTube", "mute", "volume up 5", "sleep in 30 minutes", "turn off the TV", a scene's name... are carried out at once without the Assistant (faster, and works on TVs without one and with keys only). Anything else goes to the TV as before.
- **Phone app:** a 🎧 button in the top bar; `⋯` gains 😴 Sleep timer and 🎬 Scenes. A home screen **widget** (power, mute, volume, the first 3 scenes), **Quick Settings** tiles "TV power" and "TV scene", and **shortcuts** when holding the app icon: they control the TV used last without opening the app.
- **iPhone / Android: add the page to the home screen** (PWA): an icon that opens full screen like an app. The page explains it once. On iPhone, opened from the icon it asks for the pairing code once more.
- **Apps tab:** **Disable / Turn on** buttons (unused system apps can be turned off and back on at any time; disabled apps always show dimmed at the end of the list; apps the TV needs, such as Google Play Services, the keyboard, the launcher and Settings, cannot be disabled). **🤖 Ask an AI which apps to disable / remove** copies a ready question with the TV model, Android version, RAM, user apps, system apps and what uses memory, to paste into ChatGPT / Claude / Gemini.
- Setting the volume now works on TVs that ignore the set command (HDMI-CEC): it reads the level back and presses +/− for the rest.
- Phone app: **Settings → The phone's volume keys** picks whether they set the TV's volume (as before) or the phone's. While 🎧 listening to the TV they always set the phone's.
- Phone app: the top row (Power, Speak, Manage, More keys) has names under the icons like the other rows.
- Unit tests: Python (`app/web/tests`), JavaScript (`node --test`), Kotlin (`:shared` and `:tv`).

## 2.15.1 - 2026-10-14

Web 2.15.1 · app điện thoại 2.13.4 · app TV 2.12.1

**Tiếng Việt**
- Sửa: TV Setup 2.12.0 hiện giao diện điện thoại trên TV 1080p (chiều ngắn của TV chỉ 540dp). Giờ chỉ máy không phải TV và màn nhỏ mới dùng giao diện một cột.

**English**
- Fixed: TV Setup 2.12.0 showed the phone layout on 1080p TVs (a TV's short side is only 540dp). Now only a device that is not a TV and has a small screen gets the one-column layout.

## 2.15.0 - 2026-10-14

Web 2.15.0 · app điện thoại 2.13.4 · app TV 2.12.0

**Tiếng Việt**
- **TV Setup trên điện thoại:** giao diện một cột cuộn được cho màn nhỏ (trước đây cột thao tác bị ép thành một vệt mỏng). Thứ tự: trạng thái, thẻ điều khiển từ thiết bị khác (mã QR), các ô thao tác 2 cột, nhật ký. TV và máy tính bảng giữ giao diện hai cột. Đã thử trên điện thoại Android 14 (emulator): ghép mã, xem hình dọc, quản lý app qua trình duyệt đều chạy.
- **Tối ưu TV chỉ chạy trên TV:** trên điện thoại hay máy tính bảng (máy báo không có tính năng TV), cả ba nơi đều từ chối và giải thích, vì việc này đổi màn hình chính và tắt app hệ thống. Hoàn tác vẫn dùng được.

**English**
- **TV Setup on a phone:** a scrolling one-column layout for small screens (the actions column used to be squeezed into a thin strip). Order: status, the card for control from another device (QR code), the action tiles in 2 columns, the log. TVs and tablets keep the two-column layout. Tried on an Android 14 phone (emulator): pairing, the upright picture and managing apps from a browser all work.
- **Optimize TV runs on TVs only:** on a phone or tablet (the device reports no TV feature) all three refuse and say why, since it swaps the home screen and turns system apps off. Undo still works.

## 2.14.3 - 2026-10-14

Web 2.14.3 · app điện thoại 2.13.3 · app TV 2.11.3

**Tiếng Việt**
- Cài TV Setup bằng nút remote (app điện thoại và web máy tính): khi TV đã có Downloader, app mở thẳng link riêng của Downloader `downloader://2397346`. Downloader tự tải TV Setup, không còn hộp “Mở bằng” và không phải gõ gì. Bước “Làm từng bước” của app điện thoại cũng dùng cách này; gõ mã vào ô địa chỉ chỉ còn là cách dự phòng.

**English**
- Installing TV Setup with the remote buttons (phone app and computer web): once the TV has Downloader, the app opens Downloader's own link `downloader://2397346`. Downloader fetches TV Setup by itself, with no “Open with” box and nothing typed. The phone app's “Step by step” uses it too; typing the code into the address field is only the fallback now.

## 2.14.2 - 2026-10-14

Web 2.14.2 · app điện thoại 2.13.2 · app TV 2.11.3

**Tiếng Việt**
- **Mã Downloader cho người mới:** trên TV cài app Downloader từ CH Play, nhập mã **2397346** là tải được TV Setup bản mới nhất; mã **2973135** cho app điện thoại TV Remote. Không cần máy tính hay điện thoại Android, nên người chỉ có iPhone cũng tự cài được.
- App điện thoại: bước “Làm từng bước” khi cài TV Setup giờ gõ mã 7 số vào Downloader thay cho đường dẫn dài, nhanh và ít sai hơn. Web máy tính: “Bật ADB giúp tôi” có thêm cách tự cài bằng mã này.
- README: thêm mục lục, mục “Cài TV Setup bằng mã Downloader”, thu gọn các phần dài. Thêm `CLAUDE.md` mô tả repo cho người sửa code.

**English**
- **Downloader codes for newcomers:** on the TV install the Downloader app from the Play Store and enter **2397346** to get the latest TV Setup; **2973135** is the TV Remote phone app. No computer or Android phone needed, so someone with only an iPhone can install it too.
- Phone app: the “Step by step” TV Setup install now types the 7-digit code into Downloader instead of the long link, faster and less error-prone. The computer web's “Turn ADB on for me” mentions installing by hand with the code.
- README: a table of contents, an “Install TV Setup with a Downloader code” section, long parts folded. Added `CLAUDE.md`, a guide to the repo for people changing the code.

## 2.14.1 - 2026-10-14

Web 2.14.1 · app điện thoại 2.13.1 · app TV 2.11.2

**Tiếng Việt**
- Sửa (app điện thoại): đã tắt hình, chỉ dùng nút bấm, mà sang app khác rồi quay lại thì dòng trên cùng đứng ở “Đang mở hình từ ...”. Khi tắt hình, app giờ giữ kết nối lúc ở ngoài (tối đa 10 phút) nên quay lại là bấm được ngay; nếu phải kết nối lại thì ghi “Đang mở điều khiển” và không còn bị kẹt ở dòng đó.

**English**
- Fixed (phone app): with the picture off, using buttons only, switching to another app and back left the top line at “Opening the picture from ...”. With the picture off the app now keeps the connection while away (up to 10 minutes), so the buttons work straight away on return; when it does have to reconnect it says “Opening the controls” and no longer sticks there.

## 2.14.0 - 2026-10-14

Web 2.14.0 · app điện thoại 2.13.0 · app TV 2.11.2

**Tiếng Việt**
- **Màn hình chờ khi kết nối TV** (app điện thoại và web máy tính): trước đây app vào thẳng màn hình điều khiển, chỉ có dòng “Đang kết nối...” nhỏ ở trên, trong khi các nút chưa bấm được. Giờ là màn hình riêng có tên TV cỡ lớn, đang làm bước nào (tìm TV, chờ cho phép trên TV, mở hình, thử chế độ chỉ bấm phím) và nút Hủy. Màn hình tự đóng khi TV đã sẵn sàng.
- Sửa: xem hình TV này, thoát ra rồi mở TV khác thì khung hình vẫn đứng ở hình cuối của TV trước. Giờ khung hình được xóa mỗi khi ngắt kết nối.

**English**
- **A waiting screen while connecting to a TV** (phone app and computer web): the app used to go straight to the remote with a small “Connecting...” line on top while none of the buttons worked yet. Now a screen of its own shows the TV's name in large type, the current step (looking for the TV, waiting for the TV to allow it, opening the picture, trying keys only) and a Cancel button. It closes by itself once the TV is ready.
- Fixed: after watching one TV, leaving and opening another, the picture stayed frozen on the first TV's last frame. The picture is now cleared on every disconnect.

## 2.13.1 - 2026-10-14

Web 2.13.1 · app điện thoại 2.12.1 · app TV 2.11.1

**Tiếng Việt**
- Sửa: trong Quản lý TV → Ứng dụng, **logo dạng ảnh ngang của app TV bị co nhỏ** giữa một khung viền dày. Ô logo dùng nhầm tên class của dải báo ADB nên bị thêm lề và viền của dải đó.

**English**
- Fixed: in Manage TV → Apps, **an app's wide TV banner was shrunk** inside a thick frame. The icon box shared a class name with the ADB notice and picked up its padding and border.

## 2.13.0 - 2026-10-14

Web 2.13.0 · app điện thoại 2.12.0 · app TV 2.11.0

**Tiếng Việt**
- **App TV: màn hình Tối ưu TV làm lại** theo giao diện mới của app: các dòng tích bằng phím mũi tên và OK, chia mục Chung / App đề xuất / App khác, đếm “Sẽ tắt N app”, danh sách tự cuộn, nút Chạy tối ưu và Hủy rõ ràng cái nào đang chọn.
- Sửa: **tìm app trên CH Play** (app điện thoại và web) mở ra lỗi “Tìm kiếm trên cửa hàng Chrome không khả dụng”. CH Play trên TV chỉ nhận tìm kiếm dạng `market://search`, giờ dùng đúng dạng đó.
- Sửa: **phát ảnh / video từ điện thoại** báo “Cần ADB” dù đang có ADB. Mở trình chọn tệp làm app tạm dừng phiên xem hình; khi quay lại, app tưởng chưa có ADB. Giờ chỉ chặn khi TV đang ở chế độ chỉ có nút remote.
- **Chọn ảnh / video bằng sheet thư viện ảnh** của hệ thống, nổi trên app, không rời khỏi remote, không cần quyền đọc ảnh. Android 12 trở xuống dùng bản của Google Play services; không có thì mới dùng trình chọn tệp. Nhạc và tệp khác có mục riêng “Phát nhạc / tệp khác”.
- TV không có app mở được tệp (Google TV trả lời bằng một app giả không làm gì) thì báo rõ “TV không có app mở loại tệp này”, thay vì báo đang phát.

**English**
- **TV app: the Optimize TV screen redone** in the app's new look: rows ticked with the arrows and OK, sections General / Suggested apps / Other apps, an “N apps will be turned off” count, a scrolling list, and Optimize / Cancel buttons where it is clear which one is selected.
- Fixed: **searching the Play Store** (phone app and web) opened a “Chrome Web Store search is unavailable” error. The TV's Play Store takes searches only as `market://search`, which is used now.
- Fixed: **playing a photo / video from the phone** said “Needs ADB” while ADB was there. The file picker paused the picture session, and on return the app thought ADB was missing. It now refuses only in keys-only mode.
- **Photos and videos are picked in the system gallery sheet**, over the app, without leaving the remote and without a photo permission. Android 12 and older use Google Play services' version; only without it the file picker. Music and other files have their own “Play a song / other file” item.
- When the TV has no app for a file (Google TV answers with a do-nothing stub), it now says “The TV has no app for this kind of file” instead of claiming to play it.

## 2.12.0 - 2026-10-14

Web 2.12.0 · app điện thoại 2.11.0 · app TV 2.10.0

**Tiếng Việt**
- **Không ở nhà mà TV hỏi “Cho phép gỡ lỗi USB?”: TV Setup tự bấm cho phép, an toàn.** App điện thoại và web máy tính, mỗi lần đã có ADB, tự xin TV Setup một “thẻ” (qua một broadcast chỉ lệnh shell của ADB gửi được). Lần sau TV hỏi (TV quên máy, Android 11+ tự thu hồi sau 7 ngày...), chúng nhờ TV Setup đọc hộp hỏi và chỉ bấm khi mã khóa trong đó **trùng mã của chính máy đang xin**; tích “Luôn cho phép”, bấm OK, rồi hiện thông báo trên TV. Hộp hỏi cũ của máy khác đang xếp hàng (TV chỉ hiện một hộp mỗi lúc) được từ chối để tới lượt máy mình; từ chối không cho ai vào. Thử trên emulator: khóa mới, không ai chạm TV, vào được sau khoảng 8 giây.
- **Dải báo “TV đang hỏi... cho một thiết bị”** trên trang nào đang có ADB (trang của TV, web máy tính, app điện thoại): hiện mã khóa, nút Cho phép (hỏi xác nhận) / Từ chối. Màn hình chờ của app điện thoại hiện mã của chính máy để so.
- **Tối ưu TV: chọn việc cần làm.** Hộp chọn ở cả ba nơi và trong app TV: tắt hiệu ứng, dùng FLauncher, tắt app đề xuất (chỉ những app TV có, tích sẵn) và **tắt thêm app khác** tùy ý (mọi app có biểu tượng, có ô lọc). App quan trọng không bao giờ có trong danh sách. Hoàn tác bật lại cả app tự chọn thêm.

**English**
- **Away from home and the TV asks “Allow USB debugging?”: TV Setup presses Allow, safely.** The phone app and the computer web, whenever they have ADB, fetch a “pass” from TV Setup (through a broadcast only ADB's shell can send). The next time the TV asks (it forgot the device, Android 11+ revokes after 7 days...), they have TV Setup read the question and press only when its key code **matches the asking device's own**; it ticks “Always allow”, presses OK and shows a notice on the TV. Older questions from other devices waiting in line (the TV shows one at a time) are declined so ours comes up; declining lets nobody in. Tested on an emulator: new key, nobody touching the TV, connected in about 8 seconds.
- **A “The TV is asking... for a device” bar** on any page that has ADB (the TV's page, the computer web, the phone app): shows the key code, Allow (with a confirmation) / Decline. The phone app's waiting screen shows its own code to compare.
- **Optimize TV: choose what to do.** A choice box on all three and in the TV app: animations off, FLauncher, suggested apps (only the ones the TV has, ticked) and **turn off other apps** as you like (every app with an icon, with a filter). Essential apps are never listed. Undo also turns back on the apps you added.

## 2.11.1 - 2026-10-14

Web 2.11.1 · app điện thoại 2.10.0 · app TV 2.9.1

**Tiếng Việt**
- App TV (tiếng Anh): ô “ADB debugging” bị cắt chữ, giờ là “Debugging” · “Open Developer options for ADB”.
- README: thêm ảnh màn hình app TV Setup mới.

**English**
- TV app (English): the “ADB debugging” tile was cut off; it now reads “Debugging” · “Open Developer options for ADB”.
- README: a screenshot of the new TV Setup app screen.

## 2.11.0 - 2026-10-14

Web 2.11.0 · app điện thoại 2.10.0 · app TV 2.9.0

**Tiếng Việt**
- **App TV có giao diện mới.** Thẻ trạng thái có chấm màu; 6 ô thao tác lớn có biểu tượng và một dòng mô tả (Tối ưu TV, Tailscale, Hoàn tác, Cập nhật, Gỡ lỗi ADB, Kết nối lại); khung “Hoạt động” riêng; cột phải “Điều khiển từ điện thoại” có mã QR. Màn hình ghép mã cũng làm lại, mã to dễ đọc. App TV nói tiếng Anh khi TV không đặt tiếng Việt.
- **Ô đang chạy hiện vòng xoay** và tiến độ (“Đang tải 40%”, “Đang cài...”, “Đang bật...”); các ô khác mờ đi chờ.
- **Không còn treo “Đang làm...” mãi mãi.** Mọi lệnh gửi TV giờ có giới hạn thời gian: TV ngừng trả lời thì báo lỗi và trả nút lại. Trang web máy tính/TV/điện thoại: mạng chập chờn khi đang theo dõi một việc không còn kết thúc giữa chừng; mất hẳn kết nối thì báo rõ.
- Sửa: nút Tailscale trên app TV đứng yên khi cài (chép 100 MB qua ADB bị kẹt). Giờ cài thẳng từ tệp đã tải, nhanh hơn nhiều; chép tệp mà hỏng thì tự thử lại một lần và không để lại tệp dở.
- **Lỗi dễ hiểu hơn**: ngày giờ TV sai (lỗi chứng chỉ), TV chưa vào Internet, mạng chậm, TV hết bộ nhớ... được nói thành câu hướng dẫn thay vì tên lỗi kỹ thuật.
- **Tailscale tự chạy lại khi bật TV** được làm ngay trong bước cài Tailscale, không cần chạy Tối ưu TV nữa. Mô tả Tối ưu TV gọn lại: “Bớt hiệu ứng và app thừa”.
- **App điện thoại nhớ IP Tailscale của TV.** Kết nối TV một lần ở nhà, app ghi lại IP Tailscale của nó; ra ngoài, bấm TV đã lưu là app tự đi qua Tailscale (dòng trạng thái ghi “kết nối được qua Tailscale”). Android không cho app khác xem danh sách máy Tailscale nên không quét được như web máy tính.
- Rà chính tả toàn bộ: thống nhất “Optimize”, tên tab “Setup”, bổ sung tiếng Anh cho trợ lý bật ADB và vài thông báo còn sót.

**English**
- **A new TV app screen.** A status card with a coloured dot; six big action tiles with an icon and one line each (Optimize TV, Tailscale, Undo, Update, ADB debugging, Reconnect); a separate “Activity” box; a right-hand “Control from your phone” card with the QR code. The pairing screen is redone too, with a big code. The TV app speaks English when the TV is not set to Vietnamese.
- **The running tile shows a spinner** and progress (“Downloading 40%”, “Installing...”, “Switching on...”); the other tiles dim while waiting.
- **No more endless “Working...”.** Every command sent to the TV now has a time limit: when the TV stops answering, the error is shown and the buttons come back. On the web page (computer, TV, phone), a network blip while following a job no longer ends it early; a lost connection is said plainly.
- Fixed: the Tailscale button in the TV app stood still while installing (copying 100 MB over ADB stalled). It now installs straight from the downloaded file, much faster; a failed copy is retried once and leaves no half file behind.
- **Clearer errors**: a wrong date on the TV (certificate errors), no Internet, a slow network, a full TV... are told as advice instead of a technical error name.
- **Tailscale starting again whenever the TV turns on** is now part of the Tailscale install itself; Optimize TV is no longer needed for that. Its description is shorter: “Fewer animations and extra apps”.
- **The phone app remembers the TV's Tailscale IP.** Connect to the TV once at home and the app notes its Tailscale IP; away from home, tapping the saved TV goes through Tailscale (the status says “reachable through Tailscale”). Android does not let other apps list Tailscale devices, so the phone cannot scan for them like the computer web does.
- Proofread throughout: “Optimize” everywhere, the “Setup” tab name, English for the ADB assistant and a few messages that lacked it.

## 2.10.0 - 2026-10-14

Web 2.10.0 · app điện thoại 2.9.0 · app TV 2.8.0

**Tiếng Việt**
- **Tailscale một nút.** Tab Thiết lập (cả ba nơi) và nút Tailscale trong app TV: tự tải Tailscale bản chính thức lên TV (kiểm checksum), cài, bỏ qua hộp hỏi VPN, bật lên, rồi đọc link đăng nhập ngay trên màn hình TV. Bạn chỉ bấm “Mở trang đăng nhập Tailscale” (hoặc quét mã QR) và Connect bằng tài khoản của mình; trang tự nhận khi TV đã vào. Không phải gõ gì trên TV, không gửi mật khẩu cho app nào. Trên app điện thoại, link mở trong trình duyệt của máy.
- Tab “Tối ưu” trên trang của TV và app điện thoại đổi thành “Thiết lập” (Tailscale + Tối ưu TV).
- Sửa: trên trang do TV phục vụ, hình trực tiếp bị phóng to và cắt mất mép; giờ hiện trọn khung.
- Sửa: lỡ đóng hộp “Cho phép gỡ lỗi USB?” trên TV (Back, Home, Hủy) thì app điện thoại và app TV chờ suông 3 phút; giờ tự hỏi lại TV (tối đa 3 lần).
- “Tối ưu TV” trên máy tính in nhật ký tiếng Việt gọn như hai app (trước là tiếng Anh với 16 dòng “skip”); báo rõ khi TV chỉ chạy app 64-bit nên không cài được FLauncher.
- Thanh trên của web máy tính: TV nối được ADB nhưng chưa nối nút remote thì báo “Nút remote: chưa nối” thay cho “TV: mất kết nối” và lỗi kỹ thuật.
- Màn hình đứng yên thì ghi “Trực tiếp · hình đứng yên” thay cho “đang chờ hình”.
- Trên điện thoại: hàng nút đầu trang nằm gọn một dòng, ô gõ chữ có chữ gợi ý ngắn, bỏ câu “bàn phím máy tính gõ thẳng lên TV” trên máy cảm ứng, bảng Phần cứng không còn bẻ chữ từng ký tự. Trang TV không hiện “127.0.0.1” và các nhãn “cần ADB” vô nghĩa ở đó.
- App TV: dòng trạng thái ghi rõ “✓ ADB đã kết nối”; hướng dẫn bật Gỡ lỗi ADB chỉ hiện khi chưa nối được.
- README: luồng dùng lần đầu (sơ đồ), Tailscale một nút, tên tab mới, ảnh chụp mới cho cả ba nơi.

**English**
- **Tailscale in one button.** The Setup tab (all three) and the Tailscale button in the TV app: downloads the official Tailscale onto the TV (checksum checked), installs it, skips the VPN question, switches it on, then reads the sign-in link off the TV's screen. You just press “Open the Tailscale sign-in page” (or scan the QR code) and Connect with your account; the page notices when the TV is in. Nothing typed on the TV, no password given to any app. In the phone app the link opens in the phone's browser.
- The “Optimize” tab on the TV's page and in the phone app is now “Setup” (Tailscale + Optimize TV).
- Fixed: on the page served by the TV, the live picture was zoomed in and cropped; it now fits the box.
- Fixed: closing the TV's “Allow USB debugging?” question by mistake (Back, Home, Cancel) left the phone and TV apps waiting 3 minutes for nothing; they now ask the TV again (up to 3 times).
- “Optimize TV” on the computer logs short Vietnamese/English lines like the two apps (it was English with 16 “skip” lines), and says plainly when the TV runs 64-bit apps only and cannot take FLauncher.
- Top bar of the computer web: a TV reachable over ADB but without the remote-button service now shows “Remote buttons: not connected” instead of “TV: disconnected” and a technical error.
- A still picture reads “Live · picture unchanged” instead of “waiting for a picture”.
- On phones: the top buttons fit on one line, shorter hints in the text boxes, no “type on the computer keyboard” line on touch devices, the Hardware table no longer breaks words letter by letter. The TV's page no longer shows “127.0.0.1” or pointless “needs ADB” labels.
- TV app: the status line says “✓ ADB connected”; the steps to turn on ADB debugging only show when it cannot connect.
- README: the first-time flow (diagram), one-button Tailscale, the new tab names, new screenshots of all three.

## 2.9.0 - 2026-10-14

Web 2.9.0 · app điện thoại 2.8.0 · app TV 2.7.0

**Tiếng Việt**
- **Một giao diện web cho cả ba nơi.** Trang trên máy tính, trang do TV phục vụ (cổng 8765, mở bằng mã QR) và phần Quản lý trong app điện thoại giờ là cùng một bộ mã (`app/shared/web`); mỗi nơi chỉ khác phần lõi trả lời `/api`. Từ nay một thay đổi giao diện có mặt ở cả ba nơi cùng lúc.
- Trang do TV phục vụ nhờ vậy có thêm: chọn chất lượng hình 240p đến 960p và chế độ ảnh chụp, xem hình nổi ở góc khi sang tab khác, ô tìm app, sắp xếp và kéo thả tệp, xem trước ảnh, tab “Tệp · Cài” gộp, hộp Giới thiệu kèm mã QR tải app. Ghép bằng mã 6 số như cũ.
- Phần Quản lý trong app điện thoại dùng giao diện mới này (Ứng dụng, Tệp · Cài, Sức khỏe, Terminal, Tối ưu).
- **“Nhìn lên TV” thành cảnh báo toàn màn hình** (app điện thoại và web): khi TV hỏi “Cho phép gỡ lỗi USB?”, cả màn hình hiện hướng dẫn tích “Luôn cho phép” rồi chọn OK, và tự đóng khi TV đã cho phép. Trước đây chỉ là một dòng chữ nhỏ ở đầu màn hình.
- Sửa lỗi app điện thoại bị đơ (“không phản hồi”) khi thoát khỏi một TV đang chờ cho phép gỡ lỗi.
- Trên điện thoại, tab Remote của trang web: ô màn hình TV gọn lại khi chưa xem, lưới app nằm dưới các nút, tab có tên nhỏ dưới biểu tượng.
- Sửa dòng “Trực tiếp · đang chờ hình” hiện cả khi không xem màn hình.

**English**
- **One web UI for all three places.** The page on the computer, the page the TV serves (port 8765, opened from the QR code) and Manage in the phone app are now the same code (`app/shared/web`); only the core answering `/api` differs. A UI change now lands in all three at once.
- The page served by the TV gains: picture quality from 240p to 960p and a screenshots mode, a floating picture on the other tabs, app search, file sorting, drag and drop and image preview, the merged “Files · Install” tab, and the About box with the QR code for the app. Pairing with the 6-digit code is unchanged.
- Manage in the phone app uses this UI (Apps, Files · Install, Health, Terminal, Optimize).
- **“Look at the TV” is now a full-screen alert** (phone app and web): when the TV asks “Allow USB debugging?”, the whole screen says to tick “Always allow” and choose OK, and closes by itself once the TV has allowed it. It used to be one small line at the top.
- Fixed the phone app freezing (“isn’t responding”) when leaving a TV that was still waiting for that permission.
- On a phone, the web page’s Remote tab: the TV picture box folds away until you watch, the app grid sits below the buttons, tabs carry their name under the icon.
- Fixed “Live · waiting for a picture” showing while not watching.

## 2.8.2 - 2026-10-14

Web 2.8.2 · app điện thoại 2.7.2 · app TV 2.6.10

**Tiếng Việt**
- Trang remote do TV phục vụ (mở bằng mã QR, cổng 8765): ô **“Mở trên TV”** nằm ngay ở tab Remote như app điện thoại và web trên máy tính, thay cho ô “Phát lên TV” khuất trong tab Cài. Thêm ô **tìm app trên CH Play / mở theo app id**.
- Cũng trang đó, trên màn hình rộng: thanh tab trải hết chiều ngang, cột bên phải không còn bị bóp hẹp.

**English**
- The remote page served by the TV (opened from the QR code, port 8765): the **“Open on the TV”** box now sits on the Remote tab, as in the phone app and the computer web UI, instead of “Play on the TV” tucked away in the Install tab. Adds a **Play Store search / app id** box.
- Same page on wide screens: the tab bar spans the full width and the right column is no longer squeezed.

## 2.8.1 - 2026-10-14

Web 2.8.1 · app điện thoại 2.7.1 · app TV 2.6.9

**Tiếng Việt**
- Luồng “Tự động: cài TV Setup lên TV” (web và app điện thoại): nút **“Đã cài xong” chỉ nhận một lần bấm** rồi mờ đi kèm dòng “đang kiểm tra” cho tới khi kiểm tra xong. Trước đây bấm nhiều lần làm TV mở Downloader và nhận link nhiều lần.
- Hộp thoại giữ nguyên kích thước khi đổi lời nhắn, nên nút không trượt đi dưới ngón tay. Trên điện thoại, chạm ra ngoài hộp không còn làm dừng cả quá trình.
- Mỗi lúc chỉ một việc chạy trên TV: bấm lại “Mở Downloader”, “Mở TV Setup”, “Mở lại”... khi việc trước chưa xong thì được báo chờ, không gửi lệnh chồng lên nhau.
- Web: đóng hộp thoại bằng cách nào (nút Đóng, phím Esc) cũng dừng việc đang chạy; bấm Dừng rồi chạy lại ngay vẫn được.

**English**
- “Automatic: install TV Setup on the TV” (web and phone app): the **“Installed” button takes one press**, then greys out with a “checking” line until the check is over. Before, pressing it repeatedly made the TV open Downloader and receive the link several times.
- The box keeps its size as its words change, so the buttons do not slide from under a finger. On the phone, a tap beside the box no longer stops the whole run.
- One job on the TV at a time: pressing “Open Downloader”, “Open TV Setup”, “Open again”... while the previous one is still running says to wait instead of piling commands up.
- Web: closing the dialog in any way (Close, Esc) stops what was running; Stop followed at once by a new run works.

## 2.8.0 - 2026-10-14

Web 2.8.0 · app điện thoại 2.7.0 · app TV 2.6.9

**Tiếng Việt**
- **Phát lên TV** ở cả ba bản. App điện thoại: nút Chia sẻ ở app khác → TV Remote (link thì TV mở, ảnh / video / nhạc thì chép sang TV rồi TV tự phát), thêm mục “Phát ảnh / video / nhạc từ điện thoại” và “Chiếu màn hình điện thoại lên TV” (mở phần Truyền của Android) trong “Mở trên TV”. Web máy tính: nút “Phát ảnh, video, nhạc từ máy này lên TV” cạnh ô mở link. Trang do TV phục vụ: khung “Phát lên TV” (link hoặc tệp) ở tab Cài. Phát tệp cần ADB.
- App điện thoại, quét Wi-Fi: chờ TV trả lời lâu hơn, nghe thêm lời TV tự giới thiệu trong mạng (mDNS) nên không còn phải quét vài lần mới thấy; mỗi TV tìm được ghi rõ ADB đã bật hay chưa; khi không thấy gì thì nói đã quét mạng nào và nhắc TV qua Tailscale phải nhập IP.
- Web máy tính: quét mạng hiện tên TV cả khi TV chưa bật ADB. Tab “Tải / Cài” gộp vào tab “Tệp · Cài”; “Tối ưu TV” chỉ còn một chỗ, ở tab Thiết lập. Khung “Tiến trình tải lên / cài đặt” gập lại được. Sửa tab Sức khỏe bị dồn sang phải và dính nhau.

**English**
- **Play on the TV** in all three. Phone app: Share in another app → TV Remote (the TV opens a link; a photo / video / song is copied to the TV, which plays it), plus “Play a photo / video / song from the phone” and “Mirror the phone’s screen on the TV” (opens Android’s Cast screen) under “Open on the TV”. Computer web: a “Play a photo, video or song from this computer on the TV” button next to the link box. The TV-served page: a “Play on the TV” card (link or file) on the Install tab. Playing a file needs ADB.
- Phone app, Wi-Fi scan: waits longer for each TV and also listens for TVs announcing themselves (mDNS), so one scan is enough; each TV found says whether ADB is on; when nothing is found it says which network was scanned and that a TV reached through Tailscale needs its IP typed.
- Computer web: the scan shows a TV’s name even with ADB off. The “Download / Install” tab is folded into “Files · Install”; “Optimize TV” lives in one place, the Setup tab. The upload / install progress card can be collapsed. Fixed the Health tab being pushed to the right with its sections stuck together.

## 2.7.6 - 2026-10-14

Web 2.7.6 · app điện thoại 2.6.3 · app TV 2.6.8

**Tiếng Việt**
- Web trên máy tính: nút **“Bật ADB giúp tôi”** trên banner “Cổng ADB đang đóng” và ở tab Thiết lập, với đủ các cách như app điện thoại: tự động cài TV Setup lên TV, mở Cài đặt TV, cài màn hình chính khác, mở CH Play, mở app theo tên gói. Chỉ cần nút remote đã ghép (cổng 6466); khi TV bật ADB xong trang tự nhận ra.
- Khi ADB chưa mở, phần **Bàn phím bị khóa** kèm lời giải thích, thay vì cho gõ mà không có tác dụng. Chữ gõ từ bàn phím máy tính và dán cũng không gửi đi nữa; mũi tên, Enter, Esc vẫn chạy.
- Hộp Giới thiệu có **mã QR tải app Android**, và mã QR mở trang remote do TV phục vụ khi app TV Setup trên TV đang bật trang đó.

**English**
- Computer web: a **“Turn ADB on for me”** button on the “ADB port closed” banner and in the Setup tab, with the same ways as the phone app: install TV Setup on the TV automatically, open TV Settings, install another home screen, open the Play Store, open an app by package name. It only needs the paired remote buttons (port 6466); the page notices once the TV has ADB on.
- With ADB closed the **Keyboard section is locked**, with the reason, instead of accepting typing that goes nowhere. Letters typed on the computer keyboard and pastes are not sent either; arrows, Enter and Esc still work.
- The About box has a **QR code for the Android app**, and one for the remote page served by the TV when the TV Setup app has it on.

## 2.7.5 - 2026-10-13

Web 2.7.5 · app điện thoại 2.6.3 · app TV 2.6.8

**Tiếng Việt**
- Web trên máy tính: **sửa lỗi luồng hình “nhẹ” chưa bao giờ chạy từ bản 2.3.0**. Trang trỏ nhầm chỗ file scrcpy nên luôn âm thầm rơi về cách quay nặng (1280×720, không giới hạn), làm TV chậm và tốn mạng; điều khiển bằng chuột và bàn phím trên hình cũng hỏng theo. Đo trên TV Sony trong 8 giây: trước 4,6 MB, giờ 0,2 MB ở 240p và 1,7 MB ở 960p.
- **Chọn chất lượng hình** như app điện thoại: 240p, 360p, 480p, 720p, 960p, thêm “Nét nhất” và “Ảnh chụp”. Mức chọn được nhớ lại.
- **Luôn mở ở màn Chọn TV**, kể cả khi đã có TV lưu. Chưa lưu TV nào thì không hiện khung “TV đã lưu” trống; nút “Quay lại” chỉ hiện khi vừa bấm “Đổi TV”. Chọn TV xong, trang chờ TV trả lời vài giây rồi mới quyết định mở Remote hay Thiết lập.
- Bỏ thanh trượt âm lượng (đã có nút + và −).
- Ô **CH Play** cạnh ô mở link: gõ tên app để TV tìm trên CH Play, hoặc app id để mở thẳng trang của app.

**English**
- Computer web: **fixed the “light” picture stream, which had never run since 2.3.0**. The page looked for the scrcpy file in the wrong place and silently fell back to the heavy recorder (1280×720, uncapped), slowing the TV and using far more data; mouse and keyboard on the picture were broken with it. Measured on the Sony TV over 8 seconds: 4.6 MB before, now 0.2 MB at 240p and 1.7 MB at 960p.
- **Picture quality levels** as in the phone app: 240p, 360p, 480p, 720p, 960p, plus “Sharpest” and “Screenshots”. The choice is remembered.
- **Always opens on the Choose-a-TV screen**, also when a TV is saved. With nothing saved there is no empty “Saved TVs” box; “Back” only shows after “Change TV”. After choosing, the page gives the TV a few seconds to answer before deciding between Remote and Setup.
- The volume slider is gone (the + and − buttons remain).
- A **Play Store** box next to the link box: type an app’s name for the TV to search the store, or an app id to open its page.

## 2.7.4 - 2026-10-13

Web 2.7.4 · app điện thoại 2.6.3 · app TV 2.6.8

**Tiếng Việt**
- Web trên máy tính: sửa lỗi sau khi cập nhật, trình duyệt ghép trang mới với script và CSS cũ còn lưu, nên màn “Chọn TV” hiện lẫn với các tab và các khung dính vào nhau. Các file này giờ mang dấu phiên bản, trình duyệt luôn tải đúng bản mới.
- App điện thoại: mỗi TV đã dùng có **nút ✕ để xóa** khỏi danh sách (trước đây phải biết bấm giữ). Xóa TV cũng quên luôn việc đã ghép mã với nó.

**English**
- Computer web: fixed the browser pairing the new page with scripts and styles it had kept from before an update, which showed the “Choose a TV” screen together with the tabs, its sections stuck together. Those files now carry a version stamp, so the browser always loads the current ones.
- Phone app: each saved TV has an **✕ button to remove** it from the list (before, one had to know to long-press). Removing a TV also forgets its pairing.

## 2.7.3 - 2026-10-13

Web 2.7.3 · app điện thoại 2.6.2 · app TV 2.6.8

**Tiếng Việt**
- Web trên máy tính: **màn “Chọn TV” đi trước**, như app điện thoại. Khi chưa chọn TV nào, trang chỉ hiện danh sách TV đã lưu, phần tìm TV (tự quét một lần khi mở) và ô nhập IP; các tab, remote và trạng thái ADB ẩn hết. Bấm vào một TV là vào điều khiển: TV sẵn sàng thì mở Remote, chưa thì mở các bước thiết lập lần đầu.
- Nút **“Đổi TV”** trên đầu trang quay lại danh sách đó, có lối “Quay lại” TV đang dùng. Tab “TV” cũ đổi tên thành “Thiết lập” và chỉ còn các bước cài đặt lần đầu.

**English**
- Computer web: the **“Choose a TV” screen comes first**, as in the phone app. With no TV chosen the page shows only the saved TVs, the search (it scans once by itself) and the IP box; the tabs, the remote and the ADB status are hidden. Clicking a TV goes to it: the Remote when it is ready, the first-time steps when it is not.
- A **“Change TV”** button in the header returns to that list, with a way back to the TV in use. The old “TV” tab is now “Setup” and holds only the first-time steps.

## 2.7.2 - 2026-10-13

Web 2.7.2 · app điện thoại 2.6.2 · app TV 2.6.8

**Tiếng Việt**
- **App TV hiện mã QR** ở cạnh phải màn hình chính: một mã để tải app điện thoại TV Remote, một mã mở remote ngay trong trình duyệt (địa chỉ của TV, không phải gõ). Người chưa cài gì chỉ cần giơ camera điện thoại lên TV.
- App điện thoại, phần Giới thiệu: mã QR và nút chia sẻ đường dẫn tải app, để đưa app cho người khác.
- App TV: dòng “Mở trên điện thoại / máy tính” hiện đúng địa chỉ của TV cả khi chưa bật ADB. Chỉ tự mở Tùy chọn nhà phát triển khi ADB đang tắt (không mở khi người dùng bấm từ chối), và chỉ tự kết nối lại một lần khi quay về.

**English**
- **The TV app shows QR codes** down the right edge of its main screen: one to download the TV Remote phone app, one that opens the remote in a browser (the TV’s address, nothing to type). Someone with nothing installed just points their phone’s camera at the TV.
- Phone app, About: a QR code and a share button for the app’s download link, to pass the app on.
- TV app: the “Open on a phone / computer” line shows the TV’s address even before ADB is on. Developer options only open by themselves when ADB is off (not after the person declined the prompt), and the reconnect on return happens once.

## 2.7.1 - 2026-10-13

Web 2.7.1 · app điện thoại 2.6.1 · app TV 2.6.7

Rà lại luồng bật ADB cho TV chưa bật, bớt các bước phải làm tay. / A pass over the “turn ADB on” flow to cut the manual steps.

**Tiếng Việt**
- App điện thoại **tự chuyển sang kết nối đầy đủ** ngay khi TV bật ADB, không cần quay lại menu bấm “Kết nối đầy đủ”.
- Menu “Bật ADB cho TV này”: lối tự động (cài TV Setup) lên đầu; các lối tự làm xếp sau.
- Khi TV chưa có Downloader: cài xong thì app đưa thẳng đường dẫn tải cho Downloader, không còn phải lái con trỏ của nó tới ô địa chỉ.
- Mở app theo tên gói và mở màn hình chính khác giờ đi qua trang CH Play khi TV không chịu mở theo tên (box FPT Play).
- TV chịu nhận phím nhưng không cho ADB (cổng mở mà chưa được cho phép): vẫn vào được chế độ chỉ bấm phím thay vì chỉ báo lỗi.
- App TV: quay lại từ Tùy chọn nhà phát triển thì tự kết nối lại, không cần chọn “Kết nối lại”.

**English**
- The phone app **switches to the full connection by itself** as soon as the TV turns ADB on; no trip back to the menu.
- “Turn on ADB for this TV” menu: the automatic route (installing TV Setup) comes first, the by-hand ones after it.
- When the TV has no Downloader yet: once installed, the app hands it the download link directly instead of steering its pointer to the address field.
- Opening an app by package name, and the other home screens, go through the Play Store page when the TV will not open by name (FPT Play box).
- A TV that takes keys but refuses ADB (port open, not allowed yet) still gets the keys-only remote instead of just an error.
- TV app: coming back from Developer options reconnects by itself.

## 2.7.0 - 2026-10-13

Web 2.7.0 · app điện thoại 2.6.0 · app TV 2.6.6

**Tiếng Việt**
- App điện thoại: nút **“Mở trên TV”** trong phần Thêm phím, dùng được cả khi chưa bật ADB: tìm app trên CH Play bằng cách gõ tên ở điện thoại, mở trang CH Play theo app id, mở một đường dẫn (video YouTube, trang web...), và lối tắt tới trang CH Play của vài app hay dùng.
- “Cài TV Setup lên TV” đi đường ngắn trước: gửi thẳng đường dẫn tải cho TV. TV có app nhận đường dẫn web (Downloader, trình duyệt) sẽ mở và tải luôn; nếu TV hỏi “Mở bằng” thì app tự chọn. Không được mới quay về cách nhập qua Downloader.
- Di chuyển con trỏ trong Downloader bằng nhiều nhịp ngắn thay vì một lần giữ dài (qua giao thức này phím không giữ được).

**English**
- Phone app: an **“Open on the TV”** button among the extra keys, also without ADB: search the Play Store by typing on the phone, open a Play Store page by app id, open a link (a YouTube video, a web page...), and shortcuts to the Play Store pages of a few common apps.
- “Install TV Setup on the TV” takes the short way first: it hands the TV the download link. A TV with an app that takes web links (Downloader, a browser) opens and downloads it; when the TV asks “Open with”, the app chooses. Only if that fails does it type the link into Downloader.
- The pointer in Downloader is moved with many short holds instead of one long one (a key cannot be kept down over this protocol).

## 2.6.9 - 2026-10-13

Web 2.6.9 · app điện thoại 2.5.9 · app TV 2.6.6

**Tiếng Việt**
- App điện thoại, “Cài TV Setup lên TV”: sửa lỗi báo “TV chưa có Downloader” dù đã cài. Một số TV (box FPT Play) không mở app khi được gọi theo tên; app giờ mở trang CH Play của Downloader rồi bấm nút chính trên đó (“Mở” khi đã cài, “Cài đặt” khi chưa). Khi phải cài, có nút “Đã cài xong” để đi tiếp.

**English**
- Phone app, “Install TV Setup on the TV”: fixed “the TV has no Downloader” being reported although it is installed. Some TVs (FPT Play box) do not open an app asked for by name; the app now opens Downloader’s Play Store page and presses its main button (“Open” when installed, “Install” when not). When it has to be installed, an “Installed” button carries on.

## 2.6.8 - 2026-10-13

Web 2.6.8 · app điện thoại 2.5.8 · app TV 2.6.6 (app TV không đổi chức năng / no functional change)

**Tiếng Việt**
- App điện thoại, “Cài TV Setup lên TV”: lượt tự động giờ **tự đưa tới ô địa chỉ của Downloader** (trang chủ của nó chỉ có con trỏ, không ô nào được chọn sẵn), gõ đường dẫn, bấm Enter và chờ tới khi TV hỏi cài đặt. Đã chạy trọn trên TV giả lập Google TV với Downloader thật.
- Gõ chữ ở chế độ chỉ bấm phím: ưu tiên gửi thành từng phím (cách Downloader nhận được); chỉ dùng bàn phím hệ thống của TV cho chữ có ký tự không gõ được bằng phím (chữ hoa, dấu tiếng Việt, “:”...).

**English**
- Phone app, “Install TV Setup on the TV”: the automatic run now **finds its own way to Downloader’s address field** (its home page has a pointer and nothing in focus), types the link, presses Enter and waits until the TV asks to install. Run from start to finish against a Google TV emulator with the real Downloader.
- Typing in keys-only mode: key presses come first (the way Downloader takes text); the TV’s system keyboard is only used for text with characters no key can type (capitals, accents, “:”...).

## 2.6.7 - 2026-10-13

Web 2.6.7 · app điện thoại 2.5.7 · app TV 2.6.5

**Tiếng Việt**
- App điện thoại, chế độ chỉ bấm phím: mục **“Cài TV Setup lên TV”** chạy tự động trong một lần bấm: mở Downloader trên TV (chưa có thì cài từ CH Play), tự nhập đường dẫn tải và bấm Go. Người dùng chỉ còn xác nhận cài đặt trên TV. Có sẵn cách làm từng bước khi lượt tự động lạc đường.
- **Gõ chữ vào app có bàn phím riêng** (như Downloader): khi app trên TV không dùng bàn phím hệ thống, chữ được gửi thành từng phím, cả ở chế độ gõ trực tiếp ⚡. Giới hạn: chữ hoa thành chữ thường, các ký tự cần Shift (như “:”) bị bỏ.
- App TV: mở lên mà ADB chưa bật thì **tự mở Tùy chọn nhà phát triển** một lần.

**English**
- Phone app, keys-only mode: **“Install TV Setup on the TV”** runs by itself on one tap: opens Downloader on the TV (installing it from the Play Store when missing), types the download link and presses Go. The person only confirms the install on the TV. A step-by-step version remains for when the automatic run loses its way.
- **Typing into apps with a keyboard of their own** (such as Downloader): when the app on the TV does not use the system keyboard, text is sent as key presses, in live ⚡ mode too. Limits: capitals arrive in lower case, and characters that need Shift (such as “:”) are dropped.
- TV app: when it is opened while ADB is off, it **opens Developer options by itself**, once.

## 2.6.6 - 2026-10-13

Web 2.6.6 · app điện thoại 2.5.6 · app TV 2.6.4

**Tiếng Việt**
- App điện thoại: khi TV không cho ADB nhưng vẫn điều khiển được bằng phím, app vào thẳng chế độ chỉ bấm phím, không còn chớp thông báo lỗi “TV từ chối kết nối” trước đó. Lỗi chỉ hiện khi không còn cách nào.

**English**
- Phone app: when the TV refuses ADB but can still be driven with keys, the app goes straight to keys-only mode without flashing the “TV refused the connection” error first. The error only shows when nothing else works.

## 2.6.5 - 2026-10-13

Web 2.6.5 · app điện thoại 2.5.5 · app TV 2.6.4 (app TV không đổi chức năng / no functional change)

**Tiếng Việt**
- App điện thoại, chế độ chỉ bấm phím: phím mũi tên, OK và touchpad giờ **giữ phím một nhịp ngắn** như ngón tay trên remote thật, thay vì nhấn-nhả tức thì. Các app di chuyển con trỏ bằng phím mũi tên (trình duyệt của Downloader...) chỉ di chuyển khi phím đang được giữ, nên trước đây con trỏ đứng yên. Giữ lâu một phím mũi tên thì con trỏ chạy liên tục. Touchpad nhạy hơn.

**English**
- Phone app, keys-only mode: the arrows, OK and the touchpad now **hold the key for a moment**, as a finger on a real remote does, instead of an instant press. Apps that move a pointer with the arrows (Downloader’s browser...) only move it while the key is down, so the pointer used to stay put. Keeping a finger on an arrow moves the pointer continuously. The touchpad is more sensitive.

## 2.6.4 - 2026-10-13

Web 2.6.4 · app điện thoại 2.5.4 · app TV 2.6.3

**Tiếng Việt**
- **Hiện số bản và ngày phát hành** ở cả ba nơi: app điện thoại (ngay dưới tên app ở màn hình đầu và trong Giới thiệu), app TV, và cuối trang của hai bản web.
- App điện thoại: **touchpad** thay cho cụm mũi tên (nút “Dùng touchpad” ngay dưới): vuốt là các phím mũi tên theo hướng đó, chạm là OK, chạm hai ngón là Quay lại. Không phải chuột thật: nó di chuyển thứ mà phím mũi tên di chuyển, trên trình duyệt của TV (vd. Downloader) chính là con trỏ.
- App điện thoại, chế độ chỉ bấm phím: **gõ chữ được** vào ô đang được chọn trên TV (ô nhập ở cuối remote), và menu “Bật ADB cho TV này” vẽ lại thành các nút rõ ràng, mỗi nút có dòng giải thích.
- Sửa: mạng chập chờn làm app đòi ghép mã lại dù TV đã ghép.

**English**
- **Version and release date shown** in all three clients: the phone app (under its name on the first screen and in About), the TV app, and the foot of both web pages.
- Phone app: a **touchpad** in place of the arrows (the “Use the touchpad” button under them): a swipe is arrow keys in that direction, a tap is OK, a two-finger tap is Back. Not a real mouse: it moves what the arrows move, which in a TV browser (Downloader, for one) is the pointer.
- Phone app, keys-only mode: **typing works** into the field that has the focus on the TV (the box at the foot of the remote), and the “Turn on ADB for this TV” menu is redrawn as proper buttons, each with a line of explanation.
- Fixed: a weak link made the app ask for a new pairing code although the TV was paired.

## 2.6.3 - 2026-10-13

Web 2.6.3 · app điện thoại 2.5.3 · app TV 2.6.2

**Tiếng Việt**
- App điện thoại: “Quét Wi-Fi” hiện **tên TV** phía trên địa chỉ IP (tên đã lưu, hoặc tên TV tự đặt, hỏi qua dịch vụ Chromecast của nó). TV nào không trả lời thì vẫn chỉ hiện IP.

**English**
- Phone app: “Scan the Wi-Fi” shows the **TV’s name** above its IP address (the saved name, or the name the TV gives itself, asked from its Chromecast service). A TV that does not answer still shows its IP only.

## 2.6.2 - 2026-10-13

Web 2.6.2 · app điện thoại 2.5.2 · app TV 2.6.2

**Tiếng Việt**
- **App điện thoại, chế độ chỉ bấm phím: trợ lý bật ADB từng bước.** (1) Tự thử mọi cách mở Cài đặt TV và tự đọc xem TV đang mở app nào để biết có thành công không; (2) nếu không, chỉ chỗ tìm Cài đặt trên màn hình chính của TV; (3) nếu vẫn không có, mở/cài một màn hình chính khác từ CH Play (Projectivy, ATV Launcher, FLauncher: đã cài thì mở, chưa cài thì mở trang CH Play, không tương thích thì chọn cái khác); (4) các bước bật ADB, kể cả trường hợp nhà mạng khóa bằng mật khẩu. Thêm hai lệnh nhanh: mở CH Play, mở app theo tên gói hoặc đường dẫn.
- App tự nối lại khi TV ngắt kết nối sau một đường dẫn nó không mở được, và đề nghị ghép mã cả khi cổng ADB không trả lời (trước đây chỉ khi bị từ chối).
- Sửa: mở app theo tên gói ở chế độ chỉ bấm phím không có tác dụng.
- **App TV: nút “Tùy chọn nhà phát triển”**, mở thẳng màn hình đó (không cần ADB), cho các box giấu Cài đặt hoặc đòi mật khẩu khi bấm 7 lần vào Bản dựng.

**English**
- **Phone app, keys-only mode: a step-by-step assistant for turning ADB on.** (1) Tries every way to open the TV’s Settings and reads which app the TV has in front to know whether it worked; (2) if not, says where to look on the TV’s own home screen; (3) failing that, opens or installs another home screen from the Play Store (opens it when installed, its store page when not, pick another when incompatible); (4) the ADB steps, including operator boxes that lock it with a password. Two quick commands: open the Play Store, open an app by package name or link.
- The app reconnects when the TV drops the connection after a link it cannot open, and offers pairing when the ADB port does not answer at all (before: only when refused).
- Fixed: opening an app by package name did nothing in keys-only mode.
- **TV app: a “Developer options” button** that opens that screen directly (no ADB needed), for boxes that hide Settings or ask for a password on the seven presses on Build.

## 2.6.1 - 2026-10-13

Web 2.6.1 · app điện thoại 2.5.1 · app TV 2.6.1 (app TV không đổi chức năng / no functional change)

**Tiếng Việt**
- **App điện thoại: điều khiển TV chưa bật ADB.** Khi TV từ chối ADB nhưng là Android TV, app đề nghị ghép bằng mã 6 ký tự hiện trên TV rồi cho bấm phím (D-pad, Home, Back, âm lượng, nguồn...). Có nút “Mở Cài đặt TV” cho các box mà màn hình chính giấu mục Cài đặt, kèm hướng dẫn bật ADB và nút “Kết nối đầy đủ”; nếu box bỏ qua phím Cài đặt (vd. FPT Play), app mở trang CH Play của một màn hình chính khác có lối vào Cài đặt. Chế độ này chỉ bấm phím: xem hình, ứng dụng, tệp, gõ chữ vẫn cần ADB. (Bản web trên máy tính đã có cách ghép mã này từ trước.)

**English**
- **Phone app: control a TV whose ADB is still off.** When the TV refuses ADB but is an Android TV, the app offers to pair with the 6-character code the TV shows and then sends keys (D-pad, Home, Back, volume, power...). An “Open TV Settings” button helps on boxes whose home screen hides Settings, with the steps to turn ADB on and a “Full connection” button; if the box ignores the Settings key, the app opens the Play Store page of another home screen that has a way into Settings. This mode is keys only: the picture, apps, files and typing still need ADB. (The computer web already paired this way.)

## 2.6.0 - 2026-10-13

Web 2.6.0 · app điện thoại 2.5.0 · app TV 2.6.0

**Tiếng Việt**
- **Lịch sử dùng TV** (Sức khỏe → Lịch sử, ở cả ba nơi): thời gian TV bật theo từng ngày trong 7 hoặc 30 ngày, app nào dùng nhiều và bao lâu, biểu đồ RAM · CPU · bộ nhớ theo giờ; chạm vào biểu đồ để xem số liệu và app đang mở tại thời điểm đó.
- Số liệu do **app TV Setup trên TV tự ghi** 5 phút một lần và lưu ngay trên TV, nên không cần máy nào khác bật. Cần TV Setup 2.6.0; lịch sử bắt đầu từ lúc cài bản này.

**English**
- **TV history** (Health → History, in all three clients): TV time per day over 7 or 30 days, which apps were used and for how long, and RAM · CPU · storage charts by the hour; touch a chart to read the figures and the app on screen at that moment.
- The figures are **recorded by the TV Setup app on the TV** every 5 minutes and kept on the TV, so nothing else has to stay on. Needs TV Setup 2.6.0; history starts when this version is installed.

## 2.5.7 - 2026-10-13

Web 2.5.7 · app điện thoại 2.4.8 · app TV 2.5.7

**Tiếng Việt**
- Trang quản lý (app điện thoại và trang trên TV): khi cuộn, nội dung không còn lộ ra ở khe phía trên thanh tab.
- Sức khỏe: tên các dịch vụ hệ thống được rút gọn, ví dụ `android.hardware.graphics.composer@2.1-service` thành `graphics.composer`, nên không còn xuống ba dòng.

**English**
- Management page (phone app and the TV's page): scrolled content no longer shows in the gap above the tab bar.
- Health: system service names are shortened, for example `android.hardware.graphics.composer@2.1-service` becomes `graphics.composer`, so they no longer wrap over three lines.

## 2.5.6 - 2026-10-13

Web 2.5.6 · app điện thoại 2.4.7 · app TV 2.5.6

**Tiếng Việt**
- **Phím điều khiển bớt nhạy, ở cả ba nơi:** chạm xuống chưa gửi gì; nhả tay ngay trên nút mới tính là bấm, và lúc đó nút mới nháy màu cam. Lỡ chạm rồi trượt tay ra khỏi nút (hoặc bắt đầu cuộn trang) thì không gửi phím nào.
- Giữ nút 0,4 giây vẫn là nhấn giữ trên TV như trước (nút sáng cam trong lúc giữ); trượt ra khỏi nút thì thả phím.

**English**
- **Remote keys are less trigger-happy, in all three clients:** touching a key sends nothing; lifting the finger on the key is what counts as a press, and only then does the key flash amber. Touch it by accident and slide off (or start scrolling) and no key is sent.
- Holding a key for 0.4 s is still a long press on the TV (the key stays amber meanwhile); sliding off it releases the key.

## 2.5.5 - 2026-10-13

Web 2.5.5 · app điện thoại 2.4.6 · app TV 2.5.5

**Tiếng Việt**
- App điện thoại: **nút chất lượng hình ngay trên màn xem TV** (240p đến 960p), đổi là áp dụng luôn. Mục chất lượng trong Cài đặt được bỏ, chữ trong Cài đặt gọn lại.
- App điện thoại: bấm **Kiểm tra ngay** mà có bản mới thì hộp cập nhật hiện ngay tại chỗ, không phải quay ra màn chọn TV.
- Trang trên TV: hình dừng (ví dụ máy khác vào xem) thì khung hình và các nút của nó tự ẩn; tên TV dài và số âm lượng không còn bị xuống dòng.
- **Bố cục điều khiển mới ở cả ba nơi:** hàng trên cùng là Nguồn · Micro · ⋯; dưới vòng điều hướng là Quay lại · Home · Menu; dưới nữa là cột Âm lượng bên trái, cột Kênh bên phải, giữa là Tắt tiếng, Phát/Dừng, Bài trước/sau, Tua lại/tới. Các nút có tên ngay dưới icon.
- **Nút ⋯ mở thêm phím:** Ngõ vào, Cài đặt TV, Thông tin, Lịch phát, TV, Phụ đề, Dừng và **Chụp màn hình** (lưu ảnh màn hình TV về máy đang dùng).
- **Tên và logo app lấy cùng một cách ở cả ba nơi** (hỏi hệ thống của TV). Web máy tính không còn tự giải mã APK hay lấy logo từ Google Play, và bớt được gói `pyaxmlparser`.
- **Tối ưu TV có trong app điện thoại và trang trên TV:** tab Cài có nút ⚡ Tối ưu TV và ↩ Hoàn tác, chạy cùng các bước như web máy tính.
- **Terminal:** danh sách "Lệnh đã chạy" để chọn lại lệnh cũ (điện thoại không có phím ↑ ↓), nhớ 30 lệnh gần nhất trên máy đang dùng.
- **Nhẹ RAM hơn cho TV:** app TV Setup chỉ giữ kết nối điều khiển khi có người đang mở remote, rảnh 3 phút thì đóng (bớt khoảng 70 MB trên TV 2 GB); lần bấm kế tiếp tự mở lại.
- **Nút 🧹 Giải phóng RAM** ở tab Sức khỏe của cả ba nơi: đóng các app đang nằm chờ ở nền, app đang hiện trên màn hình không bị ảnh hưởng.
- **Tối ưu TV** giờ tắt hẳn hiệu ứng chuyển cảnh (trước là 0,5x).
- **Nút không có tác dụng thì báo:** bấm Menu, Ngõ vào, Lịch phát, TV hoặc Cài đặt TV mà màn hình TV không đổi thì cả ba nơi hiện "TV đã nhận nút này, nhưng màn hình hiện tại không phản hồi", để không tưởng là app hỏng.
- App điện thoại: danh sách ứng dụng có **tên thật và logo** của app, hỏi thẳng từ TV.
- App điện thoại: thanh nút phía trên hình TV có thêm khoảng cách.
- Web máy tính: nút Ngõ vào giờ mở được danh sách ngõ vào trên TV này (đi qua ADB).
- README viết lại gọn hơn, có ảnh chụp, phần kỹ thuật thu gọn được.

**English**
- Phone app: a **picture quality button on the watch screen** (240p to 960p), applied at once. The quality section is gone from Settings, whose labels are shorter.
- Phone app: **Check now** opens the update dialog right there when a new version exists, instead of sending you back to the TV list.
- **A new control layout in all three clients:** the top row is Power · Mic · ⋯; under the D-pad come Back · Home · Menu; below that a volume bar on the left, a channel bar on the right, and Mute, Play/Pause, Previous/Next, Rewind/Forward between them. Buttons carry their name under the icon.
- **The ⋯ button shows more keys:** Input, TV settings, Info, Guide, Live TV, Subtitles, Stop and **Screenshot** (saves the TV's screen to the device in your hand).
- **App names and icons come the same way in all three** (asked from the TV's own system). The web on the computer no longer decodes APKs or fetches icons from Google Play, and drops the `pyaxmlparser` package.
- **Optimize TV in the phone app and on the TV's page:** the Install tab has ⚡ Optimize TV and ↩ Undo, running the same steps as the web on the computer.
- **Terminal:** an "Earlier commands" list to pick a previous command from (a phone has no ↑ ↓ keys); it keeps the last 30 on the device in your hand.
- **Lighter on the TV's memory:** the TV Setup app keeps its control session only while somebody has the remote open and closes it after 3 idle minutes (about 70 MB less on a 2 GB TV); the next press opens it again.
- **A 🧹 Free up RAM button** on the Health tab of all three: closes apps that are only waiting in the background; what is on screen is untouched.
- **Optimize TV** now turns transition animations off completely (it was 0.5x).
- **A key that does nothing says so:** when Menu, Input, Guide, Live TV or TV settings leaves the TV's screen unchanged, all three clients show "The TV got the key, but the current screen did not react", so it does not look like a broken app.
- Phone app: the app list shows each app's **real name and icon**, asked from the TV itself.
- Phone app: more room between the top buttons and the TV picture.
- Web on the computer: the Input button now opens the input list on this TV (it goes through ADB).
- The page on the TV: when the picture stops (another device took over, for example) the picture box and its buttons hide themselves; a long TV name and the volume figure no longer wrap.
- README rewritten: shorter, with screenshots and collapsible technical sections.

## 2.5.4 - 2026-10-13

Web 2.5.4 · app điện thoại 2.4.5 · app TV 2.5.4

**Tiếng Việt**
- Thêm hai mức hình nhẹ hơn: **360 px** và **240 px** (cạnh dài), trong Cài đặt của app điện thoại và ở nút chất lượng trên trang của TV. Ở 240 px chữ trên TV gần như không đọc được, chỉ đủ để thấy mình đang ở đâu.

**English**
- Two lighter picture levels: **360 px** and **240 px** (longest side), in the phone app's Settings and on the quality button of the TV's page. At 240 px text on the TV is barely readable; it is only enough to see where you are.

## 2.5.3 - 2026-10-13

Web 2.5.3 · app điện thoại 2.4.4 · app TV 2.5.3

**Tiếng Việt**
- **Không còn nút nào nằm đè lên hình TV.** App điện thoại: nút toàn màn hình chuyển lên thanh trên cùng, cạnh nút con mắt. Trang trên TV: nút chất lượng và nút toàn màn hình cũng lên thanh trên cùng. Chỉ khi đang toàn màn hình mới có một nút X mờ ở góc để thoát.

**English**
- **No buttons sit on the TV picture any more.** Phone app: the full screen button moves to the top bar, next to the eye. The page on the TV: the quality and full screen buttons move to the top bar too. Only in full screen is there one faint X in the corner to leave.

## 2.5.2 - 2026-10-13

Web 2.5.2 · app điện thoại 2.4.3 · app TV 2.5.2

**Tiếng Việt**
- Trang trên TV: **chọn chất lượng hình** bằng nút ở góc trên phải của hình (480p · 720p · 960p, bấm để đổi), cùng ba mức với app điện thoại. Lựa chọn được nhớ theo trình duyệt. 480p nhẹ và mượt nhất.

**English**
- The page on the TV: **pick the picture quality** with the button at the top right of the picture (480p · 720p · 960p, tap to change), the same three levels as the phone app. The choice is remembered per browser. 480p is the lightest and smoothest.

## 2.5.1 - 2026-10-13

Web 2.5.1 · app điện thoại 2.4.2 · app TV 2.5.1

**Tiếng Việt**
- Trang trên TV và web máy tính: phím điều khiển **đổi màu cam ngay khi chạm**, như app điện thoại. Trước đây phải giữ 0,4 giây (lúc TV bắt đầu nhận là giữ phím) mới đổi màu.

**English**
- The page on the TV and the web on the computer: remote keys **turn amber the moment you touch them**, like the phone app. They used to change only after 0.4 s, when the TV starts seeing the key as held.

## 2.5.0 - 2026-10-13

Web 2.5.0 · app điện thoại 2.4.1 · app TV 2.5.0

**Tiếng Việt**
- **Trang trên TV xem được hình TV:** nút con mắt ở góc trên bật hình, chạm (hoặc bấm chuột) lên hình để điều khiển, có nút toàn màn hình. Tắt hình thì TV ngừng mã hóa. Mỗi lúc một trình duyệt xem được hình.
- Ô **tìm app theo tên** phía trên lưới ứng dụng ở tab Remote, trên trang của TV và web máy tính.

**English**
- **The page on the TV shows the TV picture:** the eye button at the top turns it on, touch (or click) the picture to control the TV, with a full screen button. Turning it off stops the encoder on the TV. One browser at a time can watch.
- A **search-by-name box** above the app grid on the Remote tab, on the TV's page and the web on the computer.

## 2.4.0 - 2026-10-13

Web 2.4.0 · app điện thoại 2.4.0 · app TV 2.4.0

**Tiếng Việt**
- **Ba nơi cùng một bố cục điều khiển** (app điện thoại, trang trên TV, web máy tính), theo thứ tự: Nguồn · Nguồn vào · Cài đặt TV · Assistant, vòng điều hướng, Quay lại · Home · Menu, Tắt tiếng · Âm lượng, Bài trước · Phát/Dừng · Bài sau, ô gõ chữ, bàn phím số.
- Trang trên TV và web máy tính có thêm nút ⚡ (gõ trực tiếp / gõ xong rồi gửi) và bàn phím số 123 như app điện thoại. App điện thoại có thêm nút Nguồn vào (Input).
- Khác biệt còn lại, có chủ ý: app điện thoại có nút mở Quản lý TV (hai trang web dùng tab); web máy tính có nút Đánh thức (nhờ máy ở nhà gửi giúp).

**English**
- **One control layout in all three places** (phone app, the page on the TV, web on the computer), in this order: Power · Input · TV settings · Assistant, the D-pad, Back · Home · Menu, Mute · Volume, Previous · Play/Pause · Next, the text box, the number pad.
- The page on the TV and the web on the computer gain the ⚡ switch (live typing / type then send) and the 123 number pad the phone app has. The phone app gains the Input button.
- Remaining differences, on purpose: the phone app has a button for TV management (the web pages use tabs); the web on the computer has Wake (relayed by a machine at home).

## 2.3.6 - 2026-10-13

Web 2.3.6 · app điện thoại 2.3.5 · app TV 2.3.3

**Tiếng Việt**
- Nút nguồn xem TV đang làm gì trước: TV đang chờ thì bật lên ngay, không hỏi; TV đang bật thì hỏi "Đưa TV về chế độ chờ?" (trước đây luôn hỏi "Tắt TV?", kể cả khi bấm để bật). App điện thoại và trang trên TV giờ dùng phím ngủ / thức riêng, nên một lần bấm không bao giờ làm ngược ý.

**English**
- The power button looks at the TV first: in standby it wakes it at once, no question; when on it asks "Put the TV in standby?" (it used to ask "Turn the TV off?" even when tapped to turn it on). The phone app and the page on the TV now use the separate sleep / wake keys, so a tap never does the opposite.

## 2.3.5 - 2026-10-13

Web 2.3.5 · app điện thoại 2.3.4 · app TV 2.3.2

**Tiếng Việt**
- App điện thoại: thêm chế độ **gõ trực tiếp** (nút ⚡ cạnh ô gõ chữ). Bật lên thì gõ tới đâu TV nhận tới đó, xóa chữ nào TV xóa chữ đó; sửa một ký tự không cần gửi lại cả câu. Tắt đi thì như cũ: gõ xong rồi bấm gửi.
- Thêm **bàn phím số** (nút 123): các phím 0-9 để nhập kênh, mã PIN.

**English**
- Phone app: a **live typing** mode (the ⚡ button next to the text box). When on, the TV follows every letter and every deletion; fixing one character no longer means sending the whole text again. When off it works as before: type, then send.
- A **number pad** (the 123 button): keys 0-9 for channels and PIN codes.

## 2.3.4 - 2026-10-13

Web 2.3.4 · app điện thoại 2.3.3 · app TV 2.3.2

**Tiếng Việt**
- App điện thoại: nút **toàn màn hình** ở góc hình TV. Bấm vào thì màn hình xoay ngang, chỉ còn hình TV (vẫn chạm để bấm được) và một nút X mờ để thoát; phím Back cũng thoát.

**English**
- Phone app: a **full screen** button in the corner of the TV picture. It turns the screen sideways and leaves only the picture (touch still works) with a faint X to leave; Back leaves too.

## 2.3.3 - 2026-10-13

Web 2.3.3 · app điện thoại 2.3.2 · app TV 2.3.2

**Tiếng Việt**
- App điện thoại: khi TV không trả lời, dòng trạng thái nói rõ lý do: đang ở ngoài Wi-Fi của TV (không đánh thức từ đây được) hay TV đang tắt (bấm nút nguồn để đánh thức). Bấm đánh thức ở ngoài mạng thì app báo ngay cách xử lý, không còn bắt chờ 20 giây.

**English**
- Phone app: when a TV does not answer, its status line says why: you are not on the TV's Wi-Fi (it cannot be woken from here) or the TV is off (tap power to wake it). Tapping wake from another network explains what to do at once instead of waiting 20 seconds.

## 2.3.2 - 2026-10-13

Web 2.3.2 · app điện thoại 2.3.1 · app TV 2.3.2

**Tiếng Việt**
- App điện thoại: mỗi TV đã lưu hiện trạng thái ngay trong danh sách (kết nối được / không thấy), tự kiểm tra lại mỗi 8 giây. Nút đánh thức chỉ hiện khi TV không trả lời.
- Nút nguồn ở màn remote hỏi lại trước khi tắt và không còn giữ được: giữ nút nguồn trước đây mở menu nguồn của TV rồi chọn luôn "Tắt nguồn". Trang trên TV cũng hỏi lại trước khi tắt.
- Tab Sức khỏe hiện "Đang tải..." trong lúc chờ số liệu đầu tiên thay vì để trống.

**English**
- Phone app: each saved TV shows its status in the list (reachable / no answer), rechecked every 8 seconds. The wake button only appears when the TV does not answer.
- The power button on the remote screen asks before turning off and can no longer be held: holding it used to open the TV's power menu and pick "Power off". The page on the TV asks too.
- The Health tab shows "Loading..." while waiting for the first numbers instead of staying empty.

## 2.3.1 - 2026-10-13

Web 2.3.1 · app điện thoại 2.3.0 · app TV 2.3.1

**Tiếng Việt**
- App TV Setup: các nút tự xuống dòng khi màn hình hẹp (màn dọc, điện thoại), không còn bị che mất.

**English**
- TV Setup app: the buttons wrap onto the next line on a narrow screen (portrait, a phone) instead of being cut off.

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
- Folders reorganized: `app/web` (web on the computer), `app/android` (phone app), `app/tv` (TV app), `app/shared`, `dev` (release tools). The top folder has only three files to click: `start-mac.command`, `start-windows.cmd`, `start-linux.sh`. If you used the old `start.command` / `start.cmd`, switch to the new names; `install-windows.cmd` is now part of `start-windows.cmd`.
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
