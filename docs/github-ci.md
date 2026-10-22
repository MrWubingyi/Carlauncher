# GitHub Actions 与 GitLab CI 对齐

两边共用静态检查、性能报告生成器、预检、重放计划和 `performance-baseline.json`。
没有增加或调整基准及门槛。GitHub 的构建和 Roborazzi 仍使用托管 Runner；
JMH 和 simpleperf 必须使用产生原基准的本地机器，不能将随机托管 VM 当成同一性能环境。

| 能力 | GitHub Actions |
| --- | --- |
| 源码原生构建及来源/库哈希验证 | `build`，保留 APK/AAR 与原生产物 |
| Detekt、Checkstyle、Lint、JVM 测试、JaCoCo 报告 | `build`，共用 `run-review.sh`；含预检正反向测试 |
| 六场景 Roborazzi 零容差 | `ui-regression`，保留中文报告和前后图 |
| JMH 时间/分配量门禁 | `performance-build`，同基准 Docker 主机和镜像 |
| 设备/场景预检、simpleperf CPU 门禁 | `simpleperf`，同基准 Windows 主机与 Android 13 AVD |
| 性能结果、失败字段、火焰图 | Job Summary、HTML、JSON、JUnit 及原始产物；失败也上传，保留 14 天 |

## 必需的 Runner 配置

先在原基准宿主注册 GitHub Runner，不是新建不同配置的 VM。工作流不会自动注册 Runner。

- Linux Runner 标签：`self-hosted, Linux, X64, carlauncher-docker`。安装 Docker，准备仓库的
  `carlauncher-android-ci:local` 镜像和已有 `carlauncher-debug-signing` 卷；必须与 GitLab
  原基准的硬件、容器资源限制及 JDK 配置保持一致。脚本固定为 2 核、6 GiB，与现有
  GitLab Docker Runner 相同。不要并行运行两个 CI 的 JMH 测量。
- Windows Runner 标签：`self-hosted, Windows, X64, carlauncher-device`。配置 JDK 21、Python、
  pwsh、`ANDROID_HOME` 与固定 NDK；让基准 Android 13 AVD 在 `emulator-5554` 上运行。
- 仓库变量 `BASELINE_GITHUB_JMH_RUNNER_NAME` 和 `BASELINE_GITHUB_SIMPLEPERF_RUNNER_NAME`
  分别填入上述两个已核实为原基准机器的 GitHub Runner 名称。预检会与 GitHub 提供的
  `RUNNER_NAME` 严格比较；缺失或不匹配直接失败。此映射由管理员确认机器身份，不代表
  任意机器只要设置同一个名称就具备性能可比性。不伪造 GitLab 的 `CI_RUNNER_ID`。

`performance-build` 下载同次 workflow 的源码原生产物，在原 CI 镜像中生成 Profiling APK
并执行 JMH。复用既有签名卷，避免在 Android 13 AVD 上覆盖安装时签名不兼容；没有密钥就
失败，不卸载用户应用或清除数据。APK 和未剥离符号以保留目录结构的 artifact 交给设备任务。

## 触发和证据边界

自托管性能任务仅在默认分支 push 或对默认分支的手动运行执行；PR 继续运行托管构建和截图，
不会执行自托管性能任务。这是与私有 GitLab MR 流程的明确差异，遵循
[GitHub 自托管 Runner 安全说明](https://docs.github.com/en/actions/reference/security/secure-use)。
没有配置自托管 Runner 时，性能任务会等待可用 Runner，不能声称已经通过。

打开一次 Actions 运行的 Summary，查看最后完成的 **中文验收摘要**：

- 首行给出四项验收通过、失败或验收不完整；表格集中展示构建、六场景截图、JMH 和设备性能。
- 构建列出两个模块的 JVM 测试数量、失败和跳过；截图列出变化/缺失场景；设备性能列出 CPU 实测、上限及相对变化。
- 每行的报告下载链接来自本次上传步骤的 `artifact-url`，构建额外提供 APK/AAR。上传失败时显示不可用，不伪造链接。
- 检查明细折叠展示；失败、取消、跳过、报告缺失或提交不一致均不会显示为完整验收通过。

下载对应 artifacts 并解压后打开 `index.html`、截图 `summary-zh.html` 或
设备报告 `raw/flamegraph.html`。下载需要登录并有仓库访问权限，产物保留 14 天。
最终汇总任务只读取各任务输出，不重新采样或修改门禁；PR 跳过性能任务时显示验收不完整。
工作流整体取消或 checkout 失败可能无法生成摘要，应以 Actions 的原始任务状态为准。
`python tools/github/test-summarize.py` 验证摘要的成功、失败和证据缺失路径。
[Actions artifacts](https://github.com/actions/download-artifact) 在同一 workflow 内传递构建产物。
CI 配置和本地脚本测试通过不等于 GitHub 自托管环境已注册或实际运行成功。
