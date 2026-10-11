# Repland 最终技术验收记录

开始：2026-10-10；更新：2026-10-11（Asia/Shanghai）。**0.1.35 首批数值画像与核心工作流的本机候选验收通过；提交 / 推送正在收尾，后置产品报告尚未开始。** 本页的代码、自动化、实际设备操作分列；历史结果不能替代当前构建。

## 来源、版本与环境

- 分支 `codex/placement-numeric-integrity`；验收时 HEAD `85c75f04117b9421786fa37fdaf1236481b3b865` 加保留工作区修改；验收后源码提交 `32d766f95224b3851dbb832bd9644b541b3436cf`，261 项源码 / 构建输入与验收清单逐文件一致。2026-10-11 fetch 成功，远程仍 `7506074f36d822b4c3d57777e40ba50a78ccdb15`，源码提交前本地 ahead 5 / behind 0；没有强制同步或覆盖修改。
- 当前已安装 `.current` 为 0.1.35-internal / 1035，Room 21。安装包 SHA-256 `9c5a54385aad72ca6e0d8ac2e4477862c83bb93ac4f23acccfd12722267341b2`。BuildConfig 来源为验收时基底 HEAD，工作区源码身份须结合本批清单，不能只用 HEAD 推断 APK 内容。
- Windows / PowerShell；JDK 17.0.20.1、Gradle 9.1、AGP 9.0.1、SDK 36 / Build Tools 36.0.0；项目 `outputs/Repland`，环境在相邻 `outputs/Environment`。标准离线 Gradle 构建，不使用诊断 R 符号桩代替 APK。
- 实际设备为 Repland_API_36 / emulator-5556 / Android 16 API 36 / x86_64 / 1080×2400 / density 420 / 2 GB / 4 核。真实 AI 复验时为 Asia/Shanghai；后续重启模拟器恢复 GMT，最终 310 项全套在 GMT 运行。验收结束重新校正为 Asia/Shanghai，并核对画像窗口与未知状态；不能将其写为跨时区完整矩阵。以后启动 AVD 应显式加 `-timezone Asia/Shanghai`。没有真实手机。
- 用户在 .current 内自行填写并确认加密保存配置。未读取、打印、导出或复制密钥；真实请求由应用读取已授权的本机配置。升级 1033→1034→1035 使用 install-r，数据目录与首次安装时刻不变。GMT / CST 格式时间不同源于时区转换。
- ui-ux-pro-max、emil-design-eng、apple-design 未在当前技能环境找到，已说明；沿用现有 Compose / Material 3 组件，只修业务回执与状态衔接。

## 本批实际修改

1. `TimelineDashboard.kt`、`ReplandApp.kt`、`TimeViewModel.kt`、`PlanViewModel.kt`：课程与精确时间调整等待真实事务成功后关闭；失败保留输入，忙碌时禁止重复动作；普通重叠确认后才回执，受保护约束继续拒绝。日历选中日期与编辑原始输入可重建恢复。
2. `AgentCenterScreen.kt`：自由输入的明确时段与确认所提交的时段统一；兼容旧草案，明确“新增事项”继续只存任务。未发送正文与独立制定计划分离，不让 AI 写数据库。
3. 新增实际 Activity 工作流测试：时间失败与重试、排序 / 计划 / 未发送正文隔离、五类保护拒绝、历史恢复；新增原生广播 → 通知验收，确认投递不启动或完成任务。执行夹具读当前约束选择空闲时段，保留任务 / 日志 / 历史。
4. 前阶段已实现的可信输入、来源迁移、C07 统一保护、数值首批代码继续保留，纳入最终源码与回归，没有重写或覆盖原未跟踪 C07 测试。
5. 原有 74 项功能文档补齐 11 项必要内容，新增 H01–H06 独立文档，共 80 项；旧盘点与交接原文归档保留，当前交接只保留状态、环境、证据、问题与后续任务。

## 核心工作流矩阵

“设备”指已标明包名的本机模拟器实际操作。仪器化 Activity 操作属于设备上的自动化操作；不能扩写为全部人工、手机或 OEM 验收。

| 用户入口 / 规则 | 方案 | 代码 | 自动化对应证据 | 实际设备与边界 |
| --- | --- | --- | --- | --- |
| 任务 → 新增：标题 / 一句话，可编辑标题，未知字段 | 确定 | 实现 | UnknownTaskWorkflowUiTest / TaskEditorWorkflowUiTest / TaskNameTest / Migration20To21Test | QA 实际创建与编辑 / 重建；.current 1035 真实 AI 确认后显示未分类、未设置优先级、预计天数未知；已知 30 分钟与明确今日时段单列。 |
| 任务 → 自动排序；助手 → 制定计划；未发送正文 | 确定 | 实现 | PlanningIsolationWorkflowUiTest / AgentCenterUiTest / PlanningWorkspaceRepositoryTest | QA 真正点击排序 / 应用 / 撤销；计划与事实不变；制定计划不发送或清除正文，重建与丢弃后正文仍保留。 |
| 日程 → 时间调整：普通重叠确认、取消、过期 | 确定 | 实现 | PlanningProtectionWorkflowUiTest / ManualPlanChangeRepositoryTest | QA 真实时间编辑与确认；普通重叠不作为同时执行证据。 |
| 日程 → 时间调整：课程 / 固定 / 硬休息 / 锁定 / 不可避免 | 确定 | 实现 | ProtectedOverlapWorkflowUiTest / UnavoidablePlanningTest / UnavoidableRepositoryTest | 五类已通过专项及最终全套的实际编辑拒绝，不同轨道也不能绕过；此前原生返回驱动失败另行保留。 |
| 提醒、主动开始、暂停、结果、补录、来源 | 确定 | 实现 | ReminderDeliveryIntegrityTest / ExecutionWorkflowUiTest / ExecutionSessionRepositoryTest / TaskFeedbackWorkflowUiTest | QA 原生通知投递与关闭拒收、真实开始 / 暂停 / 重建 / 返回 / 结果；.current 1035 安排后仍未开始且尚无执行记录。真实定时闹钟、重启与 OEM 后台未验证。 |
| 我的 → 历史：恢复排序 / 安排，状态与执行保持 | 确定 | 实现 | HistoryRestoreWorkflowUiTest / PlanRestoreRepositoryTest / PlanningProtectionWorkflowUiTest | 成功恢复与锁定拒绝均已通过实际 Activity 专项及最终全套；旧版本转为历史，原内容保留。过去时段不自动开始由源逻辑 / 仓储断言覆盖。 |
| 助手草案：自动保存 / 冷恢复 / 丢弃 / 确认 / 重检 | 确定 | 实现 | PlanningIsolationWorkflowUiTest / DraftSaveWorkflowUiTest / PlanningWorkspaceRepositoryTest / C05 来源测试 | .current 1035 真实 AI 草案冷启动恢复，确认前任务库无新任务；QA 编辑失败 / 重建 / 丢弃。正常“放弃本次调整”会丢弃规划草案；读取失败时提供保留关闭，不笼统称所有关闭都保留。 |
| AI：有限契约、确认后生效、失败保留与降级 | 确定 | 实现 | CompatibleTransportTest / ArrangementAssistantAdviceValidatorTest / AssistantSaveReceiptTest / AgentCenterUiTest | .current 1035 使用已保存配置，AI 返回明确 16:00 / 30 分草案；冷启动后确认回执新增 1 / 写入 1，与日程一致。只验证本次提供方与请求；多服务真实失败矩阵未全测。 |
| 我的 → 数值画像：来源、未知、快照描述、任务联动 | 首批确定 | 首批实现 | NumericProfileTest / NumericProfileRepositoryTest / NumericProfileWorkflowUiTest / NumericProfileAiWorkflowTest / NumericNarrationContractTest | 首批估时、执行段、类别偏好与只读描述 / 引用；后三维度未知。QA 有来源排除 / 停用 / 采纳实际操作；.current 1035 实查估时 / 执行段 0 样本未知、默认类别权重明确标注、未开始任务未纳入。真实描述服务成功、主表单内嵌与大数据性能未验证 / 未完成。 |

## 构建与测试记录

原始日志、APK、XML 和截图本地位于 `android/app/build/reports/final-delivery-2026-10-10/`，build 目录不随克隆自动得到。可携带的用例结果、来源清单、哈希、失败历史与非敏感截图已汇总在 [docs/verification/final-0.1.35](verification/final-0.1.35/README.md)；克隆后可获取。原始日志 / APK 另在本地保留。

| 批次 | 实际结果 | 解释 |
| --- | --- | --- |
| 0.1.33 历史 | 150 单元 / 299 设备全套通过 | 仅适用于该批保留源码与 APK，不替代本批。 |
| 时间失败复现 | 2 项 / 2 失败 | SQLite 拒写后表单提前关闭，原正式计划未变。 |
| 0.1.34 修复专项 | 13 / 0 失败 | 两条真实回执、日程组件与保护确认通过；150 单元通过，internal 成功。 |
| 0.1.34 真实 AI | 返回成功但确认只写任务 1 / 时段 0 | QA-070 真实阻断，原 APK 699a54… 独立保留。 |
| AI 意图复现 | 2 项 / 1 失败 | 新回归复现预览 1 / 实际 0；原生通知完整通过。 |
| 0.1.35 相关专项 | 28 / 0 失败 | 自由输入在线 / 离线、恢复、明确收集、回执与保护等；150 单元通过。 |
| 0.1.35 首轮全套 | 307 / 1 失败 / 0 错误 / 0 跳过 | 固定时间执行夹具与保留安排碰撞；不绕过普通重叠规则。 |
| 空闲夹具 / 规划隔离 / 五类保护专项 | 4 / 0 失败 | 同一生产源码；internal、release Kotlin / Manifest、离线边界及构建清单成功。 |
| 0.1.35 真实 AI 复验 | 通过指定工作流 | 未确认候选不写任务；冷启动恢复后回执 1 / 1；日程可见，任务未开始、执行记录为空；配置升级后可用。 |
| 0.1.35 第二轮全套 | 309 / 2 失败 | 历史断言误比 isCurrent；Espresso 返回键选错 Native Sheet 窗口。两处验收代码已修正，5 项历史 / 保护 / 保存回执专项及最终全套通过。 |
| 保存途中重建 / 历史 / 保护专项 | 5 / 0 失败 | 三条实际保存回执（含挂起保存跨 Activity 重建）与历史 / 五类保护通过；没有为重建新增产品修复。 |
| 310 项第一次全套 | 309 通过 / 1 失败 | 任务 AI 发送后确认入口超时；相同 APK 原测试单项通过，根因未定位（QA-072）。验收改用已有建议按钮进入同一契约，并新增计划不变断言。 |
| 310 项第二次全套 | 309 通过 / 1 失败 | 画像仓储已为 2 样本，页面断言仍读取 3 样本旧帧；改为等待页面确实显示 2 / 3 样本与对应状态，未改计算规则（QA-073）。 |
| **最终全套** | **310 / 0 失败 / 0 错误 / 0 跳过** | `final-310-verified.log`，设备用例合计 413.933 秒；Gradle 整体 7 分 34 秒，构建清单成功。以实际 XML 时间为准，源码未再修改。 |

早期测试编译错误（测试 lambda 分隔符、GrantPermissionRule / assertExists 导入）、重启时设备未上线等日志也保留；这些运行未获得设备通过结果，不能计入通过数量。

## 未验证边界与后续事项

- 没有真机升级、完整历史 1→21 用户库、真实学生 PDF / 扫描件、全部系统文件选择器、TalkBack / 平板 / 硬件键盘与全部弹层组合证据。
- 原生通知广播已实际投递并验证无执行副作用；AlarmManager.set 的自然到点、重启、时区、Doze 与 OEM 后台矩阵仍待，不承诺精确到秒。
- 真实模型只验证当前配置的指定流程；数值 AI 描述成功与所有服务故障矩阵仍未取得真实证据，有限契约与回退由隔离自动化验证。
- 数值画像后三维度未知；任务主创建 / 编辑表单内嵌建议、细粒度增量与大数据性能仍待。当前规划不因参数变化自动重排，不累乘 / 不重复类别权重。
- QA-062 / QA-066 / QA-067 / QA-072 的间歇点击、空闲 / 线程根因未定位；成功重跑不能关闭这些 P2 风险。QA-073 为验收等待层级问题，等待实际 UI 状态后最终全套通过。
- 当前 .current 仅保留两项名称明确为 QA 的本批验收任务；没有因安排产生执行记录，没有清数据 / 卸载 / 覆盖旧保留版本。
- 本机候选范围 QA-069 / QA-070 核心阻断已关闭，当前全套通过且安装 APK 与最终 internal 哈希一致。150 JVM 测试是同一生产源码的实际结果，最终设备运行期间仅修改测试驱动与文档；没有把旧 0.1.33 结果算作本批。本轮交付采用正常 Git 操作，最终 HEAD 与远程核对结果以 Git 回执为准；冲突 / 授权失败即停。
- 技术验收完成后已查询四款类似产品的当前官方资料，并形成[产品与交互意见报告](REPLAND_PRODUCT_INTERACTION_REVIEW_2026-10-11.md)；竞品未设备实测，建议未自动实施。

## 提交前复核（2026-10-11）

261 项源码 / 构建输入与验收清单一致；80 份独立功能文档均具备 11 项内容及四层状态；当前文档 915 个相对链接有效。归档 76 份正文完整保留：72 份与旧提交一致，4 份另保留此前阶段的进度说明。公开源码 / 文档 480 文件凭据特征扫描无命中；未访问应用配置或数据目录。原有 C07 测试原始哈希保持不变。

提交前再次 fetch 成功，源码提交后相对远程 ahead 6 / behind 0，无后续远程提交待审查。文档收尾没有更改生产或测试源码，不重复构建替代已有同源结果。模拟器按 Asia/Shanghai 重新启动，实际当前包仍为 1035，首次安装与更新时间保持；仅启动 .current，QA 停止，没有清数据或卸载。

## GitHub 交付阻断（2026-10-11）

本地源码提交 `32d766f`、验收 / 文档 / 报告提交 `de4513d63fe0cf908beef73b2385aaa9c08f3ac0` 已完成。两次正常推送直连 GitHub 443 失败；仅对后续 Git 命令使用 Windows 已配置的本机 HTTP 代理，未改全局 Git 或网络设置。连接后远程返回 `Permission to Swan1127/Repland.git denied to Henry-Yang666` / HTTP 403，**推送未完成**。

按用户约束停止推送，不自行切换账号、修改权限或强制处理。后续须在本机完成具有仓库写权限的身份授权，再核对远程分支并正常推送；不要求发送凭据。本记录追加为独立文档提交，生产源码、已验收构建和测试结果不变。
