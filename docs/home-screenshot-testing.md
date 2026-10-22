# 首页截图回归（Roborazzi）

首页截图测试位于 `app/src/test/java/com/example/carlauncher/ui/screenshot/HomeScreenshotTest.kt`。
它运行真实 `CarLauncherScreen` 与 `CarLauncherViewModel`，用同一套测试分别渲染重构前和
重构后源码。卡片、布局、地图、队列弹窗均来自生产代码，没有重写一份测试 UI。

## 固定场景与运行环境

| 图片 | 场景 |
| --- | --- |
| `home_demo.png` | 默认演示首页 |
| `map_fullscreen.png` | 点击 Full Screen 后的地图全屏 |
| `media_queue.png` | 点击播放队列后的弹窗及背景 |
| `home_connected.png` | 注入车辆快照后的首页（64 km/h、47% 电量） |
| `vehicle_connected.png` | Control 页已连接状态 |
| `vehicle_disconnected.png` | 从连接状态解绑后的 Control 页 |

环境固定为 Windows、JDK 21、Robolectric 4.17 / API 35 Native Graphics、Roborazzi 1.76.0，
1408 × 792 dp / mdpi、默认字体缩放 1、en-US、Asia/Shanghai。
测试通过 `LocalDashboardClock` 将日期固定到 2026-10-21 10:00，生产默认仍取系统时间。
媒体保持暂停；使用 Compose 测试帧时钟推进动画，先排空 UI 通知再推进帧，不使用真实 sleep。
车辆服务替身只提供固定快照，不调用 Service.onCreate、不启动 JNI、不连接车辆。

截图校验使用 `changeThreshold = 0`、`maxDistance = 0`，没有区域遮罩或位移容差。
测试先断言关键文本可见，再截图；全屏捕获包含 Dialog，避免只截到弹窗背后的页面。

## 日常校验（不更新基准）

先配置本机完整 JDK 21 的 `JAVA_HOME`。在仓库根目录运行：

```powershell
.\gradlew.bat :app:verifyRoborazziDebug --tests 'com.example.carlauncher.ui.screenshot.HomeScreenshotTest' '-Proborazzi.test.compare=true'
```

原生 HTML 报告在 `app/build/reports/roborazzi/debug/index.html`，机器结果在
`app/build/test-results/roborazzi/debug/results-summary.json`。
基准图位于 `app/src/test/screenshots/home/`，`baseline-provenance.json` 记录来源与 SHA-256。
PowerShell 中带点号的 `-P` 参数必须加引号，避免被拆为 Gradle 任务名。

不要在校验失败后直接执行 record 或 verifyAndRecord 来消除差异；先核对实际变化。
基准更新属于有意的 UI 变更，需要审阅图片后再更新。

## 重建此次“改造前”基准

旧源码来自 `c8a706161d32928a71c83b479ca618fb68debc63` 的以下四个文件：
`ComposeMainActivity.kt`、`CarLauncherScreen.kt`、`CarLauncherViewModel.kt`、`MapCard.kt`。
上一轮改造开始时这四个文件没有既有未提交改动。

`tools/visual-regression/prepare_baseline.py` 在新的 `build/` 子目录复制必要构建输入，
仅用上述 Git blob 替换这四个文件并省略重构新增类。其余源码（包括既有未提交改动）、
时钟接口和截图测试夹具与当前工作区一致。它不 checkout、reset、stash，也不修改 Git 索引。
输出目录必须不存在，不能覆盖已有副本。此脚本是本次明确重构边界的重建工具，不能用于任意历史提交。

```powershell
python tools/visual-regression/prepare_baseline.py --ref c8a706161d32928a71c83b479ca618fb68debc63 --output build/ui-regression/before-new
Push-Location build/ui-regression/before-new
.\gradlew.bat :app:recordRoborazziDebug --tests 'com.example.carlauncher.ui.screenshot.HomeScreenshotTest'
Pop-Location
```

只有旧副本 record 成功且确认 6 张图片都存在后，才能将它们作为基准。
源码清单 `source-manifest.json` 区分 Git blob 和共享工作区输入，保存各自 SHA-256。

如需同时保留当前截图供并排展示，将 record 输出指向独立的 `build/` 目录，**不能指向基准目录**：

```powershell
.\gradlew.bat :app:recordRoborazziDebug --tests 'com.example.carlauncher.ui.screenshot.HomeScreenshotTest' '-PhomeScreenshotDirectory=build/outputs/home-current'
python tools/visual-regression/report.py --baseline app/src/test/screenshots/home --current app/build/outputs/home-current --manifest build/ui-regression/before-new-source-manifest.json --output build/ui-regression/report
```

报告脚本需要 Pillow。它独立计算精确 RGB 像素差异，输出便携的 `summary-zh.html`、
前后图、粉红色差异高亮图和 `pixel-results.json`。不把当前图复制成基准。

## CI 自动生成报告

GitHub Actions 的 `ui-regression` 使用 Windows 2022；GitLab 的同名任务复用
`carlauncher-device` Windows shell Runner，与 simpleperf 共用资源锁，避免争用主机。
GitLab Runner 需要完整 JDK 21、Python 3.12、可用的 pip、上述固定 Android SDK 组件，
并正确配置 `JAVA_HOME` 和 `ANDROID_HOME`。截图任务不操作模拟器，不依赖原生构建任务。
Linux 构建和原有质量门禁继续独立运行。Windows Server 与本机的渲染一致性仍需首次 CI 实跑确认；
不因环境差异放宽零像素容差，也不自动更新基准。

任务执行 `python tools/visual-regression/run_ci.py`：先将当前图录制到唯一的 build 目录，
再用版本管理中的旧基准执行 verify，最后生成中文 HTML。录制不会写入基准目录。
任何 Gradle 失败、图片缺失、尺寸变化或像素差异均返回非零退出码；失败时仍尝试生成报告。
每次运行独立保存到 `build/ui-regression/ci/<运行ID>/`，不会覆盖上一次本地报告。

在 GitHub Actions 下载 `CarLauncher-ui-regression-<编号>`，或在 GitLab Job 的
Artifacts 下载 `UI screenshot regression`，解压后打开对应目录的 `summary-zh.html`。
产物保留 14 天，包含前后图片、差异图、退出码、提交标识、Gradle 日志及 JUnit XML。
中文报告图片链接可离线打开；原生报告作为原始诊断保存，可能含工作区相对路径。
初始化依赖阶段失败、强制取消或超时可能无法生成完整报告，应查看 Job 日志。
采用 [GitLab Job artifacts](https://docs.gitlab.com/ci/jobs/job_artifacts/) 的 `when: always`
和 GitHub 的 `if: always()` 保留失败证据。

基准 PNG、来源 JSON、截图测试、Gradle 依赖和 CI 脚本需要一并提交后才会在远程流程生效。
CI 不运行 `prepare_baseline.py` 重建历史基准，也不执行 `verifyAndRecord`。

## 验收边界

这是本机视觉回归，不运行 Launcher Activity 的真实服务绑定，也不证明 AAOS 系统窗口、
Car/VHAL、跨进程通信、真实地图 TaskView、车辆硬件或性能通过验收。
它不能代替现有 `ComposeDashboardTest` 的设备交互测试。
新增截图依赖不改变原有静态检查、原生库来源校验或覆盖率门槛。

依赖与接口参考：[Roborazzi 官方文档](https://github.com/takahirom/roborazzi)、
[Robolectric 配置说明](https://robolectric.org/getting-started/)。
