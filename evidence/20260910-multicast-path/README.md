# 2026-09-10 组播路径对照与 TCP 二层通道

**结论：在隔离 socket/TAP 拓扑中，真实 SOME/IP-SD 与两轮 Method 验证通过。默认模拟器 NAT 拓扑仍没有外部组播互通证据。**

统一报告：[summary-zh.html](summary-zh.html)。完整解释、启动与恢复步骤：[开发记录](../../docs/2026-09-10-SOMEIP-组播经TCP二层通道.md)。

## 证据矩阵

| 阶段 | 结果 | 证据 |
| --- | --- | --- |
| 现有拓扑只读检查 | Android 10.0.2.16；Windows 192.168.31.85；Ubuntu ens33 192.168.31.248、ens37 192.168.50.128；组播默认走 tun0 | 前轮 Method PCAP、linux-restored.txt |
| Windows / Ubuntu 组播 | 指定 socket 发送接口后双向收到标记；Windows 119、Ubuntu 115（含重复） | windows-paired.txt、ubuntu-paired.txt、windows-ethernet.pcapng |
| 直接 QEMU connect | 远端与回环 connect 均 EAGAIN；回环转接时 TAP 0 入站帧；非 SD 失败 | emulator-socket-error.log、emulator-socket-local-error.log、socket-tap-run.txt |
| QEMU listen + TCP 转接 | TAP 收到 180 帧，发送 569 帧；DHCP 分配 10.203.0.2 | socket-forward.txt、socket-tap-listen-run.txt、sd-socket/dhcp.log |
| SD + Method | 1 个探针通过，包含两轮发现/收发/启停，Gradle 退出 0，10 秒 | sd-socket/gradle.log、JUnit XML、android-logcat.txt |
| 抓包核验 | 2 Find、150 组播 Offer、2 单播 Offer；8 Request / 8 Response；RC 两轮 00/01/01/00 | sd-socket/results.json、packets.csv、pcap-check.txt |
| 资源恢复 | TAP、精确组播路由和辅助进程消失；Android 回到 10.0.2.16 | linux-restored.txt、android-restored.txt、两个 helper 日志的 CLEANUP_COMPLETE |

原始 `sd-socket/sd-method.pcap` 是完整 TAP 抓包，182 包，包括 DHCP；0 kernel dropped。现有解析器仅面向 SOME/IP，直接喂 DHCP 会报长度错误，因此保留原始文件并导出 170 包的 `someip-only.pcap` 后解析：

```powershell
& 'D:\Program Files\Wireshark\tshark.exe' -r evidence/20260910-multicast-path/sd-socket/sd-method.pcap -Y 'udp.port == 30490 || udp.port == 30509' -F pcap -w evidence/20260910-multicast-path/sd-socket/someip-only.pcap
.\tools\someip-probe\inspect-pcap.ps1 -Pcap evidence/20260910-multicast-path/sd-socket/someip-only.pcap -OutputCsv evidence/20260910-multicast-path/sd-socket/packets.csv -IncludeSd
```

`ubuntu-group-probe.txt` 是最初未保证同时运行的初探，收到 0；不能据此判断网络不通。随后使用有时间戳、并行运行的 paired 探针和 PCAP 得到有效结果。

## 来源与范围

- Android Emulator 36.6.11，本地 `-help-wifi-socket` 输出保存在 emulator-wifi-socket-help.txt，明确默认 user-mode NAT 与 socket 二层模式区别。
- [Android 官方网络限制](https://developer.android.com/studio/run/emulator-networking-address) 与 [36.5+ 虚拟 Wi-Fi 互联说明](https://developer.android.com/studio/run/emulator-networking-interconnect)。
- 使用已有源码提交 `988fc7d4bcdc330edd791048436ed485bcffd132` 的 APK/测试，未改 Android 生产代码和测试断言。脚本与本轮证据的 SHA-256 见 SHA256SUMS.txt；测试构建含 UP-TO-DATE，未声称 clean build。
- 本轮没有新增全量 JVM、普通设备、Lint 或覆盖率运行。前轮完整回归见 [Service 生命周期报告](../20260910-service-lifecycle/summary-zh.html)。本轮 `.ec` 导出仍因 user10/user0 路径失败，不计为覆盖率通过。
- 临时 Ubuntu 接口、DHCP 与精确组播路由是此方案的一部分；默认路由、VPN、防火墙与业务网卡均未修改。不是零系统网络操作，也不是生产部署或长期性能验收。
- 恢复后模拟器仍正常运行；其两个持续写入的 emulator-restored*.log 不归档、不计入哈希，启动输出的固定快照另存为 emulator-restored-startup-snapshot.txt。
- 开启 SD 时无需静态 endpoint；抓包中的真实 Offer 提供 `10.203.0.1:30509`。TCP 仅封装传输原始以太网帧，辅助程序不生成 SD Offer/Method Response。
