package dev.tvremote.setup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.tvremote.core.adb.Adb
import dev.tvremote.core.ontv.Tailscale

/**
 * Switches Tailscale back on after the Play Store updates it. An update kills the app and nothing starts it again (always-on
 * VPN only acts at boot), so a TV reached over Tailscale drops out until someone at home opens it. Only a Tailscale that was
 * connected when the update came is reconnected: one switched off on purpose stays off. From RemoteService, like [Guard].
 */
internal class TailscaleWatch(private val c: Context, private val adb: () -> Adb?) {
    private val ui = Handler(Looper.getMainLooper())
    private val cm = c.getSystemService(ConnectivityManager::class.java)
    @Volatile private var vpnUp = false
    @Volatile private var vpnLost = 0L                                 // when a VPN last went away: the update kills it first

    private val vpn = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(n: Network) { vpnUp = true }
        override fun onLost(n: Network) { vpnUp = false; vpnLost = System.currentTimeMillis() }
    }

    private val replaced = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            if (i.data?.schemeSpecificPart != Tailscale.PKG) return
            Log.i("TailscaleWatch", "updated; vpn up=$vpnUp, lost ${(System.currentTimeMillis() - vpnLost) / 1000}s ago")
            if (vpnUp || System.currentTimeMillis() - vpnLost > 5 * 60_000) return   // still up, or it was off before the update
            ui.postDelayed({ Thread { reconnect() }.start() }, 3000)  // let the new version settle before poking it
        }
    }

    fun start() {
        try {
            cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), vpn)
        } catch (e: Exception) { }
        val f = IntentFilter(Intent.ACTION_PACKAGE_REPLACED).apply { addDataScheme("package") }
        if (Build.VERSION.SDK_INT >= 33) c.registerReceiver(replaced, f, Context.RECEIVER_NOT_EXPORTED) else c.registerReceiver(replaced, f)
    }

    fun stop() {
        try { cm.unregisterNetworkCallback(vpn) } catch (e: Exception) { }
        try { c.unregisterReceiver(replaced) } catch (e: Exception) { }
        ui.removeCallbacksAndMessages(null)
    }

    private fun reconnect() {
        for (attempt in 0 until 3) {
            if (vpnUp) return
            try {                                                      // the same nudge as Tailscale.start; without ADB the app sends it
                adb()?.shell("am broadcast -a com.tailscale.ipn.CONNECT_VPN -n ${Tailscale.PKG}/.IPNReceiver")
                    ?: c.sendBroadcast(Intent("com.tailscale.ipn.CONNECT_VPN").setClassName(Tailscale.PKG, Tailscale.PKG + ".IPNReceiver"))
            } catch (e: Exception) { }
            Thread.sleep(10_000)
        }
    }
}
