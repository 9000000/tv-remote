package dev.tvremote.core.adb
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * A small mDNS ("Bonjour") browser, for the two services Android's Wireless debugging announces: `_adb-tls-pairing._tcp`
 * (the port that takes the 6-digit code) and `_adb-tls-connect._tcp` (the port to connect to, which changes every time
 * Wireless debugging is switched on). The question is sent from an ordinary port with the "unicast reply" bit set, so the
 * TV answers this socket alone and Android needs no multicast lock (as in Ssdp.kt).
 */
object Mdns {
    const val PAIRING = "_adb-tls-pairing._tcp"
    const val CONNECT = "_adb-tls-connect._tcp"

    class Service(val name: String, val ip: String, val port: Int)

    private const val PTR = 12
    private const val A = 1
    private const val SRV = 33

    class Record(val name: String, val type: Int, val target: String = "", val port: Int = 0, val ip: String = "")

    /** A question for [name] of record type [type], asking for the answer to be sent straight back. */
    fun query(name: String, type: Int): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(byteArrayOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0))     // id 0, a standard query, one question
        for (label in name.split('.').filter { it.isNotEmpty() }) {
            val b = label.toByteArray(Charsets.UTF_8)
            o.write(b.size); o.write(b)
        }
        o.write(0)
        o.write(type shr 8); o.write(type and 255)
        o.write(0x80); o.write(1)                                    // class IN, with the unicast-reply bit
        return o.toByteArray()
    }

    /** Every PTR, SRV and A record of an answer (in any of its sections); empty for anything that is not one. */
    fun parse(p: ByteArray, len: Int = p.size): List<Record> {
        val out = ArrayList<Record>()
        try {
            fun u16(at: Int) = (p[at].toInt() and 255 shl 8) or (p[at + 1].toInt() and 255)
            if (len < 12) return out
            var at = 12
            repeat(u16(4)) {                                         // questions: skipped
                at = skipName(p, at) + 4
            }
            val count = u16(6) + u16(8) + u16(10)
            repeat(count) {
                val (name, afterName) = readName(p, at, len)
                val type = u16(afterName)
                val rdlen = u16(afterName + 8)
                val data = afterName + 10
                if (data + rdlen > len) return out
                when (type) {
                    PTR -> out.add(Record(name, PTR, target = readName(p, data, len).first))
                    SRV -> out.add(Record(name, SRV, target = readName(p, data + 6, len).first, port = u16(data + 4)))
                    A -> if (rdlen == 4) out.add(Record(name, A, ip = (0 until 4).joinToString(".") { (p[data + it].toInt() and 255).toString() }))
                }
                at = data + rdlen
            }
        } catch (ignored: IndexOutOfBoundsException) { }             // a cut-off or odd packet: what was read so far stands
        return out
    }

    private fun skipName(p: ByteArray, from: Int): Int {
        var at = from
        while (true) {
            val n = p[at].toInt() and 255
            if (n == 0) return at + 1
            if (n and 0xC0 == 0xC0) return at + 2
            at += n + 1
        }
    }

    /** A name with its compression pointers followed, and where the name ends where it was first met. */
    private fun readName(p: ByteArray, from: Int, len: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var at = from
        var end = -1
        var jumps = 0
        while (true) {
            val n = p[at].toInt() and 255
            if (n == 0) { if (end < 0) end = at + 1; break }
            if (n and 0xC0 == 0xC0) {
                if (end < 0) end = at + 2
                at = (n and 0x3F shl 8) or (p[at + 1].toInt() and 255)
                if (++jumps > 20 || at >= len) throw IndexOutOfBoundsException("bad name")
                continue
            }
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(p, at + 1, n, Charsets.UTF_8))
            at += n + 1
        }
        return sb.toString() to end
    }

    /** Joins what several packets said into services of [type]: the instance's SRV gives the port, the host's A record the address. */
    class Collector(private val type: String) {
        private val instances = LinkedHashSet<String>()
        private val srv = HashMap<String, Record>()
        private val addr = HashMap<String, String>()
        private val sender = HashMap<String, String>()               // instance → who sent the packet that named it (the address, when no A record came)

        fun add(records: List<Record>, from: String) {
            for (r in records) when (r.type) {
                PTR -> if (r.name.equals("$type.local", true)) { instances.add(r.target); sender.getOrPut(r.target) { from } }
                SRV -> { srv[r.name] = r; sender.getOrPut(r.name) { from } }
                A -> addr[r.name.lowercase()] = r.ip
            }
        }

        fun found(): List<Service> = instances.mapNotNull { n ->
            val s = srv[n] ?: return@mapNotNull null
            Service(n.removeSuffix(".$type.local"), addr[s.target.lowercase()] ?: sender[n] ?: return@mapNotNull null, s.port)
        }

        /** Instances that were named but whose port is not known yet, to ask for. */
        fun unresolved(): List<String> = instances.filter { srv[it] == null }
        fun hosts(): List<String> = srv.values.map { it.target }.filter { !addr.containsKey(it.lowercase()) }
    }

    /** Every [type] service that answers within [ms], each once; [bind] puts the socket on the Wi-Fi itself (Android, with a VPN taking the traffic). */
    fun find(type: String, ms: Int = 2500, bind: (DatagramSocket) -> Unit = {}, each: (Service) -> Unit = {}): List<Service> {
        val col = Collector(type)
        val said = HashSet<String>()
        try {
            MulticastSocket(null).use { s ->
                s.reuseAddress = false
                s.bind(java.net.InetSocketAddress(0))
                bind(s)
                s.soTimeout = 250
                s.timeToLive = 255
                val group = InetAddress.getByName("224.0.0.251")
                val ifaces = interfaces()
                fun ask(name: String, t: Int) {
                    val q = query(name, t)
                    val sent = ifaces.count { ni ->
                        try { s.networkInterface = ni; s.send(DatagramPacket(q, q.size, group, 5353)); true } catch (e: Exception) { false }
                    }
                    if (sent == 0) try { s.send(DatagramPacket(q, q.size, group, 5353)) } catch (ignored: Exception) { }
                }
                ask("$type.local", PTR)
                val start = System.currentTimeMillis()
                val until = start + ms
                var again = start + 700
                val buf = ByteArray(9000)
                while (System.currentTimeMillis() < until) {
                    val p = DatagramPacket(buf, buf.size)
                    try { s.receive(p) } catch (e: SocketTimeoutException) { }
                    if (p.length > 0 && p.address != null) col.add(parse(p.data, p.length), p.address.hostAddress ?: "")
                    p.length = buf.size
                    for (f in col.found()) if (said.add(f.name + "@" + f.ip + ":" + f.port)) each(f)
                    if (System.currentTimeMillis() >= again) {            // UDP: ask again, and for what an answer left out
                        again += 700
                        ask("$type.local", PTR)
                        for (n in col.unresolved()) ask(n, SRV)
                        for (h in col.hosts()) ask(h, A)
                    }
                }
            }
        } catch (ignored: Exception) { }                             // no network, or multicast blocked: nothing found this way
        return col.found()
    }

    private fun interfaces(): List<NetworkInterface> = try {
        NetworkInterface.getNetworkInterfaces().toList().filter { ni ->
            try { ni.isUp && !ni.isLoopback && ni.supportsMulticast() && ni.inetAddresses.toList().any { it is Inet4Address } } catch (e: Exception) { false }
        }
    } catch (e: Exception) { emptyList() }
}
