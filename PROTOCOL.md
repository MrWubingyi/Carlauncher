# 车辆数据传输协议文档 (Vehicle Data Transmission Protocol)

本文件定义了 `CarLauncher` 应用中车辆状态数据的传输协议，包括字段名称、类型、取值范围及含义。

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

## 5. 测试场景模拟 (Mock Testing Scenarios)

为了验证接收端的健壮性，模拟数据源包含以下自动化测试场景（120秒为一个循环周期）：

1.  **正常模拟 (0-60s)**：速度、档位、灯光等数据按逻辑正常规律变化。
2.  **非法数据测试 (61-70s)**：
    *   `speedKph`: 发送 `255` (超出文档范围 0-200)。
    *   `rpm`: 发送 `9999` (超出文档范围 1-8000)。
    *   `soc`: 发送 `150` (超出文档范围 0-100)。
    *   `dataStatus`: 显式设置为 `1` (`INVALID`)。
    *   **目的**：验证接收端是否具备基本的边界检查和非法数据过滤能力。
3.  **静默/断连测试 (70-100s)**：
    *   数据源将持续 **30秒** 不发送任何数据包。
    *   **目的**：验证接收端的超时检测机制（Watchdog）是否正常工作，以及是否能正确触发“无信号”或“连接断开”的 UI 状态。
4.  **恢复模拟 (100-120s)**：数据包恢复正常发送，序列号继续递增，用于验证系统在异常后的自动恢复能力。
