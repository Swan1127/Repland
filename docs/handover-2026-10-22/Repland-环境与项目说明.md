# Repland 项目与本机开发环境

日期：2026-10-07（Asia/Shanghai）。仓库分支 `codex/placement-numeric-integrity`，提交 `7506074f36d822b4c3d57777e40ba50a78ccdb15`，源码版本 `0.1.29 / 1029`。

## 项目梳理

项目是 Android 本地优先任务规划应用。按整体结构检查了应用入口、主页面与 ViewModel、业务模型和操作服务、Room 存储与迁移、提醒、PDF 导入、远程 AI 适配器、依赖配置、测试及 CI；并对全部 Git 跟踪文本文件做符号、导入和文档标题扫描。此次工作用于准备环境，未开展逐行缺陷审计或修改产品功能。

319 个 Git 跟踪文件中有 202 个 Kotlin 文件、51 个 Markdown 文档、3 个 Gradle Kotlin 配置文件和 2 个 Node.js 工具文件。完整静态索引见 [文件索引](Repland-文件索引.md)。

| 模块 | 作用与关键代码 |
| --- | --- |
| 应用入口与注入 | `MainActivity.kt`、`ReplandApplication.kt`；AppContainer 连接 Repository、业务服务和 Provider |
| 前端 | `ui/ReplandApp.kt` 协调今日、任务、助手、我的；其他 `ui/` 文件提供页面、编辑抽屉和 ViewModel |
| 规划 | `domain/model/Plan.kt`、`PlanningPriority.kt`、`PlanningOperationService.kt`；本地排序、30 天规划窗口、30 分钟槽、预览与确认 |
| 数据 | `data/room/`；Room schema 20，显式注册 1→20 迁移；计划与草案确认使用事务 |
| 执行与提醒 | 执行会话 Repository 与 `reminders/`；用户确认反馈，系统提醒跟随已确认计划 |
| PDF 课表 | `data/importer/PdfTimetableImporter.kt`；使用 PDFBox Android 生成候选，确认后保存 |
| AI | `src/remote/` 内的兼容 HTTPS 模型接口；debug/internal 包含联网实现，release 使用本地 Advisor；凭据保存在 Android Keystore |
| 测试 | `src/test/` JVM 单元测试、`src/androidTest/` Android 仪器与 UI 测试；`.github/workflows/android.yml` 定义 CI |
| Node 工具 | `tools/agnes-dev-gateway.mjs` 是辅助开发网关，使用 Node 内置模块；当前 Android 应用直接使用用户配置的 HTTPS 服务，无需启动此网关 |

基础功能无需数据库服务器、Python、Docker、NDK 或 CMake。Compose、Room、KSP、PDFBox 等是 Gradle 依赖，由构建下载，不需要另外安装成软件。使用联网 AI 时，用户在应用界面自行配置服务与 API Key。

## 已准备的软件

开发环境位于本文件旁的 `Environment/`，项目位于 `Repland/`。通过启动脚本设置当前进程环境，不修改系统全局 PATH。

| 环境 | 安装版本 / 位置 |
| --- | --- |
| Android Studio | Rabbit 1 / 2026.2.1，`Environment/AndroidStudio/android-studio/`，包含 IDE 所需的 Java 25 |
| 构建 JDK | Azul Zulu OpenJDK 17.0.20.1，`Environment/JDK17/`；与 CI 的 Java 17 主版本一致 |
| Gradle | 9.1.0；项目 Wrapper 的发行包已预置到独立 Gradle 缓存 |
| Android SDK | `Environment/Android/sdk/`；Platform android-36、Build Tools 36.0.0 |
| SDK CLI | Command-line Tools 22.0 |
| ADB | Platform Tools 37.0.1 |
| 模拟器 | Emulator 37.2.12，Android 36 Google APIs x86_64 系统镜像 |
| AVD | `Repland_API_36`，Pixel 6、1080×2400、2 GiB RAM；配置可被 emulator 和 avdmanager 列出 |
| Git / Node | Git 已可用；当前工具环境中已有 Node 24.19.0；辅助网关不需要 npm 包 |

项目 `android/local.properties` 已配置本机 SDK 路径，受 Git 忽略。`Environment/gradle-home/gradle.properties` 指定 JDK 17。若迁移目录，需要更新这两个文件以及 `Environment/studio.properties` 中的绝对路径。

网络连接不稳定时使用本环境的 Gradle 初始化脚本优先访问华为云和阿里云 Maven 镜像，原仓库依赖版本不变；文件位于 `Environment/gradle-home/init.d/repland-mirrors.gradle`。删除此文件可恢复项目原有上游仓库。

下载核对：Android Studio ZIP 和 SDK CLI ZIP 的 SHA-256 与 Android 官方下载页一致；Gradle ZIP 与项目 Wrapper 固定 SHA-256 一致。JDK ZIP 来自 Azul 官方 CDN，记录 SHA-256 `552a335d5342c800fd03b45a23bb1136f5aa5562193542ece506d8c44b0c60d9`（本地记录值，未取得独立官方校验文件）。

## 使用

2026-10-07 补充验证：已在普通 Windows 环境启动 Android Studio 和模拟器。AVD 显示名称已改为 `Pixel 6`，内部标识仍为 `Repland_API_36`，现有启动脚本可继续使用。WHPX 加速检查通过；ADB 确认 `emulator-5554` 在线、`sys.boot_completed=1`、Android 版本 `16`、ABI `x86_64`。默认目录 `C:\Users\x16\.android` 已创建。此结果更新下文此前在沙箱中的模拟器启动限制，不代表 APK 构建验证已通过。

1. 双击 `Environment/Open-Repland.cmd`，打开已配置环境的 Android Studio 和项目 `Repland/android/`。
2. 双击 `Environment/Build-Repland.cmd`，运行单元测试，构建 debug/internal 包并检查离线边界。
3. 双击 `Environment/Start-Repland-Emulator.cmd` 启动专用模拟器，也可在 Android Studio 的 Device Manager 中使用 `Repland_API_36`。

PowerShell 中也可以先执行：

```powershell
. .\Environment\Activate-Repland.ps1
& "$env:REPLAND_PROJECT\gradlew.bat" -p $env:REPLAND_PROJECT '-Pkotlin.compiler.execution.strategy=in-process' :app:testDebugUnitTest :app:assembleDebug :app:assembleInternal
```

debug 包为 `com.swan1127.repland.qa`，internal 包为 `com.swan1127.repland.current`。设备测试只对隔离 QA 包运行；跨机器 debug 签名不同，不能据此覆盖已有用户包或以卸载解决签名冲突。

## 本次验证

SDK 安装命令成功；Gradle Wrapper、JDK 17、AVD 列表检查通过。debug、internal、release、JVM 单元测试和 Android 仪器测试的外部编译 / 运行依赖已解析并下载，准备任务 `BUILD SUCCESSFUL`；离线边界 `:app:verifyOfflineMvpBoundary` 通过。Node 辅助网关测试 1 项通过，测试使用假上游，没有真实模型请求。

APK 构建未通过，停在 `:app:processDebugResources`：AAPT2 进程退出。进一步使用一个与 Repland 无关的最小 Manifest 链接 Android 36 框架，AGP 自带 AAPT2 与 SDK Build Tools 的 AAPT2 均返回 `-1073741819`（Windows 原生访问违规 / `0xC0000005`）。说明该失败也会发生在最小打包调用，根因尚未确定，不能归结为具体 Repland 业务代码缺陷。没有为了绕过失败修改源码或降低版本。JVM 单元测试因构建依赖未完成而未运行，Android 设备测试也未运行，不能沿用交接文档的旧机器 127 / 267 测试通过记录。

请在普通 Windows 会话中双击 `Environment/Build-Repland.cmd` 复跑。如果仍出现 AAPT2 崩溃，需要继续排查本机工具执行环境；本次不能承诺离开沙箱后一定通过。构建与诊断日志见 `Environment/verification/`，依赖准备结果见 `prepare-dependencies-final.log`。

当前沙箱对 Java 的 Windows 目录权限检查存在差异：JDK 25 的 `File.canWrite()` 返回 false，JDK 17 返回 true，而实际写入均可成功；因此构建固定使用 JDK 17。ADB 默认需要 `C:\Users\x16\.android`，即使本次已获目录权限授权，沙箱仍拒绝创建；普通 Windows 环境运行启动脚本时应再次验证。

模拟器硬件加速检查返回代码 6，提示检查 Windows Hypervisor Platform 与 Hyper-V 功能。未启用 Windows 系统功能、安装驱动或重启电脑，未宣称模拟器已成功启动。若普通环境仍有此提示，在“启用或关闭 Windows 功能”中检查 Windows Hypervisor Platform，按系统提示处理重启后复测；也可连接专用 Android 真机。

官方参考：[Android Studio 下载](https://developer.android.com/studio)、[AGP 9.0 兼容要求](https://developer.android.com/build/releases/agp-9-0-0-release-notes)、[Azul Zulu](https://www.azul.com/downloads/)。
