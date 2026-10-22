# Repland 前端阶段验证与交付索引（F631 基线）

本文件记录多 Agent / FSRS 融合前的前端阶段。以下“最终”指该阶段最终结果；新增融合后的 APK、测试和设备结果见 `INTEGRATION_QA_2026-10-22.md`，不能使用本页的旧哈希或 1511 项替代。

日期：2026-10-22。目录沿用开始工作时的 `Repland-Frontend-2026-10-21` 名称，最终产物日期为 10 月 22 日。

## 最终构建

- 基线：用户源码附件的 `5a15288`，不是历史前端参考提交。
- 最终包：`com.swan1127.repland.redesign`，应用标签“Repland 新版”，启动 Activity `com.swan1127.repland.MainActivity`。
- APK：`../Repland-新版-2026-10-22.apk`，21,148,721 字节。
- APK SHA256：`F631BC96887542DBB8620E5D36B77565C6745E3BAE0273E6BE7C4F152D7E9EB5`。
- 最后一次统一构建：`BUILD SUCCESSFUL in 3m 35s`，53 个任务全部实际执行，无源码缓存跳过。
- 命令：`Build-Redesign.ps1 -Tasks ':app:assembleDebug',':app:testDebugUnitTest',':app:lintDebug'`。
- 完整日志：`../release-validation-2026-10-22.log`。
- 原版对照使用新建独立包 `.redesignbaseline`，原来的 141 个源码文件及全部原始资源与附件逐字节一致，仅调试包隔离配置不同。溯源见 `BASELINE-PROVENANCE.md`。

## 自动化检查

| 项目 | 最终实际结果 |
| --- | --- |
| Debug 构建 | 成功，APK 元数据另用 aapt 核对 |
| JVM 单元测试 | 89 个套件，1511 个用例，0 失败、0 错误、0 跳过 |
| 本轮新增协调器用例 | 17 项：真实读回确认、失败 / 部分成功、重试幂等、锁定 / 多片段保护、最终一致性、历史时段快照等 |
| 本轮新增日程展示用例 | 8 项：重叠 / 邻接区间并集、无效区间与时间边界等 |
| 本轮新增任务保存用例 | 4 项：校验、等待成功、仓库失败与取消 |
| 本轮新增清理 / 撤销用例 | 6 项：数据库成功后才清 UI、失败保留、清理错误、清栈、与正在执行的撤销串行、防失败清栈 |
| Lint | 实际执行，1 Error + 102 Warning；不是全绿静态检查 |

原附件测试为 1476 项；这里的 1511 来自最终重新执行的 JUnit XML，不引用旧报告充数。完整 JUnit XML 在项目 `app/build/test-results/testDebugUnitTest/`，HTML 在 `app/build/reports/tests/testDebugUnitTest/`。这些是 JVM / 仓库替身验证，不等同于全部设备和真实服务验证。

Lint 唯一 Error：`MissingPermission`，`LocalReminderReceiver.kt:110`，通知 API 的权限分析。该文件 SHA256 与原附件一致，本轮未改；原工程已有 `abortOnError=false`，所以 Gradle lint 任务成功不代表问题为零。报告为 `lint-results-debug.xml` / `lint-results-debug.html`，保留所有 warning，不隐瞒或随意加 suppress。

## 设备操作与图片

唯一测试设备是现有 Android 16 / API 36 模拟器 `Repland_API_36`，不是实体手机。测试事项以 `QA` 开头，写入独立测试包；未操作原正式包的数据。

完整逐步覆盖、每轮 APK 区分、修复复测、截图和实际录屏记录见 `DEVICE_QA_2026-10-22.md`。`release-final-*` 是视觉修复复测，`final-today.png`、`final-plan.png`、`final-task-detail.png` 是 437F 导航修复后的三页。最终 F631 仅追加清库切换 IO 线程，视觉不变；以 `clean-*` 和清库 JSON 证明该补丁的设备验证。其他较早的 `redesign-*`、`final-01-*` 等不能混作最终版本的通过证明。

原版对照为 `baseline-pristine-today.png`、`baseline-pristine-plan.png`、`baseline-pristine-mine.png`。旧 `baseline-today.png` 是早期工作中截图，不属于纯原包对照。

### 真实录屏

- `release-walkthrough.mp4`：437F 构建，1080×2400，实际时长 74.28 秒；未剪辑，完整 376 个视频帧离线解码成功。
- 真实链路：安排详情 → 推后 15 分钟 → 回看 08:15–08:45 → 计划 / 任务详情 → 我的 → 助手未启用失败 → 历史空态 → 关闭。
- 动作时间记录：`release-walkthrough-actions.json`；解码记录：`video-decode-check.log`；抽帧图仅供分析，不替代真实 MP4。
- 视频 SHA256：`F6BFFDD1B88B18B553AF7FEB77B4A3BB67E37F8FC8B3FE0D619C7AB280813D7E`。
- 一页预览：`preview.html`，21 个本地文件引用已检查存在。

### 清理与入口

清理前只读确认隔离包中仅三条 QA、默认设置且未添加云端密钥。旧仓库同步 `clearAllTables()` 在 Main 调用导致 UI 清空失败；已最小改为 `Dispatchers.IO`，未改 schema、清理范围或正式包。实际 Room 2.7.2 字节码的主线程保护记录在 `room-clear-thread-audit.txt`。

F631 复测通过应用明确确认清空，页面提示成功；`release-store-after-io-clear.json` 只读显示任务、计划、安排片段、日志、AI、画像和其他应用表均为 0，仅保留 Room / Android 元数据。没有执行 SQL 删除、`pm clear` 或清理其他包。

桌面入口与最后的草稿 / 设置复核记录见设备报告，完成后在此补记。

## 交付文件与完整性

- 源码目录：`../repland-upstream-design/`。
- 源码 ZIP：`../Repland-前端改造源码-2026-10-22.zip`，287 个文件，解压总大小约 4.35 MB；不含 SDK、Gradle 缓存、build、旧 APK、本机 SDK 路径与绝对路径 junction。
- 源码 ZIP SHA256：`211EBF6CD4F9A2990CD2DE273959953ACB10396F6B223204082C18AAC9B2DCC5`。
- 原文件变更：32 修改、16 新增、0 删除，详见 `source-change-manifest.csv`，每条包含新旧 SHA256。
- 完整交接 ZIP：`../Repland-前端改造交付-2026-10-22.zip`，包含源码 ZIP、APK、预览、录屏及验证资料，不包含 SDK 和构建缓存。
- 机器可读汇总：`release-summary.json`；校验列表：`ARTIFACTS.sha256`。
- 设计 / 能力映射 / 构建 / 限制：`../repland-upstream-design/docs/FRONTEND_REDESIGN_2026-10-22.md`。

本轮没有提交 / 推送 GitHub，没有创建分支或 PR，也没有上传公开服务。源码 ZIP 是便于继续开发的代码交付，不包含整套本机 Android 环境；当前电脑可直接用桌面入口启动。

## 验证边界

- 无云端 API 密钥，真实云端成功与云端错误分类没有全部设备实测；保留未配置错误和草稿恢复的真实证据，绝不注入假模型回应或假成功回执。
- 剩余容量所需的完整公开领域契约不在此快照；只显示真实安排覆盖，不捏造可用分钟。
- 历史回执是最近 30 次结果快照，撤销仅为既有会话内受支持操作；不承诺全部操作持久可撤销。
- 使用原生语义和标签，但未做完整 TalkBack 朗读、实体手机 / 平板设备矩阵、远程故障注入、磁盘故障和严格性能 trace。实际操作录屏不是帧率基准测试。
