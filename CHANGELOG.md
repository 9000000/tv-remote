# Changelog / Nhật ký thay đổi

Ba số phiên bản, mỗi số chỉ tăng khi phần của nó đổi: `VERSION` (bản phát hành và app máy tính), `app/android/VERSION` (app điện thoại), `app/tv/VERSION` (app TV). Bản phát hành kèm file cài nằm ở [Releases](https://github.com/HoangTran0410/tv-remote/releases); các app tự kiểm tra bản mới ở đó.

Three version numbers, each bumped only when its own part changes: `VERSION` (the release and the computer app), `app/android/VERSION` (phone app), `app/tv/VERSION` (TV app). Releases with the install files are on the [Releases](https://github.com/HoangTran0410/tv-remote/releases) page; the apps check there for updates.

## Unreleased

**Tiếng Việt**

- Cài TV Setup lên TV chưa bật ADB: luôn đi qua Downloader. Trước đây trình duyệt có sẵn của TV (như TCL) nhận đường dẫn APK, tải về rồi đứng ở trang trắng, không mở được trình cài. Cách gửi link thẳng vẫn còn, ở cuối danh sách “Bật ADB”, ghi rõ là dự phòng; sau một phút chưa thấy báo cài xong thì app gợi ý cài qua Downloader.
- Bật ADB lần đầu: TV chỉ hỏi “Cho phép gỡ lỗi USB?” một lần. Trước đây mỗi phần của app (hình, quản lý, nhạc...) hỏi riêng, và TV Setup hỏi thêm cho chính nó; giờ các phần chờ chung một câu trả lời, và điện thoại / máy tính vừa được cho phép tự trả lời giúp câu hỏi của TV Setup.
- Rung nhẹ khi bấm nút remote trên điện thoại (app điện thoại, và trang web mở bằng Chrome trên Android). Tắt được trong Cài đặt → Khi bấm nút.
- Nghe tiếng TV trên điện thoại (🎧) không còn nhanh và giật: một số điện thoại (như Xiaomi) báo vị trí phát theo từng nấc lớn, app tưởng bị trễ nên bỏ gần 1/3 số gói âm thanh. Giờ đo vị trí phát chính xác và chỉ bỏ gói khi thật sự trễ.
- TV Setup tự lo câu hỏi “Cho phép gỡ lỗi USB?” mà không cần điện thoại: câu hỏi bị màn TV Setup che thì TV Setup tự lùi xuống cho thấy, xong tự quay lại; câu hỏi cũ kẹt lại thì hỏi lại; nếu TV vẫn không hiện câu hỏi thì chỉ cách tắt/bật Gỡ lỗi USB. Sửa lỗi TV Setup mới cài tạo nhiều khóa cùng lúc, làm TV hỏi liên tục cho những khóa lạ.
- App điện thoại: hình TV chạy một kết nối riêng, như app máy tính. Bật/tắt hình, đổi chất lượng hay ra ngoài app chỉ mở/đóng phần hình; phím, thanh đang phát và tiếng vẫn chạy liền, không phải kết nối lại.

**English**

- Installing TV Setup on a TV without ADB always goes through Downloader. The TV's own browser (TCL's, for one) used to take the APK link, download it and then sit on a white page, unable to open the installer. Sending the link straight to the TV is still there, last in “Turn on ADB”, marked as a fallback; after a minute with no word that it installed, the app offers Downloader.
- Turning ADB on the first time: the TV asks “Allow USB debugging?” once. Each part of the app (picture, Manage, now playing...) used to ask on its own, and TV Setup asked for itself too; now they wait for one answer, and the phone or computer just allowed answers TV Setup's question for it.
- A light tap under the finger when pressing a remote key on a phone (the phone app, and the page in Chrome on Android). Off in Settings → When pressing a key.
- Listening to the TV on the phone (🎧) no longer plays fast and choppy: some phones (Xiaomi, for one) report the playback position in big steps, so the app thought it was behind and dropped about a third of the sound. It now reads the position exactly and drops only when really behind.
- TV Setup handles “Allow USB debugging?” on its own, no phone needed: a question hidden behind its screen makes it step aside and come back after; a stuck question is asked again; and if the TV still shows none it says how to switch USB debugging off and on. A newly installed TV Setup no longer makes several keys at once, which had the TV asking again and again for strange keys.
- Phone app: the TV picture has a connection of its own, as in the computer app. Turning it on or off, changing its quality or leaving the app opens or closes only the picture; keys, the now playing bar and the sound go on without reconnecting.

## 0.5.0 - 2026-10-17

Web 0.5.0 · app điện thoại 0.5.0 · app TV 0.5.0

**Tiếng Việt**
- 🔔 Gửi thông báo lên TV (Thêm phím → Gửi thông báo lên TV): gõ tiêu đề (không bắt buộc) và vài chữ, TV hiện thành thẻ nhỏ đè lên mọi app, không giành phím remote và tự biến mất. Chọn góc (trên trái / trên phải / dưới trái / dưới phải, mặc định trên phải) và thời gian hiện (3 / 5 / 10 / 30 giây, mặc định 5); lần sau nhớ lựa chọn. Có trong app điện thoại, app máy tính và trang web của TV (khách “Chỉ remote” cũng gửi được). Cảnh có thêm bước “🔔 Gửi thông báo”, nên hẹn giờ và cảnh gửi được. Cần TV Setup 0.4.2 trở lên.
- Các tab xếp thẻ kiểu gallery: thẻ nào nằm ngay dưới thẻ trên cột của nó, không còn khoảng trống; màn rộng có tới 3 cột (Sức khỏe, Tự động) và trang rộng hơn. Đổi ngôn ngữ không còn nháy màn Remote trước khi về lại tab đang xem.
- 📱 Sức khỏe → App đang chạy: xem app nào đang chạy, chạy ngầm, tốn bao nhiêu RAM, và bấm Dừng từng app. Chỉ dừng được app người dùng cài hoặc app có trên màn hình chính; màn hình chính, bàn phím, Android, TV Remote, TV Setup và Tailscale bị khóa (🔒 Cần giữ) để không dừng nhầm.
- 🧹 Báo khi RAM đầy (tab Tự động, mặc định tắt): RAM của TV dùng quá mức đã chọn (80–95%) thì TV Setup hiện một bảng đè lên mọi app, bấm Dọn RAM, Xem app (mở Quản lý trên TV ở tab Sức khỏe) hoặc Bỏ qua; báo lại sau ít nhất 30 phút. Cần TV Setup mới.
- 📺 TV Setup → Quản lý trên TV: mở luôn giao diện quản lý (Ứng dụng, Tệp, Sức khỏe, Tự động, Thiết lập) trên màn hình TV, điều khiển bằng remote của TV, không cần điện thoại hay máy tính. Chạy được cả khi đã tắt remote qua web.
- 📲 Chia sẻ (Thêm phím → Chia sẻ): mã QR cho điện thoại khác quét là vào thẳng remote của TV, không cần gõ mã (dùng 1 lần, trong 10 phút), kèm mã QR tải app điện thoại. Đang nối TV qua Tailscale thì có cảnh báo; TV chưa có TV Setup thì chỉ hiện mã tải app. Cần TV Setup mới.
- ▶️ Đang phát: bấm tạm dừng / chạy tiếp / qua bài thì nút quay chờ tới khi TV làm xong; bấm liên tục không còn làm nút và TV lệch nhau.
- Tự động: lúc TV Setup đang cập nhật, trang không còn báo nhầm "TV Setup là bản cũ" kèm nút Cập nhật; nó chờ TV Setup chạy lại rồi tự hiện.
- Cập nhật: số phiên bản nằm trong danh sách có chữ ký; app từ chối bản có số phiên bản không khớp với danh sách (chống cài lại bản cũ).
- 📱 App điện thoại: chia sẻ từ app khác chỉ nhận tệp dạng content:// của app đó và liên kết http/https.
- App máy tính: tệp cập nhật tạm nằm trong thư mục riêng của người dùng.
- Giới hạn giờ dùng app: app đang mở từ trước nửa đêm hoặc từ lúc TV bật lại (không có sự kiện mới) giờ cũng được tính giờ, hết giờ vẫn bị đóng.
- Hẹn giờ, giờ đi ngủ, giới hạn app: hai lần kiểm tra không còn chạy chồng nhau (tránh hiện cảnh báo hai lần).
- Bấm “Thử lại” khi TV đang hỏi “Cho phép gỡ lỗi USB?” kết nối lại ngay, không còn đứng yên khoảng 10 giây.
- Sức khỏe nhẹ cho TV hơn: thông tin ít đổi (model, màn hình, Wi-Fi, IP) chỉ hỏi TV mỗi phút một lần; thẻ Mạng và Phần cứng không vẽ lại khi không đổi nên chọn và chép IP, SSID được. Tab Thiết lập trên máy tính hỏi TV ít hơn và không vẽ lại các bước khi không đổi. Tab Ứng dụng bớt một lần hỏi TV. Xem hình TV mượt hơn, tốn ít bộ nhớ hơn.
- Hộp thoại đang mở thì phím mũi tên, Esc, Backspace trên máy tính không còn gửi sang TV.
- Gửi tệp / cài APK có nút ■ Dừng; lỗi khi gửi hiện bằng lời dễ hiểu (và hỏi cài / cập nhật TV Setup nếu thiếu).
- Thông báo nhỏ ở góc hiện lâu hơn khi dài. Nút hủy hẹn giờ và hỏi xóa cảnh (tên có · hoặc →) có tiếng Anh.
- Thanh tab dùng được bằng phím ← → cho người dùng bàn phím và trình đọc màn hình. Cài TV Setup báo lỗi khi mất kết nối thay vì chờ mãi.

- 🔐 Chia sẻ có quyền: chọn **Toàn quyền**, **Chỉ remote** (phím, gõ chữ, hình, mở app, hẹn giờ tắt, chạy cảnh có sẵn) hoặc **Chỉ xem** (hình TV và đang phát). Chủ TV (app điện thoại, app máy tính, ai nhập mã 6 số trên TV) đổi quyền từng thiết bị trong Thiết lập → Thiết bị đã ghép; khách chỉ thấy và gỡ được chính mình. Cần TV Setup mới.
- Ghép bằng mã không còn bị một trình duyệt chậm chặn lại.
- 💻 App máy tính: mỗi lần mở có một khóa mới, cửa sổ app và trình duyệt tự mang theo (không phải gõ gì); chương trình khác trên máy không điều khiển TV được nữa.
- 📱 App điện thoại: hỏi “Gửi lên TV?” trước khi gửi tệp hay đường link mà app khác chia sẻ sang.
- Giờ đi ngủ, giới hạn app, giới hạn âm lượng: nút ＋ Thêm mở một hộp nhỏ để chọn giờ và mức, không còn bày sẵn cả form trên thẻ.
- 📲 Chia sẻ có hai tab, Chia sẻ TV (chọn quyền, nút quyền có icon) và Chia sẻ app, trên cả app điện thoại và trang web.
- App đang chạy: chip 🔒 Cần giữ không còn rớt xuống dòng; các nút chú thích của biểu đồ CPU / RAM / bộ nhớ nhỏ lại như cũ.
- 📱 App điện thoại: phím media và hàng Quay lại / Home / Menu trải hết chiều ngang; hình TV cao tối đa nửa màn hình.
- 📺 Màn TV Setup gọn hơn: ô thao tác, thẻ ADB và cột Điều khiển từ điện thoại nhỏ lại, phần Hoạt động rộng hơn.
- 📺 Quản lý trên TV: không còn thoáng hiện tab Remote khi mở; ← → trên thanh tab chỉ dời ô sáng từng tab một, bấm OK mới mở tab (không phải tải các tab đi ngang qua); chọn quyền trong Thiết bị đã ghép (hay danh sách nào vẽ lại) thì ô sáng vẫn ở đó, mũi tên xuống đi tiếp từng thiết bị thay vì nhảy lên đầu trang. Cuộn lên trong một danh sách không còn nhảy lên thanh tab giữa chừng (vào thanh tab là về tab đang mở), và chọn được nút Khôi phục (các nút chọn tệp).
- 💻 App máy tính: mở app thêm lần nữa lúc nó đang chạy không còn làm hỏng khóa của cửa sổ đang mở.
- Chạy cảnh có bước hẹn giờ khi TV chưa có TV Setup (hoặc là bản cũ): app điện thoại và máy tính giờ hỏi "Cài / Cập nhật TV Setup?" như các tính năng khác, thay vì chỉ ghi "bước 1 lỗi". Ba app dùng chung một đoạn chạy cảnh.
- 📺 App TV: mở sẵn tab Web của TV (hoặc để trang poll trạng thái) không còn giữ tiến trình scrcpy-server (~70 MB) chạy mãi: chỉ phím, gõ, chạm, hình/ảnh chụp mới tính là đang dùng, 3 phút không dùng thì nghỉ. Chạm vào remote trên trang là đánh thức lại trước phím đầu tiên; mỗi lần đánh thức bớt một lệnh ADB và dò scrcpy-server nhanh hơn.
- 🔒 App TV: màn hình mã ghép không bật lại chồng lên màn hình đang hiện; hỏi mã liên tục mà không ai nhập thì phải đợi 30 giây, rồi lâu dần (tối đa 5 phút). Màn hình mã và màn cảnh báo hẹn giờ chỉ app TV và ADB shell mới mở được, app khác trên TV thì không.
- Gửi tệp / cài APK có nút **■ Dừng** thật: dừng cả phía máy chủ (ngắt việc chép, ngắt `pm install`, ngắt tải về), xóa phần tệp gửi dở trên TV và tệp tạm; trang ghi "Đã dừng" chứ không báo lỗi. Cũng có ở "Nhiều TV cùng lúc" (máy tính và điện thoại) và khi 📱 phát tệp từ điện thoại lên TV.
- Trước khi nhận tệp, TV được hỏi còn trống bao nhiêu; không đủ thì từ chối ngay, chưa gửi byte nào: "TV chỉ còn trống 1.2 GB, không đủ chép tệp 3.4 GB... Xóa bớt app hoặc tệp rồi thử lại." APK cần chỗ cho cả bản chép lẫn app sau khi cài (gấp đôi) và một phần dự phòng.
- Ngõ vào thật: nút “Ngõ vào” giờ liệt kê các ngõ của TV theo tên TV đặt (vd “HDMI 1 · PlayStation 5”, AV) và chạm là chuyển. Android TV / Google TV đọc qua ADB (HDMI-CEC cho tên thiết bị), LG qua kết nối remote; chuyển bằng đường dẫn của Live TV, không được thì dùng phím HDMI. TV không có ngõ nào để chọn (box, máy ảo) hoặc Samsung: hiện một dòng và mở bảng chọn của TV như trước. Có ở app điện thoại, app máy tính và Web của TV; thêm bước “Chuyển ngõ vào” cho Cảnh.
- 📡 Ghép bằng Gỡ lỗi không dây (Android 11 trở lên): với TV không có “Gỡ lỗi USB” qua cổng 5555, vào Bật ADB giúp tôi → Ghép bằng Gỡ lỗi không dây, nhập IP:cổng và mã 6 số TV hiện ra (IP:cổng điền sẵn nếu tìm thấy trên mạng). App tự tìm cổng kết nối, nhớ cổng cho từng TV, và không còn hỏi “Cho phép gỡ lỗi USB?”. Có trên app điện thoại và app máy tính (app TV và trang web của TV không cần).
- Gửi tệp lớn (khoảng 70 MB trở lên) lên TV Android mới không còn đứng giữa chừng rồi báo “TV ngừng nhận dữ liệu”: app chờ TV ghi kịp rồi mới gửi tiếp. Bấm Dừng lúc đang cài APK thì trang ghi “Đã dừng” thay vì báo lỗi. Ghép bằng Gỡ lỗi không dây: bước nhập cổng kết nối chỉ cần cổng, không hỏi lại mã đã dùng. 📱 App điện thoại: nút có icon giữ icon sát bên chữ ở giữa nút.
- Tab Sức khỏe: nhiệt độ của TV (mức nhiệt và từng cảm biến), tải GPU (khi TV cho đọc), lưu lượng mạng đang tải xuống / lên, và Bluetooth: bật hay tắt, thiết bị đã ghép, cái nào đang kết nối (remote, tay cầm).

**English**
- 🔔 Notify the TV (More keys → Notify the TV): type a title (optional) and a few words and the TV shows them as a small card over any app, which takes no remote keys and goes away by itself. Pick the corner (top left / top right / bottom left / bottom right, default top right) and how long it stays (3 / 5 / 10 / 30 s, default 5); the page remembers your choice. In the Phone app, the Computer app and the TV web page (a “Remote only” guest can send one too). Scenes have a new step, “🔔 Send a notification”, so timers and scenes can send one. Needs TV Setup 0.4.2 or newer.
- The tabs lay their cards out like a gallery: each card sits right under the one above it in its column, no holes; up to 3 columns on wide screens (Health, Automation), and a wider page. Switching language no longer flashes the Remote tab before going back to the tab on screen.
- 📱 Health → Running apps: which apps run, in the background too, how much memory each takes, and a Stop button for each. Only apps the user installed or with a tile on the home screen can be stopped; the home screen, keyboard, Android, TV Remote, TV Setup and Tailscale are locked (🔒 Must keep), so nothing vital is stopped by mistake.
- 🧹 Full memory notice (Automation tab, off by default): when the TV uses more memory than the level picked (80–95%), TV Setup shows a notice over any app: Free RAM, See the apps (Manage on this TV, Health tab) or Skip; again 30 minutes later at the soonest. Needs the new TV Setup.
- 📺 TV Setup → Manage on this TV: the manage screens (Apps, Files, Health, Automation, Setup) right on the TV, driven with the TV's remote, no phone or computer needed. Works with the web remote turned off too.
- 📲 Share (More keys → Share): a QR code another phone scans to open the TV's remote straight away, no code to type (once, within 10 minutes), with a QR code for the phone app. A warning when this device reaches the TV through Tailscale; without TV Setup only the app's code shows. Needs the new TV Setup.
- ▶️ Now playing: pause / play / next show a spinner until the TV has done it; pressing fast no longer leaves the button and the TV out of step.
- Automation: while TV Setup is being updated, the page no longer says "TV Setup is too old" with an Update button; it waits for TV Setup to start again and shows itself.
- Updates: the version number is inside the signed list; the app refuses a release whose number does not match it (no replaying an old release).
- 📱 Phone app: sharing from another app takes only that app's content:// files and http/https links.
- Computer app: the update's temporary script goes in a private folder.
- App limits: an app already open since before midnight or since the TV woke (no new usage event) is now counted, so it is still closed when its time is up.
- Sleep timer, bedtime, app limits: two checks no longer run at once (no double warning).
- "Try again" while the TV is asking "Allow USB debugging?" reconnects at once instead of sitting idle for about 10 seconds.
- Lighter Health for the TV: facts that rarely change (model, screen, Wi-Fi, IPs) are asked once a minute; the Network and Hardware cards are not redrawn when nothing changed, so an IP or SSID can be selected and copied. The Setup tab on the computer asks the TV less and does not redraw its steps when unchanged. The Apps tab makes one request less. Watching the TV picture is smoother and uses less memory.
- With a dialog open, the computer's arrow keys, Esc and Backspace no longer go to the TV.
- Sending a file / installing an APK has a ■ Stop button; send errors are shown in plain words (and ask to install / update TV Setup when it is missing).
- Toasts stay longer when long. The cancel-timer button and the delete-scene question (names with · or →) are translated.
- The tab bar works with ← → for keyboard and screen-reader users. Install TV Setup says so when the connection is lost instead of waiting for ever.

- 🔐 Share with access levels: pick **Full access**, **Remote only** (keys, typing, the picture, opening apps, the sleep timer, running saved scenes) or **View only** (the TV's picture and what is playing). The TV's owner (the phone app, the computer app, whoever typed the 6-digit code from the TV) changes each device's access in Setup → Paired devices; a guest sees and unpairs only itself. Needs the new TV Setup.
- Pairing by code is no longer held up by a slow browser.
- 💻 Computer app: a new key at each start, carried by its window and the browser it opens (nothing to type); other programs on the computer can no longer drive the TV.
- 📱 Phone app: asks “Send to the TV?” before sending a file or link another app shared.
- Bedtime, app limits, volume limits: ＋ Add opens a small dialog to pick the times and levels, instead of the whole form laid out on the card.
- 📲 Share has two tabs, Share the TV (pick the access, the access buttons with icons) and Share the app, on the phone app and the page.
- Running apps: the 🔒 Keep chip no longer drops to a new line; the CPU / RAM / storage chart's legend keys are small again.
- 📱 Phone app: the media keys and the Back / Home / Menu row use the full width; the TV picture is at most half the screen high.
- 📺 TV Setup's screen is more compact: smaller action tiles, ADB card and Control from a phone column, more room for Activity.
- 📺 Manage on this TV: the Remote tab no longer flashes on opening; ← → on the tabs moves the focus one tab at a time and OK opens it (the tabs passed on the way are not loaded); picking an access level in Paired devices (or any list drawn again) keeps the focus there, so the down arrow goes on to the next device instead of jumping to the top. Moving up a list no longer jumps to the tabs halfway (reaching the tabs lands on the open one), and Restore (the file picker buttons) can be reached.
- 💻 Computer app: opening the app again while it runs no longer breaks the key of the window already open.
- Running a scene with a timer step on a TV that has no TV Setup (or an old one): the phone and computer apps now ask "Install / Update TV Setup?" like other features, instead of only noting "step 1 failed". The three apps share one scene runner.
- 📺 TV app: an open TV web page (or one polling the state) no longer keeps the scrcpy-server process (~70 MB) running forever: only keys, typing, touch, the picture and screenshots count as use, and after 3 minutes without it the session rests. A touch on the page's remote wakes it before the first key; waking now skips one ADB command and finds scrcpy-server sooner.
- 🔒 TV app: the pairing code screen is not started again over itself; asking for codes nobody types makes the TV wait 30 seconds, then longer (up to 5 minutes). Only the TV app and the ADB shell can open the code screen and the timer warning; other apps on the TV cannot.
- Sending a file or installing an APK has a real **■ Stop**: it stops the server side too (the copy, `pm install`, the download), deletes the half-sent file on the TV and the temp files, and the page says "Stopped" rather than showing an error. Also in "Several TVs at once" (computer and phone) and when the phone app plays a file on the TV.
- Before a file is accepted the TV is asked how much room it has; if it is not enough the file is refused before a byte is sent: "The TV has only 1.2 GB free, not enough for this 3.4 GB file... Remove some apps or files, then try again." An APK needs room for the copy and the installed app (twice its size) plus a reserve.
- Real input switching: the “Input” button now lists the TV’s inputs under the names the TV gives them (e.g. “HDMI 1 · PlayStation 5”, AV) and a tap switches. Android TV / Google TV read them over ADB (HDMI-CEC for the device names), LG through its remote link; the switch is Live TV’s own link, falling back to the HDMI key codes. A TV with no inputs to choose (a box, an emulator) or a Samsung shows one line and opens the TV’s own picker as before. In the Phone app, the Computer app and the TV web page; Scenes get a “Switch input” step.
- 📡 Pair with Wireless debugging (Android 11+): for a TV with no “USB debugging” on port 5555, open Turn on ADB for me → Pair with Wireless debugging, then type the IP:port and the 6-digit code the TV shows (the IP:port is prefilled when found on the network). The app finds the connect port by itself, remembers it per TV, and never asks “Allow USB debugging?”. In the Phone app and the Computer app (the TV app and the TV web page do not need it).
- Sending a big file (about 70 MB and up) to a recent Android TV no longer stalls halfway with “the TV stopped taking data”: the app waits for the TV to write what it has before sending more. Stop during an APK install now reads “Stopped” instead of an error. Wireless debugging pairing: the connect-port step asks only for the port, not the used code again. 📱 Phone app: a button's icon sits beside its centred words.
- Health tab: the TV's temperature (heat level and each sensor), GPU load (where the TV exposes it), the network traffic right now (down / up), and Bluetooth: on or off, paired devices, which are connected (remotes, gamepads).

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
