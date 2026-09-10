# SOME/IP 状态与界面

首页通过 `VehicleSendService.getSomeipStatus()` → `VehicleRepository` →
`CockpitUiState` 展示 SOME/IP 状态。TCP 继续运行作为基线，TCP 连接结果不会影响
SOME/IP 状态；本地车辆数据质量仍单独展示。

| 状态 | 含义 |
|---|---|
| STARTING | 正在启动 native Client，尚未确认服务可用 |
| UNAVAILABLE | 收到服务不可用通知，清除此前响应结果 |
| WAITING_RESPONSE | 服务可用，等待首个 Method 响应；不算在线 |
| ONLINE | 收到 Method `0x1001` 的 RESPONSE 且 Return Code 为 `0x00` |
| RESPONSE_ERROR | 收到非零 Return Code，或收到 MT_ERROR（即使其返回码为零） |
| RESPONSE_TIMEOUT | 服务仍可用，但连续 3000 ms 没有收到响应 |
| START_FAILED | 配置、库加载或 nativeStart 失败 |
| STOPPED | native 已停止，或 Android Service 已销毁 |

响应超时以 `SystemClock.elapsedRealtime()` 为基准，每 100 ms 独立检查。
首次从服务可用时计时，此后从最后一次响应计时；成功和错误响应均证明收到响应，
但只有成功响应进入 ONLINE。重复 AVAILABLE 不延长超时。无数据回调时仍可超时。
超时后收到新响应按结果恢复；服务不可用或停止后排队的响应不能恢复在线。
这衡量的是响应接收健康度，不是逐请求超时、延迟统计或 LVGL 应用结果的额外确认。

通信区显示可用性、当前状态和本次服务可用期间最后一个返回码；超时状态里的
`Last RC` 是历史结果，不能解释为当前成功。顶栏保留本地 INVALID DATA 提示。
Service 已绑定时始终允许 STOP SEND，包括等待、错误及超时状态；未绑定显示 START SEND。
Activity 重绑读取 Service 的当前快照，不重置计时。

生命周期执行器在进程内共享，旧 Service 的 stop 与新 Service 的 start 串行执行。
回调经主线程分发，解除监听后尚未执行的旧回调被丢弃。销毁时取消 watchdog、
清除状态并排队解除监听和停止 native。

验证命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline
.\gradlew.bat :app:connectedDebugAndroidTest :app:lintDebug --offline --continue
```

JVM 测试使用显式时间覆盖 2999/3000 ms 边界、错误、恢复及迟到回调。
设备测试覆盖 JNI 事件的主线程分发、监听清理，以及 Repository/Activity 中 TCP
与 SOME/IP 状态相反时的实际显示；这些测试不启动 Ubuntu 网络服务。

后续真实 native/Method 探针见 `tools/someip-probe/README.md`。发送接口现在返回
提交结果；JNI 在初始化前使用应用私有目录保存本地路由 socket。2026-09-09
真实注册和三轮启停已通过，Method 尚受 Android NETLINK_ROUTE 权限阻塞。

2026-09-10 更新（取代上段旧阻塞状态）：Android 网络适配后静态 Method 已通过，
服务端有界去重已通过；SD 双端组播仍未互通。本轮新增真实 Service 的 Home 后台、
解绑重绑和两轮单次释放验证，详见 [Service 生命周期记录](2026-09-10-SOMEIP-Service生命周期.md)。
下一增量为 Ubuntu Adapter → 线程安全快照 → LVGL UI Timer。
