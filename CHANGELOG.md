# Changelog / Nhật ký thay đổi

Có ba số phiên bản, mỗi số chỉ tăng khi phần của nó đổi: `VERSION` (bản phát hành và web trên máy tính), `app/android/VERSION` (app điện thoại), `app/tv/VERSION` (app TV). Tiêu đề mỗi mục dưới đây là số của bản phát hành. Bản phát hành kèm file APK nằm ở [Releases](https://github.com/HoangTran0410/tv-remote/releases); hai app tự kiểm tra bản mới ở đó.

Three version numbers, each bumped only when its own part changes: `VERSION` (the release and the web UI on the computer), `app/android/VERSION` (phone app), `app/tv/VERSION` (TV app). The headings below are release numbers. Releases with the APK files are on the [Releases](https://github.com/HoangTran0410/tv-remote/releases) page; both apps check there for updates.

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
