# GitLab 代码审查与性能报告

流水线执行原生构建 → Detekt/Android Lint 审查 → 两个模块的单元测试、Debug/Profiling
APK、覆盖率和 JMH。设备采样使用独立的 `simpleperf` Job，避免容器编译负载干扰测量。
合并请求会使用 MR 流水线；同一提交已有 MR 时不再重复运行 push 流水线。

## 在 GitLab 中查看

- **流水线 → Tests**：JUnit 单元测试、静态检查状态、JMH 和 simpleperf 检查结果。
  性能 testcase 的输出含实际测量值；没有配置回归基线时，预算检查明确显示 skipped。
- **Merge Request → Code Quality**：Detekt、Lint 的问题、文件及行号。分支对比需要目标
  分支也有报告；首次启用后先让主分支完成一轮。此处是静态分析，不是 AI 语义审查。
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
共享 Docker CPU 的变化会影响结果。当前检查真实测量是否完整有效；没有批准的同机器
基线和退化预算，因此不会将采集成功声称为“性能无退化”。

## Android simpleperf

Docker Job 构建本次提交的 Profiling APK，并提供未剥离原生符号。Windows shell Runner
使用本机 SDK/NDK 和指定设备执行已有的 `record-dashboard.py`、`summarize.py`：预热 15 秒，
采样 60 秒，同时执行地图和媒体演示操作。采样 Job 持有独占 resource_group。

普通提交后在 GitLab 手动启动 `simpleperf`。若希望流水线自动采样并将采样失败作为
阻断项，在 Run pipeline 时设置 `RUN_SIMPLEPERF=1`。默认设备为 `emulator-5554`，
可用变量 `PERF_SERIAL` 改为另一已授权、匹配 x86_64 APK 的设备。
无设备、缺少数据、没有执行交互或提交证据不匹配都会使该 Job 和 JUnit 检查失败。
没有基线时只报告 CPU 样本及其有效性，不给出无依据的耗时阈值或帧率结论。

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
