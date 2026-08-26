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
    private final Gear gear;      // 当前档位 (P, R, N, D)
    private final int soc;          // 电池电量状态 (State of Charge, 0-100)
    private final TurnSignal turnSignal;         //转向灯状态
    private final boolean parkingBrake;     // 驻车制动状态 (true = 开启)
    private final WarningState warning;        //告警
    private final DataValidity validity;
    private final Boolean doorLock; //车门上锁状态
    private final Boolean beltWarning; // 安全带告警 (true = 未系)
    private final Integer headlightsState; // 前照灯/近光灯状态
    private final Integer highBeamLightsState; //远光灯状态
    private final Float engineCoolantTemp; //冷却液温度
    private final Float evBatteryLevel; // 电动汽车电池电量
    private final DataStatus dataStatus;   //数据质量

    private VehicleState(Builder builder) {
        this.version = builder.version;
        this.sequence = builder.sequence;
        this.timestampMs = builder.timestampMs;
//        this.vehSpeedKph = clamp(builder.vehSpeedKph, 0, 200);
        this.vehSpeedKph = builder.vehSpeedKph;
//        this.engRpm = clamp(builder.engRpm, 0, 8000);
        this.engRpm = builder.engRpm;
        this.gear = builder.gear == null ? Gear.P : builder.gear;
        this.soc = clamp(builder.soc, 0, 100);
        this.turnSignal = builder.turnSignal == null ? TurnSignal.NONE : builder.turnSignal;
        this.parkingBrake = builder.parkingBrake;
        this.warning = builder.warning == null ? WarningState.NONE : builder.warning;
        this.validity = builder.validity == null ? DataValidity.INCOMPLETE : builder.validity;
        this.doorLock = builder.doorLock;
        this.headlightsState = builder.headlightsState;
        this.highBeamLightsState = builder.highBeamLightsState;
        this.engineCoolantTemp = builder.engineCoolantTemp;
        this.evBatteryLevel = builder.evBatteryLevel;
        this.dataStatus = builder.dataStatus;
        this.beltWarning = builder.beltWarning;
    }

    public int getVersion() { return version; }
    public long getSequence() { return sequence; }
    public long getTimestampMs() { return timestampMs; }
    public int getVehSpeedKph() { return vehSpeedKph; }
    public int getEngRpm() { return engRpm; }
    public Gear getGear() { return gear; }
    public int getSoc() { return soc; }
    public TurnSignal getTurnSignal() { return turnSignal; }
    public boolean isParkingBrake() { return parkingBrake; }
    public WarningState getWarning() { return warning; }
    public DataValidity getValidity() { return validity; }
    public Boolean getDoorLock() { return doorLock; }
    public Boolean getBeltWarning() { return beltWarning; }
    public Integer getHeadlightsState() { return headlightsState; }
    public Integer getHighBeamLightsState() { return highBeamLightsState; }
    public Float getEngineCoolantTemp() { return engineCoolantTemp; }
    public Float getEvBatteryLevel() { return evBatteryLevel; }
    public DataStatus getDataStatus() { return dataStatus; }

    public String toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("version", version);
        json.put("seq", sequence);
        json.put("timestampMs", timestampMs);
        json.put("speedKph", vehSpeedKph);
        json.put("rpm", engRpm);
        json.put("gear", gear.getValue());
        json.put("soc", soc);
        json.put("turnSignal", turnSignal.getValue());
        json.put("parkingBrake", parkingBrake);
        json.put("warning", warning.getValue());
        json.put("validity", validity.getValue());
        json.put("doorLock", doorLock);
        json.put("beltWarning", beltWarning);
        json.put("headlightsState", headlightsState);
        json.put("highBeamLightsState", highBeamLightsState);
        json.put("engineCoolantTemp", engineCoolantTemp);
        json.put("evBatteryLevel", evBatteryLevel);
        if (dataStatus != null) {
            json.put("dataStatus", dataStatus.getValue());
        }
        return json.toString();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    public static class Builder {
        private int version;
        private long sequence;
        private long timestampMs;
        private int vehSpeedKph;
        private int engRpm;
        private Gear gear = Gear.P;
        private int soc;
        private TurnSignal turnSignal = TurnSignal.NONE;
        private boolean parkingBrake;
        private WarningState warning = WarningState.NONE;
        private DataValidity validity = DataValidity.INCOMPLETE;
        private Boolean doorLock = false;
        private Boolean beltWarning = false;
        private Integer headlightsState = 0;
        private Integer highBeamLightsState;
        private Float engineCoolantTemp;
        private Float evBatteryLevel;
        private DataStatus dataStatus;

        public Builder() {}

        public int getVersion() { return version; }
        public Builder setVersion(int version) { this.version = version; return this; }

        public long getSequence() { return sequence; }
        public Builder setSequence(long sequence) { this.sequence = sequence; return this; }

        public long getTimestampMs() { return timestampMs; }
        public Builder setTimestampMs(long timestampMs) { this.timestampMs = timestampMs; return this; }

        public int getVehSpeedKph() { return vehSpeedKph; }
        public Builder setVehSpeedKph(int vehSpeedKph) { this.vehSpeedKph = vehSpeedKph; return this; }

        public int getEngRpm() { return engRpm; }
        public Builder setEngRpm(int engRpm) { this.engRpm = engRpm; return this; }

        public Gear getGear() { return gear; }
        public Builder setGear(Gear gear) { this.gear = gear; return this; }
        public Builder setGear(String gearStr) { this.gear = Gear.fromString(gearStr); return this; }

        public int getSoc() { return soc; }
        public Builder setSoc(int soc) { this.soc = soc; return this; }

        public TurnSignal getTurnSignal() { return turnSignal; }
        public Builder setTurnSignal(TurnSignal turnSignal) { this.turnSignal = turnSignal; return this; }

        public boolean isParkingBrake() { return parkingBrake; }
        public Builder setParkingBrake(boolean parkingBrake) { this.parkingBrake = parkingBrake; return this; }

        public WarningState getWarning() { return warning; }
        public Builder setWarning(WarningState warning) { this.warning = warning; return this; }

        public DataValidity getValidity() { return validity; }
        public Builder setValidity(DataValidity validity) { this.validity = validity; return this; }

        public Boolean getDoorLock() { return doorLock; }
        public Builder setDoorLock(Boolean doorLock) { this.doorLock = doorLock; return this; }

        public Boolean getBeltWarning() { return beltWarning; }
        public Builder setBeltWarning(Boolean beltWarning) { this.beltWarning = beltWarning; return this; }

        public Integer getHeadlightsState() { return headlightsState; }
        public Builder setHeadlightsState(Integer headlightsState) { this.headlightsState = headlightsState; return this; }

        public Integer getHighBeamLightsState() { return highBeamLightsState; }
        public Builder setHighBeamLightsState(Integer highBeamLightsState) { this.highBeamLightsState = highBeamLightsState; return this; }

        public Float getEngineCoolantTemp() { return engineCoolantTemp; }
        public Builder setEngineCoolantTemp(Float engineCoolantTemp) { this.engineCoolantTemp = engineCoolantTemp; return this; }

        public Float getEvBatteryLevel() { return evBatteryLevel; }
        public Builder setEvBatteryLevel(Float evBatteryLevel) { this.evBatteryLevel = evBatteryLevel; return this; }

        public DataStatus getDataStatus() { return dataStatus; }
        public Builder setDataStatus(DataStatus dataStatus) { this.dataStatus = dataStatus; return this; }

        public VehicleState build() {
            return new VehicleState(this);
        }
    }
}
