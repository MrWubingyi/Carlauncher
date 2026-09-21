# vSomeIP Android 预编译库

vSomeIP 源码和公共头文件由 `third_party/vsomeip` Git submodule 管理，来源为 [MrWubingyi/vsomeip](https://github.com/MrWubingyi/vsomeip)。主仓库通过 gitlink 固定提交，不自动跟随远端分支。

本目录保存与子模块提交配套的 Android x86_64 预编译库，供本地和 GitHub Actions 直接链接：

- `lib/x86_64/libvsomeip3.so`：vSomeIP 3.7.5，NDK 28.2.13676358、API 24、`c++_shared`，静态链接 Boost 1.90.0。
- `SOURCE_COMMIT`：构建库所对应的源码提交；CI 检查其与子模块 HEAD 一致。
- `licenses/`：vSomeIP MPL-2.0 和 Boost 许可证。
- `SHA256SUMS`：库、许可证和源码提交记录的校验值。

当前提交为 `d217416287b48c4f935bdaa7926a67efde4b0c9b`，已包含 Android 网络适配和 monolithic 修改，导出 `vsomeip_android_set_network_state`。构建使用 `ENABLE_MULTIPLE_ROUTING_MANAGERS=ON`、`ANDROID_CI_BUILD=ON`。原预编译库来自 `E:\Src\vsomeip\build-android-x86_64-mono`；源码和头文件现在直接取自同一提交的子模块，不再保存源码压缩包或重复的头文件。

## 获取与更新

```powershell
git submodule update --init --recursive
```

升级时先在子模块中检出目标提交，使用相同 NDK、ABI 和 monolithic 参数重新构建，再打包：

```powershell
.\tools\vsomeip-android\package-dependency.ps1
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
git add third_party/vsomeip third_party/vsomeip-android
```

打包默认读取子模块下的 `build-android-x86_64-mono/libvsomeip3.so`；可以通过 `-Library` 指定其他位置的配套构建，调用者须确认其确实由 `SOURCE_COMMIT` 所记录的源码构建。
脚本要求源码修改已提交，并检查网络适配导出符号。定制源码提交应先推送到子模块远端，再提交主仓库的 gitlink 和预编译包。
源码由子模块管理，预编译产物仍由主仓库管理；CI 不重编译 vSomeIP 和 Boost。

CMake 可通过 `VSOMEIP_SOURCE_ROOT` 和 `VSOMEIP_LIBRARY_DIR` 覆盖源码目录和库目录。当前仅提供 x86_64 库。

## Windows 大小写冲突

当前子模块提交同时含有 `documentation/README.md` 与 `documentation/readme.md`。Windows 默认文件系统无法区分它们，首次检出可能显示一个文档被修改；这不影响编译。确认未编辑这两个文档后，可仅在本机排除小写路径：

```powershell
Remove-Item -LiteralPath .\third_party\vsomeip\documentation\README.md
git -C third_party/vsomeip sparse-checkout set --no-cone '/*' '!/documentation/readme.md'
```

该配置仅存于本机子模块 Git 元数据，不改变固定提交；Linux CI 使用完整检出。
