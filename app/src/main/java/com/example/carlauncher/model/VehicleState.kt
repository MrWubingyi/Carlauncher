package com.example.carlauncher.model

import com.example.carlauncher.data.DataStatus
import org.json.JSONException
import org.json.JSONObject

/** 车辆状态数据模型，代表车辆在某一时刻的核心运行参数。 */
open class VehicleState private constructor(builder: Builder) {

    open val version: Int = builder.getVersion() // 协议版本号
    open val sequence: Long = builder.getSequence() // 消息序列号，用于追踪消息顺序
    open val timestampMs: Long = builder.getTimestampMs() // 生成状态时的时间戳 (毫秒)
    open val vehSpeedKph: Int = builder.getVehSpeedKph() // 车速 (公里/小时)
    open val engRpm: Int = builder.getEngRpm() // 发动机/电机转速 (RPM)
    open val gear: Gear = builder.getGear() ?: Gear.P // 当前档位 (P, R, N, D)
    open val soc: Int = clamp(builder.getSoc(), 0, 100) // 电池电量状态 (State of Charge, 0-100)
    open val turnSignal: TurnSignal = builder.getTurnSignal() ?: TurnSignal.NONE // 转向灯状态
    open val isParkingBrake: Boolean = builder.isParkingBrake() // 驻车制动状态 (true = 开启)
    open val warning: WarningState = builder.getWarning() ?: WarningState.NONE // 告警
    open val validity: DataValidity = builder.getValidity() ?: DataValidity.INCOMPLETE
    open val doorLock: Boolean? = builder.getDoorLock() // 车门上锁状态
    open val beltWarning: Boolean? = builder.getBeltWarning() // 安全带告警 (true = 未系)
    open val headlightsState: Int? = builder.getHeadlightsState() // 前照灯/近光灯状态
    open val highBeamLightsState: Int? = builder.getHighBeamLightsState() // 远光灯状态
    open val engineCoolantTemp: Float? = builder.getEngineCoolantTemp() // 冷却液温度
    open val evBatteryLevel: Float? = builder.getEvBatteryLevel() // 电动汽车电池电量
    open val dataStatus: DataStatus? = builder.getDataStatus() // 数据质量

    @Throws(JSONException::class)
    open fun toJson(): String {
        val json = JSONObject()
        json.put("version", version)
        json.put("seq", sequence)
        json.put("timestampMs", timestampMs)
        json.put("speedKph", vehSpeedKph)
        json.put("rpm", engRpm)
        json.put("gear", gear.value)
        json.put("soc", soc)
        json.put("turnSignal", turnSignal.value)
        json.put("parkingBrake", isParkingBrake)
        json.put("warning", warning.value)
        json.put("validity", validity.value)
        json.put("doorLock", doorLock)
        json.put("beltWarning", beltWarning)
        json.put("headlightsState", headlightsState)
        json.put("highBeamLightsState", highBeamLightsState)
        json.put("engineCoolantTemp", engineCoolantTemp)
        json.put("evBatteryLevel", evBatteryLevel)
        if (dataStatus != null) {
            json.put("dataStatus", dataStatus!!.value)
        }
        return json.toString()
    }

    private fun clamp(value: Int, minimum: Int, maximum: Int): Int =
        Math.max(minimum, Math.min(value, maximum))

    open class Builder {
        private var version: Int = 0
        private var sequence: Long = 0
        private var timestampMs: Long = 0
        private var vehSpeedKph: Int = 0
        private var engRpm: Int = 0
        private var gear: Gear? = Gear.P
        private var soc: Int = 0
        private var turnSignal: TurnSignal? = TurnSignal.NONE
        private var parkingBrake: Boolean = false
        private var warning: WarningState? = WarningState.NONE
        private var validity: DataValidity? = DataValidity.INCOMPLETE
        private var doorLock: Boolean? = false
        private var beltWarning: Boolean? = false
        private var headlightsState: Int? = 0
        private var highBeamLightsState: Int? = null
        private var engineCoolantTemp: Float? = null
        private var evBatteryLevel: Float? = null
        private var dataStatus: DataStatus? = null

        open fun getVersion(): Int = version

        open fun setVersion(version: Int): Builder {
            this.version = version
            return this
        }

        open fun getSequence(): Long = sequence

        open fun setSequence(sequence: Long): Builder {
            this.sequence = sequence
            return this
        }

        open fun getTimestampMs(): Long = timestampMs

        open fun setTimestampMs(timestampMs: Long): Builder {
            this.timestampMs = timestampMs
            return this
        }

        open fun getVehSpeedKph(): Int = vehSpeedKph

        open fun setVehSpeedKph(vehSpeedKph: Int): Builder {
            this.vehSpeedKph = vehSpeedKph
            return this
        }

        open fun getEngRpm(): Int = engRpm

        open fun setEngRpm(engRpm: Int): Builder {
            this.engRpm = engRpm
            return this
        }

        open fun getGear(): Gear? = gear

        open fun setGear(gear: Gear?): Builder {
            this.gear = gear
            return this
        }

        open fun setGear(gearStr: String?): Builder {
            this.gear = Gear.fromString(gearStr)
            return this
        }

        open fun getSoc(): Int = soc

        open fun setSoc(soc: Int): Builder {
            this.soc = soc
            return this
        }

        open fun getTurnSignal(): TurnSignal? = turnSignal

        open fun setTurnSignal(turnSignal: TurnSignal?): Builder {
            this.turnSignal = turnSignal
            return this
        }

        open fun isParkingBrake(): Boolean = parkingBrake

        open fun setParkingBrake(parkingBrake: Boolean): Builder {
            this.parkingBrake = parkingBrake
            return this
        }

        open fun getWarning(): WarningState? = warning

        open fun setWarning(warning: WarningState?): Builder {
            this.warning = warning
            return this
        }

        open fun getValidity(): DataValidity? = validity

        open fun setValidity(validity: DataValidity?): Builder {
            this.validity = validity
            return this
        }

        open fun getDoorLock(): Boolean? = doorLock

        open fun setDoorLock(doorLock: Boolean?): Builder {
            this.doorLock = doorLock
            return this
        }

        open fun getBeltWarning(): Boolean? = beltWarning

        open fun setBeltWarning(beltWarning: Boolean?): Builder {
            this.beltWarning = beltWarning
            return this
        }

        open fun getHeadlightsState(): Int? = headlightsState

        open fun setHeadlightsState(headlightsState: Int?): Builder {
            this.headlightsState = headlightsState
            return this
        }

        open fun getHighBeamLightsState(): Int? = highBeamLightsState

        open fun setHighBeamLightsState(highBeamLightsState: Int?): Builder {
            this.highBeamLightsState = highBeamLightsState
            return this
        }

        open fun getEngineCoolantTemp(): Float? = engineCoolantTemp

        open fun setEngineCoolantTemp(engineCoolantTemp: Float?): Builder {
            this.engineCoolantTemp = engineCoolantTemp
            return this
        }

        open fun getEvBatteryLevel(): Float? = evBatteryLevel

        open fun setEvBatteryLevel(evBatteryLevel: Float?): Builder {
            this.evBatteryLevel = evBatteryLevel
            return this
        }

        open fun getDataStatus(): DataStatus? = dataStatus

        open fun setDataStatus(dataStatus: DataStatus?): Builder {
            this.dataStatus = dataStatus
            return this
        }

        open fun build(): VehicleState = VehicleState(this)
    }
}
