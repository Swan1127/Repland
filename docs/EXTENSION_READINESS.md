# 当前工程边界与扩展说明

更新：2026-10-22。产品规则统一见 [主思路](../REPLAND_IDEA.md)，本文保留开发所需的实现定位和扩展约束；不是另一份产品路线。当前基线为 `main` / `08a686b`，版本 0.1.35（1035），Room 21。

## 源码定位

以下均为当前仓库路径，链接到实际文件；保留的内部实现不等于当前界面入口。

| 范围 | 主要入口 |
| --- | --- |
| 导航、日程入口与设置 | [ReplandApp.kt](../android/app/src/main/java/com/swan1127/repland/ui/ReplandApp.kt) |
| 一句话处理与前台维护 | [AutonomyViewModel.kt](../android/app/src/main/java/com/swan1127/repland/ui/agent/AutonomyViewModel.kt) |
| 自动事务与最近操作撤销 | [RoomAutonomyRepository.kt](../android/app/src/main/java/com/swan1127/repland/data/room/RoomAutonomyRepository.kt) |
| 时间设置 | [TimeViewModel.kt](../android/app/src/main/java/com/swan1127/repland/ui/time/TimeViewModel.kt) |
| 确定性排序 | [PlanningPriority.kt](../android/app/src/main/java/com/swan1127/repland/domain/model/PlanningPriority.kt) |
| 使用总结 | [UsageSummaryViewModel.kt](../android/app/src/main/java/com/swan1127/repland/ui/profile/UsageSummaryViewModel.kt)、[RoomUsageSummaryRepository.kt](../android/app/src/main/java/com/swan1127/repland/data/room/RoomUsageSummaryRepository.kt) |
| 数值规划后端 | [RoomNumericProfileRepository.kt](../android/app/src/main/java/com/swan1127/repland/data/room/RoomNumericProfileRepository.kt)、[计算契约](features/H01_NUMERIC_CONTRACT.md) |
| 数据迁移与导出 | [ReplandDatabase.kt](../android/app/src/main/java/com/swan1127/repland/data/room/ReplandDatabase.kt)、[LocalDataJsonExporter.kt](../android/app/src/main/java/com/swan1127/repland/data/export/LocalDataJsonExporter.kt) |
| 构建、包名与网络边界检查 | [app/build.gradle.kts](../android/app/build.gradle.kts)、[版本配置](../android/gradle.properties) |

## 任务、安排与执行分开

任务是可复用对象；`PlannedSegment` 携带日期、起止分钟和稳定 `trackId`，是任务的一次安排。轨道定义及规划工作区持久化，不再沿用早期“只有临时轨道名称”的探索说明；工作区使用 `planning_workspace` 中的结构化内容，不必为每个产品概念建立同名 Room 表。

执行会话保存真实投入；计划版本和过期时段不能充当执行证据。日 / 周 / 月读取相同安排来源，不能为视图维护相互独立的事实。轨道不代表资源并行执行模型。

当前本地滚动排程为 30 天，基本量子 30 分钟。更细手动编辑入口不等于端到端任意分钟排程。自动安排使用开 / 关设置；完整仅建议模式是未来扩展。

## 必须保持的规则契约

| 领域 | 当前要求 |
| --- | --- |
| 开始与完成 | 由明确用户操作或受支持自然语言指令提供依据；到点、会话结束和无反馈不推断结果。 |
| 重叠与保护 | 普通重叠走受支持的明确确认流程；课程、固定事项、硬休息、锁定、旧 REQUIRED 安排及活动执行会话不能被直接覆盖。 |
| 历史恢复 | 只恢复允许恢复的排序 / 安排，形成新版本；任务状态和执行日志不回退，过去时段不重新启动。 |
| 未知与来源 | 类别 / 优先级未设置、时长 / 天数未知可保存；非法数值仍拒绝。旧来源不明、用户输入和规划估计分开。 |
| 草案 | 与正式状态分离、可恢复、应用前重检；授权内普通操作不强制先经草案。 |
| 旧模式 | 旧三模式数据仅兼容；不恢复为当前三类用户或三个导航流程。 |
| 固定性 | 旧不可避免 / REQUIRED 跨路径保护继续；重要程度与固定性分离须单独迁移，不借扩大 AI 范围取消旧保护。 |
| 撤销 | 最近受支持操作的整组更正；后续编辑、执行及当前约束冲突时整体拒绝，不静默覆盖新事实。 |

任务来源区分 `UNKNOWN`、`USER_INPUT`、`ACCEPTED_SUGGESTION`、`LEGACY_UNVERIFIED` 和 `PLANNING_ESTIMATE`。保存未变字段不得洗白来源，非法来源解码失败不能回退成用户确认。原估计 / 类别的开始前快照与当前编辑值也必须分开。

当前确定性 V1 评分保留，AI 评分项使用中性处理；旧固定权重 / “±5”不作为未来产品权限上限，不能据此声称已实现新的 AI 排序。新增方案应先说明与手工顺序、REQUIRED 保护、截止和近期稳定的关系。

## 数据与可靠提交

- Room 当前为 21，保留既有迁移链；不能使用破坏性 fallback 或清库代替迁移。旧非空默认值保持原值并标记来源不明，不猜测历史输入。
- 同组业务改动使用事务，重复操作 ID 保持幂等；读入失败保留最后可信内容，不用空列表冒充真实空状态。
- UI 的成功回执必须对应实际保存成功；失败保留输入并允许重试，避免重复提交。异步旧响应不能覆盖新输入、配置或事实。
- 自动修复仅在受支持前台时机发生；手动暂不安排的范围不能被立即补回。
- 撤销恢复旧时段仍须满足当前时间约束，不能通过撤销恢复已失效或受保护冲突的安排。
- JSON 导出是用户主动的本地业务数据导出；不包含 API Key、头像、用户名、轨道定义及所有助手工作区，不能称为完整设备备份。当前没有完整 JSON 备份恢复导入。
- PDF 导入针对受支持的文本课表结构，预览确认后才写课程；扫描件 OCR 和任意学校课表兼容不属于现有承诺。

## 两套画像能力各守边界

| 能力 | 当前定位 | 范围与权限 |
| --- | --- | --- |
| 近期使用总结 | 当前“我的”主内容；只读自然语言回顾 | 最近 30 天，沿用业务 AI 开关与发送授权，前台自动生成；只发有限聚合事实。 |
| 数值规划参数 | 保留的内部计算与兼容能力 | 90 天窗口，可信原估计与累计实际投入等严格证据口径；旧单独授权不作为使用总结的前置条件。 |

总结的事实版本、文案模板版本与请求尝试独立维护；相同内容缓存、失败持久退避、配置变化与迟到响应保护不能丢失。总结不能反向进入事实证据，不能更新 numeric 参数或正式计划。数值参数的更正去重、原估计冻结和不累乘规则见 [内部契约](features/H01_NUMERIC_CONTRACT.md)。

## AI 与构建边界

debug / internal / designPreview 编译 `src/remote` 适配器，release 没有远程 Advisor。Gradle 的 `verifyOfflineMvpBoundary` 是现行构建边界检查，不意味着所有候选包都禁止联网。具体包名和签名规则见 [发布](RELEASE.md)。

业务模型请求须同时满足：构建支持、全局 AI 开启、已有数据授权、服务配置和密钥可用。连接测试为独立显式动作。API Key 仅通过 Keystore 支持的加密存储管理，不进入 Room、导出或日志。密钥配置不等于已经授权业务发送。

AI 只能返回结构化建议或给定事实的描述，领域层负责对象引用、版本、来源、时间、保护、容量和事务。新增远程入口必须同步 [隐私](PRIVACY.md) 与 [测试](TESTING.md)，验证关闭 / 未配置时零业务请求、失败降级、取消及迟到回复。

## 当前验证缺口与扩展顺序

已有公共结果仅证明各自记录的源码和范围，参见 [测试](TESTING.md)。以下仍需针对候选源码获取证据，不能从历史成功推断关闭：

- 真机保留数据升级、真实旧用户库迁移与不同签名 / 版本冲突；
- API 24 最低支持边界，TalkBack、平板、硬件键盘及复杂弹层 / 大字号 / 横竖屏组合；
- 真实学生课表、文件选择器完整路径及大数据性能；
- 通知自然到点、设备重启、时区、Doze 和 OEM 后台限制；
- 多模型服务真实鉴权、超时、格式错误、取消和配置切换矩阵；
- 旧记录中的 QA-062 / 066 / 067 / 072 间歇点击或线程问题：根因及当前复现状态待重新核对，成功重跑不自动表示已修复。

当前工作区已有自动回执、时间设置和撤销约束相关修复及测试修改；本次文档整理保留原样，没有将其作为已发布功能或已通过测试的证据。先巩固现有四条流程和真实设备验收，再独立推进字段迁移、复杂排程、正式 release BYOK 与其他扩展。
