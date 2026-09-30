# 架构演进草案

> 状态：架构草案  
> 目的：为“先稳定本地核心，再引入 Agent/Workflow”提供可渐进演进的结构，不要求一次性重写当前应用。

## 1. 当前架构的主要问题

当前项目已经有 Domain、Room Repository、ViewModel 和受限 PlanningAgent，但仍存在几处会阻碍后续演进的耦合：

### 1.1 UI 层承担流程编排

`ReplandApp.kt` 集中了导航、任务详情、时间约束、计划草案、AI 请求预览、反馈弹窗、每日回顾和数据管理的跨模块协调。这样做适合快速完成 MVP，但会带来：

- 同一业务动作从多个入口触发时行为容易不一致。
- UI 需要知道计划输入如何拼装、哪些对象要保护、何时要重新生成草案。
- Agent 加入后，UI 容易变成请求构建、权限判断、结果解释和持久化的混合层。

### 1.2 领域规则和应用流程边界不够清晰

当前 `Plan`、`TaskRepository`、`PlanViewModel` 和 Room Repository 各自拥有部分规则。后续应明确：

- Domain Core：规则、状态转换、约束校验和纯计算。
- Application Workflow：跨对象编排、触发原因、草案生命周期和提交事务。
- Adapter：Room、提醒、文件、AI 服务等外部实现。
- UI：展示状态、收集输入、发出意图，不编排业务流程。

### 1.3 事实变化和建议变化需要更强区分

当前已经有计划草案与当前计划的区分，但随着 Agent 增加，需要在架构层明确三类数据：

1. **Fact**：用户确认或系统确定产生的事实，例如任务状态、执行日志、计划版本。
2. **Proposal**：本地规划器或 Agent 生成的建议，例如草案、拆分建议、取舍方案。
3. **Decision**：用户确认或明确授权的选择，例如确认草案、接受当前冲突方案。

Agent 只能生成 Proposal；Workflow 负责把 Decision 转换成 Fact。

## 2. 建议的目标结构

```text
ui/
  screens + ui-state + user-intent

application/
  workflows/
    CaptureTaskWorkflow
    GeneratePlanDraftWorkflow
    ResolveConflictWorkflow
    ReviewExecutionWorkflow
    ConfirmPlanWorkflow
    RestorePlanWorkflow
    DailyReviewWorkflow
  queries/
  workflow-state/

domain/
  model/
  policies/
    TaskLifecyclePolicy
    PlanFeasibilityPolicy
    ConstraintPolicy
    ReminderPolicy
  planners/
    DeterministicPlanner
  ports/

adapters/
  room/
  reminders/
  importer/
  agent/
  export/
```

这不是要求立刻按目录重构，而是后续代码新增时应遵循的方向。当前已有 `domain/model`、`domain/ports` 和 `data/room`，可以从 application workflow 层渐进补齐。

## 3. 建议引入的核心抽象

### 3.1 Workflow Command

表示用户或系统要启动的业务流程，例如：

```text
GeneratePlanDraft(reason = FEEDBACK_RECORDED, affectedTaskIds = [...])
ResolveConflict(conflictId, choice = SPLIT_TASK)
ConfirmPlanDraft(draftId, authorization = USER_CONFIRMED)
```

Command 不应直接由 UI 拼装领域细节，而应由 Workflow 读取当前事实形成完整上下文。

### 3.2 Workflow Result

每个流程返回结构化结果，而不是让 UI 猜数据库变化：

```text
WorkflowResult
  status: SUCCESS | NEEDS_USER_DECISION | FAILED | FALLBACK
  factsChanged: [...]
  proposal: optional
  affectedObjects: [...]
  warnings: [...]
  nextActions: [...]
```

### 3.3 Proposal / Decision / Fact

建议在设计层先确立这三类概念，即使第一版仍然使用现有 Room 表：

- Proposal：有来源、版本、范围、生成时间和过期条件。
- Decision：有用户、时间、授权范围、选择结果和关联 Proposal。
- Fact：有创建来源、关联 Decision 和可追溯历史。

未来若需要持久化，可再根据真实场景引入 `workflow_runs`、`proposals`、`decisions` 或事件表；不要一开始为了“Agent 架构”批量增加抽象表。

### 3.4 Trigger / Invalidation Reason

统一记录“为什么需要重新规划”：

- `TASK_CREATED`
- `TASK_EDITED`
- `TIME_CONSTRAINT_CHANGED`
- `FEEDBACK_RECORDED`
- `DEADLINE_RISK_CHANGED`
- `MANUAL_ORDER_CHANGED`
- `PLAN_RESTORED`
- `AGENT_REQUESTED`

同一个计划生成 Workflow 不应因入口不同而有不同规则，只应根据触发原因决定影响范围和文案。

## 4. 渐进式重构顺序

### 阶段 0：只补测试和流程地图

- 为任务创建、草案确认、反馈、回退、提醒同步画出输入/输出/副作用。
- 明确当前代码中每个规则的真实归属。
- 不改数据模型，不引入新 Agent 能力。

### 阶段 1：提取应用层用例

- 先提取 `GeneratePlanDraftWorkflow`、`ConfirmPlanWorkflow` 和 `ReviewExecutionWorkflow`。
- ViewModel 只调用 Workflow 并映射 `WorkflowResult`。
- 先保持现有 Repository 和 UI 不变，减少迁移风险。

### 阶段 2：统一流程状态与触发原因

- 让任务、时间、反馈变化都进入同一个“计划需要更新”的入口。
- 统一草案过期、冲突、不可行和待确认的状态表达。
- 补充 Workflow 单元测试和跨 Repository 集成测试。

### 阶段 3：将 Agent 接入 Proposal 端口

- Agent 只能实现 `Advisor` 类端口。
- Workflow 负责请求预览、上下文最小化、超时、验证、降级和用户确认。
- Agent 输出不能拥有 Repository 写权限。

### 阶段 4：评估是否需要流程持久化

- 只有当用户需要恢复中断流程、查看决策来源或跨设备继续时，才引入 WorkflowRun/Proposal/Decision 持久化。
- 单次请求和短流程优先使用内存状态，避免过早复杂化 Room migration。

## 5. 架构守则

- 核心规划器不依赖 Android、Compose、网络或模型供应商。
- Workflow 可以调用 Domain 和 Ports，但不能把领域判断散落在 UI 回调里。
- Agent 没有直接写入任务、计划、提醒和画像的能力。
- 任何自动流程都必须有触发原因、影响范围、失败结果和恢复动作。
- 一个 Workflow 的副作用必须可测试、可重复执行或具备幂等策略。
- 用户确认前只能创建 Proposal，用户确认后才产生 Fact。
- 结构化数据优先于自然语言；Agent 解释是视图，不是事实来源。

## 6. 尚未决定的架构问题

以下问题先记录，不在本轮擅自定案：

- 是否最终采用完整事件溯源，还是在现有版本/日志模型上增加关联字段。
- Workflow 状态是否需要持久化，还是只保留短时 UI 状态。
- 真实 Agent 接入采用服务端网关、设备侧模型还是两者并存。
- 是否引入统一领域事件总线，还是先采用显式 Workflow 调用。
- 计划版本、执行日志和画像证据之间最终采用何种查询模型。

