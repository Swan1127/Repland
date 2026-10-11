# M4-P 数值画像：2026-10-10 实施与技术验收

本轮完成首批数值画像实现与本机技术验收：正常 Gradle 150 单元、最终 299 设备全套均通过，最终 `.current` 0.1.33 已原位安装并核对 APK 身份。下方保留每次失败与旧环境历史；首批本机通过不代表全部目标规格、真实 AI 或所有设备已验收。

## 当前来源、范围与环境

- 实际分支 `codex/placement-numeric-integrity`；HEAD `85c75f04117b9421786fa37fdaf1236481b3b865` 加保留工作区修改及新增源码，未 commit / push。重新 fetch 成功，远程仍为 `7506074f36d822b4c3d57777e40ba50a78ccdb15`，本地领先 5，无新远程内容。
- 声明版本 `0.1.33 / 1033 / Room 21`。最终源码文件清单、补丁、未跟踪源码、APK 和报告统一保留于 `android/app/build/reports/h01-final-2026-10-10`；HEAD 单独不能还原本批构建。
- Windows / PowerShell、Zulu JDK 17.0.20.1、Gradle 9.1.0、AGP 9.0.1、SDK / target 36 / build-tools 36.0.0。正常离线 Gradle、单 worker、Kotlin 进程内、关闭本批 KSP / Kotlin 增量；未使用诊断 init 脚本或 R 符号桩制作 APK。
- 本任务 AVD `Repland_API_36` / `emulator-5556`，Android 16 x86_64、设备时区 GMT（主机 Asia/Shanghai，未擅自更改）、1080×2400、density 420，WHPX 可用，保留 userdata、禁加载 / 保存快照、软件渲染。另一既有 `Pixel_6` / `emulator-5554` 为 offline，未安装、清除、关闭或替换它。
- 请求的 `ui-ux-pro-max`、`emil-design-eng`、`apple-design` 在技能目录和插件缓存均未找到；已说明，沿用 Compose / Material 3 / EditorSheet，本批没有新增手势。
- 未读取、打印、导出或提交真实 API 密钥，未发送真实 AI 请求。测试使用隔离 fixture 和 fake advisor；运行时代码仅在 App 经明确授权主动请求时读取配置。

## 首批实现与验收映射

| 项目 | 实际能力 | 验证与边界 |
| --- | --- | --- |
| H01 | [numeric-v1 计算契约](H01_NUMERIC_CONTRACT.md)：90 本地自然日、0 未知 / 1–2 参考 / ≥3 可建议、任务 / 会话去重、同类别中位数、R7 四分位数 | 纯领域数值与未知 / 停用 / 不累乘夹具；门槛是工程口径，不是科学阈值 |
| H02 | 首次明确开始同事务冻结可信原估计 / 类别；明确累计总投入单独补录并绑定执行日志；更正失效；快照持久化、来源排除 / 停用、移除确认、显式安全导出 | Room 的冻结、类别改动、重复采纳、日志更正、重开数据库、坏版本 / 写失败、窗口过期 / 未来证据测试；旧记录不猜测补齐 |
| H03 | 我的 → 个人规划画像：数值、样本数、窗口 / 更新时间 / 版本 / 来源、默认与明确类别权重、排除 / 停用、输入确认、累计补录、任务记录入口 | 实际 Activity 点击排除 / 恢复、非法 -1 原样拒绝、补录、取消 / 采纳与重建；截图检查输入和按钮在 IME 下可见。仅本机正常竖屏范围 |
| H04 | 指定快照、有限只读 JSON、ID / 精确值验证、主动生成、版本缓存、授权 / 配置 / 取消 / 新证据守卫，失败明确 LOCAL | fake AI 的开关 / 同意 / 配置变化与迟到响应、无自动请求、非法输出回退、最小载荷；真实供应商响应 / 网络矩阵未验收 |
| H05 | 来源任务卡单独确认估时建议，未修正基准仅乘一次；可选执行段拆分参考；新规划草案携带参数版本 / 引用、最终事务拒旧 | 任务与执行记录 / 已确认计划不被派生计算修改；采纳只改目标预计；关闭 / 读取失败继续基础规划。任务主表单无独立内嵌按钮，拆分不自动执行 |
| H06 | 正常 Gradle / Room / 迁移 / 前端工作流 / fake AI / 请求体量对照与当前包技术验证 | 最终结果见下表；自动化不替代所有真机、字体 / 横屏、通知或真实 AI 验收 |

其余三维度（重排 / 中断、明确时段偏好、安排方式）按计划首批边界显示未知 / 待可靠证据，不从延期、拖动、未确认草案或 AI 推断补齐。旧“关于你的记录”保留为历史文字，不能成为数值证据。统计计算和文字摘要均不评价人格、能力、自律或健康。

主要源码：`domain/model/NumericProfile.kt`、`domain/ports/NumericProfileRepository.kt`；`data/room/RoomNumericProfileRepository.kt` / `NumericProfileCodec.kt` 及开始 / 结束事务；`ui/profile/NumericProfileViewModel.kt` / `NumericProfileSection.kt`；remote `CompatibleNumericProfileAdvisor.kt`；`PlanningOperationService` / `PlanDraft` / `PlanDraftCodec` / `RoomPlanRepository`；`LocalDataSnapshot` / `RoomDataManagementRepository` / `LocalDataJsonExporter`。沿用 Room 21 workspace 的版本 1 记录，无破坏性数据库升级。

## 最终验收表

| 验证项 | 实际结果 / 证据 | 边界 |
| --- | --- | --- |
| 正常资源与构建 | AAPT2最小link及正常资源重跑成功；debug QA / androidTest / internal APK已生成；release Kotlin / Manifest、verifyOfflineMvpBoundary通过 | 未签名发布release；未使用R桩；final-small-values.log整体命令曾因设备失败返回失败，成功任务单列 |
| JVM | 正常Gradle 150项，失败 / 错误 / 跳过0；jvm/*.xml | 显示修正后生产源码，此后只改QA测试驱动 |
| 完整设备 | 最终299项，失败 / 错误 / 跳过0，BUILD SUCCESSFUL 4m52s；final-receipt-full.log / final-device-299.xml | 本机API36，旧失败保留，不能据此关闭间歇风险 |
| 新规则专项 | 画像设备15项（仓储11 / fakeAI1 / 契约2 / UI1）、扩展v20→21迁移均包含全套；保留数据专项5/5、核心收据专项1/1 | 不以专项替代全套，不穷举所有旧库 |
| 实际画面 / 操作 | numeric-profile-*-final.png已查看；0样本未知 / 3样本1.5、非法-1、键盘按钮、取消 / 采纳、不改计划、<0.01 | 标准竖屏；真机 / 大字号 / 横屏未据此验收 |
| 原位安装 / 冷启动 | install-r Success；最终安装base.apk哈希与internal一致；1033/0.1.33-internal；firstInstallTime=2026-10-09 15:32:26、dataDir原样；首页 / 我的 / 画像 / 冷启动后未知快照保留；只有current PID | 本机1032→1033；迁移夹具验证任务 / 课程 / 固定 / 计划 / 反馈 / 设置，不称真实用户全库逐条检查 |
| 源码身份 | HEAD加工作区、231文件SHA256、tracked补丁与新增源码副本；原UnavoidablePlanningTest哈希不变 | 未commit/push；页面HEAD不能单独还原此包 |

最终 APK SHA256：

- `app-debug.apk`：`a908473fb8a19e5e1cb893653471172262b355049cbd9ce5d888056164f4ca31`。
- `app-debug-androidTest.apk`：`c0cca103d1e913bf3aa99ced87da2210f3c909506b79c7b65af1136f4d4a71b7`。
- `app-internal.apk`：`5cfe1e51896c395b8a5e2b0938e3087e5c184b42527fd839b8440f7d05dea5b6`。

完整证据：`android/app/build/reports/h01-final-2026-10-10`。生产APK在显示修正后生成；其后仅修正保留QA数据时的测试导航 / 异步等待，最终测试APK与最终源码清单一致。current-final-*及current-profile-cold-final.*为最终安装 / 冷启动证据；其他Repland包保留安装并停止运行。


## 新增复现、修正及失败记录

本机详细原日志目录 `android/app/build/reports/h01-host-verification`，最终独立目录保存引用的副本。

1. QA-063：旧文字将两条口径未知的 30 分钟反馈相加为 60。先建立失败再最小修正为单条说明未知口径、多条仅计记录数；旧已保存文字保留。
2. QA-064：在新的 unrestricted 执行环境，空 Manifest 的最小 AAPT2 link 返回 0，正常 `processDebugResources --rerun-tasks` 及完整 APK 构建成功，accel-check 返回 0。先前原生访问异常的根因不扩大为 App 资源错误；旧受限日志仍保留。
3. QA-065：先复现同一天未来累计确认到达有效时间后缓存仍未知（12 项专项中 1 失败），在版本中加入证据到时资格及本地时区；复查又发现时钟回拨使开始快照仍在未来却提前纳入，新增断言失败后补 `original.at <= now`。现在时间边界变化会使旧快照 / 文案版本失效；重新读取 / 计算时检查，不新增后台定时服务。
4. 首次完整 298 项 / 1 失败：`PlacementNumericUiTest.event_display_and_collision_use_actual_chunk_not_task_total` 等待 Compose 空闲超时。同一 APK 直接单项 1/1 通过（3.188 秒）；未改该产品和测试源码、未定位根因或取得失败瞬间画面。记 QA-066，不能因后续成功宣布已修复。旧 QA-062 详情点击风险独立保留。
5. 扩展时钟断言后的 299 项 / 8 失败：1 项为上述未来开始快照真实缺陷，2 项执行测试与上一轮自身留下的日程冲突，5 项反馈测试被总览每组仅预览三项的真实 UI 限制挡住。保留数据暴露了旧测试依赖空环境的问题。仅调整 QA 夹具：通过真实受保护操作移除自身普通安排（保留任务 / 执行记录 / 计划历史），每次重读新计划的分段 ID；反馈从真实“今天”筛选进入。画像 UI 新夹具使用确定的新来源顺序，不删除旧夹具。
6. 首次修正专项 23 项 / 1 失败：整理夹具时第二个旧 segment ID 已因新计划生成而过期，正式事务正确拒绝；改为每次重新读取，不放宽生产校验。其余 22 项包括画像仓储、迁移、载荷、真实画像 UI 及反馈工作流均通过。后续完整结果单列，不用局部通过替代全套。
7. **安装保留例外**：首次使用 AGP 默认 connected test 时，结束清理自动卸载了本轮新装的隔离 `.qa` / `.qa.test`，截图随 QA 存储丢失；`.current` 1032 未受影响。随后依据本机 AGP 选项设置 `android.injected.androidTest.leaveApksInstalledAfterRun=true`、`android.experimental.testOptions.uninstallIncompatibleApks=false`，后续运行保留 QA 安装和数据，不以卸载 / 清数据解决重复回归。脚本默认匹配 5556 / 指定 AVD，版本冲突直接停止。

8. 最终生产源码首次完整 299 项 / 1 失败：核心反馈 UI 点击 `feedback-confirm` 时出现 SnapshotStateObserver 主线程 / instrumentation 线程观测交叉异常。TestRunner 堆栈保存到 `core-snapshot-thread-extract.txt`，定位第 194 行；同包单项 1/1 通过（8.624 秒），该测试 / 产品未改，QA-067 根因未定位，不能称修复。后续同包全套单列。

9. 上述同包全套复跑 299 项 / 2 失败：NativePageNavigationUiTest 与 ReadFailureWorkflowUiTest 同样在三项总览预览中找不到新夹具。补真实“查看全部”导航 helper，并核对其余同类入口；TaskEditorWorkflowUiTest 复用 helper。保留原操作 / 返回 / 身份 / 输入与失败断言，仅测试驱动变化，生产三个构建变体未改变。此轮失败 XML 与旧测试 APK 独立保留；最终结果绑定新的测试 APK。

10. 完整 299/0 后的实际截图复核发现 QA-068：正的毫秒级执行段显示为“0 分钟”。原图与已通过的旧 APK / XML 移至独立 `before-small-values`，只将正且小于 0.01 的显示值改为 `<0.01`，增加 1 项展示单元测试。数值 / 样本 / 采纳 / 计划均未改；未知与真正的零分别保留。最终结果绑定重新编译的新 APK，不能复用修正前 149 / 299 的验收。

11. QA-068 显示修正后的首次全套 299 项 / 3 失败，已保留 `small-values-first-299.xml`：PlacementNumericWorkflowUiTest 反复留存自身的普通安排后找不到预设 240 分钟空档；SessionReadIntegrityTest 只监听读取流却在谓词中读取 busy / finished，独立状态先后发出时等待无法唤醒；UnknownTaskWorkflowUiTest 在保留数据列表滚动后仍要求短暂 Snackbar 可见。分别只整理该 QA 夹具自己的普通安排（保留任务 / 日志 / 历史）、改为 combine 相关状态且每步等忙碌结束、在收据出现时断言并保留其可见期间的遮挡几何断言。没有延长超时或删除业务断言；生产 APK 未变。首次专项启动前 5556 已退出，Gradle 报 device not found，0 项执行；原因未定位，保留 emulator 日志后按同一 AVD / userdata 无快照重启，不清数据。专项首次 5 项 / 1 失败：出场动画中的 Snackbar 尚在语义树但已不可见；收据在出现时已通过可见断言，后续只在实际可见时检查遮挡几何。修正后 5 项全过（52 秒），完整结果另列。

12. 保留 QA 数据的最终全套再跑 299 项 / 1 失败（`retained-full-first-299.xml`）：CoreWorkflowUiTest 在长列表滚动之后检查已消失的短暂保存收据，失败在第 184 行，区别于 QA-067 的线程异常。移到真实保存收据出现时校验原文，再执行原列表导航、真实反馈写入与状态断言；没有修改生产源码。核心收据专项1/1通过（50秒）；最终全套299/0通过（4m52s），分别绑定更新的测试APK。

## 当前包截图复核

最终源码 QA 实际截图另存 `numeric-profile-*-final.png`，已逐张查看：休闲类别 3 个有效样本系数 1.5，其余无样本类别保持未知；累计总投入原始 `-1` 保留并显示未保存错误，键盘打开时取消 / 确认按钮完整可见；估时 20×1.5=30 待确认并明确不改变当前计划，极短真实执行段的拆分参考显示 `<0.01 分钟`。来源为隔离合成夹具，不冒称实际用户画像或完整字体 / 横屏矩阵。显示修正前旧图保留为 QA-068 证据。

## 隔离请求体对照

同一组 12 个合成任务，冻结原估计 / 类别与明确累计总投入构成完整必要证据，实际 remote adapter 的结构化参数载荷为对照。UTF-8 JSON 实测：必要证据 **2872 字节**，参数摘要 **354 字节**；课程比率中位数 **1.5**，数值一致，有限回复只引用请求参数，载荷不含任务标题 / ID 或原始日志。结果见 `numeric-payload-comparison.json`。这是合成夹具的传输体量及事实契约验证，不是实际供应商调用、token / 费用节省或建议质量等价的证明。

## 关联核心规则复核

| 规则 | 本批检查 / 证据入口 | 限制 |
| --- | --- | --- |
| 未知字段、默认与明确输入分开，未确认信息不是画像证据 | v20→21 迁移扩展、UnknownTaskWorkflowUiTest、历史未开始快照不得补造、当前输入确认不改历史 | 没有穷举所有旧版本数据库 |
| 只主动开始 / 明确补录形成执行证据，暂停与反馈不双计 | ExecutionSessionRepositoryTest、CoreWorkflowUiTest 的无计时完成、画像轮次 / 累计更正测试 | 真实通知投递不在本批验收中 |
| 历史恢复不回退状态 / 日志、不重排结束任务或重启过去时段 | PlanRestoreRepositoryTest 及实际保护 UI 工作流 | 不将恢复当执行纠正 |
| 不可避免与硬保护跨本地 / AI 降级 / 最终事务一致，普通重叠需确认 | 保留原 UnavoidablePlanningTest SHA256 `85611f9da5c034ad7dfc2cf0c85f5ec1471501609717cf7b8ba1c092068a6be5`、UnavoidableRepositoryTest、PlanningOperationServiceTest、PlanningProtectionWorkflowUiTest | 真实在线模型的完整输出矩阵仍待 |
| 排序只改列表；草案确认前可恢复，旧来源拒写；制定计划独立于输入 | PlanningOperationServiceTest、PlanningWorkspaceRepositoryTest、DraftSaveIntegrityTest / DraftSaveWorkflowUiTest、AgentCenterUiTest | T08/T10 全部失败回执范围没有据此关闭 |
| 画像建议可编辑 / 单独采纳，AI 只读契约，不改变当前确认计划 | NumericProfileRepositoryTest、NumericProfileWorkflowUiTest、NumericProfileAiWorkflowTest、NumericNarrationContractTest | 无人格评分；后三维度未知，真实服务与主表单内嵌入口另列 |

单一模式、旧模式兼容 / 导出及统一“高 / 中 / 低 / 不可避免”继续沿用上一阶段实现。本批未恢复切换入口、未删除旧数据或改写类别排序权重；全套结果只覆盖报告列出的源码与设备。
## 后续顺序与未验证范围

1. 保留 QA-062、QA-066、QA-067 间歇 UI 风险；后续若再现需捕获失败时画面 / UI 树 / 线程与对应 APK，不靠增加超时或删用例关闭。
2. T08/T10 完整失败回执及相关链路仍按原交接待办推进；不将本批画像回执扩大为全应用所有写路径已验收。
3. 真实供应商 / 弱网 / 超时 / 在线规划保护、真实通知 / 进程生命周期 / 手机矩阵、紧凑窗口 / 大字号 / 横屏 / 无障碍矩阵独立待验收。本批没有真实 API 配置或密钥检查。
4. 本机 `.current` 的 install-r 只证明该已安装版本原位升级 / 启动；迁移夹具具体证明任务、课程、固定事项、历史计划、反馈和设置保留，不声称检查了真实用户全库每条数据或跨版本全历史。
5. 当前按来源 / 设置变更触发事务内重算并使用内容版本缓存，尚无单维度增量算法或大数据性能验收。后续画像维度及主任务表单内嵌建议入口按产品优先级处理；不做人格 / 能力 / 自律 / 健康评分、用户招募 / 7 天试用 / 留存研究、额度监控、自动关机或无关功能。

## 历史：此前受限执行环境的记录

以下完整保留当时结论，仅适用于当时环境 / 源码 / 验证方式，不覆盖上方最新验收。

# M4-P 数值画像：2026-10-10 实施与验证边界

本批尚未完成技术验收。H01 契约与首批 H02–H05 源码已落地；H06 只有源码编译 / JVM 验证，正常 APK 打包受本机 AAPT2 异常阻塞，设备测试、UI、升级和最终当前版安装均未完成。不得以这份文档、编译通过或旧包回归代替设备验收。

## 来源与环境

- 分支 `codex/placement-numeric-integrity`，HEAD `85c75f04117b9421786fa37fdaf1236481b3b865`，加工作区保留修改及本批新增源码，未 commit / push。
- 本阶段已成功 fetch（HTTP/1.1），远程 `origin/codex/placement-numeric-integrity` / FETCH_HEAD 均为 `7506074f36d822b4c3d57777e40ba50a78ccdb15`，没有后续远程提交；本地领先 5。原交接文档“fetch 失败”属于上一阶段历史。
- Windows / PowerShell；Zulu JDK 17.0.20.1、Gradle 9.1.0、AGP 9.0.1、SDK / target 36、build-tools 36.0.0；离线构建、单 worker、Kotlin 进程内、关闭本批 KSP / Kotlin 增量。
- 声明候选版本 `0.1.33 / 1033 / Room 21`，没有完成该版本可验收 APK。上批已安装 `.current` 0.1.32；本批 `adb devices -l` 无连接设备，未确认设备当前版本、未升级、未启动旧版本、未卸载或清数据。
- 所需 `ui-ux-pro-max`、`emil-design-eng`、`apple-design` 在技能目录与插件缓存中均未找到，已说明；采用现有 Compose / Material 3 / EditorSheet。本批未新增手势。
- 没有读取、打印、导出或提交真实 API 密钥，没有真实 AI 请求。测试使用假配置与 fake advisor；远程适配器的密钥读取仅为 App 经授权请求时的运行时代码。

## 实际实现

| 项目 | 源码能力 | 本批验证状态 |
| --- | --- | --- |
| H01 | [版本化计算契约](H01_NUMERIC_CONTRACT.md)：90 天、0 未知 / 1–2 参考 / ≥3 建议、按任务去重与类别中位数、R7 四分位数 | 纯数值夹具通过；工程门槛不代表统计显著性 |
| H02 | 首次明确开始同事务冻结可信原估计 / 类别；累计投入单独明确补录并绑定日志；更正失效、按原基准一次修正；快照、设置、排除 / 停用、移除累计确认与安全导出 | JVM 计算通过；Room / 迁移 / 重启 / 写失败测试已编译，未设备运行 |
| H03 | 我的 → 个人规划画像；数值、状态、窗口 / 版本 / 时间、来源、默认与明确类别偏好、排除 / 停用、补录 / 当前来源确认、打开任务记录；旧文字记录保留并标注历史 | Compose 已编译；实际点击、显示 / 字体 / IME / 重建未验证 |
| H04 | 指定快照、有限只读 JSON、精确值 / ID 校验、主动刷新、版本缓存、同意 / 总开关 / 配置 / 取消 / 新证据守卫；失败标明 LOCAL，不将 AI 文案当证据 | 纯校验通过；最小载荷 / fake AI 迟到响应测试已编译，未运行；真实供应商未验证 |
| H05 | 来源页估时建议单独确认，保留未修正基准，重复查看 / 采纳不累乘；可选执行段拆分参考；制定计划 / 安排今天 / 剩余重排草案引用必要参数 / 版本并在最终事务复检；基础规划可在画像失败 / 关闭时继续 | 领域计算通过；计划不变、拒旧建议、参数失效与新草案测试已编译，未运行 |
| H06 | 新增 13 项设备测试源码，覆盖 Room、持久化、迁移关联、失败、只读 AI、实际页面工作流；恢复验证脚本 | 未完成正常构建、设备全套、视觉验收、当前版升级 |

其余维度：重排 / 中断、明确时段偏好、安排方式仍显示未知 / 待可靠证据，不从拖动或延期猜测。任务创建 / 编辑主表单尚无独立内嵌画像按钮；本批采纳入口在画像来源任务卡，能打开任务详情。拆分是有数值和来源的可选参考，不自动拆分或改变当前计划，未新增独立自动拆分器。AI 只能选择提供的事实引用及预设可选规划建议，不能输出任意评分 / 任意写操作。

源码主要入口：

- `domain/model/NumericProfile.kt`、`domain/ports/NumericProfileRepository.kt`。
- `data/room/RoomNumericProfileRepository.kt`、`NumericProfileCodec.kt`、首次开始 / 会话结束事务关联；沿用 Room 21 workspace，新增版本 1 记录，不破坏性迁移、不猜测补历史。
- `ui/profile/NumericProfileViewModel.kt` / `NumericProfileSection.kt`；`src/remote/.../CompatibleNumericProfileAdvisor.kt`。
- `PlanningOperationService` / `PlanDraft` / `PlanDraftCodec` / `RoomPlanRepository` 的草案引用与最终事务拒旧；`LocalDataSnapshot` / `RoomDataManagementRepository` / `LocalDataJsonExporter` 的显式画像记录白名单。

## 实际验证与失败历史

证据目录：`android/app/build/reports/h01-2026-10-10`。最终源码身份、未跟踪源码及编译类哈希见该目录清单。该源码身份不是 HEAD 单独身份。

- 先建立计算测试，再实现纯领域。初始 6 个数值测试通过。
- 旧文字画像双计复现：`h01-development3.log`，146 项 / 1 失败，两个口径未知的 30 分钟被旧汇总写成 60 分钟。最小修正后：单条保留原值但明确口径未知；多条只报记录数、不合计。历史已保存文字不删除，不成为数值输入。
- 中间正常 Gradle 单元批次 146 项 / 0 失败（`h01-development4.log` 对应当时源码），不替代最终源码验证。
- 最终当前编译类使用 JDK / JUnitCore 直接执行全部 JVM 测试，149 项 / 0 失败（含新增 9 项数值 / 描述校验）；这是独立 JVM 结果，不称正常 Gradle 全构建通过。具体日志与最终复跑以证据目录为准。
- 主代码、JVM 测试和全部 Android 测试源码编译：使用诊断 init 脚本跳过失败的 `processDebugResources`，仅复用同源码生成的 `generateDebugRFile/R.jar` 作为 R 符号桩，SHA256 `023b11f5786b5c44a608218aa1491d17a5be205138b701b4a2b2ad0a2558dcf7`。未用于打包、安装或界面验收。恢复正常验收必须先真正运行资源链接，脚本已强制重跑该任务。
- 正常打包失败：`h01-development5.log` / `h01-development6.log` / `h01-aapt-diagnostic.log`，AAPT2 进程在 link 中异常退出且无资源错误说明；直接 CLI 最小 link 也退出 `-1073741819`（Windows 访问异常），换 SDK 自带 AAPT2 与工作区临时目录仍失败（`h01-aapt-sdk.log`）。补做合法无资源引用的最小 Manifest，link 同样退出 `-1073741819` 且无 stderr（`minimal-native-link.log`），排除仅因诊断 Manifest 缺资源而失败。AAPT2 `version` 正常；根因未定位，不能当作 App 资源错误已修复或断言完全由沙箱造成。
- 当前沙箱下 `subst R:` 返回 `Access denied`，未替换已有映射。网络权限已获得，解决 Gradle 本机回环连接，但未解决原生工具退出。没有使用强制覆盖、删除缓存或伪造可验收 APK。
- `emulator -accel-check` 返回 6，提示 Hyper-V / Windows Hypervisor Platform 可用性检查；无连接设备。本批没有在旧包上运行新增测试。需要恢复可运行 AAPT2 / 模拟器的本机执行环境。

## 继续验收顺序

1. 在能够运行原生 Android 构建工具的环境恢复正常资源链接；使用 `outputs/Environment/verification/Verify-NumericProfile.ps1`。脚本语法已检查，尚未完成执行；默认完整离线构建和设备回归，通过 `-InstallCurrent` 才原位安装 / 启动最终 `.current`，失败即停止，不卸载 / 清数据。脚本不会使用诊断 init / R 桩。
2. 先跑新增画像仓储、迁移、AI fake / 载荷与实际 UI 测试；修正暴露的问题，再跑最终同源码全套。对首次失败保留截图、UI 树、XML 和对应 APK 哈希；QA-062 间歇详情问题仍未定位，不将旧成功替代复验。
3. 在最终包实测未知数据、来源核对、错误补录与重试、排除 / 停用 / 恢复、取消 / 采纳、重复建议、执行记录入口、重建与重启、AI 关闭 / 过期 / 本地回退、草案来源及保护时段；检查小屏 / IME / 字号。
4. 完成 internal / release 编译、离线边界、具体 APK 哈希与源码绑定；原位升级保护任务、课程、历史计划、反馈和设置，停止其他版本，仅最终当前版运行。
5. 真实 AI / 通知 / 真机矩阵、T08/T10 完整失败回执、QA-062 保持独立待办；用户试用 / 招募、额度监控、自动关机和无关功能不执行。没有最终验收前不宣布 H01–H06 或整个计划完成。

## 续接复核：执行权限阻塞仍未解除

用户再次要求继续后，重新核对源码清单，已验证源码文件哈希 0 差异；HEAD / 分支未变化。重新 fetch 成功，远程仍为 7506074，无新提交，工作区修改原样保留。

新增隔离：空 Manifest 链接去掉 Android 平台文件，Maven AAPT2 与 SDK 36 自带版本依旧均返回 -1073741819，日志见 `android/app/build/reports/h01-resume-native`。本次 ADB 显示 emulator-5554 offline，未安装或运行新增测试，也未重启 / 清除该设备。

尝试申请仅对最小链接诊断使用沙箱外执行；工具在执行前直接拒绝：当前 Granular 策略 `sandbox_approval=false`，禁止申请 escalated permissions。命令未执行，不能称沙箱外测试失败；仅网络授权不能解除该执行策略。后续需要切换到允许本机 Android 原生工具运行的执行环境，或由用户在本机运行已准备的 `outputs/Environment/verification/Verify-NumericProfile.ps1` 并提供日志。正常构建 / 设备 / 升级仍待验收，阶段未完成。
