# 2026-09-09 SOME/IP Method 开发增量

状态：**native 注册/启停增量通过；Android → Ubuntu Method 往返未通过**。

## 本轮实现

- JNI 发送返回 boolean，Java 日志区分 submitted/skipped。true 仅表示提交给库。
- native 日志增加 Service/Method/Client/Session，以及发送 seq/hex。
- native 完整 start/stop 串行化、重复启动幂等、发送与停止互斥。
- 状态回调改用 weak_ptr，停止时清除回调，消除 application 自身持有回调造成的循环引用。
- 在创建 application 前配置私有 `VSOMEIP_BASE_PATH`，修复默认 `/tmp` 无写权限的问题。
- 增加冻结字节向量、无符号序号、范围与长度测试，以及显式启用的真实设备探针。
- 新增独立 Ubuntu 测试服务，构建通过，使用配套库时能够监听 `192.168.31.248:30509`。

## 三次运行

| 证据目录/文件 | 结果 |
|---|---|
| `attempt1-routing-failure/` | Method 探针失败；默认 `/tmp` 路由 socket 与锁文件被拒绝，客户端未注册 |
| `attempt2-netlink-failure/` | 私有目录修复后注册成功，但 `Error binding NETLINK socket: Permission denied`，服务不可用超时 |
| `native-lifecycle-pass/` | 真实 native 注册、重复 start、重复 stop、停止后发送拒绝，连续三轮通过 |
| `service.log` | Ubuntu 系统安装库的 configuration module 加载失败 |
| `service-build-libs.log` / `service-fixed.log` | 使用 `/root/develop/vsomeip/build` 配套库后服务启动成功，限时停止 |
| `capture-fixed.log` / `method-probe-fixed.pcap` | 0 个业务报文，与 Android 未启用外部路由的日志相符；不是往返成功证据 |

原始 JUnit XML、UTP 结果与对应 Logcat 在各目录内。日志包含三个
`registration-stop cycle=0/1/2 passed`。本轮没有 Android Method 成功响应，也没有
LVGL 更新或 Ubuntu 服务重启恢复证据，不勾选 WP2 端到端完成。

## 环境与复现

- Android：Automotive Android 15，user 10，`wlan0=10.0.2.16`，ABI x86_64。
- Android native：vSomeIP 3.7.5 monolithic，NDK 28.2.13676358 交叉库。
- Ubuntu：`192.168.31.248`，GCC 11.4.0；新目录 `/root/develop/carlauncher-probe-20260909`。
- 真实 Method 探针采用静态 UDP 单播，不验证 SD。既有服务和系统安全策略未修改。
- 命令与测试机制见 `tools/someip-probe/README.md`。两侧限时测试服务及抓包均已结束。
- 当前为未提交工作树；源码及证据哈希记录在 `SHA256SUMS.txt`，不以虚构提交 ID 代替。

## 下一项开发

适配普通 Android 应用可用的网络状态通知：把真实接口/IP 的可用与丢失传给
vSomeIP 外部路由状态，不依赖当前被拒绝的 NETLINK_ROUTE 订阅。随后复跑
Method 成功、非法帧拒收、恢复与启停，再进入 Ubuntu LVGL Adapter。
