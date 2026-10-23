# Repland 独立主开发版

当前版本为 **1.0.1-dev（2）**，来自 2026-10-22 完成修复的本地独立工程。仓库中的 `upstream-design/` 保留这一份当前源码，生产实现位于 `repland/`；它与根目录的 `android/` 使用不同的数据模型，分别构建和验证。

## 环境

| 项目 | 版本 |
| --- | --- |
| JDK | 21 |
| Gradle | 9.1.0，已包含 wrapper 及分发包 SHA-256 |
| Android SDK | compileSdk 36 / minSdk 26 / targetSdk 36 |
| 构建插件 | AGP 9.0.1 / Kotlin 2.2.10 / KSP 2.2.10-2.0.2 |
| 主要依赖 | Room 2.7.2 / Compose BOM 2024.09.00 |

SDK 可由 `ANDROID_HOME` 配置，或在本目录创建未入库的 `local.properties` 并写入本机 `sdk.dir`。

## 构建

在 `upstream-design/` 内执行。源码目录链接只需创建一次，157 个生产 Kotlin 文件均只保存于 `repland/`。

Windows PowerShell：

```powershell
.\setup-junction.ps1
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
```

macOS / Linux：

```bash
bash setup-junction.sh
bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
```

链接为 `app/src/main/java/com/swan1127/repland` → `<工程根>/repland`，链接自身不入库。AGP 9 使用其内置 Kotlin 支持，保留当前插件声明及 `android.disallowKotlinSourceSets=false`。

Linux CI 在全新检出目录执行 `bash prepare-ci-sources.sh`，为 AGP/K2 Lint 提供常规源码目录。脚本先调用现有链接脚本，确认链接精确指向本工程的 `repland/`，只移除该链接，再复制源码并逐项比较内容；遇到已有普通目录会明确失败。这个构建副本由 Git 忽略，`repland/` 仍是唯一入库的生产源码。Windows 开发继续使用上述 junction 流程；不要在含真实源码或已有构建副本的目录重复运行 CI 准备脚本。

Debug 产物位于 `app/build/outputs/apk/debug/app-debug.apk`。Release 为未签名构建，需要发布时另行配置签名。APK、缓存、个人配置及验证导出文件不随源码提交。

## 验证与私人 fixture

2026-10-23 公开源码复验：98 个 JVM 套件、1537 项测试全部通过；Debug、未签名 Release、设备测试 APK 及 Debug/Release Lint 通过。专用 API 26 x86 模拟器（320×640、160 dpi）发现 33 项设备测试，其中 23 项通过、10 项因未提供私人课表明确跳过。当前提交的云端结果见 [独立工作流](../.github/workflows/upstream-design.yml)。

[修复报告](docs/REPAIR_AND_TEST_2026-10-22.md)、[验收矩阵](docs/ACCEPTANCE_MATRIX_2026-10-22.md)、[跨模块记录](docs/FULL_FEATURE_ACCEPTANCE_2026-10-22.md)及[数据库迁移记录](docs/DATABASE_UPGRADE_ACCEPTANCE_2026-10-22.md)保留 2026-10-22 对应源码的本地验证结果和限制。其中日志、截图、导出数据及安装包只存在于原本机验收环境；这些数字不能代替当前提交的 CI 结果。

仪器测试仅在专用测试模拟器执行：

```bash
bash gradlew :app:connectedDebugAndroidTest
```

公开源码不包含私人课表 PDF、原课表的结构化内容或个人文件路径。`UserTimetableImportTest` 需要两个 ignored 文件：`app/src/androidTest/assets/user-timetable.pdf` 和 `user-timetable.expected.txt`；后者每行格式为 `星期数字|起始节次-结束节次|课程名|周次`，由持有私人 fixture 的开发者独立核对后在本地提供。缺少文件会显式跳过；`FullFeatureInstrumentedTest` 缺少 PDF 也会跳过。公开 JVM 回归使用合成课表，仍覆盖 19 条安排、带括号名称、断续/单周/孤立周次及长节次往返。

无真实模型或语音服务的测试不证明外部服务已可用。完整跨设备后台行为、系统小组件、真实旧用户库样本和 JSON 全量恢复仍有报告中列出的验证边界。
