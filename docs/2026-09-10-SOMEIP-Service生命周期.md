# CarLauncher 真实 Service 生命周期与配置修复

2026-09-10，已按任务 `01a088ab-f841-76c1-82b0-9efd27a68554` 优先补齐真实 Android Service 生命周期证据。

此前代码、65 JVM / 63 设备测试、Method/去重与 SD 失败证据已提交为 `61ce4f7cbeab4ee5e20ad8123a8bf8885b3512f1`。其他 IDE、TCP 协议和设计文档改动保持原状。

本轮修正 `VehicleSendService.rewriteUnicast`：原正则会同时覆盖静态远端地址，现在解析 JSON 后只改根节点。读取或解析失败向启动失败路径传播，不再静默继续；设备回归覆盖静态端点保留、重复改写幂等和非法配置保护。

两轮真实流程：开始发送 → Home → onStop/解绑 → 后台 Method 继续收发 → Intent 返回 → 绑定原 Service → 停止发送 → 单次 native 释放 → 新 Service 再做一轮。日志记录实例身份以支持跨层核验。后台各接受 6 个成功响应，两个实例各释放一次；停止后提交失败。

Linux PCAP 共 92 包，native 提交/接受 23/23；服务端收到 46 个 Request，去重后应用 23 次。Client/Session 在第二轮会复用，核验必须同时考虑 payload（包含 timestamp），不能只按 Session 汇总两个生命周期。

全量：JVM 65 通过；设备 64 通过、4 个显式探针跳过；显式 Service 探针另行 1 通过；Lint 0 错误/101 警告。`.ec` 导出仍因 user10/user0 路径失败，未声称覆盖率门禁通过。

复现命令、Linux 启动信息、失败轮次及原始日志：[证据 README](../evidence/20260910-service-lifecycle/README.md)。统一报告：[summary-zh.html](../evidence/20260910-service-lifecycle/summary-zh.html)。

仍未完成：SD 跨端发现、长时间后台保活、进程死亡恢复、ARM 和 LVGL 集成。下一步是 Ubuntu Adapter 写线程安全 snapshot，再由 LVGL UI Timer 更新；不能把 native 原始启停、TCP 成功或本轮约 0.6 秒后台窗口外推为这些验收完成。
