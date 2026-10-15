# CarLauncher simpleperf 集成指南

本项目通过独立的 `profiling` 构建采集 Java、Kotlin 和原生调用栈，用于定位座舱首页交互的 CPU 热点。
`record-dashboard.py` 自动执行可重复的首页操作，`summarize.py` 输出线程和函数排名。
采样需要运行中的 Android 设备；普通构建只验证代码和配置，不产生性能结论。

## 构建配置

| 配置 | 值及作用 |
| --- | --- |
| Gradle 构建类型 | `profiling`，通过 `initWith(release)` 继承当前 release 配置 |
| 包名 / 版本后缀 | `com.example.carlauncher.profiling` / `-profiling`，可与普通应用共存 |
| 桌面名称 / 签名 | CarLauncher Profiling / Debug 签名，便于本地安装 |
| 调试及插桩 | `isDebuggable = false`，关闭 Android 测试和 JVM 测试覆盖率 |
| Manifest | 仅 `app/src/profiling/AndroidManifest.xml` 声明 `<profileable android:shell="true" />` |
| 库变体 | `matchingFallbacks` 指向 release，兼容只有 debug/release 的 Android 库 |
| R8 | 当前 release 关闭优化，profiling 同样关闭；结果不代表未来开启 R8 后的性能 |

构建定义在 [app/build.gradle.kts](../app/build.gradle.kts)，变体资源位于
[app/src/profiling](../app/src/profiling)。没有增加应用运行时依赖或改变业务绘制逻辑。

脚本兼容两种原生目录布局：JNI 仍属于 `app` 时使用其 `build/intermediates/cxx/RelWithDebInfo`；
存在 `someip-android/build.gradle.kts` 时改用该模块的同名目录。
目录存在时通过 simpleperf 的 `-lib` 参数提供未剥离符号；不存在时仍可采样，但原生符号信息可能不足。
运行前应构建当前 APK，避免使用旧 APK 与新源码分析同一次结果。

## 环境准备

- 完整 JDK 21、项目所需 Android SDK、NDK 28.2.13676358 和原生依赖。原生依赖准备见
  [本地构建说明](../third_party/vsomeip-android/README.md)。
- 可用的 Python 3.10+。Windows 的 `python` 可能只是应用商店别名，可将下面的变量改为真实解释器绝对路径。
- 已授权 ADB 的 Android 10 / API 29+ 设备。当前项目 APK 为 x86_64，直接适用于匹配 ABI 的 AAOS 模拟器；
  ARM 真机需要先完成应用及原生依赖的 ABI 配置和构建。
- NDK 下的 `simpleperf/app_profiler.py`、`report_html.py` 以及配套主机库。项目脚本只使用 Python 标准库及这些 NDK 工具。

PowerShell 示例从仓库根目录执行，按本机环境修改路径：

```powershell
$profileSdk = 'D:/Android/SDK'
$profileNdk = "$profileSdk/ndk/28.2.13676358"
$profilePython = 'python'
$profileSerial = 'emulator-5554'
& $profilePython --version
& "$profileSdk/platform-tools/adb.exe" devices -l
```

确认 `JAVA_HOME` 指向 JDK 21，并将其 `bin` 放入 `PATH`，然后构建：

```powershell
.\gradlew.bat :app:assembleProfiling --offline
.\gradlew.bat :app:lintProfiling --offline
```

首次下载依赖时去掉 `--offline`。APK 位于 `app/build/outputs/apk/profiling/app-profiling.apk`。
构建会执行原有 Detekt 和相应构建检查，不需要跳过任何规则。

## 自动采样首页交互

```powershell
& $profilePython tools/profiling/record-dashboard.py `
    --sdk $profileSdk --ndk $profileNdk --serial $profileSerial
```

脚本安装或更新 profiling APK，重启该独立应用，从 UI 语义中的控件名称定位操作区域。
它预热 15 秒，再录制 60 秒，循环执行地图放大、缩小、拖动、复位，以及媒体演示切歌、播放切换和进度拖动。
采样期间保持设备停留在首页，避免手动操作和并行构建等高负载任务。

实际采样选项为 `-e task-clock:u -f 1000 -g --duration 60`，记录目标应用进程所有线程的用户态 CPU 调用栈。
工具强制使用非 root 模式，不强制 AOT 编译，不启动车辆网络服务；普通应用的数据不会被清除。

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `--sdk` / `--ndk` / `--serial` | 必填 | SDK、NDK 路径和目标 ADB 设备 |
| `--duration` | 60 | 正式采样秒数，至少 10 秒 |
| `--warmup` | 15 | 预热秒数，可为 0 |
| `--frequency` | 1000 | 采样频率，范围 1–1000 Hz |
| `--output` | 自动生成 | 新建输出目录，不能指向已存在目录，防止覆盖旧结果 |

默认输出目录是 `app/build/reports/simpleperf/<时间>-dashboard/`，完成时会打印报告路径。
每次重新采样都创建独立目录，保留 APK 哈希、源码哈希和操作日志，便于确认比较对象。

## 生成和阅读报告

把 `$profileRun` 改为本次脚本打印的目录：

```powershell
$profileRun = 'app/build/reports/simpleperf/20261015-094615-dashboard'
& $profilePython tools/profiling/summarize.py --ndk $profileNdk $profileRun
```

| 文件 | 用途 |
| --- | --- |
| `perf.data`、`binary_cache/` | 原始数据和解析符号，后续重新分析需保留 |
| `report.html` | 官方 simpleperf 函数表和调用图；页面依赖外部 CDN |
| `summary.html`、`summary.json` | 离线摘要，含线程、函数、库分布及丢样计数 |
| `functions.csv`、`app-functions.csv` | 全部函数及 `com.example` 应用函数排名 |
| `metadata.json` | 设备、APK/源码哈希、Git 状态、实际命令及操作次数 |
| `actions.jsonl` | 预热和正式录制的动作、坐标、阶段内相对时间 |
| `record.log`、`report.log` | 采样和报告生成诊断 |
| `ui-before.xml`、`ui-after.xml`、`screen-after.png` | 界面证据；AAOS 截图优先选择 HWC display 0 |
| `worktree.diff` | 相关已跟踪文件相对 HEAD 的差异；不是完整源码备份，未跟踪源码仅记录哈希 |

输出目录被 Git 忽略；提交工具时不提交 APK、符号缓存或采样数据。人工撰写的 `analysis.html` 不是脚本自动生成文件。

**Self** 表示函数自身消耗，**Inclusive** 包含其子调用。摘要按每个样本的 event period 加权，
相同函数名在单条栈中只计一次 Inclusive；各行有包含关系，不能直接相加。
分母是整个目标进程采到的用户态 CPU 时间，不是墙钟时间、整机利用率或 GPU 时间。
`task-clock:u` 的 period 可换算为时间；不能将这一换算直接套用到 CPU cycles 等其他事件。

先看线程分布，再看应用函数的 Inclusive 和全局函数的 Self，最后沿调用图定位实际子调用。
同时检查 `summary.json` 的 `record_stats`、未知符号比例和样本数量。零丢样并不意味着统计结果没有采样误差。

## 首次采样记录

2026-10-15 在 Android 15 AAOS x86_64 模拟器上，预热 15 秒后录制 60 秒首页交互，执行 117 次操作，
得到 3,038 个样本、约 3.038 秒采样用户态 CPU 时间；丢样和栈截断计数均为 0。
主线程占 41.44%，RenderThread 占 41.41%；应用侧 `DarkMapCanvas` 绘制回调的 Inclusive 为 5.33%。

该记录来自当时包含未提交原生模块拆分的工作区，按输出目录中的 APK 和源码哈希识别，不能当作本提交的严格性能基线。
它提示可优先检查地图路径重复创建，但不证明已有卡顿，也不能将全部 GC 归因于地图。
本场景使用离线示意地图及无音源媒体演示，不能评价真实导航、媒体解码或持续 SOME/IP 接收。
性能回归比较应固定设备、构建、操作和预热条件，进行多次测量。

## 故障处理与 CI

| 现象 | 处理 |
| --- | --- |
| 找不到 Python 或启动应用商店 | 将 `$profilePython` 设置为实际 Python 3.10+ 解释器路径 |
| 设备 offline / unauthorized | 用 `adb devices -l` 检查连接并完成设备授权，再确认 `--serial` |
| API 低于 29 | 改用支持 shell profileable 的设备；脚本会在安装前拒绝此设备 |
| 找不到 APK 或原生库 | 先准备匹配 ABI 的原生依赖并运行 `assembleProfiling` |
| 另一 simpleperf 正在运行 | 等待其完成；脚本不会终止其他采样会话 |
| 找不到首页控件或布局不支持 | 检查 `ui-before.xml`，确认当前页面和布局，再调整场景脚本 |
| 官方 HTML 空白或图表不加载 | 检查 CDN 访问，或直接打开离线 `summary.html` |
| Lint 提示 `shell` 仅 API 29+ 生效 | 应用 minSdk 仍为 24；该属性在旧系统被忽略，采样脚本要求 API 29+ |
| 完整 Lint 长时间不推进 | 保留日志，取消本次命令后用 `--no-daemon --max-workers=2` 重试，勿关闭规则或停止其他构建 |

本次不修改 CI。若后续加入 CI，可先增加 `:app:assembleProfiling :app:lintProfiling` 验证构建；
自动采样应在具备固定设备和可重复负载的独立任务中运行，上传报告供比较，不能直接作为普通 runner 上的固定 CPU 阈值门禁。

## 官方参考

- [Android profileable 配置](https://developer.android.com/guide/topics/manifest/profileable-element)
- [AOSP simpleperf 应用采样指南](https://android.googlesource.com/platform/system/extras/+/refs/heads/main/simpleperf/doc/android_application_profiling.md)

## 离线交互火焰图

`tools/profiling/summarize.py`同时生成 `flamegraph.html`，单文件内含数据和交互，无需 CDN 或安装插件。
GitLab simpleperf Job 的 **CPU flame graph (offline HTML)** 报告链接提供下载；也可从 artifacts 的
`build/reports/simpleperf/raw/flamegraph.html` 获取。浏览器可能下载附件，下载后直接打开即可。

默认从上到下显示进程、线程、调用者和叶函数，便于深调用栈阅读；取消 Root at top 可切换为传统向上火焰图。
点击帧放大，Back 返回、Reset zoom 恢复，Find function 搜索高亮并显示去重的进程 CPU 占比；
悬停或键盘聚焦显示完整函数名、inclusive/self CPU 时间及占比。宽度按 task-clock 事件 period 加权，
递归栈不会去重，水平顺序不是时间轴。未知符号仍标为 unknown，不会伪造解析结果。
火焰图用于定位 CPU 热点，性能阻断仍以版本化基线比较为准。
