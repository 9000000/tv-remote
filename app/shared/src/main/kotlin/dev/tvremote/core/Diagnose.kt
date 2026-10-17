package dev.tvremote.core

import dev.tvremote.core.adb.Mdns
import dev.tvremote.core.remote.Remote
import dev.tvremote.core.remote.Ssdp
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * "Why can't I reach the TV?", answered in about three seconds with ONE conclusion and what to do about it, in place of the
 * raw error. What it looks at, all at once: whether this device is on a network that can hold the TV, which of the TV's ports
 * answer (and whether a closed one was *refused*, which proves the TV is up, or stayed silent), whether a VPN is up, what the
 * app's own ADB link says; only when nothing answers it looks around for the same TV under a new address (SSDP / mDNS).
 * [decide] is the table of conclusions, free of any network so a test can walk it; [Real] is the network.
 */
object Diagnose {
    /** OPEN: connected. REFUSED: the TV's network card answered "no one listens here" (it is up). SILENT: nothing came back. */
    enum class Port { OPEN, REFUSED, SILENT }

    /** This device against the TV's address. NA: nothing to check (a name, a loopback, a public address). */
    enum class Net { OK, NONE, OTHER, TAILSCALE_OFF, NA }

    class Found(val ip: String, val name: String)

    interface Probe {
        fun port(host: String, port: Int, ms: Int): Port
        fun net(host: String): Net
        /** A VPN is carrying this device's traffic. */
        fun vpn(): Boolean
        /** What this app's own ADB link to [host] says (AdbLink.state(), "waiting" while the TV asks), null with no link to it. */
        fun adb(host: String): String?
        /** The TV called [name] of this [kind] under an address other than [not], null when none or more than one. */
        fun find(kind: String, name: String, not: String): Found?
    }

    /** What was seen; [moved] only once the search for a new address was made. */
    class Facts(val kind: String, val ip: String, val ports: Map<Int, Port>, val net: Net, val vpn: Boolean, val adb: String?,
                val tls: Boolean = false, val moved: Found? = null) {
        val open get() = ports.filterValues { it == Port.OPEN }.keys
        val alive get() = ports.values.any { it != Port.SILENT }
        val home get() = Lan.home(ip) != null
    }

    class Check(val id: String, val ok: Boolean?, val note: String)
    class Result(val code: String, val ip: String, val kind: String, val found: Found?, val checks: List<Check>, val ms: Long)

    const val CLOCK = "clock"                  // the TV answers but its date is wrong: TLS refuses
    const val NO_WIFI = "no_wifi"              // this device is on no local network
    const val OTHER_NET = "other_net"          // ... or on another one than the TV's
    const val TAILSCALE = "tailscale"          // a 100.x address and this device's Tailscale is off
    const val ALLOW = "allow"                  // ADB answers and the TV is asking "Allow USB debugging?"
    const val ADB_OFF = "adb_off"              // the remote service answers, ADB's port is closed: keys still work
    const val REMOTE_OFF = "remote_off"        // ADB answers, the remote service does not
    const val UP = "up"                        // answers: the failure was momentary
    const val CLOSED = "closed"                // the TV is on the network but none of its services listen
    const val MOVED = "moved"                  // nothing at this address; the same TV, by name, answers at another
    const val VPN = "vpn"                      // nothing answers and a VPN is carrying the traffic
    const val OFF = "off"                      // nothing answers: off, asleep, or another address
    val CODES = listOf(CLOCK, NO_WIFI, OTHER_NET, TAILSCALE, ALLOW, ADB_OFF, REMOTE_OFF, UP, CLOSED, MOVED, VPN, OFF)

    const val ADB = 5555
    private const val SETUP = 8765

    /** The ports worth asking, by kind: the remote service(s), then what only an Android TV has (ADB and TV Setup). */
    fun ports(kind: String): List<Int> = when (kind) {
        Remote.Kind.ANDROID -> listOf(ADB, 6466, 6467, SETUP)
        Remote.Kind.LG -> listOf(3000, 3001)
        Remote.Kind.SAMSUNG -> listOf(8001, 8002)
        Remote.Kind.ROKU -> listOf(8060)
        Remote.Kind.VIDAA -> listOf(36669)
        else -> listOf(ADB, 6466, 6467, SETUP)
    }

    /** An error that comes from a TV whose date is wrong (Errors.say words it the same way). */
    fun clockError(err: String): Boolean = Regex("SSLHandshake|CertPathValidator|Chain validation|CERTIFICATE_VERIFY|not yet valid|expired", RegexOption.IGNORE_CASE).containsMatchIn(err)

    /** The one conclusion. Order matters: what rules out the others first. */
    fun decide(f: Facts): String {
        val open = f.open
        val android = f.kind == Remote.Kind.ANDROID
        if (f.tls && open.isNotEmpty()) return CLOCK
        if (open.isEmpty()) {
            when (f.net) { Net.NONE -> return NO_WIFI; Net.OTHER -> return OTHER_NET; Net.TAILSCALE_OFF -> return TAILSCALE; else -> {} }
            if (f.alive) return CLOSED
            if (f.moved != null) return MOVED
            if (f.vpn && f.home) return VPN
            return OFF
        }
        if (!android) return UP
        val adb = ADB in open
        val remote = 6466 in open || 6467 in open
        if (adb && (f.adb == "unauthorized" || f.adb == "waiting")) return ALLOW
        if (!adb && (remote || SETUP in open)) return ADB_OFF
        if (adb && !remote) return REMOTE_OFF
        return UP
    }

    /** What each conclusion needs the person (or the page) to do next. */
    fun actions(code: String): List<String> = when (code) {
        ADB_OFF -> listOf("adbhelp", "retry")
        MOVED -> listOf("use", "retry")
        OFF, CLOSED, VPN -> listOf("retry", "scan")
        CLOCK -> listOf("retry")
        else -> listOf("retry")
    }

    /** [title] and [fix]: a short line saying what is wrong, and the one thing to do. */
    fun say(code: String, f: Facts, vi: Boolean): Pair<String, String> {
        fun t(v: String, en: String) = if (vi) v else en
        return when (code) {
            CLOCK -> t("Ngày giờ trên TV đang sai", "The TV's date and time are wrong") to
                t("TV trả lời nhưng kết nối bảo mật bị từ chối. Trên TV: Cài đặt → Hệ thống → Ngày và giờ → bật tự động, rồi thử lại.",
                    "The TV answers but the secure connection is refused. On the TV: Settings → System → Date & time → set it to automatic, then try again.")
            NO_WIFI -> t("Máy này chưa vào Wi-Fi", "This device is not on Wi-Fi") to
                t("Bật Wi-Fi và nối vào cùng mạng với TV, rồi thử lại.", "Turn Wi-Fi on and join the TV's network, then try again.")
            OTHER_NET -> t("Máy này đang ở mạng khác với TV", "This device is on another network than the TV") to
                t("TV ở ${f.ip}. Nối máy này vào đúng Wi-Fi của TV (không phải mạng khách hay 4G), rồi thử lại.",
                    "The TV is at ${f.ip}. Join the TV's own Wi-Fi (not a guest network or mobile data), then try again.")
            TAILSCALE -> t("Tailscale đang tắt trên máy này", "Tailscale is off on this device") to
                t("TV ở địa chỉ Tailscale ${f.ip}. Bật Tailscale trên máy này rồi thử lại.", "The TV is at the Tailscale address ${f.ip}. Turn Tailscale on here, then try again.")
            ALLOW -> t("TV đang chờ bạn cho phép", "The TV is waiting for you to allow this device") to
                t("Nhìn lên TV: tích “Luôn cho phép” ở hộp “Cho phép gỡ lỗi USB?” rồi chọn OK.", "Look at the TV: tick “Always allow” in the “Allow USB debugging?” box, then choose OK.")
            ADB_OFF -> t("Gỡ lỗi ADB đang tắt trên TV", "ADB debugging is off on the TV") to
                t("Các phím vẫn bấm được. Muốn xem hình, mở app, quản lý tệp thì bật “Gỡ lỗi ADB” trong Tùy chọn nhà phát triển của TV.",
                    "The keys still work. For the picture, apps and files turn on “ADB debugging” in the TV's Developer options.")
            REMOTE_OFF -> t("Dịch vụ remote của TV không trả lời", "The TV's remote service does not answer") to
                t("ADB trả lời nên phím đi qua ADB. Nếu muốn dùng remote không qua ADB, khởi động lại TV.", "ADB answers, so keys go through ADB. To use the remote without ADB, restart the TV.")
            UP -> t("TV đang trả lời", "The TV is answering") to
                t("Lỗi lúc nãy chỉ thoáng qua. Bấm Thử lại; nếu TV hiện hộp hỏi, chọn Cho phép.", "The error was momentary. Try again; if the TV shows a question, choose Allow.")
            CLOSED -> t("TV đang bật mạng nhưng chưa nhận lệnh", "The TV is on the network but not listening") to
                t("Thường là TV đang ngủ nhẹ. Bấm nút nguồn trên điều khiển của TV để đánh thức, chờ vài giây rồi thử lại.",
                    "Usually the TV is dozing. Press the power button on the TV's own remote, wait a few seconds, then try again.")
            MOVED -> t("TV đã đổi địa chỉ", "The TV has a new address") to
                t("Tìm thấy “${f.moved?.name}” ở ${f.moved?.ip} (trước là ${f.ip}). Dùng địa chỉ mới?", "Found “${f.moved?.name}” at ${f.moved?.ip} (it was ${f.ip}). Use the new address?")
            VPN -> t("VPN đang chặn đường tới TV", "A VPN is in the way") to
                t("Không tới được TV trong nhà khi VPN bật. Trong app VPN bật cho phép mạng LAN (Local network / Bypass LAN), hoặc tắt VPN rồi thử lại.",
                    "The TV at home is out of reach while the VPN is on. Allow the local network in the VPN app (Local network / Bypass LAN), or turn the VPN off, then try again.")
            else -> t("TV không trả lời", "The TV is not answering") to
                t("TV có thể đang tắt hẳn hoặc ngủ sâu, hoặc đã đổi địa chỉ. Bật TV rồi thử lại, hoặc quét mạng để tìm lại.",
                    "The TV may be off or asleep, or have a new address. Turn it on and try again, or scan the network to find it.")
        }
    }

    private fun checks(f: Facts, code: String, vi: Boolean): List<Check> {
        fun t(v: String, en: String) = if (vi) v else en
        val out = ArrayList<Check>()
        out.add(Check("net", when (f.net) { Net.NA -> null; Net.OK -> true; else -> false },
            when (f.net) {
                Net.NA -> t("Mạng của máy này: không cần kiểm tra", "This device's network: nothing to check")
                Net.OK -> t("Máy này cùng mạng với TV", "This device is on the TV's network")
                Net.NONE -> t("Máy này không có Wi-Fi", "This device has no Wi-Fi")
                Net.OTHER -> t("Máy này ở mạng khác TV", "This device is on another network than the TV")
                Net.TAILSCALE_OFF -> t("Tailscale đang tắt", "Tailscale is off")
            }))
        val open = f.open.sorted()
        out.add(Check("tv", f.alive, when {
            open.isNotEmpty() -> t("TV trả lời ở cổng ", "The TV answers on port ") + open.joinToString(", ")
            f.alive -> t("TV có mạng nhưng các cổng đều đóng", "The TV is on the network but its ports are closed")
            else -> t("Không có gì trả lời ở ${f.ip}", "Nothing answers at ${f.ip}")
        }))
        if (f.vpn) out.add(Check("vpn", !(f.home && open.isEmpty()), t("VPN đang bật", "A VPN is on")))
        if (f.adb in listOf("ready", "unauthorized", "waiting", "offline")) out.add(Check("adb", f.adb == "ready", t("ADB của app: ", "The app's ADB: ") + f.adb))
        if (f.tls) out.add(Check("clock", if (code == CLOCK) false else null, t("Lỗi bảo mật (ngày giờ?)", "Secure connection error (date and time?)")))
        return out
    }

    /**
     * Looks, then decides. [err] is the error the person met (its words tell a wrong clock); [name] the TV's saved name, used
     * to look for it under another address when nothing answers. Takes about [Real.PORT_MS] plus the search, never more than ~3.5 s.
     */
    fun run(probe: Probe, ip: String, kind: String, name: String, err: String, vi: Boolean): Result {
        val start = System.currentTimeMillis()
        val ports = ports(kind)
        val pool = Executors.newFixedThreadPool(ports.size + 2) { Thread(it, "diagnose").apply { isDaemon = true } }
        try {
            val open = ports.associateWith { p -> pool.submit(Callable { probe.port(ip, p, Real.PORT_MS) }) }
            val net = pool.submit(Callable { probe.net(ip) })
            val vpn = pool.submit(Callable { probe.vpn() })
            val adb = pool.submit(Callable { probe.adb(ip) })
            fun <T> get(f: java.util.concurrent.Future<T>, or: T): T = try { f.get(Real.PORT_MS + 700L, TimeUnit.MILLISECONDS) } catch (e: Exception) { or }
            var f = Facts(kind, ip, open.mapValues { get(it.value, Port.SILENT) }, get(net, Net.NA), get(vpn, false), get(adb, null), clockError(err))
            var code = decide(f)
            var moved: Found? = null
            if ((code == OFF || code == VPN) && name.isNotBlank()) {
                moved = try { probe.find(kind, name, ip) } catch (e: Exception) { null }
                if (moved != null) { f = Facts(kind, ip, f.ports, f.net, f.vpn, f.adb, f.tls, moved); code = decide(f) }
            }
            val cs = checks(f, code, vi)
            return Result(code, ip, kind, moved, cs, System.currentTimeMillis() - start).also { WireLog.all.add("·", "diagnose $ip $kind -> $code (${it.ms} ms)") }
        } finally {
            pool.shutdownNow()
        }
    }

    fun json(r: Result, vi: Boolean): JSONObject {
        val f = Facts(r.kind, r.ip, emptyMap(), Net.NA, false, null, false, r.found)
        val (title, fix) = say(r.code, f, vi)
        return JSONObject().put("code", r.code).put("title", title).put("fix", fix).put("ip", r.ip).put("kind", r.kind).put("ms", r.ms)
            .put("actions", JSONArray(actions(r.code)))
            .put("found", r.found?.let { JSONObject().put("ip", it.ip).put("name", it.name) } ?: JSONObject.NULL)
            .put("checks", JSONArray(r.checks.map { JSONObject().put("id", it.id).put("ok", it.ok ?: JSONObject.NULL).put("note", it.note) }))
    }

    /** The conclusion as one block of words (the phone's native screens, which have a text box and no cards). */
    fun text(r: Result, vi: Boolean): String {
        val (title, fix) = say(r.code, Facts(r.kind, r.ip, emptyMap(), Net.NA, false, null, false, r.found), vi)
        return "$title\n$fix"
    }

    /** The network itself. [link]: this app's ADB link to the TV in use, when there is one, for [adb]. */
    class Real(private val link: dev.tvremote.core.adb.AdbLink? = null, private val bindUdp: (java.net.DatagramSocket) -> Unit = Diagnose.bindUdp) : Probe {
        companion object { const val PORT_MS = 1400 }

        override fun port(host: String, port: Int, ms: Int): Port = try {
            Lan.connect(host, port, ms).close(); Port.OPEN
        } catch (e: ConnectException) {
            if (e.message?.contains("refused", true) == true || e.cause?.message?.contains("refused", true) == true) Port.REFUSED else Port.SILENT
        } catch (e: Exception) { Port.SILENT }

        override fun net(host: String): Net = netOf(host, locals())
        override fun vpn(): Boolean = Lan.wifi() != null || tunnelUp()
        override fun adb(host: String): String? {
            val l = link?.takeIf { it.host == host } ?: return null
            return if (l.waitingForUser()) "waiting" else l.state()
        }

        override fun find(kind: String, name: String, not: String): Found? {
            val want = name.trim().lowercase()
            if (want.isEmpty()) return null
            val cands: List<Found> = if (kind == Remote.Kind.ANDROID) {
                val svcs = Mdns.find("_androidtvremote2._tcp", 1800, bindUdp).filter { it.ip != not }
                val pool = Executors.newFixedThreadPool(maxOf(1, svcs.size)) { Thread(it, "diagnose").apply { isDaemon = true } }
                try {
                    svcs.map { s -> pool.submit(Callable { Found(s.ip, Ssdp.castName(s.ip).ifEmpty { s.name }) }) }.mapNotNull { try { it.get(1500, TimeUnit.MILLISECONDS) } catch (e: Exception) { null } }
                } finally { pool.shutdownNow() }
            } else Ssdp.find(1800, bindUdp).filter { it.kind == kind && it.ip != not }.map { Found(it.ip, it.name) }
            return cands.filter { it.name.trim().lowercase() == want }.singleOrNull()
        }
    }

    /** Set by the phone: puts a UDP socket on the Wi-Fi under a VPN, as its scans do. */
    @Volatile var bindUdp: (java.net.DatagramSocket) -> Unit = {}

    /** A local IPv4 address with the length of its network, from an interface a TV could be reached through. */
    class Local(val ip: InetAddress, val prefix: Int)

    private val NOT_LAN = Regex("^(lo|tun|tap|utun|ppp|wg|tailscale|rmnet|ccmni|pdp|dummy|docker|veth|br-|virbr|vmnet|vboxnet|awdl|llw|ap\\d|p2p)", RegexOption.IGNORE_CASE)

    internal fun locals(): List<Local> = try {
        NetworkInterface.getNetworkInterfaces().toList().filter { ni -> try { ni.isUp && !ni.isLoopback && !NOT_LAN.containsMatchIn(ni.name) } catch (e: Exception) { false } }
            .flatMap { ni -> ni.interfaceAddresses.filter { it.address is Inet4Address }.map { Local(it.address, it.networkPrefixLength.toInt()) } }
    } catch (e: Exception) { emptyList() }

    /** A tunnel interface (not Tailscale's 100.64/10) holding an IPv4 address: a VPN carries this device's traffic. */
    private fun tunnelUp(): Boolean = try {
        NetworkInterface.getNetworkInterfaces().toList().any { ni ->
            ni.isUp && Regex("^(tun|tap|utun|ppp|wg|ipsec)", RegexOption.IGNORE_CASE).containsMatchIn(ni.name) &&
                ni.inetAddresses.toList().any { a -> a is Inet4Address && !tailnet(a) && !a.isLinkLocalAddress }
        }
    } catch (e: Exception) { false }

    private fun tailnet(a: InetAddress) = Lan.inSubnet(a, InetAddress.getByName("100.64.0.0"), 10)

    /** This device against [host]'s address, from its own addresses [mine]. */
    fun netOf(host: String, mine: List<Local>): Net {
        if (!Regex("""\d{1,3}(\.\d{1,3}){3}""").matches(host)) return Net.NA          // a name is never looked up here (Lan.home)
        val ip = try { InetAddress.getByName(host) } catch (e: Exception) { return Net.NA }
        if (ip !is Inet4Address || ip.isLoopbackAddress) return Net.NA
        if (tailnet(ip)) return if (NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().any { ni -> try { ni.isUp && ni.inetAddresses.toList().any { it is Inet4Address && tailnet(it) } } catch (e: Exception) { false } }) Net.OK else Net.TAILSCALE_OFF
        if (Lan.home(host) == null) return Net.NA
        val lan = mine.filter { Lan.home(it.ip.hostAddress ?: "") != null && !tailnet(it.ip) }
        if (lan.isEmpty()) return Net.NONE
        return if (lan.any { Lan.inSubnet(ip, it.ip, it.prefix) }) Net.OK else Net.OTHER
    }
}
