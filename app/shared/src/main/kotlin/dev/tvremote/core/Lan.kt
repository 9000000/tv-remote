package dev.tvremote.core

import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.ConcurrentHashMap

/**
 * Sockets to a TV on the home network. A VPN on the phone that takes every packet (most do, an exit node too) leaves the TV
 * out of reach while it sits one metre away; RustDesk goes around it on the Wi-Fi itself, and so does this. So does a phone
 * whose way out is its mobile data while the Wi-Fi is still on (Wi-Fi "without Internet", or Android's switch to mobile data
 * when the Wi-Fi is weak): a plain socket to 192.168.x goes to the carrier, and every saved TV said "no answer" while the
 * Wi-Fi scan, on the Wi-Fi itself, found them. Only the phone sets [wifi], and only then: elsewhere (the computer app, TV Setup)
 * every socket is a plain one, as before.
 * Only a literal private address goes around: names and Tailscale's 100.64/10 are what the VPN is there for.
 */
object Lan {
    /** The Wi-Fi (or Ethernet) under the VPN, or beside the mobile data. */
    interface Wifi {
        /** A VPN is the way out (else the mobile data): only then is "let the LAN through in the VPN app" the advice. */
        val vpn: Boolean get() = true
        /** Puts [s] on the Wi-Fi; throws when the VPN forbids it (Android's "Block connections without VPN"). */
        fun bind(s: Socket)
        /** [ip] is inside the Wi-Fi's own subnet: meant for the room, not for a network behind the VPN. */
        fun onLink(ip: InetAddress): Boolean
        fun open(url: URL): URLConnection
    }

    /** Asked at each connect (VPNs and networks come and go): null when the Wi-Fi is the way out, or there is no Wi-Fi. */
    @Volatile var wifi: () -> Wifi? = { null }

    /** In the error when neither way reached the TV with a VPN up: Errors.say asks to let the LAN through in the VPN app. */
    const val BLOCKED = "the home network is out of reach under the VPN"

    private val viaWifi = ConcurrentHashMap<String, Boolean>()        // which way reached each address last: tried first next time

    /** A private address written as one (10/8, 172.16/12, 192.168/16, 169.254/16, IPv6 link-local and ULA), else null. */
    fun home(host: String): InetAddress? {
        val h = host.removePrefix("[").removeSuffix("]")
        if (!h.contains(':') && !Regex("""\d{1,3}(\.\d{1,3}){3}""").matches(h)) return null   // a name: never looked up here
        val a = try { InetAddress.getByName(h) } catch (e: Exception) { return null }
        val b = a.address
        val private = if (b.size == 4) {
            val x = b[0].toInt() and 255; val y = b[1].toInt() and 255
            x == 10 || (x == 172 && y in 16..31) || (x == 192 && y == 168) || (x == 169 && y == 254)
        } else (b[0].toInt() and 0xfe) == 0xfc || ((b[0].toInt() and 255) == 0xfe && (b[1].toInt() and 0xc0) == 0x80)
        return if (private) a else null
    }

    /** [ip] is in [net]/[prefix]. */
    fun inSubnet(ip: InetAddress, net: InetAddress, prefix: Int): Boolean {
        val a = ip.address; val b = net.address
        if (a.size != b.size || prefix < 0 || prefix > a.size * 8) return false
        for (i in a.indices) {
            val bits = (prefix - i * 8).coerceIn(0, 8)
            val mask = (0xff shl (8 - bits)) and 0xff
            if ((a[i].toInt() and mask) != (b[i].toInt() and mask)) return false
        }
        return true
    }

    /**
     * A socket connected to [host]:[port] within [ms]. With a VPN up and a home address: the Wi-Fi first when the address is
     * on it (or worked last time), the VPN's way otherwise, then the other. [made] gets each socket before it connects, so
     * whoever waits on it can close it from another thread.
     */
    @Throws(IOException::class)
    fun connect(host: String, port: Int, ms: Int, made: (Socket) -> Unit = {}): Socket {
        val w = wifi()
        val ip = if (w == null) null else home(host)
        if (w == null || ip == null) return dial(host, port, ms, null, made)
        val first = viaWifi[host] ?: try { w.onLink(ip) } catch (e: Exception) { false }
        var err: IOException? = null
        for (around in listOf(first, !first)) {
            try {
                return dial(host, port, ms, if (around) w else null, made).also { viaWifi[host] = around }
            } catch (e: IOException) { if (err == null) err = e }
        }
        if (!w.vpn) throw err!!
        throw ConnectException("$BLOCKED ($host:$port: $err)").apply { initCause(err) }
    }

    /** A connection to a TV's own web server ([url]'s host on the home network): on the Wi-Fi under a VPN, as [connect]. */
    fun open(url: URL): URLConnection {
        val w = wifi()
        val ip = if (w == null) null else home(url.host)
        if (w == null || ip == null) return url.openConnection()
        val around = viaWifi[url.host] ?: try { w.onLink(ip) } catch (e: Exception) { false }
        return if (around) w.open(url) else url.openConnection()
    }

    internal var dial: (String, Int, Int, Wifi?, (Socket) -> Unit) -> Socket = ::socket   // tests: which way was tried

    private fun socket(host: String, port: Int, ms: Int, w: Wifi?, made: (Socket) -> Unit): Socket {
        val s = Socket()
        try {
            if (w != null) try { w.bind(s) } catch (e: Exception) { throw IOException("the VPN forbids the Wi-Fi: $e", e) }
            made(s)
            s.connect(InetSocketAddress(host, port), ms)
            return s
        } catch (e: IOException) {
            try { s.close() } catch (ignored: IOException) { }
            throw e
        }
    }

    internal fun forget() = viaWifi.clear()                           // tests
}
