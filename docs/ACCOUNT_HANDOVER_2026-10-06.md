# Repland 账号交接：状态、验收与后续任务

更新：2026-10-06（Asia/Shanghai）。这是下一个账号的统一接手入口，不需要原聊天历史。交接基线为源码 `5ea3338cc1e385f243b4f3dab706e3c54deb733c`，分支 `codex/placement-numeric-integrity`；本文件之后的文档提交不改变该构建来源。

## 1. 先读：当前到底完成到了哪里

- 最新源码版本：0.1.29 / 1029。本次完整自动化回归：267 项 Android 设备测试、127 项单元测试，失败 / 错误 / 跳过均为 0。设备全套 XML 用时 251.337 秒。
- 当前模拟器用户包仍为 **0.1.28-internal / 1028**，包名 `com.swan1127.repland.current`。本次不做原位升级，不修改用户任务、不提取 API 密钥、不发送真实模型请求。0.1.29 自动化通过不等于已经安装交付。
- 前次全套因额度中断，仅产生部分结果，不能算通过；本次重新跑完 267 项才作为完整结果。中断历史保留，不以本次成功抹除。
- 原产品计划 **尚未全部完成**。系统回归已完成；按最新要求，功能清单已独立拆分，本文仅保留交接状态、约束、证据、后续任务与操作信息；后续功能暂不继续实施。
- 功能与前端定位请读取 [功能与前端清单](FEATURE_FRONTEND_INVENTORY_2026-10-06.md)：74 个功能条目、源码地图、业务与测试映射、后台能力及遗留组件。本文不再重复该清单。
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

## 3. 本批 0.1.29 修改、证据与已知问题

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

## 4. 下一账号逐项任务（本次只记录，不执行）

新增权威决定（2026-10-06）：用户已逐项确认 [一致性报告](CONSISTENCY_REVIEW_2026-10-06.md) 的 C01–C07，以 [交付计划](PRODUCT_DELIVERY_PLAN_2026-10-03.md) 第 3.0 节为实施差距清单。优先补未知字段 / 来源与迁移、不可避免已确认时段跨 AI / 本地 / 降级保护和名称统一，关联普通重叠确认、历史恢复与草案校验；均未因本轮讨论修改代码或完成设备验收。先前 267 / 127 回归不覆盖这些新规则。

| 优先级 / 任务 | 具体操作范围 | 完成标准 / 证据 |
| --- | --- | --- |
| 1：完成 29 安装交付 | 核对本文件附录的 internal 哈希、来源与签名；在当前包原位升级，不卸载 / 清数据；只启动当前版，停止 QA / 旧包 | 我的页版本 1029 与来源正确；升级前后原任务、课程、计划、反馈、设置保持；API 配置只通过 UI 验证，不读 Key；记录实际检查，不以安装成功代替数据验证。 |
| 2：补齐实际前端入口 | 处理 QA-059 的计划历史 / 恢复 / 清空 / 锁定入口，遵循现有导航与组件 | 原生用户流程可达，取消 / 恢复失败保留原计划，锁定在制定 / 单日 / AI / 重排中保护；补 UI 与事务回归，不仅测 Repository。 |
| 3：剩余数字与异步操作审查 | 对照 [独立功能清单](FEATURE_FRONTEND_INVENTORY_2026-10-06.md) 逐项核对原数字、目标身份、回执、失败保留；特别 T08/T10 与其他活跃编辑入口 | 先写复现测试，记录产品 / 驱动 / 环境失败区别；修复后绑定新源码与 APK，不以过滤或弱化断言通过。 |
| 4：M2 复杂容量 / 截止策略 | 多任务不足、紧急任务与高分任务竞争、可分段工作、手动顺序 / 锁定 / 固定约束 | 原计划保护不变，解释准确、分钟真实、处理选项可行动；只在授权规则内完善，不承诺全局最优或静默解锁。 |
| 5：M3 真实 AI 全矩阵 | 当前用户授权配置：多任务批量新增、已有任务连续修改、制定路由、查询 / 解释；错误密钥、超时、取消、无效格式、断网、换配置 / 关 AI 后迟到响应 | 正式业务只在确认后原子生效；错误零写入，输入保留，实际数量准确；跨页同结果；无配置 / 关闭零请求；用最小上下文、有限次数，不记录凭据或原始个人响应。 |
| 6：M4 真实执行恢复与提醒 | 开始 → 暂停 → 后台 → 进程停止 / 重启 → 继续 → 部分 / 完成 → 剩余预览；提醒开关、拒绝 / 授权、取消、重启、时区变化 | 同一会话 / 任务身份恢复，暂停不计时，计时不自动完成，取消重排不改计划；实际系统 alarm / notification 证据。Activity.recreate 不替代真实进程重启。 |
| 6-P：M4-P 数值画像与现状描述（新增待实施） | 先满足 C04 可信未知 / 来源及执行证据，按权威计划 H01–H06 冻结参数、计算、我的页数值 / 来源、AI 主动描述、任务建议联动 | 程序计算为主；样本不足未知；数值与描述绑定快照、不反向建证据；不双计、不累乘、不重复加权，停用可用，升级不丢数据；隔离技术验收，不做用户试用。 |
| 7：M5 实际文件 / 显示 / 升级矩阵 | 真实 PDF 通过系统选择器，文本 / 无效 / 扫描件；日周月、深浅色、小屏 / 横屏 / 字号 / TalkBack / 硬件键盘；旧数据真实升级 | 按设备、系统、具体 APK 记实测及限制。无真机 / 平板 / 用户 PDF 时明确未验证，不造通过。无需用户招募或连续试用。 |
| 8：完整工作流收尾 | 收集 → 排序 → 制定 → 编辑 → 确认 → 执行 → 打乱 → 重排 / 取消 → 复盘；设置变化、读取 / 写入失败、恢复 | 工作流技术报告、已知限制、候选包清单；同源完整自动化回归仍通过，阻断 / 数据一致性缺陷关闭。 |
| 9：最终产品对比与交互意见 | 网上查 Todoist、TickTick、Structured、Motion、Sunsama 等官方最新资料；与 Repland 当前可用功能对比，逐流程检查 | 写优缺点、依据、用户操作卡点与优先级；区分竞争产品能力 / 推断 / 本地实测；只形成报告，不改代码。历史报告不当作这次最终报告。 |

原阶段对应：M0 核心基线通过；M1 核心持久化 / 事务与有限共用服务已实现、仍有入口与工作流验收；M2 核心本地流程已实现，复杂容量未完；M3 契约已接、真实验证部分通过；M4 本地执行已接、真实系统矩阵未完；M5 进行中。不要重新从零实现已有能力，也不要把原计划标为全部完成。

## 5. 操作环境、复跑与安全交接

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

> 在 D:\Repland 继续 Repland 原产品计划。先完整读取 docs/ACCOUNT_HANDOVER_2026-10-06.md 和 docs/FEATURE_FRONTEND_INVENTORY_2026-10-06.md，再核对 Git、实际安装版本、保留包与验收证据。最新 0.1.29 的 267 设备 / 127 单元完整回归已通过，但用户当前包仍为 0.1.28，不能误认为 29 已升级。按交接第 4 节顺序逐项推进；不重新实现已完成能力，不进行 7 天试用 / 招募，不清用户数据、不读取密钥。AI 只走有限业务契约，确认前不改正式数据。后置网上产品对比只写报告，不实施建议。每项记录具体源码 / 包 / 测试与未验证边界。

## 6. 本次构建附录

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

保留目录 `android/app/build/reports/retained/0.1.29-regression/`：三个 APK、build-manifest.txt、unit-test-results/。完整设备 XML 独立复制到 `android/app/build/reports/repland-029-final-full.xml`；原 XML / HTML 仍在第 5 节目录。以上均是本地忽略产物，新账号在同机器可访问；跨机器需安全取得 APK / 报告或重建，不能认为已经随 Git 上传。

下一账号应先核对实际安装与源码。本次没有升级当前版、没有创建已完成交付的 v0.1.29 标签，也没有执行后续功能任务；0.1.29 的完整自动化回归已完成，与整个产品计划完成是两件事。
