# 2026-09-09 Android 网络适配与真实 Method 证据

入口：[summary-zh.html](summary-zh.html)。本轮在原有独立 SOME/IP 状态基础上继续开发，保留每次失败，不覆盖前轮证据。

## 结果

- 网络适配：ConnectivityManager 的实际地址、路由、访问阻塞/丢失回调进入 vSomeIP Android connector，外部路由启动成功。
- 真实 Method：两轮各 4 次，合法 → 速度越界 → schema 错误 → 合法恢复；返回码均为 `00,01,01,00`，重复启停通过。
- JNI 按 Client/Session 消费首个待处理响应，清理 3 秒过期请求，停止/网络丢失清空 pending；本次抓包中的 8 个重复响应被忽略。
- JVM：65 通过。普通设备 JUnit：65 总数，63 通过、2 个 opt-in 探针跳过。两个真实探针另行执行，各 1 项通过；生命周期探针含 3 轮注册启停。
- 最终全量 Gradle 退出码 0；Lint：0 fatal / 0 error / 101 warnings；APK 构建成功。
- 覆盖率：设备在 user10，自动导出错误访问 `/data/data` 的 user0 路径，本轮 .ec 无效；旧 .ec 不计入。未生成新 JaCoCo 百分比，未宣称覆盖率门禁通过。

## 证据目录

- `attempt1-udp-bind`：Android 通知已启动外部路由；内核 IPv4 路由缺失，UDP `Network is unreachable (101)`。抓包 0。系统 LinkProperties 仍报告旧网关，正常 Wi-Fi 重连后 DHCP 恢复内核路由。未手写路由或改变 SELinux。
- `attempt2-return-code`：真实响应到达，但重复的 Session 1 成功响应被误当作下一请求结果；保留失败 JUnit、双端日志、PCAP。
- `method-pass`：加入请求关联后最终通过，含 JUnit、Android 日志、Ubuntu 日志、32 包 PCAP、解析 CSV 与关联摘要。16 REQUEST / 16 RESPONSE，8 组不同请求，各重复 2 次。丢包统计 0。
- `lifecycle-pass`：最终代码的 3 轮真 native 注册、重复启停原始日志和 JUnit。
- `verification`：当前完整 JVM/普通设备 JUnit、Lint HTML/XML。
- `vendor-network.patch`：对依赖现有两文件的差异；新增 include 和可重放脚本在 `tools/vsomeip-android`。库及 APK 散列见 `build-artifacts.txt`。

## 命令

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.carlauncher.someip.VsomeipNativeProbeTest#requestResponseRejectAndRestart' '-Pandroid.testInstrumentationRunnerArguments.someipPeer=192.168.31.248' '-Pandroid.testInstrumentationRunnerArguments.someipLocal=10.0.2.16' --offline
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.carlauncher.someip.VsomeipNativeProbeTest#nativeRegistrationAndRepeatedStopAreSafe' '-Pandroid.testInstrumentationRunnerArguments.someipLocal=10.0.2.16' '-Pandroid.testInstrumentationRunnerArguments.someipLifecycle=true' --offline
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug :app:assembleDebug --offline --continue
& .\tools\someip-probe\inspect-pcap.ps1 -Pcap evidence/20260909-android-network/method-pass/method.pcap -OutputCsv evidence/20260909-android-network/method-pass/packets.csv
```

Ubuntu 使用 `tools/someip-probe/run-network-adapter-probe.sh` 在独立目录运行限时服务与抓包。本轮远程进程已退出，业务端口已释放。

## 尚未验收

静态单播不等于 SD 组播发现；没有运行 LVGL 状态写入。重复 REQUEST 的来源尚未定位，未来服务端需按业务语义幂等/去重；客户端去重不提供 exactly-once 执行。
真实断网/漫游恢复尚未做设备操作验收，当前地址变化和丢失依据自动化状态测试。更换本地 IP 后要停止/启动 Service，尚不自动迁移 endpoint。
Android 24–32 缺少公开 RouteInfo type 检查，本补丁对 SD 路由保持不可用；本轮实测 Android 15 / API 35 / x86_64。
