# GitLab 代码审查与性能报告

流水线执行原生构建 → Detekt/Android Lint 审查 → 两个模块的单元测试、Debug/Profiling
APK、覆盖率和 JMH。设备采样使用独立的 `simpleperf` Job，避免容器编译负载干扰测量。
Docker Runner 在独立 `carlauncher-debug-signing` 卷中保留专用 Debug 签名，避免每次
容器重建生成不同证书而导致 Profiling APK 无法覆盖安装。该密钥不是发布签名，
不会进入源码或 Job artifacts；执行 `register-runner.ps1` 会幂等准备此卷。
合并请求会使用 MR 流水线；同一提交已有 MR 时不再重复运行 push 流水线。

## 在 GitLab 中查看

- **流水线 → Tests**：JUnit 单元测试、静态检查状态、JMH 和 simpleperf 检查结果。
  性能 testcase 的输出含实际测量值；回归检查输出基线、当前值、变化百分比、阈值和来源。
- **Merge Request → Code Quality**：Detekt、Lint 的问题、文件及行号。分支对比需要目标
  分支也有报告；首次启用后先让主分支完成一轮。此处是静态分析，不是 AI 语义审查。
  沿用项目门禁：Detekt finding 或 Lint error 阻断构建；Lint warning 仍保留在
  Code Quality 报告中，不会因为 Job 成功就被清除。
- **Job → Artifacts / 报告链接**：下载 HTML、JSON、JUnit、Lint/Detekt 原始报告、JMH
  原始结果，以及 simpleperf 原始采样、函数/线程分布、设备/APK/提交证据。
- JaCoCo XML 用于 MR 的逐行覆盖率显示；完整 HTML 继续作为产物保留。

当前为 GitLab CE。自定义 Metrics/Browser Performance widget 需要付费版本，而且
浏览器性能格式并不适用于 Android CPU 采样。本项目使用 CE 支持的 JUnit、Code Quality
和 Job annotations。未配置 GitLab Pages 时 HTML 可能作为附件下载，下载后可离线打开
`index.html` 或 `summary.html`；不承诺 GitLab 能直接内嵌渲染任意 HTML。

## JVM 性能

`:app:jvmPerformanceReport` 使用 JMH 1.37，测量生产代码的二进制编解码、JSON 车辆快照
解码和状态校验。每个 benchmark 有 2 个独立 JVM fork、3 轮预热和 5 轮测量，每轮 1 秒；
返回值由 JMH 消费，避免无效代码消除。报告含 ns/op、分配量、JDK、fork 和采样配置。

它测量宿主机 JVM 上的纯计算路径，不能代表 Android ART、UI 帧率、JNI 或网络延迟。
共享 Docker CPU 的变化会影响结果。基线为成功流水线 #19，提交 `f70849b8db506de6385673368eedc78a52e63917`，JMH Job #44、simpleperf Job #45。
测量值版本化保存在 `tools/gitlab/performance-baseline.json`，不依赖会过期的 artifacts。
JMH 耗时允许增加 20%；分配量允许增加 max(基线的 10%, 1 B/op)，避免近零分配量的噪声误判。
超过上限失败，等于上限通过。benchmark 集合、JDK/JMH、fork、预热、测量、线程数和单位必须一致。

## Android simpleperf

GitLab 与 GitHub 共用 `run-simpleperf.ps1` 和 `record-dashboard.py --baseline`。
预检先读取真实设备 fingerprint、序列号、屏幕尺寸，并比较场景名称、预热/采样时间、
采样事件和频率；不匹配时在安装 APK 前退出。安装并打开页面后，进一步比较实际语义树
推导出的动作坐标与基准、重放计划来源提交；不匹配时不会开始预热或启动 simpleperf。
日志逐项输出 `expected` / `actual`，`raw/preflight.json`、JUnit 和 HTML 保留差异，
报告明确标记未采样，不能用旧的 perf.data 冒充本次成功。采样后的原有检查继续保留。
设备离线或 ADB 查询失败也按不匹配处理。Android 15 不会被自动降级、伪造 fingerprint 或跳过。
两个 CI 共用 Windows 命名互斥量 `Global\CarLauncherSimpleperf`，设备被占用时立即失败。

当前基准要求 Android 13 `TEA1.250515.001/13505934`、1408×792。
使用独立 `CarLauncher_Perf_API33` AVD 占用 `emulator-5554`，避免将日常 Android 15 AVD
用于性能比较；预检只校验，不会自动停止、切换或擦除正在使用的模拟器。

Docker Job 构建本次提交的 Profiling APK，并提供未剥离原生符号。Windows shell Runner
使用本机 SDK/NDK 和指定设备执行已有的 `record-dashboard.py`、`summarize.py`：预热 15 秒，
采样 60 秒，同时执行地图和媒体演示操作。采样 Job 持有独占 resource_group。

每次提交和 MR 自动运行 `simpleperf`，失败阻断流水线，不再需要 `RUN_SIMPLEPERF`。
默认设备 `emulator-5554`；离线时失败，Runner 离线则等待，不会跳过。
设备 serial、系统 fingerprint、屏幕、动作、采样 60 秒和预热 15 秒均需匹配基线。
交互次数须在 119 次的 ±5% 内，避免少执行工作而虚假改善。
场景 user CPU 基线 9.411 秒，允许 +20%，上限 11.2932 秒。
无设备、缺少数据、没有执行交互或提交证据不匹配都会使该 Job 和 JUnit 检查失败。
此预算检查固定场景的 CPU 回归，不代表帧率。共享宿主机和单次基线仍有噪声。
Tests 中每项 regression testcase 输出基线值、当前值、百分比和阈值，超限为 failure；
HTML/JSON 和原始采样即使失败也上传。基线不随提交自动滚动；修改设备、配置或预算时
需审核基线文件并记录新成功流水线及原因，不能自动放宽预算掩盖失败。

### 专用 Windows Runner

在登录用户会话中运行（需 Git、Python、pwsh、JDK 21、Android SDK/NDK 和已授权设备）：

```powershell
./tools/gitlab/setup-device-runner.ps1
```

脚本下载并核对官方 Runner 19.4.1 校验和，创建仅属于此私有项目、标签为
`carlauncher-device` 的 shell Runner，并在当前用户会话后台启动。
凭据、二进制、PID 和日志都位于 Git 忽略的 `.gitlab-local/device-runner`。
重启 Windows 后需重新执行启动脚本；它不会安装系统服务或更改全局 ADB 监听。
shell Runner 会执行仓库脚本，只应让可信提交使用该设备 Runner。

设备专属场景、统计含义和限制见 [simpleperf 指南](simpleperf-profiling.md)。

## 离线交互火焰图

`tools/profiling/summarize.py`同时生成 `flamegraph.html`，单文件内含数据和交互，无需 CDN 或安装插件。
GitLab simpleperf Job 的 **CPU flame graph (offline HTML)** 报告链接提供下载；也可从 artifacts 的
`build/reports/simpleperf/raw/flamegraph.html` 获取。浏览器可能下载附件，下载后直接打开即可。

默认从上到下显示进程、线程、调用者和叶函数，便于深调用栈阅读；取消 Root at top 可切换为传统向上火焰图。
点击帧放大，Back 返回、Reset zoom 恢复，Find function 搜索高亮并显示去重的进程 CPU 占比；
悬停或键盘聚焦显示完整函数名、inclusive/self CPU 时间及占比。宽度按 task-clock 事件 period 加权，
递归栈不会去重，水平顺序不是时间轴。未知符号仍标为 unknown，不会伪造解析结果。
火焰图用于定位 CPU 热点，性能阻断仍以版本化基线比较为准。

## 固定基线动作重放

CI 使用 `--replay-plan tools/profiling/dashboard-replay.json`，计划直接取自流水线 #19、Job #45 的真实 `actions.jsonl`。
Windows 仅推送一次脚本并启动设备端 shell；动作由 Android `/proc/uptime` 单调时钟按原始时间点调度，
不再在 Windows 上逐次启动 ADB、按时间循环决定动作数。固定重放 31 次预热和 119 次采样交互。
所有输入成功返回后才记完成；保留计划/实际开始/完成时间、设备脚本及日志。
预热完成后才开始录制，采样动作全部完成且不得超出 59 秒工作窗口（60 秒采样预留启动余量）；
计数、顺序、输入、计划哈希或日志不一致均失败。原 ±5% 计数门禁仍保留，新增精确动作校验更严格。
原 #19 CPU/JMH 基线数值及全部回归阈值保持不变，不能通过重新生成基线或归一化计数掩盖失败。

## GitHub Actions 范围

GitHub 的 Android Build 共用 `run-review.sh` 与报告转换器，执行 Detekt、Checkstyle、两模块
Lint、Debug/Profiling APK、AAR、单元测试、分层检查、原生来源与 APK 校验及覆盖率报告。
Actions Summary 展示审查和测试汇总，详细报告通过 Artifacts 下载。
按当前配置，GitHub 不运行 JMH/simpleperf；性能基线、固定重放、火焰图和阻断只在 GitLab 执行。
