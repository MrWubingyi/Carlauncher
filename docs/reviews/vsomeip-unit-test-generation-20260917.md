# vSomeIP 单元测试生成记录

状态：已生成，NOT_RUN；未编译、未执行测试、未计算覆盖率。依据审批 REV-b32dd43d4262406ab5266b25850c37fb 第 1 版及 Q1/Q2/Q3 回答。测试生成不代表产品行为通过。

## 本次增量

| 文件（app/src/test/java/com/example/carlauncher/someip/） | 对应审批用例 | 内容 |
|---|---|---|
| ApprovedEventFixture.java | 基准帧 F | 共享完整 JSON 构造；不复制生产逻辑 |
| VehicleEventBoundaryContractTest.java | VS-08 | 7 个字段的下界外、下界、上界、上界外，共 28 组参数；采用批准的 rpm=0 等边界 |
| VehicleEventRejectionContractTest.java | VS-09/10/27 | 每个必填字段缺失/null/错误类型；非法标识、枚举、整数溢出；空、截断、非对象、超长和 NUL payload |
| VehicleEventLengthContractTest.java | VS-10 | 恰好 4096 字节合法 JSON，断言身份与车速 |
| VSomeIpNativeTransportTest.java（追加） | VS-15/16/17/20 | 同时间戳乱序/重复、重启 seq 重置、重新发现、UDP 序号缺口、移除 listener |
| SomeipEventWatchdogContractTest.java | VS-01/13/20/22 | 首帧前等待、2999/3000/3001 边界、Event 恢复、重复 available、停止后迟到事件、失败后重启 |

新增断言不依赖真实等待、远端网络或 JNI 启动，不新增依赖，不修改生产源码和既有测试预期。传输测试只调用解码与 listener 转发入口，不调用 start/stop 或配置准备；现有 Android Log 桩属于旁路日志，不用于证明 Android 框架行为。

## 未覆盖边界与后续工作

- VS-12/Q3 的非法帧不得刷新 watchdog、VS-27 的完整质量状态组合，必须在 VSomeIpDataSource 层验证。该类直接使用 Handler/SystemClock，现有 returnDefaultValues JVM 配置无法证明真实定时行为；本次不以假时间恒为 0 的断言冒充验证。需要后续设备组件测试，或独立授权引入可注入时钟/调度器。
- VS-14 的 Service/Repository/UI 缓存清理、VS-20/21 的真实生命周期、VS-25 的 UI 线程，仍需 Android instrumentation。
- 服务发现、SD/UDP、双端同源、订阅阻塞、C++ mock、LVGL、性能门槛及其余集成场景仍需各自组件或端到端测试。本文不声称覆盖全部 28 项审批用例。
- 内嵌 NUL 拒收按协议写为强断言，当前源码无显式 NUL 校验，可能产生失败；尚未执行，不报告为已确认缺陷。
- 本次未生成/刷新 JUnit、JaCoCo 或 Lint 报告，历史报告不能作为新增测试通过的证据。

后续获得执行授权后，可先运行 Debug 单元测试并定位真实失败；本次未调用任何 Gradle 测试任务，也未自动提交 Git。
