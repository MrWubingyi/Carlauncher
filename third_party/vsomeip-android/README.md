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

从项目根目录执行 `build-local.ps1`。Windows 需要 PowerShell 5.1+、Git、Python 3.9+、
OpenSSH 的 `ssh`/`scp`、完整 JDK 21（含 `javac` 和 `jlink`）及 Android SDK。
SDK 路径优先使用 `local.properties` 的 `sdk.dir`，否则使用 `ANDROID_HOME` / `ANDROID_SDK_ROOT`；
SDK 组件缺失时调用已安装的 Android CLI 安装项目固定版本。
JDK 默认查找 `JAVA_HOME`、Android Studio JBR 和 `~/.jdks`；也可传 `-JavaHome`。

Ubuntu 需要可用的 SSH 密钥认证，以及 Git、curl、tar、bzip2、Python 3.9+ 和 g++。
脚本检查这些前提；缺少 Android CLI 时安装到独立缓存的 `bin` 目录，缺少 NDK/CMake 时
自动安装。首次下载需要 Ubuntu 能访问 Google Android 与 Boost 官方下载站。

```powershell
# 首次设置；UbuntuHost 也可以是 ~/.ssh/config 中的别名
.\build-local.ps1 -UbuntuHost root@192.168.112.129 -SaveConfiguration
# 后续一条命令
.\build-local.ps1
# 强制从源码重建原生库
.\build-local.ps1 -RebuildNative
# APK + JVM 单元测试
.\build-local.ps1 -Test
# 可选：使用 Ubuntu 已有 SDK 和指定缓存目录
.\build-local.ps1 -UbuntuHost user@ubuntu -RemoteRoot /home/user/.cache/carlauncher-local-build `
  -RemoteSdk /home/user/Android/Sdk -JavaHome 'C:\Program Files\Java\jdk-21' -Jobs 4 -SaveConfiguration
```

`-SaveConfiguration` 保存 `UbuntuHost`、`RemoteRoot`、`RemoteSdk` 和 `JavaHome` 到
项目根目录的 `local-build.json`，此文件被 Git 忽略。也可以用环境变量
`CARLAUNCHER_UBUNTU_HOST` 提供主机。命令参数优先于配置文件；`Jobs` 默认 2，
`-Test` 和 `-RebuildNative` 仅对本次执行生效。

缓存键包含主仓库 HEAD、vSomeIP gitlink 与三个原生构建/验证脚本内容；提交或脚本变更后
重新构建。应用未提交改动由本地 Gradle 直接编译。原生脚本未提交改动按当前内容传送；
vSomeIP 必须与 HEAD gitlink 一致且构建输入干净，仅忽略 `documentation/` 的文档差异，
以兼容 Windows 大小写冲突。通过 Git bundle 传递固定提交，无需推送，也无需 Ubuntu
持有 GitHub 凭据。机器配置和产物不进入源码提交。

Ubuntu 默认目录为 `$HOME/.cache/carlauncher-local-build`，SDK 放在其中的 `sdk`。
Boost 下载包保存在 `downloads/`，每次复用前校验脚本固定的 SHA-256；CI 默认不启用此缓存。
每次远程操作使用 `runs/<随机ID>`，成功产物按输入哈希缓存于 `artifacts/`；
脚本不会清理已有目录。远程缓存校验不通过时重新构建，旧缓存保留。
本地下载、构建日志及旧产物保存在 `build/local-build/<随机ID>/`。
远端日志是相应 `runs/<随机ID>/console.log`；SSH 或构建失败会停止，不继续打包 APK。
旧本地产物只有在新产物完整验证成功后才替换。

有效本地缓存不连接 Ubuntu；取回后校验所有必要文件的 SHA-256、源码提交、构建输入和
工具链参数、ELF 架构、SONAME、Android 网络符号与动态依赖。归档仅允许预期普通文件，
拒绝链接及路径越界。最后调用 Gradle，禁用 Java 自动探测并使用完整 JDK 21，避免选中
编辑器附带的精简 JRE；APK 生成后再次校验包内库哈希。

输出仍为 `app/build/outputs/apk/debug/app-debug.apk`，x86_64、默认 Debug 签名。
这是构建入口，不执行设备安装或运行验收。

### 手动准备原生依赖

源码构建脚本在 Linux 上执行。Windows 开发可使用 Linux / WSL 构建环境，
也可下载**当前源码版本对应的成功 CI** 的 `CarLauncher-native-x86_64-*` 产物，
将其内容解压到 `build/native/artifact/`，再执行 `gradlew.bat :app:assembleDebug`。
解压后应存在 `build/native/artifact/lib/x86_64/libvsomeip3.so`，不能额外嵌套产物文件夹。
检查 `provenance.json` 中的主仓库提交、vSomeIP 提交与环境版本，并按 `SHA256SUMS` 校验文件。
这些本地生成文件由 `/build` 忽略规则排除，不能提交回源码目录。

当前子模块包含大小写冲突的两个文档路径；Windows 可使用本机 sparse-checkout 排除
`documentation/readme.md`。Linux CI 完整检出，源码构建脚本要求子模块状态干净。

## 验证与归档

CI 检查 ELF64 / x86_64、未带版本号的 SONAME、Android 网络适配导出符号以及动态依赖白名单。
Boost 静态链接，不允许依赖主机 Linux 库或外置 vSomeIP 插件。
APK 生成后，对包内 `lib/x86_64/libvsomeip3.so` 与本次生成库做 SHA-256 一致性校验。

原生产物包含库、许可证、`provenance.json`、`SHA256SUMS`、ELF / 符号检查记录和
`apk-verification.json`；编译日志、CMake 配置与编译命令单独归档，失败时也上传可用日志。
APK、测试报告、原生产物和诊断日志保留 14 天。历史提交中的二进制保留在 Git 历史中，当前构建不再读取。

这提供源码到 APK 的可追溯证据；并不声称不同环境的重复构建必然逐字节一致，也不替代设备运行验证。
