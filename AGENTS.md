# 仓库 Agent 工作指引

## 开始工作

先读根 README 和相关模块文档，检查 `git status --short`。保留用户未提交的改动，
不要重置工作区或为了构建而自动提交。应用位于 `app`，通用客户端位于 `someip-android`，
vSomeIP 是 `third_party/vsomeip` 子模块。适用本目录及其所有子目录。

## 修改约束

- 应用与测试主要使用 Kotlin；Java 由 Checkstyle 检查，Kotlin 由 Detekt 检查。
- 通用 SOME/IP 库不得依赖 app 或车辆业务；应用仅经公共 API 使用库。
- 修改协议、JNI 或网络生命周期时阅读 `PROTOCOL.md`、模块 README 和网络适配说明。
- 不关闭静态规则、跳过检查、添加 baseline 或降低覆盖率/性能阈值来掩盖失败。
- 不手工引入旧 `.so`。子模块升级须先有可追溯源码提交，再更新主仓库 gitlink、重建并校验产物。
- 不提交 SDK/JDK 绝对路径、本机配置、密钥、构建缓存或报告。

## 本地验证

默认使用 `powershell -NoProfile -ExecutionPolicy Bypass -File .\build-local.ps1`。
入口检查 Detekt、Checkstyle、分层、原生库来源，构建 APK/AAR，执行 JVM 测试和 Lint，
再验证 APK 中库哈希。`-Quick` 仅适合迭代，不能声称完成默认全套检查。
`-Coverage` 生成 app 覆盖率报告，不会执行 90%/85% 阈值校验。

单纯静态检查可运行：

```powershell
.\gradlew.bat :app:detekt :someip-android:detekt :app:checkstyleAll :someip-android:checkstyleAll
.\tools\check-someip-layering.ps1
```

使用完整 JDK 21；固定 SDK/NDK/CMake 版本以 Gradle 与构建脚本为准。
正常构建允许 Gradle 有效缓存，报告中区分实际执行与 `UP-TO-DATE`。
对脚本逻辑或门禁的改动，应验证成功与相关失败路径；优先采用已有测试工具。

## 远程执行边界

普通本地构建默认不连接 Ubuntu。只有用户已授权该主机的源码传输/远程构建时，
才使用 `-AllowRemote`；此参数允许发送包含可达历史的 Git bundle 与原生脚本。
配置文件中存在主机名本身不构成当前请求的传输授权。缓存失效时先说明原因和目标主机，
已有授权则继续，没有授权则询问。不得绕过执行环境的审批拒绝。

远程准备使用 `build-local.ps1` 的隔离目录、来源校验及安全归档解包，不自行拼装替代流程。
日志与失败证据保留在 `build/`；清理/移动前核实绝对路径位于预期目录。

## 交付

按改动运行必要检查及 `git diff --check`，说明检查结果、未执行项与阻塞。
构建成功、报告生成、覆盖率阈值、性能门禁、设备/协议验收分别陈述，不能互相替代。
Checkstyle 的 `NO-SOURCE` 不等于扫描了 Java；Detekt 的 Checkstyle XML 不等于 Checkstyle 扫描。
只有用户要求提交时才提交，提交前排查生成文件与敏感信息，交付精确提交哈希和工作区状态。
