# 多 Agent / 记忆模型融合验证

日期：2026-10-22。本文件覆盖在 F631 前端阶段基线上的新融合，旧 `RELEASE_QA_2026-10-22.md` 的 APK 哈希及 1511 项用例不是本次结果。

## 构建与来源

- 原目标源码：用户提供的 `5a15288` 快照，保留已制作的新版前端。
- 供体：`repland-0.1.35.zip`，SHA-256 `A87CB968C082ABD3155DCCD6F3BF96EABBC168F87FAE5B5B357AFA21D254A2B8`。
- 最终 APK：`../Repland-新版-2026-10-22.apk`，21,408,649 字节。
- APK SHA-256：`E118D22F99A6968625B97DBADE8675E53F557C8513392FBFEBF1A2DA8886EC24`。
- 包名 `com.swan1127.repland.redesign`，版本 `1.0-redesign`，minSdk 26 / targetSdk 36。供体版本号不代表目标被降级或整体替换。
- 最后统一命令：`Build-Redesign.ps1 -Tasks ':app:assembleDebug',':app:testDebugUnitTest',':app:lintDebug'`。
- 结果：`BUILD SUCCESSFUL in 3m 34s`；53 个任务实际执行。使用 `--no-watch-fs --rerun-tasks`，防止源码目录链接导致旧编译缓存被误用。
- 日志：`../integration-release-validation-2026-10-22.log`。APK 包名、SDK 和版本另经 `aapt dump badging` 核对。

## 自动化测试

99 个 JVM 测试套件，**1654 项，0 失败、0 错误、0 跳过**。较前端阶段增加 143 项：

| 范围 | 新增用例 |
| --- | ---: |
| 六角色团队、运行时、剩余排程、修复器及融合约束 | 75 |
| 记忆模型、引用、版本化编解码与旧复习记录兼容 | 41 |
| 记忆 JSON 导出及导出 ViewModel | 9 |
| 团队 ViewModel、来源变化、确认失败与取消隔离 | 18 |

覆盖无 AI advisor 的真实团队运行、固定/必做/进行中安排保护、未知时长不编造、真实可用时间、跨当前时间只扣未来容量、重叠容量去重、无习惯配置不编造偏好、孤儿执行日志排除、三档评分、同日重复反馈不虚增稳定度、有限数值校验、默认关闭与空引用区分、过期草案拒绝及失败保留。

最终还修正了手动编辑草案后的验收展示：编辑会清除旧团队结果和旧事件，避免将生成时的通过状态误当成编辑后已经重新验收；既有锁定保护测试追加断言并通过。

最初两次检查暴露并修正供体测试与目标非空估时字段 / 截止日原因的契约差异，以及取消测试的观察流夹具阻塞；没有将失败运行的报告冒充最终通过。最终 XML 归档在 `integration-unit-results/`，汇总见 `release-summary.json`。

## 静态检查

Lint 任务完成，但报告仍有 **1 个 Error / 106 个 Warning**，不是零警告交付。

- Error 为原有 `MissingPermission`：`LocalReminderReceiver.kt:110` 通知权限问题；该文件未在本次融合中修改。
- 本轮还保留英文角色数量的复数建议和旧复习文案资源未使用等警告。完整条目见 `lint-results-debug.xml` / `lint-results-debug.html`。
- 融合前的静态报告另存 `frontend-baseline-lint-results.xml` / `.html`，避免混淆前端阶段 102 个 Warning 与本次结果。

## 选择性融合审计

相对独立 F631 源码 ZIP，新增 35 个文件、修改 13 个、删除 0 个。全部 29 个 Room 文件、`Task.kt`、云端群聊引擎所在的 `AgentTeam.kt`、Gradle 配置和 Manifest 逐字节未变；没有直接拷贝供体同为 v21 但结构不同的数据库。

未带入供体天气、执行会话、NumericProfile 或自动使用总结。`AutonomousPlanRepair` 仅作为规划团队的纯领域工具，不是后台自主控制入口。源码包排除目录链接、构建缓存、本机 SDK 配置及签名文件；原始附件与 F631 回退包均独立保留。

## 运行证据与限制

设备操作、实际数据回读、最终 APK 安装验证及截图见 `INTEGRATION_DEVICE_QA_2026-10-22.md`；仅以该记录实际执行的项目作为设备通过范围。领域测试不冒充设备点击测试，静态截图不冒充动效性能基准。

本地规划团队使用 `NoOpAiAdvisor`，不需云端密钥；它是本地六角色工具运行时，不是已联调六个云模型。原有云端群聊保留，未配置真实密钥的云成功流程不在验证结论内。

记忆算法是附件中的 **FSRS-inspired 自定义模型**，不含官方完整 FSRS 权重优化器或个人语料参数拟合。引用只影响放置偏好，不自动复活已完成任务，不修改用户时长、优先级和状态。

确认前来源校验不是跨多个仓库的数据库级原子 CAS。没有引入供体天气、后台自治、账户或新的敏感权限，也不声明存在未实现的记忆导入还原。

## 产物索引

- `release-summary.json`、`ARTIFACTS.sha256`：本次 APK / 源码指纹和检查汇总。
- `integration-source-change-manifest.csv`：相对融合前 F631 源码基线的逐文件差异。
- `source-change-manifest.csv`：相对用户原始 `repland-upstream-design.zip` 的完整前端及融合差异。
- `../Repland-前端阶段基线-F631-2026-10-22.zip`：融合前的独立源码回退点。
- `../repland-upstream-design/docs/MULTI_AGENT_FSRS_INTEGRATION_2026-10-22.md`：使用入口、兼容方案和模型边界。

继续使用桌面“Repland 新版 2026-10-22”快捷方式，指向当前已融合的同一源码目录和 APK；无需使用旧前端或设计副本快捷方式。
