# Compose 座舱重绘

以提供的深色双栏设计图为视觉基准：38:62 分栏、独立能量状态栏与空调面板、银色车辆、蓝色半圆速度表、地图底部媒体浮层、五项底部导航。系统安全区域由 WindowInsets.safeDrawing 处理，无额外固定底部留白；窄窗口改为纵向滚动布局。

## 功能边界

| 区域 | 行为 |
| --- | --- |
| Home | 总览、双区温度、冷暖、灯光、门锁、挡位、自动/手动演示 |
| Maps | 离线设计地图，可拖动、缩放、回中、全屏和返回 |
| Navigation | 输入目的地，用 geo URI 调起已安装的地图应用；无应用时反馈 |
| Control | 开始/停止 SOME/IP 接收，查看车辆字段、连接、告警、有效性和数据源；应用抽屉、设置、广播、数据库、诊断入口 |
| Media | 演示队列、上一首/下一首、播放进度、拖动定位、暂停、收藏、单曲循环、静音状态；可打开音乐应用 |
| 顶栏 | 当前日期；Wi-Fi/蓝牙设置入口；头像打开应用抽屉 |

默认 DEMO 显示参考图示例值。启动车辆服务后自动切换为真实接收；只读显示上报状态，缺失值为“—”。停止/解绑后清空实时仪表，不能以演示按钮覆写真实挡位。前台通知返回 Compose 主页面。温度、冷暖、驾驶模式、演示开关和当前页通过 SavedStateHandle 保存。

地图为本地绘制，不提供实时定位和路线规划。天气为明确标注的示例值。音乐仅演示交互和计时，不含歌曲音源。车控服务现有协议为只读接收，未实现真实空调、自动驾驶或车锁命令。真实导航和音频交由设备已有应用处理。

数据库结果回传兼容工作区现有改动，但 Compose 不依赖这些未提交源码的新常量；既有数据库版本仍可打开并返回。

## 验证

- `:app:assembleDebug`：编译安装。
- `:app:testDebugUnitTest`：现有 JVM 回归。
- `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zqautomotive.launcher.ComposeDashboardTest`：温度/模式及重建恢复、地图返回、五页导航、媒体队列和边界、实时数据替换/断线清空。
- 实机截图与执行记录保存在本地 `evidence/compose-redesign/`；该目录按项目既有规则不提交。

2026-09-28 验证结果：Debug 构建通过；220 项 JVM 测试通过；7 项 Compose 设备测试通过（含首次点击启动真实 Service、停止解绑）；Lint 为 0 错误、58 警告、7 提示。设备为 Android 15 Automotive 模拟器。手动 SOME/IP 联调收到 ONLINE、96 km/h、3200 rpm、70% 电量；实时数据展示与原服务一致。

已查看 1408×792、1408×1056 和 1024×768 三种窗口截图，验证地图浮层避让和仪表布局，最终恢复 1408×792。设备测试前需结束 `android layout` 的交互会话，以免其 UiAutomation 占用妨碍运行时权限处理。此轮设备测试未生成可用的 `coverage.ec`，不据此宣称覆盖率达标。

## 车辆素材

使用内置 imagegen 工具生成透明 PNG，已复制进 `app/src/main/res/drawable-nodpi/`，运行时无需网络。原始生成提示见 [compose-assets-prompts.md](compose-assets-prompts.md)。

- `dashboard_car_rear.png`：俯视车尾。
- `dashboard_car_side.png`：侧面车身。
