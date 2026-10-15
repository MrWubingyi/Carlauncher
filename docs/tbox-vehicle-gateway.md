# 车机车辆数据与控制 SOME/IP 网关

更新：2026-10-15。状态：车机已实现，TBox 网关服务待实现及联调。

## 范围与架构

车机使用同一个 vSomeIP 客户端会话接收车辆数据、查询控制能力、提交控制命令和接收执行结果。
数据沿 `SomeIpVehicleAdapter → VSomeIpDataSource → VehicleSendService → VehicleRepository → Compose` 更新。
控制沿相反方向，经可选的 `VehicleControl` 数据源接口进入同一 adapter/transport；没有 TCP、HTTP、广播或 VHAL 控制旁路。
通用 `someip-android` 仍仅处理原始字节；本轮无需修改 native/JNI 或增加并发客户端。

这里新增的是 TBox 车辆聚合网关协议，并非已存在的 UCM/BCall 二进制协议。
TBox 需要从现有 CanService 本地接口取得实际数据，通过现有 vsomeipd 栈 offer 此网关服务。
不能把车机直接指向已有 0x200B/0x200A 等服务，它们的业务载荷不同。

## 明确的控制边界

| 动作 | wire action | 能力 |
|---|---|---|
| 中控门锁闭锁 | lock | 仅门锁，不驱动车门物理开闭 |
| 中控门锁解锁 | unlock | 仅门锁，不解除发动机防盗/限速 |
| 寻车 | find | TBox 车型支持的短时寻车闪灯/鸣笛，不是远近光灯开关 |

电动车门、车窗、天窗、独立远近光灯、空调、启动/熄火、挡位、动力、制动、任意 CAN 帧均不开放。
TBox 声明的未知动作也不会出现在可执行动作集合中。原有主页灯光/门锁按钮继续仅供演示，实时控制统一在 Control 页面操作并确认。

所有动作都要求：实时模式、已绑定服务、SOME/IP available、TBox 当前能力租约有效、最近有效车辆快照未满 3000 ms、
validity=0、dataStatus=0、车速严格等于零、P 挡、驻车制动开启，并且没有待完成请求。
车机在发送时重新校验，确认弹窗不是发送授权缓存。TBox 必须再次校验车型支持、调用权限、车辆状态和命令仲裁，复用原有远控条件检查和反馈链路。
challenge 用于会话关联和排除旧报文，不是身份认证或密码；网络访问控制和调用权限由部署及 TBox 服务端落实。

## SOME/IP 配置

默认 asset 为 `app/src/main/assets/vsomeip-client.json`：

- service `0x1111` / instance `0x2222`，event `0x8001` / group `0x0001`，method `0x1001`。
- 这些是沿用本项目的网关候选编号，TBox 集成前须确认整车矩阵没有冲突；没有冒用已有业务服务编号。
- 本次维持当前 native 的默认 major `0`、ET_EVENT、UDP，服务端须匹配；不是 TBox 现有 major 1 服务的直接客户端。
- SD 为 Ethernet 源码配置的 `239.98.102.1:30490`；本机 unicast=auto，事件上限 4096 B，请求上限从 16 B 增至 2048 B。
- 实际车机应选定正确以太网/VLAN 地址和组播路由，可用现有 override 配置；USB 配置须按实际 TBox 配置另行对齐。
- 原 mock/探针若仍使用旧 SD 组播 `224.244.224.245`，必须同步修改或使用独立 override。

服务端对 getCapabilities/control 的 method RPC 可返回受理状态，但车机不会用该 RPC 的 E_OK 当作硬件执行成功；
完整业务结果由同一事件通道的 controlResult 返回。不要给旧 16 B 方法处理器直接发送新协议。

## 应用载荷：UTF-8 JSON

所有入站帧不得为空或含 NUL，最大 4096 B；网关控制帧 version 必须为整数 1。

### 车辆数据

沿用 JSON v1 完整快照（不含 type）：

```json
{"version":1,"seq":1,"timestampMs":1792020000000,"speedKph":0,"rpm":0,"soc":80,"gear":0,"turnSignal":0,"warning":0,"validity":0,"dataStatus":0,"parkingBrake":true,"doorLock":true,"beltWarning":false,"headlightsState":0,"highBeamLightsState":0,"engineCoolantTemp":30,"evBatteryLevel":80}
```

gear：0=P、1=R、2=N、3=D；speedKph 单位 km/h；soc/evBatteryLevel 为百分比。
字段全量要求和枚举由 `SomeIpEventCodec` 定义。TBox 必须转换 CAN 原始比例、无效值和车型挡位编码。
当前模型不支持逐字段有效性：缺少必要字段时不能伪造零值并声明有效，应标记整体 incomplete/NO_DATA；后续若需部分字段展示再扩展 schema。
按照 (timestampMs, seq) 去重，只有新合法快照刷新数据 watchdog；能力和控制结果不刷新车辆数据健康。
失联或超时时清空 Service 的显示缓存；UI 不保留旧速度/门锁作为当前值。

### 能力查询与租约

车机 availability 首次变为 true 时查询，以后每 1000 ms 再查询。每次失联生成新 challenge；查询可重试，执行命令不自动重试。

```json
{"version":1,"type":"getCapabilities","challenge":"<车机当前连接随机UUID>"}
```

TBox 在 event 上回包：

```json
{"version":1,"type":"capabilities","challenge":"<原样返回>","seq":1,"leaseMs":3000,"actions":["lock","unlock","find"]}
```

seq 必须是非负整数，在相同 challenge 下严格递增；重复/倒序不续租。leaseMs 必须是 1..3000 的整数。
服务端只声明车型当前真正允许的动作；撤销用更新的 seq 和空 actions。
TBox 重启若 seq 重置，必须重新服务发现产生新连接，或保留该 challenge 的递增序列。
旧连接的能力/结果被拒收。租约到期或服务失联，UI 和发送入口同时禁用。

### 控制提交

```json
{"version":1,"type":"control","requestId":"<每请求随机UUID>","challenge":"<当前连接>","capabilitySeq":1,"action":"unlock"}
```

每次用户确认产生一个 requestId；最多一条请求在途。TBox 必须按照 requestId 去重，避免 UDP 重复包重复执行。
控制命令不能作为状态数据回写 UI。服务端需防止陈旧请求执行，结合当前 challenge/能力序列、有效租约和业务权限校验。

### 执行结果

```json
{"version":1,"type":"controlResult","requestId":"<原请求>","challenge":"<原请求>","action":"unlock","status":"accepted"}
```

status 可为 accepted / succeeded / failed / rejected。
accepted 仅表示已受理，succeeded 必须来自真实硬件执行确认，failed/rejected 均显示失败。
只有 challenge、requestId、action 都匹配当前在途请求且未超过 5000 ms 才被接受。
accepted 不延长最初的 5000 ms 期限。终态只能发生一次；重复、迟到、其他请求的结果不覆盖它。
超时、执行中断线或停止显示 UNKNOWN，不自动重发。send 返回 false 显示 NOT_SENT；发送异常保守显示 UNKNOWN。
门锁图标只由之后的车辆快照更新，不由提交/受理/执行成功消息乐观切换。

## 验证与交付边界

- JVM `VehicleGatewayTest` 覆盖真实 adapter/codec 的数据映射入口、动作白名单、条件校验、租约、会话隔离、关联结果、超时和停止后回调。
- Compose 测试覆盖实时数据替换、缺少能力禁用、确认/取消、动作发送及车辆快照更新门锁图标。
- 构建、静态检查、模拟器 UI 测试与真实 TBox 联调分开报告。
- 本轮没有修改或部署 TBox，没有发送任何真实硬件控制命令。

TBox 下一步：增加车辆网关 provider，接入 CanService 数据源与现有远控业务入口；在 peer/台架验证 SD、订阅、周期快照、能力撤销、拒绝、失败、超时及真实 ECU 状态回读后，再进行整车验收。

### 本轮实测结果

- 应用 JVM：272 项，0 失败、0 跳过；其中 VehicleGatewayTest 11 项。
- Debug APK 与 androidTest APK 构建成功；Detekt 0 findings，依赖边界检查及 git diff --check 通过。
- Lint：0 错误、20 项既有依赖/SDK 更新提示。初次匿名测试类型触发 Lint 分析器崩溃，改为命名测试替身后完整重跑通过。
- Android 13 模拟器 emulator-5554：ComposeDashboardTest 8 项通过，包含实时车辆快照更新、控制禁用/确认/取消和门锁反馈更新；测试控制请求由 fake Service 捕获。
- 视觉检查：Control 页面及滚动后的门锁/寻车区域均已截图核对。
- 本地证据：build/tbox-build.log、build/tbox-dashboard-tests.log、build/tbox-controls.png、build/tbox-controls-scrolled.png。
- 本轮不声明真实 TBox SOME/IP 通信、实车执行、设备覆盖率或整车验收通过。
