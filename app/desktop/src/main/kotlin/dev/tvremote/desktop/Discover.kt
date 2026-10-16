package dev.tvremote.desktop

import dev.tvremote.core.Remote
import dev.tvremote.core.Ssdp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Finding TVs: hosts answering on the remote (6466) or ADB (5555) port, on this computer's networks and the tailnet; LG and
 * Samsung TVs by their answer to an SSDP search (Ssdp.kt). Each row says its "kind".
 */
object Discover {
    val TAILSCALE = listOf("/Applications/Tailscale.app/Contents/MacOS/Tailscale", "/opt/homebrew/bin/tailscale", "/usr/local/bin/tailscale",
        "/usr/bin/tailscale", "C:\\Program Files\\Tailscale\\tailscale.exe").firstOrNull { File(it).canExecute() } ?: "tailscale"

    /** [unknown]: collects hosts the OS gave up on ("Host is down"...) instead of a plain "no": the port refused means the host is there. */
    fun portOpen(ip: String, port: Int, timeoutMs: Int = 700, unknown: MutableMap<String, String>? = null): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs) }
        true
    } catch (e: ConnectException) {
        if (unknown != null && e.message?.contains("refused", true) != true) unknown[ip] = e.message ?: "error"
        false
    } catch (e: Exception) {
        false
    }

    /** This computer's private IPv4 addresses, each with the /24 around it (where a TV on the same Wi-Fi would be). */
    fun localNetworks(): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (nif in NetworkInterface.getNetworkInterfaces().toList()) {
            if (!nif.isUp || nif.isLoopback) continue
            for (a in nif.inetAddresses.toList()) {
                if (a !is Inet4Address || !a.isSiteLocalAddress) continue
                val ip = a.hostAddress
                if (ip.startsWith("100.")) continue
                out.add(ip to ip.substringBeforeLast('.') + ".0/24")
            }
        }
        return out.distinctBy { it.first }.sortedBy { it.first }
    }

    /** The name an Android TV gives itself, from its Chromecast service. "" when it does not answer. */
    fun castName(ip: String): String = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(ip, 8008), 800)
            s.soTimeout = 1500
            s.getOutputStream().write("GET /setup/eureka_info HTTP/1.1\r\nHost: $ip:8008\r\nConnection: close\r\n\r\n".toByteArray())
            val got = StringBuilder()
            val buf = ByteArray(2048)
            var name = ""
            while (got.length < 20000) {                              // the TV may keep the connection open: stop at the name
                val n = s.getInputStream().read(buf)
                if (n <= 0) break
                got.append(String(buf, 0, n, Charsets.UTF_8))
                Regex("\"name\"\\s*:\\s*\"([^\"]{1,60})\"").find(got)?.let { name = it.groupValues[1] }
                if (name.isNotEmpty()) break
            }
            name
        }
    } catch (e: Exception) {
        ""
    }

    class Peer(val name: String, val lan: String)

    /** Online Android peers on the tailnet, by Tailscale IP; [Peer.lan]: its home IP, when this computer reaches it directly there. */
    fun tailscalePeers(): Map<String, Peer> = try {
        val p = ProcessBuilder(TAILSCALE, "status", "--json").redirectError(ProcessBuilder.Redirect.DISCARD).also { it.environment()["TAILSCALE_BE_CLI"] = "1" }.start()
        val text = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(8, TimeUnit.SECONDS)) p.destroyForcibly()
        val peers = JSONObject(text).optJSONObject("Peer") ?: JSONObject()
        peers.keys().asSequence().mapNotNull { peers.optJSONObject(it) }
            .filter { it.optString("OS") == "android" && it.optBoolean("Online") && (it.optJSONArray("TailscaleIPs")?.length() ?: 0) > 0 }
            .associate { p -> p.getJSONArray("TailscaleIPs").getString(0) to
                Peer(p.optString("HostName"), Regex("^([0-9.]+):\\d+$").find(p.optString("CurAddr"))?.groupValues?.get(1)?.takeIf { Server.PRIVATE.matches(it) } ?: "") }
    } catch (e: Exception) {
        emptyMap()
    }

    /** One scan in the background: the page asks for it again and again and shows each TV as soon as it answers. */
    private class Scan {
        val found = ConcurrentHashMap<String, JSONObject>()
        @Volatile var networks: List<String> = emptyList()
        @Volatile var done = false
        @Volatile var lanOf: Map<String, String> = emptyMap()         // Tailscale IP to home IP, for the peers reached at home
        /** A TV found at both its home IP and its Tailscale IP is one row: the home one, which also names the other. */
        fun json(): JSONObject {
            val rows = found.values.mapNotNull { f ->
                val ip = f.optString("ip")
                val lan = lanOf[ip] ?: ""
                if (lan.isNotEmpty() && found.containsKey(lan)) null
                else JSONObject(f.toString()).also { r -> lanOf.entries.firstOrNull { it.value == ip && found.containsKey(it.key) }?.let { r.put("tailscale", it.key) } }
            }
            return JSONObject().put("networks", JSONArray(networks)).put("done", done)
                .put("found", JSONArray(rows.sortedWith(compareBy({ it.optString("via") }, { it.optString("ip") }))))
        }
    }
    @Volatile private var scan: Scan? = null

    /** [fresh]: start a new scan unless one is running; otherwise what the current (or last) one has found so far. */
    @Synchronized fun poll(fresh: Boolean): JSONObject {
        val cur = scan
        if (cur == null || fresh && cur.done) {
            val s = Scan(); scan = s
            Thread({ try { run(s); if (s.found.isEmpty()) run(s) } finally { s.done = true } }, "discover").apply { isDaemon = true }.start()   // now and then a first scan misses TVs a second one finds
            return s.json()
        }
        return cur.json()
    }

    private fun run(s: Scan) {
        val found = s.found
        val unknown = ConcurrentHashMap<String, String>()
        val pool = Executors.newFixedThreadPool(120) { Thread(it, "discover").apply { isDaemon = true } }
        fun probe(ip: String, via: String, name: String) = pool.submit {
            val remote = portOpen(ip, 6466, unknown = unknown)
            val adb = portOpen(ip, 5555, unknown = unknown)
            if (remote || adb) found[ip] = JSONObject().put("ip", ip).put("remote", remote).put("adb", adb).put("via", via).put("name", castName(ip).ifEmpty { name })
                .put("kind", Remote.Kind.ANDROID)
        }
        try {
            val nets = localNetworks()
            s.networks = nets.map { it.second }
            val peers = tailscalePeers()
            s.lanOf = peers.filterValues { it.lan.isNotEmpty() }.mapValues { it.value.lan }
            val jobs = ArrayList<java.util.concurrent.Future<*>>()
            jobs.add(pool.submit { Ssdp.find { f -> if (!found.containsKey(f.ip)) found[f.ip] = JSONObject().put("ip", f.ip).put("remote", true).put("adb", false).put("via", "lan").put("name", f.name).put("kind", f.kind) } })
            for ((me, _) in nets) for (h in 1..254) { val ip = me.substringBeforeLast('.') + ".$h"; if (ip != me) jobs.add(probe(ip, "lan", "")) }
            for ((ip, p) in peers) jobs.add(probe(ip, "tailscale", p.name))
            jobs.forEach { it.get() }
            // a first scan asks the whole network at once who has each address, and the Mac answers "Host is down" for many
            // hosts without waiting, the TV often among them: by now the answers are in, so those are asked once more
            val again = unknown.keys.filter { !found.containsKey(it) }
            if (again.isNotEmpty()) {
                Thread.sleep(1000)
                again.map { probe(it, if (it in peers) "tailscale" else "lan", peers[it]?.name ?: "") }.forEach { it.get() }
            }
            if (found.isEmpty()) println("scan found nothing: tailscale ${peers.ifEmpty { "no peers" }}; errors ${unknown.values.groupingBy { it }.eachCount()}")
        } finally {
            pool.shutdownNow()
        }
    }
}
