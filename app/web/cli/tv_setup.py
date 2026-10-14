"""One-shot TV setup over ADB. Safe to re-run; nothing is uninstalled.

  tv_setup.py [apply] [host:port]    keep Tailscale alive, speed up the UI, light launcher, turn off unused system apps
  tv_setup.py undo    [host:port]    put the stock launcher, apps and animation speed back
  tv_setup.py plan    [host:port]    print (JSON) what apply could do on this TV, for the web UI to offer
  --vi                               messages in Vietnamese
  --keep-anim / --keep-home          leave the animations / the home screen as they are
  --disable=pkg,pkg                  the apps to turn off instead of the suggested ones (empty: none)

Universal: the Tailscale, animation and launcher steps work on any Android TV / Google TV with ADB debugging on.
Not universal: the SONY list only exists on Sony BRAVIA sets (elsewhere those are skipped as "not installed"; add that
brand's own bloat to EXTRA). Review the lists before running this on a TV whose owners use those apps.
"""
import hashlib, json, re, subprocess, sys, tempfile, urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from tvtools import ADB, utf8_console
from adbtools import PROTECTED                                   # never offered or turned off (as Setup.kt PROTECTED)

FL_PKG = "me.efesser.flauncher"
FL_URL = "https://github.com/osrosal/flauncher/releases/download/v2025.07.001/flauncher-armeabi-v7a-release.apk"   # 32-bit ARM, also runs on arm64
FL_SHA = "467fd0befe6daa3993a8a34a3981a631858ace28b38d9836d691478af8b71e40"
GOOGLE = "com.google.android.play.games com.google.android.videos com.google.android.youtube.tvmusic".split()
SONY = """com.sony.dtv.b2b.vendorprotocol com.sony.dtv.b2b.rs232csupport com.sony.dtv.b2b.hotelmode com.sony.dtv.b2b.prosettings
com.sony.dtv.b2b.importexport com.sony.dtv.b2b.noderuntime.normal com.sony.dtv.b2b.noderuntime.privilege com.sony.dtv.b2b.webappruntimedrm
com.sony.dtv.demomode com.sony.dtv.demosupport com.sony.dtv.multiscreendemo com.sony.dtv.sonyselect tv.samba.ssm""".split()
EXTRA = []
LAUNCHER = ["com.google.android.tvrecommendations", "com.google.android.tvlauncher"]   # disabled last, only once FLauncher is installed
ANIM = "window_animation_scale transition_animation_scale animator_duration_scale"
NAMES = {"com.google.android.play.games": "Google Play Games", "com.google.android.videos": "Google Play Movies & TV",
         "com.google.android.youtube.tvmusic": "YouTube Music", "com.sony.dtv.b2b.vendorprotocol": "Sony B2B vendor protocol",
         "com.sony.dtv.b2b.rs232csupport": "Sony B2B RS-232C", "com.sony.dtv.b2b.hotelmode": "Sony Hotel mode",
         "com.sony.dtv.b2b.prosettings": "Sony Pro settings", "com.sony.dtv.b2b.importexport": "Sony B2B import/export",
         "com.sony.dtv.b2b.noderuntime.normal": "Sony B2B runtime", "com.sony.dtv.b2b.noderuntime.privilege": "Sony B2B runtime (privileged)",
         "com.sony.dtv.b2b.webappruntimedrm": "Sony B2B web app DRM", "com.sony.dtv.demomode": "Sony Demo mode",
         "com.sony.dtv.demosupport": "Sony Demo support", "com.sony.dtv.multiscreendemo": "Sony Multi-screen demo",
         "com.sony.dtv.sonyselect": "Sony Select", "tv.samba.ssm": "Samba TV"}
OFF_LIST = "/data/local/tmp/tvr-disabled.txt"                    # what was turned off beyond the lists above, for undo


def run(*args, timeout=120):
    p = subprocess.run([ADB, *args], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    return (p.stdout + p.stderr).strip()


def main(mode, target, vi=False, anim=True, home=True, disable=None):
    """Prints one line per step, in the words the two apps use (Setup.kt), Vietnamese when asked (the web UI passes its language)."""
    t = lambda v, e: v if vi else e
    sh = lambda cmd: run("-s", target, "shell", cmd)
    has = lambda pkg: "package:" in sh(f"pm path {pkg}")
    last = lambda text: (text.splitlines() or [""])[-1]
    run("connect", target)
    if run("-s", target, "get-state") != "device":
        print(t(f"✗ Không nối được ADB tới {target} (TV đã bật Gỡ lỗi ADB và cho phép máy này chưa?)", f"cannot reach {target} over ADB (is ADB debugging on, and this computer allowed?)")); return 1
    print(f"TV: {sh('getprop ro.product.manufacturer')} {sh('getprop ro.product.model')}, Android {sh('getprop ro.build.version.release')}", flush=True)
    if mode != "undo" and sh("pm has-feature android.software.leanback").strip() == "false":   # a phone or tablet: see Setup.tvOnly
        print(t("✗ Máy này không phải TV. Tối ưu TV đổi màn hình chính và tắt app hệ thống, nên chỉ dùng cho TV.",
                "✗ This device is not a TV. Optimize TV swaps the home screen and turns system apps off, so it is for TVs only.")); return 1

    if mode == "plan":
        enabled = {l[8:].strip() for l in sh("pm list packages -e").splitlines() if l.startswith("package:")}
        suggest = [p for p in GOOGLE + SONY + EXTRA if p in enabled]
        cur_home = last(sh("cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN")).split("/")[0]
        shown = {l.strip().split("/")[0] for cat in ("LEANBACK_LAUNCHER", "LAUNCHER")
                 for l in sh(f"cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.{cat} 2>/dev/null").splitlines() if "/" in l}
        others = sorted(p for p in shown if p in enabled and p not in suggest and p != cur_home and not PROTECTED.fullmatch(p))
        item = lambda p: {"pkg": p, "label": NAMES.get(p, "")}
        print(json.dumps({"anim": sh("settings get global window_animation_scale").strip() != "0.0", "flauncher": has(FL_PKG), "home": cur_home,
                          "suggest": [item(p) for p in suggest], "apps": [item(p) for p in others]})); return 0

    if mode == "undo":
        on = 0
        mine = [l.strip() for l in sh(f"cat {OFF_LIST} 2>/dev/null").splitlines() if l.strip()]
        for p in dict.fromkeys(LAUNCHER + GOOGLE + SONY + EXTRA + mine):
            if has(p):
                sh(f"pm enable --user 0 {p}"); on += 1
        sh(f"rm -f {OFF_LIST}")
        sh(f"for k in {ANIM}; do settings put global $k 1.0; done")
        print(t(f"✓ Đã bật lại {on} app (gồm màn hình chính Google), hiệu ứng về 1.0x", f"✓ Re-enabled {on} apps (Google's home screen among them), animations back to 1.0x"))
        print(t("FLauncher và cài đặt Tailscale được giữ nguyên.", "FLauncher and the Tailscale settings are left as they are.")); return 0

    # 1. Tailscale survives standby and reboot (the Tailscale button does this too; here for a Tailscale installed by hand)
    if has("com.tailscale.ipn"):
        sh("dumpsys deviceidle whitelist +com.tailscale.ipn")                 # exempt from Doze / battery optimisation
        sh("am set-standby-bucket com.tailscale.ipn active")                    # resets on reboot, hence re-runnable
        sh("cmd appops set com.tailscale.ipn RUN_IN_BACKGROUND allow; cmd appops set com.tailscale.ipn RUN_ANY_IN_BACKGROUND allow")
        sh("settings put secure always_on_vpn_app com.tailscale.ipn; settings put secure always_on_vpn_lockdown 0")   # VPN starts at boot, no lockdown
        print(t("✓ Tailscale: tự chạy lại sau khi TV ngủ hoặc khởi động lại", "✓ Tailscale: starts again by itself after standby or a restart"), flush=True)

    # 2. Snappier UI
    # window and transition animations off; the animator stays at 0.5x, because at 0 loading spinners freeze
    if anim:
        sh("settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0.5")
        print(t("✓ Hiệu ứng chuyển cảnh: tắt", "✓ Transition animations: off"), flush=True)

    # 3. Apps turned off: the suggested ones, or the ones the person picked (the ones this TV does not have are skipped quietly)
    off = 0
    for p in dict.fromkeys(GOOGLE + SONY + EXTRA if disable is None else disable):
        if not re.fullmatch(r"[A-Za-z]\w*(\.\w+)+", p) or PROTECTED.fullmatch(p) or not has(p):
            continue
        sh(f"pm disable-user --user 0 {p}; am force-stop {p}"); off += 1
        if p not in GOOGLE + SONY + EXTRA:
            sh(f"grep -qx '{p}' {OFF_LIST} 2>/dev/null || echo '{p}' >> {OFF_LIST}")   # for undo
    if off:
        print(t(f"✓ Đã tắt {off} app", f"✓ Turned off {off} apps"), flush=True)
    if not home:
        print(t("Xong.", "Done.")); return 0

    # 4. Light launcher, then retire the Google home screen
    if not has(FL_PKG):
        print(t("… Đang tải FLauncher (10 MB)", "… Downloading FLauncher (10 MB)"), flush=True)
        with tempfile.TemporaryDirectory() as d:
            apk = Path(d) / "flauncher.apk"
            try:
                apk.write_bytes(urllib.request.urlopen(FL_URL, timeout=120).read())
            except OSError as e:
                print(t(f"✗ Không tải được FLauncher ({e}) — giữ nguyên màn hình chính", f"✗ FLauncher download failed ({e}), launcher left unchanged")); return 1
            if hashlib.sha256(apk.read_bytes()).hexdigest() != FL_SHA:
                print(t("✗ FLauncher sai checksum, không cài", "✗ FLauncher checksum mismatch, not installing")); return 1
            print(t("… Đang cài FLauncher", "… Installing FLauncher"), flush=True)
            out = last(run("-s", target, "install", "-r", str(apk), timeout=300))
        if not has(FL_PKG):
            why = t("TV này chỉ chạy app 64-bit, FLauncher chỉ có bản 32-bit", "this TV runs 64-bit apps only and FLauncher is 32-bit") if "NO_MATCHING_ABIS" in out else out
            print(t(f"– Không cài được FLauncher ({why}) — giữ nguyên màn hình chính", f"– FLauncher could not be installed ({why}), the home screen is left as it is"))
            print(t("Xong.", "Done.")); return 0
    for p in LAUNCHER:
        if has(p):
            sh(f"pm disable-user --user 0 {p}; am force-stop {p}")
    home = last(sh("cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN"))
    print(t("✓ Màn hình chính: ", "✓ Home screen: ") + home)
    print(t("Xong. Bấm nút Home để thấy màn hình chính mới.", "Done. Press Home to see the new home screen."))
    if sys.stdout.isatty():
        print(f"Undo with: tv_setup.py undo {target}")
    return 0


if __name__ == "__main__":
    utf8_console()
    args = sys.argv[1:]
    vi = "--vi" in args
    opts = {"anim": "--keep-anim" not in args, "home": "--keep-home" not in args}
    for a in args:
        if a.startswith("--disable="):
            opts["disable"] = [p for p in a[10:].split(",") if p]
    args = [a for a in args if a not in ("--vi", "--keep-anim", "--keep-home") and not a.startswith("--disable=")]
    mode = args.pop(0) if args and args[0] in ("apply", "undo", "plan") else "apply"
    if args and args[0].startswith("-"):
        print(__doc__); sys.exit(2)
    if not args:
        import tvconfig
        cur = tvconfig.load()["current"]
        if not cur:
            print(__doc__); sys.exit(2)
        args = [cur + ":5555"]                                        # the TV picked in the web UI
    sys.exit(main(mode, args[0], vi, **opts))
