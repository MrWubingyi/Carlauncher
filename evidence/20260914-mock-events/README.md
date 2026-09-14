# 2026-09-14：统一 mock Event 双客户端验证

已实现并验证：远端 `vehicle_mock_service` 统一生成车辆状态，Android 与 LVGL
分别订阅 `0x1111/0x2222` 的 `0x8001` Event（EventGroup `0x0001`）。

## 真实运行证据

- [Android UI 日志](android-event-ui.log)：实际 Activity 的速度控件连续显示
  `seq=1429..1439`，速度 `58..78 km/h`；测试同时断言 TCP 未连接。
- [两端对照结果](matched-ui-events.json)：上述 11 个 Android UI 序列/速度均与
  [LVGL 接收记录](remote/lvgl.log) 完全一致。
- [LVGL 实际标签记录](remote/lvgl-ui.log)：`LVGL_UI seq=2517..2527`
  对应控件文字 `34..54`；记录来自真实 `ui_bridge` 定时器中 `lv_label_get_text`。
  使用实际 dashboard 程序及 SDL dummy/software，不包含物理显示器画面验收。
- [发布器日志](remote/service.log) 与 [抓包](remote/sd-method.pcap)：真实 SD/Event 通信。
  pcap 名称沿用旧 helper，内容包含本次 Event。
- LVGL 记录了静默期间 3 秒超时，并在 `seq=1000/2200/3400` 恢复。
- [Android 专项 JUnit](instrumentation)：1 项真实 Event → Service → Repository → UI 测试通过。
- [Android JVM JUnit](unit)：69 项通过，包括新增完整快照解码、越界/缺失/类型错误和 Event watchdog。
- 远端 CTest：`vehicle_mock_timeline`、`vehicle_processor_behavior` 两项通过。
- [最终设备回归 JUnit](regression)：69 项中 64 项通过、5 项显式网络探针跳过，0 失败。
- [Android Lint](lint-results-debug.html)：0 错误、101 警告。
- 最终命令 `:app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug --offline --continue`
  退出 0，`BUILD SUCCESSFUL in 1m 31s`。Android APK/JNI 和远端 mock/LVGL 均已编译通过。

## 修改和复现

[启动与接口说明](../../tools/someip-probe/MOCK_EVENTS.md)。
[LVGL 集成补丁](lvgl-event-integration.patch) 包含 CMake、main 和可选 UI trace 修改；
新增订阅器源码及可重复执行安装脚本保存在 `tools/someip-probe`。

远端目录为 `/root/develop/carlauncher-probe-20260909` 和
`/root/develop/dashboard_simulator`，已实际修改并编译。Android 当前入口
只启动 Event 订阅；旧 Java mock/Method 探针保留用于离线兼容回归。

## 验证边界与环境恢复

模拟器使用已验证的临时 QEMU Ethernet/TAP 通道；内层是 SOME/IP UDP/SD。
默认模拟器 NAT 无法直接代替该通道。Ubuntu 临时 TAP、精确组播路由、
mock/LVGL 测试进程均已自动清理；Android 恢复 `10.0.2.16` 默认网络完成回归后关闭。

首次无头 LVGL 启动缺少软件 SDL renderer，修正后通过。
普通回归中的旧 Method 文案断言已更新。临时网络结束后一次 ADB 属性查询超时，
恢复默认网络后重新执行最终回归。AGP 输出了 coverage.ec 的 run-as 警告，
本轮不宣称设备覆盖率结果；以 JUnit 记录判定测试。
