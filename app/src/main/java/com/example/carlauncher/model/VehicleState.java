package com.example.carlauncher.model;

import com.example.carlauncher.data.DataStatus;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 车辆状态数据模型，代表车辆在某一时刻的核心运行参数。
 */
public class VehicleState {

    private final int version;      // 协议版本号
    private final long sequence;    // 消息序列号，用于追踪消息顺序
    private final long timestampMs; // 生成状态时的时间戳 (毫秒)
    private final int vehSpeedKph;     // 车速 (公里/小时)
    private final int engRpm;          // 发动机/电机转速 (RPM)
    private final String gear;      // 当前档位 (P, R, N, D)
    private final int soc;          // 电池电量状态 (State of Charge, 0-100)
    private final TurnSignal turnSignal;         //转向灯状态
    private final boolean parkingBrake;     // 驻车制动状态 (true = 开启)
    private final DataValidity validity;
    private WarningState warning;        //告警
    private DataStatus dataStatus;   //数据质量

    /**
     * 构造函数。
     */

    public VehicleState(
            int version,
            long sequence,
            long timestampMs,
            int speedKph,
            int rpm,
            String gear,
            int soc,
            TurnSignal turnSignal,
            boolean parkingBrake,
            WarningState warning,
            DataValidity validity
    ) {
        this.version = version;
        this.sequence = sequence;
        this.timestampMs = timestampMs;
        this.vehSpeedKph = clamp(speedKph, 0, 200); // 限制车速范围
        this.engRpm = clamp(rpm, 0, 8000);          // 限制转速范围
        this.gear = validateGear(gear);          // 校验档位有效性
        this.soc = clamp(soc, 0, 100);           // 限制电量范围
        this.turnSignal = turnSignal == null ? TurnSignal.NONE : turnSignal;
        this.parkingBrake = parkingBrake;
        this.warning = warning == null ? WarningState.NONE : warning;
        this.validity = validity == null ? DataValidity.INCOMPLETE : validity;
    }

    public long getSequence() {
        return sequence;
    }

    public int getVehSpeedKph() {
        return vehSpeedKph;
    }

    public int getEngRpm() {
        return engRpm;
    }

    public String getGear() {
        return gear;
    }

    public int getSoc() {
        return soc;
    }

    public TurnSignal getTurnSignal() {
        return turnSignal;
    }

    public boolean isParkingBrake() {
        return parkingBrake;
    }

    public WarningState getWarning() {
        return warning;
    }

    public DataValidity getValidity() {
        return validity;
    }

    /**
     * 将车辆状态转换为 JSON 字符串，以便通过网络发送。
     *
     * @return JSON 格式的字符串
     * @throws JSONException 如果转换过程中发生错误
     */
    public String toJson() throws JSONException {
        JSONObject json = new JSONObject();

        json.put("version", version);
        json.put("seq", sequence);
        json.put("timestampMs", timestampMs);
        json.put("speedKph", vehSpeedKph);
        json.put("rpm", engRpm);
        json.put("gear", gear);
        json.put("soc", soc);
        json.put("turnSignal", turnSignal.name());
        json.put("parkingBrake", parkingBrake);
        json.put("warning", warning.name());
        json.put("validity", validity.name());
        return json.toString();
    }

    /**
     * 辅助方法：将数值限制在指定范围内。
     */
    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    /**
     * 辅助方法：校验档位是否合法，非法档位默认返回 "P"。
     */
    private static String validateGear(String gear) {
        if ("P".equals(gear)
                || "R".equals(gear)
                || "N".equals(gear)
                || "D".equals(gear)) {
            return gear;
        }

        return "P";
    }
}
