# vSomeIP Android 源码构建

主仓库不再保存或读取 `lib/x86_64/libvsomeip3.so`。vSomeIP 源码由
[third_party/vsomeip](https://github.com/MrWubingyi/vsomeip) 子模块管理，gitlink 是唯一的源码版本记录。
CI 在全新的 Ubuntu runner 上从源码构建 Boost 和 vSomeIP，然后构建 JNI 与 APK。

## 构建输入与输出

- vSomeIP：子模块固定提交，已经包含 Android 网络适配与 monolithic 修改，不重复应用补丁。
- Boost：官方 1.90.0 源码包，下载后校验脚本固定的 SHA-256，以静态 PIC 库构建。
- 环境：Android CLI 安装 NDK 28.2.13676358、CMake 3.22.1；目标为 x86_64 / API 24 / `c++_shared`。
- 参数：Release、`ENABLE_MULTIPLE_ROUTING_MANAGERS=ON`、`ANDROID_CI_BUILD=ON`，关闭 DLT 和 systemd。
- 输出：`build/native/artifact/lib/x86_64/libvsomeip3.so`。CMake 只链接此生成路径，无旧库回退。

源码构建脚本拒绝脏子模块、与主仓库 HEAD gitlink 不一致的检出、以及已存在的 `build/native`。
升级时先推送子模块提交，再提交主仓库 gitlink；运行脚本前必须完成主仓库提交。
CI 不使用原生二进制缓存，每次重新编译。首次构建耗时会高于旧的预编译包方案。

## Linux 本地执行

需要 Git、curl、tar、bzip2、Python 3、主机 C++ 编译器（Boost.Build 启动工具）和 Android CLI。

```bash
git submodule update --init --recursive
export ANDROID_HOME="$HOME/Android/Sdk"
android --sdk="$ANDROID_HOME" sdk install \
  platforms/android-36.1 build-tools/36.0.0 \
  ndk/28.2.13676358 cmake/3.22.1 platform-tools
bash tools/vsomeip-android/build-source.sh
bash gradlew :app:assembleDebug :app:testDebugUnitTest
python3 tools/vsomeip-android/verify-artifact.py --apk app/build/outputs/apk/debug/app-debug.apk
bash gradlew :app:fullDebugUnitTestCoverageReport
```

重新构建前，确认路径后清理项目内生成的 `build/native` 目录。不要删除 `third_party/vsomeip`。
`NATIVE_BUILD_JOBS` 可设置并行度，默认 2，避免云端内存不足。

## Windows / Android Studio

### Windows 统一构建入口

本地入口、参数、环境和检查范围统一维护在[根 README](../../README.md)。
默认 `./build-local.ps1` 使用已验证的本地原生库，构建 APK/AAR 并运行 JVM 测试和 Lint。
远程准备必须显式使用 `-AllowRemote`；强制重建使用 `-AllowRemote -RebuildNative`。
配置主机名不会自动启用源码传输。

## 验证与归档

CI 检查 ELF64 / x86_64、未带版本号的 SONAME、Android 网络适配导出符号以及动态依赖白名单。
Boost 静态链接，不允许依赖主机 Linux 库或外置 vSomeIP 插件。
APK 生成后，对包内 `lib/x86_64/libvsomeip3.so` 与本次生成库做 SHA-256 一致性校验。

原生产物包含库、许可证、`provenance.json`、`SHA256SUMS`、ELF / 符号检查记录和
`apk-verification.json`；编译日志、CMake 配置与编译命令单独归档，失败时也上传可用日志。
APK、测试报告、原生产物和诊断日志保留 14 天。历史提交中的二进制保留在 Git 历史中，当前构建不再读取。

这提供源码到 APK 的可追溯证据；并不声称不同环境的重复构建必然逐字节一致，也不替代设备运行验证。
