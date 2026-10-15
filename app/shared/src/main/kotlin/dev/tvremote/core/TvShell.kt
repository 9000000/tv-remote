package dev.tvremote.core

/**
 * Shell recipes run on the TV for the Apps tab (clean, back up, restore, log) and the TV card (restart, DNS, quick settings).
 * The same text as app/web/tvshell.py, which says what each one does: app/web/tests/tvshell_cases.json holds what both must
 * give (TvShellTest), so the computer and the apps change a TV in exactly the same way. Anything a person typed is checked
 * here first and throws IllegalArgumentException.
 */
object TvShell {
    private val PKG = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")
    private val COMP = Regex("[A-Za-z][A-Za-z0-9_.]*/[A-Za-z0-9_.\$]+")
    private val HOST = Regex("(?=.{4,253}\$)[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+")
    private val SINCE = Regex("[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}")
    private val NUM = Regex("-?[0-9]{1,10}")
    private val PERM = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

    const val CLEAN = "a=\$(stat -f -c '%a %S' /data); pm trim-caches 999G >/dev/null 2>&1; sleep 2; echo \$a \$(stat -f -c '%a %S' /data)"

    const val REBOOT_PREP = "case \"\$(settings get secure always_on_vpn_app)\" in com.tailscale.ipn) ;; *) " +
            "pm path com.tailscale.ipn >/dev/null 2>&1 && ip addr | grep -qE 'inet 100\\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\\.' " +
            "&& { settings put secure always_on_vpn_app com.tailscale.ipn; settings put secure always_on_vpn_lockdown 0; };; esac; echo ok"

    private const val HOME_LIST = "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
    private const val HOME_NOW = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1"

    const val TVSET_READ = "echo sdk \$(getprop ro.build.version.sdk); echo dns_mode \$(settings get global private_dns_mode); " +
            "echo dns_host \$(settings get global private_dns_specifier); echo sleep \$(settings get secure sleep_timeout); " +
            "echo saver \$(settings get secure screensaver_enabled); echo saver_after \$(settings get system screen_off_timeout); " +
            "wm density; echo home \$(" + HOME_NOW + "); " + HOME_LIST + " | grep / | sed 's/^ */launcher /'"

    const val BOOT = "cmd package query-receivers --brief -a android.intent.action.BOOT_COMPLETED 2>/dev/null | grep / | sed 's/^ *//'"

    const val SETTINGS_READ = "for n in global secure system; do settings list \$n 2>/dev/null | sed \"s/^/\$n /\"; done"

    fun appInfo(pkg: String): String {
        pkg(pkg)
        return "p=$pkg; dumpsys package \$p | sed -n '/^Packages:/,\$p'; printf '\\043~\\043\\n'; pidof \$p; printf '\\043~\\043\\n'; " +
            "dumpsys meminfo \$p 2>/dev/null | grep -m1 -E '^ *TOTAL'; printf '\\043~\\043\\n'; $BOOT | grep \"^\$p/\""
    }

    fun perm(pkg: String, name: String, on: Boolean): String {
        pkg(pkg)
        if (!name.matches(PERM)) throw IllegalArgumentException("bad permission")
        return "pm ${if (on) "grant" else "revoke"} $pkg $name 2>&1 && echo ok"
    }

    fun backup(pkg: String): String {
        pkg(pkg)
        return "trap '' HUP PIPE; p=$pkg; d=/data/local/tmp/tvr-bk-\$\$; rm -rf \$d; mkdir -p \$d/apk && cd \$d || exit 1; " +
            "for f in \$(pm path \$p | sed 's/^package://'); do ln -s \"\$f\" apk/\${f##*/}; done; " +
            "for k in obb data; do [ -d /sdcard/Android/\$k/\$p ] && ln -s /sdcard/Android/\$k/\$p \$k; done; " +
            "echo \$p > package.txt; dumpsys package \$p | grep -m1 versionName | cut -d= -f2 > version.txt; " +
            "tar -chf - * 2>/dev/null; cd /; rm -rf \$d"
    }

    fun restore(tar: String): String {
        if (!tar.matches(Regex("/data/local/tmp/tvr-rs-[0-9a-f]{8}\\.tar"))) throw IllegalArgumentException("bad restore path")
        return "t=$tar; d=\${t%.tar}; rm -rf \$d; mkdir -p \$d && cd \$d && tar -xf \$t 2>/dev/null; " +
            "ls apk/*.apk >/dev/null 2>&1 || { echo 'not a backup'; cd /; rm -rf \$d \$t; exit; }; " +
            "p=\$(cat package.txt 2>/dev/null); echo \"\$p\" | grep -qE '^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+\$' || p=; " +
            "s=\$(pm install-create -r | tr -dc 0-9); " +
            "for f in apk/*.apk; do cat \$f | pm install-write -S \$(stat -c %s \$f) \$s \${f##*/} - >/dev/null; done; " +
            "pm install-commit \$s 2>&1; " +
            "[ -n \"\$p\" ] && for k in obb data; do [ -d \$k ] && mkdir -p /sdcard/Android/\$k/\$p && cp -R \$k/. /sdcard/Android/\$k/\$p/; done; " +
            "cd /; rm -rf \$d \$t; echo package \$p"
    }

    fun log(pkg: String, since: String = ""): String {
        pkg(pkg)
        if (since.isNotEmpty() && !since.matches(SINCE)) throw IllegalArgumentException("bad time")
        val pick = if (since.isNotEmpty()) "-T '$since'" else "-t 300"
        return "p=\$(pidof $pkg); echo pid \$p; [ -n \"\$p\" ] && logcat -d -v threadtime --pid=\${p%% *} $pick | tail -n 500"
    }

    fun tvset(key: String, raw: String): String {
        var value = raw.trim()
        when (key) {
            "dns" -> {
                if (value.isEmpty()) return "settings put global private_dns_mode opportunistic; settings delete global private_dns_specifier >/dev/null 2>&1; echo ok"
                value = value.lowercase(java.util.Locale.ROOT)
                if (!value.matches(HOST)) throw IllegalArgumentException("bad DNS name")
                return "trap '' HUP PIPE; m=\$(settings get global private_dns_mode); h=\$(settings get global private_dns_specifier); " +
                    "settings put global private_dns_specifier $value; settings put global private_dns_mode hostname; " +
                    "e=\$((\$(date +%s)+30)); while [ \$(date +%s) -lt \$e ]; do sleep 2; " +
                    "timeout 4 ping -c 1 -W 2 www.google.com 2>&1 | grep -q '^PING ' && { echo ok; exit; }; done; " +
                    "if [ \"\$h\" = null ]; then settings delete global private_dns_specifier >/dev/null 2>&1; else settings put global private_dns_specifier \"\$h\"; fi; " +
                    "[ \"\$m\" = null ] && m=opportunistic; settings put global private_dns_mode \$m; echo failed"
            }
            "sleep" -> return "settings put secure sleep_timeout ${int(value, 60000, 86400000, -1)}; echo ok"
            "saver" -> {
                if (value == "off") return "settings put secure screensaver_enabled 0; echo ok"
                return "settings put secure screensaver_enabled 1; settings put system screen_off_timeout ${int(value, 60000, 86400000)}; echo ok"
            }
            "density" -> {
                if (value == "reset") return "wm density reset; echo ok"
                return "wm density ${int(value, 100, 800)}; echo ok"
            }
            "home" -> {
                if (!value.matches(COMP)) throw IllegalArgumentException("bad home screen")
                return "$HOME_LIST | grep -qF '$value' || { echo 'not a home screen'; exit; }; " +
                    "cmd package set-home-activity '$value' >/dev/null 2>&1; echo home \$($HOME_NOW)"
            }
        }
        throw IllegalArgumentException("unknown setting")
    }

    private fun pkg(pkg: String) { if (!pkg.matches(PKG)) throw IllegalArgumentException("bad package name") }

    private fun int(value: String, lo: Long, hi: Long, also: Long? = null): Long {
        if (!value.matches(NUM)) throw IllegalArgumentException("bad number")
        val n = value.toLong()
        if (n != also && n !in lo..hi) throw IllegalArgumentException("out of range")
        return n
    }
}
