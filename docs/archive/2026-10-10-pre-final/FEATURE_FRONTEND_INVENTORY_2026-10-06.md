# Repland 功能与前端清单

2026-10-10 最新验收：数值画像首批 H01–H05 实现及 H06 本机技术验证完成。最终正常 Gradle **150 单元 / 299 设备全套，0 失败 / 错误 / 跳过**；internal 构建、release 编译 / Manifest 和离线边界通过。`.current` 已用 install-r 从本机1032升级至1033，并再次安装最终显示修正包；版本 **0.1.33 / 1033 / Room 21**，安装包哈希一致、首次安装时间和数据目录不变，实际冷启动及画像入口通过。源码为 `codex/placement-numeric-integrity` / `85c75f04117b9421786fa37fdaf1236481b3b865` 加保留工作区修改与新增源码，未 commit / push；fetch 后远程仍为 `7506074`。实现映射、每次失败、最终源码 / APK / 截图和下一步见 [本批技术验收](features/H01_NUMERIC_PROFILE_2026-10-10.md)。后三画像维度保持未知；任务主表单内嵌建议、单维度增量 / 大数据性能、真实 AI / 通知 / 真机矩阵、T08/T10 完整失败回执，以及 QA-062 / QA-066 / QA-067 未定位风险仍待，不将首批完成扩大为全项目完成。

2026-10-10 历史（此前受限执行环境，已由上方最新状态替代）：已核对分支 codex/placement-numeric-integrity / 85c75f0 加保留工作区修改；本阶段 fetch 成功，远程仍为 7506074，无后续提交。H01 契约、首批 H02–H05 数值 / 来源 / 只读 AI / 估时与草案联动源码已实现，候选声明 0.1.33 / 1033 / Room 21。诊断源码编译及 149 项直接 JVM 测试通过，离线边界检查通过；正常 AAPT2 link 访问异常退出，未完成可验收 APK、设备 / UI / 升级，H06 与本阶段尚未完成。未安装新包、未调用真实 AI。源码身份、失败历史、验证方式限制和恢复脚本见 [本批实施与边界](features/H01_NUMERIC_PROFILE_2026-10-10.md)，以下旧结果仅适用于对应版本。

2026-10-09 续作完成（0.1.32 / 1032 / Room v21）：C07 本地 / 降级 / 最终事务保护、C02 普通重叠单独确认、C03 我的页历史入口 / 锁定新版本及 C05 旧草案来源拒写已实施。最终源码为 `85c75f0` + 工作区补丁（未 commit / push），139 单元和最终同包 284 设备全套均 0 失败 / 错误 / 跳过；internal 构建、release 编译 / Manifest 和离线边界通过。已安装并只运行 `.current` 0.1.32；本机首次安装，不冒称真实用户升级。首次同包 283/284 的间歇详情点击超时记 QA-062，根因未定位；远程 fetch 仍失败。完整源码身份、APK 哈希、复现与失败历史、实际 UI 边界及下一步见 [本批实施记录](features/C07_PLANNING_PROTECTION.md)。数值画像 H01–H06、T08/T10 完整失败回执、真实 AI / 通知 / 真机矩阵仍待完成。以下保留修改前及历史证据。

2026-10-09：下方 74 项继续作为 0.1.29 历史入口地图。当前 `85c75f0` 为 0.1.31 / Room v21；未知字段已实现但 C07 跨路径保护、QA-059 入口与 H01–H06 等尚未完成。最新能力状态及本机证据见 [实际项目基线](PROJECT_BASELINE_2026-10-09.md)。

2026-10-08：74 项现有入口已建立[独立功能文档](features/README.md)，后续实现与测试维护在对应文件。下方保持 0.1.29 历史盘点；0.1.30 未知字段 / 来源实施见 [C04](features/C04_INPUT_INTEGRITY.md)，不将此清单旧测试扩大为新规则验收。

更新：2026-10-06（Asia/Shanghai）。盘点基线：源码 `5ea3338cc1e385f243b4f3dab706e3c54deb733c`，版本 0.1.29 / 1029，分支 `codex/placement-numeric-integrity`。本文独立保存 74 个功能条目的前端、业务代码、相关测试与能力边界，以及后台与遗留组件说明。

当前安装版本、回归与构建证据、后续任务、操作环境及安全注意事项统一见 [账号交接文档](ACCOUNT_HANDOVER_2026-10-06.md)。本清单是上述源码的功能快照，不代表 0.1.29 已安装、全部功能已验收或后续计划已完成。

## 1. 阅读代码的地图

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

## 2. 全功能与对应前端清单

### 2.1 导航、今日与轨道

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

### 2.2 任务收集、列表、详情与反馈

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

### 2.3 助手与计划预览

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

### 2.4 执行、我的、时间与数据

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

### 2.5 后台能力与遗留代码（不是额外的前端功能）

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
