# Repland 交付进度

更新日期：2026-10-03。用户已授权：先提交 GitHub 当前状态，再执行产品交付计划。

## 基线

- 当前项目快照已推送 `origin/main`：`5901bc3`。
- 实施分支：`codex/product-delivery`。
- 候选版本：`0.1.1 (1001)`；安装包哈希与最终测试结果在实际构建后记录。

## 阶段状态

| 阶段 | 状态 | 证据/剩余工作 |
| --- | --- | --- |
| M0 | 核心基线通过 | debug/internal 编译、61 项单元测试、40 项 API 36.1 设备测试通过；领域冲突失败保留原计划。真机发布验收仍在 M5。 |
| M1 | 未开始 | 持久化顺序与草案、批量原子提交、过期检查、轨道持久化。 |
| M2 | 未开始 | 独立制定计划与任务排序、对话下属工作流及缺项补充。 |
| M3 | 未开始 | 正式 Provider 与统一 AI 操作；真实模型验证需有效测试配置。 |
| M4 | 未开始 | 执行会话、重排、历史恢复与单一模式。 |
| M5 | 未开始 | 真机与用户连续试用。 |

## 验证记录

已通过：`:app:testDebugUnitTest`（61 项）、`:app:assembleInternal`、`:app:connectedDebugAndroidTest`（40 项，API 36.1）、`:app:verifyOfflineMvpBoundary`。新增领域及设备 Repository 冲突测试覆盖课程、固定事项、休息、锁定及单日阻挡。

发现并处理：旧规则允许 AVAILABLE 例外覆盖课程；现统一为硬约束优先，并验证容量不足结果。debug 包改用 `.qa` 隔离测试；仪器测试断言同步使用实际包名。

候选 APK 的 SHA-256 和版本由 `:app:recordBuildArtifacts` 生成到 `android/app/build/outputs/build-manifest.txt`；后续阶段继续更新安装包，最终验收绑定最终哈希。

当前正式安装包的数据不用于自动化测试，不清除用户数据。所有完成声明须绑定候选构建与实际验证结果。
