"""Bounded interface-specific multicast diagnostic; no routing/firewall changes."""
import argparse
import json
import select
import socket
import time

parser = argparse.ArgumentParser()
parser.add_argument('--local', required=True)
parser.add_argument('--label', required=True)
parser.add_argument('--seconds', type=int, default=25)
args = parser.parse_args()
group, port = '224.244.224.245', 37090
udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
udp.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
udp.bind(('', port))
udp.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP,
               socket.inet_aton(group) + socket.inet_aton(args.local))
udp.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_IF, socket.inet_aton(args.local))
udp.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 1)
payload = ('CARLAUNCHER_MCAST_PATH:' + args.label).encode('ascii')
deadline = time.monotonic() + min(max(args.seconds, 1), 60)
sent = received = 0
print(json.dumps({'event': 'start', 'time': time.time(), 'local': args.local}), flush=True)
try:
    while time.monotonic() < deadline:
        udp.sendto(payload, (group, port))
        sent += 1
        next_send = min(time.monotonic() + 0.4, deadline)
        while time.monotonic() < next_send:
            if select.select([udp], [], [], max(0, next_send - time.monotonic()))[0]:
                data, address = udp.recvfrom(1024)
                if data.startswith(b'CARLAUNCHER_MCAST_PATH:') and data != payload:
                    received += 1
                    print(json.dumps({'event': 'receive', 'time': time.time(),
                                      'peer': address, 'payload': data.decode('ascii')}), flush=True)
finally:
    udp.close()
print(json.dumps({'event': 'end', 'time': time.time(), 'sent': sent, 'received': received}), flush=True)
