// W37 WP1 · protocol_ids.hpp v0.2
// 对齐接口表 v0.2（Learning/SOME-IP/evidence-20260907-w37-wp1/接口表-v0.1-模板.md）
// SOME/IP 协议头由 vsomeip 库封装解析；本文件只定义应用层常量与 payload schema。
#pragma once

#include <vsomeip/vsomeip.hpp>

#include <cstddef>

namespace vehicle_someip {

// ---- SOME/IP 身份（复用 W34-W36 冻结组 + W37 新增 Method）----
constexpr vsomeip::service_t kServiceId = 0x1111;
constexpr vsomeip::instance_t kInstanceId = 0x2222;
constexpr vsomeip::method_t kHelloWorldMethodId = 0x3333;       // 保留（W34 回归用，不动）
constexpr vsomeip::method_t kSetVehicleStateMethodId = 0x1001;  // 新增（W37，Decision C）
constexpr vsomeip::event_t kStateEventId = 0x8001;              // 预留（Ubuntu→Android 状态回推）
constexpr vsomeip::eventgroup_t kStateEventGroupId = 0x0001;    // 预留

// ---- Payload schema v0.2：固定 16 B，全大端 BE ----
constexpr std::size_t kPayloadSize = 16;
constexpr vsomeip::byte_t kSchemaVersion = 1;  // 接收端校验 ==1（Decision D-3）

// 字段偏移
constexpr std::size_t kOffSchema    = 0;   // u8
constexpr std::size_t kOffSeq       = 1;   // u32 BE
constexpr std::size_t kOffTimeMs    = 5;   // u64 BE
constexpr std::size_t kOffSpeed     = 13;  // u8
constexpr std::size_t kOffGear      = 14;  // u8
constexpr std::size_t kOffDataStatus = 15; // u8

// 无效哨兵 / 范围（与接口表 v0.2 一致）
constexpr vsomeip::byte_t kInvalidByte = 0xFF;
constexpr int kSpeedKphMin = 0;
constexpr int kSpeedKphMax = 200;
constexpr int kGearMin = 0;  // P
constexpr int kGearMax = 3;  // D
constexpr int kDataStatusMin = 0;  // NORMAL
constexpr int kDataStatusMax = 4;  // TRANSPORT_DISCONNECTED

}  // namespace vehicle_someip
