# 服务端重复请求与 SD 探针

## 重复请求处理契约

`vehicle_request_processor.hpp` 是不依赖 vSomeIP/Android/LVGL 的内存状态处理器。
`service.cpp` 调用它后发送响应，后续 Ubuntu 状态仓库可以复用该处理逻辑。

| 项目 | 行为 |
| --- | --- |
| 重复身份 | Client ID + Session ID + 完整 16 B payload；服务/方法由 handler 固定 |
| 默认窗口 | 自首次到达起 5 秒，重复请求不延长窗口 |
| 默认容量 | 256 条；缓存满时 RC=1 / CACHE_FULL，不淘汰仍有效的身份 |
| 重复请求 | 返回首次 RC；不再次写入 snapshot，不增加 applied_count |
| 非法帧 | RC=1，不修改 snapshot；16 B 非法帧的响应也可复用 |
| Session 重用 | 不同 payload 视为新请求，支持客户端重启后的 Session 重用 |
| 并发 | 查重、登记身份和更新 snapshot 在同一互斥锁内完成 |

这是一段进程内的有限去重窗口，不提供跨进程重启或无限时间的 exactly-once 保证。
假设部署中的 Client ID 唯一。不同 Session 的相同 payload 不是重复身份；未实现跨请求的 seq 新旧排序，
当前 snapshot 使用最后接收的合法新请求。接入 LVGL 前需另行确定多客户端、重启和旧帧处理策略。

## SD 模式

Ubuntu 用 `service-sd.json`，Android 运行 `discoversServiceAndExchangesMethods`。
Android 配置只含本机地址与 SD 参数，没有远端地址和静态 services 表。
只有 SD 发现产生 AVAILABLE，才继续两轮 Method 验证。已有静态 Method 探针保持独立。

Android 启用 SD 时持有 Wi-Fi MulticastLock，在启动失败或停止时释放；需要普通权限
`CHANGE_WIFI_MULTICAST_STATE`。组播锁只能取消设备的 Wi-Fi 接收过滤，不能代替网络设备转发组播。

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.carlauncher.someip.VsomeipNativeProbeTest#discoversServiceAndExchangesMethods' '-Pandroid.testInstrumentationRunnerArguments.someipSd=true' '-Pandroid.testInstrumentationRunnerArguments.someipLocal=10.0.2.16' --offline
```

Ubuntu 的 `run-sd-probe.sh` 将服务和 `ens33` 抓包限制为 60 秒（再留 5 秒强制退出期限）。
脚本临时增加 `224.244.224.245/32 dev ens33`，避免实验组播走 VPN；检测到既有精确路由时拒绝覆盖，
退出时只删除本次增加的路由。不会更换默认路由。
模拟器可使用现有 adb shell 权限，执行 45 秒 `tcpdump -ni wlan0 'udp port 30490 or udp port 30509'`。
应用本身仍以普通应用 UID 运行，不需要给应用 root，也不需要修改 SELinux。

```powershell
& .\tools\someip-probe\inspect-pcap.ps1 -Pcap <capture.pcap> -OutputCsv <packets.csv> -IncludeSd
```

CSV 包含 SD entry 的 Find/Offer、Service/Instance、TTL 与 IPv4 Endpoint；并保留原始 payload hex。
解析器只处理本实验的 Ethernet + 非分片 IPv4 UDP PCAP，不是通用 Wireshark 替代品。
必须结合双端抓包确认流向：只有本端发送 Find 或 Offer 不能宣称已完成发现。

## 验证命令

```bash
cmake -S . -B build
cmake --build build -j2
./build/vehicle_processor_test
ctest --test-dir build --output-on-failure
```

纯 C++ 测试覆盖合法解码、重复消费、错误帧、边界、过期、容量、身份区别和并发；没有真实等待。
本轮联调材料统一放在 `evidence/20260909-sd-replay`。

## 2026-09-09 实测

去重处理器在 Windows 和 Ubuntu 的 8 个行为用例通过；两轮真实 Method 通过。
Ubuntu 收到 16 个请求，其中 8 个重复；4 个合法的新请求只更新了 4 次状态，非法帧不更新。
可以用 `verify-replay-log.ps1 -Log <service.log>` 核对本探针的响应和应用次数。

SD 尚未通过：首次 Ubuntu 缺少 ens33 组播路由，修正后 ens33 抓到 61 个 Offer（TTL=3，Endpoint 为
192.168.31.248:30509/UDP），Android wlan0 抓到 4 个 Find。两端均没有收到对方消息，未进入 Method 阶段。
当前证据定位到两端之间的组播传输链路，尚未定位具体是模拟器、宿主机、虚拟机网络还是其他转发环节。
后续需在支持双向组播互通的链路上重测；不以本地发送成功或静态 Method 通过替代 SD 验收。

参考：[COVESA vSomeIP 配置](https://github.com/COVESA/vsomeip/blob/master/documentation/vsomeipConfiguration.md)、
[Android 模拟器网络地址](https://developer.android.com/studio/run/emulator-networking-address)。
