# HTTP JSON 分层接入说明

现有 DatabaseLabActivity 读取网络 JSON 或选择 JSON 文档后，先合并保存到原 Room 数据库，再显示成功。内部快照读取和 SAF 导出继续沿用原实现。

“选择文档读取并保存”通过 ContentResolver 打开正文，再交给 JsonRecordsImporter 完整解析，最后复用 RoomNetworkRecordsStore 的事务合并。文档字段沿用导出快照：id、name、value、createdAt 必填，note 默认空字符串。任一记录格式错误，整份文档都不写入。合并、空数组、ID 和只读 Provider 规则与网络导入一致。

文档导入追加验证：285 项 JVM 测试通过；模拟器上 19 项 Room 导入、Provider 和原页面回归测试通过。APK、Detekt、Lint 通过（0 错误、22 警告）；完整文档选择器手动流程尚未验收。日志为 `build/document-room-*.log`，默认构建仍被原生库来源不匹配阻塞，未执行远程构建。

## 网络结果保存到 Room

生产调用链：页面 → HttpRecordsViewModel → PersistingRecordsRepository → HttpRecordsRepository → RoomNetworkRecordsStore → 原 LabRoomDatabase。

- `name` 沿用原表的唯一键；同名更新 value、note、created_at，保留本地 `_id`。新名称使用 Room 自增 ID，网络 id 不覆盖本地 ID。
- 整个响应在 IO 线程的一个事务中合并；任一写入失败则整批回滚。同一响应中同名记录以后出现的内容为准。
- 重复读取不会增加同名记录；空数组不清空数据库，服务器未返回的本地记录保留。
- 只有事务完成后页面才显示“读取并保存成功”。数据库失败显示独立提示，可重试；网络或解析失败不触发写入。
- 已取消请求的迟到响应在保存前被拒绝，事务内也检查取消。已经提交的事务不会因为之后取消而撤销。
- Provider 继续只读、exported=false。保存直接经过 Room，不经过 Provider 写方法。保存后点击原数据库查询或 Provider“查询全部”查看结果；本次未增加自动观察刷新。
- 网络结果区仍显示服务端 ID；Provider 返回本地 ID，两者可能不同。

下面 2026-10-12/13 的验收记录描述当时仅展示的版本，不作为新增持久化行为的验收证据。

本次持久化验证（2026-10-19）：app 全量 282 项 JVM 测试通过；API 35 模拟器上 19 项 Room/Provider 测试通过，其中新增两项验证合并幂等、空响应保留与失败事务回滚。设备测试使用隔离内存数据库，不代表完整网络页面的人工验收。Debug APK、测试 APK、Detekt 和 Lint 通过（Lint 0 错误、22 警告）。默认 build-local.ps1 的静态检查通过后仍因 `repository_commit` 原生库来源不匹配而阻塞，未启用远程构建。执行日志保存在忽略目录 `build/network-room-*.log`。

## 文件与职责

所有路径相对于 CarLauncher 工程根目录：

| 文件 | 职责 |
|---|---|
| app/src/main/java/com/example/carlauncher/data/remote/RecordsRepository.kt | 网络记录模型与可替换的数据接口，不依赖 UI 或 Room |
| app/src/main/java/com/example/carlauncher/data/remote/HttpRecordsRepository.kt | OkHttp 请求、超时、JSON 数组解析、响应关闭，以及协程取消到 Call.cancel 的连接 |
| app/src/main/java/com/example/carlauncher/ui/lab/database/HttpRecordsViewModel.kt | StateFlow 页面状态、请求 Job、取消、错误提示、请求代次隔离 |
| app/src/main/java/com/example/carlauncher/ui/lab/database/HttpRecordsSection.kt | 展示状态和记录，将读取/取消事件传给 ViewModel |
| app/src/main/java/com/example/carlauncher/ui/lab/database/DatabaseLabActivity.kt | 创建 ViewModel、收集状态、通过 networkContent 插槽接入页面 |
| app/src/debug/AndroidManifest.xml | 仅 Debug 引用测试网络安全配置 |
| app/src/debug/res/xml/debug_network_security.xml | 只允许 10.0.2.2 使用明文 HTTP |
| tools/http_records_server.py | 读取已有快照，在本机提供正常/延迟响应 |

依赖集中在 gradle/libs.versions.toml 和 app/build.gradle.kts。新增 OkHttp 4.12.0；协程显式固定为工程已有的 1.9.0，Lifecycle 沿用 2.9.0。MockWebServer 和 coroutines-test 仅用于本地测试。

## Activity 如何接入

1. 使用 by viewModels 和 HttpRecordsViewModel.factory 注入 Repository；Activity 不执行请求。
2. 在 setContent 内通过 collectAsStateWithLifecycle 收集 uiState。
3. DatabaseLabScreen 增加带默认空内容的 networkContent 插槽，因此现有 Preview/测试调用无需增加参数。
4. Activity 向插槽传入 HttpRecordsSection：state 是收集结果，onLoad/onCancel 指向 ViewModel。
5. 插槽在文件操作按钮之后、原数据库结果区之前渲染。

UI -> loadRecords -> Repository.fetchRecords -> OkHttp 后台线程请求并解析 -> Room 事务保存 -> ViewModel 更新 StateFlow -> Compose 重绘。

这里故意用独立 HttpRecordsViewModel，仅管理新增网络区。它不读写原页面的 status、resultText、actionsEnabled 或 databaseExecutor。

## 取消与生命周期

- UI 点击取消后，ViewModel 立即使当前请求代次失效，再取消 Job 并显示“已取消请求”。
- Repository 的 invokeOnCancellation 调用 Call.cancel，中断底层调用；response.use 负责关闭响应。
- 即使旧依赖迟到返回，代次不匹配也不能覆盖新请求的 UI。
- CancellationException 原样重新抛出，不转成网络错误。
- ViewModel 跨 Activity 配置重建保留状态与请求；正常退出 Activity、ViewModel 清除时，viewModelScope 自动取消。不要再在 Activity.onDestroy 手动取消网络任务，否则配置重建也会中断请求。
- collectAsStateWithLifecycle 暂停状态收集不等于取消请求；页面进入后台时请求仍可能完成。
- 进程死亡后不恢复请求或结果；本包不包含进程恢复验收。

## 启动与验证

在工程根目录运行：

~~~powershell
& 'C:\Users\w7647\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' .\tools\http_records_server.py .\records_snapshot.json
~~~

脚本直接读取已有快照，不复制归档；另一个文件可作为参数传入。服务监听 Windows 回环地址 127.0.0.1:8765。标准模拟器通过 10.0.2.2:8765 访问；自定义 TAP 拓扑需修改 Repository 的 endpoint 和 Debug 网络安全配置。

运行 Debug 应用并进入现有数据库页，在“网络 JSON”区域操作：

| 路径 | 操作 | 必要结果 |
|---|---|---|
| 成功 | 关闭延迟开关，点击读取 | 显示已有三条记录，与源 JSON 一致 |
| 超时 | 打开延迟开关，点击读取 | 服务延迟 8 秒，客户端读取超时 3 秒，提示超时，允许再次读取 |
| 断网 | 实际断开模拟器到主机的网络后读取，再恢复 | 显示失败或超时，恢复后正常读取 |
| 取消 | 打开延迟，读取后在超时前取消；关闭延迟再读取 | 立即显示已取消，新请求成功，旧结果不覆盖 |

停止 Python 服务只证明服务不可达，不等于模拟器断网。成功后还应查询 Room/Provider，确认保存的字段与记录数量。

构建和本地测试：

~~~powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest --tests '*HttpRecords*Test'
~~~

使用 Gradle/Android Studio 的 JDK 17+（本次执行使用已安装 JDK 21），不要使用 PATH 中的 Java 8。

本地测试覆盖解析、HTTP 错误、无效 JSON、超时、连接不可达、取消与重试，以及 ViewModel 的旧结果隔离。它们不代替 Automotive 设备上的四路径实操。只保存本包新增运行结果；源码/编译或 JVM 测试通过不自动表示设备验收完成。
## 本次检查（2026-10-12）

- Kotlin 编译通过；AndroidTest 源码编译通过。
- 全量 244 项 JVM 测试通过，其中新增 Repository 6 项、ViewModel 4 项；无失败、错误或跳过。
- 测试服务 Python 语法检查通过。当前没有 py 启动器，上方命令使用已验证的 Codex Python 路径。
- 尚未在 Automotive 设备运行四路径，也未进行设备截图或 UI 验收。

## 2026-10-13 设备运行收口（覆盖上节待验收状态）

用户在 Automotive API 35 环境提供截图，并确认按正常读取、超时恢复、主动取消后重试、服务不可达及恢复的范围收口。原“实际断网”路径因环境无法操作，经用户接受替换为停止/恢复 Windows Python 服务。实际网络接口断开未验证，仅限定结论，不作为阻塞或自动补测任务。

- 正常读取：三条记录与现有快照一致，终端 GET /records 返回 200。
- 超时恢复：延迟响应开启时显示“请求超时，请重试”；关闭延迟后再次读取三条记录成功。
- 主动取消：显示“已取消请求”，读取按钮恢复；随后正常请求成功，截图中延迟请求完成后仍保持成功结果。结合已有代次隔离 JVM 测试，不外推所有竞态。
- 服务不可达/恢复：终端回到 PowerShell 提示符后页面显示连接或读取失败；后续截图显示服务重新启动及成功读取。
- 初次延迟开关截图曾显示通用网络失败，未将其当作超时证据；后续明确超时提示作为超时路径依据。

唯一截图归档与结论入口：[HTTP JSON 收口记录](D:/Obsidian/Obsidian-Note/Learning/Android/参考/网络/HTTP%20JSON%20-%20CarLauncher%20分层与四路径验收.md)。原图保存于该库 Learning/Android/证据/2026-10-13-http-json，不在代码仓重复复制。

本次仅整理用户运行证据与提交已有代码；复用 10-12 编译和 244 项 JVM 测试结果，没有重新执行设备实验。截图不是安装 APK 与提交的严格二进制溯源证明。数据库原结果区的 No records 与网络结果区相互独立。
