# C04 — 未知任务字段与输入来源

更新：2026-10-08（Asia/Shanghai）。本批候选 0.1.30 / 1030，Room v21。权威规则为产品交付计划第 3.0 节；数值画像 H01–H06 尚未实现。

## 用户入口与前端

今日 / 任务新增的 TaskCaptureSheet 以一句事项保存；TaskName 生成可编辑标题。详情 TaskEditorDialog 允许天数留空，重建保留原输入。类别有“未分类”，优先级有“未设置”；“不可避免”名称在现有资源与快速收集统一。旧字段来源不明在任务详情标明。助手批量确认默认不填中优先级或 1 天。

前端：ui/components/TaskExperienceComponents.kt、ui/ReplandApp.kt、ui/agent/AgentCenterScreen.kt、ui/schedule/DailyDesk.kt、TimelineDashboard.kt。沿用 Compose / Material 3；本机 ui-ux-pro-max、emil-design-eng、apple-design 未找到，没有冒称读取或完成技能验收。

## 业务逻辑与数据来源

- TaskCategory.UNSPECIFIED / TaskPriority.UNSPECIFIED 表示缺失选择；estimatedDays 使用 null。TaskDraft 的可选输入默认未知；标题或描述至少一项非空。负数、0、溢出或超范围天数仍拒绝，不能借未知绕过数字校验。
- TaskInputSources 记录类别、优先级、天数、总时长、截止日期、计划日期六项来源。UNKNOWN / USER_INPUT / ACCEPTED_SUGGESTION / LEGACY_UNVERIFIED 分开；六项按固定顺序存储，当前存储契约随 Room v21 引入。非法来源读取失败，不回退为用户确认。
- 新增和替换保存字段来源；普通编辑保留未变字段来源，实际改变的字段使用新来源。已有明确初始优先级仍遵守现有保护，未知优先级可以明确补齐。同值旧字段不会仅因保存编辑器而变为已确认来源。
- 快速收集草稿 codec v2 保留类别 / 优先级来源；v1 恢复原值并标记来源不明。saveable 状态同步保存来源，不因 Activity 重建变成用户选择。
- 本地排序：未设置优先级使用现行中性 50，未分类的类别因子使用中性 50；理由显示未知，没有写回 MEDIUM 或 COURSE。类别偏好仍只有原四类，不增加画像权重。
- 助手确认后的类别 / 时长等记录为已采纳建议；不在预览阶段建正式任务或画像证据。无类别回复保留 UNSPECIFIED；模型请求携带必要字段来源，明确旧来源不是用户声明。
- 导出保留未知、旧原值与字段来源，不导出 API Key。既有文字画像不再以来源不明的旧课程类别构造课程学习证据；一般用户确认反馈仍保留。

领域：Task.kt、TaskCaptureDraft.kt、TaskInputSources.kt、PlanningPriority.kt、PlanningRevision.kt、AiAdvisorContract.kt、ArrangementAssistant*.kt、PlanningReadService.kt、ProfileEvidence.kt。持久化：RoomTaskRepository、RoomTaskCaptureRepository、TaskEntity、TaskDao、ReplandDatabase；导出：LocalDataJsonExporter。

## 安全迁移

20→21 在 Room 升级事务中重建任务表以允许未知天数；复制所有原任务列，只新增来源列，不删除业务行，不改课程、计划、反馈、工作区或设置。旧值包括 COURSE / MEDIUM / 1 原样保留，来源空编码解码为 LEGACY_UNVERIFIED；不能推测哪些旧默认是用户填写。注册迁移链，没有 destructive fallback。

Migration20To21Test 使用独立 v20 数据库形状验证旧任务、计划、反馈、类别设置及同值编辑来源保留，新未知任务可保存。Migration5To6Test 验证 5→21 链和 nullable 天数。没有用户旧设备数据或签名，本机新 AVD 的测试不替代真实用户原位升级矩阵。

## 测试与构建证据

- 基线 7506074：新增最小录入与纯描述校验两个单元失败，原 127 项通过。原 XML 保留 build/reports/continuation-2026-10-08/c04-baseline-failure.xml。
- 修正后 132 单元通过；新增旧类别画像排除测试后继续最终检查。设备专项 20 / 0 failures / 0 errors / 0 skipped，通过持久化、导出、v1 草稿、5→21 / 20→21、页面录入与重建编辑、既有编辑回执和解析。
- 首轮 19 专项中 UI 测试因固定按钮没有滚动父容器失败；修正驱动为可见断言与真实点击，未改产品绕过。原记录 c04-targeted-driver-failure.xml；成功记录 c04-targeted-20-pass.xml。
- 以上为实现工作区专项；最终源码、APK 哈希及完整回归结果在完成后追加，不将专项等同全部工作流通过。

测试：TaskCaptureDraftTest、TaskInputSourcesTest、ProfileEvidenceGeneratorTest、TaskCaptureRepositoryTest、UnknownTaskWorkflowUiTest、Migration20To21Test、Migration5To6Test、DebugAgnesArrangementAdvisorParserTest、TaskEditorIntegrityTest。

## 未验证与后续

### 2026-10-08 本机续作证据

接手本地 HEAD 为 fb2f4fe，工作区干净；缓存远程引用为 7506074。fetch 在沙箱与用户环境、HTTP/1.1、Windows TLS 后端均连接重置，不能据此声称远程没有新提交；尚未推送。本机 JDK 17.0.20.1、SDK Android 36、Repland_API_36 AVD 已核对；ADB/Gradle 需要用户环境运行，R: 短路径与本次进程 safe.directory 用于构建，不更改全局 Git 信任。

- fb2f4fe：133 单元 / 0 失败、错误、跳过。CoreWorkflow/UnknownTask 九项实际页面专项在 b030e93 的工作区修改上通过（24.098 秒）。追加完整筛选 / 滚动定位，保留所有业务断言；截止日明确为今日、计划日期仍未知，未修改产品日期规则。
- b030e93 完整设备 272 / 1 失败 / 0 错误 / 0 跳过（167.28 秒）；失败为 UnknownTaskWorkflow 的真实任务卡点击后详情未出现。XML 为 build/reports/continuation-2026-10-08/c04-b030e93-full-failure.xml。
- 单项原生几何复测通过；f18d085 增加任务卡完整位于列表的断言和点击前绘制截图。该源码完整设备仍为 272 / 1 失败 / 0 错误 / 0 跳过（161.392 秒），同一详情进入失败，几何断言通过。XML 为 c04-f18d085-full-failure.xml。不能把绘制同步当成已经修复问题。
- 早期九项中的截止日断言误把计划日期也设为今天，已纠正为未知；后续另一个用例在总览前三项查卡超时，改为完整筛选定位。原记录 c04-deadline-assertion-failure.xml / c04-overview-limit-failure.xml 保留。
- Gradle 结束后无法读取 QA 截图目录，改为同 APK 直接 instrumentation 全套取证，仍在定位。完整 C04 验收尚未通过，未升级或交付内测当前包。此前 20 项专项与旧全套不能替代本轮失败结果。

本机未找到 AGENTS.md 或 ui-ux-pro-max / emil-design-eng / apple-design 技能；未冒称使用。C07 的领域 / 事务 / 界面确认复现准备后继续，H01–H06 仍未实现。

完整首跑 272 项中 270 通过、2 失败、0 skipped：CoreWorkflowUiTest 在未等待步骤更新时点击不存在的时长入口；新未知任务测试在只展示前三项的总览寻找后排任务。原 XML c04-full-first-failure.xml 保留。分别补步骤出现等待及真实“待安排”筛选 / 列表滚动，所有状态、来源、时长与计划不变断言保留；不删除、跳过或弱化业务断言。完整同源复跑待记录。首次构建在源码提交身份修正前启动，不能将其 BuildConfig 来源或未完成产物作为最终候选。

真实用户设备 20→21 原位升级、真机显示 / 无障碍、真实模型请求仍未验证。旧字段同值来源重新确认入口未新增；目前保守保留来源不明。数值画像原估计 / 类别观察时点尚待 H01 建立，不以本批当前字段来源替代历史时点证据。不可避免跨路径保护、普通重叠、历史入口与草案规则按各自批次继续。
