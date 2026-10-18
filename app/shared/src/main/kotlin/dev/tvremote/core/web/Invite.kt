package dev.tvremote.core.web
import org.json.JSONObject
import java.io.IOException
import dev.tvremote.core.ontv.RulesClient
import dev.tvremote.core.ontv.Setup
import dev.tvremote.core.ontv.SetupNeed
import dev.tvremote.core.ontv.Tailscale

/**
 * Share the remote (the ⋯ More keys entry): a QR another phone scans to open the TV web page already paired, with no code to
 * type. TV Setup hands out one invite at a time, good for ten minutes and one browser (RemoteService.newInvite); only a device
 * that can already do everything here asks for one: through the shell (TrustReceiver, like a pass) or from a paired page.
 */
object Invite {
    val SCOPES = listOf("full", "remote", "view")

    /**
     * [host] is how this device reaches the TV; [scope]: what the browser that opens the link may do (full, remote, view:
     * Scope.kt in TV Setup). The answer: {"url", "warn"} or, without a TV Setup that has invites,
     * {"setup": "missing" | "old", "have", "min"} (the page then shows only the QR that downloads the phone app).
     */
    @Throws(IOException::class)
    fun share(sh: (String) -> String, host: String, scope: String): JSONObject {
        val sc = if (scope in SCOPES) scope else "remote"
        val have = SetupNeed.have(sh)
        if (!SetupNeed.ok(have, "invite")) return JSONObject().put("setup", if (have.isEmpty()) "missing" else "old").put("have", have).put("min", SetupNeed.MIN["invite"])
        val out = sh("am broadcast --include-stopped-packages -n ${RulesClient.SETUP}/.TrustReceiver --es want invite --es scope $sc 2>&1")
        val m = Regex("data=\"([0-9a-f]{32}) ?([0-9.]*)\"").find(out) ?: throw IOException("TV Setup did not answer")
        // the page is served by TV Setup's service; a receiver may not start it from the background, the shell may
        sh(RulesClient.START)
        return link(m.groupValues[2], m.groupValues[1], host)
    }

    /**
     * The link in the QR: the TV's home network address when it has one (a phone beside it is on that Wi-Fi), else the
     * address this device used. warn: "tailscale" when this device is away (the scanning phone must be on the TV's Wi-Fi),
     * "tailscale_only" when the link itself is a Tailscale address (the scanning phone needs Tailscale too).
     * The invite goes after # so it is never sent in a request line, only by the page, once.
     */
    fun link(lan: String, invite: String, host: String): JSONObject {
        val at = lan.ifEmpty { host }
        val warn = if (Tailscale.isAddress(at)) "tailscale_only" else if (Tailscale.isAddress(host)) "tailscale" else ""
        return JSONObject().put("url", "http://$at:8765/#invite=$invite").put("warn", warn)
    }
}
