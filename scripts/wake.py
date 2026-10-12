"""Wake a TV from standby with Wake-on-LAN.

  wake.py                          broadcast here, and also through a relay machine at home over SSH, then wait for the TV
  wake.py --local --mac AA:BB:..   only broadcast the magic packet from this machine (what the relay runs)

Wake-on-LAN only reaches the TV from a machine on the TV's own network, so from outside we relay through one.
The TV must have it enabled: Settings > Network & Internet > Remote start (Wake on LAN/Wi-Fi) = On.

Settings come from the environment (the web server fills them in from tvs.json for the current TV):
  TV_HOST     address to wait for            TV_MAC      the TV's network MAC address
  TV_LAN      the TV's LAN IP (for the subnet broadcast address)
  RELAY_USER, RELAY_HOST   SSH login of a machine on the TV's network (optional)
  RELAY_CMD   command to run there, default: python wake.py --local --mac <TV_MAC>
"""
import argparse, os, socket, subprocess, sys, time

MAC = os.environ.get("TV_MAC", "")
TV_HOST = os.environ.get("TV_HOST", "")
RELAY_USER, RELAY_HOST = os.environ.get("RELAY_USER", ""), os.environ.get("RELAY_HOST", "")


def broadcasts():
    lan = os.environ.get("TV_LAN", "")
    return ["255.255.255.255"] + ([lan.rsplit(".", 1)[0] + ".255"] if lan.count(".") == 3 else [])


def magic_packet(mac):
    raw = bytes.fromhex(mac.replace(":", "").replace("-", ""))
    return b"\xff" * 6 + raw * 16


def send_local(mac):
    pkt = magic_packet(mac)
    sent = 0
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        for ip in broadcasts():
            for port in (9, 7):
                try:
                    for _ in range(3):
                        s.sendto(pkt, (ip, port)); sent += 1
                        time.sleep(0.1)
                except OSError:
                    pass
    return sent


def send_via_relay(mac):
    cmd = os.environ.get("RELAY_CMD") or f"python wake.py --local --mac {mac}"
    r = subprocess.run(["ssh", "-o", "BatchMode=yes", "-o", "ConnectTimeout=8", "-l", RELAY_USER, RELAY_HOST, cmd],
                       capture_output=True, text=True, timeout=30)
    return r.returncode == 0, (r.stdout + r.stderr).strip()


def tv_up(timeout=1.5):
    try:
        with socket.create_connection((TV_HOST, 6466), timeout=timeout):
            return True
    except OSError:
        return False


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--local", action="store_true")
    ap.add_argument("--mac", default=MAC)
    ap.add_argument("--wait", type=int, default=90, help="seconds to wait for the TV to come back")
    a = ap.parse_args()
    if a.local:
        if not a.mac:
            print("no MAC address given"); sys.exit(2)
        print(f"sent {send_local(a.mac)} magic packets for {a.mac}"); sys.exit(0)

    if TV_HOST and tv_up():
        print("TV already reachable"); sys.exit(0)
    if not a.mac:
        print("No MAC address saved for this TV (save the TV in the web UI while ADB is connected)."); sys.exit(1)
    print(f"local broadcast: {send_local(a.mac)} packets")
    if RELAY_HOST:
        try:
            ok, out = send_via_relay(a.mac)
            print(f"relay via {RELAY_HOST}: {'ok' if ok else 'FAILED'} {out}")
        except Exception as e:
            print("relay failed:", e)
    else:
        print("no relay machine configured: this only works when this computer is on the TV's own network")
    for i in range(a.wait // 3):
        if TV_HOST and tv_up():
            print(f"TV is up after ~{i * 3}s"); sys.exit(0)
        time.sleep(3)
    print("TV did not come back. Check that 'Remote start' is enabled on the TV and that a machine on its network relayed the packet.")
    sys.exit(1)
