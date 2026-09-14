# seq 差异诊断：2026-09-14 用户截图

截图中 Android 约为 seq=2660，另两个终端的 mock/LVGL 约为 seq=14718。
通过只读进程和日志检查确认是不同 mock 实例。

| 实例 | PID | 启动时间（远端本地时间） | 配置/输出 |
|---|---|---|---|
| 手动 mock | 28344 | 16:00:22 | mock-service.json；stdout=/dev/pts/3 |
| 手动 LVGL | 28450 | 16:00:37 | ./bin/main |
| 测试 helper | 32215 | 16:20:39 | socket-tap-sd-events-20260914162039 |
| 测试 mock | 32230 | 16:20:39 | 该测试目录/service.log |
| 测试 LVGL | 32231 | 16:20:39 | 该测试目录/lvgl.log |

从测试目录和 Android Logcat 提取的相同帧：

| seq | mock timestampMs | Android Logcat epoch 秒 | 三处 speed（mock / Android / LVGL） |
|---|---|---|---|
| 2640 | 1789374306683 | 1789374305.723 | 80 / 80 / 80 |
| 2660 | 1789374308689 | 1789374307.730 | 80 / 80 / 80 |
| 3522 | 1789374395324 | 1789374394.362 | 44 / 44 / 44 |

Android 的序列属于 16:20 启动的测试 mock，并未落后另一套 mock 一万多帧。
两端时钟未同步，Logcat 时间比帧内生成时间小约 960 ms；这些时间不能直接用于
测量绝对网络延迟。三个样本相对时差稳定，未显示持续增长的接收积压。

修改：项目 README 补充模拟器关闭/前台启动及同源日志对照命令；
helper 明确输出 TEST_SOURCE、MOCK_LOG、LVGL_LOG，已同步远端并通过 Python AST 语法检查。
未停止或重启用户当前运行的模拟器、mock、LVGL。
