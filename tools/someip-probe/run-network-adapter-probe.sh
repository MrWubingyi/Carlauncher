#!/bin/sh
set -eu
cd /root/develop/carlauncher-probe-20260909
mkdir -p android-network-adapter
LD_LIBRARY_PATH=/root/develop/vsomeip/build VSOMEIP_CONFIGURATION="$PWD/service.json" \
    timeout -s INT 120 ./build/vehicle_probe_service >android-network-adapter/service.log 2>&1 &
service_pid=$!
timeout -s INT 120 tcpdump -U -ni ens33 'udp port 30509' \
    -w android-network-adapter/method.pcap >android-network-adapter/capture.log 2>&1 &
capture_pid=$!
trap 'kill -INT "$service_pid" "$capture_pid" 2>/dev/null || true' INT TERM EXIT
wait "$service_pid" || true
wait "$capture_pid" || true
trap - INT TERM EXIT
