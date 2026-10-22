#!/usr/bin/env python3
"""A stand-in for a TV that has only Wireless debugging (no port 5555): announces _adb-tls-connect._tcp and _adb-tls-pairing._tcp
over mDNS, and on the connect port speaks adbd's TLS start then answers AUTH (this key is not paired).
The phone / computer app should find it, offer pairing, and say the TV wants a pairing code (Manage.canPair, AdbTls, Mdns).
usage: python3 dev/test/faketv.py <lan-ip> <connect-port> <pairing-port> <cert.pem> <key.pem>
  a throwaway pair: openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:P-256 -nodes -subj /CN=faketv -keyout k.pem -out c.pem"""
import socket, struct, ssl, sys, threading, time

ip, cport, pport, cert, key = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), sys.argv[4], sys.argv[5]
CNXN, AUTH, STLS = 0x4e584e43, 0x48545541, 0x534c5453

def name(n):
    out = b''
    for l in n.strip('.').split('.'):
        out += bytes([len(l)]) + l.encode()
    return out + b'\0'

def rr(n, t, rdata, ttl=120):
    return name(n) + struct.pack('>HHIH', t, 1, ttl, len(rdata)) + rdata

HOST = 'faketv.local'
def answer(svc, port, inst):
    full = inst + '.' + svc + '.local'
    recs = [rr(svc + '.local', 12, name(full)),
            rr(full, 33, struct.pack('>HHH', 0, 0, port) + name(HOST)),
            rr(HOST, 1, socket.inet_aton(ip))]
    return struct.pack('>HHHHHH', 0, 0x8400, 0, 1, 0, len(recs) - 1) + recs[0] + b''.join(recs[1:])

SERVICES = {'_adb-tls-connect._tcp': (cport, 'adb-FAKETCL-x1'), '_adb-tls-pairing._tcp': (pport, 'adb-FAKETCL-x1')}

def mdns():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
    s.bind(('', 5353))
    s.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, socket.inet_aton('224.0.0.251') + socket.inet_aton(ip))
    while True:
        data, addr = s.recvfrom(9000)
        low = data.lower()
        for svc, (port, inst) in SERVICES.items():
            if name(svc + '.local').lower() in low and not (data[2] & 0x80):     # a question about it
                s.sendto(answer(svc, port, inst), addr)
                print('mdns answered', svc, 'to', addr, flush=True)

def msg(cmd, a0, a1, data=b''):
    return struct.pack('<6I', cmd, a0, a1, len(data), sum(data) & 0xffffffff, cmd ^ 0xffffffff) + data

def readmsg(c):
    h = b''
    while len(h) < 24:
        d = c.recv(24 - len(h))
        if not d: raise EOFError
        h += d
    cmd, a0, a1, n, _, _ = struct.unpack('<6I', h)
    data = b''
    while len(data) < n:
        data += c.recv(n - len(data))
    return cmd, a0, a1, data

def serve():
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.minimum_version = ssl.TLSVersion.TLSv1_3
    ctx.load_cert_chain(cert, key)
    ctx.verify_mode = ssl.CERT_OPTIONAL
    ctx.check_hostname = False
    ls = socket.socket(); ls.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); ls.bind((ip, cport)); ls.listen(5)
    while True:
        c, a = ls.accept()
        try:
            cmd, *_ = readmsg(c)
            print('connect from', a, hex(cmd), flush=True)
            c.sendall(msg(STLS, 0x01000000, 0))
            readmsg(c)
            t = ctx.wrap_socket(c, server_side=True)
            readmsg(t)                                                         # the client's CNXN again, over TLS
            t.sendall(msg(AUTH, 1, 0, b'0' * 20))                              # an unknown key: it must pair first
            time.sleep(1); t.close()
        except Exception as e:
            print('conn error', e, flush=True)

def pairing():
    ls = socket.socket(); ls.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1); ls.bind((ip, pport)); ls.listen(5)
    while True:
        c, a = ls.accept(); c.close()

for f in (mdns, serve, pairing):
    threading.Thread(target=f, daemon=True).start()
print('fake TV up at', ip, cport, pport, flush=True)
while True:
    time.sleep(3600)
