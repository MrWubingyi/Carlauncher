# 2026-09-10 真实 Service 生命周期验收

统一入口：[summary-zh.html](summary-zh.html)。本轮补齐指定任务要求的真实 Activity 后台、解绑重绑、Service 重启与单次释放；不将旧 JNI 直接启停测试冒充 Service 验收。

## 结论与证据

| 项目 | 本轮结果 | 原始证据 |
| --- | --- | --- |
| 真实生命周期 | 两轮通过；每轮按 Home、Activity onStop 解绑，返回同一 Service，再通过按钮停止；下一轮实例不同 | attempt4 的 JUnit、android-logcat.txt |
| 后台 Method | 两轮后台区间各有 6 个有效响应 | attempt4/lifecycle-check.txt |
| 资源释放 | 两个 Service 各一次 onDestroy、SOMEIP_RELEASED、native app stopped；停止后提交失败、重复 stopService 返回 false | attempt4 的日志及探针断言 |
| 真实链路 | native 提交 23 次、接受 RC=00 响应 23 次；Linux PCAP 92 包：46 Request、46 Response | attempt4/method.pcap、packets.csv、service.log |
| 服务端去重 | 23 次不同请求各出现两份；23 次应用、23 次重复命中，无重复应用 | attempt4/lifecycle-check.txt |
| 配置修复 | 仅更新根 unicast，保留 services[].unicast；同地址不重写，非法 JSON 抛错且文件不变 | verification/device 中 VehicleServiceConfigTest |
| 全量 JVM | 65 通过、0 失败 | verification/unit |
| 全量普通设备 | 68 总数：64 通过、4 个显式探针跳过，0 失败 | verification/device |
| Lint | 0 Fatal、0 Error、101 Warning | verification/lint HTML/XML |

最终显式探针 Gradle 退出 0，用时 13 秒；全量 JVM/设备/Lint 退出 0，用时 1 分 33 秒，包含 UP-TO-DATE 任务，未声称 clean rebuild。Gradle 进度曾显示 Finished 72，但 JUnit XML 实际为 68 个用例（64 通过、4 跳过），本记录以 XML 为准。

**覆盖率无有效本轮报告。** Automotive user10 的 `.ec` 导出仍访问 user0 路径并失败。本轮不使用旧 `.ec`，不报告覆盖率百分比或门禁通过。

## 环境与边界

- Windows Automotive_1408p_landscape / emulator-5554，Android 15，user10，x86_64；Android 本机地址 10.0.2.16。
- Ubuntu root@192.168.31.248，ens33，vSomeIP 3.7.5；仅使用 `/root/develop/carlauncher-probe-20260909/`。PCAP 看到 NAT 后源地址 192.168.31.85。
- Service 0：185194368；Service 1：130262534。原始日志为设备 UTC 时间，运行发生在北京时间 14:18 左右。
- 服务 0x1111 / 实例 0x2222 / Method 0x1001 / Client 0x5566，UDP 30509；16 字节 v0.2 载荷。
- 仅显式启用的探针临时替换应用私有配置为静态远端，finally 恢复或删除该文件；生产 asset 仍启用 SD。探针需要空闲 Service，预授予已声明的普通运行时通知/车速权限，适用于 Gradle 临时安装的测试应用。
- Native 以普通应用 UID 执行；没有改变 SELinux、路由或 PCC 生产代码。TCP ECONNREFUSED 与 SOME/IP 成功同时存在，本轮不依赖 TCP 成功。
- 没有新增 SD 发现通过证据；9 月 9 日两端组播不可互达的阻塞仍有效。未验收 ARM ABI、进程被杀后的恢复、长时间后台保活、SD 模式下 multicast lock 单次释放或 LVGL UI。
- 后台验收窗口每轮约 0.6 秒，证明解绑期间持续 Method 响应，不代表分钟/小时级保活。

## Linux 启动与复现

已编译的服务可在 Linux 终端启动：

```bash
cd /root/develop/carlauncher-probe-20260909
export LD_LIBRARY_PATH=/root/develop/vsomeip/build
export VSOMEIP_CONFIGURATION="$PWD/service.json"
timeout -k 5 -s INT 60 ./build/vehicle_probe_service
```

这是静态 Method 配置，UDP 30509；启动日志需显示 offer 0x1111/0x2222，收到数据后显示 `PROBE_RX ... rc=00 ...`。手动持续运行可省去 timeout，以 Ctrl+C 结束。不要换成存在 Boost 依赖问题的 `/usr/local/lib` 库。

Android 显式验收：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --offline `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.example.carlauncher.someip.VehicleServiceLifecycleProbeTest' `
  '-Pandroid.testInstrumentationRunnerArguments.someipServiceLifecycle=true' `
  '-Pandroid.testInstrumentationRunnerArguments.someipPeer=192.168.31.248' `
  '-Pandroid.testInstrumentationRunnerArguments.someipLocal=10.0.2.16'
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug --offline --continue
.\tools\someip-probe\inspect-pcap.ps1 -Pcap evidence/20260910-service-lifecycle/attempt4/method.pcap -OutputCsv evidence/20260910-service-lifecycle/attempt4/packets.csv
.\tools\someip-probe\verify-service-lifecycle.ps1 -EvidenceDirectory evidence/20260910-service-lifecycle/attempt4
```

Linux 每轮使用独立目录和限时 tcpdump，过滤 `udp port 30509`。最终捕获 92 包，内核丢包 0。`linux-cleanup.txt` 为空表示过滤查询未发现 30490/30509 监听和相关探针/抓包进程；`android-cleanup.txt` 显示无 VehicleSendService。

## 保留的失败记录

- attempt1：后台 Method 已持续收发；ActivityScenario.moveToState(RESUMED) 无法将 AAOS 任务带回前台，超时。
- attempt2：实际 Intent 返回后单轮完整生命周期通过；ActivityScenario.close 内部状态为空，测试仍失败。
- attempt3：直接 Instrumentation 驱动完成首轮；普通运行时权限弹窗滞留，第二次 startActivitySync 超时。失败后清理测试应用。
- attempt4：显式联调先授予声明的运行时权限，两轮完整流程及全部断言通过。前三轮不是产品生命周期失败的证据，也不能记成探针通过。

源码基线提交：`61ce4f7cbeab4ee5e20ad8123a8bf8885b3512f1`。本轮最终源码与证据通过 `SHA256SUMS.txt` 定位；历史失败时的探针与最终探针存在上述差异。原始证据使用 Git `-text` 保存，防止换行转换影响哈希。

下一增量：Ubuntu Adapter 将验证后的数据写入线程安全快照，由 LVGL UI 线程 Timer 读取并更新 speed/gear/connection。仍需独立完成构建、线程图和三态截图；本轮未将整个 WP2/WP3 标为 Done。
