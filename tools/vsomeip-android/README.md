# Android 网络通知适配（vSomeIP 3.7.5）

普通 Android 应用无法订阅 `NETLINK_ROUTE` 组播，原版 connector 无法触发外部路由启动。
本补丁只替换 `__ANDROID__` 下的监听入口：由 CarLauncher 的 `ConnectivityManager.NetworkCallback`
提供真实 LinkProperties 和应用访问阻塞状态，经 JNI 导出的 `vsomeip_android_set_network_state`
进入 vSomeIP。没有观察值时默认不可用；不更改 Linux 路径，也不固定声明网络在线。

## 重建

项目使用 `third_party/vsomeip` 子模块中的定制源码，其中已经包含本适配，无需再次应用补丁。先执行 `git submodule update --init --recursive`。按原有 Android monolithic 参数配置该源码的构建目录后，重建并打包：

```powershell
& 'C:\Program Files\CMake\bin\cmake.exe' --build .\third_party\vsomeip\build-android-x86_64-mono --target vsomeip3 -j 4
.\tools\vsomeip-android\package-dependency.ps1
.\gradlew.bat :app:assembleDebug --offline
```

在未应用本适配的 vSomeIP 3.7.5 源码上执行（示例使用原本机源码路径）：

```powershell
& .\tools\vsomeip-android\apply-network-patch.ps1 -SourceRoot E:\Src\vsomeip
& 'C:\Program Files\CMake\bin\cmake.exe' --build E:\Src\vsomeip\build-android-x86_64-mono --target vsomeip3 -j 4
.\tools\vsomeip-android\package-dependency.ps1 -SourceRoot E:\Src\vsomeip -Library E:\Src\vsomeip\build-android-x86_64-mono\libvsomeip3.so
.\gradlew.bat :app:assembleDebug --offline
```

脚本修改 `netlink_connector.hpp/.cpp` 并复制同目录的 `android_network_connector.inc`。
所有锚点先检查唯一性；重复应用会报错。其他既有 Android monolithic 配置改动保持原样。
已应用的源码更新 `.inc` 时应先审查差异，再复制该文件和重建。
本项目导入的 `libvsomeip3.so` 必须包含该导出符号，否则 JNI 链接会明确失败。
项目默认使用子模块头文件并链接 [配套预编译库](../../third_party/vsomeip-android/README.md)；源码修改须先提交和推送，并同步主仓库的子模块提交与库包，才能用于 App 和 CI。

## 并发和状态约束

- Android 按配置的本地 IP 匹配网卡；服务发现还需该网卡到 SD multicast 的路由。
- API 29+ 等待 `onBlockedStatusChanged(false)`，不把未知或受限网络发布为可用。
- 同时报告多个网络时选择持有配置地址且具有所需路由的接口，选择结果保持确定性。
- vSomeIP 持有弱引用，通过 io strand 串行处理通知；stop 的 generation 校验丢弃旧队列事件。
- Java observer 在 unregister 前撤销 active，拒绝迟到事件；停止和网络丢失清空 pending 请求。
- native 只接受待处理 Client ID / Session ID 对应的首个终态响应；3 秒过期响应及重复响应不更新 UI。
- 网络可用、UDP 已提交、服务 AVAILABLE 均不能替代 Method RESPONSE 成功。

当前 API 24–32 无公开 RouteInfo type 检查，本适配对 SD 路由保持不可用；静态单播可用。
本轮实测环境为 Android 15（API 35）、x86_64。配置 IP 变化后会撤销可用性；若获得新 IP，
需要停止再启动 Service 以重新写入绑定地址，当前尚未自动迁移 native endpoint。
Java 的 `start(Context, configPath)` 管理网络观察者；不带 Context 的入口只用于注册/启停探针。

## 验证

`NetworkStateTrackerTest` 测地址、阻塞、路由、网卡变化和丢失；`AndroidNetworkMonitorTest`
测 framework callback 的生命周期、注册失败和迟到通知；Method 探针验证真实 Ubuntu 往返与重启。
证据见 [本轮汇总](../../evidence/20260909-android-network/summary-zh.html)。

平台回调参数处理遵循 [Android NetworkCallback 文档](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback)：
使用有序的回调参数，不在 `onAvailable` 中同步查询 LinkProperties。
