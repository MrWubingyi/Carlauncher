# 2026-09-09 SD 与服务端去重证据

统一入口：[summary-zh.html](summary-zh.html)。实现说明：[REPLAY_AND_SD.md](../../tools/someip-probe/REPLAY_AND_SD.md)。

- `processor-tests-windows.txt`：8 个纯 C++ 行为用例通过。
- `processor-linux-ctest.log`：Ubuntu 原始 CTest 日志，同样 8 个行为用例，CTest 1/1 通过。
- `verification`：当前全量 JVM JUnit（65 通过）、普通设备 JUnit（66 总数，63 通过、3 探针跳过）、Lint HTML/XML（0 错误、101 警告）。全量 Gradle 退出码 0。
- `sd-attempt`：首次 SD 失败，Android 4 个 Find、Ubuntu ens33 0 包，Ubuntu 无该网卡的组播路由；JUnit/Android 日志及双端 PCAP。
- `sd-routed-attempt`：临时 /32 组播路由后再次失败；Ubuntu 61 个 Offer，Android 4 个 Find，两端未见对方消息，业务 Method 0 包。含路由快照和解析 CSV。
- `method-pass`：最后的真实静态 Method 通过；32 包、8 个不同请求、8 个重复请求、4 次合法状态应用；RC 为两轮 `00,01,01,00`。
- `parser-regression.csv`：解析器对前轮已通过 Method PCAP 的回归输出。

## 执行命令与结果

Windows 纯 C++：`g++ -std=c++17 -Wall -Wextra -Werror -pthread tools/someip-probe/processor_test.cpp` 后运行；退出码 0。
Ubuntu：`cmake -S . -B build && cmake --build build -j2 && ./build/vehicle_processor_test && ctest --test-dir build --output-on-failure`；退出码 0。

Android 构建：`:app:assembleDebug :app:assembleDebugAndroidTest --offline`，退出码 0。
Android 全量：`:app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug --offline --continue`，退出码 0。
SD 显式探针 `VsomeipNativeProbeTest#discoversServiceAndExchangesMethods`，`someipSd=true, someipLocal=10.0.2.16`，两次退出码均为 1（服务可用性超时）。
最终静态 Method `VsomeipNativeProbeTest#requestResponseRejectAndRestart`，`someipPeer=192.168.31.248, someipLocal=10.0.2.16`，退出码 0。

PCAP 解析使用 `inspect-pcap.ps1 -IncludeSd`；去重证据核对使用 `verify-replay-log.ps1`，都在 `tools/someip-probe`。

## 状态与限制

**服务端去重与静态 Method 通过；SD 端到端未通过。** 不能将单端发送 Find/Offer 当作发现成功。
组播问题当前只定位到两端之间的链路，未确定某个具体网络组件有故障。无 LVGL 集成验收。
窗口去重不是无限 exactly-once；未做跨请求顺序检查，Client ID 唯一是假设。
当前设备覆盖率导出仍有 user10/user0 路径问题，`.ec` 无效；未报告覆盖率百分比或门禁通过。

本轮临时组播路由已删除，相关服务及抓包进程已退出。脚本随后完善了信号退出清理的幂等性，经过 shell 语法检查；该整理没有重新运行 SD，也没有改变应用或服务处理器。
