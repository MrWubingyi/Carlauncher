# CarLauncher 设计测试输入基线（2026-09-17）

## 目标与范围

依据用户指定的《CarLauncher车机程序设计文档.md》补充缺失测试并执行 Android Debug 回归。使用 sdlc-input-analysis 建立追溯，android-unit-test-workflow 生成和运行测试，content-review 提交测试内容与结果的不可变快照。

用户已授权生成和执行测试。本次不把旧设计描述自动当成新需求，也不把测试失败改为通过预期。生产缺陷修复、SOME/IP 远端服务部署、性能验收与协议重设计不在本次测试变更中。运行结果以本次 `summary-zh.html` 及其归档 XML 为准。

## 证据基线

| 来源 | 定位 | 支持的事实与性质 |
|---|---|---|
| DOC-01 | `docs/CarLauncher车机程序设计文档.md`，v1.0，2026-09-06，基准 e2e4c66 | 用户指定输入；源码设计说明，不是全部已批准验收要求 |
| SRC-01 | 分支 `vsomeip-jni-client`，HEAD `f2f0de0e98763f1648f3ff83e6891e165142856f` | 当前实现基线 |
| TEST-OLD | `app/src/test`、`app/src/androidTest` | 既有测试；此次完整重跑才形成当前证据 |
| LOCAL-01 | 开始时未跟踪的 `ApprovedEventFixture`、4 个 SOME/IP ContractTest 及 `docs/reviews/` | 已有用户工作，保留并纳入回归；不计为本次新增 |
| BUILD-01 | `app/build.gradle.kts`、版本目录、`gradle/gradle-daemon-jvm.properties` | AGP 9.3.1、Gradle 9.5.0、Java 源兼容 11、Daemon JDK 21、JUnit 4、Debug 设备覆盖率 |
| ENV-01 | `adb devices -l`、`gradlew --version` | Automotive API 35 模拟器 emulator-5554，当前 Android user 10；命令行默认 Java 8，本次显式使用已安装 JDK 21 |

未发现工作区内 AGENTS.md。旧 summary 仅有 59 条 JVM 结果且无完整设备/任务状态，不能作为本次有效增量验收基线。先跑现有完整 JVM/connected 测试，再执行新增定向测试与完整回归。

## 六阶段分析

| 阶段 | 输入和结论 | 状态及理由 | 交接与缺口 |
|---|---|---|---|
| 需求分析 | DOC-01 §5–10、§12–13；边界、快照、显示、生命周期、数据库迁移可验证 | 有冲突：旧 TCP 输出与当前 SOME/IP Event 接收不一致 | 仅为仍适用契约补测试；性能阈值、负载、持续时间、统计口径待确认 |
| 概要设计 | Service 持有源，Repository/ViewModel 适配 UI；SQLite lab_record v2，SharedPreferences 配置 | 有冲突：原网络层/状态机已替换，旧 VehicleTcpClient 与 TcpConnectionState 已不存在 | 不生成针对已删除类的测试；VHAL 为保留可选路径 |
| 详细设计 | §6 快照、校验；§7.2 Mock 周期；§5.4 Fragment 恢复；§10.2 数据迁移 | 待补充：取消后迟到任务、同实例重新前台、迁移保留内容缺断言 | 增加下表 TC-D 系列；缺陷预期与现状明确区分 |
| 编码（实现） | 本次改动仅测试及验证/交接资料，沿用 Java/JUnit4 与 ActivityScenario | 材料齐备：现有调度器注入口支持确定性测试 | 不引入真实等待、外网或生产重构；完整源码清单见统一报告 |
| 测试 | 新增 18 个 JVM 执行用例、6 个设备用例；已有用例完整回归 | 待补充：以当前失败、跳过、覆盖率门禁为准 | 默认行 90% / 分支 85% 门禁保持；失败不能表述为验收通过 |
| 维护 | §13 风险转为复现测试和 ISSUE-D 编号；留存执行命令、日志、XML、覆盖率和 Lint | 待补充：生产修复及修复后回归尚未完成 | 阶段分析不代表六阶段均已验收；无发布或上线证据 |

## 需求与测试追溯

以下类名在 `com.example.carlauncher` 包下。每个测试方法的精确结果与参数化行见 JUnit；表中的“新增”不代表已通过。

| ID / 来源 | 验收依据与预期 | 架构 / 实现 | 新增测试定位 | 缺陷关联 |
|---|---|---|---|---|
| TC-D01 / §6.2 | 先 Math.round 再检验 0–200；-0.5、200.5 的相邻浮点值；溢出、NaN、缺档优先级，11 组 | 校验层 / VehicleStateValidator | JVM `model.VehicleDesignBoundaryTest` | 边界回归 |
| TC-D02 / §6.1 | Builder 复用不改变已发布快照 | 模型 / VehicleState | JVM `model.VehicleSnapshotDesignTest.reusedBuilder_doesNotMutatePublishedSnapshot` | 不可变性 |
| TC-D03 / §6.1、§7.2 | speed/rpm/原始电量和温度保留；SOC=150 钳制到100，validity 写1 | 模型与 JSON | `injectedInvalidValues_preserveRawSpeedRpmAndBatteryButClampSoc` | 保留现状，不认可单位正确 |
| TC-D04 / §6.1 | 显式 null 的可选字段省略；false/0 不是 null 替代 | 模型与 JSON | `explicitNullOptionals_areOmittedRatherThanWrittenAsFalseOrZero` | wire 契约 |
| TC-D05 / §6.1 | long 序号/时间戳保持整数精度；SOC 两端闭区间 | 模型与 JSON | `sequenceAndTimestamp_preserveLongPrecisionOnWire` | 不验证 JS 消费端精度 |
| TC-D06 / §7.2 | seq699 最后非法帧 SOC100/原始电量150/温度-999 | Mock → 快照 | `data.mock.MockVehicleDataSourceTest.invalidWindow_lastFramePreservesRawBatteryAndClampsSoc` | 窗口右边界 |
| TC-D07 / §7.2 stop | 重复停止只通知一次；已排队普通帧不再通知 | Mock 调度器注入 | `stop_twiceEmitsStoppedOnceAndDropsAlreadyQueuedNormalFrame` | 取消清理 |
| TC-D08 / §7.2 stop、§8.3 | 停止清空 listener 后，已排队 seq700 任务也应安全退出，不抛异常 | Mock 调度器注入 | `stop_beforeQueuedSilenceTransitionDoesNotDereferenceClearedListener` | ISSUE-D01；取消健壮性验收建议，区别于文档原有明确逐帧规则 |
| TC-D09 / §5.5、§13 广播再次进入 | 同一 Activity STOP→RESUME 后重新注册接收器 | Android 生命周期 | `BroadcastLabActivityTest.sameInstanceReturningToForeground_registersReceiverAgain` | ISSUE-D02；验证文档已指出的缺陷修复目标 |
| TC-D10 / §10.2 | V1 已有记录升级保留 id/name/value/created_at，补空 note，后续 id 递增 | SQLiteOpenHelper | `data.local.LabDatabaseHelperTest.upgrade_populatedVersionOnePreservesIdValueAndTimestampWithEmptyNote` | 迁移数据丢失防护 |
| TC-D11 / §10.2 | 省略 note 得到默认空串，显式 NULL 被 NOT NULL 拒绝 | 实际 SQLite / 生产建表方法 | `schema_omittedNoteDefaultsEmptyButExplicitNullIsRejected` | DB 约束 |
| TC-D12 / §5.4 | 配置重建保留 Network 详情、返回栈数量，不重复安装详情 | FragmentManager | `FragmentLabActivityTest.recreate_keepsSelectedDetailAndBackStackWithoutAddingInitialFragments` | 当前设备布局限定 |
| TC-D13 / §5.2、§6.1 | 非法快照原值与 INVALID_SPEED 同时显示 | Activity / 真实控件 | `MainActivityTest.invalidSnapshot_displaysRawValuesAlongsideInvalidity` | 现状刻画，不表示无效数值显示策略已验收 |
| TC-D14 / §5.2 | 后续快照缺可选字段时清除旧显示，恢复占位符 | LiveData → ViewBinding | `nextSnapshotWithMissingOptionals_clearsPreviouslyDisplayedValues` | 防止残留旧数据 |

TC-D01 是 11 个参数化执行实例，其余每行一个执行用例，合计 24 个。新测试不访问外网、不启动远端服务、不使用 Thread.sleep。设备层使用真实资源、SQLite 和生命周期；Mock 使用已有受控调度器。原有测试使用的 Android 默认返回值配置保持，本次不依赖它证明 Android 行为。

## 缺口与冲突

| ID | 来源 / 缺口 | 影响与处理 |
|---|---|---|
| GAP-D01 | DOC-01 §2/§8/§9 与 SRC-01 Service、someip、CockpitUiState | 旧 TCP、START SEND、恢复期禁用按钮不再适用；当前为 SOME/IP Event 接收与 START/STOP RECEIVE，不回退实现以迎合文档 |
| GAP-D02 | DOC-01 §7.3/§11 VHAL | 真实 CarService 权限、属性支持、m/s 转换、watchdog 尚缺专门组件测试；不得用静态阅读当硬件验收 |
| GAP-D03 | DOC-01 §13 设置返回栈、数据库退出 | 单栏“返回后再次点同项”、异步数据库关闭竞态尚缺稳定 seam/同步观测点；本次不扩大到生产结构修复 |
| GAP-D04 | 既有 SOME/IP opt-in probes | 远端事件、真实网络发现、JNI 启停依赖外部条件；跳过必须列出，不计通过 |
| GAP-D05 | 覆盖率执行数据 | 原构建 glob 会包含 9 月4日旧 manual-user10-coverage.ec；AGP 采集默认 user0 路径失败，connected 文件为空。此次使用显式当前 exec/ec 的临时 init 脚本，保留完整生产类分母，不混入历史文件 |
| GAP-D06 | 默认全模块 90%/85% | 含 UI、Service、VHAL、网络编排，局部测试通过不能证明全模块达标；报告列出每个源文件和漏测处 |

## 下一阶段交接

| 接收方 | 输入 | 授权 / 交付 |
|---|---|---|
| android-unit-test-workflow | 本文与 TC-D01–14 | 用户已授权测试生成与执行；交付分层 JUnit、当前覆盖率/门禁、Lint、统一中文报告 |
| content-review | 本文、新增测试内容、测试结果和差异快照 | 只记录人工内容决定；未决定为 PENDING，不以沉默模拟批准 |
| 后续修复工作 | ISSUE-D01/02 与失败堆栈，GAP-D02/03 | 本次不更改生产行为；修复后重跑失败用例及完整两层回归 |

统一报告位置：`evidence/20260917-design-tests/summary-zh.html`。人工审阅编号、revision、SHA-256 和状态由实际 content-review 输出另行记录。本文不把内容审批当作测试通过证据。
