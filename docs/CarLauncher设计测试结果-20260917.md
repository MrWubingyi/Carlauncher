# CarLauncher 设计测试结果（2026-09-17）

本轮已按批准内容执行并归档；测试与覆盖率门禁未通过。未修改生产代码，未削弱覆盖率阈值，未提交 Git。

统一入口：[中文报告](../evidence/20260917-design-tests/summary-zh.html)。报告包含两层 JUnit、失败堆栈、跳过清单、覆盖率计数及全模块源文件盘点、Lint、执行命令和退出状态、日志及全部报告入口。

## 审批记录

- reviewId：`REV-5d10a82ff17a4f77b7cd107a627d0fc4`，revision：1，决定：`APPROVED`。
- SHA-256：`9e622edb325c50ec29a132a1e75280e561ab5bbbf7201a19c549dcc94ab76232`。
- 审批人：武；时间：`2026-09-17T09:20:23.449+00:00`（北京时间17:20:23）；意见：通过；问题答案：`{}`。
- 执行前逐项核对10份送审文件，内容与批准快照完全一致。
- [审批导出与原始快照](reviews/design-tests-prereview-20260917.approval.json)。批准对象是用例和设计，不是测试通过，也不扩大为生产缺陷修复授权。
- 此前误将审批后置的执行事实已在审阅中披露。报告保留此前日志；下列完整回归是批准后重新执行的结果。批准快照中的PENDING及尚未运行描述为历史状态，本文件记录后续状态，不修改已批准正文。

## 结果

| 层次 | 执行用例数 | 通过 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|---:|
| JVM完整回归 | 220 | 219 | 1 | 0 | 0 |
| Android设备完整回归 | 75 | 69 | 1 | 0 | 5 |
| 本次新增JVM | 18 | 17 | 1 | 0 | 0 |
| 本次新增设备 | 6 | 5 | 1 | 0 | 0 |

新增项包含11个参数化边界实例；合计24项，22通过、2失败。已有未提交SOME/IP测试保留并纳入完整回归，不计为本次新增。设备覆盖率恢复重跑与connected结果相同，不重复计入总数。

## 已复现缺陷

| ID | 用例 / 复现条件 | 实际结果 | 后续修复方向（本次未实施） |
|---|---|---|---|
| ISSUE-D01 | `MockVehicleDataSourceTest.stop_beforeQueuedSilenceTransitionDoesNotDereferenceClearedListener`：推进至seq699，stop清空listener，再执行已捕获的任务 | seq700静默状态路径直接访问listener，抛出NullPointerException | 统一停止状态和迟到任务防护，避免状态回调直接解引用可清空字段；回归普通帧/静默/恢复边界 |
| ISSUE-D02 | `BroadcastLabActivityTest.sameInstanceReturningToForeground_registersReceiverAgain`：同一Activity RESUMED→CREATED→RESUMED | receiverRegistered仍为false | 将视图binding释放放到合适的销毁阶段，确保onStart能重新注册、onStop仍正确注销 |

失败断言保留，没有改成“当前错误行为应通过”，也未使用Ignore或重试掩盖失败。

## 覆盖率与静态检查

- JVM+设备全模块行覆盖：1274/1987 = **64.12%**；分支：426/792 = **53.79%**。
- 原门槛保持LINE≥90%、BRANCH≥85%；`:app:verifyFullDebugUnitTestCoverage`退出1，明确因阈值不足失败。
- 合并前仅JVM覆盖28/55个可执行类；合并后50/55。盘点41个Java源文件，无可执行源文件从分母静默消失；接口没有可执行行，单独标记。
- JaCoCo仅衡量Java代码，不覆盖C++/JNI本体。未使用生产类白名单，未改变原生成类排除项。
- Android Lint：Fatal 0、Error 0、Warning 104、Information 0、Other 0；`:app:lintDebug`成功。警告明细见统一报告。
- AGP在多用户车机上默认读取user0，导致connected覆盖率文件为0字节。用同一套已编译测试指定user10的coverageFile重新执行，取得57,346字节的本次设备执行数据，校验JaCoCo头并合并。
- 临时init脚本显式选择本次JVM/设备执行数据，排除9月4日遗留ec；同时补足覆盖率任务对编译输出的依赖。脚本和两个执行数据文件已归档，不修改app构建配置。

## 尚未完成的验收

VHAL真实属性/权限、单栏返回后重选、数据库异步退出以及需要远端的5个SOME/IP/JNI探针仍未完成验证。性能阈值及测量口径未定义，不报告性能通过。当前新增用例补充了已批准的缺口，不等于全部设计需求和全模块覆盖率已达标。

下一步应针对ISSUE-D01/02安排生产修复，再回归失败用例及完整两层测试；扩大覆盖率需要继续设计框架/源数据路径的测试，不能通过缩小分母解决。
