package com.example.carlauncher.someip;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * W37 WP1 第三步草稿 · Payload 编码器（schema v0.2，固定 16 B，全大端 BE）。
 *
 * 布局：schemaVersion u8=1 | seq u32 BE | timestampMs u64 BE | speedKph u8 |
 *       gear u8（0=P 1=R 2=N 3=D）| dataStatus u8（0..4，见 DataStatus）。
 * 无效哨兵统一 255；范围外/非法枚举按哨兵编码（由接收端拒收并返回 E_NOT_OK）。
 */
public final class SomeipPayloadCodec {

    public static final int SCHEMA_VERSION = 1;
    public static final int SIZE = 16;

    public static final int SPEED_KPH_MIN = 0;
    public static final int SPEED_KPH_MAX = 200;
    public static final int GEAR_MIN = 0;      // P
    public static final int GEAR_MAX = 3;      // D
    public static final int DATA_STATUS_MIN = 0;  // NORMAL
    public static final int DATA_STATUS_MAX = 4;  // TRANSPORT_DISCONNECTED
    public static final int INVALID = 0xFF;

    private SomeipPayloadCodec() {}

    /** 编码 16 B payload；越界字段自动置无效哨兵 255（不抛异常）。 */
    public static byte[] encode(int seq, long timestampMs, int speedKph,
                                int gear, int dataStatus) {
        ByteBuffer b = ByteBuffer.allocate(SIZE).order(ByteOrder.BIG_ENDIAN);
        b.put((byte) SCHEMA_VERSION);
        b.putInt(seq);
        b.putLong(timestampMs);
        b.put((byte) rangeOrInvalid(speedKph, SPEED_KPH_MIN, SPEED_KPH_MAX));
        b.put((byte) rangeOrInvalid(gear, GEAR_MIN, GEAR_MAX));
        b.put((byte) rangeOrInvalid(dataStatus, DATA_STATUS_MIN, DATA_STATUS_MAX));
        return b.array();
    }

    /** 解码（供日志/单测/抓包对照）。返回 [schema, seq, timestampMs, speedKph, gear, dataStatus]。 */
    public static long[] decode(byte[] payload) {
        if (payload == null || payload.length != SIZE) {
            throw new IllegalArgumentException("payload must be " + SIZE + " bytes");
        }
        ByteBuffer b = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        long schema = b.get() & 0xFFL;
        long seq = b.getInt() & 0xFFFFFFFFL;
        long timestampMs = b.getLong();
        long speedKph = b.get() & 0xFFL;
        long gear = b.get() & 0xFFL;
        long dataStatus = b.get() & 0xFFL;
        return new long[]{schema, seq, timestampMs, speedKph, gear, dataStatus};
    }

    private static int rangeOrInvalid(int value, int min, int max) {
        return (value >= min && value <= max) ? value : INVALID;
    }
}
