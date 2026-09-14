# 远端 mock → Android / LVGL Event 订阅

`vehicle_mock_service` 是当前车辆数据提供者。它将原 Android
`MockVehicleDataSource` 的生成逻辑移植为 C++，两个客户端共享同一序列和时间戳。
Android 前台 Service 不再启动本地 mock、TCP 转发或 Method 发送。
原 Java mock 与 Method 探针保留供离线回归，不参与当前生产链路。

| 项目 | 契约 |
|---|---|
| Service / Instance | `0x1111 / 0x2222` |
| Event / EventGroup | `0x8001 / 0x0001` |
| 类型 | 非可靠 `ET_EVENT`，UDP 单播通知各订阅端 |
| SD / 服务端口 | UDP `30490 / 30509` |
| Payload | UTF-8 JSON 完整快照，`version=1`，无换行和 NUL 终止符，最大 4096 B |
| 字段 | 根目录 PROTOCOL.md 的全部车辆字段；与旧 Method 16 B payload 不同 |
| 更新频率 | 每 100 ms 生成一次；新订阅者在下一帧收到当前状态 |
| 默认模式 | 每 100 ms 持续发布，不注入静默或非法值 |
| 显式故障模式 | 服务加 `--fault-cycle`：`seq % 1200` 为 600–699 时越界，700–999 时静默，1000 起恢复 |

Android JNI 将 notification 复制到 Java byte[]，主线程校验并更新 Service 快照，
Repository/LiveData 驱动控件。服务可用不等于在线；收到可解码 Event 才在线。
非法数值标记 INVALID，静默 3000 ms 清空旧数据，服务不可用立即清空，恢复 Event 后恢复。
重复或更旧时间戳的数据不会回退界面。状态机内部保留旧 RESPONSE_* 枚举兼容测试，
界面显示 WAITING EVENT / EVENT TIMEOUT，不把 Event 伪装成 Method Response。

LVGL 订阅线程调用既有互斥快照模型，100 ms `ui_bridge` 定时器在 UI 线程更新控件。
`vehicle_data_update_from_tcp` 是现有模型 API 的历史名称，此链路没有 TCP JSON 转发。
默认不再启动 19090 TCP 服务，避免两个来源竞争覆盖车辆状态。

## Ubuntu 构建和启动

实际项目：`/root/develop/carlauncher-probe-20260909`；LVGL：`/root/develop/dashboard_simulator`。
把本目录的新源文件、JSON、安装脚本及 CMakeLists.txt 同步到 probe 后：

```bash
cd /root/develop/carlauncher-probe-20260909
cmake -S . -B build -Dvsomeip3_DIR=/root/develop/vsomeip/build
cmake --build build -j2
ctest --test-dir build --output-on-failure
python3 install-lvgl-event-client.py
cmake -S /root/develop/dashboard_simulator -B /root/develop/dashboard_simulator/build \
  -Dvsomeip3_DIR=/root/develop/vsomeip/build
cmake --build /root/develop/dashboard_simulator/build -j4
```

## Android 与可见 LVGL 的统一启动方式

请完整执行 [项目 README](../../README.md) 的启动顺序：

1. 关闭旧实验，运行 helper 时必须带 `--network-only --duration 0`。
2. 使用 helper 输出的 `MOCK_CONFIG` 启动唯一 mock。
3. 使用同一运行目录的 `LVGL_CONFIG` 在图形终端启动可见 LVGL。
4. 有窗口的模拟器带 `-wifi-socket` 启动，转接器带 `-DurationSeconds 0`。
5. Android Studio 运行 App，点击 START RECEIVE。

两份生成配置都使用 `10.203.0.1` 和相同的路由命名空间；Android DHCP 为 `10.203.0.2`。
不能将目录中的静态 `mock-service.json` / `lvgl-client.json` 直接代替这两份生成配置。
可见 LVGL 不设置 `SDL_VIDEODRIVER=dummy`。`VEHICLE_EVENT_UI_TRACE=1` 可输出实际速度标签。

helper 不带 `--network-only` 的旧行为仅用于隔离自动测试：它会自行启动 mock 和无头 LVGL。
不要把该模式与另一套手动 mock/可见 LVGL 混用。

网络和服务都准备好后，显式专项测试命令为：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --offline '-Pandroid.testInstrumentationRunnerArguments.class=com.example.carlauncher.someip.VehicleEventProbeTest' '-Pandroid.testInstrumentationRunnerArguments.someipEvents=true'
```

该测试会自动操作并关闭测试 Activity；手动看 UI 时使用 app → Run。
