# 主应用整合交接 · 2026-10-22

## 工程与来源

最终工程是 `D:\Repland\android`；用 Android Studio 打开此目录。当前分支为 `codex/unified-app-20261022`。

| 来源 | 用途 |
| --- | --- |
| GitHub `Swan1127/Repland`，合并提交 `a9255e4`，Henry PR #4 | 主应用基础、新前端、已适配本地主工程的六角色规划与记忆模块。 |
| `C:\Users\Lenovo\Desktop\rrrrrr` | 按用户要求拉取的远端源码参考，已更新至 `a9255e4`，保持干净。 |
| `C:\Users\Lenovo\Desktop\repland-0.1.35(1)\swan-repland-0.1.35\source` | 对照六角色 runtime、FSRS 形状模型及包内 HANDOVER；基线 `715f113` 加未提交修改。PR #4 已将这些模型适配到主工程。 |
| 原 `D:\Repland` 工作区 | 保留撤销约束校验、自治回执串行化、时间设置文案和相应测试修复，以及用户原有文档整理。 |

没有将 swan 的另一套数据库和 Task 模型整体替换进来。继续使用主工程的 Room 21 及原迁移链、未知估时和输入来源语义。记忆保存在现有辅助 SharedPreferences；草案仍在 Room `planning_workspace`，新增的 JSON 元数据兼容旧草案，不需要改变数据库版本。

整合前源码备份：`output/integration-backup-20261022/main-source-before.zip`；原完整工作区差异：同目录 `local-before.patch`。本记录写作时尚未推送；2026-10-23 核查已确认整合提交 `d5b7c7a` 位于 GitHub `main`。另行维护的 `1.0.1-dev` 主开发版见 [`upstream-design/`](../upstream-design/SETUP.md)，其源码与本工程分别保存。

## 统一业务链路

1. 真实任务、可用时间、固定事项、执行会话、数值画像继续由原仓库提供。
2. 日程“预览任务安排”→ `PlanViewModel` → `PlanningOperationService` → 六角色团队 → 原确定性生成器。
3. 团队工具记录展示在同一份草案中；草案预览不会代表任务已经开始或完成。用户确认后，原 Room 事务校验来源版本、锁定、执行中任务与约束并保存。
4. “我的”→“学习与复习”选择真实任务、录入内容并复习；三档明确反馈更新记忆状态和下一次复习时间。
5. 设置中的“记忆参考”默认关闭；开启后将复习信号交给 HabitTuner，只影响日内软性放置，不改用户时长、优先级或任务状态。
6. JSON 导出包含 `learningMemory`；清除本地数据同时清除复习状态、参考设置与团队进程内记忆。

六角色使用本地确定性工具。记忆模型借用 FSRS 的曲线形状和稳固度／难度概念，使用手定参数，并非官方训练权重的完整 FSRS。Agent 跨轮摘要是进程内状态，与持久化复习记忆分开。

## 本次加固

- 生成草案的来源版本检查与旧草案比较，在同一 Room 事务中完成；人工编辑后不会被生成结果覆盖。
- 确认必须匹配当前持久草案；重复确认不重复写入计划。
- 记忆参考指纹保存在草案中，重启后仍拒绝使用已变化的参考确认。
- 清理团队记忆会使正在生成的旧轮次失效，不会重新写回已清除的日志。
- 复习状态与个性化增益一次提交，保存失败可重试；多个 AuxiliaryStore 实例共享互斥，清理后的旧评分不会复活记录。
- 三档反馈在 IO 线程保存，拦截重复点击，仅在成功后给回执；跨天重置界面评分状态。
- 修复快速设置可用时间后生成不可取消的问题；补齐首次使用及暂停自动安排时的主动团队预览入口。

## 构建与安装

```powershell
.\Build-App.ps1 -Offline
.\Start-App.ps1 -Serial emulator-5582
```

无本机缓存时去掉 `-Offline`，由 Gradle wrapper 下载依赖。脚本自动寻找 JDK 17+、Android Studio jbr 和本地 Gradle 9；SDK 来自 `android/local.properties` 或 Android 环境变量。启动脚本要求已有授权设备，不自动创建模拟器，不卸载应用或清理设备数据。

- `android/app/build/outputs/apk/internal/app-internal.apk`：`com.swan1127.repland.current`，“Repland 当前版”，含现有可选 BYOK 适配器。
- `android/app/build/outputs/apk/debug/app-debug.apk`：`com.swan1127.repland.qa`，隔离测试包。
- 正式 `release` 继续遵守原签名及离线边界。本次交付 internal 安装包，使用原工程的本地测试签名规则。

## 验证记录

使用 JDK 21、现有 Gradle 9.1.0、API 36 SDK，独立新建的 `ReplandIntegration20261022` / `emulator-5582`。测试仅操作专用 QA 安装和合成数据。

- JVM：50 个套件、331 项测试，零失败、零错误。
- Debug、internal、Debug 仪器测试包构建通过。
- `lintDebug`、`lintInternal` 通过，均零 Error，86 条 Warning。
- `verifyOfflineMvpBoundary` 通过。
- 最终同一轮 76 项设备回归全部通过，涵盖辅助记忆、复习／团队 Compose 界面、真实 Room 草案事务、执行会话、自治、20→21 迁移及数值画像，以及原有导航／自治／时间设置和真实主应用端到端流程。
- 端到端流程覆盖任务录入、六角色预览、确认、复习录入、授权与重建。
- 强制结束 QA 进程后重新冷启动，磁盘中的真实任务关联复习点和已启用的记忆参考仍保留。
- internal 在独立模拟器冷启动成功，崩溃缓冲无记录。
- `Build-App.ps1` 的 PowerShell 5.1 参数／环境预检通过；`Start-App.ps1` 实际安装、启动 internal 成功。

证据：`output/integration-validation-final.log`、`output/integration-device-final.log`、`output/integration-device-unified-final.log`、`output/integration-launch-final.log`、`output/unified-home.png`。早期端到端测试对固定页脚错误地调用滚动，以及把草案临时 ID 与正式安排 ID 等同，均已修正为真实界面／持久化语义后重跑通过；早期失败日志仍保留。

最终安装包：`output/Repland-unified-20261022.apk`，与当前源码构建出的 internal APK 相同。

本次不是完整历史仪器测试或全机型验证。保留的 0.1.35 版本号不能代替源码身份；交付 APK 的 SHA-256 与源码文件指纹记录在 `output/unified-app-manifest.json`。
