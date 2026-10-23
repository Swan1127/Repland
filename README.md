# Repland

当前主开发版是独立工程 **[upstream-design/](upstream-design/SETUP.md)**，版本 `1.0.1-dev / 2`，使用 JDK 21、Gradle 9.1.0 和 SDK 36。首次检出需在该目录执行 `setup-junction.ps1`（Windows）或 `bash setup-junction.sh`（Linux / macOS），随后运行 Gradle wrapper。该工程的完整源码、测试、资源和构建脚本均独立保存；私人课表、凭据、构建缓存和安装包不入库。[独立 CI](.github/workflows/upstream-design.yml) 验证构建、JVM、Lint 和 API 26 设备测试，缺少私人课表的用例会明确跳过。

下文及根目录的 `Build-App.ps1` / `Open-App.cmd` 对应现有 `android/` 整合工程 `0.1.35 / 1035`；它有自己的 [Android CI](.github/workflows/android.yml)。两个工程的 Room 21 数据结构不同，当前分别构建和验证；主开发版入口与说明见上面的独立工程链接。

Android 本地优先的个人任务规划与执行辅助工具：用一句话记录事项，在已设置的时间约束中安排，依据明确进度维护剩余计划，并保留解释与撤销。

源码版本 **0.1.35（1035）**。2026-10-22 已以 GitHub 合并提交 `a9255e4`（Henry PR #4）为基础，将新前端、swan 六角色规划、FSRS 形状记忆模型及本地修复整合到本目录的 `android/`。整合分支为 `codex/unified-app-20261022`；具体改动与验证见 [整合交接](docs/INTEGRATION_2026-10-22.md)。产品规则统一见 [主思路](REPLAND_IDEA.md)。

## 当前体验

- 底部为日程、任务、我的，保留柔和多色视觉。
- 日程右下角打开“一句话处理”，支持新增、安排、明确进度、查询及受保护的最近整次操作撤销。
- 自动安排可开启或暂停，只在 App 打开及返回前台等受支持时机维护，不承诺关闭 App 后持续实时重排。
- “我的”展示头像、用户名和自动的近期使用总结；其余项目集中到设置。
- 无模型也可使用本地任务、时间约束、基础排程、执行、提醒与导出。远程 AI 依实际构建、授权和配置启用。
- 日程中的“预览任务安排”调用本地六角色规划团队，查看工具日志后确认应用；学习与复习支持真实任务录入及忘记／模糊／记得三档反馈。可选记忆参考默认关闭，只影响放置时段。

## 文档入口

| 文档 | 用途 |
| --- | --- |
| [整合交接](docs/INTEGRATION_2026-10-22.md) | 三份来源、统一工程、构建与验证结果。 |
| [项目介绍](PROJECT_INTRODUCTION.md) | 背景、意义、技术方案与多层 Agent 协同创新点。 |
| [REPLAND_IDEA.md](REPLAND_IDEA.md) | 产品主思路、当前能力、生效规则与后续路线。 |
| [CONTEXT.md](CONTEXT.md) | 任务、计划、执行、画像与撤销等领域术语。 |
| [当前工程说明](docs/EXTENSION_READINESS.md) | 源码定位、数据保护、AI 构建边界与扩展要求。 |
| [隐私](docs/PRIVACY.md) | 本地数据、AI 发送范围、密钥、导出与清空。 |
| [测试](docs/TESTING.md) · [发布](docs/RELEASE.md) | 验证方法、历史证据边界与构建身份。 |
| [文档维护与备份](docs/DOCUMENTATION.md) | 本轮清理范围、完整备份、恢复方法及后续维护规则。 |

## 开发

用 Android Studio 打开 `android/`。当前构建使用 JDK 17、SDK / target 36，最低 API 24。在仓库根目录执行：

```powershell
.\android\gradlew.bat -p android :app:testDebugUnitTest
.\android\gradlew.bat -p android :app:assembleDebug :app:assembleInternal :app:verifyOfflineMvpBoundary
```

仪器测试仅使用专用测试设备及隔离包。包名、远程能力和发布前检查见 [发布规则](docs/RELEASE.md)。本机双击 `Open-Design.cmd` 打开设计副本，`Open-Schedule-Preview.cmd` 打开只读日程演示。这些入口及 `Build-Design.ps1` 依赖仓库外的 `D:\Environment\Activate-Repland.ps1` 与配置好的模拟器；其他电脑直接使用标准 Gradle 构建。

Windows 可运行 `Build-App.ps1` 构建主应用，连接 Android 设备后双击 `Open-App.cmd` 打开“Repland 当前版”。新脚本自动定位本机 JDK / Gradle，不依赖旧的 `D:\Environment`。多设备时用 `Start-App.ps1 -Serial <设备号>`。

当前整合版验证见 [整合交接](docs/INTEGRATION_2026-10-22.md)。历史验证保存在 [2026-10-18 使用总结验证](docs/verification/automatic-usage-summary-2026-10-18/README.md)和[更早的 0.1.35 技术证据](docs/verification/final-0.1.35/README.md)，各自仅适用于其源码快照。

不要提交密钥、签名材料、`local.properties`、个人数据库或设备备份；已有数据迁移不能使用破坏性重置。
