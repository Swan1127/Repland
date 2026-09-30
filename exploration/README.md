# Repland 探索区

这里存放 Repland 当前阶段的探索性设计、架构推演、工作流盘点和 Agent 边界研究。

本目录不是正式产品规范的替代品。当前结论分为三种状态：

- `事实`：已经存在于代码、测试或现有产品文档中。
- `假设`：为了探索方向提出，尚未进入正式产品边界。
- `待确认`：需要通过实现、测试或用户反馈验证。

既有代码的事实与既有正式实现约束仍以根目录 `CONTEXT.md` 和 `docs/` 为准；但**探索中的目标能力不再受旧 MVP 功能边界限制**。旧边界在这里是待审视的历史决策，不是新方案的产品上限。正式代码变更前，应把与旧规范冲突的决定明确评审并更新规范/ADR，避免设计与实现各说各话。

## 当前探索主线

1. 先把非 Agent、非远程 Workflow 参与的本地核心功能链路做稳定。
2. 盘点传统应用中的重复劳动、跨模块协调和规则密集型流程。
3. 区分哪些问题应该用确定性 Workflow 解决，哪些问题适合由受限 Agent 提供建议、解释或草案。
4. 调整架构，使核心业务、流程编排、Agent 建议和 UI 展示能够独立演进。
5. 在可靠底座上提炼 Repland 的 Agent + Workflow 创新体验。

## 文件索引

| 文件 | 内容 | 状态 |
| --- | --- | --- |
| `01-core-loop-baseline.md` | 当前本地核心链路、已实现事实和基础验收线 | 工作基线 |
| `02-traditional-workflow-audit.md` | 传统应用中的重复工作、痛点和确定性 Workflow 候选 | 第一版盘点 |
| `03-agent-workflow-candidates.md` | Agent、Workflow 与普通功能的边界及候选创新场景 | 探索假设 |
| `04-architecture-evolution.md` | 从当前 Android MVP 向可演进架构的调整方向 | 架构草案 |
| `05-exploration-log.md` | 后续每轮探索、验证、决策和否决记录 | 持续更新 |
| `06-overall-product-and-system-design.md` | 能力优先的端到端产品、前后端、视觉动效与 Agent/Workflow 设计及对抗性审查 | 探索设计稿 |
| `08-implementation-and-verification.md` | 已实现切片、Agnes 安全配置、测试结果、剩余能力和对抗性审查 | 实施记录 |
| `09-ui-redesign-competitor-research.md` | 针对当前粗糙 UI 的竞品取经、信息架构重做、渐进式创建、任务总览台、视觉与动效规范 | 新一轮重设计基线 |
| `10-component-level-ui-benchmark.md` | 卡片/列表行/时间块/抽屉/详情/空状态/动效的竞品对标和可实现规格 | 组件级重设计基线 |
| `11-frontend-refactor-verification.md` | 实际前端重构、视觉决策、功能保全、测试与对抗性审查 | 模拟器验收记录 |

竞品设计边界正式报告暂保存在 [`docs/COMPETITOR_DESIGN_BOUNDARIES.md`](../docs/COMPETITOR_DESIGN_BOUNDARIES.md)，后续如果它进入探索过程的工作版本，再在本目录建立链接或迁移记录。

> 注意：`09-ui-redesign-competitor-research.md` 是对当前 UI 工作面的重设计基线。它不否定已有领域模型、数据和测试，但会覆盖此前“解释型首页、全量任务卡、全量创建表单”的视觉与交互安排。
