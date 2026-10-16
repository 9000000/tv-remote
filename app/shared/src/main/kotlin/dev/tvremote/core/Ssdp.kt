package dev.tvremote.core

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL

/**
 * Finding LG and Samsung TVs on the home network: they answer an SSDP search (UPnP's "who is there?") for their remote
 * services, which a port scan would only find slowly. The answers come back to this socket alone, so Android needs no
 * multicast lock. Android TVs are found by their ports as before (Discover.kt, the phone's scan).
 */
object Ssdp {
    class Found(val ip: String, val kind: String, val name: String)

    private val SEARCH = listOf("urn:lge-com:service:webos-second-screen:1", "urn:samsung.com:device:RemoteControlReceiver:1")

    /** Every TV that answers within [ms], each once, with the name it gives itself; [each] as soon as one is known. */
    /** [bind]: puts the socket on the Wi-Fi itself (Android, with a VPN such as Tailscale taking the traffic). */
    fun find(ms: Int = 2500, bind: (DatagramSocket) -> Unit = {}, each: (Found) -> Unit = {}): List<Found> {
        val seen = LinkedHashMap<String, Found>()
        try {
            DatagramSocket().use { s ->
                bind(s)
                s.soTimeout = 300
                val to = InetAddress.getByName("239.255.255.250")
                repeat(2) {                                           // UDP: a second ask for the one that got lost
                    for (st in SEARCH) {
                        val m = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $st\r\n\r\n".toByteArray()
                        s.send(DatagramPacket(m, m.size, to, 1900))
                    }
                }
                val until = System.currentTimeMillis() + ms
                val buf = ByteArray(4096)
                while (System.currentTimeMillis() < until) {
                    val p = DatagramPacket(buf, buf.size)
                    try { s.receive(p) } catch (e: SocketTimeoutException) { continue }
                    val ip = p.address.hostAddress ?: continue
                    if (seen.containsKey(ip)) continue
                    val head = String(p.data, 0, p.length, Charsets.UTF_8)
                    val kind = kind(head) ?: continue
                    val f = Found(ip, kind, name(ip, kind, Regex("(?im)^location:\\s*(\\S+)").find(head)?.groupValues?.get(1)))
                    seen[ip] = f
                    each(f)
                }
            }
        } catch (ignored: Exception) {                                // no network, or multicast blocked: nothing found this way
        }
        return seen.values.toList()
    }

    /** LG or Samsung, from the answer's headers. */
    fun kind(head: String): String? = when {
        Regex("(?i)lge-com|webos").containsMatchIn(head) -> Remote.Kind.LG
        Regex("(?i)samsung|tizen").containsMatchIn(head) -> Remote.Kind.SAMSUNG
        else -> null
    }

    /** The name the person gave the TV: Samsung's REST description, else the UPnP description's friendlyName. */
    private fun name(ip: String, kind: String, location: String?): String {
        if (kind == Remote.Kind.SAMSUNG) get("http://$ip:8001/api/v2/")?.let { t ->
            try { JSONObject(t).optJSONObject("device")?.optString("name")?.takeIf { it.isNotEmpty() }?.let { return it.removePrefix("[TV] ") } } catch (ignored: Exception) { }
        }
        val xml = location?.let { get(it) } ?: return ""
        return Regex("<friendlyName>([^<]{1,80})</friendlyName>").find(xml)?.groupValues?.get(1)?.trim()?.removePrefix("[TV] ") ?: ""
    }

    private fun get(at: String): String? = try {
        val c = URL(at).openConnection() as HttpURLConnection
        c.connectTimeout = 1200; c.readTimeout = 1500
        try { if (c.responseCode != 200) null else c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) } } finally { c.disconnect() }
    } catch (e: Exception) { null }
}
