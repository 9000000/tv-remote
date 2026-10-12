"""Bring a new Android TV / Google TV under this remote. Run it on a computer on the SAME Wi-Fi/LAN as the TV:

  tv_new.py <tv-lan-ip>        e.g. tv_new.py 192.168.1.50

The one thing no script can do is switch on ADB debugging; a person at the TV does that once:
  Settings > Device preferences > About > press OK 7 times on "Build"
  Settings > Device preferences > Developer options > "ADB debugging" (or "USB debugging") = On
  and, when this script connects, choose "Always allow" on the TV.

After that this script installs Tailscale (official APK, checksum verified), opens this remote in your browser pointed
at the TV so you can see its screen and click it with the mouse, waits while you sign the TV in to YOUR Tailscale
account (the TV shows a code; enter it in the Tailscale admin console, no typing on the TV), pairs the remote keys, optionally runs
tv_setup.py, and saves the TV so start.command / start.cmd opens it.

The web page does the same under its "TV" tab; this script is the command-line way.
"""
import hashlib, json, os, re, subprocess, sys, tempfile, time, urllib.request, webbrowser
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(BASE / "app"))
import tvconfig
from tvtools import ADB, utf8_console

PORT = 8766
PKGS = "https://pkgs.tailscale.com/stable/"


def main(ip):
    target = f"{ip}:5555"
    run = lambda *a, timeout=120: (lambda p: (p.stdout + p.stderr).strip())(
        subprocess.run([ADB, *a], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout))
    sh = lambda cmd: run("-s", target, "shell", cmd)
    has = lambda pkg: "package:" in sh(f"pm path {pkg}")
    ts_ip = lambda: (re.search(r"inet (100\.[0-9.]+)/32", sh("ip -4 addr show")) or [None, ""])[1]
    py = lambda *a, **kw: subprocess.run([sys.executable, *a], cwd=BASE, **kw)

    print(f"== 1/5 Kết nối ADB tới {target}", flush=True)
    run("connect", target)
    for i in range(60):
        if run("-s", target, "get-state") == "device":
            break
        if i == 0:
            print('   Nhìn lên TV: nếu có hộp hỏi "Cho phép gỡ lỗi USB?" thì tích "Luôn cho phép" rồi OK. (đang chờ...)', flush=True)
        time.sleep(2)
        if i % 3 == 2:                                                # adb keeps showing "unauthorized" after the TV said yes
            run("disconnect", target)
        run("connect", target)
    else:
        print('   Không kết nối được. Kiểm tra: TV và máy này cùng mạng, đã bật "Gỡ lỗi ADB", IP đúng.'); return 1
    model = sh("getprop ro.product.model")
    print(f"   OK: {sh('getprop ro.product.manufacturer')} {model}, Android {sh('getprop ro.build.version.release')}", flush=True)

    print("== 2/5 Tailscale", flush=True)
    if has("com.tailscale.ipn"):
        print("   đã cài sẵn")
    else:
        try:
            name = json.load(urllib.request.urlopen(PKGS + "?mode=json", timeout=30))["APKs"]["universal"]
            print(f"   đang tải {name} (khoảng 100 MB)...", flush=True)
            data = urllib.request.urlopen(PKGS + name, timeout=600).read()
            want = urllib.request.urlopen(PKGS + name + ".sha256", timeout=30).read().decode().split()[0]
        except (OSError, KeyError, IndexError) as e:
            print(f"   tải Tailscale thất bại: {e}"); return 1
        if hashlib.sha256(data).hexdigest() != want:
            print("   checksum Tailscale không khớp, dừng"); return 1
        with tempfile.TemporaryDirectory() as d:
            (Path(d) / "ts.apk").write_bytes(data)
            out = run("-s", target, "install", "-r", str(Path(d) / "ts.apk"), timeout=600)
        print(f"   cài: {(out.splitlines() or ['?'])[-1]}", flush=True)
        if not has("com.tailscale.ipn"):
            return 1

    server = None
    try:
        if not ts_ip():
            print("== 3/5 Thêm TV vào Tailscale của bạn", flush=True)
            sh("monkey -p com.tailscale.ipn -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1 || "
               "monkey -p com.tailscale.ipn -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1")
            server = subprocess.Popen([sys.executable, "app/server.py", "--tv", ip, "--port", str(PORT)], cwd=BASE,
                                      stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            time.sleep(2); url = f"http://127.0.0.1:{PORT}/?view=1"; webbrowser.open(url)
            print(f"   Trình duyệt vừa mở màn hình TV ({url}). TV hiện một mã (và mã QR), bấm chuột lên hình nếu cần chọn Get started.")
            print("   Không cần gõ tài khoản trên TV: mở https://login.tailscale.com/admin/machines -> Add device -> nhập mã đó (hoặc quét QR bằng điện thoại).")
            print("   Nếu TV hỏi cho phép tạo kết nối VPN thì chọn OK. (đang chờ TV vào mạng Tailscale, tối đa 15 phút...)", flush=True)
            for _ in range(180):
                if ts_ip():
                    break
                time.sleep(5)
            else:
                print("   Chưa thấy TV vào Tailscale. Chạy lại script này sau khi thêm TV xong."); return 1
        else:
            print("== 3/5 TV đã vào Tailscale")
        ts = ts_ip()
        print(f"   IP Tailscale của TV: {ts}", flush=True)

        print("== 4/5 Ghép nút điều khiển (remote)", flush=True)
        rc = py("scripts/pair.py", ip, "--check", stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode
        if rc == 3:
            print("   TV sẽ hiện mã 6 ký tự (thấy được trong trình duyệt nếu đang mở).", flush=True)
            if py("scripts/pair.py", ip).returncode:
                print(f"   Ghép chưa xong; chạy lại sau: pair.py {ts}")
        elif rc == 0:
            print("   đã ghép sẵn")
        else:
            print(f"   Không tới được dịch vụ remote của TV (cổng 6466). Các nút bấm sẽ chưa dùng được; thử lại sau: pair.py {ts}")
    finally:
        if server:
            server.terminate()

    print("== 5/5 Tối ưu TV (giữ Tailscale sống, giảm hiệu ứng, thay màn hình chính bằng FLauncher, tắt app hệ thống không dùng)")
    try:
        ans = input("   Chạy luôn không? Hoàn tác được bằng tv_setup.py undo. [y/N] ").strip().lower()
    except EOFError:
        ans = ""
    if ans == "y":
        py("scripts/tv_setup.py", "apply", target)
    else:
        print(f'   bỏ qua (chạy sau: tv_setup.py apply {ts}:5555, hoặc nút "Tối ưu TV" trên web)')

    mac = ""
    for block in re.split(r"^\d+: ", sh("ip addr show"), flags=re.M):   # MAC of the interface that is on the home network
        m, e = re.search(r"inet ((?!100\.|127\.)[0-9.]+)/", block), re.search(r"link/ether ([0-9a-f:]{17})", block)
        if m and e:
            mac = e[1].upper(); break
    cfg = tvconfig.load()
    tvconfig.upsert(cfg, {"name": model, "host": ts, "lan": ip if not ip.startswith("100.") else "", "mac": mac}, replace_host=ip)
    tvconfig.save(cfg)
    print(f'\nXong. Đã lưu "{model}" ({ts}). Bấm đúp start.command / start.cmd để mở remote; TV này đã được chọn sẵn.')
    print("Bật TV khi nó tắt hẳn vẫn cần một máy trong cùng mạng nhà gửi Wake-on-LAN (xem wake.py).")
    return 0


if __name__ == "__main__":
    utf8_console()
    if len(sys.argv) != 2 or sys.argv[1].startswith("-"):
        print(__doc__); sys.exit(2)
    sys.exit(main(sys.argv[1]))
