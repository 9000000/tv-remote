package dev.tvremote.home

import org.json.JSONObject

/**
 * The QR widgets of TV Home ("qrs" in [HomeConfig]): each is a template (a small form) that builds the text the code holds, plus an
 * optional caption. Whatever the person types is kept as fields, so a client can open the widget again to change it; TV Home builds the
 * payload from them with [payload] (js/home_qr.js `homeQrPayload` is the same rule, tests/home_qr_cases.json holds the cases both read).
 *   wifi     ssid, pass, sec (WPA / WEP / nopass), hidden → WIFI:T:WPA;S:..;P:..;H:true;;  (\ ; , : " escaped); no password = an open network
 *   link     url (https:// is put in front of a bare address)
 *   text     text, as typed: the raw payload is the widget
 *   tel      num → tel:+84...        sms  num, body → sms:num?body=..        mail  to, subject, body → mailto:to?subject=..&body=..
 *   contact  name, tel, email → MECARD:N:..;TEL:..;EMAIL:..;;
 * "show" (wifi only): also write the password under the code, default off. The password lives only in TV Home's config on the TV.
 */
object HomeQr {
    val TEMPLATES = listOf("wifi", "link", "text", "tel", "sms", "mail", "contact")
    val SECURITY = listOf("WPA", "WEP", "nopass")
    const val MAX_BYTES = 271                                         // what Qr.kt (version 10, level L) holds
    const val FIELD_MAX = 250
    const val CAP_MAX = 60
    const val QR_MAX = 4                                          // "qr1" .. "qr4", each is a widget of its own in "widgets"
    val KEYS = (1..QR_MAX).map { "qr$it" }

    /** Which fields each template has (the others are dropped by [widget]). */
    val FIELDS: Map<String, List<String>> = mapOf(
        "wifi" to listOf("ssid", "pass", "sec", "hidden"), "link" to listOf("url"), "text" to listOf("text"), "tel" to listOf("num"),
        "sms" to listOf("num", "body"), "mail" to listOf("to", "subject", "body"), "contact" to listOf("name", "tel", "email"))

    /** [s] with \ ; , : " each behind a backslash (the Wi-Fi and MECARD formats). */
    fun esc(s: String): String = s.replace(Regex("""([\\;,:"])"""), "\\\\$1")

    /** [s] percent-encoded as UTF-8, keeping the letters, digits and - _ . ~ (the same as the page; encodeURIComponent keeps more). */
    fun pct(s: String): String = buildString {
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xff
            if (c in 0x30..0x39 || c in 0x41..0x5a || c in 0x61..0x7a || c == 0x2d || c == 0x5f || c == 0x2e || c == 0x7e) append(c.toChar())
            else append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
        }
    }

    /** An address that already says what it is: any scheme:// or a few that have none ("example.com:8080" is a host, not a scheme). */
    private val SCHEME = Regex("^(?:[A-Za-z][A-Za-z0-9+.-]*://|(?:mailto|tel|sms|smsto|geo|whatsapp|tg|viber|zalo):)", RegexOption.IGNORE_CASE)
    private fun str(f: JSONObject, k: String) = f.optString(k, "")
    private fun num(s: String) = s.filter { it in '0'..'9' || it == '+' || it == '*' || it == '#' }

    /** The text the code holds; "" when the template lacks what it cannot do without (a network's name, an address, a number). */
    fun payload(tpl: String, f: JSONObject): String = when (tpl) {
        "wifi" -> {
            val ssid = str(f, "ssid"); val pass = str(f, "pass")
            val sec = str(f, "sec").let { if (it == "nopass" || pass.isEmpty()) "nopass" else if (it == "WEP") "WEP" else "WPA" }   // no password = open, whatever was chosen; a password left behind on "open" is not sent
            if (ssid.isEmpty()) "" else "WIFI:T:$sec;S:${esc(ssid)};" + (if (sec == "nopass") "" else "P:${esc(pass)};") + (if (f.optBoolean("hidden")) "H:true;" else "") + ";"
        }
        "link" -> str(f, "url").trim().let { if (it.isEmpty()) "" else if (SCHEME.containsMatchIn(it)) it else "https://$it" }
        "text" -> str(f, "text")
        "tel" -> num(str(f, "num")).let { if (it.isEmpty()) "" else "tel:$it" }
        "sms" -> num(str(f, "num")).let { n -> val b = str(f, "body"); if (n.isEmpty()) "" else "sms:$n" + if (b.isEmpty()) "" else "?body=" + pct(b) }
        "mail" -> str(f, "to").trim().let { to ->
            val q = listOf("subject" to str(f, "subject"), "body" to str(f, "body")).filter { it.second.isNotEmpty() }.joinToString("&") { it.first + "=" + pct(it.second) }
            if (to.isEmpty()) "" else "mailto:$to" + if (q.isEmpty()) "" else "?$q"
        }
        "contact" -> {
            val name = str(f, "name"); val tel = str(f, "tel"); val mail = str(f, "email")
            if (name.isEmpty() && tel.isEmpty() && mail.isEmpty()) ""
            else "MECARD:" + (if (name.isEmpty()) "" else "N:${esc(name)};") + (if (tel.isEmpty()) "" else "TEL:${esc(tel)};") + (if (mail.isEmpty()) "" else "EMAIL:${esc(mail)};") + ";"
        }
        else -> ""
    }

    /** [o] as a widget TV Home keeps, or null when it holds nothing a code can carry (no such template, nothing typed, too long for the code). */
    fun widget(o: JSONObject?): JSONObject? {
        if (o == null) return null
        val tpl = o.optString("tpl").takeIf { it in TEMPLATES } ?: return null
        val given = o.optJSONObject("f") ?: JSONObject()
        val f = JSONObject()
        for (k in FIELDS.getValue(tpl)) when {
            k == "hidden" -> f.put(k, given.optBoolean(k, false))
            k == "sec" -> f.put(k, given.optString(k).takeIf { it in SECURITY } ?: "WPA")
            else -> f.put(k, given.optString(k, "").take(if (tpl == "text") MAX_BYTES else FIELD_MAX))
        }
        val p = payload(tpl, f)
        if (p.isEmpty() || p.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return null
        return JSONObject().put("tpl", tpl).put("f", f)
            .put("cap", o.optString("cap").replace(Regex("\\s+"), " ").trim().take(CAP_MAX))
            .put("show", tpl == "wifi" && o.optBoolean("show", false))
    }

    /** The "qrs" of [o] made safe: only [KEYS], only widgets [widget] keeps. */
    fun widgets(o: JSONObject?): JSONObject {
        val out = JSONObject()
        if (o != null) for (k in KEYS) widget(o.optJSONObject(k))?.let { out.put(k, it) }
        return out
    }
}
