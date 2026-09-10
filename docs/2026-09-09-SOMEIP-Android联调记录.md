# CarLauncher：SOME/IP 独立状态与 Android native 联调（2026-09-09）

## 已完成部分

- `VehicleRepository` 和座舱界面改为读取 SOME/IP 自身状态，不再用 TCP 在线代表 SOME/IP 成功。
- `SomeipConnectionMonitor` 区分启动、服务不可用、等待响应、成功、Return Code 错误、响应超时和停止。只有真实 RESPONSE 且 RC=0 才进入 ONLINE；服务可用后 3 秒未收到响应进入超时，重复 AVAILABLE 不延长计时。
- Java Service 的 native 启停通过进程级单线程执行器串行执行；停止后发送返回 false。JNI 修复 handler 引用环、重复启停和发送/停止竞争。
- JNI 在首次创建 vSomeIP application 前将 `VSOMEIP_BASE_PATH` 设置为应用私有文件目录，修复 `/tmp/vsomeip.lck` 和 `/tmp/vsomeip-0` 权限错误。
- 增加独立 Ubuntu Method 探针，使用静态单播隔离 Method 层，校验 16 B payload，返回 RC=0/1。TX/RX 日志包含 Client ID、Session ID，TX 另含 seq 和 payload hex。

## 接口与验收边界

Service `0x1111` / Instance `0x2222` / Method `0x1001`；Android Client `0x5566`；UDP `30509`。
payload 为大端：schema(u8)=1、seq(u32)、timestampMs(u64)、speed(u8)、gear(u8)、dataStatus(u8)。共 16 B。

`nativeStart=true` 只表示初始化及线程启动；AVAILABLE 只表示路由层认为服务可用；`nativeSendState=true` 只表示提交到 vSomeIP。
这些都不能替代真实 Method RESPONSE。静态单播探针也不能替代 SOME/IP-SD 组播和 LVGL 集成验收。

## 本轮原始验证结果（网络适配开发前）

| 验证层 | 结果 |
| --- | --- |
| JVM 回归 | 62 通过 |
| 普通设备回归 | 63 项：61 通过、2 项显式探针跳过 |
| 真 native 生命周期探针 | 1 项通过，包含 3 轮注册、重复启动、重复停止 |
| 真 Method 探针 | 2 次尝试均失败，尚未验收 |
| Android Lint | 0 fatal / 0 error / 101 warnings |
| 覆盖率 | 本轮设备 .ec 导出无效，旧 user10 .ec 不计入；未宣称覆盖率门禁通过 |

第一次 Method 尝试暴露 `/tmp` 权限问题；修复后第二次已注册成功，但普通 Android 应用绑定 `NETLINK_ROUTE` 被拒绝。
vSomeIP 等待网卡状态通知后才启动外部 IP 路由，因此客户端没有发出业务 UDP，Ubuntu 抓包为 0 个包。
Ubuntu `/usr/local/lib` 的 cfg 插件另有 Boost 1.75 缺失；探针通过 `LD_LIBRARY_PATH=/root/develop/vsomeip/build` 使用成套源码构建库启动。

## 下一步开发

为 Android 接入 `ConnectivityManager.NetworkCallback` / `LinkProperties` 的真实地址和路由信息，替换 Android 上受限的 NETLINK 监听；处理网络丢失、地址变化及停止后的迟到回调。
随后重跑合法帧、非法帧拒收、恢复和重启两轮 Method 探针，并保存 Android 日志、Ubuntu 日志和 PCAP。
不能用固定“网络在线”、TCP 成功或修改 SELinux 替代验证。当前仍不把 WP2 / SD / LVGL 标为完成。

## 可复现材料

- [原始汇总报告](file:///D:/Android/AndroidStudioProjects/CarLauncher/evidence/20260909-someip-method/summary-zh.html)
- [证据索引及 SHA256 清单](file:///D:/Android/AndroidStudioProjects/CarLauncher/evidence/20260909-someip-method/README.md)
- [探针源码与完整命令](file:///D:/Android/AndroidStudioProjects/CarLauncher/tools/someip-probe/README.md)
- [状态契约](file:///D:/Android/AndroidStudioProjects/CarLauncher/docs/someip-status.md)

这份笔记保留“网络适配开发前”的结果；后续结果追加记录，不覆盖失败证据。

## 同日继续开发：网络适配与真实 Method 已通过

### 代码落地

新增 `AndroidNetworkMonitor`，订阅 `ConnectivityManager.NetworkCallback`，按配置 IP 匹配 LinkProperties 地址与路由，处理访问受限、网络丢失和停止后的迟到事件。
`NetworkStateTracker` 把选网和可用性判断提取为可测试的 Java 逻辑。
JNI 将这些真实通知送入 Android 专用 vSomeIP connector；connector 通过 io strand 串行处理，弱引用和 generation 检查隔离旧生命周期事件。
依赖补丁和重建脚本保存在项目 `tools/vsomeip-android`，已重建 `libvsomeip3.so` 并打包 APK。

实际收发发现同一 Session 的 UDP 响应到达两次。JNI 现在记录待处理的 Client/Session，只消费首个匹配的终态响应；重复、未匹配及超过 3 秒的响应不更新 UI，停止/网络丢失清空 pending。
这让“成功”对应尚未完成的真实请求，避免上一请求的重复成功响应串入下一请求。

### 联调过程与证据

1. 网络适配成功启动外部路由，但 UDP 报 `Network is unreachable (101)`。模拟器系统 LinkProperties 仍报告旧网关，内核 IPv4 路由缺失；通过正常 Wi-Fi 重连刷新 DHCP 恢复。没有手写路由或更改 SELinux。
2. 首次收到真实响应后，重复响应导致非法帧用例误取到上一帧的 RC=0；保存失败证据并修复请求关联。
3. 最后两轮各 4 请求全部通过：合法、速度越界、schema 错误、合法恢复，RC 顺序均为 `00、01、01、00`；重复启动/停止通过。

| 轮次 | seq | Session | RC |
| --- | --- | --- | --- |
| 1 | 16909060、16909061、16909062、16909063 | 0001、0002、0003、0004 | 00、01、01、00 |
| 2 | 16909070、16909071、16909072、16909073 | 0001、0002、0003、0004 | 00、01、01、00 |

Ubuntu PCAP 共 32 包：16 REQUEST、16 RESPONSE，对应 8 组不同请求，各重复 2 次，抓包丢弃 0。
Android 接受 8 个匹配响应并忽略另外 8 个重复响应；关联核对包含双向 IP/端口、Client、Session 和 payload seq。

### 最终验证

- JVM 全量：65 通过。
- 普通设备 JUnit：65 总数，63 通过、2 个 opt-in 探针跳过、0 失败。
- 真 Method 探针：1 项通过，含两轮请求响应和重启；真 native 生命周期探针：1 项通过，含 3 轮注册启停。
- Lint：0 fatal / 0 error / 101 warnings；最终全量 Gradle 退出码 0，APK 构建成功。
- 覆盖率仍存在 user10 自动导出路径问题，本轮 .ec 无效，不宣称覆盖率门禁通过。

[本轮完整汇总报告](file:///D:/Android/AndroidStudioProjects/CarLauncher/evidence/20260909-android-network/summary-zh.html) ·
[证据与复现命令](file:///D:/Android/AndroidStudioProjects/CarLauncher/evidence/20260909-android-network/README.md) ·
[依赖补丁和重建说明](file:///D:/Android/AndroidStudioProjects/CarLauncher/tools/vsomeip-android/README.md)

### 后续任务与限制

- 下一步验证 SOME/IP-SD 组播发现，再接入 Ubuntu 状态仓库/LVGL；当前静态单播通过不代表这些阶段已完成。
- 重复 REQUEST 来源尚未定位，服务端状态写入应按业务语义处理幂等/去重；客户端去重不保证 exactly-once 执行。
- 网络丢失和地址变化逻辑有自动化测试，但真实断网/漫游恢复尚未做设备验收。获得新 IP 后目前需停止再启动 Service，以重建 native 绑定。
- 本轮实测 Android 15 / API 35 / x86_64；API 24–32 没有公开 RouteInfo type 检查，本补丁对 SD 路由保持不可用。

实现依据：[Android NetworkCallback 官方文档](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback)。使用有序回调参数，不在 `onAvailable` 中同步查询 LinkProperties。
