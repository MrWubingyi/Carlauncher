# UI 回归 Runner：另一台 Windows 机器配置与排障

适用于本仓库 `ui-regression` / Roborazzi 六场景截图测试。本文是迁移操作指南，
尚未在你的另一台机器执行；没有该机器的作业日志时，不能断言失败根因。
不更新截图基准，不放宽像素容差或性能阈值。

## 1. 先区分任务和平台

| 情况 | 当前配置 / 处理方式 |
| --- | --- |
| GitHub Actions `ui-regression` | 当前 `runs-on: windows-2022`，由 GitHub 托管；另一台本地机器无需注册 Runner |
| GitLab `ui-regression` | 当前 `tags: [carlauncher-device]`，Windows Shell 执行 |
| 本机手动验证 | Windows 上运行 `python tools/visual-regression/run_ci.py` |
| simpleperf | 需要原性能基准主机和 Android 13 设备；不能迁到任意新机器 |

Roborazzi 使用 JVM/Robolectric 绘制固定场景，**不需要启动模拟器、不需要 ADB、
不需要连接车辆，也不需要 Docker socket**。当前基准在 Windows 生成；Linux、WSL
和 Linux 容器会被 `run_ci.py` 明确拒绝，不应绕过该检查。

先查看失败停在哪：Pending/Queued 是调度问题；Prepare/Checkout 是 Runner、权限或网络；
Gradle 执行失败是工具链或测试问题；有差异图则需要分析实际 UI 差异。

## 2. 准备 Windows x64 工具链

建议使用独立短路径工作目录（如 `C:\ci\work`）和专用执行账号。下面的目录都是示例，
替换成新机器实际路径，仅保存在本机；不要提交到仓库。

安装 Git、PowerShell 7、完整 JDK 21、Python 3.12（含 pip），以及 Android SDK Command-line Tools。
版本对齐当前 GitHub 作业：Pillow 12.3.0、Platform 36.1、Build Tools 36.0.0、
NDK 28.2.13676358、CMake 3.22.1。不要把 NDK 自带 Python 当作主 Python。

在**将来运行 Runner 的账号**下打开 PowerShell 7，配置当前进程环境：

```powershell
$env:JAVA_HOME = 'C:\ci\jdk-21'
$env:ANDROID_HOME = 'C:\ci\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PYTHONUTF8 = '1'
$env:Path = "C:\ci\python312;$env:JAVA_HOME\bin;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:Path"

Get-Command git, pwsh, python, java, javac | Select-Object Name, Source
python -c "import sys; print(sys.executable); print(sys.version)"
python -m pip --version
java -version
javac -version
python -m pip install Pillow==12.3.0
if ($LASTEXITCODE -ne 0) { throw 'Pillow 安装失败' }

& "$env:ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" `
  'platforms;android-36.1' 'build-tools;36.0.0' `
  'ndk;28.2.13676358' 'cmake;3.22.1'
if ($LASTEXITCODE -ne 0) { throw 'SDK 安装失败；检查许可证、网络和目录权限' }
Test-Path "$env:ANDROID_HOME/platforms/android-36.1/optional/android.car.jar"
```

SDK 许可证由有权限的操作者按组织要求接受。最后的 `Test-Path` 必须为 True。
这些 `$env:` 设置只影响当前进程及其子进程；在此窗口启动 Runner 才会继承。
作为 Windows 服务运行时，要为**服务账号**配置等效环境和目录权限并重启服务，
不能以“个人终端可运行”证明服务也可运行。不要把含代理密码的完整环境变量输出到日志。

网络需能访问 Git 托管平台、Gradle 分发、Google Maven/SDK、Maven Central、
Python 包源及 Robolectric Android 依赖。另一台机器上的 `localhost:8929` 指向它自己，
不是原 GitLab 主机；应使用管理员提供的可达 GitLab 地址及正确证书。
原机器的 `127.0.0.1` 代理地址同样不能直接照搬，不关闭 TLS 校验解决连接问题。

## 3. 先跑本机冒烟，再注册 Runner

在目标分支的仓库根目录运行，先确认 SDK 本机配置未指向原机器：

```powershell
git status --short
python -c "import PIL; print(PIL.__version__)"
.\gradlew.bat --version
if ($LASTEXITCODE -ne 0) { throw 'Gradle/JDK 检查失败' }
python tools/visual-regression/test_ci.py
if ($LASTEXITCODE -ne 0) { throw '报告脚本测试失败' }
python tools/visual-regression/run_ci.py
if ($LASTEXITCODE -ne 0) { throw '截图回归失败；保留本次报告并按下表排查' }
```

优先使用 `run_ci.py`，不手抄测试包名（应用已迁移到 `com.zqautomotive.launcher`）。
它将当前截图写到独立输出目录，再校验已提交基准；不要裸跑
`:app:recordRoborazziDebug`，其默认截图路径可能覆盖 `app/src/test/screenshots/home`。
成功必须同时满足：脚本退出码 0、`run.json` 的 capture/verify 均为 0、
`pixel-results.json` 六场景齐全且 changed/incomplete 均为 0。
`local-worktree` 是手动运行时正常的提交标记，CI 中才由平台注入提交 SHA。

## 4. GitLab：新建专用 UI Runner

1. 在项目 Settings → CI/CD → Runners 创建项目 Runner，设置独立标签
   `carlauncher-ui-windows`；关闭无标签任务接收，按目标分支设置 Protected 策略。
2. 从 GitLab 官方渠道安装 Windows Runner，在 UI 提供的注册流程中使用该实例地址
   和新的 Runner authentication token；选择 `shell` executor，Shell 使用 `pwsh`。
   不复制旧机器的 token、config.toml 或服务凭据到文档/仓库。
3. 先在配置好上述环境的终端运行 `gitlab-runner.exe run`，确认 UI 显示在线。
   手动冒烟和 CI 验收成功后，再按组织方式安装 Windows 服务并复验账号环境。
4. **在准备采用新机器时**，将 `.gitlab-ci.yml` 中仅 `ui-regression` 的标签改为：

```yaml
ui-regression:
  # 保留原 job 的 stage、script、artifacts、timeout 等其他字段
  tags: [carlauncher-ui-windows]
```

这是待应用的配置片段，本文不会自动改工作流。原来的 `resource_group` 可以保留，
它是项目级互斥，可能让 UI 任务等待设备任务。不要改 simpleperf 的标签，
也不要给新 UI Runner 加 `carlauncher-device`，避免接走原性能设备任务。
Runner 标签必须包含 job 要求的全部标签；在线但仍 Pending 时检查项目绑定、Paused、
Protected 分支匹配、并发占用和资源组等待。

## 5. GitHub：优先保留托管 UI Runner

现有 `windows-2022` 作业已自动安装 JDK 21、Python 3.12、Pillow 与 SDK 组件。
如果该作业报错，应看失败步骤，而不是在另一台电脑安装本地 Runner。

确有自托管需求时，在仓库 Settings → Actions → Runners → New self-hosted runner
选择 Windows x64，按官方页面的一次性注册命令配置，添加独立标签
`carlauncher-ui-windows`；先用配置好环境的终端执行 `run.cmd` 验证在线。
完整 SDK Command-line Tools 和 `ANDROID_HOME` 需要预装，现有步骤只安装 SDK 组件。

不要直接把现有 UI 作业改为自托管却保留所有 PR 触发：仓库工作流支持 pull_request，
不可信 PR 不应运行在持久化本机 Runner 上。建议另建只允许受信任默认分支手动触发的
UI 验证工作流，保留 PR 的托管任务。若最终修改现有 job，应同时审核事件条件及下游
`needs` 的影响；只改标签不足以完成安全迁移。

自托管选择标签示例（不是完整工作流）：

```yaml
runs-on: [self-hosted, Windows, X64, carlauncher-ui-windows]
```

不要复用 `carlauncher-device`，不修改 `BASELINE_GITHUB_SIMPLEPERF_RUNNER_NAME` 或
`BASELINE_GITHUB_JMH_RUNNER_NAME` 来让新 UI 主机冒充原性能基准主机。

## 6. 常见故障定位

| 现象 | 检查与处理 |
| --- | --- |
| Pending / Queued | 先核对在线状态、标签全部匹配、仓库/项目授权、Protected 条件及资源组/并发占用 |
| `Windows baseline requires a Windows runner` | 调度到了 Linux/WSL；改用真正 Windows 执行器，不删除平台检查 |
| `pwsh` 不存在 | 安装 PowerShell 7，让服务账号 PATH 可见，重启 Runner |
| `No module named pip` / `PIL` | 查看 `sys.executable`；常见为 NDK Python 抢占 PATH；使用完整 Python 3.12 和同一解释器的 `python -m pip` |
| `UnicodeEncodeError` | 为 Runner/job 设置 `PYTHONUTF8=1`，重启后再试；GitHub 工作流已设置 |
| JAVA_HOME/JDK 错误 | `gradlew.bat --version` 检查实际 JVM；必须完整 JDK 21，不只检查 Android Studio 设置 |
| SDK location / android.car.jar 缺失 | 检查服务环境和 local.properties；安装准确 Platform 36.1，检查 optional/android.car.jar |
| SDK license / Access denied | 由操作者处理许可证；服务账号需能读写 SDK、工作目录、Gradle/Python 缓存 |
| 下载超时、401、SSL 错误 | 检查该账号的 DNS、代理、证书和仓库访问；不要复制原机器 localhost 地址或关闭证书校验 |
| Filename too long | 使用较短工作根目录；必要时为 Runner 账号配置 Git core.longpaths；不要靠更改测试包名解决 |
| Gradle daemon disappeared / OOM | 检查内存和并发，先单任务运行；保留日志，不降低测试覆盖或跳过门禁 |
| `No tests found` | 使用当前分支的 run_ci.py；检查源码/脚本是否混用旧包名，不改为忽略无测试 |
| 脚本失败但生成 HTML | 报告生成不代表测试通过；查看 run.json、capture.log、verify.log、report.log |
| 非零像素差异 | 打开中文报告逐场景对比；核对 OS、JDK、字体/渲染环境与依赖，再判断是否真实 UI 变化；不更新基准掩盖失败 |
| 服务启动成功但作业失败 | 用服务账号重复第 2、3 节；重点检查 PATH、SDK 权限、用户缓存及代理 |

## 7. 报告收集与远程排障所需信息

每次运行保存到 `build/ui-regression/ci/<本次 UUID>/`：

- `summary-zh.html`：中文结论及前后图；下载整个 ZIP 并解压，不能只取 HTML。
- `run.json`：提交、平台、Python 版本与 capture/verify 退出码。
- `capture.log`、`verify.log`、`report.log`：分别定位采集、校验、报告失败。
- `pixel-results.json`、`images/`、JUnit 与 Roborazzi 报告：场景完整性和差异证据。

GitLab 已配置失败也上传产物；GitHub 上传步骤使用 `always()`；默认保留 14 天。
若另建工作流，必须保留失败上传行为，不能仅在成功后上传。

需要进一步排障时，提供：平台（GitLab/GitHub/手动）、Windows 版本、Runner 的
executor/shell/标签、是否服务运行、首个失败步骤和上述三个日志。环境信息只提供
工具路径与版本；请去掉 token、私钥、带密码的代理 URL。无需提供整份含密钥的 Runner 配置。

## 8. 官方说明与仓库依据

- [GitLab Runner 注册](https://docs.gitlab.com/runner/register/)
- [GitLab Shell / PowerShell 支持](https://docs.gitlab.com/runner/shells/)
- [GitHub 自托管 Runner 标签](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/apply-labels)
- [GitHub 自托管 Runner 访问边界](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/manage-access)
- 仓库入口：[run_ci.py](../tools/visual-regression/run_ci.py)、[脚本测试](../tools/visual-regression/test_ci.py)、[GitHub 配置](../.github/workflows/android-build.yml)、[GitLab 配置](../.gitlab-ci.yml)。
- [双平台 CI 总览](github-ci.md)。本文仅提供配置指导，不表示另一台机器已验收通过。
