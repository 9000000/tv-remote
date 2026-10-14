"""Finding TVs: which hosts answer, this machine's networks and Tailscale peers, Cast names, the scan behind the TV list."""
import asyncio, errno, ipaddress, json, os, re, socket
from tvtools import find

TAILSCALE = find("tailscale", "/Applications/Tailscale.app/Contents/MacOS/Tailscale", r"C:\Program Files\Tailscale\tailscale.exe")
TS_ENV = {**os.environ, "TAILSCALE_BE_CLI": "1"}                      # the Mac app's binary acts as the CLI only from a Terminal


async def port_open(ip, port, timeout=0.7, unknown=None):
    """unknown: a set that collects hosts the OS gave up on at once ("Host is down", "No route to host") instead of trying."""
    try:
        _, w = await asyncio.wait_for(asyncio.open_connection(ip, port), timeout)
        w.close()
        return True
    except OSError as e:
        if unknown is not None and e.errno in (errno.EHOSTDOWN, errno.EHOSTUNREACH):
            unknown.add(ip)
        return False
    except asyncio.TimeoutError:
        return False


def local_networks():
    """The /24 around each of this computer's private IPv4 addresses (where a TV on the same Wi-Fi would be)."""
    ips = set()
    for probe in ("192.168.0.1", "10.0.0.1", "172.16.0.1", "8.8.8.8"):    # no packet is sent: this only asks the OS which address it would use
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sk:
            try:
                sk.connect((probe, 9)); ips.add(sk.getsockname()[0])
            except OSError:
                pass
    try:
        ips.update(i[4][0] for i in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET))
    except OSError:
        pass
    nets = []
    for ip in sorted(ips):
        a = ipaddress.ip_address(ip)
        if a.is_private and not a.is_loopback and not ip.startswith("100."):
            nets.append((ip, ipaddress.ip_network(ip + "/24", strict=False)))
    return nets


async def cast_name(ip):
    """The name an Android TV gives itself, asked from its Chromecast service (every Android TV has one). "" when it does not answer."""
    try:
        r, w = await asyncio.wait_for(asyncio.open_connection(ip, 8008), 0.8)
        try:
            w.write(f"GET /setup/eureka_info HTTP/1.1\r\nHost: {ip}:8008\r\nConnection: close\r\n\r\n".encode())
            got = b""
            while len(got) < 20000:                                   # the TV may keep the connection open: stop at the name
                chunk = await asyncio.wait_for(r.read(2048), 1.5)
                if not chunk:
                    break
                got += chunk
                m = re.search(r'"name"\s*:\s*"([^"]{1,60})"', got.decode("utf-8", "replace"))
                if m:
                    return m[1]
        finally:
            w.close()
    except (OSError, asyncio.TimeoutError):
        pass
    return ""


async def discover():
    """TVs we can see: LAN hosts answering on the remote (6466) or ADB (5555) port, and Android devices on the tailnet."""
    sem, found, unknown = asyncio.Semaphore(120), {}, set()

    async def probe(ip, via, name=""):
        async with sem:
            remote, adb = await asyncio.gather(port_open(ip, 6466, unknown=unknown), port_open(ip, 5555, unknown=unknown))
        if remote or adb:
            found[ip] = {"ip": ip, "remote": remote, "adb": adb, "via": via, "name": await cast_name(ip) or name}

    jobs, mine = [], set()
    for me, net in local_networks():
        mine.add(me)
        jobs += [probe(str(h), "lan") for h in net.hosts() if str(h) != me]
    try:
        p = await asyncio.create_subprocess_exec(TAILSCALE, "status", "--json", env=TS_ENV, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
        out, _ = await asyncio.wait_for(p.communicate(), 8)
        for peer in (json.loads(out).get("Peer") or {}).values():
            if peer.get("OS") == "android" and peer.get("Online") and peer.get("TailscaleIPs"):
                jobs.append(probe(peer["TailscaleIPs"][0], "tailscale", peer.get("HostName", "")))
    except Exception:
        pass                                                          # no Tailscale CLI here: LAN results only
    await asyncio.gather(*jobs)
    # A first scan asks the whole network at once who has each address (ARP), and the Mac answers "Host is down" for many
    # hosts without waiting, the TV often among them: it turned up only on the second scan. By now the answers have come
    # in, so those hosts are asked once more.
    again = [ip for ip in unknown if ip not in found]
    if again:
        await asyncio.sleep(1)
        await asyncio.gather(*[probe(ip, "lan") for ip in again])
    return {"networks": [str(n) for _, n in local_networks()], "found": sorted(found.values(), key=lambda f: (f["via"], f["ip"]))}
