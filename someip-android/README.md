# Android SOME/IP 客户端库

本模块提供 `com.example.someip` 下的 `SomeIpClient`、`SomeIpClientConfig`、
`SomeIpService` 和 `VSomeIp.create`。库封装 JNI、vSomeIP 生命周期、Android 网络观察、
组播锁和主线程回调；不包含车辆 JSON、车速/挡位、Launcher 模型、Service 或 asset/IP 选择策略。

## 接入

同仓库使用 `implementation(project(":someip-android"))`。其他 Android 应用可以消费
`build/outputs/aar/someip-android-debug.aar`；发布坐标和 Maven 仓库未配置。
直接引用 AAR 时，由宿主工程提供 Kotlin 标准库；同仓库的 Gradle 模块依赖会携带依赖元数据。

```kotlin
val client: SomeIpClient = VSomeIp.create(
    context.applicationContext,
    configFile,
)

// 在应用自己的串行后台执行器中启动/停止；回调交给主线程。
val started = client.start(object : SomeIpClient.Listener {
    override fun onAvailabilityChanged(available: Boolean) { /* 发布连接状态 */ }
    override fun onPayload(payload: ByteArray) { /* 由业务层解码 */ }
    override fun onResponse(ok: Boolean, returnCode: Int) { /* 方法响应状态 */ }
})
val submitted = client.send(byteArrayOf(1, 2, 3))
client.close()
```

`configFile` 必须是可读的 vSomeIP JSON 配置文件，放在应用私有、可写的目录中。
JSON 的 `client-endpoint.application` 必须唯一匹配 `applications[].name`；
unicast、SD、远端端点和路由策略由宿主提供。
库不会从 assets 复制配置或自行挑选 IP。CarLauncher 的具体配置见
`VehicleSomeIpClientFactory` 与 `SomeIpConfigFile`。

## JSON 启动配置

在原生 vSomeIP JSON 顶层增加 `client-endpoint`，它由本库读取，其他原生字段仍由 vSomeIP 读取：

```json
{
  "unicast": "192.0.2.10",
  "applications": [{"name": "example-client", "id": "0x5566"}],
  "routing": "example-client",
  "client-endpoint": {
    "application": "example-client",
    "service": "0x1234",
    "instance": "0x0001",
    "event": "0x8001",
    "event-group": "0x0001",
    "method": "0x0001",
    "max-event-payload-bytes": 4096,
    "max-request-payload-bytes": 4096
  },
  "service-discovery": {
    "enable": "true", "multicast": "224.244.224.245", "port": "30490", "protocol": "udp"
  }
}
```

`client-endpoint` 的上述字段均必填，不使用车辆默认值补齐。整数可使用 JSON 整数、
十进制字符串或 `0x` 十六进制字符串；不接受小数、布尔值、溢出和越界 ID。
解析入口为 `SomeIpClientConfig.fromFile(file)`，`VSomeIp.create(context, file)` 会先解析校验再创建客户端。
修改配置应先停止客户端，然后重新 create/start；已创建的 client 持有配置快照。
类型化 `SomeIpClientConfig` API 保留用于已有调用者和测试，CarLauncher 正式启动使用文件入口。

CarLauncher 配置源按以下顺序选择：

1. 存在 `filesDir/vsomeip/vsomeip-client.override.json` 时，使用这份完整 JSON，非法配置直接报错。
2. 否则每次读取 `app/src/main/assets/vsomeip-client.json`，重新安装后的新配置不受旧副本影响。

应用支持 `unicast: "auto"`，仅在此值下选择本机 IPv4；具体 IP 保持原值。
解析后的 native 配置写入 `filesDir/vsomeip/vsomeip-runtime.json`，配置源保持不变。
旧 `filesDir/vsomeip/vsomeip-client.json` 保留但不再加载；如需沿用自定义内容，补齐
`client-endpoint` 后保存为 override 文件。通用库本身不识别 `auto` 或选择地址，这是宿主策略。

## 能力与约束

- 每个客户端配置一个 service/instance、一个事件组/事件和一个 method；支持事件消费、
  原始字节请求和响应状态。当前不提供服务端、动态多订阅、响应正文或逐请求 Future/RPC 接口。
- 报文可以是零字节；事件和请求长度上限分别可配置，默认 4096 字节。车辆 16 字节协议
  检查属于应用的 codec/探针，库不解释业务字段。可配置上限范围为 1 字节至 1 MiB。
- `start` 表示本地启动成功；服务发现可用、请求提交成功、收到成功响应是三个独立状态。
  `send` 返回值只表示提交结果。native 的 client/session 匹配与响应去重仍保留；
  过期响应会丢弃，当前接口不提供请求超时通知。
- vSomeIP 使用进程级配置和 routing 环境。本版本同一进程最多一个活动会话；
  第二个客户端启动返回 `false`，不能替换监听或停止已有会话。相同客户端重复 start 幂等。
  进程内首次使用的配置父目录固定；更换目录需重启进程。同目录配置在关闭旧会话后可重新加载。
- 主线程排队回调受会话代次保护；停止/重启后旧队列不能进入新的 listener。
  宿主必须在生命周期结束时调用 `stop`/`close`，启动失败后可显式重试。
- 当前构建产物仅含 x86_64，最低 API 24。服务发现所需的 Android 路由观察在 API 24–32
  保持既有保守行为，静态单播与 API 33+ SD 的行为应分别验证。

## 构建与验证

先按仓库 `third_party/vsomeip-android/README.md` 准备匹配源码版本的
`build/native/artifact/lib/x86_64/libvsomeip3.so`，使用 JDK 21：

```powershell
.\gradlew.bat :someip-android:assembleDebug :someip-android:testDebugUnitTest
.\gradlew.bat :someip-android:detekt :someip-android:lintDebug
.\gradlew.bat :someip-android:compileDebugAndroidTestKotlin
.\tools\check-someip-layering.ps1
```

其他 ABI 需要先生成对应的 vSomeIP native 依赖，再通过 `-PsomeipAbis=arm64-v8a`
选择构建；改变属性本身不会生成缺失的依赖。CMake 还允许覆盖外部源码/库目录，
具体名称见 `src/main/cpp/CMakeLists.txt`。

库的 JVM 测试验证生命周期与网络状态决策；instrumentation 验证 JNI 绑定和 Android 回调。
通过编译或 JVM 测试不代表真实 ECU/Ubuntu peer 通信已验收。

模块打包方式依据 [Android library 文档](https://developer.android.com/studio/projects/android-library)，
JNI 回调和线程边界参考 [Android JNI tips](https://developer.android.com/ndk/guides/jni-tips)。
