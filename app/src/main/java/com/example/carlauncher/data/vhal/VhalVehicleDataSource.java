package com.example.carlauncher.data.vhal;

import android.car.Car;
import android.car.VehicleGear;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.VehicleTurnSignal;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.data.VehicleDataSource;
import com.example.carlauncher.model.DataValidity;
import com.example.carlauncher.model.TurnSignal;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.model.VehicleStateValidator;
import com.example.carlauncher.model.WarningState;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Reads vehicle properties from AAOS CarService and publishes unified snapshots.
 */
public final class VhalVehicleDataSource implements VehicleDataSource {
    private static final String TAG = "VEHICLE_VHAL";
    private static final int GLOBAL_AREA_ID = 0;
    private static final long NO_DATA_TIMEOUT_MS = 1500L;
    private static final long WATCHDOG_PERIOD_MS = 500L;

    private final Context applicationContext;
    private final Handler carHandler = new Handler(Looper.getMainLooper());

    private Car car;
    private CarPropertyManager propertyManager;
    private ScheduledExecutorService watchdog;
    private Listener listener;

    private boolean running;
    private int speedPropertyId;
    private int turnSignalPropertyId;
    private boolean speedRegistered;
    private boolean gearRegistered;
    private boolean turnSignalRegistered;
    private boolean parkingBrakeRegistered;

    private Float speedMps;
    private Integer gearValue;
    private Integer turnSignalValue;
    private Boolean parkingBrakeValue;
    private long lastSpeedTimestampMs;
    private long sequence;
    private DataSourceStatus currentStatus = DataSourceStatus.STOPPED;

    public VhalVehicleDataSource(Context context) {
        applicationContext = context.getApplicationContext();
    }

    private final CarPropertyManager.CarPropertyEventCallback propertyCallback =
            new CarPropertyManager.CarPropertyEventCallback() {
                @Override
                public void onChangeEvent(CarPropertyValue value) {
                    handlePropertyChanged(value);
                }

                @Override
                public void onErrorEvent(int propertyId, int areaId) {
                    handlePropertyError(propertyId, areaId, 0);
                }

                @Override
                public void onErrorEvent(
                        int propertyId,
                        int areaId,
                        int errorCode
                ) {
                    handlePropertyError(propertyId, areaId, errorCode);
                }
            };

    @Override
    public synchronized void start(Listener listener) {
        if (running) {
            return;
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }

        running = true;
        this.listener = listener;
        updateStatusLocked(DataSourceStatus.CONNECTING);
        startWatchdogLocked();
        connectCarServiceLocked();
    }

    private void connectCarServiceLocked() {
        try {
            car = Car.createCar(
                    applicationContext,
                    carHandler,
                    Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                    (connectedCar, ready) -> onCarLifecycleChanged(
                            connectedCar,
                            ready
                    )
            );
        } catch (RuntimeException exception) {
            reportFatalErrorLocked("Create CarService client failed", exception);
        }
    }

    private synchronized void onCarLifecycleChanged(
            Car connectedCar,
            boolean ready
    ) {
        if (!running) {
            return;
        }

        if (!ready) {
            propertyManager = null;
            resetValuesLocked();
            updateStatusLocked(DataSourceStatus.DISCONNECTED);
            return;
        }

        try {
            propertyManager = (CarPropertyManager) connectedCar.getCarManager(
                    Car.PROPERTY_SERVICE
            );
            if (propertyManager == null) {
                reportFatalErrorLocked(
                        "CarPropertyManager is unavailable",
                        new IllegalStateException("property service missing")
                );
                return;
            }

            registerPropertyCallbacksLocked();
        } catch (RuntimeException exception) {
            reportFatalErrorLocked("Connect CarPropertyManager failed", exception);
        }
    }

    private void registerPropertyCallbacksLocked() {
        speedPropertyId = chooseSpeedPropertyLocked();
        speedRegistered = registerPropertyLocked(
                speedPropertyId,
                CarPropertyManager.SENSOR_RATE_UI,
                true
        );

        gearRegistered = registerPropertyLocked(
                VehiclePropertyIds.GEAR_SELECTION,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
                false
        );

        turnSignalPropertyId = chooseTurnSignalPropertyLocked();
        turnSignalRegistered = turnSignalPropertyId != 0
                && registerPropertyLocked(
                turnSignalPropertyId,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
                false
        );

        parkingBrakeRegistered = registerPropertyLocked(
                VehiclePropertyIds.PARKING_BRAKE_ON,
                CarPropertyManager.SENSOR_RATE_ONCHANGE,
                false
        );

        if (!speedRegistered) {
            reportFatalErrorLocked(
                    "No readable VHAL speed property",
                    new IllegalStateException("speed subscription failed")
            );
            return;
        }

        Log.i(
                TAG,
                "VHAL subscribed: speed=" + speedRegistered
                        + " gear=" + gearRegistered
                        + " turn=" + turnSignalRegistered
                        + " parkingBrake=" + parkingBrakeRegistered
        );
        updateStatusLocked(DataSourceStatus.NO_DATA);
    }

    private int chooseSpeedPropertyLocked() {
        if (isPropertySupportedLocked(
                VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY
        )) {
            return VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY;
        }
        return VehiclePropertyIds.PERF_VEHICLE_SPEED;
    }

    private int chooseTurnSignalPropertyLocked() {
        if (isPropertySupportedLocked(
                VehiclePropertyIds.TURN_SIGNAL_LIGHT_STATE
        )) {
            return VehiclePropertyIds.TURN_SIGNAL_LIGHT_STATE;
        }
        if (isPropertySupportedLocked(VehiclePropertyIds.TURN_SIGNAL_STATE)) {
            Log.i(TAG, "Use legacy TURN_SIGNAL_STATE property");
            return VehiclePropertyIds.TURN_SIGNAL_STATE;
        }
        return 0;
    }

    private boolean registerPropertyLocked(
            int propertyId,
            float sampleRate,
            boolean required
    ) {
        if (!isPropertySupportedLocked(propertyId)) {
            Log.w(TAG, "VHAL property not supported: " + propertyId);
            return false;
        }

        try {
            boolean registered = propertyManager.registerCallback(
                    propertyCallback,
                    propertyId,
                    sampleRate
            );
            if (!registered) {
                Log.w(TAG, "VHAL subscription rejected: " + propertyId);
            }
            return registered;
        } catch (SecurityException exception) {
            Log.w(
                    TAG,
                    "No permission for VHAL property " + propertyId,
                    exception
            );
            if (required) {
                notifyErrorLocked(exception);
            }
            return false;
        } catch (RuntimeException exception) {
            Log.w(
                    TAG,
                    "VHAL subscription failed: " + propertyId,
                    exception
            );
            if (required) {
                notifyErrorLocked(exception);
            }
            return false;
        }
    }

    private boolean isPropertySupportedLocked(int propertyId) {
        if (propertyManager == null) {
            return false;
        }
        try {
            CarPropertyConfig<?> config =
                    propertyManager.getCarPropertyConfig(propertyId);
            return config != null;
        } catch (RuntimeException exception) {
            Log.w(TAG, "Read VHAL property config failed: " + propertyId, exception);
            return false;
        }
    }

    private synchronized void handlePropertyChanged(CarPropertyValue value) {
        if (!running || value == null) {
            return;
        }
        if (value.getStatus() != CarPropertyValue.STATUS_AVAILABLE) {
            Log.w(
                    TAG,
                    "VHAL property unavailable: " + value.getPropertyId()
                            + " status=" + value.getStatus()
            );
            return;
        }

        Object propertyValue = value.getValue();
        int propertyId = value.getPropertyId();

        if (propertyId == speedPropertyId && propertyValue instanceof Float) {
            speedMps = (Float) propertyValue;
            lastSpeedTimestampMs = System.currentTimeMillis();
        } else if (propertyId == VehiclePropertyIds.GEAR_SELECTION
                && propertyValue instanceof Integer) {
            gearValue = (Integer) propertyValue;
        } else if (propertyId == turnSignalPropertyId
                && propertyValue instanceof Integer) {
            turnSignalValue = (Integer) propertyValue;
        } else if (propertyId == VehiclePropertyIds.PARKING_BRAKE_ON
                && propertyValue instanceof Boolean) {
            parkingBrakeValue = (Boolean) propertyValue;
        } else {
            return;
        }

        publishSnapshotLocked();
    }

    private void publishSnapshotLocked() {
        Listener currentListener = listener;
        if (!running || currentListener == null || speedMps == null) {
            return;
        }

        float speedKph = speedMps * 3.6f;
        String gear = gearValue == null ? "P" : mapGear(gearValue);
        TurnSignal turnSignal = turnSignalValue == null
                ? TurnSignal.NONE
                : mapTurnSignal(turnSignalValue);
        boolean parkingBrake = parkingBrakeValue != null && parkingBrakeValue;
        DataValidity validity = VehicleStateValidator.validate(
                speedKph,
                gear
        );

        VehicleState state = new VehicleState(
                1,
                ++sequence,
                System.currentTimeMillis(),
                Math.round(speedKph),
                calculateRpm(speedKph),
                gear,
                70,
                turnSignal,
                parkingBrake,
                WarningState.NONE,
                validity
        );

        updateStatusLocked(DataSourceStatus.CONNECTED);
        if (sequence == 1 || sequence % 100 == 0) {
            Log.i(
                    TAG,
                    "snapshot seq=" + sequence
                            + " speed=" + state.getVehSpeedKph()
                            + " gear=" + state.getGear()
                            + " turn=" + state.getTurnSignal()
                            + " parkingBrake=" + state.isParkingBrake()
            );
        }
        currentListener.onStateChanged(state);
    }

    private synchronized void handlePropertyError(
            int propertyId,
            int areaId,
            int errorCode
    ) {
        if (!running) {
            return;
        }

        Log.w(
                TAG,
                "VHAL property error: property=" + propertyId
                        + " area=" + areaId
                        + " code=" + errorCode
        );
        if (propertyId == speedPropertyId) {
            updateStatusLocked(DataSourceStatus.NO_DATA);
        }
    }

    private void startWatchdogLocked() {
        watchdog = Executors.newSingleThreadScheduledExecutor();
        watchdog.scheduleWithFixedDelay(
                this::checkDataTimeout,
                WATCHDOG_PERIOD_MS,
                WATCHDOG_PERIOD_MS,
                TimeUnit.MILLISECONDS
        );
    }

    private synchronized void checkDataTimeout() {
        if (!running || lastSpeedTimestampMs == 0L) {
            return;
        }

        long ageMs = System.currentTimeMillis() - lastSpeedTimestampMs;
        if (ageMs > NO_DATA_TIMEOUT_MS) {
            updateStatusLocked(DataSourceStatus.NO_DATA);
        }
    }

    private void updateStatusLocked(DataSourceStatus status) {
        if (currentStatus == status) {
            return;
        }

        currentStatus = status;
        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onSourceStatusChanged(status);
        }
    }

    private void notifyErrorLocked(Exception exception) {
        Listener currentListener = listener;
        if (currentListener != null) {
            currentListener.onError(exception);
        }
    }

    private void reportFatalErrorLocked(
            String message,
            RuntimeException exception
    ) {
        Log.e(TAG, message, exception);
        updateStatusLocked(DataSourceStatus.ERROR);
        notifyErrorLocked(exception);
    }

    private void resetValuesLocked() {
        speedMps = null;
        gearValue = null;
        turnSignalValue = null;
        parkingBrakeValue = null;
        lastSpeedTimestampMs = 0L;
    }

    private static int calculateRpm(float speedKph) {
        if (speedKph <= 0f) {
            return 800;
        }
        return Math.min(8000, 800 + Math.round(speedKph) * 25);
    }

    private static String mapGear(int gear) {
        switch (gear) {
            case VehicleGear.GEAR_PARK:
                return "P";
            case VehicleGear.GEAR_REVERSE:
                return "R";
            case VehicleGear.GEAR_NEUTRAL:
                return "N";
            case VehicleGear.GEAR_DRIVE:
                return "D";
            default:
                return "P";
        }
    }

    private static TurnSignal mapTurnSignal(int value) {
        boolean left = (value & VehicleTurnSignal.STATE_LEFT) != 0;
        boolean right = (value & VehicleTurnSignal.STATE_RIGHT) != 0;

        if (left && right) {
            return TurnSignal.HAZARD;
        }
        if (left) {
            return TurnSignal.LEFT;
        }
        if (right) {
            return TurnSignal.RIGHT;
        }
        return TurnSignal.NONE;
    }

    private void unregisterCallbacksLocked() {
        if (propertyManager == null) {
            return;
        }

        try {
            propertyManager.unregisterCallback(propertyCallback);
        } catch (RuntimeException exception) {
            Log.w(TAG, "Unregister VHAL callbacks failed", exception);
        }

        speedRegistered = false;
        gearRegistered = false;
        turnSignalRegistered = false;
        parkingBrakeRegistered = false;
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;
        unregisterCallbacksLocked();

        if (watchdog != null) {
            watchdog.shutdownNow();
            watchdog = null;
        }

        if (car != null) {
            car.disconnect();
            car = null;
        }
        propertyManager = null;
        resetValuesLocked();

        updateStatusLocked(DataSourceStatus.STOPPED);
        listener = null;
    }

    @Override
    public synchronized boolean isRunning() {
        return running;
    }
}