package dev.tvremote.desktop

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Waking a TV from standby with Wake-on-LAN: the magic packet from here, and through a relay machine
 * at home over SSH when one is saved (the packet only reaches the TV from its own network), then waiting for the TV.
 */
object Wake {
    class Result(val ok: Boolean, val log: String)

    fun wake(host: String, tv: JSONObject, waitS: Int): Result {
        val log = StringBuilder()
        val mac = tv.optString("mac")
        if (host.isNotEmpty() && Discover.portOpen(host, 6466, 1500)) return Result(true, "TV already reachable")
        if (mac.isEmpty()) return Result(false, "No MAC address saved for this TV (save the TV in the web UI while ADB is connected).")
        log.append("local broadcast: ${sendLocal(mac, tv.optString("lan"))} packets\n")
        val relayHost = tv.optString("relay_host")
        if (relayHost.isNotEmpty()) {
            try {
                val hex = mac.filter { it.isLetterOrDigit() }.take(12)      // the packet sent there: Python 3 is all the relay needs
                val cmd = tv.optString("relay_cmd").ifEmpty { "python3 -c 'import socket;p=b\"\\xff\"*6+bytes.fromhex(\"$hex\")*16;" +
                    "s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM);s.setsockopt(socket.SOL_SOCKET,socket.SO_BROADCAST,1);" +
                    "[s.sendto(p,(\"255.255.255.255\",n)) for n in (9,7) for _ in range(3)]'" }
                val p = ProcessBuilder("ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=8", "-l", tv.optString("relay_user"), relayHost, cmd).redirectErrorStream(true).start()
                val ok = p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0
                if (p.isAlive) p.destroyForcibly()
                log.append("relay via $relayHost: ${if (ok) "ok" else "FAILED"} ${p.inputStream.bufferedReader().readText().trim()}\n")
            } catch (e: Exception) {
                log.append("relay failed: $e\n")
            }
        } else {
            log.append("no relay machine configured: this only works when this computer is on the TV's own network\n")
        }
        for (i in 0 until waitS / 3) {
            if (host.isNotEmpty() && Discover.portOpen(host, 6466, 1500)) return Result(true, log.append("TV is up after ~${i * 3}s").toString())
            Thread.sleep(3000)
        }
        return Result(false, log.append("TV did not come back. Check that 'Remote start' is enabled on the TV and that a machine on its network relayed the packet.").toString())
    }

    private fun sendLocal(mac: String, lan: String): Int {
        val raw = mac.replace(":", "").replace("-", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val pkt = ByteArray(6) { 0xff.toByte() } + (1..16).flatMap { raw.toList() }
        val targets = listOf("255.255.255.255") + if (lan.count { it == '.' } == 3) listOf(lan.substringBeforeLast('.') + ".255") else emptyList()
        var sent = 0
        DatagramSocket().use { s ->
            s.broadcast = true
            for (ip in targets) for (port in listOf(9, 7)) try {
                repeat(3) { s.send(DatagramPacket(pkt, pkt.size, InetAddress.getByName(ip), port)); sent++; Thread.sleep(100) }
            } catch (ignored: Exception) {
            }
        }
        return sent
    }
}
