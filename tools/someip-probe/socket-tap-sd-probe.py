"""Temporary QEMU socket -> Linux TAP lab, bounded and cleaned in finally.

Run as root on the existing Ubuntu probe host. Ethernet frames use QEMU's
4-byte big-endian length prefix over a TCP connection from the Windows host.
"""
import fcntl
import json
import os
import select
import signal
import socket
import struct
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path('/root/develop/carlauncher-probe-20260909')
RUN = sys.argv[1] if len(sys.argv) == 2 else 'socket-tap-sd-20260910'
if not RUN.startswith('socket-tap-sd-') or any(c not in 'abcdefghijklmnopqrstuvwxyz0123456789-' for c in RUN):
    raise ValueError('Invalid experiment directory name')
OUT = ROOT / RUN
TAP = 'cl-sd-tap'
GROUP = '224.244.224.245/32'
LOCAL = '10.203.0.1'
children = []
logs = []
tap = None
listener = None
connection = None
route_added = False
running = True

def run(*command):
    return subprocess.check_output(command, text=True).strip()

def start(name, command, env=None):
    output = open(OUT / name, 'w')
    logs.append(output)
    process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT, env=env)
    children.append(process)
    return process

def stop_signal(signum, frame):
    global running
    running = False

for sig in (signal.SIGINT, signal.SIGTERM):
    signal.signal(sig, stop_signal)

try:
    if OUT.exists():
        raise RuntimeError('Evidence directory exists; preserve previous run')
    if run('ip', '-4', 'route', 'show', 'exact', GROUP):
        raise RuntimeError('Existing multicast route; refusing to replace it')
    if subprocess.run(['ip', 'link', 'show', TAP], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
        raise RuntimeError('TAP name already in use')
    if run('ip', '-4', 'route', 'show', 'exact', '10.203.0.0/24'):
        raise RuntimeError('Test subnet already in use')
    OUT.mkdir()
    tap = os.open('/dev/net/tun', os.O_RDWR)
    fcntl.ioctl(tap, 0x400454ca, struct.pack('16sH', TAP.encode(), 0x0002 | 0x1000))
    run('ip', 'addr', 'add', LOCAL + '/24', 'dev', TAP)
    run('ip', 'link', 'set', TAP, 'up')
    run('ip', 'route', 'add', GROUP, 'dev', TAP)
    route_added = True
    (OUT / 'routes-during.txt').write_text(run('ip', '-4', 'route') + '\n')
    config = json.loads((ROOT / 'service-sd.json').read_text())
    config['unicast'] = LOCAL
    config_path = OUT / 'service-sd.json'
    config_path.write_text(json.dumps(config, indent=2))
    start('dhcp.log', ['dnsmasq', '--keep-in-foreground', '--conf-file=/dev/null', '--port=0',
          '--interface=' + TAP, '--bind-interfaces', '--dhcp-authoritative',
          '--dhcp-range=10.203.0.2,10.203.0.2,255.255.255.0,10m',
          '--dhcp-option=3,' + LOCAL, '--dhcp-option=6,' + LOCAL,
          '--dhcp-leasefile=' + str(OUT / 'dhcp.leases'), '--log-dhcp', '--log-facility=-'])
    start('capture.log', ['tcpdump', '-U', '-ni', TAP, '-w', str(OUT / 'sd-method.pcap'),
          'udp port 30490 or udp port 30509 or udp port 67 or udp port 68'])
    environment = dict(os.environ, LD_LIBRARY_PATH='/root/develop/vsomeip/build',
                       VSOMEIP_CONFIGURATION=str(config_path))
    start('service.log', [str(ROOT / 'build/vehicle_probe_service')], environment)
    listener = socket.socket()
    listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    listener.bind(('192.168.31.248', 37492))
    listener.listen(1)
    listener.settimeout(1)
    print('READY tcp=192.168.31.248:37492 tap=' + TAP, flush=True)
    deadline = time.monotonic() + 420
    while running and time.monotonic() < deadline:
        try:
            candidate, peer = listener.accept()
            if peer[0] != '192.168.31.85':
                candidate.close()
                continue
            connection = candidate
            break
        except socket.timeout:
            pass
    if connection is None:
        raise RuntimeError('No emulator connection within deadline')
    connection.settimeout(2)
    print('CONNECTED peer=' + str(peer), flush=True)
    buffer = bytearray()
    inbound = outbound = 0
    while running and time.monotonic() < deadline:
        if any(child.poll() is not None for child in children):
            raise RuntimeError('A child process exited; inspect logs')
        ready, _, _ = select.select([tap, connection], [], [], 0.5)
        if connection in ready:
            chunk = connection.recv(65536)
            if not chunk:
                break
            buffer.extend(chunk)
            while len(buffer) >= 4:
                length = struct.unpack('!I', buffer[:4])[0]
                if length < 14 or length > 65536:
                    raise RuntimeError('Invalid Ethernet frame length')
                if len(buffer) < length + 4:
                    break
                frame = bytes(buffer[4:length + 4])
                del buffer[:length + 4]
                os.write(tap, frame)
                inbound += 1
        if tap in ready:
            frame = os.read(tap, 65536)
            connection.sendall(struct.pack('!I', len(frame)) + frame)
            outbound += 1
    print(json.dumps({'emulator_to_tap': inbound, 'tap_to_emulator': outbound}), flush=True)
finally:
    for child in reversed(children):
        if child.poll() is None:
            child.send_signal(signal.SIGINT)
    for child in reversed(children):
        try:
            child.wait(timeout=5)
        except subprocess.TimeoutExpired:
            child.kill()
            child.wait()
    for output in logs:
        output.close()
    if connection is not None:
        connection.close()
    if listener is not None:
        listener.close()
    if route_added:
        subprocess.run(['ip', 'route', 'del', GROUP, 'dev', TAP], check=False)
    if tap is not None:
        os.close(tap)  # Nonpersistent TAP disappears here, including its subnet route.
    print('CLEANUP_COMPLETE', flush=True)
