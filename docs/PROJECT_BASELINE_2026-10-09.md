# Repland 实际项目基线与后续顺序

日期：2026-10-09（Asia/Shanghai）。本阶段为接手审计、构建与技术验证，未实施新产品功能。当前产品计划未全部完成。

## 1. 仓库与证据身份

- 实际仓库：本工作区 `outputs/Repland`，不是工作区根目录。
- 实际分支：`codex/placement-numeric-integrity`；HEAD：`85c75f04117b9421786fa37fdaf1236481b3b865`。
- 缓存远程引用：`7506074f36d822b4c3d57777e40ba50a78ccdb15`。本地领先 5 提交；本次 fetch 在沙箱内失败，用户环境重试分别连接重置、HTTP/1.1 443 连接失败。**没有成功拉取，不能断言远程没有后续提交。** 未切 main、未合并、未推送；当前已在目标分支，无需切换。
- 接手时唯一未提交内容为未跟踪的 `android/app/src/test/java/com/swan1127/repland/UnavoidablePlanningTest.kt`；原样保留。SHA-256：`85611f9da5c034ad7dfc2cf0c85f5ec1471501609717cf7b8ba1c092068a6be5`。本阶段运行了它，没有修改、删除或纳入提交。
- 已核对 5 个本地提交：`13455e2` 实施 C04 与独立功能文档；`fb2f4fe` 同步收集步骤及筛选定位；`b030e93` 完整筛选列表定位；`f18d085` 卡片几何及绘制同步；`85c75f0` 按 Snackbar 实际覆盖位置避让任务列表并增加断言。
- 源码版本配置实际为 **0.1.31 / 1031，Room v21**。C04/交接页开头仍写 0.1.30，这是前批候选记录，不能替代当前构建版本。
- 仓库及适用祖先路径未找到 AGENTS.md。已完整阅读本次指定八份资料，并结合 C04 独立文档、源码、历史 XML 与实际运行结果核对；不把旧机器 D: 路径或旧安装结果移植到本机。

## 2. 能力状态（实现、验证与交付分开）

| 范围 | 源码真实情况 | 本阶段结论与剩余范围 |
| --- | --- | --- |
| M0 / 基础结构 | 原生 Kotlin、Compose、Material 3、Room，本地单设备；四页导航、二级时间设置；可识别构建来源 | 已有项目，继续复用；不是 Web 重建或全新开发 |
| C04 / 任务收集 | 标题或事项可保存；UNSPECIFIED 类别/优先级，nullable 天数；六字段来源；草稿 v2、v1 兼容；导出来源；旧不可信类别不作为课程画像依据 | 已实现，待本次完整设备结果及真实用户升级；旧同值来源保守保留，未提供同值重新声明入口；当前来源不等于原估计/分类历史时点证据 |
| M1 / 共用数据与确认 | 持久化顺序、规划草案、助手工作区、稳定轨道、来源摘要、防重复、原子提交、失败恢复 | 核心链路已存在；不是所有 UI/设置都由统一命令控制；确认服务与 Room 校验需继续复用 |
| M2 / 本地规划 | 列表排序、独立制定、安排今天、剩余重排、30 天/30 分钟槽、缺信息补充、未排量及变更预览 | 核心已接入；贪心方案不承诺全局最优；复杂多任务截止与容量策略尚未完成 |
| C07 / 不可避免 | 名称已统一；AI 契约有 REQUIRED 保护；本地生成/降级仍主要以 isLocked 保护，排序仍只按分数处理 REQUIRED | **确定性失败，待开发**；见第 4 节，不可宣称跨路径保护已完成 |
| C02 / 普通重叠 | PlacementValidator 拒绝同轨重叠、跨轨锁定冲突和课程/固定/休息/阻挡；普通异轨重叠可能通过，但领域签名没有明确重叠授权参数 | 未完成统一的“普通重叠须确认、保护段禁止”契约；不可避免冲突也需纳入。需审查 UI→领域→事务，不能仅靠 UI 确认 |
| C03 / 计划历史 | restore 与 accept 共用事务，过滤结束任务的未来工作，不改任务状态/执行日志；仍保留历史过去片段 | 后台已有，QA-059 前端不可达仍存在；过去片段展示/提醒与“不重启过去时段”需专项核验 |
| C05 / 草案 | 本地可恢复、可丢弃、sourceRevision 校验、失败保留 | 已有实现；真实进程恢复、过期及各提交路径仍按指定包验证，不能改回非持久草案 |
| C01 / 执行 | 主动开始、持久会话、暂停/恢复、明确结果、剩余预览；墙钟不自动完成，不填推测耗时 | 本地闭环已有；真实进程终止/后台/OEM 与实际通知矩阵未完成 |
| C06 / 模式 | 当前单一模式；旧参与数据兼容/导出，未恢复模式入口 | 保持现状，不删除兼容数据 |
| M3 / AI | QA/internal 编入 HTTPS Provider；授权/配置/开关、有限操作、连续修改、取消及晚到响应保护；release 本地 Advisor | 既有历史真实样例仅对应旧机器/旧包；本机未配置或调用真实模型，完整真实失败矩阵待授权配置；release BYOK 路线未完成 |
| M4-P / 画像 | 现有 ProfileEvidence 是可追溯文字证据；没有 H01–H06 数值快照/计算/查看/AI 描述/任务联动闭环 | **待开发**，先建立可信输入与执行证据；不将文字证据当数值画像 |
| M5 / 系统技术验收 | 有生成 PDF 夹具、页面/事务/迁移测试；提醒、导出等已有实现 | 真机原位升级、真实 PDF 系统选择器、通知投递/取消/重启/时区、TalkBack/平板/硬件键盘仍未验收；JSON 导出无全量恢复能力 |

补充审查项：T08 课程拖入与 T10 精确调整的异步失败/回执；“已完成”筛选实际包含其他非活动状态；已有明确优先级普通编辑仍被 Repository 保留，与目标“用户可改优先级”的完整入口存在差距。以上为源码审查项，尚未作为本轮复现事故关闭或扩大实现范围。

明确延期：账号、Gateway、同步、多端、外部日历双向同步、三模式、自由聊天、习惯系统与复杂长期画像。用户招募、7 天试用、留存实验已移出任务及门槛。最终竞品报告在技术收尾后进行，只写建议。

## 3. 当前机器环境与操作边界

- Windows 11 x64 / PowerShell；环境位于相邻 `outputs/Environment`。
- Zulu JDK **17.0.20.1**、Gradle wrapper **9.1.0**、AGP **9.0.1**；SDK platform **android-36**，build-tools **36.0.0**；compile/target 36、min 24。
- AVD **Repland_API_36**，本次启动为 `emulator-5554` / Android 16 API 36 / x86_64。启动前没有在线设备；启动后 `sys.boot_completed=1`。
- 启动后、测试前 `pm list packages com.swan1127.repland` 为空：本 AVD 没有旧 Repland 用户包，不能做 0.1.28→当前版用户数据核对。未假设已有模型配置，未读取密钥。
- 使用现有 AVD，`-no-snapshot-load -no-snapshot-save -gpu swiftshader`，后台隐藏启动；没有 wipe-data、卸载或 pm clear。只运行隔离 `.qa` 自动化，不安装 `.current` 作为用户交付。
- ADB 沙箱内服务不可连接；在用户环境可启动并连接。构建使用现有 Activate-Repland.ps1 设置本进程环境，`--offline` 使用本机已有依赖；没有安装 SDK/JDK/插件，没有改全局 Git 信任或映射 R:。
- 测试前系统 font_scale=1.0，三项动画比例均 1.0；测试后核对实际状态。
- 构建提示 SDK XML 版本兼容警告，以及测试输入工具 recycle 弃用警告；单独记录，不把警告当测试失败或产品缺陷。

## 4. 本次验证与缺陷证据

验证对象：HEAD `85c75f0` 的生产与设备测试源码，加上接手时已有的未跟踪 C07 单元测试。未修改 App 源码。记录目录：`android/app/build/reports/baseline-2026-10-09/`（忽略的本地证据，跨机器不随 Git 自动获得）。

### 单元测试

执行 `:app:testDebugUnitTest :app:verifyOfflineMvpBoundary`，XML 合计 **139 tests / 4 failures / 0 errors / 0 skipped**；命令因断言失败退出 1。原有 **133 项通过**，未跟踪 C07 的 6 项中 2 通过、4 失败。离线边界任务已成功执行。

| C07 断言 | 实际结果 |
| --- | --- |
| 未安排不可避免先于高软分任务 | 失败：期望 required，实际 high |
| direct generate 保留未显式锁定的不可避免时段 | 失败：09:15–10:00 被改为 08:00–09:00 |
| remaining / today 保留已确认时段与轨道 | 失败：09:15–10:00 custom 被改为 08:00–09:00 focus；循环在 remaining 失败，today 分支未运行 |
| disabled / unconfigured / timeout 降级保持位置 | 失败：次日 10:00–10:30 被改为 08:00–08:30；循环在 disabled 失败，后两分支未运行 |
| 无可用时间不编造时段 | 通过 |
| 已完成不可避免不重新排入 | 通过 |

源码对应：`Plan.kt::generateRemaining` retained 缺 REQUIRED 条件、`generate` preservedLocks 只看 isLocked；`PlanningPriority.kt::rank` 缺不可避免独立候选优先层；`PlanningAgent.kt::localFallback` 调用 generate 而非基于 current 的重排。`RoomPlanRepository::acceptInTransaction/saveAssistantChanges/movePlacement` 亦主要检查显式锁定/已开始/执行中，尚需事务层保护及主动修改确认。本轮测试证明草案层移动，**不冒称已复现用户正式数据损坏**。

单元日志：`android/app/build/reports/baseline-2026-10-09-unit.log`；完整 XML 已复制到本记录目录 `unit-results/`。旧 267/127 与前批 272/1 失败记录保留，均不作为本版本新结果。

### 设备与构建

完整设备 XML：**272 tests / 0 failures / 0 errors / 0 skipped，192.937 秒**，已复制为本记录目录 `device-full.xml`。包含 UnknownTaskWorkflowUiTest 的未知录入→提示仍可见→卡片位于提示上方→真实点击→重建编辑→来源/原计划不变，以及 Migration20To21Test、Migration5To6Test。QA-060 在本机 API 36 的该包及断言范围通过，不扩大为全显示矩阵通过。

设备/构建命令 **BUILD SUCCESSFUL in 5m 44s**：connectedDebugAndroidTest、assembleInternal、compileReleaseKotlin、processReleaseMainManifest、verifyOfflineMvpBoundary、recordBuildArtifacts 全部完成。不是正式签名 release APK 发布，也没有安装 internal 用户包。记录 BuildConfig 来源 `85c75f04117b`、版本 0.1.31；构建清单完整来源与 HEAD 一致。

| 本次 APK | SHA-256 |
| --- | --- |
| app-debug.apk | `d42cdc5c931a4d7af1be0c01bc8c023717dae4e3399fc27eacb88cf289d35cec` |
| app-debug-androidTest.apk | `58a09149e61495f843a6d48d9bc78d01a269172d5d52b0caa3c40ef55aeebfcc` |
| app-internal.apk | `75df219f76155d360ab2faea994b1375f3ea48521c5bf435c09f8989ec0d5e12` |

APK、构建清单、两份命令日志独立保留在本记录目录，不覆盖旧 retained 版本。测试结束后 Repland 包查询为空（Gradle 已清理测试安装）；系统字号与三项动画比例均为 1.0，夜间模式为 no，AVD 保留运行。没有用户包/业务数据清除或真实 API 请求。

复跑（在仓库根目录，串行，不与其他构建并发）：

```powershell
. ../Environment/Activate-Repland.ps1
& "$env:REPLAND_PROJECT/gradlew.bat" -p "$env:REPLAND_PROJECT" --offline '-Pkotlin.compiler.execution.strategy=in-process' :app:testDebugUnitTest :app:verifyOfflineMvpBoundary
# 上述当前应复现 C07 失败；不要因退出 1 就漏掉 XML 统计，也不要删除测试。
& "$env:REPLAND_PROJECT/gradlew.bat" -p "$env:REPLAND_PROJECT" --offline '-Pkotlin.compiler.execution.strategy=in-process' :app:connectedDebugAndroidTest :app:assembleInternal :app:compileReleaseKotlin :app:processReleaseMainManifest :app:verifyOfflineMvpBoundary :app:recordBuildArtifacts
```

本阶段只新增本报告及四份现有文档的入口/缺陷记录。`git diff --check` 通过（Windows 行尾提示）；原未跟踪测试哈希复核不变。文档未提交，HEAD 保持原值；远程核对仍未完成，不将本地审计称为成功拉取。

## 5. 下一阶段执行顺序

1. 网络可用后先 `git fetch origin`，比较远程与 `85c75f0`；如有新提交，阅读 diff 后仅 fast-forward；分歧/冲突停止报告，保留本地提交与所有未提交文件。当前远程核对仍受网络阻塞。
2. 收尾 C04 / QA-060：按本次完整设备结果决定是否关闭本机范围；补真实升级与显示验收，保持字段未知和来源。不要回退断言或用等待提示消失绕过遮挡。
3. C07：以保留的失败单元为起点，补规划服务/在线/关闭/未配置/超时/今日/剩余路径，事务防覆盖与用户主动改动确认；不自动把 REQUIRED 永久写成 isLocked，不让软排序覆盖硬约束或手动顺序。补设备事务/界面验收后再宣布通过。
4. 关联 C02/C03/C05 与 QA-059：明确重叠授权进入领域和事务，接通历史/锁定入口；恢复不回退事实、不复活结束任务；草案重新校验。同步审查 T08/T10 失败反馈及优先级编辑差距。
5. M2 复杂容量/截止样例；M3 真实有限 AI 全矩阵（没有有效授权配置时只做假 Provider 与本地验证，不索取或提取凭据）；M4 真实进程恢复及提醒。
6. 在可信执行证据上推进 H01–H06，先用隔离技术样例冻结窗口/门槛/去重/更正，不凭文档认定算法已批准或实现。
7. M5 指定包升级/PDF/通知/显示/无障碍技术矩阵与完整闭环，整理候选包交付限制；技术实施收尾后才做最终只读竞品报告。

本阶段不提交或推送新代码，不创建新自动跟进，不改已有保留标签；后续接手以本报告的新实测及 Git 实际状态为准。
