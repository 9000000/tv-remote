package dev.tvremote.phone

import android.content.Context
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

// Small network helpers: is a port open, TVs announcing themselves on the Wi-Fi, the name an Android TV gives itself.

/** True when something accepts a TCP connection at [ip]:[port] within [timeoutMs]. */
internal fun open(ip: String, port: Int, timeoutMs: Int, via: android.net.Network? = null): Boolean =
    try { (via?.socketFactory?.createSocket() ?: Socket()).use { it.connect(InetSocketAddress(ip, port), timeoutMs) }; true } catch (e: Exception) { false }

/** The Wi-Fi (or Ethernet) itself, under any VPN: with Tailscale on (an exit node above all) the home network is not reachable
 *  through the default route, so the scan's sockets go straight onto it. Null when there is none, or the VPN forbids it. */
internal fun Context.lanNetwork(): android.net.Network? = try {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    @Suppress("DEPRECATION")
    cm.allNetworks.firstOrNull { n ->
        cm.getNetworkCapabilities(n)?.let { c ->
            !c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) &&
                (c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET))
        } == true
    }?.takeIf { n -> try { java.net.Socket().use { n.bindSocket(it) }; true } catch (e: Exception) { false } }   // a VPN without bypass: EPERM
} catch (e: Exception) { null }

/** Listens for Android TVs announcing their remote service on the Wi-Fi (mDNS); [onTv] gets each one's address. Returns how to stop. */
internal fun Context.announced(onTv: (String) -> Unit): () -> Unit {
    try {
        val nsd = getSystemService(Context.NSD_SERVICE) as android.net.nsd.NsdManager
        val queue = java.util.concurrent.LinkedBlockingQueue<android.net.nsd.NsdServiceInfo>()
        var busy = false
        lateinit var next: () -> Unit
        next = {                                                  // old Androids resolve one at a time
            val s = queue.poll()
            if (s == null) busy = false
            else {
                busy = true
                try {
                    @Suppress("DEPRECATION")
                    nsd.resolveService(s, object : android.net.nsd.NsdManager.ResolveListener {
                        override fun onResolveFailed(i: android.net.nsd.NsdServiceInfo, code: Int) { next() }
                        override fun onServiceResolved(i: android.net.nsd.NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            (i.host as? Inet4Address)?.hostAddress?.let(onTv)
                            next()
                        }
                    })
                } catch (e: Exception) { busy = false }
            }
        }
        val listener = object : android.net.nsd.NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(type: String, code: Int) {}
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onServiceLost(s: android.net.nsd.NsdServiceInfo) {}
            override fun onServiceFound(s: android.net.nsd.NsdServiceInfo) { synchronized(queue) { queue.add(s); if (!busy) next() } }
        }
        nsd.discoverServices("_androidtvremote2._tcp.", android.net.nsd.NsdManager.PROTOCOL_DNS_SD, listener)
        return { try { nsd.stopServiceDiscovery(listener) } catch (ignored: Exception) { } }
    } catch (e: Exception) {
        return {}
    }
}
