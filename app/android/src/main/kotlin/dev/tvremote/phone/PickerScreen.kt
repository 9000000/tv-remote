package dev.tvremote.phone

import android.content.Context
import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Build
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import dev.tvremote.core.Remote
import dev.tvremote.core.Ssdp
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import dev.tvremote.core.SameTv

// The first screen: saved TVs (with whether each answers now), waking one that is off, scanning the Wi-Fi, typing an IP.

/** The box at the top of the picker: an error (red) or a progress note. */
internal fun MainActivity.message(text: String?, error: Boolean) {
    pickerMsg.text = text ?: ""
    pickerMsg.setTextColor(if (error) ERR_TEXT else TEXT)
    pickerMsg.background = box(if (error) ERR_BG else CARD, 12)
    pickerMsg.visibility = if (text == null) View.GONE else View.VISIBLE
}

// ---------------------------------------------------------------- waking a TV that is off
/**
 * Standby with the network still on: the TV answers, so connect and press "wake". Network off too: only a
 * Wake-on-LAN packet helps, and that reaches the TV from its own Wi-Fi only (never through Tailscale).
 */
internal fun MainActivity.wake(ip: String) {
    if (waking) return
    waking = true
    val mac = pref("mac:$ip")
    val lan = pref("lan:$ip")
    message(t("Đang đánh thức ", "Waking ") + title(ip) + "...", false)
    val port = upPort(ip)
    Thread {
        var up = open(ip, port, 1500)
        var sent = 0
        var i = 0
        val hopeless = mac.isEmpty() || !Wol.sameNetwork(lan)     // no packet can reach the TV from here: say so at once instead of waiting
        while (i < 22 && !up && !hopeless) {
            if (i % 6 == 0 && mac.isNotEmpty()) sent += Wol.send(mac, lan)
            try { Thread.sleep(1000) } catch (e: InterruptedException) { break }
            up = open(ip, port, 600) || lan.isNotEmpty() && lan != ip && Wol.sameNetwork(lan) && open(lan, port, 400)
            i++
        }
        val ok = up
        val packets = sent > 0
        val home = Wol.sameNetwork(lan)
        ui.post {
            waking = false
            if (isFinishing || host != null) return@post
            if (ok) {
                wakeNext = true
                connect(ip)
                return@post
            }
            val why = if (mac.isEmpty()) t("App chưa biết địa chỉ MAC của TV này (nó tự lưu sau lần kết nối đầu tiên bằng bản app này), nên chưa gửi được gói đánh thức.",
                "The app does not know this TV’s MAC address yet (it is saved after the first connection with this version), so no wake packet was sent.")
            else if (!packets || !home) t("TV đã tắt cả mạng nên không nghe qua Tailscale. Gói đánh thức chỉ tới được TV từ đúng Wi-Fi nhà TV, mà điện thoại đang ở mạng khác. Cách mở: nối điện thoại vào Wi-Fi đó rồi bấm lại, hoặc dùng nút Đánh thức trên web ở máy tính (nó nhờ một máy ở nhà gửi giúp).",
                "The TV’s network is off too, so it cannot hear anything over Tailscale. A wake packet only reaches the TV from its own Wi-Fi, and the phone is on another network. Join that Wi-Fi and try again, or use Wake in the web UI on a computer (it relays through a machine at home).")
            else t("Đã gửi gói đánh thức trong Wi-Fi nhưng TV không lên. Kiểm tra trên TV: Cài đặt → Mạng và Internet → “Khởi động từ xa” phải bật.",
                "Wake packets were sent on the Wi-Fi but the TV did not come up. On the TV check: Settings → Network & Internet → “Remote start” must be on.")
            message(t("Chưa đánh thức được TV.\n\n", "Could not wake the TV.\n\n") + why, true)
        }
    }.start()
}

/** The port that answers while the TV is on: ADB's on an Android TV, the remote link's on an LG or Samsung TV. */
internal fun MainActivity.upPort(ip: String): Int = kindOf(ip).let { if (it == Remote.Kind.ANDROID) 5555 else Remote.Kind.ports(it).first() }

// ---------------------------------------------------------------- saved TVs
internal fun MainActivity.name(ip: String): String = pref("name:$ip")

internal fun MainActivity.savedIps(): MutableList<String> = pref("ips").split(",").filter { it.isNotEmpty() }.toMutableList()

private val PER_TV = listOf("name", "mac", "lan", "ts", "tvs", "kind") // the prefs kept per saved TV, as "<field>:<ip>" (and atv:)

private fun MainActivity.savedTv(ip: String) = SameTv.Tv(ip, pref("lan:$ip"), pref("ts:$ip"), pref("mac:$ip"))

/** The saved TV that [ip] is another address of (its home IP, its Tailscale IP), if any. */
internal fun MainActivity.savedOwner(ip: String): String? = savedIps().firstOrNull { it != ip && ip in savedTv(it).addresses }

/** Rows that are one TV (picked once on the Wi-Fi, once over Tailscale) become one, keeping what each knew. [first]: kept. */
internal fun MainActivity.foldSaved(first: String? = null) {
    val ips = savedIps().let { l -> if (first != null && first in l) listOf(first) + (l - first) else l }
    val groups = SameTv.groups(ips) { savedTv(it) }
    if (groups.all { it.second.isEmpty() }) return
    val e = prefs.edit()
    for ((k, dups) in groups) for (d in dups) {
        for (f in listOf("name", "mac", "tvs")) if (pref("$f:$k").isEmpty() && pref("$f:$d").isNotEmpty()) e.putString("$f:$k", pref("$f:$d"))
        val addr = (savedTv(d).addresses - k)
        if (pref("lan:$k").isEmpty()) addr.firstOrNull { !it.startsWith("100.") }?.let { e.putString("lan:$k", it) }
        if (pref("ts:$k").isEmpty()) addr.firstOrNull { it.startsWith("100.") }?.let { e.putString("ts:$k", it) }
        if (prefs.getBoolean("atv:$d", false)) e.putBoolean("atv:$k", true)
        for (f in PER_TV) e.remove("$f:$d")
        e.remove("atv:$d")
    }
    e.putString("ips", TextUtils.join(",", groups.map { it.first })).apply()
}

internal fun MainActivity.save(ip: String, model: String) {
    val ips = savedIps()
    ips.remove(ip)
    ips.add(0, ip)
    val e = prefs.edit().putString("ips", TextUtils.join(",", ips))
    if (model.isNotEmpty()) e.putString("name:$ip", model)
    e.apply()
}

/** Asks each saved TV, every 8 s while the picker shows, whether it answers, and through which of its addresses. */
internal fun MainActivity.probeSaved() {
    ui.removeCallbacks(probe)
    if (host == null && !waking) for (ip in savedIps()) Thread {
        // the saved address first, then the TV's other one: its Tailscale IP when away, its home IP when at home
        val via = listOf(ip, pref("ts:$ip"), pref("lan:$ip")).filter { it.isNotEmpty() }.distinct()
            .firstOrNull { h -> kindOf(ip).let { k -> if (k != Remote.Kind.ANDROID) Remote.Kind.ports(k).any { open(h, it, 1500) } else open(h, 5555, 2500) || prefs.getBoolean("atv:$ip", false) && open(h, 6466, 1500) } }
        val up = via != null
        val lan = pref("lan:$ip")
        val away = lan.isNotEmpty() && !Wol.sameNetwork(lan)   // the phone is not on the TV's own Wi-Fi
        ui.post { if (via != null) route[ip] = via else route.remove(ip); reach[ip] = up; awayFrom[ip] = away; showReach(ip, up) }
    }.start()
    ui.postDelayed(probe, 8000)
}

internal fun MainActivity.showReach(ip: String, up: Boolean?) {
    val (b, wk) = savedRows[ip] ?: return
    val status = when (up) {
        null -> t("đang kiểm tra...", "checking...")
        true -> route[ip].let { r -> if (r == null || r == ip) t("● kết nối được", "● reachable")
            else if (r.startsWith("100.")) t("● kết nối được qua Tailscale", "● reachable through Tailscale") else t("● kết nối được trong nhà", "● reachable at home") }
        false -> if (awayFrom[ip] == true) t("○ không thấy · bạn ở ngoài Wi-Fi của TV", "○ no answer · you are off the TV’s Wi-Fi")
            else t("○ không thấy · TV tắt?", "○ no answer · TV off?")
    }
    val text = android.text.SpannableString((if (name(ip).isEmpty()) ip else name(ip) + "\n" + ip) + "\n" + status)
    val from = text.length - status.length
    text.setSpan(android.text.style.ForegroundColorSpan(if (up == true) 0xff4ade80.toInt() else MUTED), from, text.length, 0)
    text.setSpan(android.text.style.RelativeSizeSpan(0.82f), from, text.length, 0)
    b.text = text
    wk.visibility = if (up == false) View.VISIBLE else View.GONE
}

internal fun MainActivity.refreshSaved() {
    foldSaved()
    savedList.removeAllViews()
    savedRows.clear()
    for (ip in savedIps()) {
        val b = row("") { connectSaved(ip) }
        val wk = icon("@power", t("Đánh thức", "Wake"), BTN)        // only offered while the TV does not answer
        wk.setOnClickListener { wake(ip) }
        wk.visibility = View.GONE
        savedRows[ip] = Pair(b, wk)
        showReach(ip, reach[ip])
        val line = LinearLayout(this)
        line.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        val wp = LinearLayout.LayoutParams(dp(60), -1)
        wp.leftMargin = dp(6)
        line.addView(wk, wp)
        line.layoutParams = gap(6)
        val ask = {
            AlertDialog.Builder(this).setMessage(t("Xóa TV này khỏi danh sách?", "Remove this TV from the list?") + "\n" + title(ip) + (if (name(ip).isEmpty()) "" else " · $ip") + "\n\n" +
                    t("Không đổi gì trên TV. Muốn dùng lại thì quét hoặc nhập IP như lần đầu.", "Nothing changes on the TV. To use it again, scan or type its IP as the first time."))
                .setPositiveButton(t("Xóa", "Remove")) { _, _ ->
                    val ips = savedIps()
                    ips.remove(ip)
                    prefs.edit().putString("ips", TextUtils.join(",", ips)).also { e -> PER_TV.forEach { e.remove("$it:$ip") } }.remove("atv:$ip").apply()
                    refreshSaved()
                }.setNegativeButton(android.R.string.cancel, null).show()
        }
        val del = icon("@close", t("Xóa TV này khỏi danh sách", "Remove this TV from the list"), BTN)
        del.setOnClickListener { ask() }
        val dp2 = LinearLayout.LayoutParams(dp(48), -1)
        dp2.leftMargin = dp(6)
        line.addView(del, dp2)
        b.setOnLongClickListener { ask(); true }
        savedList.addView(line)
    }
    fleetBtn.visibility = if (savedList.childCount >= 2) View.VISIBLE else View.GONE
    if (savedList.childCount == 0) savedList.addView(note(t("Chưa có TV nào. Quét Wi-Fi hoặc nhập IP bên dưới.", "No TVs yet. Scan the Wi-Fi or type an IP below.")))
}

// ---------------------------------------------------------------- finding TVs on the Wi-Fi
private fun MainActivity.foundRow(ip: String, name: String, adb: Boolean) = row((if (name.isNotEmpty()) name + "\n" else "") + ip + " · " +
    (if (adb) t("ADB đã bật", "ADB on") else t("ADB chưa bật: chỉ bấm phím, app sẽ giúp bật", "ADB off: keys only, the app helps turn it on"))) { prefs.edit().remove("kind:$ip").apply(); connect(ip) }

/** An LG or Samsung TV the SSDP search found: its own remote link, no ADB. */
private fun MainActivity.foundRow(f: Ssdp.Found) = row((if (f.name.isNotEmpty()) f.name + "\n" else "") + f.ip + " · TV " + Remote.Kind.name(f.kind)) {
    prefs.edit().putString("kind:${f.ip}", f.kind).apply(); connect(f.ip)
}

internal fun MainActivity.scan() {
    scanBtn.isEnabled = false
    scanBtn.text = t("Đang quét...", "Scanning...")
    foundList.removeAllViews()
    Thread {
        val nets = ArrayList<String>()
        val found = Collections.synchronizedList(ArrayList<String>())
        try {
            val add = { a: java.net.InetAddress? ->
                val ip = a?.hostAddress
                if (ip != null && a is Inet4Address && a.isSiteLocalAddress) ip.substring(0, ip.lastIndexOf('.') + 1).let { if (it !in nets) nets.add(it) }
            }
            try {
                for (ni in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                    if (!ni.isUp || ni.isLoopback) continue
                    for (a in Collections.list(ni.inetAddresses)) add(a)
                }
            } catch (ignored: Exception) {
            }
            try {                                                 // a second source: with a VPN (Tailscale) on, the list above can miss the Wi-Fi
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                @Suppress("DEPRECATION")
                for (n in cm.allNetworks) cm.getLinkProperties(n)?.linkAddresses?.forEach { add(it.address) }
            } catch (ignored: Exception) {
            }
            val seen = { ip: String ->
                if (synchronized(found) { if (ip in found) false else found.add(ip) }) {
                    val adb = open(ip, 5555, 900)
                    val n = name(ip).ifEmpty { castName(ip) }
                    ui.post { foundList.addView(foundRow(ip, n, adb)) }   // shown as soon as it answers, not after the whole network
                }
            }
            val stopMdns = announced { ip -> Thread { seen(ip) }.start() }   // TVs also announce themselves; this finds the slow ones
            val ssdp = Thread { Ssdp.find(3000) { f -> if (synchronized(found) { if (f.ip in found) false else found.add(f.ip) }) ui.post { foundList.addView(foundRow(f)) } } }.apply { start() }   // LG and Samsung TVs answer this
            val pool = Executors.newFixedThreadPool(96)
            for (net in nets) for (i in 1 until 255) {
                val ip = net + i
                pool.execute { if (open(ip, 6466, 900) || open(ip, 5555, 900)) seen(ip) }
            }
            pool.shutdown()
            pool.awaitTermination(25, TimeUnit.SECONDS)
            ssdp.join(5000)
            stopMdns()
        } catch (ignored: Exception) {
        }
        ui.post {
            scanBtn.isEnabled = true
            scanBtn.text = "🔍  " + t("Quét Wi-Fi", "Scan the Wi-Fi")
            if (foundList.childCount == 0) foundList.addView(note(
                if (nets.isEmpty()) t("Điện thoại chưa nối Wi-Fi. Khác mạng thì nhập IP Tailscale của TV bên dưới.", "The phone is not on Wi-Fi. On another network, type the TV’s Tailscale IP below.")
                else t("Đã quét ", "Scanned ") + nets.joinToString(", ") { it + "x" } + t(": không thấy TV nào. TV có đang bật và cùng Wi-Fi này không?\n\nTV ở mạng khác (qua Tailscale) thì không quét được: nhập IP Tailscale của TV (100.x.x.x) bên dưới.",
                    ": no TV found. Is it on, and on this Wi-Fi?\n\nA TV on another network (through Tailscale) cannot be scanned for: type its Tailscale IP (100.x.x.x) below.")))
        }
    }.start()
}

// ---------------------------------------------------------------- picker screen
internal fun MainActivity.buildPicker() {
    picker = col()
    picker.setPadding(dp(16), dp(20), dp(16), dp(24))
    val title = label("TV Remote", 24, TEXT)
    title.typeface = Typeface.DEFAULT_BOLD
    val top = LinearLayout(this)
    top.gravity = Gravity.CENTER_VERTICAL
    val names = col()
    names.addView(title)
    names.addView(label(t("Bản ", "Version ") + versionLine(), 12, MUTED))
    top.addView(logo(40), LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(12) })
    top.addView(names, LinearLayout.LayoutParams(0, -2, 1f))
    val set = icon("@settings", t("Cài đặt", "Settings"), BTN)
    set.setOnClickListener {
        hideKeyboard()
        settings.visibility = View.VISIBLE
    }
    top.addView(set, LinearLayout.LayoutParams(dp(52), dp(44)))
    picker.addView(top)
    pickerMsg = label("", 14, ERR_TEXT)
    pickerMsg.background = box(ERR_BG, 12)
    pickerMsg.setPadding(dp(12), dp(10), dp(12), dp(10))
    pickerMsg.visibility = View.GONE
    picker.addView(pickerMsg, gap(12))
    updateBtn = button("", GREEN)
    updateBtn.setOnClickListener { askUpdate() }
    updateBtn.visibility = View.GONE
    val up = LinearLayout.LayoutParams(-1, dp(48))
    up.topMargin = dp(12)
    picker.addView(updateBtn, up)

    val savedHead = LinearLayout(this)                                // the heading, with "Several TVs" at its end once there are two
    savedHead.gravity = android.view.Gravity.CENTER_VERTICAL
    savedHead.addView(head(t("TV ĐÃ DÙNG", "YOUR TVS")), LinearLayout.LayoutParams(0, -2, 1f))
    fleetBtn = fleetButton()
    savedHead.addView(fleetBtn, LinearLayout.LayoutParams(-2, dp(34)))
    picker.addView(savedHead, gap(14))
    savedList = col()
    picker.addView(savedList)

    picker.addView(head(t("TÌM TRONG WI-FI", "FIND ON THE WI-FI")), gap(18))
    scanBtn = button("🔍  " + t("Quét Wi-Fi", "Scan the Wi-Fi"), ACCENT)
    scanBtn.setOnClickListener { scan() }
    val sp = LinearLayout.LayoutParams(-1, dp(48))
    sp.topMargin = dp(6)
    picker.addView(scanBtn, sp)
    foundList = col()
    picker.addView(foundList)

    picker.addView(head(t("NHẬP IP CỦA TV", "TYPE THE TV’S IP")), gap(18))
    val line = LinearLayout(this)
    ipBox = input(t("vd 192.168.1.88 hoặc 100.x.x.x", "e.g. 192.168.1.88 or 100.x.x.x"))
    ipBox.inputType = InputType.TYPE_CLASS_PHONE
    ipBox.imeOptions = EditorInfo.IME_ACTION_GO
    ipBox.setOnEditorActionListener { _, _, _ ->
        connect(ipBox.text.toString())
        true
    }
    val go = icon("@right", t("Kết nối", "Connect"), ACCENT)
    go.setOnClickListener { connect(ipBox.text.toString()) }
    line.addView(ipBox, LinearLayout.LayoutParams(0, dp(48), 1f))
    val lp = LinearLayout.LayoutParams(-2, dp(48))
    lp.leftMargin = dp(8)
    lp.width = dp(60)
    line.addView(go, lp)
    picker.addView(line, gap(6))
    val help = button(t("Cách kết nối", "How to connect"), 0)
    help.setTextColor(ACCENT)
    help.setOnClickListener {
        AlertDialog.Builder(this).setTitle(t("Cách kết nối", "How to connect")).setMessage(t(
            "1. BẬT ADB TRÊN TV (một lần)\nCài đặt → Tùy chọn thiết bị → Giới thiệu → bấm OK 7 lần vào “Bản dựng”. Rồi Tùy chọn nhà phát triển → bật “Gỡ lỗi ADB”.\n\n" +
                "2A. CÙNG WI-FI VỚI TV\nBấm “Quét Wi-Fi” rồi chọn TV. Hoặc xem IP trên TV: Cài đặt → Mạng và Internet → tên Wi-Fi.\n\n" +
                "2B. KHÁC MẠNG\nBật Tailscale trên cả điện thoại và TV. TV đã kết nối ở nhà một lần thì app nhớ IP Tailscale của nó, ra ngoài chỉ cần bấm TV trong danh sách. Chưa thì nhập IP 100.x của TV (xem trong app Tailscale).\n\n" +
                "TV chưa có Tailscale? Lúc đang ở nhà, kết nối TV rồi bấm nút ô lưới → Thiết lập → “Cài và đăng nhập Tailscale”: app tự cài lên TV và mở trang đăng nhập trên điện thoại, bạn chỉ bấm Connect.\n\n" +
                "3. LẦN ĐẦU KẾT NỐI\nTV hỏi cho phép: tích “Luôn cho phép” rồi OK.\n\n" +
                "MẸO\nNút nguồn cạnh tên TV: đánh thức TV đang tắt. Nút ✕ cạnh tên TV: xóa khỏi danh sách.",
            "1. TURN ON ADB ON THE TV (once)\nSettings → Device Preferences → About → press OK 7 times on “Build”. Then Developer options → turn on “ADB debugging”.\n\n" +
                "2A. SAME WI-FI AS THE TV\nTap “Scan the Wi-Fi” and pick the TV. Or read the IP on the TV: Settings → Network & Internet → the Wi-Fi name.\n\n" +
                "2B. ANOTHER NETWORK\nTurn on Tailscale on both the phone and the TV. Once the TV has been connected at home, the app remembers its Tailscale IP: away, just tap the TV in the list. Otherwise type the TV’s 100.x IP (see the Tailscale app).\n\n" +
                "No Tailscale on the TV yet? While at home, connect to the TV, then tap the grid button → Setup → “Install and sign in to Tailscale”: the app installs it on the TV and opens the sign-in page on the phone; you only press Connect.\n\n" +
                "3. THE FIRST CONNECTION\nThe TV asks for permission: tick “Always allow”, then OK.\n\n" +
                "TIPS\nThe power button next to a TV wakes it when it is off. The ✕ next to a TV removes it from the list."))
            .setPositiveButton(t("Đã hiểu", "Got it"), null).show()
    }
    picker.addView(help, LinearLayout.LayoutParams(-1, dp(44)))
}
