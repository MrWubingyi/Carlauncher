# CarLauncher 本地开发

Android 车载桌面，包含应用 `:app` 和可独立构建 AAR 的通用 SOME/IP 客户端
`:someip-android`。应用与测试主要使用 Kotlin，JMH 基准使用 Java；AGP 内置 Kotlin，
Room 使用 KSP。当前应用产物仅支持 x86_64，Debug APK 使用开发签名。

## 环境准备

在仓库根目录使用 Windows PowerShell 5.1+ 或 PowerShell 7：

- Git、Python 3.9+。
- 完整 JDK 21，必须包含 `java`、`javac`、`jlink`。
- Android SDK：Platform 36.1、Build Tools 36.0.0、NDK 28.2.13676358、CMake 3.22.1。
- 已初始化的 `third_party/vsomeip` 子模块，以及匹配当前构建输入的原生库产物。

```powershell
git submodule update --init --recursive
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:ANDROID_HOME = 'C:\path\to\Android\Sdk'
```

SDK 优先读取 `local.properties` 的 `sdk.dir`，其次读取 `ANDROID_HOME` / `ANDROID_SDK_ROOT`。
缺少 SDK 组件时，入口使用已安装的 Android CLI 下载固定版本。
JDK 可通过 `-JavaHome` 指定；也会读取本机配置、`JAVA_HOME`、Android Studio JBR 和 `~/.jdks`。
本地构建不等于完全离线：首次 Gradle 依赖解析和 SDK 安装仍可能下载文件。

## 日常构建

```powershell
# 默认：静态检查、分层检查、原生库校验、Debug APK/AAR、两模块 JVM 测试和 Lint
.\build-local.ps1
# 快速构建：保留静态检查、分层及产物校验，跳过 JVM 测试和 Lint
.\build-local.ps1 -Quick
# 默认检查完成后，额外生成 app JaCoCo 报告
.\build-local.ps1 -Coverage
```

若 PowerShell 执行策略限制本地脚本，仅对本次进程使用：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-local.ps1
```

`-Test` 为兼容旧命令保留，默认已运行测试；不能与 `-Quick` 同用。
Gradle 可以复用有效缓存，`UP-TO-DATE` 表示本次没有重新执行任务。
构建失败立即返回非零状态，不输出成功结论；不会自动清理现有构建产物。

## 原生库准备与远程模式

Windows Gradle 编译 JNI，但不会从源码构建 vSomeIP。入口要求
`build/native/artifact/` 中的产物通过 SHA256、来源版本、构建输入、ELF 和导出符号校验，
并核对 APK 中的 `libvsomeip3.so` 与已验证产物一致。
有效本地缓存不需要 Ubuntu；缓存缺失、损坏或与当前提交不匹配时，默认失败并提示下一步。

只有显式传入 `-AllowRemote`，入口才允许 SSH/SCP 到配置的 Ubuntu：

```powershell
# 首次远程准备并保存本机设置；主机替换为自己有权使用的 Ubuntu
.\build-local.ps1 -AllowRemote -UbuntuHost user@ubuntu -SaveConfiguration
# 允许在缓存失效时自动远程准备
.\build-local.ps1 -AllowRemote
# 强制重建原生库
.\build-local.ps1 -AllowRemote -RebuildNative
```

远程模式传输主仓库和 vSomeIP 的 Git bundle（包含可达历史）及当前原生脚本，
在 Ubuntu 独立目录构建，校验下载归档后替换本地缓存。应用未提交改动仍在 Windows 构建。
vSomeIP 构建输入必须先提交，检出提交必须匹配主仓库 HEAD 的 gitlink；入口忽略子模块
`documentation/` 的改动，但不得将此例外用于实际编译输入。

Ubuntu 需要密钥认证、Git、curl、tar、bzip2、Python 3.9+、g++，以及 Android/Boost 下载网络。
`-RemoteRoot`、`-RemoteSdk` 可覆盖远端目录，`-Jobs` 控制原生并行度（默认 2）。
`local-build.json` 保存 UbuntuHost、RemoteRoot、RemoteSdk、JavaHome，已被 Git 忽略；
`-AllowRemote` 不会写入配置，每次使用都要显式指定。主机也可由 `CARLAUNCHER_UBUNTU_HOST` 提供。

## 检查范围与报告

| 检查 | 默认本地入口 | 报告 |
| --- | --- | --- |
| Detekt：Kotlin 命名、复杂度、异常与协程规则 | 必跑，违规失败 | 各模块 `build/reports/detekt/` |
| Checkstyle 12.3.0：Java 命名、导入、大括号等 | 必跑，违规失败 | 各模块 `build/reports/checkstyle/checkstyleAll.html` / `.xml` |
| SOME/IP 分层依赖 | 必跑，违规失败 | 控制台及本地构建日志 |
| JVM 单元测试 | 默认运行，Quick 跳过 | 各模块 `build/reports/tests/testDebugUnitTest/index.html` |
| Android Lint | 默认运行，Quick 跳过；错误失败 | 各模块 `build/reports/lint-results-debug.html` |
| 原生库来源与 APK 库一致性 | 必跑，校验失败即停止 | `build/native/artifact/apk-verification.json` |
| JaCoCo 报告 | `-Coverage` 启用 | `app/build/reports/jacoco/fullDebugUnitTestCoverageReport/html/index.html` |

Checkstyle/Detekt 扫描各模块 `src` 下手写源码，含 JVM、设备测试和 JMH；
没有 Java 的模块显示 `NO-SOURCE`，不产生 Checkstyle 报告。规则在
`config/checkstyle/checkstyle.xml`、`config/detekt/detekt.yml`。
两者挂到 `preBuild`、`check` 和相应编译任务；不要跳过门禁或用 baseline 隐藏问题。

SOME/IP 公共 API 另启用 `UndocumentedPublicClass`、`UndocumentedPublicFunction` 和
`OutdatedDocumentation`。范围为 `someip-android/src/main/java/com/example/someip/*.kt`，
包括嵌套公开类型；不覆盖 `internal`、测试或 app。已有 KDoc 的参数/属性标签须与签名匹配。
规则检查文档存在及标签一致性，语义准确性仍需代码审查。

JaCoCo 当前实际统计 app，排除生成代码；可合并已有设备覆盖率数据。
`verifyFullDebugUnitTestCoverage` 定义行 90% / 分支 85% 门槛，但本地默认和两套 CI
均未调用此校验任务，生成报告不代表阈值通过。

## 产物与诊断

- APK：`app/build/outputs/apk/debug/app-debug.apk`
- AAR：`someip-android/build/outputs/aar/someip-android-debug.aar`
- 每次入口日志：`build/local-build-时间戳.log`
- 远程传输与日志：`build/local-build/<run-id>/`

```powershell
# 单独运行静态检查，不需要原生库或设备
.\gradlew.bat :app:detekt :someip-android:detekt :app:checkstyleAll :someip-android:checkstyleAll
# 单独运行分层检查
.\tools\check-someip-layering.ps1
```

JDK 缺 `jlink.exe` 时换用完整 JDK 21。原生库校验失败时查看具体来源字段或 SHA256 错误，
按远程模式准备匹配产物，不手工替换为来源不明的 `.so`。
Gradle 缓存目录访问失败需要修复该目录权限或按执行环境授权，不能据此判断代码编译失败。

## CI 与设备验证

GitHub 的 [Android Build](.github/workflows/android-build.yml) 构建原生库、APK/AAR，
运行静态检查、单元测试、分层检查、APK 校验并生成覆盖率报告。
GitHub 同时构建 Profiling APK 并运行其 Lint，Actions Summary 展示静态审查与测试汇总，
详细 XML/HTML 报告位于 Artifacts。GitHub 不运行 JMH、simpleperf 或性能回归门禁。
[GitLab CI](.gitlab-ci.yml) 继续在原本机环境运行 JMH、专用设备 simpleperf、固定动作重放
与性能回归门禁，保留原有基线和阈值。详情见 [CI 报告](docs/gitlab-reports.md)。

本地默认构建不运行设备仪器测试、性能门禁或 Ubuntu/LVGL 联调。
需要时单独执行 `:app:connectedDebugAndroidTest`、`:someip-android:connectedDebugAndroidTest`，
或参考以下文档；构建通过不能代替设备、协议和 UI 验收。

- [原生源码构建](third_party/vsomeip-android/README.md)
- [SOME/IP 模块](someip-android/README.md)
- [Android / Ubuntu / LVGL 联调](docs/local-someip-integration.md)
- [simpleperf 使用](docs/simpleperf-profiling.md)
- [车辆协议](PROTOCOL.md)
- [Mock 事件](tools/someip-probe/MOCK_EVENTS.md)
