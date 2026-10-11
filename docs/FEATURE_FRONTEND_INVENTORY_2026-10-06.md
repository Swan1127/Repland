# Repland 当前功能与前端清单

更新：2026-10-11（Asia/Shanghai）。当前候选源码 0.1.35 / 1035 / Room 21，分支 `codex/placement-numeric-integrity`，源码提交 `32d766f95224b3851dbb832bd9644b541b3436cf`。本机候选验收已完成；150 JVM / 310 QA 设备通过，.current 真实模型与未知画像实查单列，外部矩阵仍未验证。提交状态在交接记录中更新。

实际构建、测试、设备操作与未验证边界统一见[本阶段验收记录](FINAL_TECH_ACCEPTANCE_2026-10-10.md)；历史完整盘点[原文保留](archive/2026-10-10-pre-final/FEATURE_FRONTEND_INVENTORY_2026-10-06.md)。各功能文档包含目标、入口、前端位置、逻辑、来源、流转、持久化、AI 范围、测试、状态、限制；方案、代码、自动化和设备操作分别列示。

## 当前源码地图

- `ui/ReplandApp.kt` 协调今日、任务、助手、我的四个底栏；时间设置为二级页。
- `ui/schedule/TimelineDashboard.kt` 负责日程、课程录入、手动调整与时间编辑；`ui/agent/AgentCenterScreen.kt` 负责有限建议与确认。
- `domain/model/PlanningOperationService.kt`、`PlanningRevision.kt`、`PlacementValidator.kt`、`ManualPlanChange.kt` 统一读取、提交、过期与保护规则。
- `data/room/ReplandDatabase.kt` 为 Room 21，迁移 20→21 保留旧未知来源；任务、执行、计划、约束、草案与 workspace 分离，不使用破坏性迁移。
- `domain/model/NumericProfile.kt`、`RoomNumericProfileRepository.kt` 与 `ui/profile/*` 为首批数值画像。remote source set 中的 `CompatibleNumericProfileAdvisor.kt` 是有限只读说明契约。
- 正式 release 保持离线；QA 与 internal 为可选 BYOK 网络构建。密钥不进入业务导出。

## 已有功能索引

下表是当前入口索引，测试状态见各独立文档与当前验收记录，不从历史结果推断本包全部通过。

| ID | 功能文档 | 前端定位 | 业务关联 |
| --- | --- | --- | --- |
| N01 | [四页底栏切换](features/N01.md) | `ui/ReplandApp.kt::ReplandApp/TabIcon` | 各 ViewModel 共同观察同一 Room 数据 |
| N02 | [我的 / 助手 → 时间设置 → 返回原页](features/N02.md) | `ReplandApp/TimeScreen`，`timeReturnTab`、原生 Back | TimeViewModel |
| N03 | [读取失败提示、重试、未可信禁写](features/N03.md) | `DataReadNotice`、各页面 / Dialog | `ui/state/RecoverableRead.kt`，各读取代次 |
| T01 | [当前 / 下一项、开始执行、完整日程入口](features/T01.md) | `TodayFocalOverview`、`NowCard`、`DailyDesk` | `TodayFocus`、TaskViewModel、PlanViewModel |
| T02 | [今日待安排、待确认与任务库跳转](features/T02.md) | `TodayScreen/PendingConfirmationTaskCard` | `TaskPlanMembership`、确认计划时段 |
| T03 | [总览 / 日 / 周 / 月切换、选择日期](features/T03.md) | `TodayScreen`、`WeekScheduleView/MonthScheduleView` | `ScheduleTimeline`、同一课程 / 例外 / 确认计划 |
| T04 | [合轨 / 分轨、时间块、当前时间展示](features/T04.md) | `TimelineDashboard/DayTimelineGrid/MergedTimelineGrid` | `RhythmTracks.project` |
| T05 | [新建命名轨道、重名反馈、跨页名称一致](features/T05.md) | `TrackStrip`、轨道创建输入 | PlanViewModel.saveTracks → PlanningWorkspace |
| T06 | [事项库：分类、选任务 / 轨道 / 开始时间、放入安排](features/T06.md) | `EventLibrarySheet` | `TaskPlacementPolicy` → TodayScreen → PlanViewModel.placeTask → RoomPlanRepository |
| T07 | [总预计时长与本次安排时长分别显示](features/T07.md) | `EventLibrarySheet` | `domain/model/UserNumericInput.kt::TaskPlacementPolicy` |
| T08 | [日轨道新增课程：手动时间 / 待拖入](features/T08.md) | `CourseComposerDialog/PendingCourseBlock` | TimeViewModel.saveWeeklyBlock |
| T09 | [点击时间块查看内容、备注、阶段、任务详情](features/T09.md) | `TimelineEntryDetailSheet` | TimelineEntry；实际 taskId / 时间约束 ID |
| T10 | [长按拖动、提前 / 推后 15 分钟、精确修改时间](features/T10.md) | `TimelineBlock/TimelineEntryDetailSheet/TimelineTimeEditorDialog` | PlanViewModel.movePlacement 或 TimeViewModel.save* |
| T11 | [时间块编辑详情与备注](features/T11.md) | `TimelineEntryDetailSheet` → 根 onEditTimelineEntry | 任务、每周块、单日例外按 ID 分流 |
| T12 | [移出安排（不删除任务）、进入专注](features/T12.md) | `TimelineEntryDetailSheet` | removePlacement；startSession(segmentId) |
| T13 | [今日复盘：安排 / 待确认 / 反馈 / 进度 / 下一项](features/T13.md) | `DailyReviewDialog` | `DailyReviewSummary`、执行日志 |
| T14 | [复盘中的 Agent 总结](features/T14.md) | `DailyReviewDialog/PlanningAgentOutcomeCard` | PlanningAgentViewModel.beginDailySummary |
| K01 | [任务页浮动新增 / 空态添加 / 今日添加](features/K01.md) | `TaskCaptureButton`、`TaskCaptureSheet` | TaskCaptureViewModel、RoomTaskCaptureRepository |
| K02 | [简短录入、可选类别 / 优先级、稍后补详情](features/K02.md) | `CaptureInputStep/CaptureDetailsStep` | TaskCaptureDraft → TaskDraft |
| K03 | [保存到任务库 / 今日清单、估时、自定义时长、截止日期](features/K03.md) | `CaptureIntentStep/CaptureDurationStep/CaptureDeadlineStep`、`TaskDatePickerDialog` | `TaskCaptureDraft.toTaskDraft` |
| K04 | [关闭保留草稿、继续填写、确认放弃草稿](features/K04.md) | `TaskCaptureSheet` 关闭 / 放弃确认 | 草稿持久化、150ms 暂存、真实保存回执 |
| K05 | [软键盘与抽屉返回：先收键盘，再关抽屉](features/K05.md) | `TaskCaptureSheet` 的 IME bridge / BackHandler | Material 3 原生 sheet；保存中拦截关闭 |
| K06 | [语音识别 → 可编辑文字 → 任务草稿](features/K06.md) | 根语音 Dialog / speechLauncher，新增页语音入口 | Android RecognizerIntent，TaskCaptureViewModel |
| K07 | [总览 / 待安排 / 今天 / 逾期 / 已完成筛选](features/K07.md) | `TasksScreen/TaskCenterView` | TaskPlanMembership、当前确认时段、同一 taskOrder |
| K08 | [需要处理 / 今天 / 待安排 / 更晚分组、查看全部](features/K08.md) | `TaskControlCenter/TaskRow` | 分组只筛选，保留共用顺序 |
| K09 | [任务行开始、查看已安排日期与时段](features/K09.md) | `TaskRow/TaskStatusButton` | startTask 状态操作、确认时段摘要 |
| K10 | [右上角自动排序、候选依据 / 上下调整 / 应用](features/K10.md) | `TaskPageAppBar/PlanDraftDialog/DraftTaskOrderRow` | LocalPriorityRanker，orderOnly，持久化顺序 |
| K11 | [撤销最近一次排序](features/K11.md) | `TaskPageAppBar` “撤销排序” | PlanViewModel.undoTaskOrder，workspace |
| K12 | [任务详情：说明、时间、优先级、进度、历史](features/K12.md) | `TaskDetailScreen/DetailSection/ExecutionLogCard` | TaskViewModel、任务日志流 |
| K13 | [编辑已有任务：标题、备注、类别、优先级、天数、时长、日期](features/K13.md) | `TaskEditorDialog/TaskDateField` | TaskViewModel.saveTask，稳定 targetId，实际 receipt |
| K14 | [完成整项、恢复已完成 / 已取消任务](features/K14.md) | `DetailActions` | completeTask / restoreTask，RoomTaskRepository |
| K15 | [部分完成（累计进度）、本次反馈、实际耗时可留空](features/K15.md) | `PartialCompletionDialog/ExecutionFeedbackDialog/FeedbackDialog` | TaskMutation、TaskFeedback、真实事务 |
| K16 | [延期原因与主动查看重排建议](features/K16.md) | `PostponeTaskDialog/PostponementGuidance` | postponeTask → 反馈日志 / 用户主动预览 |
| K17 | [取消任务的明确确认](features/K17.md) | `ConfirmTaskStatusDialog` | cancelTask，任务 / 未来时段生命周期事务 |
| K18 | [替换任务并保留原历史](features/K18.md) | `TaskEditorDialog` 的替换上下文 | replaceTask，旧任务 REPLACED，新任务 ID |
| K19 | [更正既有执行日志](features/K19.md) | `CorrectExecutionLogDialog/ExecutionLogCard` | taskId + correctedLogId 固定，追加更正 |
| K20 | [任务详情“打开对话”、理解 / 估时 / 拆分 / 重排建议](features/K20.md) | `AssistantSheet/PlanningAgentOutcomeCard/EditableAiAdvice`、请求预览 Dialog | PlanningAgentViewModel / PlanningAgentWorkflow |
| P01 | [独立“制定计划”](features/P01.md) | `AgentCenterScreen` 顶部按钮，根 generatePlanDraft(reorder=true) | PlanningOperationService、PlanGenerator、PlanViewModel |
| P02 | [空任务引导、缺可用时间引导](features/P02.md) | `AgentCenterScreen`，`PlanDraftDialog` 的补充入口 | 是否有活动任务 / AVAILABLE 约束 |
| P03 | [最小可用时间卡、可选每周保存、重新预览](features/P03.md) | `ui/plan/QuickAvailabilityDialog.kt` | saveAvailabilityAndGenerate → RoomTime / 规划服务 |
| P04 | [“说出你的安排”输入、语音、生成候选](features/P04.md) | `AgentComposer` | 本地 Interpreter 或 ArrangementAssistantViewModel |
| P05 | [输入框下“安排今天”](features/P05.md) | `ComposerPreset/selectWorkflow` | 有正文则安排候选；空正文则已有任务单日规划 |
| P06 | [输入框下“新增事项”、批量收集](features/P06.md) | `selectWorkflow/AgentProposalPanel` | CAPTURE_TASKS，保存批量任务事务 |
| P07 | [任务查询：未完成 / 今天 / 待安排 / 逾期，跳详情](features/P07.md) | `agent-read-result` 结果区 | QUERY_TASKS → PlanningOperationService.query |
| P08 | [排序解释](features/P08.md) | `agent-read-result` 的依据 / 查看任务 | EXPLAIN_ORDER → LocalPriorityAssessment |
| P09 | [模型意图“制定计划”路由](features/P09.md) | `AgentCenterScreen::applyAdvice` | FORMULATE_PLAN → 同一本地制定入口 |
| P10 | [新增 / 已有候选区分，待决定区、删除候选](features/P10.md) | `AgentProposalPanel/UndecidedProposalShelf/AgentDraftTimeline` | AgentTaskProposal、existingTaskId、workspace |
| P11 | [候选修改时长 / 起止 / 轨道，重建保留原输入](features/P11.md) | `AgentPlacementDialog` | stable proposalId，UserNumericInput、workspace |
| P12 | [连续补充指令修改同一草案](features/P12.md) | `AgentProposalPanel` 的 follow-up | CompatibleArrangementAdvisor、身份验证 |
| P13 | [请求处理中取消、输入 / 配置变化使旧响应失效](features/P13.md) | `agent-cancel-request`、onPromptChange | Job.cancel、requestVersion、providerRevision |
| P14 | [实际确认、成功数量回执、查看任务 / 安排](features/P14.md) | `AgentProposalPanel`、`agent-save-receipt` | saveAssistantChanges / saveTasksAndPlace，Room 原子事务 |
| P15 | [持久化助手正文 / 草案 / 修改指令](features/P15.md) | `initialWorkspace/onWorkspaceChanged` | PlanningWorkspace、InteractionWorkspaceCodec |
| P16 | [规划预览：新旧时段 / 顺序、理由、多日展开](features/P16.md) | `PlanDraftDialog/PlanDraftTimelinePreview/DraftDayTrackPreview` | PlanDraftReview、当前计划、protectedIds |
| P17 | [修改候选顺序、编辑分段、时长 / 日期 / 轨道](features/P17.md) | `DraftTaskOrderRow/PlanSegmentEditorDialog` | PlanDraftEditor、saveDraftEdit(expected,updated,requestId) |
| P18 | [本次暂不安排、缺信息、容量缺口及处理入口](features/P18.md) | `PlanDraftDialog` 未排入区 / defer 按钮 | UnscheduledReason / 剩余分钟；补任务 / 可用时间 |
| P19 | [确认计划、取消 / 保留关闭 / 恢复查看](features/P19.md) | `accept-plan-draft`、保留预览入口 | acceptDraft / discardDraft、PlanningRevision、同一事务 |
| P20 | [历史列表、恢复历史、清除当前计划、锁定 / 解锁段](features/P20.md) | 我的 → 计划历史与锁定管理 → `PlanOverviewDialog`（已接通） | restore / clearCurrentPlan / setSegmentLocked 已存在 |
| E01 | [开始 / 暂停 / 继续 / 返回保留专注](features/E01.md) | `ExecutionSessionDialog`，今日继续入口 / 时间块专注 | TaskViewModel、RoomExecutionSessionRepository、持久化会话 |
| E02 | [结束：继续进行 / 部分完成 / 完成 / 跳过](features/E02.md) | `ExecutionSessionDialog` 的结果区 | finishSession、ExecutionOutcome、反馈事务 |
| E03 | [结果后的剩余工作重排 / 取消保留当前计划](features/E03.md) | `TodayScreen` 结果区、PlanDraftDialog | generatePlanDraft 普通重排；执行 / 保护 / 历史规则 |
| S01 | [版本、版本码、构建来源显示](features/S01.md) | `MineScreen` 的 `app-version` | BuildConfig，Gradle git revision |
| S02 | [本地提醒开关、系统通知授权与拒绝提示](features/S02.md) | `MineScreen` Switch、根 permissionLauncher | ReminderSettingsViewModel、LocalReminderScheduler |
| S03 | [AI 总开关、首次授权与请求发送说明](features/S03.md) | `AiSettingsSection/AiConsentDialog/AiRequestPreviewDialog` | AiSettingsRepository、PlanningAgentWorkflow |
| S04 | [模型地址 / ID / Key、本机加密保存、清除 Key、测试连接](features/S04.md) | `AiProviderSettingsDialog` | AiProviderConfigViewModel、SecureAiProviderConfigRepository、Provider |
| S05 | [类别偏好展开、数字权重、真实保存与反馈](features/S05.md) | `MineScreen` 类别区 | CategoryPreferenceViewModel、RoomCategoryPreferenceRepository |
| S06 | [关于你的记录：生成、本地证据、修改结论、删除](features/S06.md) | `ProfileEvidenceSection/Card/EditorDialog` | ProfileEvidenceViewModel、RoomProfileEvidenceRepository |
| S07 | [导出本地 JSON，系统选择保存位置、成功 / 失败反馈](features/S07.md) | `LocalDataManagementSection`、CreateDocument launcher | DataManagementViewModel、RoomDataManagementRepository、LocalDataJsonExporter |
| S08 | [明确确认清除本地业务数据，取消提醒，防草稿回写](features/S08.md) | 根清除确认 Dialog、LocalDataManagementSection | clearAllTables、prepareForDataClear/finishDataClear |
| S09 | [每周课程 / 可用 / 休息 / 固定时间新增、编辑、删除](features/S09.md) | `TimeScreen/WeeklyBlockCard/WeeklyTimeBlockEditorDialog/DeleteTimeEntryDialog` | TimeViewModel、RoomTimeRepository、TimeConstraintRevision |
| S10 | [单日可用 / 不可用等例外新增、编辑、删除](features/S10.md) | `DateOverrideCard/DateOverrideEditorDialog` | TimeViewModel、DateOverrideDraft、RoomTimeRepository |
| S11 | [学期首周周一、学期周展示](features/S11.md) | `SemesterWeekCard/SemesterStartEditorDialog` | SemesterSettings、周模式计算 |
| S12 | [课表 PDF 读取、识别预览、选项、钟点核对](features/S12.md) | `TimeScreen` 文件入口、`TimetableImportReviewDialog` | PdfTimetableImporter、TimeViewModel |
| S13 | [导入课程逐项编辑节数、周模式、确认 / 取消 / 去重 / 回执](features/S13.md) | `ImportedCourseEditorDialog/TimetableImportReviewDialog` | TimetableReview、RoomTimeRepository 的确认事务 |
| S14 | [系统深浅色、字号 / 横竖屏适配、反馈与触控面积](features/S14.md) | `ui/theme/*`、TaskPageAppBar、EditorSheet、各原生 Sheet | Material 3、WindowInsets、原生返回 / liveRegion |

## 数值画像首批

| ID | 独立文档 | 实际状态 |
| --- | --- | --- |
| H01 | [证据与计算契约](features/H01.md) | v1 已冻结；工程门槛不作为人的评分。 |
| H02 | [计算、来源与缓存](features/H02.md) | 首批三个维度已有计算 / 持久化；后三维度未知，细粒度增量 / 大数据性能仍待。 |
| H03 | [数值与来源页面](features/H03.md) | 我的数值画像入口已接通；未知、资格、来源及停用分别展示。 |
| H04 | [只读 AI 描述](features/H04.md) | 有限契约、主动刷新、缓存和本地回退已有代码；真实描述服务成功未取得本批证据。 |
| H05 | [任务与草案联动](features/H05.md) | 一次估时采纳与草案引用已有；任务主表单内嵌入口仍待。 |
| H06 | [验收与证据](features/H06.md) | 当前源码单独复跑，历史 150 / 299 不替代本批结果。 |

## 能力边界

- 计划历史入口已接通，旧盘点的“不可达”已修复；恢复不回退状态或执行，不重新安排已结束任务，不启动过去时段。
- 普通任务重叠须确认；课程、固定事项、硬休息、锁定与不可避免保护先修改约束 / 解除保护。AI、本地与失败降级共用事务保护。
- 当前任务与执行来自显式操作；到点提醒、日程与 AI 文案均不是执行或完成证据。
- 本地规划为有限窗口的确定性贪心算法，容量不足明确保留未安排项；复杂截止 / 优先级联合优化不承诺全局最优。
- 旧三模式 / 使用事件只兼容与导出；旧 CompleteTaskDialog 等未调用组件不是额外已开放功能。
- JSON 导出不是完整可恢复备份；云同步、账号、外部日历双向同步和导入恢复不在当前已实现范围。
- 真机、真实学生 PDF、TalkBack / 平板 / 全部 OEM 与闹钟后台矩阵仍未验证。没有证据的入口不标作设备通过。
