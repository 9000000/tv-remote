# Changelog / Nhật ký thay đổi

Ba số phiên bản, mỗi số chỉ tăng khi phần của nó đổi: `VERSION` (bản phát hành và app máy tính), `app/android/VERSION` (app điện thoại), `app/tv/VERSION` (app TV). Bản phát hành kèm file cài nằm ở [Releases](https://github.com/HoangTran0410/tv-remote/releases); các app tự kiểm tra bản mới ở đó.

Three version numbers, each bumped only when its own part changes: `VERSION` (the release and the computer app), `app/android/VERSION` (phone app), `app/tv/VERSION` (TV app). Releases with the install files are on the [Releases](https://github.com/HoangTran0410/tv-remote/releases) page; the apps check there for updates.

## Unreleased

**Tiếng Việt**
- Tối ưu TV gặp TV Home cũ trên TV thì cập nhật lên bản mới nhất trước khi đổi màn hình chính (cập nhật lỗi thì dùng bản đang có).
- App điện thoại / máy tính cũ hơn TV Home không còn làm mất widget mới của TV Home khi bật thêm hay đổi cỡ một widget.
- TV Home: widget lịch tháng thiết kế lại, gọn kiểu Apple: hôm nay khoanh đỏ, chỉ ghi mùng 1 và rằm âm lịch, Chủ nhật không còn đỏ chói.

**English**
- Optimize TV updates an older TV Home on the TV to the latest before making it the home screen (a failed update keeps the one there).
- A phone / computer app older than TV Home no longer drops TV Home's newer widgets when turning one on or resizing one.
- TV Home: the month calendar widget redesigned, cleaner, Apple-like: today in a red circle, only the lunar 1st and 15th marked, Sundays no longer bright red.

## 0.16.0 - 2026-10-21

Web 0.16.0 · app điện thoại 0.16.0 · app TV 0.16.0 · TV Home 0.3.0 · TV Channels 0.1.0

**Tiếng Việt**
- Tối ưu TV dùng TV Home làm màn hình chính thay cho FLauncher: 0,2 MB thay vì 10 MB, lấy từ release của dự án (ký và kiểm SHA-256), chỉnh được từ điện thoại / máy tính.
- Tối ưu TV đọc thêm danh sách app gợi ý tắt / phải giữ từ trang web của dự án (`docs/optimize/bloat.json`, có chữ ký): TV mới được thêm vào mà không cần ra bản app mới. Danh sách này chỉ thêm được app phải giữ, không bớt được.
- TV Channels (app mới): xem kênh truyền hình miễn phí từ danh sách công khai của iptv-org, theo quốc gia và thể loại, có yêu thích, tìm kiếm, đổi kênh bằng ▲▼ / số; kênh không phản hồi được làm mờ.
- TV Channels: tự gom kênh thành nhóm (Yêu thích cũng là một nhóm); ghim một kênh hay cả nhóm ra màn hình chính, bấm vào là mở đúng nhóm đó.
- TV Home: app khác có thể ghim lối tắt vào Yêu thích (vd. một kênh của TV Channels); nhấn giữ để bỏ ghim.
- TV Channels: thêm các kênh miễn phí của VieON (VTV, HTV, HTVC, đài tỉnh, radio VOH): VTV1 xem được cả khi nguồn iptv-org bị chặn; nguồn kênh nằm trong một danh sách ký trên web nên sửa / tắt được mà không cần bản mới.
- Gỡ app an toàn hơn: không tắt / xoá dữ liệu / gỡ app đang giữ mạng Wi-Fi đã lưu (mất app là TV quên mạng, mất cả ADB); không mở app kiểm tra của nhà máy (một TV Haier mở một lần là kẹt chế độ nhà máy, mất nút Input). TV Setup xin thêm quyền tự chạy trên TCL để sửa nút remote chạy được.
- TV Channels: kênh VieON trong Yêu thích / nhóm không còn mất khi mở lại app; lịch phát sóng không làm chậm lúc chuyển kênh; bấm Home lúc đang mở kênh không còn phát tiếng ngầm.
- TV Channels: lịch phát sóng kênh VN (từ TV360 và VieON): thẻ kênh hiện chương trình đang chiếu; khi xem, ▶ hiện chương trình đang chiếu / tiếp theo, ▶ lần nữa mở lịch cả ngày; giữ OK trên kênh → Lịch phát sóng.
- Tab TV có thẻ 📺 Kênh TV: cài / cập nhật TV Channels lên TV, gõ tên kênh để mở; nói "mở kênh VTV1" cũng được.
- TV Home: thẻ widget có nền mờ của ảnh nền (như dải Yêu thích), chữ dễ đọc trên ảnh nhiều màu; ảnh mờ mịn hơn. Mức chuyển động (Tự động / Nhẹ / Đầy đủ; TV ít RAM tự dùng Nhẹ). Banner app nét hơn. Lên / xuống quay về đúng ô Yêu thích vừa rời; về Home thì đứng lại ở app vừa mở. Dải Yêu thích cân trên dưới (tên app chỉ hiện khi chọn).

**English**
- Optimize TV makes TV Home the home screen instead of FLauncher: 0.2 MB instead of 10 MB, from the project's release (signed, SHA-256 checked), set up from the phone / computer.
- Optimize TV also reads which apps to suggest / always keep from the project's site (`docs/optimize/bloat.json`, signed): a new TV's apps are added without a new app. That list can only add apps to keep, never remove them.
- Safer app removal: an app that owns a saved Wi-Fi network is never turned off, cleared or removed (the TV would forget the network, and ADB with it); factory test apps are never opened (one Haier went into factory mode, Input key dead). TV Setup asks TCLs to let it auto-start, so the remote key fix runs there.
- TV Channels: VieON channels stay in favourites and groups after a restart; the programme guide no longer slows channel changes; Home while a channel loads no longer leaves sound playing.
- TV Channels: programme guide for Vietnamese channels (from TV360 and VieON): cards show what is on; while watching, ▶ shows now / next and ▶ again the day's guide; hold OK on a channel → Programme guide.
- TV Channels: VieON's free channels added (VTV, HTV, HTVC, provinces, VOH radio): VTV1 plays even where iptv-org's source is blocked; the sources come from a signed list on the site, so they can be fixed or turned off without a new release.
- TV Channels (new app): free TV channels from iptv-org's public lists, by country and category, with favourites, search, ▲▼ / number keys; channels that do not answer are dimmed.
- TV Channels: your own groups of channels (Favourites is one of them); pin a channel or a whole group to the home screen, which opens that group.
- TV Home: other apps can pin shortcuts to the favourites (e.g. a TV Channels channel); long-press to unpin.
- TV tab: a 📺 TV channels card to install / update TV Channels and open a channel by its name; say "open channel BBC News" too.
- TV Home: widget cards get the wallpaper's blurred copy behind them (as the favourites band does), readable over a busy photo; a smoother blur. Motion level (Auto / Calm / Full; Calm on low-RAM TVs). Sharper app banners. Up / Down return to the favourite you left; back home, the app you opened keeps the focus. The favourites band has even space above and below (the name shows only on focus).

## 0.15.1 - 2026-10-21

Web 0.15.1 · app điện thoại 0.15.1 · app TV 0.15.1 · TV Home 0.2.1

**Tiếng Việt**

- Sửa: thẻ Hệ thống của TV Home không còn ghi "0.00 GHz" khi TV báo sai tốc độ CPU (máy ảo báo 2 kHz)
- Hướng dẫn: rà lại 36 mục theo bản 0.15.0, thêm ảnh TV Home và bảng Tùy chỉnh trên TV
- Tối ưu TV: thêm 29 gói quảng cáo / theo dõi / tự cài app (Hisense, Sharp, Vestel, CVTE, XGIMI, Fire TV…); giữ lại hơn 40 gói đã có người báo hỏng khi tắt (nút Nguồn vào, phím remote, cập nhật); Xiaomi TV Manager không còn bị đề xuất tắt (XiaoAI cần nó)
- An toàn hơn: không tắt app nào giữ cổng HDMI / AV / tuner của TV (tắt rồi có TV báo "Không có tín hiệu" luôn); đổi launcher thì bấm thử Home xem thật sự mở gì; không đổi launcher trên TV Kalley / MOKA (kẹt logo khi khởi động lại); TV Home mở được ngay từ lúc TV khởi động
- TV Home mượt hơn trên TV yếu: hình nền vẽ thẳng vào màn hình, TV không phải ghép 2 lớp mỗi khung hình (BRAVIA: lướt app từ 45 ms còn ~10 ms một khung)
- TV Home: nút Nguồn vào xổ danh sách cổng (tên đặt trên TV, thiết bị đang cắm qua HDMI-CEC, chấm xanh "Đã kết nối"); Cài đặt và Tùy chỉnh gộp thành một nút có menu; các nút trên đầu và menu kiểu kính như các thẻ; phím Lên từ hàng app tới được các nút trên đầu (TV Android 9)
- Trang TV Home: tên widget không còn bị các nút ép hẹp ở màn hình nhỏ
- TV Home: ô đang chọn thành một tấm kính lướt giữa các app (như Apple TV), không phủ lên ảnh app; muốn viền xanh như cũ thì chọn ở Tùy chỉnh → Ô đang chọn

**English**

- TV Home smoother on weak TVs: the wallpaper is drawn into the window, so the TV no longer blends two full-screen layers every frame (a BRAVIA: moving between apps from 45 ms to ~10 ms a frame)
- TV Home: Inputs drops a list of ports (the names set on the TV, the HDMI-CEC device plugged in, a green "Connected" dot); Settings and Customize are one button with a menu; the buttons on top and their menus are glass like the cards; Up from the apps reaches the buttons on top (Android 9 TVs)
- TV Home page: widget names no longer squeezed by their buttons on a narrow screen
- TV Home: the focus is a pane of glass gliding from app to app (as on Apple TV), over the edge only, so banners keep their colours; the old blue ring is in Customize → Focus
- Fix: TV Home's System card no longer says "0.00 GHz" when the TV reports a bogus CPU speed (the emulator says 2 kHz)
- Guide: 36 topics checked against 0.15.0, with pictures of TV Home and its Customize panel on the TV
- Optimize TV: 29 more ad / tracking / auto-install packages (Hisense, Sharp, Vestel, CVTE, XGIMI, Fire TV…); 40+ packages kept that owners lost something without (the Input button, remote keys, updates); Xiaomi TV Manager no longer suggested (XiaoAI needs it)
- Safer: no app that holds the TV's HDMI / AV / tuner is turned off (one TV showed "No Signal" for good); a launcher swap presses Home to see what really opens; no swap on Kalley / MOKA TVs (they looped at the logo after a restart); TV Home starts from the moment the TV boots

## 0.15.0 - 2026-10-21

Web 0.15.0 · app điện thoại 0.15.0 · app TV 0.15.0 · TV Home 0.2.0

**Tiếng Việt**

- TV Home: **hình nền xoay vòng**. 10 hình nền vẽ sẵn (bình minh, núi xa, đồi cát, sóng, kẹo, đào, đồng cỏ, sương mù, lấp lánh, hẻm đá: vẽ bằng code, không sọc màu, xem trước trên điện thoại / máy tính giống hệt trên TV), ảnh Bing mỗi ngày (không cần khóa, lưu trong máy, mất mạng thì dùng ảnh cũ) và tới 10 ảnh của bạn. Chọn nhiều hình nền rồi đặt đổi sau 30 phút / 1 giờ / 6 giờ / mỗi ngày / mỗi lần mở Home; chỉ đổi khi Home đang hiện, không chạy ngầm. Hộp thoại và bảng Tùy chỉnh trên TV có cùng lựa chọn. TV Home cũ vẫn dùng một hình nền
- TV Home: chọn ngôn ngữ riêng (Theo TV / Tiếng Việt / English) trên TV và trên app; thẻ thời tiết gọn hơn (trời, cao/thấp nằm cạnh nhiệt độ)
- Sửa: viền focus của TV Home không còn mép đen, banner nền trắng không lòi ra ngoài góc viền
- TV Home: thẻ **Hệ thống** to làm lại thành lưới số liệu: mỗi ô có icon, con số lớn, tên nhỏ, thanh mảnh cho CPU / RAM / bộ nhớ. Thêm tên máy, Android, tốc độ CPU, thời gian đã bật, tốc độ Wi-Fi (Mbps), DNS riêng (AdGuard, NextDNS… màu xanh) và cổng ADB (màu vàng khi bật). Thẻ rộng 4 cột, hẹp hơn 3 hoặc 2, số dài tự thu nhỏ, không bị cắt
- TV Home: ảnh Bing giữ **8 ngày gần nhất**, xoay vòng hết 8 ảnh (mới nhất trước) rồi mới sang hình nền kế tiếp; chỉ chọn mỗi Ảnh Bing cũng xoay được. Bấm lại ô Ảnh Bing (khi đang dùng nó) là đổi sang ảnh ngày trước. Ảnh mới của ngày hiện lên trong vòng 3 giờ kể cả khi Home vẫn để nguyên trên màn hình
- TV Home: bảng Tùy chỉnh trên TV mượt hơn trên box yếu (đo trên FPT Play: mở bảng ~65 → ~40 ms/khung, di chuyển giữa các ô ~55 → ~27 ms/khung). Ảnh thu nhỏ của hình nền làm sẵn đúng cỡ và được giữ lại cho lần mở sau
- TV Home: chuyển động mượt kiểu Apple TV: focus nảy lên như lò xo, ánh sáng phủ lên thẻ, thẻ rung nhẹ khi bấm vào cạnh. Hình nền vẽ một lần trên lớp riêng: box yếu chạy 60 khung/giây thay vì ~40, gradient và ảnh nền hết bị sọc màu
- Sửa: Tối ưu TV không còn làm hiệu ứng trong app nhanh gấp đôi (animator 0.5), vốn làm cuộn và focus của TV Home bị giật. Giờ chỉ tắt hiệu ứng chuyển cảnh giữa các màn hình; TV đã tối ưu trước đây chạy lại Tối ưu TV là về bình thường
- Widget của TV Home: **mỗi thẻ chọn To hoặc Nhỏ** thay cho các cách xếp có sẵn. Thẻ to có một cột riêng, hai thẻ nhỏ liền nhau chung một cột, thẻ nhỏ đứng một mình là cột hẹp; thẻ nào hết chỗ thì chờ (hộp thoại ghi "Chưa hiện"), hàng app yêu thích luôn còn trên màn hình. Mỗi thẻ có dạng nhỏ riêng (thời tiết: nhiệt độ và cao / thấp, hệ thống: các vòng, lịch: ngày hôm nay và ngày âm, app dùng nhiều: 2 app đầu). Hộp thoại trên điện thoại / máy tính có nút To / Nhỏ bên cạnh ↑ ↓ ✕ và hình thu nhỏ kết quả; trên TV bấm nút To / Nhỏ ở mỗi thẻ. Thứ tự đổi được bằng ↑ ↓. Cách xếp cũ tự đổi thành các cỡ cho ra cùng hình. TV Home cũ vẫn thấy các nút cách xếp cũ
- TV Home hiện tên Wi-Fi đang kết nối trên nút mạng (quyền vị trí Android đòi được cấp qua ADB khi cài hoặc chỉnh TV Home, TV không hỏi gì)
- TV Home có widget **Thời tiết** (Open-Meteo, không cần tài khoản): nhiệt độ lúc này, cao / thấp trong ngày và 3 ngày tới, icon trời. Nơi ở tự tìm: thành phố gõ ở hộp thoại TV Home trên điện thoại / máy tính, không thì vị trí của TV, không có nữa thì lấy theo địa chỉ mạng (box FPT Play không có định vị nên dùng cách này). Lấy lại mỗi 45 phút khi đang ở màn hình chính, mất mạng thì giữ số cũ. TV Home được thêm quyền INTERNET
- TV Home có widget (bật trong Tùy chỉnh TV Home, trên TV hoặc điện thoại / máy tính): **Hệ thống** (vòng RAM, CPU, bộ nhớ, sóng Wi-Fi, biểu đồ mạng trực tiếp, IP, ADB) và **App dùng nhiều** (cột 7 ngày, top app có icon, thời gian còn lại của giới hạn app, lấy từ TV Setup), **Lịch tháng** (dương lịch và âm lịch trong từng ô ngày, hôm nay tô màu, Chủ nhật màu hồng, mùng 1 ghi cả tháng âm), **Lịch âm** (ngày âm, can chi, còn mấy ngày tới rằm / mùng 1 / Tết, không cần mạng), **Hẹn giờ** (giờ TV tắt, giờ ngủ) và **Ghi chú** gõ từ điện thoại, ghim lên màn hình chính tới khi gỡ. Nhẹ cho TV yếu: chỉ cập nhật khi đang ở màn hình chính
- Bảng Tùy chỉnh TV Home trên TV làm lại: ô bật / tắt có icon, hình nền dạng lưới, trượt vào từ bên phải
- TV Home có bản mới cũng được nhắc như TV Setup, chung một thanh gọn (một nút Cập nhật cho cả hai). **Để sau** hỏi khi nào nhắc lại: sau 1, 7, 30 ngày, hoặc bỏ qua bản này
- Thẻ 📺 TV (tab Thiết lập) có hàng **Màn hình**: Tắt / bật màn hình và Giảm độ sáng, không cần mở remote
- Remote hồng ngoại: chọn hãng không còn là danh sách số model. Model dùng chung một mã gộp làm một; hãng có nhiều kiểu thì hiện **Kiểu 1 (phổ biến nhất)**, **Kiểu 2**... kèm vài model và số phím. Nút hãng không còn xuống dòng
- Remote hồng ngoại lưu được nhiều remote có tên (TV phòng khách, box nhà mạng...): mở Remote hồng ngoại là thấy danh sách, bấm là dùng, không phải dò lại. **Thêm remote** để chọn hãng hoặc dò mã rồi đặt tên; đổi tên, xóa ngay trong danh sách; **Xuất file** / **Nhập file** để chuyển sang điện thoại khác (nhập chỉ thêm remote chưa có)
- Nút **TV Home** trong menu Thêm của remote: mở Tùy chỉnh TV Home (chưa cài thì dẫn tới thẻ Màn hình chính)
- Launcher không đang làm màn hình chính (FLauncher...) không còn bị coi là "Cần giữ": gỡ, tắt được; launcher đang dùng vẫn được giữ
- TV Home không hiện các app hệ thống chỉ dành cho điện thoại mà hãng để lại (thông báo hệ thống, phóng to màn hình, app mẫu SystemUpdaterSample...)
- Tối ưu TV biết thêm 48 gói rác của nhiều hãng (Hisense, Philips, Sony, Fire TV, NVIDIA, Panasonic, Xiaomi...) và giữ nguyên thêm khoảng 150 gói mà chủ TV báo là tắt thì hỏng (nguồn vào, bộ dò kênh, phím Input, ghép remote)
- App TV: **tự bật lại nút remote bị chết** khi launcher gốc (Google TV) bị tắt hoặc đổi: Nguồn vào, YouTube, Netflix, Prime Video, PatchWall (Xiaomi), Cài đặt, Tất cả app. Chỉ nhận nút mà hệ thống gửi cho một app không còn nữa; nút còn chạy thì không đụng tới. Tự bật khi cần, tắt / bật ở **Tối ưu TV → Nút điều khiển** trên màn hình TV Setup
- Màn hình TV Setup gọn lại còn 4 ô: **Quản lý trên TV**, **Tối ưu TV**, **Kết nối**, **Cập nhật**; ô gom việc mở một menu ngắn. Một hàng trên TV, 2x2 trên điện thoại
- Sửa: tab Ứng dụng: gỡ / tắt một app không còn tải lại cả danh sách
- Sửa: app điện thoại: bấm Quay lại từ menu con thì tiêu đề menu trước bị sai
- Sửa: TV Home: app có "banner" vuông (Media Center của Rockchip) hiện icon và tên thay vì một ô mờ; nút Input của remote mở danh sách nguồn vào khi TV đưa phím đó cho màn hình chính
- Sửa: TV đã lưu: trong thẻ hẹp (nửa trang trên máy tính) các nút xuống dòng dưới tên TV, không ép chữ
- TV Home: **Màn chờ TV Home**: công tắc trong Tùy chỉnh (trên TV và ở hộp thoại TV Home của app) đặt đồng hồ lớn, ngày dương / âm và hình nền làm màn hình chờ của TV, tự dời chỗ mỗi phút; tắt thì màn chờ cũ được trả lại
- TV Home: ô app có ổ khóa khi bị khóa PIN, hết giờ giới hạn hoặc đang giờ ngủ, và ⏳ kèm số phút còn lại khi có giới hạn (cần TV Setup mới; TV Setup cũ thì không có huy hiệu, mọi thứ khác như cũ)
- TV Home nhẹ hơn trên TV yếu (box 2 GB): ảnh nền tối đa 1920x1080 và 2 byte mỗi điểm ảnh; ảnh nền và banner được nhả khi màn hình ẩn mà máy thiếu RAM, nạp lại khi quay về; cài / gỡ / cập nhật một app chỉ đổi ô của app đó
- Sửa: TV Home: bấm Home khi đang ở màn hình chính làm cả màn hình nháy (trừ widget); giờ không đổi gì, chỉ đóng bảng đang mở hoặc đưa focus về app đầu
- Sửa: TV Home: bấm xuống từ hàng đầy sang hàng cuối ngắn không đi đâu; giờ nhảy tới ô gần nhất, nhớ cột khi lên xuống, giữa hàng yêu thích và lưới nhảy tới ô đối diện
- Sửa: TV Home: viền focus giờ xanh có viền tối, thấy rõ trên banner trắng (YouTube, Play Store), kể cả khi tắt hiệu ứng

**English**

- TV Home: **rotating backgrounds**. 10 drawn backgrounds (dawn, peaks, dunes, waves, candy, peach, meadow, mist, bokeh, canyon: painted by code, no banding, the preview on the phone / computer matches the TV), Bing's picture of the day (no key, cached on the TV, the last one when offline) and up to 10 of your own photos. Pick several and set the change to 30 min / 1 hour / 6 hours / daily / each time Home opens; it changes only while Home is on screen, nothing runs in between. The dialog and the TV's Customize panel have the same choices. An older TV Home keeps its one background
- TV Home: a language of its own (Same as the TV / Tiếng Việt / English), on the TV and in the app; a tighter weather card (sky and high / low beside the temperature)
- Fix: TV Home's focus ring has no dark edge any more, and a white banner no longer shows past its corners
- TV Home: the big **System** card redone as a grid of figures: an icon, the figure large and its name small, a thin bar for CPU / RAM / storage. It adds the model, Android, the CPU's speed, uptime, the Wi-Fi link speed (Mbps), Private DNS (AdGuard, NextDNS… in green) and the ADB port (amber when on). 4 columns when wide, 3 or 2 when narrower; long figures shrink instead of being cut
- TV Home: Bing keeps the **last 8 days' pictures** and the rotation turns through all of them (newest first) before the next background; Bing alone rotates too. Pressing the Bing tile again (while it is on) shows the day before. A new day's picture shows within 3 hours, even with Home left on screen
- TV Home: the Customize panel on the TV is smoother on weak boxes (measured on an FPT Play box: opening ~65 → ~40 ms a frame, moving between tiles ~55 → ~27 ms). The backgrounds' small pictures are made at their size and kept for the next opening
- TV Home: smoother, Apple TV-like motion: focus rises on a spring, light settles on the card, a card nudges at an edge. The wallpaper is painted once on its own layer: on a weak box focus moves at 60 frames a second instead of ~40, and gradients and photos no longer show bands
- Fix: Optimize TV no longer halves the apps' own animations (animator scale 0.5): TV Home's scroll and focus ran twice as fast and looked jerky. It now turns off only the screen-to-screen transitions; running Optimize TV again puts a TV optimized before back to normal
- TV Home's widgets: **each card is Big or Small** instead of preset layouts. A big card gets a column, two small cards in a row share one, a small card on its own is a narrow column; a card with no room waits (the dialog says "Not shown yet"), and the favourite apps always stay on screen. Each card has its own small form (weather: temperature and high / low, system: the rings, calendar: today's date and lunar date, most used apps: the top 2). The dialog on the phone / computer has Big / Small buttons beside ↑ ↓ ✕ and a tiny picture of the result; on the TV press the Big / Small button on a card. The order changes with ↑ ↓. An old layout turns into the sizes that gave the same picture. An older TV Home still sees the old layout buttons
- TV Home shows the Wi-Fi's name on its network button (the location permission Android asks for is granted over ADB when TV Home is installed or set up, nothing asked on the TV)
- TV Home has a **Weather** widget (Open-Meteo, no account): the temperature now, today's high / low and the next 3 days, a sky icon. The place is found by itself: a city typed in the TV Home dialog on the phone / computer, else the TV's own location, else the internet address's (the FPT Play box has no location provider, so it uses this). Asked again every 45 minutes while the home screen is shown, the last answer kept when offline. TV Home gets the INTERNET permission
- TV Home has widgets (turned on in Customize TV Home, on the TV or from the phone / computer): **System** (rings for memory, CPU, storage and Wi-Fi signal, a live network graph, IP, ADB) and **Most used apps** (7 days as columns, top apps with icons, time left on app limits, from TV Setup), **Month calendar** (the solar and the lunar day in every cell, today filled, Sundays rose, a lunar 1st shows its month), **Lunar calendar** (the lunar date, the year's name, days to the full moon / new moon / Tết, offline), **Timer** (when the TV turns off, bedtime) and a **Note** typed on the phone, pinned on the home screen until cleared. Light on weak TVs: they update only while the home screen is shown
- The Customize TV Home panel on the TV redone: toggle tiles with icons, backgrounds in a grid, slides in from the right
- New TV Home versions are offered like TV Setup's, in the same one-line bar (one Update for both). **Later** asks when: in 1, 7 or 30 days, or skip this version
- The 📺 TV card (Setup tab) has a **Screen** row: Screen off / on and Dim the screen, without opening the remote
- Infrared remote: picking a brand is no longer a list of model numbers. Models that share one code are one; a brand with several shows **Type 1 (most common)**, **Type 2**... with a few models and the number of keys. The brand button stays on one line
- Infrared remote keeps several remotes by name (living room TV, the operator's box...): Infrared remote opens on that list, one tap to use one, no finding again. **Add a remote** picks a brand or finds the code, then names it; rename and delete in the list; **Export** / **Import** to move them to another phone (import adds only the ones not there)
- A **TV Home** button in the remote's More menu: opens Customize TV Home (or, when it is not installed, the Home screen card)
- A launcher that is not the home screen now (FLauncher...) is no longer "Must keep": it can be removed or turned off; the one in use is still kept
- TV Home leaves out the phone-only system apps a maker left behind (system notification, screen zoom, the SystemUpdaterSample demo...)
- Optimize TV knows 48 more packages from many brands (Hisense, Philips, Sony, Fire TV, NVIDIA, Panasonic, Xiaomi...) and leaves about 150 more that owners found break the TV when off (inputs, tuner, the Input key, remote pairing)
- TV app: **dead remote buttons come back** when the stock (Google TV) launcher is turned off or swapped: Input, YouTube, Netflix, Prime Video, PatchWall (Xiaomi), Settings, All apps. It only takes a key the system sends to an app that is gone; a button that still works is left alone. On only when needed; switch it in **Optimize TV → Remote buttons** on the TV Setup screen
- The TV Setup screen down to 4 tiles: **Manage on this TV**, **Optimize TV**, **Connection**, **Update**; a grouped tile opens a short menu. One row on a TV, 2x2 on a phone
- Fix: Apps tab: removing or turning off an app no longer reads the whole list again
- Fix: phone app: Back from a sub-menu showed the wrong title on the menu before
- Fix: TV Home: an app with a square "banner" (Rockchip's Media Center) shows its icon and name instead of a blur; the remote's Input key opens the inputs where the TV hands that key to the home screen
- Fix: saved TVs: in a narrow card (half the page on a computer) the buttons go under the TV's name instead of squeezing it
- TV Home: **TV Home screensaver**: a switch in Customize (on the TV and in the app's TV Home dialog) makes the big clock, the solar / lunar date and the wallpaper the TV's screensaver, moving every minute; switched off, the previous screensaver comes back
- TV Home: a tile has a padlock when the app is PIN-locked, its time limit is used up or it is bedtime, and ⏳ with the minutes left when it has a limit (needs a newer TV Setup; an older one shows no badges, everything else as before)
- TV Home is lighter on weak TVs (2 GB boxes): the wallpaper is at most 1920x1080 at 2 bytes a pixel; the wallpaper and banners are let go while the screen is hidden and memory is short, and come back on return; installing / removing / updating an app changes only that app's tile
- Fix: TV Home: pressing Home on the home screen made the whole screen flicker (all but the widgets); now nothing changes, it only closes an open panel or moves focus to the first app
- Fix: TV Home: pressing Down from a full row to a short last row went nowhere; it now goes to the nearest tile, remembers the column on Up / Down, and hands over between the favourites and the grid
- Fix: TV Home: the focus ring is blue with a dark edge, clear on white banners (YouTube, Play Store), with motion off too

## 0.14.0 - 2026-10-20

Web 0.14.0 · app điện thoại 0.14.0 · app TV 0.14.0 · TV Home 0.1.0

**Tiếng Việt**

- Sửa: Ứng dụng → Đang mở: app đã đóng vẫn hiện lại sau vài giây trên Android 9 (BRAVIA); danh sách không còn nháy mỗi lần làm mới (biểu tượng giữ nguyên, mục Hệ thống Android không tự gập)
- Ứng dụng → Đang mở: “Đóng tất cả” thành nút **Đóng hàng loạt** nổi bật, có biểu tượng; bấm vào hiện danh sách app sẽ đóng để tích / bỏ tích, app TV cần giữ hiện mờ, không chọn được
- Menu mở từ menu khác (chọn hãng → chọn mã ở remote hồng ngoại, Mở trên TV → Tải app...) có nút **Quay lại**; nút back của điện thoại và Esc cũng về menu trước, không phải đóng rồi mở lại. App điện thoại: nút Đóng của hộp thoại thành ✕ ở góc trên, như trên web
- Sửa: Dò mã (remote hồng ngoại): nút “Tắt tiếng” xuống hai dòng và bị lệch khỏi hàng
- Sửa: vài màu quá nhạt (nút Xóa đỏ khi rê chuột, chữ xám ở giao diện tối, chip khởi động), vài chỗ bấm quá nhỏ trên điện thoại (chip trên cùng, tiêu đề thẻ gập, ô tích, màu vẽ), nút chỉ có biểu tượng (xóa, sửa) không có tên cho trình đọc màn hình
- Remote hồng ngoại: **Dò mã tự động** trên Android TV đã kết nối. Hướng đèn vào TV, điện thoại thử từng mã, TV (qua ADB) báo khi nhận được: không phải ngồi xem và bấm. TV khác, hoặc box cắm vào TV thường, vẫn dò tay. Mở từ remote của TV (Thêm → Hồng ngoại): mỗi TV nhớ mã riêng

**English**

- Fix: Apps → Open: a closed app came back after a few seconds on Android 9 (BRAVIA); the list no longer flickers on each refresh (icons kept, Android system stays open)
- Apps → Open now: “Close all” is now a clear **Close in bulk** button with an icon; it lists the apps it will close to tick or untick, apps the TV needs shown greyed and not selectable
- A menu opened from another (brand → remote in the infrared remote, Open on TV → Get apps...) has a **Back** button; the phone's back key and Esc go back too, no closing and opening again. Phone app: a dialog's Close is now ✕ at the top corner, as on the page
- Fix: Find code (infrared remote): the “Mute” chip went to two lines and dropped out of its row
- Fix: a few colours too faint (red Delete when hovered, grey text in dark mode, the boot chip), small things to press on a phone (top chips, folded cards' titles, ticks, the drawing colour), icon-only delete / edit buttons with no name for screen readers
- Infrared remote: **automatic Find code** on a connected Android TV. Point the LED at the TV; the phone tries each code and the TV (over ADB) says when one arrives: no watching and tapping. Other TVs, or a box plugged into a plain TV, still find it by hand. Open it from a TV's remote (More → Infrared): each TV keeps its own code

## 0.13.0 - 2026-10-20

Web 0.13.0 · app điện thoại 0.13.0 · app TV 0.13.0 · TV Home 0.1.0

**Tiếng Việt**

- Sửa: Quay lại sau khi cài một launcher mới có thể về nhầm launcher khác (box FPT, Android 14): Android trả lời sai launcher đang dùng ngay sau khi có launcher mới; giờ đọc lựa chọn Home thật (role HOME); Android 9 (BRAVIA) quên hẳn launcher đang dùng khi cài launcher mới, nên giờ ghi lại nó trước khi cài, và không bao giờ ở lại launcher vừa thử
- TV Home: sửa viền app / nút to lên khi chọn bị cắt ở mép (nút Nguồn vào / Mạng / Cài đặt trên cùng); Phải ở app cuối hàng sang app đầu hàng sau (không còn nhảy lên nút Cài đặt), Trái ngược lại, hai đầu lưới và hàng yêu thích thì đứng yên
- TV Home có màn hình **Tùy chỉnh TV Home** ngay trên TV, dùng phím mũi tên: hình nền (16 màu + ảnh nếu có), đồng hồ 24 giờ, lời chào, hiệu ứng chuyển động. Mở bằng nút thanh trượt cạnh Cài đặt, phím Menu, hoặc mục đầu menu giữ OK; đổi xong áp dụng ngay và trang web đọc cùng một cấu hình
- Thư viện launcher: launcher không có ngôn ngữ của TV có nhãn ngôn ngữ nó sẽ hiện, và câu hỏi trước khi cài nói rõ; trên Android 13 trở lên nó được đặt hiện tiếng Anh, chỉ riêng nó: LTV trên TV tiếng Việt từng hiện tiếng Ả Rập từ phải sang trái, UnitedU hiện tiếng Trung; ngôn ngữ của mỗi launcher đọc từ chính APK khi ký danh sách
- Sửa: đổi launcher không còn tắt cả app cài đặt ban đầu của Google (setupwraith), chỉ tắt màn hình Home của nó và chỉ khi nó nhận nút Home. Tắt cả app làm Android xoá Wi-Fi đã lưu: một FPT Play Box mất mạng và mất ADB. Lần đổi bị treo lâu cũng không còn để lại app bị tắt
- Thư viện launcher: thêm UnitedU, Dizzify, AndSmartTV, TIVI; câu hỏi trước khi cài rõ hơn (Giữ / Quay lại, tự về sau 30 giây); tiến trình tải / cài hiện ở thẻ ⏳ Tiến trình chung của trang, có Dừng, Giữ, Quay lại; trả lời Giữ / Quay lại trên điện thoại hay máy tính thì câu hỏi trên TV cũng đóng; thẻ Tiến trình nằm trên màn hình TV thu nhỏ, không bị che
- 📡 Remote hồng ngoại (chỉ app điện thoại, máy có đèn IR): không cần mạng, không cần chọn TV. Mã Samsung, LG, Sony, TCL, Philips, Sharp, Toshiba, Hisense, Android TV box, Mi Box... (Flipper-IRDB, CC0), **Dò mã** cho thiết bị không có trong danh sách (vd FPT Play Box).
- TV Home mượt và đẹp hơn, kiểu Apple TV: app được chọn nhô lên (lò xo, bóng mềm, ánh sáng lướt qua), hàng cuộn êm, lưới app sáng dần khi bấm xuống, đồng hồ và hàng yêu thích hiện dần; hàng yêu thích mờ như kính (ảnh nền làm nền ảnh mờ). Vẫn nhẹ: View thường, không thư viện, APK chỉ nặng thêm ~3 KB. Công tắc **Hiệu ứng chuyển động** trong hộp thoại TV Home (và trong menu giữ OK trên TV); tắt thì đổi tức thì, còn viền sáng. Cũng tắt khi Android đặt "ảnh động" về 0
- 🖼 **Hình nền đẹp** cho TV Home: thêm mười bộ màu (chuyển sắc và màu đơn, vẽ không cần ảnh nên không tốn thêm RAM) và 12 ảnh (ảnh công khai của NASA và hai ảnh dựng sẵn) trong hộp thoại TV Home, có ghi nguồn; chỉ ảnh được chọn mới tải về, đúng 1920×1080 (~200–450 KB), kiểm SHA-256 theo danh sách có chữ ký rồi gửi lên TV
- 🔒 Khóa app bằng mã PIN (tab Tự động): mở app đã khóa thì TV hỏi PIN (phím số của remote hoặc bàn phím trên màn hình); dùng tới khi sang app khác hoặc TV tắt. Sai 5 lần chờ 1 phút. PIN chỉ lưu trên TV, dạng băm. Cần TV Setup mới
- Tắt / bật màn hình: bấm lại để bật màn hình từ app. Remote thật của TV cũng bật lại, còn phím gửi từ app thì không (trước đây phím nào cũng bật)
- 🔅 Giảm độ sáng (Thêm phím → Hẹn giờ tắt): phủ màu lên mọi app (Tối, Lọc xanh, Vàng, Nâu cổ, Đỏ đêm, Xanh), độ mạnh 5 đến 90%, đổi ngay khi kéo. Giữ cả khi TV Setup khởi động lại. Cần TV Setup mới
- ⚡ Đo tốc độ mạng của chính TV (Sức khỏe → 📶 Mạng): độ trễ, tải xuống, tải lên, đủ cho 4K / Full HD / HD; có nút Dừng, mở lại trang vẫn thấy lần đo trước
- Chọn app bằng ô có biểu tượng và ô tìm (không cần gõ dấu) thay cho danh sách thả xuống: khóa app, giới hạn app, bước "Mở app" của Cảnh.
- Tab Ứng dụng ghi phiên bản của từng app (cả TV Setup) cạnh tên gói.
- Thư viện launcher: thẻ có ảnh màn hình chính thật cỡ lớn, nút ⋯ (Chi tiết, Mã nguồn / CH Play, Tải APK); sửa lỗi các launcher từ thứ hai trở đi không có nút Cài.
- Sức khỏe: Lỗi app nằm trong Lịch sử; nút Giải phóng RAM lên góc thẻ Tải hệ thống. Tất cả cài đặt: mỗi nhóm là một khối riêng, dễ nhìn.
- App có nhãn 🔒 Cần giữ không còn nút Dừng / Xóa dữ liệu / Tắt / Gỡ (cả khi làm hàng loạt); nhãn này giờ gồm cả app riêng của hãng TV mà TV cần (remote, nguồn vào…). Chi tiết app: nút Cho phép / Không của quyền không còn bị cắt trên Mac; quyền trùng tên (Vị trí, Thiết bị ở gần) ghi thêm tên gốc.
- 🏠 TV Home: launcher riêng của dự án, đứng đầu Thư viện launcher (cài và dùng một chạm, lỗi thì TV tự quay về bản cũ). Đồng hồ, lời chào, nút Nguồn vào / Mạng / Cài đặt, hàng app yêu thích và lưới app; giữ OK trên app để ghim, dời, ẩn. Không quảng cáo. Chỉnh từ điện thoại hoặc máy tính (tab TV → 🏠 → Tùy chỉnh TV Home): app yêu thích, app ẩn, hình nền (có sẵn hoặc ảnh của bạn), đồng hồ 12/24 giờ; TV đổi ngay. Có nút Cập nhật TV Home khi có bản mới
- Launcher cài từ thư viện được biên dịch sẵn ngay khi cài: mở lần đầu nhanh hơn trên TV yếu
- APK app điện thoại nhỏ hơn khoảng 1 MB (bỏ bản cho chip x86 32-bit)
- Menu Mở trên TV gọn hơn: Mở app đã cài (ô chọn app có tìm kiếm, thay cho danh sách mọi app), Tải app (CH Play và các app gợi ý), Mở link, Gửi lên TV (ảnh, video, nhạc, clipboard)
- Giới hạn app mỗi ngày: nút ngắn gọn (+30p), dòng đã dùng ngắn hơn (25p / 3g30 (+2g30)), nút Đặt lại để bỏ thời gian đã cho thêm hôm nay (cần TV Setup mới)
- Menu Hẹn giờ tắt gọn hơn: 30 phút, 1 giờ, 2 giờ; Thời gian khác (15p, 45p, 1g30, tự chọn); Màn hình TV (tắt màn hình vẫn nghe, giảm độ sáng); Giờ đi ngủ
- Tắt màn hình, vẫn nghe: phím đầu tiên trên remote của TV chỉ bật lại màn hình, không mở app đang chọn (cần TV Setup mới)
- Đi từ menu sang tab khác (Giờ đi ngủ, Sửa cảnh) thì cuộn tới đúng thẻ và làm nó sáng lên

**English**

- Fix: Go back after installing a new launcher could land on a different one (FPT box, Android 14): Android misreported the home in use right after a new launcher appeared; the real Home choice (the HOME role) is read now; Android 9 (BRAVIA) forgets the home in use when a new launcher is installed, so it is noted before installing, and Go back never stays on the launcher tried
- TV Home: fixed the focus ring / enlarged buttons being cut off at the edge (the Inputs / Wi-Fi / Settings buttons on top); Right on a row's last app goes to the next row's first (no more jump up to Settings), Left the other way, and at the ends of the grid and the favourites row focus stays
- TV Home has a **Customize TV Home** screen on the TV itself, with the D-pad: background (16 colours + the photo when there is one), 24-hour clock, greeting, motion effects. Open it with the sliders button beside Settings, the Menu key, or the first entry of the hold-OK menu; changes apply at once and the web page reads the same config
- Launcher gallery: a launcher without the TV's language is tagged with the one it will show, and the question before installing says so; on Android 13 and later it is set to show English, that launcher only: LTV on a Vietnamese TV showed Arabic right to left, UnitedU Chinese; each launcher's languages are read from its APK when the list is signed
- Fix: changing the launcher no longer turns off Google's setup app (setupwraith) as a whole, only its Home screen and only where it answers Home. Turning the whole app off made Android delete the Wi-Fi it had saved: an FPT Play Box lost its network and its ADB. A swap that hung for long no longer leaves an app turned off either
- Launcher gallery: UnitedU, Dizzify, AndSmartTV, TIVI added; a clearer question before installing (Keep / Go back, back on its own after 30 seconds); the download / install shows in the page's ⏳ Progress card, with Stop, Keep, Go back; answering Keep / Go back on the phone or computer closes the question on the TV too; the Progress card sits above the small TV picture instead of under it
- 📡 Infrared remote (Phone app only, phones with an IR blaster): no network, no TV picked. Codes for Samsung, LG, Sony, TCL, Philips, Sharp, Toshiba, Hisense, Android TV boxes, Mi Box... (Flipper-IRDB, CC0), and **Find code** for a device in no list (e.g. FPT Play Box).
- TV Home is smoother and prettier, Apple TV style: the focused app lifts (spring, soft shadow, a shine passing over), rows scroll gently, the app grid lights up as you go down, the clock and favourites fade in; the favourites band is frosted (over a photo, a blurred copy of it). Still light: plain Views, no library, the APK grows by only ~3 KB. A **Motion effects** switch in the TV Home dialog (and in hold OK on the TV); off means instant changes with a bright ring. Also off when Android's animation scale is 0
- 🖼 **Wallpapers** for TV Home: ten more colour sets (gradients and solids, drawn without a bitmap, so no extra memory), and 12 pictures (public NASA images and two renders) in the TV Home dialog, credited; only the chosen one is downloaded, at 1920×1080 (~200–450 KB), checked by SHA-256 against a signed list, then sent to the TV
- 🔒 Lock apps with a PIN (Automation tab): opening a locked app asks for the PIN on the TV (the remote's number keys or the keypad on screen); it stays open until another app comes up or the TV turns off. 5 wrong PINs: a minute's wait. The PIN is kept only on the TV, hashed. Needs the new TV Setup
- Screen off / on: tap it again to turn the screen back on from the app. The TV's own remote turns it on too; keys sent from the app no longer do
- 🔅 Dim the screen (More keys → Sleep timer): a tint over every app (Dark, Blue filter, Yellow, Sepia, Night red, Blue) at 5 to 90%, applied as you drag. Kept when TV Setup restarts. Needs the new TV Setup
- ⚡ The TV's own internet speed (Health → 📶 Network): ping, download, upload, and whether that is enough for 4K / Full HD / HD; Stop button, and the last result shows when the page is opened again
- Apps are picked from their icons, with a search (accents not needed), instead of a drop-down list: app locks, app limits, a scene's "Open an app" step.
- The Apps tab shows each app's version (TV Setup's too) beside its package name.
- Launcher gallery: cards with a large picture of the real home screen, and ⋯ (Details, Source / Play page, Download the APK); fixed the launchers after the first having no Install button.
- Health: App errors are under History; Free up RAM sits at the top of System load. All settings: each group is a box of its own.
- Apps marked 🔒 Must keep no longer offer Stop / Clear data / Turn off / Remove (in bulk too), and the mark now covers the brands' own apps the TV needs (remote, inputs…). App details: a permission's Allow / No is no longer cut off on a Mac; permissions sharing a name (Location, Nearby devices) show their own name under it.
- 🏠 TV Home: this project's own launcher, first in the Launcher gallery (one tap to install and use; if it fails the TV goes back on its own). A clock, a greeting, Inputs / Network / Settings buttons, a row of favourite apps and the app grid; hold OK on an app to pin, move or hide it. No ads. Set up from the phone or computer (TV tab → 🏠 → Customize TV Home): favourites, hidden apps, the background (built-in or your own photo), a 12/24-hour clock; the TV changes at once. An Update TV Home button shows when there is a new one
- A launcher installed from the gallery is compiled at install: it opens faster the first time on a slow TV
- The phone app's APK is about 1 MB smaller (no build for 32-bit x86 chips)
- A shorter Open on the TV menu: Open an installed app (an app picker with a search, instead of every app listed), Get apps (Play Store and suggested apps), Open a link, Send to the TV (photos, videos, music, clipboard)
- Daily app limits: a short button (+30 min), a shorter used line (25m / 3h30 (+2h30)), and Reset to take back today's extra time (needs the new TV Setup)
- A shorter Sleep timer menu: 30 minutes, 1 hour, 2 hours; Other times (15 min, 45 min, 1h30, custom); TV screen (screen off with sound, dim); Bedtime
- Screen off, keep listening: the first key on the TV's remote only turns the screen back on; it no longer also opens the app in focus (needs the new TV Setup)
- A menu entry that leads to another tab (Bedtime, Edit scenes) scrolls to its card and lights it up

## 0.12.0 - 2026-10-19

Web 0.12.0 · app điện thoại 0.12.0 · app TV 0.12.0

**Tiếng Việt**

- Tab Sức khỏe nhanh hơn: mỗi lần làm mới ~0.4 giây thay vì hơn 1 giây (CPU đo từ lần đọc trước, không ngồi chờ); mục Ngủ và thức đọc nhanh gấp ~3 lần
- Nút Hỏi AI: chỉ một vòng xoay (trước bị hai), và lấy danh sách nhanh hơn nhiều (RAM đọc bằng `meminfo --oom`)
- Tab Ứng dụng có chip **Đang mở**: các app đã mở trên TV (như màn đa nhiệm của Android), mới nhất trước, kèm RAM; ▶ quay lại app, ✕ đóng hẳn (khỏi danh sách và dừng), Đóng tất cả; app TV cần giữ (launcher, bàn phím, TV Setup, Tailscale, app hệ thống) có nhãn Cần giữ cạnh tên, không có ✕. App chạy nền nằm bên dưới; mục "App đang chạy" ở tab Sức khỏe chuyển về đây. Đọc RAM nhanh hơn ~3 lần
- Chọn DNS riêng: danh sách dễ đọc (tên đậm, nhãn Chặn quảng cáo / Gia đình, tên máy chủ và IP dòng dưới) thay cho danh sách hệ thống dồn mọi thứ một dòng; chạm là điền
- TV đã lưu trên màn hẹp: chữ chiếm hết bề ngang, các nút xuống một hàng bên dưới (Mở rộng ra, có chữ)
- Nút "Xem màn hình" ở danh sách TV chỉ còn icon 👁 (bật thì nền xanh), lời giải thích nằm trong tooltip; trên app điện thoại lẫn trang web
- App máy tính: "Nhiều TV cùng lúc" thành thẻ riêng; các thao tác (Cài APK, Gỡ app, Màn hình chính, Màn hình chào, Khởi động lại) là nút hiện sẵn, không giấu sau ⋯; TV chọn có nền xanh nhạt, "Đã chọn 2/3 TV", Chọn tất cả / Bỏ chọn
- Nhiều TV → Màn hình chính: chọn launcher bằng thẻ như Thư viện launcher (ảnh, mô tả, ghi chú, link Mã nguồn / Trang CH Play mở được), trên trang web lẫn app điện thoại
- Nút đang làm việc (Quét Wi-Fi, Kết nối lại, lấy danh sách app...) hiện vòng xoay thay cho icon, trên cả app điện thoại lẫn trang web
- Màn điều khiển: phần bàn phím không còn tự hiện khi vừa vào (app điện thoại) và không tự mở lại lần sau; chỉ mở khi bấm ⌨️
- Gỡ app trên nhiều TV: mỗi app một dòng có hình, tên và "trên 3/5 TV", chạm là chọn (nền xanh nhạt), ô tìm khi danh sách dài, Chọn tất cả / Bỏ chọn, nút "Gỡ (n)"; mỗi TV báo "đã gỡ n app". Trang web, app máy tính và app điện thoại giống nhau.
- Danh sách TV thấy tình trạng từng TV ngay, không cần kết nối: bật / chờ / không thấy, kiểu máy và Android, app đang mở (TV cần ADB còn có thời gian chạy và RAM trống); TV chỉ có điều khiển hoặc LG / Samsung / Roku thì chỉ hiện điều giao thức cho biết, không báo lỗi. Làm mới nền mỗi 10 giây, dừng khi không xem. Nút "Xem màn hình" (mặc định tắt) hiện ảnh nhỏ của từng TV đang bật mỗi 5 giây; bấm vào TV để mở như cũ. App máy tính và app điện thoại.
- TV Android 11+ chỉ có Gỡ lỗi không dây (không có cổng 5555): app điện thoại và máy tính tìm thấy TV qua mDNS thay vì báo "không thấy TV", chẩn đoán nói rõ "TV này chỉ có Gỡ lỗi không dây" và mở thẳng màn ghép nối bằng mã 6 số; cổng đổi mỗi lần bật thì tìm lại.
- TCL (bản Trung Quốc) không có Tùy chọn nhà phát triển: hướng dẫn bật ADB và chẩn đoán thêm cách mở công tắc ẩn (Cài đặt → Giới thiệu, bấm Lên, Xuống, Trái, Phải, phải làm lại mỗi lần).
- Firmware chặn `pm install` ("install apk has be disabled from pm"): cài TV Setup và cập nhật thử lại bằng phiên PackageInstaller (install-create, install-write, install-commit); chỉ báo lỗi khi cả hai cách đều hỏng.
- README: bảng TV đã thử (theo chủ TV báo qua cleanyourtv, và TV ta tự thử).
- Mở màn Quản lý nhanh hơn: không còn chờ 3–4 giây ở "Đang kết nối ADB…" (trang hỏi lại ngay khi ADB vừa kết nối, thay vì đợi lượt 4 giây); app điện thoại còn kết nối ADB sẵn cho Quản lý ngay khi remote đã chạy. App máy tính: "· cần ADB" ẩn khi đã có ADB.
- App điện thoại: sang app khác rồi quay lại trong 15 giây thì hình hiện lại ngay, không đen 1–2 giây (như trang web); dòng "Đang mở …" không còn tạm mất lúc vừa quay lại.
- Hình mượt hơn ở chế độ Tự động: 20 hình/giây ở 720p và 960p (trước là 10), 15 ở 480p trở xuống (trước là 8). TV dùng bộ mã hóa phần mềm thì vẫn 10 để TV không bị nặng.
- App điện thoại: lúc xem hình, phím đi chung phiên của hình; sau 1 phút phiên riêng của phím nghỉ, TV đỡ khoảng 47 MB RAM. Tắt hình là phiên phím chạy lại ngay (dưới nửa giây).
- Tối ưu TV đổi được màn hình chính trên Google TV (trước chỉ Android TV): tắt trình cài đặt ban đầu trước (để nút Home không rơi vào nó, làm TV khởi động lại liên tục), rồi kiểm tra nút Home; TV không cho đổi thì bật lại màn hình chính cũ. Hoàn tác không còn bật lại app vốn tắt sẵn từ nhà máy. Không bao giờ tắt màn hình chính hay bàn phím đang dùng; bàn phím đang chọn không có trên TV thì chuyển sang Gboard.
- Tối ưu TV gợi ý thêm app thu thập dữ liệu, quảng cáo, demo và tự cài app của Xiaomi, TCL, Hisense, Sony, Fire TV (theo báo cáo của người dùng cleanyourtv), và không bao giờ tắt những app mà các TV đó cần (remote, nguồn vào HDMI, Wi-Fi, kho app).
- Ảnh chụp màn hình không còn hỏng trên một số TV Xiaomi (chip MediaTek in thêm một dòng chữ trước ảnh).
- Màn hình chào: TV hiện video hoặc ảnh chào khách mỗi lần bật (hoặc chỉ sau khi khởi động lại), bấm phím bất kỳ để tắt, hết thì về màn hình chính; không hiện lại trong 2 phút. Đặt ở tab Tự động (tải lên có tiến độ, xem thử, giây hiện ảnh, Xóa), và cho nhiều TV cùng lúc ở "Nhiều TV cùng lúc → ⋯ → Đặt màn hình chào" (web và app điện thoại: tải tệp một lần, đẩy sang từng TV, mỗi TV một dòng kết quả; TV lỗi không chặn TV khác). Cần TV Setup 0.12.0 trở lên
- Thư viện màn hình chính (launcher): danh sách ký số nằm trên GitHub, mỗi mục có tên, mô tả, ảnh, mã nguồn, TV hợp. Chọn một cái, app tải về (kiểm SHA-256), cài, đặt làm màn hình chính, rồi hỏi "Giữ không?" 30 giây: không trả lời thì TV tự về launcher cũ. TV Setup trông chừng sau đó: launcher lỗi hai lần trong 5 phút hoặc nút Home không mở được thì tự trả về. Có ở app điện thoại, app máy tính, trang web của TV và màn hình TV Setup; cài một lúc nhiều TV ở "Nhiều TV cùng lúc" (mỗi TV tự kiểm tra và tự trả về).
- Thư viện màn hình chính: thêm launcher của CH Play, mỗi cái có ảnh lấy từ trang CH Play của nhà phát triển: AT4K Launcher, Monet Launcher, dream Launcher, ATV Launcher Pro (cùng Projectivy). Thẻ có nhãn CH Play và nút **Mở trong CH Play** (mở CH Play ngay trên TV); cài xong thì nút thành **Dùng**, đi qua cùng cách đổi an toàn (hỏi Giữ, TV tự quay lại). Nhiều TV cùng lúc: launcher CH Play dùng được trên TV đã cài sẵn, TV chưa cài sẽ báo lỗi bảo cài từ CH Play.

**English**

- The Health tab is faster: each refresh ~0.4 s instead of over 1 s (CPU counted from the reading before, no wait); Sleep and wake reads ~3 times faster
- Ask AI: one spinner (there were two), and the list comes much faster (memory read with `meminfo --oom`)
- The Apps tab has an **Open now** chip: the apps opened on the TV (like Android's recents screen), newest first, with their memory; ▶ goes back to one, ✕ closes it for good (off the list and stopped), Close all; what the TV needs (home screen, keyboard, TV Setup, Tailscale, system apps) has Must keep beside its name, no ✕. Background apps are under them; Health's "Running apps" moved here. Memory is read ~3 times faster
- Choosing your own DNS: a readable list (name in bold, an Ad blocking / Family tag, the server name and IP under it) instead of the system list with everything on one line; tap to fill
- Saved TVs on a narrow screen: the words take the whole width, the buttons go to a row under them (Open wider, with its word)
- The "Show screens" button on the TV list is only its 👁 (blue tint while on), the explanation in its tooltip; in the Phone app and on the page
- Computer app: "Several TVs at once" is a card of its own; its acts (Install APK, Remove apps, Home screen, Welcome screen, Restart) are buttons in sight, not behind ⋯; picked TVs get the light blue tint, "2/3 TVs picked", Select all / Clear
- Several TVs → Home screen: the launcher is chosen from cards as in the launcher gallery (picture, words, notes, a Source / Play page link that opens), on the page and in the Phone app
- A button at work (Scan the Wi-Fi, Retry the connection, getting the app list...) shows a turning ring in its icon's place, in the Phone app and on the page
- Remote screen: the keyboard part no longer shows on its own when the screen opens (Phone app) or reopens next time; only ⌨️ opens it
- Removing apps on several TVs: one row per app with its picture, name and "on 3/5 TVs", tap to pick (light blue tint), a search when the list is long, Select all / Clear, a "Remove (n)" button; each TV reports "removed n apps". The same on the web page, the Computer app and the Phone app.
- The TV list shows each TV's state without connecting: on / standby / no answer, model and Android, the app open (a TV with ADB also its uptime and free memory); keys-only, LG, Samsung and Roku TVs show only what their protocol tells, never an error. Refreshed in the background every 10 s, paused when not looked at. A "Show screens" button (off by default) shows a small picture of each TV that is on every 5 s; tap a TV to open it as before. Computer app and phone app.
- Android 11+ TVs with only Wireless debugging (no port 5555): the phone and computer apps find the TV by mDNS instead of saying "no TV found", the self-check says "This TV only has Wireless debugging" and opens the 6-digit pairing screen; the port changes on every switch-on, so it is looked up again.
- TCL (China) with no Developer options: the ADB steps and the self-check now say how to open the hidden switch (Settings → About, press Up, Down, Left, Right, needed again each time).
- Firmware that blocks `pm install` ("install apk has be disabled from pm"): installing and updating TV Setup retries through a PackageInstaller session (install-create, install-write, install-commit); an error only when both ways fail.
- README: a Tested TVs table (reported by owners through cleanyourtv, and the TVs we tried ourselves).
- Manage opens faster: no more 3–4 s on "Connecting ADB…" (the page asks again as soon as ADB is up instead of at the next 4 s tick), and the phone app dials Manage's ADB ahead once the remote runs. Computer app: "· needs ADB" is hidden once ADB is there.
- Phone app: back from another app within 15 s, the picture is there at once instead of black for 1–2 s (as on the web page); the "Now open …" line no longer goes missing for a moment on return.
- Smoother picture on Auto: 20 frames a second at 720p and 960p (was 10), 15 at 480p and below (was 8). A TV on its software encoder stays at 10, so it is not slowed down.
- Phone app: while the picture shows, keys ride on the picture's session, and after a minute the keys' own one rests, sparing the TV about 47 MB of memory. Hide the picture and the keys' session is back at once (under half a second).
- Optimize TV swaps the home screen on Google TV too (Android TV only before): the setup wizard goes off first (Home landing on it made the TV restart over and over), then Home is checked, and the old home screen comes back on if the TV will not allow it. Undo no longer turns on apps that were off from the factory. The home screen and keyboard in use are never turned off; a chosen keyboard missing from the TV is replaced by Gboard.
- Optimize TV suggests more apps to turn off: data collection, ads, demos and auto-installers of Xiaomi, TCL, Hisense, Sony and Fire TV (from what cleanyourtv's users reported), and never turns off what those TVs need (the remote, HDMI inputs, Wi-Fi, the app store).
- Screenshots no longer come out broken on some Xiaomi TVs (MediaTek sets print a line before the picture).
- Welcome screen: the TV shows a greeting video or picture each time it is turned on (or only after a restart); any key closes it, and when it ends the TV goes to its home screen; not shown again within 2 minutes. Set it in the Automation tab (upload with progress, preview, picture seconds, Remove), and for several TVs at once under "Several TVs at once → ⋯ → Set welcome screen" (web page and phone app: the file is uploaded once and pushed to each TV, one result line per TV; a TV that fails does not stop the others). Needs TV Setup 0.12.0 or later
- Launcher gallery: a signed list on GitHub, each entry with name, description, picture, source and the TVs it fits. Pick one: the app downloads it (SHA-256 checked), installs it, makes it the home and asks "Keep it?" for 30 seconds: no answer and the TV goes back to its old launcher. TV Setup then watches it: two crashes in 5 minutes, or a Home button that cannot open it, and it is put back. In the Phone app, the Computer app, the TV web page and TV Setup's own screen; on several TVs at once under "Several TVs" (each TV checks and rolls back on its own).
- Launcher gallery: more Google Play launchers, each with a picture taken from the developer's own Play listing: AT4K Launcher, Monet Launcher, dream Launcher, ATV Launcher Pro (beside Projectivy). The card has a Google Play tag and an **Open in Google Play** button (opens Play on the TV itself); once installed the button becomes **Use**, through the same safe swap (Keep question, the TV goes back by itself). Several TVs at once: a Play launcher is used on the TVs that already have it; a TV without it reports an error telling to install it from Google Play.

## 0.11.0 - 2026-10-19

Web 0.11.0 · app điện thoại 0.11.0 · app TV 0.11.0

**Tiếng Việt**

- Nhiều máy cùng xem / nghe một TV: góc hình hiện **👁 2** (và 🎧) khi có máy khác cũng đang xem, trên web và app điện thoại. Các trình duyệt mở cùng app máy tính hoặc cùng trang web của TV giờ dùng chung một luồng hình và một luồng tiếng (một scrcpy-server trên TV thay vì mỗi máy một cái, đỡ ~50 MB RAM TV mỗi máy). Trước đây trình duyệt thứ hai giành mất hình của trình duyệt đầu, trình duyệt đầu báo "Không lấy được hình từ TV".
- TV chỉ gửi được một luồng hình mà máy khác đang xem: báo "TV đang gửi hình cho máy khác" thay vì "TV không gửi hình".
- Nghe 🎧 trên trang web của TV: khi TV im (menu, phim tạm dừng) không gửi gì nữa, thay vì 47 KB/s (192 KB/s với HQ) toàn tiếng im; có tiếng lại thì nghe ngay, không thêm độ trễ. Đỡ hẳn khi nghe qua Tailscale hay 4G.
- Trang web của TV: lúc đang xem hình, phím đi qua luồng hình, nên TV bớt một tiến trình (~50 MB RAM) trong lúc xem. Phím không chậm hơn (đo trên emulator).

**English**

- Several devices on one TV: the picture shows **👁 2** (and 🎧) when another device watches too, on the web and in the phone app. Browsers on the same computer app or the same TV web page now share one picture stream and one sound stream (one scrcpy-server on the TV instead of one each, ~50 MB of the TV's memory saved per device). Before, a second browser took the picture from the first, which then said it could not get the picture.
- A TV that can send only one picture, already watched by another device, now says "The TV is sending its picture to another device" instead of "no picture".
- 🎧 on the TV's web page: nothing is sent while the TV is silent (a menu, a paused film) instead of 47 KB/s (192 KB/s with HQ) of silence; sound comes back at once, with no added delay. Much lighter over Tailscale or 4G.
- The TV's web page: while the picture is on, keys go through the picture's session, so the TV runs one process less (~50 MB of memory) meanwhile. Keys are no slower (measured on the emulator).

## 0.10.0 - 2026-10-19

Web 0.10.0 · app điện thoại 0.10.0 · app TV 0.10.0

**Tiếng Việt**

- App máy tính: nút **Nói** dùng được micro của máy. Trên Mac, máy đổi lời nói thành chữ (Mac hỏi quyền micro một lần). Trên Windows, Linux, giọng bạn đi thẳng tới Trợ lý của TV như micro của remote thật (TV Android, qua giao thức remote, không cần ADB). Tự dừng khi bạn nói xong.
- Phím ⌨️ Bàn phím: hàng trên cùng của remote web (chỗ nút Đánh thức cũ) mở ô gõ chữ và ô "Hỏi TV" ngay bên dưới, như "Thêm" (mỗi lúc chỉ mở một trong hai); trên app điện thoại nó ở trong "Thêm". Bỏ thẻ Cảnh dưới remote: cảnh chạy từ "Thêm" → Cảnh. "Thêm" xếp lại theo mức dùng: Mở trên TV, Ngõ vào, Phụ đề, Hẹn giờ tắt lên đầu. Đánh thức (app máy tính) chuyển ra danh sách TV đã lưu: TV ngủ sâu thì không mở được remote, nên đánh thức ở đó rồi mở luôn. Ô "Hỏi TV" dùng được cả khi chưa có ADB (app máy tính tìm YouTube qua giao thức remote); bỏ nút 🎤 trùng với nút Nói.
- App điện thoại giống web: hàng trên Nguồn · Nói · Bàn phím · Thêm; Bàn phím mở ô gõ chữ và ô "Hỏi TV" ngay bên dưới (mỗi lúc một panel); Nói đi thẳng vào nhận giọng nói (không còn hộp "Nói hoặc gõ"); nút Quản lý lên thanh trên cùng, cạnh nút xem.
- Sửa: giới hạn âm lượng làm TV câm khi đang nghe 🎧 trên TV Android 12 trở xuống (loa TV tắt, phím âm lượng chỉnh luồng nghe nên bị chặn). Giới hạn giờ chỉ áp cho loa TV.
- Nói trên web: một hộp "Đang nghe" hiện chữ nghe được và thanh âm lượng nhảy theo micro (biết ngay micro có thu không), nút Xong để gửi ngay, Hủy (hoặc Esc) để bỏ.
- Nói trên app máy tính (Mac): nếu macOS từ chối nhận dạng giọng nói ("service-not-allowed"), giọng nói được gửi thẳng tới Trợ lý của TV thay vì báo lỗi.
- Tên app trong "Đang mở" (và tên bài, tên cảnh) không còn chạy chữ khi đã đủ chỗ: chỉ chạy khi chữ bị cắt thật.
- Giới thiệu (web và app điện thoại) có link tới trang hướng dẫn tvremote.pages.dev.
- Chia sẻ TV: đổi "Người quét được làm gì" giờ nhanh hơn (một lệnh tới TV thay vì ba), mã QR cũ mờ đi trong lúc chờ mã mới.
- Nút **Chụp màn hình** (trong Thêm) quay vòng tới khi ảnh đã lưu, không còn trông như bấm không ăn.
- Chữ của trang giờ nằm theo mã trong `i18n/vi.json`, `i18n/en.json` (mỗi ngôn ngữ một file), để sau này thêm được ngôn ngữ mới. Gần như mọi chữ đã chuyển, cả chữ do script viết, và các app (điện thoại, TV Setup, máy tính) đọc cùng các file đó; báo lỗi từ server theo ngôn ngữ trang đang dùng. Các phím của remote cũng theo mã; bảng dịch cũ (dịch câu tiếng Việt trên trang đã hiện) đã bỏ hẳn, sửa luôn “· 1 TVs”.
- Thanh chờ (cài app, gửi file) chạy mượt hơn trên TV yếu: không bắt trang tính lại bố cục mỗi khung hình.
- App máy tính trên Mac: chữ chạy (tên bài, app đang mở, tên cảnh) lại chạy khi quay về tab Remote, không cần rê chuột.
- Nút đang bật / đang chọn (tab, Nghe, HQ, lựa chọn trong Thiết lập) giờ là nền nhạt viền xanh; màu xanh đặc chỉ còn cho nút bấm để làm (OK, Đặt, Cài, Thêm), nên một màn hình không còn đầy nút xanh khó phân biệt. Trên web và app điện thoại.
- App điện thoại: dòng trên cùng của remote hiện lại tên TV (chỉ còn "đang tắt hình", "chạm hình để bấm"...).
- Tất cả cài đặt: Gỡ lỗi ADB và Gỡ lỗi không dây bật lên được, có hỏi lại trước (không tắt được, để không mất kết nối); TV tự tắt lại (Gỡ lỗi không dây cần Wi‑Fi) thì app nói rõ. Độ sáng là thanh kéo. Âm lượng phim, nhạc và âm lượng hệ thống hiện mức thật của TV và mức tối đa (ví dụ 9 / 15), gõ số hoặc bấm − / +, đổi qua lệnh âm lượng thật (TV không nhận lệnh thì bấm phím âm lượng). Thêm tên và nhóm mới “HDMI & loa ngoài” (HDMI‑CEC, eARC), bấm giữ, tốc độ con trỏ, thông báo nổi, VPN luôn bật; cờ nhà máy và công tắc riêng của TCL chỉ xem.
- TCL (Android 11): TV Setup bị tắt rồi sập mỗi lần app điện thoại / máy tính mở lại nó (TCL chặn chạy nền dạng foreground, Android giết app sau 10 giây). Giờ app mở TV Setup bằng đường thường và đưa nó vào danh sách không bị tiết kiệm pin, nên không sập và ít bị TV tắt hơn.
- Màn remote: “Thêm phím” và bàn số mở / đóng trượt mượt, phần bên dưới trượt theo thay vì nhảy (web và app điện thoại). TV Setup: mỗi dòng trong ô Hoạt động có giờ.
- 🎧 Nút 🔊 cạnh HQ: thanh kéo ×0.5 … ×10 cho tiếng TV to hơn trên điện thoại / máy tính, có bộ giới hạn để không rè.
- Nhẹ cho TV hơn:
  - App điện thoại: Quản lý, Cài đặt hay Giới thiệu che remote thì TV ngừng mã hóa hình, ngừng hỏi app đang mở / đang phát. Đóng Quản lý thì trang của nó cũng ngừng hỏi TV (Sức khỏe hỏi mỗi 5 giây kể cả khi đã đóng).
  - Trang web: Sức khỏe 10 giây/lần, chế độ Ảnh chụp 1 ảnh mỗi ~2 giây, "Cho phép gỡ lỗi USB?" 20 giây/lần, đang phát 3 giây/lần khi không phát gì; app máy tính không đọc app đang mở qua ADB khi TV đã tự báo.
  - TV Setup: ADB lỗi thì thử lại giãn dần tới 5 phút một lần, thay vì mở phiên mới mỗi 5 giây mãi; có người bấm thì thử ngay.
- Chữ chạy (đang phát, tên app…) hết khựng, nhất là trên app máy tính cho Mac.
- Nút Nói trên app máy tính và trang web của TV làm như app điện thoại: nói (trình duyệt tự nhận giọng) hoặc gõ vào ô Hỏi TV; giữ nút để mở Trợ lý của TV.
- Chọn bộ mã hóa Phần cứng / Phần mềm mà 10 giây chưa thấy hình: app hỏi có chuyển về Tự chọn không.
- 🎧 Nghe tiếng TV hết bị nhanh và giật trên TV gửi tiếng từng đợt (TCL): app không bỏ lẻ từng mẩu tiếng suốt vài giây nữa mà nhảy về gần hiện tại một lần; sau mỗi lần đứt tiếng thì giữ sẵn nhiều tiếng hơn (cả trên trang web).
- Chữ một dòng không đủ chỗ (tên bài và kênh đang phát, giá trị cài đặt, tên gói app, tên cảnh; trên app điện thoại: tên TV, tên bài và kênh, tên app đang mở) tự chạy qua lại thay vì bị “…”. Máy bật giảm chuyển động thì vẫn là “…”.
- Hàng tab và hàng tab con (Sức khỏe: Hiện tại … Khả năng) không còn đẩy chữ ra ngoài khi màn hình hẹp hoặc chữ to: hàng cuộn ngang, cạnh còn nội dung có lớp mờ, tab đang chọn tự cuộn vào giữa. Tab “Tệp · Cài” đổi tên thành “Tệp”.
- Sang app khác rồi quay lại không còn mất hình: chỉ dừng sau 15 giây bị che, và tự xem lại khi quay về. Trình giải mã không ra hình sau 4 giây thì tự chuyển cách khác, có ghi vào nhật ký.
- 🎧 Nút **Đều**: tiếng trễ khoảng nửa giây nhưng không giật. Không bật thì app vẫn tự học: nhớ mức đệm của từng TV, lần sau mở là êm ngay, không phải giật vài lần mới học lại.
- Chữ chạy (tên bài, tên gói, giá trị cài đặt) giờ chạy vòng một chiều như app điện thoại, mượt hơn, và trên app Mac hết bị cắt, đứng hình hay mất chữ (tên một Cảnh dài làm trang tự vẽ lại 20 lần mỗi giây).

**English**

- Computer app: the **Speak** key uses the computer's microphone. On a Mac the computer turns speech into words (the Mac asks for the microphone once). On Windows and Linux your voice goes straight to the TV's Assistant, like a real remote's microphone (Android TVs, over the remote protocol, no ADB needed). It stops when you finish speaking.
- A ⌨️ Keyboard key: on the web remote's top row (where Wake was) it opens the typing box and the "Ask the TV" box right below, like More (one of the two open at a time); on the phone app it is in More keys. The Scenes card under the remote is gone: scenes run from More → Scenes. More keys sorted by use: Open on the TV, Input, Subtitles, Sleep timer first. Wake (computer app) moved to the saved TVs list: a TV in deep standby cannot open its remote, so it is woken there and then opened. The "Ask the TV" box works without ADB too (the computer app searches YouTube through the remote protocol); the 🎤 that repeated Speak is gone.
- The phone app matches the web: top row Power · Speak · Keyboard · More; Keyboard opens the typing box and the "Ask the TV" box right below (one panel at a time); Speak goes straight to speech (no "Speak or type" box); Manage moves to the top bar, beside the picture key.
- Fix: a volume limit left the TV silent while 🎧 listening on Android 12 or older (the speakers are off then and the volume keys move the listening stream, which the limit held down). The limit now applies to the TV's speakers only.
- Speak on the web: a "Listening" box shows the words heard and bars that move with the microphone (a dead mic shows at once), Done to send now, Cancel (or Esc) to drop it.
- Speak in the computer app (Mac): when macOS refuses speech recognition ("service-not-allowed"), the voice goes to the TV's Assistant instead of an error.
- The app's name in "Now open" (and song and scene names) no longer slides when it fits: only when the words are really cut.
- About (the page and the phone app) links to the guide at tvremote.pages.dev.
- Share the TV: changing "What the scanner may do" is faster (one call to the TV instead of three), and the old QR fades while the new one comes.
- The **Screenshot** key (under More) spins until the picture is saved, so the tap no longer looks lost.
- The page's words now live by id in `i18n/vi.json`, `i18n/en.json` (one file per language), so new languages can be added later. Nearly every word has moved, the scripts' too, and the apps (phone, TV Setup, computer) read the same files; the server's messages follow the page's language. The remote's keys go by id too; the old table (translating Vietnamese sentences on the drawn page) is gone, which also fixes “· 1 TVs”.
- The waiting bar (installing, sending files) runs smoother on slow TVs: it no longer makes the page lay itself out each frame.
- Computer app on Mac: running lines (song, open app, scene names) move again after coming back to the Remote tab, without moving the mouse.
- A button that is on or chosen (a tab, Listen, HQ, a choice in Setup) is now a light tint with a blue edge; the solid blue is kept for buttons that do something (OK, Set, Install, Add), so a screen is no longer full of look-alike blue buttons. On the web and in the phone app.
- Phone app: the remote's top line shows the TV's name again (it showed only "picture off", "touch the picture to tap"...).
- All settings: ADB and Wireless debugging can be turned on after a question (never off, so the remotes are not cut off); when the TV turns it straight back off (Wireless debugging needs Wi‑Fi) the app says so. Brightness is a slider. Media and system volume show the TV's live level and its top (such as 9 / 15), typed or with − / +, set through the real volume command (the volume keys where the TV ignores it). New names and a new group “HDMI & audio out” (HDMI‑CEC, eARC), long‑press delay, pointer speed, pop‑up notifications, always‑on VPN; factory flags and TCL's own switches are view only.
- TCL (Android 11): TV Setup was stopped, then crashed each time the phone or computer app started it again (TCL refuses a foreground service, Android kills the app 10 s later). The apps now start it the plain way and put it on the battery allow list: no crash, and the TV stops it less.
- Remote: “More keys” and the number pad slide open and shut, what is below follows instead of jumping (web and phone app). TV Setup: each line in Activity has its time.
- 🎧 A 🔊 button next to HQ: a ×0.5 … ×10 slider for the TV's sound on the phone / computer, with a limiter so it does not crack.
- Lighter on the TV:
  - Phone app: with Manage, Settings or About over the remote, the TV stops encoding the picture and is no longer asked what is open / playing. Closing Manage also stops its page asking the TV (Health asked every 5 s even when closed).
  - Web page: Health every 10 s, Screenshots one about every 2 s, "Allow USB debugging?" every 20 s, now playing every 3 s when nothing plays; the computer app no longer reads the open app over ADB when the TV already says it.
  - TV Setup: with ADB failing it retries less and less often, up to every 5 min, instead of a new session every 5 s forever; a key press tries at once.
- Running lines of text (now playing, app names…) no longer hitch, above all in the Mac computer app.
- The Speak button on the computer app and the TV's web page works as on the phone app: speak (the browser hears it) or type in the Ask the TV box; hold it to open the TV's Assistant.
- With the Hardware / Software encoder picked by hand and no picture after 10 seconds, the app offers to go back to Auto.
- 🎧 Listening no longer sounds fast and stuttering on a TV that sends its sound in bursts (a TCL): instead of dropping a scrap every half second for seconds, it skips back near live once; after each break it keeps more sound queued (the web page too).
- A line of text with no room (now playing title and channel, setting values, package names, scene names; in the phone app: the TV name, the title and channel, the open app) slides to its end and back instead of ending in “…”; with reduced motion it stays “…”.
- The tab bar and the sub‑tabs (Health: Now … Capabilities) no longer push words out on a narrow screen or with large text: the row scrolls sideways, a fade shows the side with more, the selected tab scrolls into the middle. The “Files · Install” tab is now “Files”.
- Switching to another app and back no longer loses the picture: it stops only after 15 s out of sight and starts again on return. A decoder that gives no frame in 4 s is swapped for the other one, and logged.
- 🎧 **Even** button: the sound plays about half a second late, never stutters. Without it the app still learns: it remembers how much to keep queued for each TV, so the next time is smooth from the start instead of stuttering a few times first.
- Running lines (song titles, package names, setting values) now loop one way as in the phone app, smoother, and on the Mac app no longer cut off, frozen or blank (a long Scene name made the page redraw itself 20 times a second).

## 0.9.0 - 2026-10-18

Web 0.9.0 · app điện thoại 0.9.0 · app TV 0.9.0

**Tiếng Việt**

- Xem màn hình TV: sau khi trang tự hạ cỡ hình vì mạng chậm, mạng ổn lại khoảng 30 giây thì cỡ hình tự tăng lại từng bậc, không vượt cỡ người dùng đã chọn. Vừa tăng mà lại chậm thì lần chờ sau lâu gấp đôi (không nhấp nhô).
- Home Assistant và Phím tắt (Siri) điều khiển TV trong mạng nhà: 🛠️ Cài đặt → “Home Assistant · Phím tắt” (web của TV và app máy tính). Đường dẫn `/api/hook?k=<mã>&key=HOME` (cũng `scene=`, `app=`, `text=`, `volume=`, `power=`) và `/api/hook/state`. Mã riêng, không phải mã ghép, tắt sẵn, đổi mã mới được, sai 5 lần bị chặn 1 phút. App máy tính chỉ mở cổng thêm (cổng app + 1) khi đang bật.
- Tay cầm chơi game (Bluetooth / USB) nối vào máy tính, điện thoại hay trình duyệt điều khiển được TV, không cần cài đặt: D-pad / cần trái = mũi tên (giữ thì lặp), A = OK, B = Quay lại, Start = Home, Select = Menu, X = Phát / Tạm dừng, Y = Tắt tiếng, LB / RB = âm lượng, LT / RT = kênh. Đang xem hình TV thì các nút gửi đi là nút tay cầm thật (BUTTON_A…), game trên TV nhận như tay cầm. Đang gõ chữ hay mở hộp thoại thì tay cầm không can thiệp. App điện thoại: màn điều khiển cũng nhận tay cầm.
- Không kết nối được TV: app tự kiểm tra nhanh (dưới 4 giây) rồi đưa ra MỘT kết luận kèm cách sửa, thay vì báo lỗi thô. Kiểm tra: máy này có Wi-Fi và cùng mạng với TV không, TV trả lời ở cổng nào, VPN, ADB đang chờ “Cho phép”, ngày giờ TV sai. Các kết luận: TV tắt hoặc ngủ, TV đã đổi địa chỉ (tìm lại theo tên, bấm một nút để dùng địa chỉ mới), “Gỡ lỗi ADB” đang tắt (phím vẫn bấm được), VPN chặn đường, TV đang chờ bạn bấm Cho phép. Có ở App máy tính, màn Quản lý trên điện thoại và màn chọn TV của app điện thoại.
- Hình TV trên app điện thoại ít trễ hơn: bộ giải mã chạy chế độ độ trễ thấp (ưu tiên thời gian thực), vẽ khung ngay khi giải xong và bỏ qua khung cũ khi đã có khung mới ngay sau. Máy chậm quá 0,6 giây thì tạo lại bộ giải mã, bỏ đến khung chính kế tiếp và xin TV gửi ngay một khung (như trên trình duyệt). Đo: `adb shell setprop log.tag.TvrPic DEBUG`, xem logcat thẻ `TvrPic`.
- Mở hình và tiếng TV nhanh hơn: các lệnh khởi động (tên máy, bản Android, địa chỉ mạng, kiểm tra file scrcpy-server) gộp thành một lượt hỏi TV, chờ cổng scrcpy mở thì hỏi dày hơn (25 ms thay vì 100 ms). File scrcpy-server trên TV được so bằng MD5 (trước chỉ so dung lượng, file đổi mà cùng dung lượng thì không được chép lại).
- Sức khỏe → Lịch sử trên TV không cho ADB đọc thư mục của app (như TCL Android 11): trước luôn báo “Chưa có dữ liệu”, giờ TV Setup tự đưa số liệu ra (cần TV Setup mới; TV Setup cũ thì hiện nút Cập nhật). Cơ chế chung cho mọi file TV Setup ghi.
- Đang phát: tên bài dài thì chữ tự chạy; không còn ghi mã app dưới tên kênh. App điện thoại: ra nền rồi mở lại, “Đang phát” và “Đang mở” hiện ngay thay vì biến mất vài giây.

**English**

- Watching the TV screen: after the page lowered the picture size on a slow network, it now steps back up one size at a time once the network has been fine for about 30 s, never above the size the person picked. If it falls behind again soon after a step up, the next wait is doubled (no flapping).
- Home Assistant and Shortcuts (Siri) can control the TV on the home network: 🛠️ Settings → “Home Assistant · Shortcuts” (TV web page and Computer app). `/api/hook?k=<token>&key=HOME` (also `scene=`, `app=`, `text=`, `volume=`, `power=`) and `/api/hook/state`. Its own token, not the pairing code; off by default; a new token can be made; five wrong tries block an address for a minute. The Computer app opens one extra port (the app's port + 1) only while it is on.
- A game controller (Bluetooth / USB) connected to the computer, phone or browser now drives the TV, with nothing to set up: D-pad / left stick = arrows (repeating when held), A = OK, B = Back, Start = Home, Select = Menu, X = Play / Pause, Y = Mute, LB / RB = volume, LT / RT = channel. While the TV picture is open the buttons go as real gamepad buttons (BUTTON_A...), so games on the TV see a gamepad. It stays out of the way while you type or a dialog is open. Phone app: the remote screen takes the controller too.
- When the TV cannot be reached, the app runs a quick self-check (under 4 seconds) and gives ONE conclusion with the fix instead of a raw error. It looks at: whether this device is on Wi-Fi and on the TV's network, which of the TV's ports answer, a VPN, ADB waiting for “Allow”, a wrong date on the TV. Conclusions: the TV is off or asleep, the TV has a new address (found again by name, one button to use it), “ADB debugging” is off (keys still work), a VPN is in the way, the TV is waiting for you to press Allow. In the Computer app, the phone's Manage screen and the phone app's TV list.
- The TV picture in the phone app lags less: the decoder runs in low-latency, real-time priority mode, draws a frame as soon as it is decoded and skips an old one when a newer one is right behind it. If the phone falls 0.6 s behind, the decoder is made again, frames are skipped up to the next key frame and the TV is asked for one (as for the browser). To measure: `adb shell setprop log.tag.TvrPic DEBUG`, then logcat tag `TvrPic`.
- Picture and sound open faster: the start-up commands (model, Android version, network address, the scrcpy-server check) are one round trip to the TV, and the wait for scrcpy's socket polls every 25 ms instead of 100 ms. The scrcpy-server on the TV is compared by MD5 (it was size only, so a changed file of the same size was never copied).
- Health → History on TVs that do not let ADB read an app's folder (a TCL on Android 11): it always said “No data yet”; now TV Setup hands the data over (needs the new TV Setup; an older one gets an Update button). One way for every file TV Setup keeps.
- Now playing: a long title slides; no package name under the channel. Phone app: back from the background, “Now playing” and “Now open” are there at once instead of vanishing for a few seconds.

## 0.8.0 - 2026-10-18

Web 0.8.0 · app điện thoại 0.8.0 · app TV 0.8.0

**Tiếng Việt**

- Xem màn hình TV qua mạng chậm (Wi-Fi yếu, Tailscale qua relay): hình không còn trễ dần cả chục giây rồi tắt. Khi trình duyệt không nhận kịp, các khung cũ bị bỏ, TV gửi ngay một khung mới, hình nhảy về lúc hiện tại. Bị chậm nhiều lần thì trang tự hạ cỡ hình một bậc (cả ba nơi).
- App máy tính hiện hình TV bằng WebCodecs: trễ ít hơn, luôn vẽ khung mới nhất.
- Chất lượng hình có thêm “Nhịp hình”: “Đều hơn” giữ mỗi khung 33 ms rồi hiện cách đều, ít giật hơn khi Wi-Fi chập chờn (mặc định “Nhanh nhất”; trên trình duyệt có WebCodecs: app máy tính, Chrome / Safari mới).
- Kết nối ADB đang chờ không còn bị tính là quá hạn vì máy tính hay điện thoại vừa ngủ dậy.
- App điện thoại: bật VPN vẫn điều khiển được TV ở nhà, app tự đi qua Wi-Fi tới địa chỉ trong nhà (Tailscale vẫn đi qua VPN). Nếu vẫn không tới được, app nhắc cho phép mạng nội bộ trong app VPN.
- Web của TV: lúc TV Setup chờ người bấm “Cho phép gỡ lỗi USB?” trên TV, trang vẫn trả lời (cả ghép mã); phần cần ADB báo ngay “nhìn lên TV, chọn Luôn cho phép” thay vì treo tới khi trang không mở được nữa. Tên app hiện đúng thay cho tên gói.
- Máy tính vừa ngủ dậy (gập máy) hay điện thoại vừa mở khóa: hình và tiếng TV đang xem tự mở lại thay vì báo “TV đã dừng gửi hình”; một lần hỏi TV không được ngay lúc thức dậy không còn hiện “Mất kết nối”. TV giữ phiên xem 60 giây (trước là 25) khi điện thoại bị treo ở nền (cả ba nơi).
- Điện thoại hay TV đời cũ chưa cập nhật “Android System WebView” (và trình duyệt quá cũ): màn Quản lý báo cần cập nhật, có nút mở Play Store, thay vì hiện các tab trống.

**English**

- Watching the TV's screen over a slow network (weak Wi-Fi, Tailscale through a relay): the picture no longer drifts tens of seconds behind and then stops. When the browser can't keep up, old frames are dropped, the TV sends a fresh one at once and the picture jumps to now; after several such jumps the page steps the picture size down one level (all three).
- The computer app shows the TV's picture through WebCodecs: less delay, always the newest frame.
- Picture quality has a new “Frame pace”: “Even” holds each frame 33 ms and shows them evenly spaced, stuttering less on a patchy Wi-Fi (default “Fastest”; in browsers with WebCodecs: the computer app, recent Chrome / Safari).
- An ADB connection being set up no longer times out because the computer or phone just woke from sleep.
- Phone app: with a VPN on, the TV at home is still reachable; the app goes over the Wi-Fi to home addresses (Tailscale still goes through the VPN). If it still can't reach the TV, it asks to allow the local network in the VPN app.
- TV web page: while TV Setup waits for someone to answer “Allow USB debugging?” on the TV, the page keeps answering (pairing too); the parts that need ADB say at once “look at the TV, choose Always allow” instead of hanging until the page no longer opens. App names show instead of package names.
- A computer that just woke (lid opened) or a phone just unlocked: the TV picture and sound being watched start again by themselves instead of “The TV stopped sending the picture”; one failed check right on waking no longer shows “Connection lost”. The TV keeps a viewing session 60 s (was 25) while the phone is frozen in the background (all three).
- Older phones and TVs whose “Android System WebView” was never updated (and browsers that are too old): the Manage screen says to update it, with a button to the Play Store, instead of showing empty tabs.

## 0.7.1 - 2026-10-17

Web 0.7.1 · app điện thoại 0.7.1 · app TV 0.7.1

**Tiếng Việt**

- Lúc app tự làm nhiều bước trên TV: cài TV Setup bằng nút remote ghi “Bước 1/3…” và nhắc đừng bấm remote của TV; ai lỡ bấm làm TV rời khỏi bước đang làm thì app dừng lại, báo và cho thử lại, không bấm tiếp vào chỗ sai (app máy tính, app điện thoại). Bật Tailscale qua ADB hiện thẻ “TV Remote đang làm việc trên TV” ở góc màn hình TV cho tới khi xong (cả ba nơi).
- App điện thoại: các hộp thoại là của app, cùng màu và kiểu nút với app (không còn hộp thoại của Android).
- Chất lượng hình gom vào một hộp: cỡ hình, khung/giây, bitrate, bộ mã hóa (cả ba nơi).
- Logo của app “Đang mở” giữ đúng hình, logo chữ nhật không còn bị cắt vuông.
- Thanh đang phát: video YouTube mới mở không còn nhảy lên khoảng 2:15 (YouTube trên Google TV gửi vị trí kèm giờ cũ). App điện thoại giữ dòng “Đang mở” cạnh thanh đang phát, như trên web.
- Tab Sức khỏe có mục “Giấc ngủ”: TV tắt / bật lúc nào và vì sao, tự tắt sau bao lâu, Tailscale và TV Setup còn chạy không và bị TV tắt lúc nào, lịch sử bật / tắt từng ngày (xem được các ngày cũ hơn); chi tiết kỹ thuật (thời gian chip ngủ sâu, số lần ngủ sâu của TV TCL…) gập lại bên dưới (cả ba nơi).
- TV Setup tự bật lại Tailscale khi TV tắt nó để lấy bộ nhớ (TV TCL tắt Tailscale cùng nhiều app khác khi thiếu RAM, trước đây phải về nhà mở lại). Tailscale người dùng tự tắt thì để yên.
- Tùy chọn “Giữ kết nối khi tắt TV” (TV Setup 0.7.1): giữ chip thức khi tắt màn hình. Không có trên TV TCL, vì TV này vẫn tự ngủ sâu dù app giữ.
- Tab Tệp · Cài và mục Giấc ngủ chia cột trên màn hình rộng như các tab khác; trên Mac (Safari) thẻ đầu mỗi cột không còn thấp hơn các cột bên. Tab Sức khỏe lúc đang đọc chỉ hiện dòng “Đang đọc từ TV...” ở giữa. Trên TV, nút Trái / Phải sang được cột bên cạnh, Lên / Xuống đi trong cùng cột.
- Xem màn hình TV: chọn bộ mã hóa “Phần cứng” trên TV TCL không còn ra hình sọc ngang kéo xéo ở cỡ 720p và 240p (bộ mã hóa của TV cần chiều ngang chia hết cho 32). Cả ba nơi.
- Web của TV: bấm “Hiện mã trên TV” không còn báo “Không gọi được server” dù TV đã hiện mã (máy chủ đóng kết nối trước khi đọc hết yêu cầu, làm mất câu trả lời). Thẻ ghép mã nằm giữa trang.
- Sửa nhiều lỗi ngầm: app máy tính không gửi được ảnh vẽ lên TV hay đoạn chữ dán dài (quá 64 KB); TV web page bỏ qua đoạn chữ dán dài; tải về một thư mục hoặc file không đọc được ra file hỏng; mở file có “#”, “%”, “?” trong tên; link http chuyển sang https không tải được trên app máy tính; báo lỗi của tải lên file lớn bị mất; “TV đang hỏi cho phép” không tắt sau khi câu hỏi đã đi; cập nhật app máy tính tải hai lần và “Kiểm tra bản mới” bị treo khi đang tải; chọn “Phần mềm” một lần làm chế độ Tự chọn dùng phần mềm mãi; tên và hình app không hiện lại sau một lần hỏi lỗi; lỗi vẽ và lỗi ảnh chụp hiện chữ kỹ thuật; trình duyệt không phát được luồng trực tiếp thì tự chuyển sang ảnh chụp; con lăn chuột trên hình ở Web của TV.
- Các hàng lựa chọn (Tự tắt, Lọc DNS, bộ lọc ứng dụng...) luôn nằm một dòng: dài hơn chỗ thì vuốt hoặc lăn chuột sang ngang, lựa chọn đang bật tự hiện ra; dòng dưới tên TV không còn ngắt giữa chữ.

**English**

- While the app works through several steps on the TV: installing TV Setup with the remote keys says “Step 1/3…” and asks to leave the TV's remote alone; when someone's key press takes the TV away from the step, the app stops, says so and offers to try again, instead of pressing on in the wrong place (computer app, phone app). Turning Tailscale on over ADB shows a “TV Remote is working on the TV” card in a corner of the TV until it is done (all three).
- Phone app: its dialogs are the app's own, in its colours and buttons (no more Android system dialogs).
- Picture quality in one dialog: size, frames per second, bit rate, encoder (all three).
- The “Now open” app's logo keeps its shape: a wide logo is no longer cut to a square.
- Now playing bar: a YouTube video just opened no longer jumps to about 2:15 (YouTube on Google TV stamps its position with an old time). The phone app keeps the “Now open” line beside the now playing bar, as the page does.
- Health tab, “Sleep” view: when the TV went off and on and why, when it turns itself off, whether Tailscale and TV Setup are running and when the TV stopped them, and an on / off history day by day (older days on request); the technical details (time the chip spent in deep sleep, a TCL TV's count of deep sleeps…) folded below (all three).
- TV Setup turns Tailscale back on when the TV stopped it to free memory (TCL TVs stop it with many other apps when short of RAM; it used to stay off until someone at home opened it). A Tailscale switched off by its user is left off.
- “Stay reachable when the TV is off” (TV Setup 0.7.1): keeps the chip awake with the screen off. Not offered on TCL TVs, which sleep deeply whatever an app holds.
- The Files · Install tab and the Sleep view are laid out in columns on wide screens, like the other tabs; on a Mac (Safari) a column's first card no longer sits lower than its neighbours. The Health tab shows only a centred “Reading from the TV...” while it reads. On the TV itself, Left / Right reach the next column and Up / Down stay in the same one.
- Watching the TV: the “Hardware” encoder on TCL TVs no longer gives a picture sheared into streaks at 720p and 240p (the TV's encoder needs a width that is a multiple of 32). All three.
- TV web page: “Show the code on the TV” no longer says “Cannot reach the server” while the TV shows the code (the server closed the connection before reading the whole request, which lost the reply). The pairing card is centred.
- Hidden faults fixed: the computer app could not send a drawing's picture or a long pasted text to the TV (over 64 KB); the TV web page dropped a long pasted text; downloading a folder or an unreadable file gave a broken file; opening a file with “#”, “%” or “?” in its name; an http link that redirects to https did not download on the computer app; the error of a big upload was lost; “The TV is asking for permission” stayed after the question had gone; the computer app's update downloaded twice and “Check for a new version” hung while it downloaded; picking “Software” once made Auto use software for good; app names and pictures did not come back after one failed read; drawing and screenshot errors in technical words; a browser that cannot play the live picture now gets snapshots; the mouse wheel on the picture on the TV web page.
- Rows of choices (Turn off when idle, DNS filter, the app filter...) stay on one line: longer than the room, they scroll sideways (swipe or mouse wheel) with the chosen one in view; the line under the TV's name no longer breaks inside a word.

## 0.7.0 - 2026-10-17

Web 0.7.0 · app điện thoại 0.7.0 · app TV 0.7.0

**Tiếng Việt**

- TV TCL: TV Setup không còn sập khi tự khởi động lại sau cập nhật hoặc bật TV (TV chặn dịch vụ nền chạy nổi, trước đây tiến trình chết sau 10 giây). Kiểm tra bản mới cũng không còn tưởng bản mới bị lỗi vì chuyện này rồi quay về bản cũ.
- Hình nâng cao: chọn khung/giây (15, 30, 60) và bitrate (1–8 Mbit/s) cho hình TV, ở mục ⚙️ Nâng cao trong ô chất lượng hình. Có trên app máy tính, Web của TV và app điện thoại.
- TV Hisense / Toshiba chạy VIDAA (thử nghiệm): bấm phím, gõ chữ, mở app, đổi nguồn vào, chỉnh âm lượng, tìm thấy khi quét. Ghép bằng mã 4 số hiện trên TV. TV đời mới cần chứng chỉ của Hisense: app tự tải về lần đầu (không kèm trong app); không tải được thì app điện thoại lấy từ app “VIDAA Smart TV” nếu đã cài, app máy tính cho chọn file .apk / .p12. Có trên app máy tính và app điện thoại.
- Danh sách TV: nút chọn TV ngắn lại, “Mở”.
- Xem hình TV: TV có bộ mã hóa hình phần cứng không chạy (như TCL Android 11, nhất là khi đang mở YouTube) không còn đứng mãi ở “Đang lấy hình”: sau 8 giây không có hình, app tự chuyển sang bộ mã hóa phần mềm của TV và nhớ cho lần sau.
- App máy tính trên Mac (và Safari): hình không còn nháy khi màn hình TV đứng yên.
- Màn remote có dòng “Đang mở” với tên và icon của app trên màn hình TV: lấy từ kết nối remote của TV, không có thì qua ADB; TV không cho biết thì ẩn. Có trên app máy tính, Web của TV và app điện thoại.
- Đổi TV: cảnh báo “TV Setup … chạy lỗi” của TV trước không còn hiện nhầm trên TV vừa chọn.
- Khung hình TV: bỏ nút vẽ (đã có trong Thêm nút); nút toàn màn hình chỉ hiện khi đang xem; ô chất lượng hình gọn lại.
- Hình nâng cao có thêm “Bộ mã hóa”: Tự chọn (mặc định), Phần cứng hoặc Phần mềm. Có trên app máy tính, Web của TV và app điện thoại.
- Nút chỉ có icon (bàn phím, micro, tìm...) giờ đặt icon chính giữa.
- Gửi nhật ký: nhật ký dài thì trang báo lỗi ghi rõ chỉ có đoạn cuối, bản đầy đủ đã được chép để dán vào.
- Roku: nút nguồn giữ lâu vẫn chỉ bật/tắt một lần; đầu Roku (không phải TV) không còn hiện phím âm lượng, kênh; Wake-on-LAN dùng đúng MAC của mạng đang dùng.
- TV Roku và đầu Roku (thử nghiệm): bấm phím, gõ chữ, mở app, đổi nguồn vào, tìm thấy khi quét. Roku đời mới chặn điều khiển từ điện thoại thì app chỉ cách bật lại. Có trên app máy tính và app điện thoại.
- Trang web: hàng nút trên cùng của remote (Nguồn, Trợ lý, Đánh thức, Thêm phím) có chữ dưới icon như app điện thoại.

**English**

- TCL TVs: TV Setup no longer crashes when it restarts itself after an update or boot (the TV blocks a background foreground-service start, which used to kill the process after 10 s). The update check no longer takes that for a broken new version and rolls back.
- Advanced picture: pick the frames per second (15, 30, 60) and bit rate (1–8 Mbit/s) of the TV's picture, under ⚙️ Advanced in the quality picker. On the computer app, the TV web page and the phone app.
- Hisense / Toshiba TVs on VIDAA (beta): keys, typing, apps, inputs, volume, found by the scan. Paired with a 4-digit code the TV shows. Newer TVs want Hisense's certificate: the app downloads it the first time (it is not shipped with the app); when that fails, the phone app takes it from the “VIDAA Smart TV” app if installed, and the computer app lets you pick its .apk or a .p12. On the computer app and the phone app.
- TV list: the buttons that pick a TV are shorter, “Connect”.
- TV picture: a TV whose hardware video encoder does not work (such as a TCL on Android 11, above all while YouTube is open) no longer sits at “Getting the picture” forever: after 8 s with no picture the app switches to the TV's software encoder and keeps it for next time.
- The computer app on a Mac (and Safari): the picture no longer flickers while the TV's screen stays still.
- The remote screen has a “Now open” line with the name and picture of the app on the TV's screen: from the TV's remote link, else over ADB; hidden when the TV does not say. On the computer app, the TV web page and the phone app.
- English: the picture button reads “Watch”, as its hint says.
- Switching TVs: the previous TV's “TV Setup … did not work” notice no longer shows on the one just picked.
- The TV picture card: the draw button is gone (it is under More keys); Full screen shows only while watching; the quality list is narrower.
- Advanced picture has an “Encoder” choice: Auto (the default), Hardware or Software. On the computer app, the TV web page and the phone app.
- Icon-only buttons (keyboard, microphone, search...) have their icon centred.
- Send the log: when the log is long, the report page says only its end is there and the whole log was copied to paste in.
- Roku: holding the power key turns it on or off once; Roku players (not TVs) no longer show volume and channel keys; Wake-on-LAN uses the MAC of the network in use.
- Roku TVs and players (beta): keys, typing, apps, inputs, found by the scan. When a newer Roku blocks control from phones, the app says how to allow it. On the computer app and the phone app.
- Web page: the remote's top row (Power, Assistant, Wake, More keys) has words under its icons, as in the phone app.

## 0.6.0 - 2026-10-17

Web 0.6.0 · app điện thoại 0.6.0 · app TV 0.6.0

**Tiếng Việt**

- Cài TV Setup lên TV chưa bật ADB: luôn đi qua Downloader. Trước đây trình duyệt có sẵn của TV (như TCL) nhận đường dẫn APK, tải về rồi đứng ở trang trắng, không mở được trình cài. Cách gửi link thẳng vẫn còn, ở cuối danh sách “Bật ADB”, ghi rõ là dự phòng; sau một phút chưa thấy báo cài xong thì app gợi ý cài qua Downloader.
- Bật ADB lần đầu: TV chỉ hỏi “Cho phép gỡ lỗi USB?” một lần. Trước đây mỗi phần của app (hình, quản lý, nhạc...) hỏi riêng, và TV Setup hỏi thêm cho chính nó; giờ các phần chờ chung một câu trả lời, và điện thoại / máy tính vừa được cho phép tự trả lời giúp câu hỏi của TV Setup.
- Rung nhẹ khi bấm nút remote trên điện thoại (app điện thoại, và trang web mở bằng Chrome trên Android). Tắt được trong Cài đặt → Khi bấm nút.
- Nghe tiếng TV trên điện thoại (🎧) không còn nhanh và giật: một số điện thoại (như Xiaomi) báo vị trí phát theo từng nấc lớn, app tưởng bị trễ nên bỏ gần 1/3 số gói âm thanh. Giờ đo vị trí phát chính xác và chỉ bỏ gói khi thật sự trễ.
- TV Setup tự lo câu hỏi “Cho phép gỡ lỗi USB?” mà không cần điện thoại: câu hỏi bị màn TV Setup che thì TV Setup tự lùi xuống cho thấy, xong tự quay lại; câu hỏi cũ kẹt lại thì hỏi lại; nếu TV vẫn không hiện câu hỏi thì chỉ cách tắt/bật Gỡ lỗi USB. Sửa lỗi TV Setup mới cài tạo nhiều khóa cùng lúc, làm TV hỏi liên tục cho những khóa lạ.
- App điện thoại: hình TV chạy một kết nối riêng, như app máy tính. Bật/tắt hình, đổi chất lượng hay ra ngoài app chỉ mở/đóng phần hình; phím, thanh đang phát và tiếng vẫn chạy liền, không phải kết nối lại.
- TV Android 12 trở xuống: sau khi bấm Cho phép, các câu hỏi “Cho phép gỡ lỗi USB?” giống hệt còn xếp hàng phía sau được app tự đóng, không phải bấm lại nhiều lần.
- Điện thoại Android 8 trở xuống: mở remote không còn bị cuộn xuống ô gõ chữ, che mất các phím mũi tên.

**English**

- Installing TV Setup on a TV without ADB always goes through Downloader. The TV's own browser (TCL's, for one) used to take the APK link, download it and then sit on a white page, unable to open the installer. Sending the link straight to the TV is still there, last in “Turn on ADB”, marked as a fallback; after a minute with no word that it installed, the app offers Downloader.
- Turning ADB on the first time: the TV asks “Allow USB debugging?” once. Each part of the app (picture, Manage, now playing...) used to ask on its own, and TV Setup asked for itself too; now they wait for one answer, and the phone or computer just allowed answers TV Setup's question for it.
- A light tap under the finger when pressing a remote key on a phone (the phone app, and the page in Chrome on Android). Off in Settings → When pressing a key.
- Listening to the TV on the phone (🎧) no longer plays fast and choppy: some phones (Xiaomi, for one) report the playback position in big steps, so the app thought it was behind and dropped about a third of the sound. It now reads the position exactly and drops only when really behind.
- TV Setup handles “Allow USB debugging?” on its own, no phone needed: a question hidden behind its screen makes it step aside and come back after; a stuck question is asked again; and if the TV still shows none it says how to switch USB debugging off and on. A newly installed TV Setup no longer makes several keys at once, which had the TV asking again and again for strange keys.
- Phone app: the TV picture has a connection of its own, as in the computer app. Turning it on or off, changing its quality or leaving the app opens or closes only the picture; keys, the now playing bar and the sound go on without reconnecting.
- TVs on Android 12 and older: once Allow is chosen, the copies of the same “Allow USB debugging?” question still queued behind it are closed by the app, instead of having to be answered again and again.
- Phones on Android 8 and older: the remote no longer opens scrolled down to the text box, with the arrow keys out of sight.

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
