# Repland 账号交接：功能、前端、验收与后续任务

更新：2026-10-06（Asia/Shanghai）。这是下一个账号的统一接手入口，不需要原聊天历史。盘点对象为源码 `5ea3338cc1e385f243b4f3dab706e3c54deb733c`，分支 `codex/placement-numeric-integrity`；本文件之后的文档提交不改变该构建来源。

## 1. 先读：当前到底完成到了哪里

- 最新源码版本：0.1.29 / 1029。本次完整自动化回归：267 项 Android 设备测试、127 项单元测试，失败 / 错误 / 跳过均为 0。设备全套 XML 用时 251.337 秒。
- 当前模拟器用户包仍为 **0.1.28-internal / 1028**，包名 `com.swan1127.repland.current`。本次不做原位升级，不修改用户任务、不提取 API 密钥、不发送真实模型请求。0.1.29 自动化通过不等于已经安装交付。
- 前次全套因额度中断，仅产生部分结果，不能算通过；本次重新跑完 267 项才作为完整结果。中断历史保留，不以本次成功抹除。
- 原产品计划 **尚未全部完成**。本次用户要求：先完成系统回归，再将后续任务及每项功能与前端记在一个文档中；后续功能暂不继续实施。
- 技术路线：Kotlin / Jetpack Compose / Material 3 / Room，本地优先、单设备。默认系统深浅色，非 Web 项目。
- 旧版保留：UI 改造前 `v0.1.16`，已验收安装版 `v0.1.28`；旧标签不移动。各批次见 [交付进度](DELIVERY_PROGRESS.md)。本地 APK 在 `android/app/build/reports/retained/`，不会随 Git 克隆自动得到。
- “自动化通过”只表示现有断言通过，不代表所有人工工作流、真实服务或真实手机均通过；本文件状态不能扩大为正式发布承诺。

## 2. 用户要求与不能改变的产品规则

权威实施计划：[PRODUCT_DELIVERY_PLAN_2026-10-03.md](PRODUCT_DELIVERY_PLAN_2026-10-03.md)；产品规则：[REPLAND_IDEA.md](../REPLAND_IDEA.md)、[CONTEXT.md](../CONTEXT.md)。冲突时以用户最新明确要求及交付计划中的范围修订为准，不沿用旧参与模式方案。

1. 任务页右上角是“自动排序”，只影响列表顺序，不移动当前日程；语音放在新增任务流程中。
2. 助手的“制定计划”独立在输入框上方：读取已有任务、重新排序并排程，生成预览；不要求先排序，不读取未发送文字。确认前不替换正式计划。
3. “安排今天 / 新增事项”仍是“说出你的安排”输入区域的下属工作流，不只是添加提示词前缀。新增事项默认只创建任务；安排今天默认只处理单日，跨日影响不能静默发生。
4. 课程、固定事项、硬保护休息、锁定和已经开始的安排优先于软排序。普通重排保留手动顺序；明确制定计划可以建议新顺序，但不授权移动保护段。
5. 未知时长、日期、优先级等不由模型静默填成用户事实。非法原数字保留并明确校验，不能把 `-30` 改成 `30`，不能把 `08:99` 进位成另一时间。
6. 确认必须经过真实事务、过期校验、防重复提交；失败保留输入与原计划，成功回执来自实际写入。提醒只跟随已确认计划，草案不产生提醒。
7. 计时结束不自动完成任务。状态、累计进度与反馈来自用户明确操作；本轮结束与整项完成不同。
8. AI 是有限业务白名单，不是无限控制整个程序。模型不直接改数据库、全局设置、任务状态或用户优先级。
9. **不做 7 天用户试用、用户招募、留存实验或类似研究**，不作为完成门槛。账号、云同步、多端协作、外部日历双向同步仍延后。
10. 技术实施收尾后才做网上竞品与工作流产品评估；只写建议，不自动改 UI 或业务。
11. 用户曾要求额度剩 1% 时保存进度并非强制关机。账号更换后如继续该授权，按真实额度核对，不沿用旧账号百分比；禁止 `/f`、额度购买或重置。此次新要求的范围是回归与交接，不新建循环任务。

## 3. 阅读代码的地图

下面 `R` 指 `android/app/src/main/java/com/swan1127/repland/`，`A` 指 `android/app/src/androidTest/java/com/swan1127/repland/`，`U` 指 `android/app/src/test/java/com/swan1127/repland/`。表中源码路径相对 R，测试类在 A，特别写“单元”的在 U。使用 `rg -n '函数名' 路径` 定位，避免行号随修改失效。

| 层 / 页面 | 入口与职责 |
| --- | --- |
| 应用与依赖 | `MainActivity.kt`、`ReplandApplication.kt` 的 `AppContainer`；注入 Repository、读取服务、操作服务、Provider；数据库文件 `repland.db`。 |
| 页面总协调 | `ui/ReplandApp.kt`：`ReplandApp`、`AppTab`；底栏今日 / 任务 / 助手 / 我的。时间设置是二级页面，不是第五个底栏。 |
| 今日与日周月 | `ui/ReplandApp.kt::TodayScreen`、`ui/schedule/TimelineDashboard.kt`、`ScheduleHorizonViews.kt`、`DailyDesk.kt`。 |
| 任务 | `ui/ReplandApp.kt::TasksScreen/TaskDetailScreen`、`ui/TaskPageAppBar.kt`、`ui/components/TaskExperienceComponents.kt`。 |
| 助手 | `ui/agent/AgentCenterScreen.kt`（自由输入、有限操作、可编辑候选），`ui/components/AssistantSheet.kt`（任务详情的协助输入）。两种入口不能混为一套已验收能力。 |
| 计划 | `ui/plan/PlanViewModel.kt`，`ReplandApp.kt::PlanDraftDialog/PlanSegmentEditorDialog/PlanOverviewDialog`。 |
| 我的 / 时间 | `ReplandApp.kt::MineScreen/TimeScreen`，各设置与编辑 Dialog。 |
| 共用业务 | `domain/model/PlanningReadService.kt`、`PlanningOperationService.kt`、`PlanningRevision.kt`、`PlacementValidator.kt`、`UserNumericInput.kt`。 |
| 存储 | `data/room/Room*Repository.kt`、DAO / Entity / Codec；`ReplandDatabase.kt` 当前 schema v20，AppContainer 显式注册 1→20 迁移，不可改为破坏性迁移。 |
| 网络 | `android/app/src/remote/java/com/swan1127/repland/data/ai/CompatibleChatTransport.kt`、`CompatibleAiAdvisor.kt`、`CompatibleArrangementAdvisor.kt`；仅 QA / internal 编入。 |
| 凭据 | `data/security/SecureAiProviderConfigRepository.kt`；Android Keystore 本机保存。不要读取、复制、提交用户密钥。 |

状态说明：**已接入**＝前端当前有连接到业务的入口；**组件存在 / 入口缺失**＝有代码但不能当成当前用户可用功能；**待外部验证**＝实现存在但缺真实 API / 系统 / 真机证据。表中测试是定位相关断言的索引，不声称每个分支都有端到端覆盖。

## 4. 全功能与对应前端清单

### 4.1 导航、今日与轨道

| ID | 当前功能 / 用户入口 | 前端代码 | 业务 / 数据关联 | 状态、验证与边界 |
| --- | --- | --- | --- | --- |
| N01 | 四页底栏切换 | `ui/ReplandApp.kt::ReplandApp/TabIcon` | 各 ViewModel 共同观察同一 Room 数据 | 已接入；`NativePageNavigationUiTest`。切换不是复制四套任务。 |
| N02 | 我的 / 助手 → 时间设置 → 返回原页 | `ReplandApp/TimeScreen`，`timeReturnTab`、原生 Back | TimeViewModel | 已接入；`NativePageNavigationUiTest`；详情返回也保留来源。 |
| N03 | 读取失败提示、重试、未可信禁写 | `DataReadNotice`、各页面 / Dialog | `ui/state/RecoverableRead.kt`，各读取代次 | 已接入；`ReadFailureWorkflowUiTest`、`ContextReadWorkflowUiTest`、`SessionReadIntegrityTest`。不是把异常显示成空数据。 |
| T01 | 当前 / 下一项、开始执行、完整日程入口 | `TodayFocalOverview`、`NowCard`、`DailyDesk` | `TodayFocus`、TaskViewModel、PlanViewModel | 已接入；`TodayFocusUiTest`。日程时间标签不等于真实执行状态。 |
| T02 | 今日待安排、待确认与任务库跳转 | `TodayScreen/PendingConfirmationTaskCard` | `TaskPlanMembership`、确认计划时段 | 已接入；`TaskPlanMembershipUiTest`；过去时段不会自动完成任务。 |
| T03 | 总览 / 日 / 周 / 月切换、选择日期 | `TodayScreen`、`WeekScheduleView/MonthScheduleView` | `ScheduleTimeline`、同一课程 / 例外 / 确认计划 | 已接入；`TimelineDashboardUiTest`、单元 `ScheduleTimelineTest`；月格 `month-date-$date`。全面显示矩阵待补。 |
| T04 | 合轨 / 分轨、时间块、当前时间展示 | `TimelineDashboard/DayTimelineGrid/MergedTimelineGrid` | `RhythmTracks.project` | 已接入；`TimelineDashboardUiTest`。轨道展示并不取消跨轨硬约束。 |
| T05 | 新建命名轨道、重名反馈、跨页名称一致 | `TrackStrip`、轨道创建输入 | PlanViewModel.saveTracks → PlanningWorkspace | 已接入；`InteractionWorkspaceRepositoryTest`、单元 `RhythmTracksTest`。稳定 ID 不随位置改写。 |
| T06 | 事项库：分类、选任务 / 轨道 / 开始时间、放入安排 | `EventLibrarySheet` | `TaskPlacementPolicy` → TodayScreen → PlanViewModel.placeTask → RoomPlanRepository | 0.1.29 已统一检查与实际时长，真实 QA 跨页保存通过；`PlacementNumericUiTest/PlacementNumericWorkflowUiTest`、`PlacementRepositoryTest`。仍未升级当前用户包。 |
| T07 | 总预计时长与本次安排时长分别显示 | `EventLibrarySheet` | `domain/model/UserNumericInput.kt::TaskPlacementPolicy` | 已接入；既有规则未知总时长本次 30 分钟；有效已知值按本次 15–240 分钟取块，不更改总预计事实；300 分钟任务实际写 240 分钟专项通过。 |
| T08 | 日轨道新增课程：手动时间 / 待拖入 | `CourseComposerDialog/PendingCourseBlock` | TimeViewModel.saveWeeklyBlock | 已接入；`PlacementNumericUiTest`。课程 15–360 分钟，开始与分钟独立校验，跨日拒绝；拖入后的后台失败恢复仍需人工复核。 |
| T09 | 点击时间块查看内容、备注、阶段、任务详情 | `TimelineEntryDetailSheet` | TimelineEntry；实际 taskId / 时间约束 ID | 已接入；`TimelineDashboardUiTest`；时段阶段不自动改任务状态。 |
| T10 | 长按拖动、提前 / 推后 15 分钟、精确修改时间 | `TimelineBlock/TimelineEntryDetailSheet/TimelineTimeEditorDialog` | PlanViewModel.movePlacement 或 TimeViewModel.save* | 已接入；`PlacementRepositoryTest`、时间轴 UI 相关测试。仓库仍检查约束；组件提前关闭与异步失败回执一致性列为后续审查，不在本次声称修复。 |
| T11 | 时间块编辑详情与备注 | `TimelineEntryDetailSheet` → 根 onEditTimelineEntry | 任务、每周块、单日例外按 ID 分流 | 已接入；`TaskEditorWorkflowUiTest/TimeSettingsWorkflowUiTest`。 |
| T12 | 移出安排（不删除任务）、进入专注 | `TimelineEntryDetailSheet` | removePlacement；startSession(segmentId) | 已接入；`PlacementRepositoryTest/ExecutionWorkflowUiTest`。 |
| T13 | 今日复盘：安排 / 待确认 / 反馈 / 进度 / 下一项 | `DailyReviewDialog` | `DailyReviewSummary`、执行日志 | 已接入；`TaskFeedbackWorkflowUiTest` 等；只汇总事实，不推断完成。 |
| T14 | 复盘中的 Agent 总结 | `DailyReviewDialog/PlanningAgentOutcomeCard` | PlanningAgentViewModel.beginDailySummary | 已接入；契约 / 本地工作流有测试，真实服务总结质量待验收。 |

### 4.2 任务收集、列表、详情与反馈

| ID | 当前功能 / 用户入口 | 前端代码 | 业务 / 数据关联 | 状态、验证与边界 |
| --- | --- | --- | --- | --- |
| K01 | 任务页浮动新增 / 空态添加 / 今日添加 | `TaskCaptureButton`、`TaskCaptureSheet` | TaskCaptureViewModel、RoomTaskCaptureRepository | 已接入；`TaskCaptureSheetUiTest/TaskCaptureViewModelTest/TaskCaptureRepositoryTest`。用户当前包 28 为旧保留版。 |
| K02 | 简短录入、可选类别 / 优先级、稍后补详情 | `CaptureInputStep/CaptureDetailsStep` | TaskCaptureDraft → TaskDraft | 已接入；未知信息不强制填满。 |
| K03 | 保存到任务库 / 今日清单、估时、自定义时长、截止日期 | `CaptureIntentStep/CaptureDurationStep/CaptureDeadlineStep`、`TaskDatePickerDialog` | `TaskCaptureDraft.toTaskDraft` | 已接入；`CaptureNumericWorkflowUiTest`、单元 `TaskCaptureDraftTest`。今日清单不等于占用一个具体时段。 |
| K04 | 关闭保留草稿、继续填写、确认放弃草稿 | `TaskCaptureSheet` 关闭 / 放弃确认 | 草稿持久化、150ms 暂存、真实保存回执 | 已接入；Activity 重建与历史 28 原生冷启动有证据；不能保证尚未落盘的任意瞬间输入在强杀后保留。 |
| K05 | 软键盘与抽屉返回：先收键盘，再关抽屉 | `TaskCaptureSheet` 的 IME bridge / BackHandler | Material 3 原生 sheet；保存中拦截关闭 | 已接入；`TaskCaptureSheetUiTest`。不是自定义伪物理手势。 |
| K06 | 语音识别 → 可编辑文字 → 任务草稿 | 根语音 Dialog / speechLauncher，新增页语音入口 | Android RecognizerIntent，TaskCaptureViewModel | 已接入；设备语音服务可联网，文字先检查；真实识别服务 / 权限待外部验证。 |
| K07 | 总览 / 待安排 / 今天 / 逾期 / 已完成筛选 | `TasksScreen/TaskCenterView` | TaskPlanMembership、当前确认时段、同一 taskOrder | 已接入；`TaskPlanMembershipUiTest`。源码“已完成”筛选包含所有非活动状态，应后续核对取消 / 替换的展示语义，不把标签当成纯 COMPLETED。 |
| K08 | 需要处理 / 今天 / 待安排 / 更晚分组、查看全部 | `TaskControlCenter/TaskRow` | 分组只筛选，保留共用顺序 | 已接入；`PlacementNumericWorkflowUiTest` 验证真实滚动 / 展开后的跨页卡片。默认每组前三项，不是任务丢失。 |
| K09 | 任务行开始、查看已安排日期与时段 | `TaskRow/TaskStatusButton` | startTask 状态操作、确认时段摘要 | 已接入；开始任务状态与启动持久化专注会话是不同入口。 |
| K10 | 右上角自动排序、候选依据 / 上下调整 / 应用 | `TaskPageAppBar/PlanDraftDialog/DraftTaskOrderRow` | LocalPriorityRanker，orderOnly，持久化顺序 | 已接入；`TaskPageAppBarUiTest/PlanDraftPreviewUiTest`、单元 `LocalPriorityRankerTest`；不移动当前时段。 |
| K11 | 撤销最近一次排序 | `TaskPageAppBar` “撤销排序” | PlanViewModel.undoTaskOrder，workspace | 已接入；`PlanningWorkspaceRepositoryTest`；单步与版本有效范围，不是任意历史撤销。 |
| K12 | 任务详情：说明、时间、优先级、进度、历史 | `TaskDetailScreen/DetailSection/ExecutionLogCard` | TaskViewModel、任务日志流 | 已接入；`TaskEditorWorkflowUiTest/TaskFeedbackWorkflowUiTest`。 |
| K13 | 编辑已有任务：标题、备注、类别、优先级、天数、时长、日期 | `TaskEditorDialog/TaskDateField` | TaskViewModel.saveTask，稳定 targetId，实际 receipt | 已接入；`TaskEditorIntegrityTest/TaskEditorWorkflowUiTest`。天数 1–30、总时长 1–1440 或未知；失败留原字段，不转为新建。 |
| K14 | 完成整项、恢复已完成 / 已取消任务 | `DetailActions` | completeTask / restoreTask，RoomTaskRepository | 已接入；`TaskLifecycleRepositoryTest`。当前直接完成按钮不使用旧 CompleteTaskDialog 表单，勿按旧组件推断行为。 |
| K15 | 部分完成（累计进度）、本次反馈、实际耗时可留空 | `PartialCompletionDialog/ExecutionFeedbackDialog/FeedbackDialog` | TaskMutation、TaskFeedback、真实事务 | 已接入；`TaskFeedbackIntegrityTest/TaskFeedbackWorkflowUiTest`、单元 `FeedbackFormValidationTest`；累计与增量不可混淆。 |
| K16 | 延期原因与主动查看重排建议 | `PostponeTaskDialog/PostponementGuidance` | postponeTask → 反馈日志 / 用户主动预览 | 已接入；`TaskFeedbackWorkflowUiTest`；不自动覆盖计划。 |
| K17 | 取消任务的明确确认 | `ConfirmTaskStatusDialog` | cancelTask，任务 / 未来时段生命周期事务 | 已接入；`TaskLifecycleRepositoryTest/TaskFeedbackWorkflowUiTest`。 |
| K18 | 替换任务并保留原历史 | `TaskEditorDialog` 的替换上下文 | replaceTask，旧任务 REPLACED，新任务 ID | 已接入；`TaskLifecycleRepositoryTest/TaskFeedbackIntegrityTest`。 |
| K19 | 更正既有执行日志 | `CorrectExecutionLogDialog/ExecutionLogCard` | taskId + correctedLogId 固定，追加更正 | 已接入；`TaskFeedbackIntegrityTest`；原记录消失 / 读取失败禁写，不改别的记录。 |
| K20 | 任务详情“打开对话”、理解 / 估时 / 拆分 / 重排建议 | `AssistantSheet/PlanningAgentOutcomeCard/EditableAiAdvice`、请求预览 Dialog | PlanningAgentViewModel / PlanningAgentWorkflow | 已接入；单元 `PlanningAgentWorkflowTest/AiAdvisorContractTest`。有限建议与可确认重排，不直接修改事实；真实全矩阵待补。 |

### 4.3 助手与计划预览

| ID | 当前功能 / 用户入口 | 前端代码 | 业务 / 数据关联 | 状态、验证与边界 |
| --- | --- | --- | --- | --- |
| P01 | 独立“制定计划” | `AgentCenterScreen` 顶部按钮，根 generatePlanDraft(reorder=true) | PlanningOperationService、PlanGenerator、PlanViewModel | 已接入；`AgentCenterUiTest/CoreWorkflowUiTest/PlanningOperationServiceTest`。目前按钮走本地规划，不等于该按钮直接用模型优化全局排程。 |
| P02 | 空任务引导、缺可用时间引导 | `AgentCenterScreen`，`PlanDraftDialog` 的补充入口 | 是否有活动任务 / AVAILABLE 约束 | 已接入；`AgentCenterUiTest/QuickAvailabilityTest`；未知时长仍需补齐。 |
| P03 | 最小可用时间卡、可选每周保存、重新预览 | `ui/plan/QuickAvailabilityDialog.kt` | saveAvailabilityAndGenerate → RoomTime / 规划服务 | 已接入；`QuickAvailabilityTest/TimeSettingsWorkflowUiTest`；保存时间不代表已确认新计划。 |
| P04 | “说出你的安排”输入、语音、生成候选 | `AgentComposer` | 本地 Interpreter 或 ArrangementAssistantViewModel | 已接入；`AgentCenterUiTest/ArrangementAssistantAccessTest`；本地解析能力有限，非通用聊天。 |
| P05 | 输入框下“安排今天” | `ComposerPreset/selectWorkflow` | 有正文则安排候选；空正文则已有任务单日规划 | 已接入；`PlanningOperationServiceTest/AssistantChangesRepositoryTest`；其他日期 / 保护段保留，实际边界由本地提交检查。 |
| P06 | 输入框下“新增事项”、批量收集 | `selectWorkflow/AgentProposalPanel` | CAPTURE_TASKS，保存批量任务事务 | 已接入；`AssistantSaveReceiptTest/AssistantChangesRepositoryTest`；默认不排入时段，非提示词前缀。 |
| P07 | 任务查询：未完成 / 今天 / 待安排 / 逾期，跳详情 | `agent-read-result` 结果区 | QUERY_TASKS → PlanningOperationService.query | 已接入；`AssistantReadOperationUiTest/PlanningReadServiceTest`；只读、本地事实，不复活已关闭任务。 |
| P08 | 排序解释 | `agent-read-result` 的依据 / 查看任务 | EXPLAIN_ORDER → LocalPriorityAssessment | 已接入；`AssistantReadOperationUiTest`；真实因子，不让模型编造分数。 |
| P09 | 模型意图“制定计划”路由 | `AgentCenterScreen::applyAdvice` | FORMULATE_PLAN → 同一本地制定入口 | 已接入；有限契约 / 历史真实单任务路由部分通过；多任务真实验收待补。不是远程全局最优解。 |
| P10 | 新增 / 已有候选区分，待决定区、删除候选 | `AgentProposalPanel/UndecidedProposalShelf/AgentDraftTimeline` | AgentTaskProposal、existingTaskId、workspace | 已接入；`AgentCenterUiTest/AssistantChangesRepositoryTest`；删除候选不删除正式任务。 |
| P11 | 候选修改时长 / 起止 / 轨道，重建保留原输入 | `AgentPlacementDialog` | stable proposalId，UserNumericInput、workspace | 已接入；`PlacementNumericUiTest`；5–720 分钟，校验与冲突保护；saved-state 测试不等于此弹窗真实冷启动已验收。 |
| P12 | 连续补充指令修改同一草案 | `AgentProposalPanel` 的 follow-up | CompatibleArrangementAdvisor、身份验证 | 已接入；`DebugAgnesArrangementAdvisorParserTest/ArrangementAssistantAdviceValidatorTest`（单元）；失败保留，不将修改当新增。真实多轮矩阵待补。 |
| P13 | 请求处理中取消、输入 / 配置变化使旧响应失效 | `agent-cancel-request`、onPromptChange | Job.cancel、requestVersion、providerRevision | 已接入；`AgentCenterUiTest/CompatibleTransportTest`；不能撤回已发送数据，取消后不能确认旧临时候选。 |
| P14 | 实际确认、成功数量回执、查看任务 / 安排 | `AgentProposalPanel`、`agent-save-receipt` | saveAssistantChanges / saveTasksAndPlace，Room 原子事务 | 已接入；`AssistantSaveReceiptTest/PlanningWorkspaceRepositoryTest`；异步失败不假成功，不破坏独立任务排序。 |
| P15 | 持久化助手正文 / 草案 / 修改指令 | `initialWorkspace/onWorkspaceChanged` | PlanningWorkspace、InteractionWorkspaceCodec | 已接入；`InteractionWorkspaceRepositoryTest`；日期 / 数据修订变化后旧草案禁止确认，需刷新。 |
| P16 | 规划预览：新旧时段 / 顺序、理由、多日展开 | `PlanDraftDialog/PlanDraftTimelinePreview/DraftDayTrackPreview` | PlanDraftReview、当前计划、protectedIds | 已接入；`PlanDraftPreviewUiTest`、单元 `PlanDraftReviewTest`；不隐藏清晨 / 深夜。 |
| P17 | 修改候选顺序、编辑分段、时长 / 日期 / 轨道 | `DraftTaskOrderRow/PlanSegmentEditorDialog` | PlanDraftEditor、saveDraftEdit(expected,updated,requestId) | 已接入；`DraftSaveIntegrityTest/DraftSaveWorkflowUiTest`；真实回执关闭、失败保留、原预览比较替换。 |
| P18 | 本次暂不安排、缺信息、容量缺口及处理入口 | `PlanDraftDialog` 未排入区 / defer 按钮 | UnscheduledReason / 剩余分钟；补任务 / 可用时间 | 已接入；`PlanDraftPreviewUiTest`、单元 `TimeConstraintPlannerTest`；复杂优先级 / 截止联合容量策略仍未完成。 |
| P19 | 确认计划、取消 / 保留关闭 / 恢复查看 | `accept-plan-draft`、保留预览入口 | acceptDraft / discardDraft、PlanningRevision、同一事务 | 已接入；`PlanningWorkspaceRepositoryTest/PlanningReadFailureTest`；确认前正式顺序与计划不变。 |
| P20 | 历史列表、恢复历史、清除当前计划、锁定 / 解锁段 | **`PlanOverviewDialog` 组件存在，但根 showPlanOverview 无置 true 入口** | restore / clearCurrentPlan / setSegmentLocked 已存在 | **后台与组件已有，当前前端不可达，待接通。** `PlanRestoreRepositoryTest` 只能证明仓库规则，不证明用户流程可达；见 QA-059。 |

### 4.4 执行、我的、时间与数据

| ID | 当前功能 / 用户入口 | 前端代码 | 业务 / 数据关联 | 状态、验证与边界 |
| --- | --- | --- | --- | --- |
| E01 | 开始 / 暂停 / 继续 / 返回保留专注 | `ExecutionSessionDialog`，今日继续入口 / 时间块专注 | TaskViewModel、RoomExecutionSessionRepository、持久化会话 | 已接入；`ExecutionSessionUiTest/ExecutionWorkflowUiTest/ExecutionSessionRepositoryTest`。实际进程重启 / OEM 后台矩阵待补。 |
| E02 | 结束：继续进行 / 部分完成 / 完成 / 跳过 | `ExecutionSessionDialog` 的结果区 | finishSession、ExecutionOutcome、反馈事务 | 已接入；`SessionInputWorkflowUiTest`、单元 `ExecutionSessionTest`；暂停不累计时长，任务事实不由墙钟推断。 |
| E03 | 结果后的剩余工作重排 / 取消保留当前计划 | `TodayScreen` 结果区、PlanDraftDialog | generatePlanDraft 普通重排；执行 / 保护 / 历史规则 | 已接入；`ExecutionWorkflowUiTest`、单元 `TaskLifecycleValidatorTest`；完整真机闭环待补。 |
| S01 | 版本、版本码、构建来源显示 | `MineScreen` 的 `app-version` | BuildConfig，Gradle git revision | 已接入；版本与实际安装包区分，不按聊天猜测版本。 |
| S02 | 本地提醒开关、系统通知授权与拒绝提示 | `MineScreen` Switch、根 permissionLauncher | ReminderSettingsViewModel、LocalReminderScheduler | 已接入；`ReminderSettingsRepositoryTest`、单元 `LocalReminderPlannerTest`；原生通知投递 / 取消 / 重启待真机验证。当前 AlarmManager.set，不承诺精确到秒。 |
| S03 | AI 总开关、首次授权与请求发送说明 | `AiSettingsSection/AiConsentDialog/AiRequestPreviewDialog` | AiSettingsRepository、PlanningAgentWorkflow | 已接入；`AiSettingsRepositoryTest/ArrangementAssistantAccessTest`；开启本身不发送请求，release 不因保存配置获得网络。 |
| S04 | 模型地址 / ID / Key、本机加密保存、清除 Key、测试连接 | `AiProviderSettingsDialog` | AiProviderConfigViewModel、SecureAiProviderConfigRepository、Provider | 已接入；`AiProviderSettingsUiTest/AiSettingsRepositoryTest/CompatibleTransportTest`。换服务需重输密钥；最小连接测试不含个人任务；真实最终版矩阵待补。 |
| S05 | 类别偏好展开、数字权重、真实保存与反馈 | `MineScreen` 类别区 | CategoryPreferenceViewModel、RoomCategoryPreferenceRepository | 已接入；`ContextReadWorkflowUiTest/CategoryPreferenceRepositoryTest`。新排序 / 新预览用新权重，当前确认计划不静默变化。 |
| S06 | 关于你的记录：生成、本地证据、修改结论、删除 | `ProfileEvidenceSection/Card/EditorDialog` | ProfileEvidenceViewModel、RoomProfileEvidenceRepository | 已接入；`ProfileEvidenceAndDataManagementRepositoryTest`、单元 `ProfileEvidenceGeneratorTest`；不是新增长期自动画像研究。 |
| S07 | 导出本地 JSON，系统选择保存位置、成功 / 失败反馈 | `LocalDataManagementSection`、CreateDocument launcher | DataManagementViewModel、RoomDataManagementRepository、LocalDataJsonExporter | 已接入；单元 `LocalDataJsonExporterTest`。无导入恢复入口；不是完整可还原备份，独立顺序 / 规划草案 / 助手 workspace / 轨道未纳入该快照，Key 不导出。系统文件流程待人工验证。 |
| S08 | 明确确认清除本地业务数据，取消提醒，防草稿回写 | 根清除确认 Dialog、LocalDataManagementSection | clearAllTables、prepareForDataClear/finishDataClear | 已接入；`TaskCaptureViewModelTest/ProfileEvidenceAndDataManagementRepositoryTest`。仅 QA 可破坏性测试，禁止清用户包；API Key 是独立清除入口，不把数据库清除当作凭据全清。 |
| S09 | 每周课程 / 可用 / 休息 / 固定时间新增、编辑、删除 | `TimeScreen/WeeklyBlockCard/WeeklyTimeBlockEditorDialog/DeleteTimeEntryDialog` | TimeViewModel、RoomTimeRepository、TimeConstraintRevision | 已接入；`TimeSettingsIntegrityTest/TimeSettingsWorkflowUiTest`；结束支持 24:00，失败留表单，约束与修订原子提交。 |
| S10 | 单日可用 / 不可用等例外新增、编辑、删除 | `DateOverrideCard/DateOverrideEditorDialog` | TimeViewModel、DateOverrideDraft、RoomTimeRepository | 已接入；`TimeSettingsWorkflowUiTest/TimeConstraintRevisionRepositoryTest`；不让可用例外覆盖课程等硬约束。 |
| S11 | 学期首周周一、学期周展示 | `SemesterWeekCard/SemesterStartEditorDialog` | SemesterSettings、周模式计算 | 已接入；`TimeSettingsIntegrityTest`；重建 / 无效日期保留与结束边界有测试。 |
| S12 | 课表 PDF 读取、识别预览、选项、钟点核对 | `TimeScreen` 文件入口、`TimetableImportReviewDialog` | PdfTimetableImporter、TimeViewModel | 已接入；`PdfTimetableImporterTest/TimetableConfirmationUiTest`；生成夹具不等于真实学生 PDF / 扫描件 / 系统选择器验收。 |
| S13 | 导入课程逐项编辑节数、周模式、确认 / 取消 / 去重 / 回执 | `ImportedCourseEditorDialog/TimetableImportReviewDialog` | TimetableReview、RoomTimeRepository 的确认事务 | 已接入；`TimetableConfirmationTest/TimetableConfirmationUiTest`；1–12 节原数字校验，确认前不新增约束。 |
| S14 | 系统深浅色、字号 / 横竖屏适配、反馈与触控面积 | `ui/theme/*`、TaskPageAppBar、EditorSheet、各原生 Sheet | Material 3、WindowInsets、原生返回 / liveRegion | 已接入且部分专项通过；不等于 TalkBack / 平板 / 硬件键盘 / 全部弹层适配已验收。 |

### 4.5 后台能力与遗留代码（不是额外的前端功能）

| 项目 | 代码 / 对应前端 | 接手时注意 |
| --- | --- | --- |
| 统一读取、有限命令、过期校验 | PlanningReadService / PlanningOperationService / PlanningRevision → 排序、制定、助手、确认 | 页面仍由 ReplandApp 协调，不能声称全部设置都可经 AI 控制。快照读取失败不能退为空上下文。 |
| 本地排序因子 | `domain/model/PlanningPriority.kt::LocalPriorityRanker` → 排序依据 | V1 优先级 35%、截止 25%、计划日期 15%、延期证据 10%、类别 10%；当前没有已审计 AI 修正，贡献为 0。勿恢复旧 45% AI 公式。 |
| 本地规划 | `domain/model/Plan.kt::PlanGenerator` → 制定计划 / 单日 / 剩余重排 | 30 天窗口，30 分钟槽的贪心规划；硬约束优先；不保证全局最优，未安排工作量不等于需要一个连续空档。 |
| 提醒投递与重排 | `reminders/LocalReminderScheduler/LocalReminderReceiver/ReminderRescheduleReceiver` → 设置开关、已确认计划 | 计划开始 / 截止 / 复盘；系统权限、重启、时区和后台限制须单独验证。 |
| 网络契约 | remote 的两个 Advisor + CompatibleChatTransport → 助手 / 详情建议 | PROPOSE_CHANGES、QUERY_TASKS、FORMULATE_PLAN、EXPLAIN_ORDER 四种操作；产品称“五类能力”把新增与局部调整拆开，不是五个 enum。连接 12s / 读取 45s；实际故障矩阵待补。 |
| Room 迁移 | ReplandDatabase v20 / AppContainer migrations → 所有持久功能 | 单个 `Migration5To6Test` 通过不等于 1→20 所有真实升级都通过。禁止 fallbackToDestructiveMigration。 |
| 旧参与模式 / 使用事件 | EngagementViewModel / RoomEngagementRepository → 兼容历史 / 导出 | 当前无三模式选择 UI，旧数据保留但不隐藏日程；不重新实施三模式产品。 |
| 旧完成表单 | `ReplandApp.kt::CompleteTaskDialog` | 仅声明、当前无调用；其中旧数字过滤不能误称当前活跃入口，删除或改造需另行任务。 |
| 其他未露出建议类型 | PlanningAgentRequestType 中排序解释等、DetailActions 的 onRequestAiAdvice 参数 | 业务类型存在不等于每个类型当前有独立按钮；以实际连接为准。 |

## 5. 本批 0.1.29 修改、证据与已知问题

| 修改前 | 修改后 | 原因 |
| --- | --- | --- |
| 非法小时 / 分钟被过滤、进位；课程时长强制裁剪 | UserNumericInput 严格解析，原字段保存，行内错误与禁写 | 不把非法输入替换为另一个用户未确认的事实。 |
| 事项库冲突检查 5–720，而真实安排 15–240 并可能截断日末 | 检查、展示、实际写入共用 TaskPlacementPolicy；跨日拒绝 | 展示与写入一致，保留任务总时长；QA-057。 |
| 事项库默认半展开时原生 IME 遮住确认按钮 | 原生全展开 sheet、可滚动内容、真实可见面积测试 | 保证按钮实际可点击，不仅存在于语义树；QA-058。 |
| 候选 / 课程 / 导入节数原输入或目标可能在重建丢失 | 稳定身份 + saveable 原字段，课表实际 Activity 重建检查 | 错误后可修正原对象，避免丢输入 / 换身份。 |

本批沿用 ui-ux-pro-max 的表单校验 / 反馈、emil-design-eng 的真实可达性与前后原因、apple-design 的原生 sheet / 手势与中断规则，没有新建另一套设计系统或添加无目的动画。

- 基线失败记录：课程 0 被裁成合法值、事项 08:99 被换时间、课表 -3 被换成 3；助手非法时长在修正测试驱动定位后复现。不能把最初未打开助手弹窗的驱动失败算产品缺陷。
- 前批正常专项 19/19，通过版本 a922c11；最终来源 5ea3338 的深色 2 倍字号横屏实际 Activity 2/2，通过，用时 11.294 秒。其后本次同源完整 267 项与 127 单元通过。不同来源 / 显示模式的证据不互相冒用。
- QA-053：本批覆盖事项库、课程、助手候选、课表节数；其余活跃数字入口仍需按清单审查，不能全局宣布任何字段都已处理。
- QA-057：共用时长和真实 Root→ViewModel→Room→任务页证据通过。测试任务总时长 300，单段实际 240，总时长不变。
- QA-058：半展开 + IME 的真实不可达问题已在 QA 修复验收，未安装用户当前版。
- QA-059：历史 / 锁定管理组件存在但当前主界面无打开入口（源码确认）。本次只记录，不实现。
- 保留历史问题：旧包 ANR / 模拟器 watchdog 等没有确定根因或最终真机证据，不因一次新回归通过宣布所有旧问题关闭。详见 [缺陷台账](BUG_TRACKER_2026-10-03.md)。
- 可审查风险（不是已复现用户事故）：时间块精确调整 / 拖入课程的异步反馈；“已完成”包含非活动状态；导出并非全量可恢复备份；数据清除与独立凭据边界。按原计划可靠性范围评估，不自动扩展备份、云同步等产品。

## 6. 下一账号逐项任务（本次只记录，不执行）

| 优先级 / 任务 | 具体操作范围 | 完成标准 / 证据 |
| --- | --- | --- |
| 1：完成 29 安装交付 | 核对本文件附录的 internal 哈希、来源与签名；在当前包原位升级，不卸载 / 清数据；只启动当前版，停止 QA / 旧包 | 我的页版本 1029 与来源正确；升级前后原任务、课程、计划、反馈、设置保持；API 配置只通过 UI 验证，不读 Key；记录实际检查，不以安装成功代替数据验证。 |
| 2：补齐实际前端入口 | 处理 QA-059 的计划历史 / 恢复 / 清空 / 锁定入口，遵循现有导航与组件 | 原生用户流程可达，取消 / 恢复失败保留原计划，锁定在制定 / 单日 / AI / 重排中保护；补 UI 与事务回归，不仅测 Repository。 |
| 3：剩余数字与异步操作审查 | 对照功能表逐项核对原数字、目标身份、回执、失败保留；特别 T08/T10 与其他活跃编辑入口 | 先写复现测试，记录产品 / 驱动 / 环境失败区别；修复后绑定新源码与 APK，不以过滤或弱化断言通过。 |
| 4：M2 复杂容量 / 截止策略 | 多任务不足、紧急任务与高分任务竞争、可分段工作、手动顺序 / 锁定 / 固定约束 | 原计划保护不变，解释准确、分钟真实、处理选项可行动；只在授权规则内完善，不承诺全局最优或静默解锁。 |
| 5：M3 真实 AI 全矩阵 | 当前用户授权配置：多任务批量新增、已有任务连续修改、制定路由、查询 / 解释；错误密钥、超时、取消、无效格式、断网、换配置 / 关 AI 后迟到响应 | 正式业务只在确认后原子生效；错误零写入，输入保留，实际数量准确；跨页同结果；无配置 / 关闭零请求；用最小上下文、有限次数，不记录凭据或原始个人响应。 |
| 6：M4 真实执行恢复与提醒 | 开始 → 暂停 → 后台 → 进程停止 / 重启 → 继续 → 部分 / 完成 → 剩余预览；提醒开关、拒绝 / 授权、取消、重启、时区变化 | 同一会话 / 任务身份恢复，暂停不计时，计时不自动完成，取消重排不改计划；实际系统 alarm / notification 证据。Activity.recreate 不替代真实进程重启。 |
| 7：M5 实际文件 / 显示 / 升级矩阵 | 真实 PDF 通过系统选择器，文本 / 无效 / 扫描件；日周月、深浅色、小屏 / 横屏 / 字号 / TalkBack / 硬件键盘；旧数据真实升级 | 按设备、系统、具体 APK 记实测及限制。无真机 / 平板 / 用户 PDF 时明确未验证，不造通过。无需用户招募或连续试用。 |
| 8：完整工作流收尾 | 收集 → 排序 → 制定 → 编辑 → 确认 → 执行 → 打乱 → 重排 / 取消 → 复盘；设置变化、读取 / 写入失败、恢复 | 工作流技术报告、已知限制、候选包清单；同源完整自动化回归仍通过，阻断 / 数据一致性缺陷关闭。 |
| 9：最终产品对比与交互意见 | 网上查 Todoist、TickTick、Structured、Motion、Sunsama 等官方最新资料；与 Repland 当前可用功能对比，逐流程检查 | 写优缺点、依据、用户操作卡点与优先级；区分竞争产品能力 / 推断 / 本地实测；只形成报告，不改代码。历史报告不当作这次最终报告。 |

原阶段对应：M0 核心基线通过；M1 核心持久化 / 事务与有限共用服务已实现、仍有入口与工作流验收；M2 核心本地流程已实现，复杂容量未完；M3 契约已接、真实验证部分通过；M4 本地执行已接、真实系统矩阵未完；M5 进行中。不要重新从零实现已有能力，也不要把原计划标为全部完成。

## 7. 操作环境、复跑与安全交接

### 环境 / 包 / Git

- 本机仓库 `D:\Repland`，PowerShell；Android SDK `D:\Android\sdk`，adb `D:\Android\sdk\platform-tools\adb.exe`；当前 AVD `emulator-5554` / Pixel_6 / API 36.1。
- 用户包 `com.swan1127.repland.current`，启动 Activity `com.swan1127.repland.MainActivity`；QA 包 `com.swan1127.repland.qa`，runner `com.swan1127.repland.qa.test/androidx.test.runner.AndroidJUnitRunner`。
- 旧 `.repland` / `.internal` 保留数据。不要全局杀 Java、卸载旧包、清当前包，不能把测试任务 / 假 Key 写入用户包。QA 数据是可再生隔离夹具。
- Git remote `git@github.com:Swan1127/Repland.git`；当前分支 `codex/placement-numeric-integrity`，源码 5ea3338。SSH 曾可推送，gh 未登录；新账号需检查自己的权限，不复制前账号令牌。文档提交后以 `git log` 与 `git status` 查实际状态。
- internal 有网络 Provider，release 编译只含本地 Advisor；internal 使用本机 debug 签名作为未配置正式签名时的内测退路，不可公开发布。不同账号 / 机器 debug key 可能不同，签名不一致不能直接保数据升级，不要用卸载解决。
- APK、截图、XML 在忽略的 build 目录，仅本机留存；GitHub 分支保存源码与本交接文本，不等于 APK 已上传为 GitHub Release。新机器应按构建命令重建并记录新来源 / 哈希。

### 复跑命令（从 D:\Repland）

```powershell
# 先确认分支、无并行构建及设备；不要打印任何凭据文件
git status --short
git log -3 --oneline
& D:/Android/sdk/platform-tools/adb.exe devices

# 串行回归、内测构建、release 编译与离线边界；不生成正式 release APK
.\android\gradlew.bat -p android '-Pkotlin.compiler.execution.strategy=in-process' :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleInternal :app:compileReleaseKotlin :app:processReleaseMainManifest :app:verifyOfflineMvpBoundary :app:recordBuildArtifacts

# 只读核对用户包实际版本
& D:/Android/sdk/platform-tools/adb.exe -s emulator-5554 shell dumpsys package com.swan1127.repland.current | Select-String 'versionCode=|versionName='
```

仅在确认 QA 隔离身份后才可 `pm clear com.swan1127.repland.qa`；不对 `.current` 使用 pm clear。本次启动前 QA clear 返回 Failed，随后 Gradle 正常安装测试包并完整运行；不能记录为用户数据清除，也不能把这次 Failed 当成测试成功。自动化 instrumentation 运行会关闭动画，恢复环境前先检查实际 settings，不猜测已恢复。

测试原始目录：`android/app/build/outputs/androidTest-results/connected/debug/`、`android/app/build/test-results/testDebugUnitTest/`；HTML：`android/app/build/reports/androidTests/connected/debug/`、`android/app/build/reports/tests/testDebugUnitTest/`。只看命令退出码不够，要核对 tests / failures / errors / skipped；补测不等于全套，假 Provider 不等于真实请求。

本次没有更新 UI 代码；此前 UI 修改需继续遵守 ui-ux-pro-max、emil-design-eng，涉及手势加 apple-design。下次使用技能必须重新读取各 SKILL.md，不能只根据本文件摘要使用。技能路径在本机 `C:/Users/Lenovo/.codex/skills/`，跨机器不一定存在。

### 下一账号可直接使用的接手提示

> 在 D:\Repland 继续 Repland 原产品计划。先完整读取 docs/ACCOUNT_HANDOVER_2026-10-06.md，再核对 Git、实际安装版本、保留包与验收证据。最新 0.1.29 的 267 设备 / 127 单元完整回归已通过，但用户当前包仍为 0.1.28，不能误认为 29 已升级。按交接第 6 节顺序逐项推进；不重新实现已完成能力，不进行 7 天试用 / 招募，不清用户数据、不读取密钥。AI 只走有限业务契约，确认前不改正式数据。后置网上产品对比只写报告，不实施建议。每项记录具体源码 / 包 / 测试与未验证边界。

## 8. 本次构建附录

本次串行命令 `BUILD SUCCESSFUL in 6m 33s`；完成单元 / 设备全套、internal 压缩构建、release Kotlin / Manifest、离线边界和 recordBuildArtifacts。没有生成正式签名 release APK。来源仍为 `5ea3338cc1e385f243b4f3dab706e3c54deb733c`。

| 验证 | 结果 |
| --- | --- |
| Pixel_6 / API 36.1 完整设备测试 | 267 tests / 0 failures / 0 errors / 0 skipped，251.337 秒 |
| JVM 单元测试 | 127 tests / 0 failures / 0 errors / 0 skipped |
| internal 候选包 | 构建成功，0.1.29-internal / 1029；未原位安装 |
| release Kotlin / Manifest 与离线边界 | 通过；不是正式 release 打包 / 发布 |
| 当前用户包 | 只读确认 0.1.28-internal / 1028；结束后停止 QA / 旧包并启动当前版，未清业务数据 |

| 本地保留文件 | SHA-256 |
| --- | --- |
| app-internal.apk | `93b786c7967cfae425813797946e48339f09085e0979a56f2685f2e7c664eebc` |
| app-debug.apk | `3146855372e92a5ab10a3c26987df41f53fd3881cc04390bc15d96e149dece19` |
| app-debug-androidTest.apk | `e65262125b2b957a7074f7622c1daa5e2b6d8ee58bb53250776e5e30b2b51d2a` |

保留目录 `android/app/build/reports/retained/0.1.29-regression/`：三个 APK、build-manifest.txt、unit-test-results/。完整设备 XML 独立复制到 `android/app/build/reports/repland-029-final-full.xml`；原 XML / HTML 仍在第 7 节目录。以上均是本地忽略产物，新账号在同机器可访问；跨机器需安全取得 APK / 报告或重建，不能认为已经随 Git 上传。

下一账号应先核对实际安装与源码。本次没有升级当前版、没有创建已完成交付的 v0.1.29 标签，也没有执行后续功能任务；0.1.29 的完整自动化回归已完成，与整个产品计划完成是两件事。
