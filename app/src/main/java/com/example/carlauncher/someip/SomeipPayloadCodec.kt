package com.example.carlauncher.someip

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * W37 WP1 第三步草稿 · Payload 编码器（schema v0.2，固定 16 B，全大端 BE）。
 *
 * 布局：schemaVersion u8=1 | seq u32 BE | timestampMs u64 BE | speedKph u8 | gear u8（0=P 1=R 2=N 3=D）|
 * dataStatus u8（0..4，见 DataStatus）。 无效哨兵统一 255；范围外/非法枚举按哨兵编码（由接收端拒收并返回 E_NOT_OK）。
 */
class SomeipPayloadCodec private constructor() {
    companion object {

        const val SCHEMA_VERSION: Int = 1
        const val SIZE: Int = 16

        const val SPEED_KPH_MIN: Int = 0
        const val SPEED_KPH_MAX: Int = 200
        const val GEAR_MIN: Int = 0 // P
        const val GEAR_MAX: Int = 3 // D
        const val DATA_STATUS_MIN: Int = 0 // NORMAL
        const val DATA_STATUS_MAX: Int = 4 // TRANSPORT_DISCONNECTED
        const val INVALID: Int = 0xFF

        /* 编码 16 B payload；越界字段自动置无效哨兵 255（不抛异常）。 */
        @JvmStatic
        fun encode(
            seq: Int,
            timestampMs: Long,
            speedKph: Int,
            gear: Int,
            dataStatus: Int,
        ): ByteArray {
            val b = ByteBuffer.allocate(SIZE).order(ByteOrder.BIG_ENDIAN)
            b!!.put(SCHEMA_VERSION.toByte())
            b!!.putInt(seq)
            b!!.putLong(timestampMs)
            b!!.put(rangeOrInvalid(speedKph, SPEED_KPH_MIN, SPEED_KPH_MAX).toByte())
            b!!.put(rangeOrInvalid(gear, GEAR_MIN, GEAR_MAX).toByte())
            b!!.put(rangeOrInvalid(dataStatus, DATA_STATUS_MIN, DATA_STATUS_MAX).toByte())
            return b!!.array()
        }

        /* 解码（供日志/单测/抓包对照）。返回 [schema, seq, timestampMs, speedKph, gear, dataStatus]。 */
        @JvmStatic
        fun decode(payload: ByteArray?): LongArray {
            if (payload == null || payload!!.size != SIZE) {
                throw IllegalArgumentException("payload must be " + SIZE + " bytes")
            }
            val b = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
            val schema = b!!.get().toLong() and 0xFFL
            val seq = b!!.getInt().toLong() and 0xFFFFFFFFL
            val timestampMs = b!!.getLong()
            val speedKph = b!!.get().toLong() and 0xFFL
            val gear = b!!.get().toLong() and 0xFFL
            val dataStatus = b!!.get().toLong() and 0xFFL
            return longArrayOf(schema, seq, timestampMs, speedKph, gear, dataStatus)
        }

        @JvmStatic
        private fun rangeOrInvalid(value: Int, min: Int, max: Int): Int =
            if ((value >= min && value <= max)) value else INVALID
    }
}
