"""Shell recipes run on the TV for the Apps tab (clean, back up, restore, log) and the TV card (restart, DNS, quick settings).

The same text is built in Manage.kt (the two Android apps): tests/tvshell_cases.json holds what each builder must give, and
both test suites check against it, so the computer and the apps change a TV in exactly the same way. Each recipe prints plain
text that the page reads; anything a person typed is checked here first and raises ValueError.
"""
import re

# whole-string matches (fullmatch), ASCII only: TvShell.kt has the same patterns
PKG = re.compile(r"[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+")
COMP = re.compile(r"[A-Za-z][A-Za-z0-9_.]*/[A-Za-z0-9_.$]+")
HOST = re.compile(r"(?=.{4,253}$)[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+")
SINCE = re.compile(r"[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}")   # a logcat threadtime stamp
NUM = re.compile(r"-?[0-9]{1,10}")

# 🧹 every app's cache, through the system's own cleaner (asking for far more than there is frees all it can); free space
# before and after as "blocks blocksize blocks blocksize"
CLEAN = ("a=$(stat -f -c '%a %S' /data); pm trim-caches 999G >/dev/null 2>&1; sleep 2; "
         "echo $a $(stat -f -c '%a %S' /data)")

# 🔁 before a restart: a Tailscale that is connected now but not set to start with the TV would leave a TV reached from
# away unreachable, so it is made always-on (a Tailscale switched off on purpose is left alone)
REBOOT_PREP = ("case \"$(settings get secure always_on_vpn_app)\" in com.tailscale.ipn) ;; *) "
               "pm path com.tailscale.ipn >/dev/null 2>&1 && ip addr | grep -qE 'inet 100\\.(6[4-9]|[7-9][0-9]|1[01][0-9]|12[0-7])\\.' "
               "&& { settings put secure always_on_vpn_app com.tailscale.ipn; settings put secure always_on_vpn_lockdown 0; };; esac; echo ok")

HOME_LIST = "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
HOME_NOW = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1"

# ⚙️ the TV card: one "name value" line each; wm density says "Physical density: 320" (and "Override density: 280")
TVSET_READ = ("echo sdk $(getprop ro.build.version.sdk); echo dns_mode $(settings get global private_dns_mode); "
              "echo dns_host $(settings get global private_dns_specifier); echo sleep $(settings get secure sleep_timeout); "
              "echo saver $(settings get secure screensaver_enabled); echo saver_after $(settings get system screen_off_timeout); "
              "wm density; echo home $(" + HOME_NOW + "); " + HOME_LIST + " | grep / | sed 's/^ */launcher /'")


def backup(pkg):
    """💾 one app as a tar on stdout: apk/ (every split), obb/ and data/ (its folders in shared storage), package.txt,
    version.txt. What the app keeps in /data/data cannot be read without root, so it is not in there. Hang-ups are ignored so
    the folder of links is still removed when the download is cancelled."""
    _pkg(pkg)
    return (f"trap '' HUP PIPE; p={pkg}; d=/data/local/tmp/tvr-bk-$$; rm -rf $d; mkdir -p $d/apk && cd $d || exit 1; "
            "for f in $(pm path $p | sed 's/^package://'); do ln -s \"$f\" apk/${f##*/}; done; "
            "for k in obb data; do [ -d /sdcard/Android/$k/$p ] && ln -s /sdcard/Android/$k/$p $k; done; "
            "echo $p > package.txt; dumpsys package $p | grep -m1 versionName | cut -d= -f2 > version.txt; "
            "tar -chf - * 2>/dev/null; cd /; rm -rf $d")


def restore(tar):
    """📥 a backup copied to `tar` on the TV: its APKs installed as one session (split apps need that), its folders put back.
    The package name comes from the file, so it is checked before it names a folder. Ends with "package <name>"."""
    if not re.fullmatch(r"/data/local/tmp/tvr-rs-[0-9a-f]{8}\.tar", tar or ""):
        raise ValueError("bad restore path")
    return (f"t={tar}; d=${{t%.tar}}; rm -rf $d; mkdir -p $d && cd $d && tar -xf $t 2>/dev/null; "
            "ls apk/*.apk >/dev/null 2>&1 || { echo 'not a backup'; cd /; rm -rf $d $t; exit; }; "
            "p=$(cat package.txt 2>/dev/null); echo \"$p\" | grep -qE '^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$' || p=; "
            "s=$(pm install-create -r | tr -dc 0-9); "
            "for f in apk/*.apk; do cat $f | pm install-write -S $(stat -c %s $f) $s ${f##*/} - >/dev/null; done; "
            "pm install-commit $s 2>&1; "
            "[ -n \"$p\" ] && for k in obb data; do [ -d $k ] && mkdir -p /sdcard/Android/$k/$p && cp -R $k/. /sdcard/Android/$k/$p/; done; "
            "cd /; rm -rf $d $t; echo package $p")


def log(pkg, since=""):
    """📜 the app's own log lines: the last 300, or those from `since` on (the page polls with the stamp of its last line).
    First line "pid <n>", empty when the app is not running."""
    _pkg(pkg)
    if since and not SINCE.fullmatch(since):
        raise ValueError("bad time")
    pick = f"-T '{since}'" if since else "-t 300"
    return f"p=$(pidof {pkg}); echo pid $p; [ -n \"$p\" ] && logcat -d -v threadtime --pid=${{p%% *}} {pick} | tail -n 500"


def tvset(key, value):
    """The command for one choice on the TV card; prints "ok" (DNS: "failed" when names stopped resolving and it was put back)."""
    value = str(value).strip()
    if key == "dns":
        if not value:
            return "settings put global private_dns_mode opportunistic; settings delete global private_dns_specifier >/dev/null 2>&1; echo ok"
        value = value.lower()
        if not HOST.fullmatch(value):
            raise ValueError("bad DNS name")
        # a DNS that does not answer on this network leaves the TV with no Internet at all: wait for names to resolve
        # again (ping prints "PING name (address)" once it has one; a new DNS can take ~10 s to check), else put the old setting back.
        # A dead DNS can hold one lookup far longer than ping's -W, hence `timeout` and a 30 s deadline; the shell ignores
        # hang-ups so the old setting still comes back when the link to the TV drops meanwhile.
        return (f"trap '' HUP PIPE; m=$(settings get global private_dns_mode); h=$(settings get global private_dns_specifier); "
                f"settings put global private_dns_specifier {value}; settings put global private_dns_mode hostname; "
                "e=$(($(date +%s)+30)); while [ $(date +%s) -lt $e ]; do sleep 2; "
                "timeout 4 ping -c 1 -W 2 www.google.com 2>&1 | grep -q '^PING ' && { echo ok; exit; }; done; "
                "if [ \"$h\" = null ]; then settings delete global private_dns_specifier >/dev/null 2>&1; else settings put global private_dns_specifier \"$h\"; fi; "
                "[ \"$m\" = null ] && m=opportunistic; settings put global private_dns_mode $m; echo failed")
    if key == "sleep":
        n = _int(value, 60000, 86400000, (-1,))
        return f"settings put secure sleep_timeout {n}; echo ok"
    if key == "saver":
        if value == "off":
            return "settings put secure screensaver_enabled 0; echo ok"
        n = _int(value, 60000, 86400000)
        return f"settings put secure screensaver_enabled 1; settings put system screen_off_timeout {n}; echo ok"
    if key == "density":
        if value == "reset":
            return "wm density reset; echo ok"
        return f"wm density {_int(value, 100, 800)}; echo ok"
    if key == "home":
        if not COMP.fullmatch(value):
            raise ValueError("bad home screen")
        return (f"{HOME_LIST} | grep -qF '{value}' || {{ echo 'not a home screen'; exit; }}; "
                f"cmd package set-home-activity '{value}' >/dev/null 2>&1; echo home $({HOME_NOW})")
    raise ValueError("unknown setting")


def _pkg(pkg):
    if not PKG.fullmatch(pkg or ""):
        raise ValueError("bad package name")


def _int(value, lo, hi, also=()):
    if not NUM.fullmatch(value):
        raise ValueError("bad number")
    n = int(value)
    if n not in also and not lo <= n <= hi:
        raise ValueError("out of range")
    return n
