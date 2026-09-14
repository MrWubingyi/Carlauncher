# SOME/IP Method 与 native 生命周期探针

2026-09-14：新增远端统一 mock Event 服务，Android 和 LVGL 直接订阅。
当前运行方式见 [MOCK_EVENTS.md](MOCK_EVENTS.md)。以下 Method 说明仅适用于历史独立探针。

关闭模拟器、前台启动模拟器和 Android Studio Run 步骤见
[项目 README](../../README.md)。
前台双界面联调必须使用 `socket-tap-event-probe.py --network-only`，
然后用同一运行目录的生成配置启动唯一 mock 和可见 LVGL。
不带此参数的旧模式会启动隔离 mock/无头 LVGL，仅用于自动测试。

这是一套显式启用的实验工具，不接入 LVGL，也不修改已有 Ubuntu 服务。
身份固定为 Service `0x1111` / Instance `0x2222` / Method `0x1001`，UDP `30509`。
16 B payload 与 `SomeipPayloadCodec` 的 schema v1 一致。服务校验版本、长度、
时间戳非零和字段范围，返回空 payload 的 RESPONSE，Return Code 为 `0x00/0x01`。

本工具使用静态单播隔离 Method 层；不能作为 SOME/IP-SD 组播或 LVGL 接入验收。

## Ubuntu 服务

修改 `service.json` 顶层 `unicast` 为实际本机 IP。在独立目录构建：

```bash
cmake -S . -B build
cmake --build build -j2
LD_LIBRARY_PATH=/root/develop/vsomeip/build \
VSOMEIP_CONFIGURATION="$PWD/service.json" \
timeout -s INT 90 ./build/vehicle_probe_service >service.log 2>&1
```

当前实验机 `/usr/local/lib/libvsomeip3-cfg.so` 缺少 Boost 1.75 动态库，
上述 `LD_LIBRARY_PATH` 选用已经能够启动的成套构建库；不要混用系统安装与源码构建产物。
未修改系统库配置或路由。另一个终端可运行限时抓包：

```bash
timeout -s INT 90 tcpdump -U -ni ens33 'udp port 30509' -w method-probe.pcap
```

## Android 真 native 验证

确认模拟器当前 IPv4，然后在 CarLauncher 根目录运行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.carlauncher.someip.VsomeipNativeProbeTest#requestResponseRejectAndRestart' '-Pandroid.testInstrumentationRunnerArguments.someipPeer=192.168.31.248' '-Pandroid.testInstrumentationRunnerArguments.someipLocal=10.0.2.16' --offline
```

测试配置写入应用私有 `vsomeip-probe.json`，不会覆盖 Service 使用的配置。
预期为两轮合法帧成功、速度越界拒收、schema 错误拒收、合法帧恢复与 native 重启。
调用超时会明确失败，不会把未收到响应当作成功。客户端 native TX/RX 日志提供
Client ID / Session ID，TX 另外提供应用 seq 和完整 payload hex，可与 PCAP 关联。
`nativeSendState` / `buildAndSend` 返回 true 仅表示提交给 vSomeIP，不表示对端收到。

独立验证 native 注册和启停（不要求 Ubuntu 对端可用）：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.carlauncher.someip.VsomeipNativeProbeTest#nativeRegistrationAndRepeatedStopAreSafe' '-Pandroid.testInstrumentationRunnerArguments.someipLocal=10.0.2.16' '-Pandroid.testInstrumentationRunnerArguments.someipLifecycle=true' --offline
```

普通设备测试不会启用上述两个探针，JUnit 将其记录为跳过；真实运行结果必须单独记录。

## 2026-09-09 实测边界

已经修复 `/tmp/vsomeip.lck` 与 `/tmp/vsomeip-0` 的权限错误：JNI 在首次创建
vSomeIP application 前设置 `VSOMEIP_BASE_PATH` 为配置所在的应用私有目录。
该路径由库首次读取后缓存，同一进程应保持不变。

真实注册与三轮启停通过；Method 探针仍失败。原因是普通 Android 应用的
`NETLINK_ROUTE` bind 被拒绝，当前库等待网卡通知后才启用外部路由。
后续需要在 vSomeIP 的 Android 网络适配层接入平台允许的真实网络状态通知，
处理网络可用、丢失和接口变化，再重跑 Method 探针；不能用固定“网络在线”值、
关闭 SELinux 或 TCP 成功替代此验收。现有 App 的 SOME/IP-SD 默认模式没有被关闭。

### 同日后续：Android 网络适配后 Method 已通过

已接入 ConnectivityManager 回调及 [vSomeIP Android connector 补丁](../vsomeip-android/README.md)。
首次适配后外部路由成功启动，但模拟器 IPv4 内核路由缺失；通过正常 Wi-Fi 重连恢复 DHCP。
随后真实收发暴露重复 UDP 响应，JNI 增加 Client/Session 匹配、首个响应消费和 3 秒过期过滤。
最后两轮合法→非法速度→非法 schema→合法恢复、重复启动与停止全部通过。
静态单播不会证明对端服务在 AVAILABLE 时真实在线，只有匹配的 RESPONSE 才算成功。
原始失败与成功材料均保留在 [本轮汇总](../../evidence/20260909-android-network/summary-zh.html)。
