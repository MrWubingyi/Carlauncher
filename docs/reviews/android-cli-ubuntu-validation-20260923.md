# Ubuntu 虚拟机 Android CLI 验证记录

验证日期：2026-09-23（北京时间）。

**结论：Android CLI 安装与 SDK 环境管理验证通过；完整构建尚未完成验收。** 第二次构建尝试在 Boost 下载阶段失败，第三次后台重试的最终状态因 SSH 连接中断尚未取回。

## 验证范围

在本机已有的 Ubuntu 22.04.5 LTS x86_64 虚拟机上，通过 SSH 使用 `root` 账号执行。工作目录为 `/root/develop/android-cli-validation-20260923`，SDK、JDK、项目副本及 Gradle 缓存均放在此目录内；Android CLI 安装在 `/root/.local/bin/android`。

使用此前 GitHub 构建通过的基线 `09ea8746ae57c5ef304d2039070fc353439ecb03`，vSomeIP 子模块固定为 `d217416287b48c4f935bdaa7926a67efde4b0c9b`。本次结果不代表当前 Windows 工作区中后续 Room 改动及未提交改动已通过验证。

## 已确认结果

| 检查项 | 结果 |
| --- | --- |
| 官方 Linux 安装脚本 | 成功 |
| `android --version` | `1.0.16406183` |
| `android --sdk=... info` | 正确识别独立 SDK 目录 |
| `android sdk install` | 五个组件安装完成，退出码 0 |
| `android sdk list` | 五个组件均出现在 Installed packages 中 |
| Java | Temurin 21.0.12.1，下载包通过官方元数据 SHA-256 校验 |
| 原生库、APK、单元测试 | 未完成验收：下载曾失败，后台重试最终结果尚未取回 |

安装的组件：

- Android Platform 36.1（revision 1）
- Build Tools 36.0.0
- NDK 28.2.13676358（r28c）
- CMake 3.22.1
- Platform Tools 37.0.1

实际执行的 SDK 安装命令：

```bash
export PATH="$HOME/.local/bin:$PATH"
export ANDROID_HOME=/root/develop/android-cli-validation-20260923/sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
android --no-metrics --sdk="$ANDROID_HOME" sdk install \
  platforms/android-36.1 build-tools/36.0.0 \
  ndk/28.2.13676358 cmake/3.22.1 platform-tools
android --no-metrics --sdk="$ANDROID_HOME" sdk list
```

## 执行过程中的问题

- SSH 新连接间歇性超时；一次持续会话在 Boost 下载阶段被重置。已保留该次中断目录 `project/build/native-interrupted-01`，后续验证改为 `nohup` 后台执行，避免 SSH 断开终止构建。没有修改虚拟机网卡、路由或 SSH 服务配置。
- 一次组件检查在 SDK 尚未解压完时提前执行，返回 1；SDK 安装本身随后正常退出，再次检查通过。不能将该次提前检查当作 CLI 安装失败。
- 后台重试在 Boost 1.90.0 源码下载阶段遇到 `curl (56): OpenSSL SSL_read: unexpected eof while reading`，于 17:13:34 退出，尚未开始编译。已保留 `08-background-console.log` 及 `project/build/native-download-failed-02`。下一次尝试通过独立 `CURL_HOME` 配置启用 `retry-all-errors`、20 秒连接超时和 600 秒单次传输上限，不改动仓库中的构建脚本。
- 用户提供的 `curl -I https://dl.google.com` 返回 HTTP 404，说明已连接到服务器；下载站根路径不存在不代表具体安装包不可下载。

第三次尝试于 17:14 启动，启动时进程 ID 为 `426983`。最后取回的日志记录到 17:15:21，仍在下载 Boost；此后 SSH/SCP 多次连接或握手超时。该快照不是最终状态，不能据此断言后台进程现在仍在运行，也不能断言已经失败或成功。

可在 Ubuntu 本机终端查看最终结果：

```bash
sudo tail -30 /root/develop/android-cli-validation-20260923/logs/09-network-retry-console.log
sudo test -f /root/develop/android-cli-validation-20260923/logs/result.json && \
  sudo cat /root/develop/android-cli-validation-20260923/logs/result.json
```

只有日志显示 `VALIDATION_EXIT=0`，并存在记录单元测试结果的 `result.json`，再核对 APK 库哈希验证结果后，才能将完整构建列为通过。脚本使用独立源码副本，原生构建要求输出目录为空；不要直接覆盖已有 `build/native` 目录重新执行。

## 证据与边界

本地日志目录：`build/ubuntu-cli-validation-20260923/`。虚拟机原始日志目录：`/root/develop/android-cli-validation-20260923/logs/`。后续构建脚本保存在两端的 `continue-validation.sh` 中。

Android CLI 负责本次 SDK 环境管理；原生编译由 NDK/CMake/Boost.Build 完成，APK 构建和 JVM 单元测试由 Gradle 完成。未执行真机、模拟器、车端或 Journey 测试。

原生构建脚本中的 `provenance.json.run_url` 是 GitHub 专用字段，本地虚拟机运行时不适用，不能作为本次验证的流水线链接。
