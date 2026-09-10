#!/bin/sh
set -eu
cd /root/develop/carlauncher-probe-20260909
mkdir -p sd-probe
service_pid=
capture_pid=
route_added=0
cleanup() {
    if [ -n "$service_pid" ]; then kill -INT "$service_pid" 2>/dev/null || true; fi
    if [ -n "$capture_pid" ]; then kill -INT "$capture_pid" 2>/dev/null || true; fi
    if [ "$route_added" = 1 ]; then
        route_added=0
        ip route del 224.244.224.245/32 dev ens33
    fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
# Only this experimental multicast group; never replace an existing route or the VPN default.
if [ -n "$(ip -4 route show exact 224.244.224.245/32)" ]; then
    echo 'Explicit multicast route already exists; inspect it before changing anything.' >&2
    exit 1
fi
ip route add 224.244.224.245/32 dev ens33
route_added=1
ip route get 224.244.224.245 >sd-probe/route-during.txt
LD_LIBRARY_PATH=/root/develop/vsomeip/build VSOMEIP_CONFIGURATION="$PWD/service-sd.json" \
    timeout -k 5 -s INT 60 ./build/vehicle_probe_service >sd-probe/service.log 2>&1 &
service_pid=$!
timeout -k 5 -s INT 60 tcpdump -U -ni ens33 'udp port 30490 or udp port 30509' \
    -w sd-probe/discovery.pcap >sd-probe/capture.log 2>&1 &
capture_pid=$!
wait "$service_pid" || true
service_pid=
wait "$capture_pid" || true
capture_pid=
