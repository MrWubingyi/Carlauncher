# TCP connection state-machine acceptance record

Date: 2026-08-26

## Scope

This package verifies the CarLauncher Java TCP state machine and the combined
cockpit connection/data-validity presentation across Android and Ubuntu LVGL.

## Acceptance result

Accepted for the W35 SOC abnormal-recovery increment.

- Initial state is service-unbound, transport offline, no stale vehicle data,
  and the primary action is `START SEND`.
- A successful start follows `DISCONNECTED -> CONNECTING -> RECOVERING -> ONLINE`.
- The first valid frame is required before the transport state becomes `ONLINE`.
- Normal and warning frames remain valid and continue updating the UI.
- Invalid frames keep the TCP transport online while the cockpit state becomes
  `INVALID_DATA`; the captured values include speed 255, RPM 9999, coolant -999,
  EV battery 150, `validity=1`, and `dataStatus=1`.
- During the no-data interval Android reports `NO_DATA / INVALID_DATA` and LVGL
  reports `Waiting for vehicle`, without presenting the previous normal state as
  current data.
- After valid frames resume, Android and LVGL return to online operation with
  matching vehicle values (82 km/h in the final screenshot).
- A transport failure is also covered: Broken pipe causes
  `ONLINE -> DISCONNECTED -> RECOVERING`; a refused connection remains in the
  retry path, and service restoration returns the state to `ONLINE`.
- The captured run contains no `FATAL EXCEPTION` or `UnsatisfiedLinkError`.

## Evidence index

| File | Evidence |
|---|---|
| `01-initial-service-unbound.png` | Initial stopped/unbound state and `START SEND` |
| `02-online-normal.png` | Valid online state, active sending, transport online |
| `03-online-warning.png` | Valid frame with `GENERAL_WARNING` |
| `04-invalid-data-android.png` | Android `INVALID_DATA / INVALID_SPEED` while transport remains online |
| `05-no-data-android-lvgl.png` | Android no-data state and LVGL `Waiting for vehicle` |
| `06-recovered-online-android-lvgl.png` | Android/LVGL recovery with matching 82 km/h |
| `Automotive-Android-15_2026-08-26_094210.logcat` | State transitions, invalid frames, failure/retry, and recovery |

## Reproduction and verification

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Expected unit-test result: five `CockpitConnectionStateTest` cases pass with no
failures or errors. Expected APK output:
`app/build/outputs/apk/debug/app-debug.apk`.

Verified on 2026-08-26: `BUILD SUCCESSFUL`, five tests, zero skipped, zero
failures, and zero errors. Debug APK SHA-256:
`7F2B28919B95A1A0CAF78D85DD15C3D0071E88C364CDB1ADBE1FDD3FAB03B3C8`.
Evidence-file hashes are recorded in `SHA256SUMS.txt`.

Useful Logcat filters:

```text
tag:VEHICLE_TCP tag:VEHICLE_LAUNCHER tag:VEHICLE_SERVICE tag:VEHICLE_VHAL package:mine
```

Key searchable markers are `TCP_STATE`, `Broken pipe`, `ECONNREFUSED`,
`speedKph=255`, `validity=1`, and `dataStatus=1`.

## Remaining boundary

The vsomeip/NDK prototype is not part of this Java TCP state-machine acceptance
package and must be integrated and reviewed separately.
