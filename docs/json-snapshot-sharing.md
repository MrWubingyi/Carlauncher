# JSON 快照临时只读分享

功能位于现有 `DatabaseLabActivity` 的“分享 JSON 快照（临时只读）”按钮。
输入是原“保存记录快照”生成的 `filesDir/records_snapshot.json`，不是仓库根目录的同名文件。
分享时不重新生成 JSON、不读写 Room，也不经过原记录 Provider 的写方法。

## 文件和授权

`SnapshotShareRepository` 在 `cache/json_shares/` 中生成唯一命名的 `.json` 副本，
按原字节复制。复制与原页面快照保存、删除共用单线程执行队列。
每次分享使用不同文件，旧接收者的 URI 不会因后续分享而换成新快照。
复制失败清理本次部分文件，失败／无接收入口清理未发送副本。

`SnapshotFileProvider` 使用单独 authority `${applicationId}.snapshotfiles`。
XML 只声明 `<cache-path name="json_snapshots" path="json_shares/" />`；
没有 files-path、root-path、external-path 或 cache 根路径。
Provider 非导出，仅对明确获得 URI grant 的应用开放对应资源。

`SnapshotShareLauncher` 构造全新的 ACTION_SEND，设置 application/json、EXTRA_STREAM、
ClipData 与 FLAG_GRANT_READ_URI_PERMISSION，再交给系统 chooser。
没有写、持久化、前缀授权；没有给全部候选应用预先授权。
`<queries>` 只增加 SEND/application/json 的发现范围。
没有接收入口时不启动 chooser；发现后入口失效的 ActivityNotFoundException 也映射为无接收入口反馈。

页面启动 chooser 后显示“已打开分享选择器，请在接收应用中确认读取结果”。
这不代表用户已选择应用，更不代表接收端成功读取。
不能在页面 onStop 时删除共享文件，因为接收端读取通常发生在发送页面停止之后。
已发起分享的副本留在 cache 中由系统按需回收，目前没有固定过期时间。
Intent URI 授权随接收任务生命周期结束；接收端已保存的内容不受撤权影响。

## 模拟器接收端前置检查

在当前 AAOS 用户执行：

```powershell
adb -s emulator-5554 shell am get-current-user
adb -s emulator-5554 shell cmd package query-activities --brief --user 10 -a android.intent.action.SEND -c android.intent.category.DEFAULT -t application/json
```

2026-10-20 初始环境没有匹配的 SEND 或 VIEW 接收 Activity。
为完成实读验证，测试 APK 增加 `JsonShareReceiverActivity`，显示名称为 JSON Share Probe。
它是只用于验证的独立应用进程／UID，不是发送应用里的模拟 Resolver。
接收器用 SDK Java 实现，确保独立启动时不依赖发送 APK 中的 Kotlin/AndroidX 类；
其 Java 源码正常参与 Checkstyle。

接收器验证 ACTION_SEND、application/json 和 content scheme，然后通过自己的 ContentResolver
打开文件、解析 JSON 数组，保存本地回执；不联网、不转发 Intent。
接收大小上限为 4 MiB，仅用于约束测试工具，不是生产分享限制。
回执包含收到的实际文本、字节数、SHA-256、URI、UID、ClipData 匹配情况，以及写入／持久化授权拒绝结果。
“Close JSON receipt”按钮调用 finishAndRemoveTask，供临时授权失效验证使用。

它的 exported=true 是为了接收标准跨应用分享，只存在于 `src/androidTest`。
无需也不得把接收器加入正式应用 Manifest。

## 定向检查

JVM：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*SnapshotShareRepositoryTest' --offline --no-daemon --max-workers=2
```

用临时目录检查精确字节复制、连续分享不覆盖旧文件、缺文件不创建副本、清理拒绝专用目录之外的路径。

设备：

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --offline --no-daemon --max-workers=2
adb -s emulator-5554 install -r --user 10 app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5554 install -r --user 10 app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument --user 10 -w -r -e class 'com.zqautomotive.launcher.ui.lab.database.SnapshotShareActivityTest,com.zqautomotive.launcher.data.provider.SnapshotFileProviderTest' com.zqautomotive.launcher.test/androidx.test.runner.AndroidJUnitRunner
```

安装测试 APK 后再次查询应出现 JSON Share Probe。不同设备须先核对实际用户 ID。
设备测试使用真正的页面按钮与系统 chooser，选择接收器（AAOS 只有一个候选时可能自动打开）后比较收到的 JSON、长度和 SHA-256，
检查双方 UID 不同、写入及持久化授权被拒绝。关闭接收任务后，无授权重发相同 URI 应读取失败。
另验证 filesDir 原件、cache 根目录文件和数据库路径不能映射成该 FileProvider URI。

缺文件用例将原快照临时改名，检查反馈后在 finally 还原。
无接收入口用例通过 `run-as` 使用测试接收应用自己的 UID 暂时禁用其 Activity，
确认查询不到接收入口后检查反馈及未发送副本清理，随后还原默认启用状态。
当前模拟器禁止普通 shell 修改此组件状态，因此测试显式断言禁用和恢复命令成功。
该用例要求模拟器没有另外安装 JSON 分享接收器；不要为运行测试禁用用户的其他应用。
原快照不存在时，测试创建临时 JSON 快照并在结束时删除，不操作 Room。

运行时回执在接收应用的 `files/json-share-receipt.json`；
成功及撤权后的回执还复制到发送应用 `cache/json-share-test-evidence/`，供归档读取。
使用 `run-as <包名> --user <用户ID>` 读取 Debug 应用私有文件，注意参数顺序。

最终结果以当次 Runner 和构建日志为准。默认构建的原生来源门禁、覆盖率阈值、
性能和协议验收均与这组分享功能测试分开报告。

参考：[Android 文件共享](https://developer.android.com/training/secure-file-sharing/share-file)、
[FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)、
[包可见性声明](https://developer.android.com/training/package-visibility/declaring)。
