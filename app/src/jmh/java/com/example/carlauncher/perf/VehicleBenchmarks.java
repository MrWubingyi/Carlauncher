package com.example.carlauncher.perf;

import com.example.carlauncher.data.someip.VehicleEventCodec;
import com.example.carlauncher.model.Gear;
import com.example.carlauncher.model.VehicleStateValidator;
import com.example.vehicle.someip.codec.SomeipPayloadCodec;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class VehicleBenchmarks {
    private byte[] binary;
    private byte[] snapshot;
    private int sequence;

    @Setup
    public void prepare() {
        binary = SomeipPayloadCodec.encode(1, 1700000000000L, 80, 3, 0);
        snapshot = ("{\"version\":1,\"seq\":51,\"timestampMs\":1700000000000,"
            + "\"speedKph\":98,\"rpm\":3250,\"gear\":3,\"soc\":70,\"turnSignal\":1,"
            + "\"parkingBrake\":true,\"warning\":0,\"validity\":0,\"doorLock\":true,"
            + "\"beltWarning\":true,\"headlightsState\":1,\"highBeamLightsState\":0,"
            + "\"engineCoolantTemp\":90,\"evBatteryLevel\":70,\"dataStatus\":0}")
            .getBytes(StandardCharsets.UTF_8);
    }

    @Benchmark public Object decodeBinary() { return SomeipPayloadCodec.decode(binary); }
    @Benchmark public Object encodeBinary() {
        return SomeipPayloadCodec.encode(sequence++, 1700000000000L, 80, 3, 0);
    }
    @Benchmark public Object decodeSnapshot() throws Exception { return VehicleEventCodec.decode(snapshot); }
    @Benchmark public Object validateState() { return VehicleStateValidator.validate(sequence++ % 201, Gear.D); }
}
