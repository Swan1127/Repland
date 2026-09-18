# Repland AI 开发交接

更新日期：2026-09-18
适用范围：后续维护本仓库的开发者或 AI Agent。

本文记录当前实现事实、已经解决的问题、尚未开始的工作和不可突破的产品边界。它不是对外隐私声明；对外行为以当前构建、`CONTEXT.md`、`README.md` 与 `docs/PRIVACY.md` 为准。

## 先读什么

按以下顺序建立上下文，避免把计划当作已经交付的功能：

1. `CONTEXT.md`：术语、产品规则和状态语义的来源。
2. `README.md`：项目入口、当前能力与构建方式。
3. 本文：本次本地 Agent 实现、BYOK 决策和下一步。
4. `docs/AGENT_AND_AI_INTEGRATION_PLAN.md`：正式联网 AI 的总体路线。
5. `docs/adr/0001-bounded-ai-advisor.md`：类型化契约和数据最小化的 ADR。
6. `docs/EXTENSION_READINESS.md`、`docs/PRIVACY.md`：联网前的安全、隐私和发布门槛。
7. `docs/TESTING.md`、`docs/RELEASE.md`：回归、签名与内测要求。

## 快照

- Android 版本：`0.1.0` / `versionCode 1000`；`internal` 变体的版本名后缀为 `-internal`。
- 技术栈：Kotlin、Jetpack Compose、Room、Coroutines；最低 API 24，目标 API 36。
- 当前发布边界：本地优先、无账号、无云同步、无分析/崩溃 SDK、无 `INTERNET` 权限。
- AI 事实：本地受限 `PlanningAgent` 已实现；运行时注入的是 `NoOpAiAdvisor`，没有真实模型调用。
- 工作树提醒：当前仓库有大量尚未提交的 Android 与文档改动。继续工作时只编辑任务相关文件；不要用 `git reset --hard`、`git checkout --` 或其他会覆盖工作树的操作。

## 不可违反的产品规则

| 规则 | 实际含义 |
| --- | --- |
| 用户是唯一执行者 | AI、规则和系统不能自动完成、延期、取消、替换任务或写入执行结果。 |
| 初始优先级受保护 | AI 不能修改用户最初选择的优先级；手动排序也不能被 AI 静默推翻。 |
| 草案不是当前计划 | 任何建议或重排都只能形成可编辑、可放弃的草案；确认后才创建新的计划版本与提醒。 |
| 硬约束优先 | 课程、休息、固定任务、用户锁定段与不可避免任务不可被普通排程覆盖。 |
| 历史追加、更正优先 | 执行反馈的更正创建新记录；旧记录保留，向 AI 提供的只应是更正后的有效反馈。 |
| 本地必须可用 | AI 关闭、服务不存在、断网、超时或响应无效时，使用本地确定性规划，不丢输入、不改当前计划。 |
| 最小化与确认 | 远程能力只能发送本次请求所需数据，并先展示预览、取得同意。 |

## 已完成：本地核心闭环

以下能力已经进入 Android 本地实现，而不只是文档计划：

- 任务、状态、执行反馈和更正日志；Room 迁移链已更新至 `MIGRATION_13_14`。
- 重复时间约束、单日例外、PDF 课表导入预览与确认。
- 30 分钟粒度、30 天窗口的确定性排程；硬约束、容量和锁定段检查。
- 计划草案的编辑、确认、丢弃、版本化历史与恢复；未确认草案不会创建提醒。
- 本地提醒、每日回顾、画像证据、本地 JSON 导出和明确确认后的本地数据清除。
- Compose 的今日、任务、时间、我的页面及相应 ViewModel/Room Repository。

主要位置：

```text
android/app/src/main/java/com/swan1127/repland/
├── data/room/       # Room 实体、DAO、Repository、迁移
├── data/importer/   # PDF 课表导入
├── domain/model/    # 任务、约束、计划、AI 契约和 Agent
├── reminders/       # 仅为已确认计划安排本地提醒
└── ui/              # Compose 页面与 ViewModel
```

## 已完成：受限 PlanningAgent

### 已解决的问题与做法

| 问题 | 解决方式 | 主要代码 |
| --- | --- | --- |
| Compose 直接构建 AI 请求或触碰网络 | 请求构建、调用、校验和降级移动到 domain `PlanningAgentWorkflow`；UI 只驱动状态和展示结果。 | `domain/model/PlanningAgent.kt`、`ui/ai/PlanningAgentViewModel.kt` |
| AI 请求范围过大 | `AiRequestFactory` 仅构造当前任务/受影响任务、相关硬约束、已确认工作段和更正后的反馈；使用请求内 reference，不发送 Room ID。 | `domain/model/AiAdvisorContract.kt` |
| AI 输出越权 | `AiAdviceValidator` 检查契约版本、请求 ID、响应类型、长度、任务范围、重复/重叠时间段、硬约束、锁定段、不可避免任务和手动顺序。 | `domain/model/AiAdvisorContract.kt` |
| 无服务时 AI 页面失效 | `NoOpAiAdvisor` 返回 `SERVICE_NOT_CONFIGURED`；Agent 产生本地建议/草案和可见失败原因。 | `ReplandApplication.kt`、`PlanningAgent.kt` |
| AI 草案直接替换计划 | Agent 没有 Repository 写能力；`PlanViewModel` 仍要求用户编辑与确认后才持久化。 | `PlanningAgent.kt`、`ui/plan/PlanViewModel.kt` |
| 难以回归边界 | 增加 Agent 单元测试、契约测试及 Compose/UI 工作流测试。 | `src/test/.../PlanningAgentWorkflowTest.kt`、`src/test/.../AiAdvisorContractTest.kt`、`src/androidTest/.../CoreWorkflowUiTest.kt` |

### 支持的请求与状态机

请求仅限：任务理解、难度/时长、任务拆分、排序解释、重规划和每日总结。

```text
Idle
  → PreparingContext
  → AwaitingConsent
  → PreviewingRequest
  → RequestingAdvice
  → ValidatingAdvice
  → ShowingAdvice / ShowingDraft
  → UserAccepted / UserDismissed / FailedFallback
```

`ShowingDraft` 永远不是当前计划。`FailedFallback` 仍可使用本地确定性规划。技术审计接口仅表达请求类型、契约版本、结果码、耗时和本地校验结果，默认实现不保存原始正文。

### 当前联网状态

当前实现**没有**网络适配器：

- `ReplandApplication.kt` 注入 `NoOpAiAdvisor`。
- `AndroidManifest.xml` 不声明 `android.permission.INTERNET`。
- `verifyOfflineMvpBoundary` 会阻止 `INTERNET`、`HttpURLConnection`、OkHttp 与 Retrofit 标记进入主代码。
- APK、Gradle、日志、导出与 Room 设置中都不能出现模型供应商 Key。

这既是安全边界，也是回归基线。不要为了让模型“先跑起来”而直接替换主构建的 Advisor。

## 已决策、尚未实现：DeepSeek BYOK 个人测试

这是一个只供个人开发验证的例外，不是当前 MVP、`internal` 或 `release` 的能力。

| 项目 | 已确认决策 |
| --- | --- |
| 构建隔离 | 将来创建独立 `byokDebug`；`debug`、`internal`、`release` 不暴露 BYOK 设置、Key 或联网实现。 |
| 模型服务 | DeepSeek，使用 OpenAI 兼容 API；固定批准的中国大陆端点 `https://api.deepseek.com`，不允许任意 Base URL。 |
| 用户输入 | 仅用户自己的 DeepSeek API Key 和模型名；不建立 Repland 用户账号。 |
| Key 生命周期 | Android Keystore 加密保存；设置页只显示“已配置”；关闭 BYOK、重置 AI 或卸载时删除；不得写入 Room、日志、JSON 导出或备份。 |
| 数据路径 | `byokDebug → DeepSeek`，不经过 Repland 服务端。当前任务相关内容仍必须每次预览并确认。 |
| 本地留存 | Repland 不持久化原始请求、模型回复、API Key 或 BYOK 审计正文。供应商侧处理与删除遵循用户自己的 DeepSeek 账户和条款。 |
| 使用限制 | 用户确认已满 14 周岁；不得发送密码、证件、金融账户、医疗详情、精确位置、未获同意的第三方信息、国家/商业秘密。 |
| 防误用 | 默认每设备每天 20 次、每分钟 3 次、单次 20 秒；断网、Key 无效、限流或模型失败不自动重试，直接回退本地规划。 |

### BYOK 开始编码前的门槛

1. 用实际 DeepSeek 开放平台账户核对适用 API 条款、训练/优化控制选项、数据删除路径和最新数据处理说明；不能把未证实的“不训练/不留存”写入文案。
2. 增加单独的产品决策记录，说明这是开发构建例外；不得削弱正式版本的服务端网关方案和离线边界。
3. 起草仅适用于 `byokDebug` 的隐私说明和首次同意文案，写明 DeepSeek 是直接接收方、Repland 不代管其留存记录、关闭 Key 不会撤回已发送内容。
4. 在分发前提供真实且可收信的支持邮箱；目前尚未给出具体地址，文案中只能使用占位符，不能发布。
5. 为构建隔离、Keystore 清除、域名白名单、零 Key 日志/导出、限流、超时、失败降级、未成年人拦截和逐次预览补充自动化测试。

不要把这一实验方案误当作正式联网方案。未来正式内测仍需要中国大陆网关、服务端密钥管理和轮换、匿名令牌或账号策略、预算/审计/删除流程、供应商书面数据保证及更新后的隐私同意。

## 最近验证结果

在本次本地 Agent 实现完成后，下列命令均已通过；继续修改后应重新运行受影响项，并在提交前运行完整集合：

```powershell
.\android\gradlew.bat :app:assembleDebug
.\android\gradlew.bat :app:assembleInternal
.\android\gradlew.bat :app:testDebugUnitTest
.\android\gradlew.bat :app:connectedDebugAndroidTest
.\android\gradlew.bat :app:verifyOfflineMvpBoundary
```

`connectedDebugAndroidTest` 当时在 `emulator-5554` 通过 17 项测试。不要在包含真实用户数据的设备上运行仪器测试。

## 推荐的下一步

### 近期：保持本地 MVP 可发布

1. 重新运行上面的五项验证，并按 `docs/TESTING.md` 做真机升级、提醒、时区、PDF 和数据清除回归。
2. 审查现有未提交工作树，按功能拆分提交；不要把签名凭据、导出数据或测试生成物加入版本控制。
3. 持续补充本地 Agent 的边界测试，而不是先接入网络。

### 若要实现 `byokDebug`

1. 先完成上一节的五个门槛和对应文档更新。
2. 添加隔离的 Gradle 构建变体，确保正式变体的 Manifest 和依赖中仍没有网络实现。
3. 实现只在该变体可用的 Key 存储、固定端点 DeepSeek 适配器、请求超时/限流和 `AiAdvisor` 错误映射。
4. 保持 `AiAdvisorRequest` / `AiAdvisorResponse` 契约与本地 `AiAdviceValidator` 不变；不能把供应商原始回复直接送入 UI。
5. 先做无 Key、错误 Key、断网、超时、无效 JSON、越权草案、清除 Key 和关闭 AI 的测试，再允许任何模型请求。

### 若要做正式联网 AI

回到 `docs/AGENT_AND_AI_INTEGRATION_PLAN.md` 阶段 2：优先部署并审计受控网关，模型 Key 只在服务端密钥管理系统中存在。不要复用个人 BYOK 路径作为正式内测架构。

## 给后续 AI Agent 的工作方式

- 先读取本文“先读什么”中的文件，再改代码；不要根据 UI 表象臆测权限边界。
- 将类型化契约、输出校验、用户确认和本地降级视为不可删的防线。
- 任何涉及网络、账号、外部 SDK、遥测、密钥或数据导出的改动都先检查 `docs/EXTENSION_READINESS.md` 与 `docs/PRIVACY.md`。
- 只做当前任务必需的最小修改；工作树不干净是已知状态，必须保留无关改动。
- 提交前至少运行相关单元测试；变更 Room、提醒、Compose 流程或联网边界时运行完整验证集。
