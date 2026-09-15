# 车辆数据传输协议文档 (Vehicle Data Transmission Protocol)

本文件定义了 `CarLauncher` 应用中车辆状态数据的传输协议，包括字段名称、类型、取值范围及含义。

## 0. 当前 SOME/IP Event 契约（v1）

本节是 Android、Ubuntu LVGL 与 Mock Server 共同遵守的当前契约。若后文的历史 TCP/Method 说明与本节冲突，以本节为准。

| 项目 | 值 |
|---|---|
| Transport | SOME/IP-SD + UDP Event |
| Service | `0x1111` |
| Instance | `0x2222` |
| Event | `0x8001` |
| Eventgroup | `0x0001` |
| Service UDP port | `30509` |
| SD multicast | `224.244.224.245:30490` |
| Payload | UTF-8 JSON 完整车辆状态快照 |
| Schema version | JSON `version == 1` |
| Publish period | 正常约 100 ms |
| Watchdog | 连续 3000 ms 未收到可接受帧后进入离线 |

### v1 接收规则

- payload 必须非空且不超过 4096 bytes，不允许内嵌 NUL；必须能够解析为完整 JSON 对象。
- `version` 必须为 `1`，`timestampMs > 0`；`seq` 用于日志关联和顺序判断。
- 同一发送进程内，`timestampMs` 单调不减；时间戳相同时仅接受更大的 `seq`。服务进程重启后允许 `seq` 从头开始，只要新帧时间戳更新且内容有效。
- `dataStatus=0` 表示正常，`dataStatus=1` 表示无效。无效帧可以进入状态判定层，但不得覆盖最后有效车辆值或作为在线有效帧刷新 watchdog。
- 服务不可用或 watchdog 超时后，客户端显示离线/等待数据；重新发现、订阅并收到首帧有效数据后恢复在线。
- vSomeIP 回调线程只解析并写入线程安全状态；Android 主线程和 LVGL Timer/UI 线程分别消费状态，网络回调不得直接操作 UI。

### 运行角色

- Mock Server：`vehicle-mock-service`，提供 Service 并发布 Event。
- Android：native vSomeIP 客户端订阅 Event，经 JNI、Service/Repository 更新 UI。
- Ubuntu LVGL：`lvgl-vehicle-client` 订阅 Event，写入互斥保护的车辆模型，由 100 ms LVGL Timer 更新控件。

### 验证边界

当前通过的是显式 TAP 拓扑下的 SOME/IP-SD/UDP Event 链路。默认 Android Emulator NAT、旧 16-byte Method payload、图片资源 TCP 接口以及 PCC 生产网络不属于本 Event 契约的通过范围。

## 1. 消息格式

数据采用 JSON 格式进行序列化。

## 2. 字段定义

| 字段名 (JSON key) | 类型 | 说明 | 取值范围 / 枚举值 (int) |
| :--- | :--- | :--- | :--- |
| `version` | `int` | 协议版本号 | 建议从 1 开始 |
| `seq` | `long` | 消息序列号 | 用于追踪消息顺序，单调递增 |
| `timestampMs` | `long` | 生成状态时的时间戳 | 毫秒级 Unix 时间戳 |
| `speedKph` | `int` | 车速 (公里/小时) | 0 - 200 |
| `rpm` | `int` | 发动机/电机转速 (RPM) | 1 - 8000 |
| `gear` | `int` | 当前档位 | 0: P, 1: R, 2: N, 3: D |
| `soc` | `int` | 电池电量状态 (State of Charge) | 0 - 100 |
| `turnSignal` | `int` | 转向灯状态 | 0: NONE, 1: LEFT, 2: RIGHT, 3: HAZARD |
| `parkingBrake` | `boolean` | 驻车制动状态 | `true`: 开启, `false`: 关闭 |
| `warning` | `int` | 告警状态 | 0: NONE, 1: GENERAL, 2: CRITICAL |
| `validity` | `int` | 数据有效性 | 0: VALID, 1: INVALID_SPEED, 2: INCOMPLETE, 3: STALE |
| `doorLock` | `boolean` | 车门上锁状态 | `true`: 已锁定, `false`: 未锁定 |
| `beltWarning` | `boolean` | 安全带告警 | `true`: 未系扣 (告警), `false`: 已系扣 |
| `headlightsState` | `int` | 前照灯/近光灯状态 | 取值逻辑参见具体实现 |
| `highBeamLightsState` | `int` | 远光灯状态 | 取值逻辑参见具体实现 |
| `engineCoolantTemp` | `float` | 冷却液温度 (℃) | - |
| `evBatteryLevel` | `float` | 电动汽车电池电量 | - |
| `dataStatus` | `int` | 数据质量状态 | 0: NORMAL, 1: INVALID, 2: NO_DATA, 3: DISCONNECTED, 4: TRANSPORT_ERR |

## 3. 枚举映射详细定义

### 3.1 Gear (档位)
- `0`: P (驻车档)
- `1`: R (倒车档)
- `2`: N (空档)
- `3`: D (前进档)

### 3.2 TurnSignal (转向灯)
- `0`: NONE (无信号)
- `1`: LEFT (左转向)
- `2`: RIGHT (右转向)
- `3`: HAZARD (危险警告灯/双闪)

### 3.3 WarningState (告警)
- `0`: NONE (无异常)
- `1`: GENERAL_WARNING (一般警告)
- `2`: CRITICAL (严重故障)

### 3.4 DataValidity (数据有效性)
- `0`: VALID (有效)
- `1`: INVALID_SPEED (速度异常)
- `2`: INCOMPLETE (数据缺失)
- `3`: STALE (数据过期)

### 3.5 DataStatus (数据质量)
- `0`: NORMAL (正常)
- `1`: INVALID (无效数据)
- `2`: NO_DATA (无数据)
- `3`: SOURCE_DISCONNECTED (数据源连接断开)
- `4`: TRANSPORT_DISCONNECTED (传输通道断开)

### (连接状态枚举)
- `0`: ONLINE(已连接且收到有效数据):连接断开或超过3秒无有效数据
- `1`: INVALID_DATA(已连接但当前帧存在越界/缺失字段):收到有效帧，或超过3秒无有效数据
- `2`: DISCONNECTED(服务不可用，或连续3秒没有有效数据):重新发现并收到首帧有效数据
- `3`: RECOVERING(连接重新建立但尚未收到有效帧):收到首帧有效数据进入 ONLINE；再次断开进入 DISCONNECTED
    [超时阈值：3000 ms
      非法帧不刷新 lastValidDataTime
      仅收到首帧完整有效数据后才进入 ONLINE
      单纯传输连接恢复只能进入 RECOVERING
      DISCONNECTED 不展示陈旧数据]

## 4. 示例 JSON

```json
{
  "version": 1,
  "seq": 1024,
  "timestampMs": 1723013000000,
  "speedKph": 60,
  "rpm": 2500,
  "gear": 3,
  "soc": 85,
  "turnSignal": 0,
  "parkingBrake": false,
  "warning": 0,
  "validity": 0,
  "doorLock": true,
  "headlightsState": 0,
  "highBeamLightsState": 0,
  "engineCoolantTemp": 90.5,
  "evBatteryLevel": 85.0,
  "dataStatus": 0
}
```

## 5. 运行时图片资源接口

图片接口与车辆数据共用同一个 TCP 连接，仍采用“一行一个 JSON”的帧格式。
运行时替换仅在以 `-DUI_USE_PNG_ASSETS=ON` 构建时可用。为避免通过 TCP
访问任意系统文件，传入的图片必须已经上传到项目 `assets` 目录（可以位于其
子目录），并且必须是具有有效 PNG 文件头的常规文件。

### 5.1 替换图片

请求：

```json
{"type":"image.set","name":"dashboard_background","path":"uploads/new-background.png"}
```

`path` 可以是相对 `assets` 的路径、`assets` 内的绝对路径，或 LVGL 的
`A:/absolute/path.png` 路径。成功返回表示请求已进入 UI 线程队列；图片通常
会在下一个 100 ms UI 刷新周期内切换：

```json
{"type":"image.set.result","ok":true,"status":"queued","name":"dashboard_background","path":"A:/absolute/assets/uploads/new-background.png"}
```

名称不存在、文件越界、文件不存在或不是 PNG 时会返回：

```json
{"type":"image.set.result","ok":false,"error":"unknown image name"}
```

### 5.2 获取全部图片路径

请求：

```json
{"type":"image.list"}
```

返回：

```json
{"type":"image.list.result","ok":true,"images":[{"name":"dashboard_background","path":"A:/absolute/assets/lvgl-cluster-blue-gradient-800-480.png"}]}
```

如果变更尚未由 UI 线程应用，资源项还包含 `pendingPath`；如果 LVGL 解码失败，
则保留原路径并在资源项中返回 `lastError`。可通过下面的命令直接测试：

```bash
printf '%s\n' '{"type":"image.list"}' | nc 127.0.0.1 19090
printf '%s\n' '{"type":"image.set","name":"vehicle_truck","path":"uploads/truck.png"}' | nc 127.0.0.1 19090
```
