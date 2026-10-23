# Repland 回归测试清单

更新日期：2026-10-23（Asia/Shanghai）。本清单针对 `android/` 整合工程 `0.1.35 / 1035`；整合源码已随 `d5b7c7a` 提交 GitHub。当前独立主开发版为 [`upstream-design/`](../upstream-design/SETUP.md) 中的 `1.0.1-dev / 2`，使用独立工作流验证，两个工程的测试数量与结果分别记录。下文历史证据只适用于对应源码快照，不能代替当前提交的 CI。

产品范围见[主思路](../REPLAND_IDEA.md)，工程约束见[工程边界](EXTENSION_READINESS.md)，构建身份与交付要求见[发布规则](RELEASE.md)。

## 环境与 CI 的实际范围

| 项目 | 当前仓库配置 |
| --- | --- |
| 构建 JDK | CI 使用 Temurin JDK 17；Java 源码与字节码兼容级别为 11，两者不是同一个设置。 |
| Android SDK | `compileSdk = 36`、`targetSdk = 36`、`minSdk = 24`；CI 安装 Build Tools `36.0.0`。 |
| CI 仪器测试 | API 26、Google APIs、x86 模拟器；先验证 KVM、构建应用与测试 APK，再启动模拟器执行测试。 |
| 最低版本缺口 | API 26 不是 `minSdk 24`。API 24/25 仍需单独验证，不能由 API 26 结果代替。 |
| 本地设备范围 | 既有历史记录包含 API 36 模拟器；它不能替代最低支持版本或真实手机/OEM 行为验证。 |

[Android 工作流](../.github/workflows/android.yml) 在 push / pull request 配置了三个独立状态：

- `Build and unit tests`：debug、internal 构建，debug JVM 测试及离线主源码边界检查。
- `Android Lint`：debug 与 internal Lint，错误导致失败；上传对应报告。
- `Instrumented tests (API 26)`：依赖构建任务，使用隔离 debug 包执行设备测试；缺少 KVM 访问或硬件加速时失败。

工作流配置不代表任一提交的 CI 已通过。designPreview、release、API 24/25、API 36 和真实设备验收不在上述完整 CI 矩阵内，必须分别记录结果。

## 本地命令与隔离

在仓库根目录使用 PowerShell，安装对应 JDK / SDK 后按需串行执行：

```powershell
.\android\gradlew.bat -p android --no-daemon :app:assembleDebug :app:assembleInternal :app:testDebugUnitTest :app:verifyOfflineMvpBoundary
.\android\gradlew.bat -p android --no-daemon :app:lintDebug :app:lintInternal
.\android\gradlew.bat -p android --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
.\android\gradlew.bat -p android --no-daemon :app:connectedDebugAndroidTest
```

默认仪器测试目标为 QA 包 `com.swan1127.repland.qa`。需要验证设计副本时，必须显式选择其构建类型，并使用专用设备和测试数据：

```powershell
.\android\gradlew.bat -p android --no-daemon '-PreplandTestBuildType=designPreview' :app:assembleDesignPreview :app:assembleDesignPreviewAndroidTest :app:testDesignPreviewUnitTest
.\android\gradlew.bat -p android --no-daemon '-PreplandTestBuildType=designPreview' :app:connectedDesignPreviewAndroidTest
```

设计副本是独立的 `.design` 包，但独立包名不代表其中没有真实数据。不得对包含个人数据或模型配置的 QA / design / 当前使用包运行会改写数据库的仪器测试。不得为了通过安装而卸载旧包、清空数据或更换签名。Gradle 已设置测试后保留 APK、禁止自动卸载不兼容包；这些选项不能代替操作者检查设备与包身份。

`android/gradle.properties` 已设置进程内 Kotlin 编译、关闭 Gradle daemon / 并行并限制 worker。Windows 曾出现 Kotlin 子进程占用构建文件；遇到同类错误时，先确认本轮进程与文件锁，串行重试。显式传入 `'-Pkotlin.compiler.execution.strategy=in-process'` 时保留 PowerShell 引号。不要全局结束 Java 进程。只有依赖已缓存时才使用 `--offline`。

## 必须覆盖的回归边界

| 范围 | 验收要点 |
| --- | --- |
| 事项与输入 | 创建、编辑、未知时长、非法原数字、日期和分类保留；未通过校验不得写入；保存失败保留输入，重试有真实回执，重复提交不重复落库。 |
| 时间与安排 | 固定时间、课程、学期、例外、跨日与结束 `24:00`；可用时间、保护时段、事项归属、分段与冲突校验；约束变化或历史恢复后重新检查，草案不产生正式安排与提醒。 |
| 自主安排与助手 | 显式授权、当前模式和操作范围约束；读取失败不当成空事实；澄清、建议、草案、确认及允许的自动动作保持各自边界，模型输出必须通过本地校验，失败与取消可恢复。 |
| 执行与历史 | 开始、专注、部分完成、结束、中断、延期、取消、替换、更正与恢复；稳定身份和执行日志可追溯，历史事实不因新计划被覆盖，写入与回执一致。 |
| 页面与恢复 | 任务、日程、助手、我的及设置的返回来源；抽屉/键盘返回，旋转、进程重建、滚动、大字号、深色和横屏；操作中输入、错误和确认入口可见。 |
| 数值画像与使用总结 | 无证据保持未知；安排不冒充执行，来源更正后失效重算；自动生成、缓存、退避、取消、配置变化、清空数据与迟到响应符合当前授权及数据版本。 |
| 模型与离线能力 | 关闭或未配置时对应功能零模型请求；远程构建另测同意范围、无效密钥、超时、无效输出、取消和迟到回复。本地规划、事实摘要及导出在断网或模型失败后仍可用。 |
| 数据与隐私 | Room 升级迁移、导出、清空及重建；凭据、请求正文和个人总结不得进入公共证据；不同包数据库和配置彼此隔离。 |

数值领域的稳定定义见[数字契约](features/H01_NUMERIC_CONTRACT.md)，模型与数据边界见[隐私说明](PRIVACY.md)。主思路中的后续设想只有实现并取得对应证据后，才加入“已通过”范围。

## 真实设备发布前清单

- 从保留事项、计划、时间约束与执行日志的旧版本原位升级候选包，核对签名、版本、Room migration 与数据完整性。
- 通知权限拒绝/授予、提醒开关、重启、时区变化及后台限制；只对有效正式安排创建或重排提醒。
- 使用系统文件选择器导入有效/无效 PDF，核对预览、编辑、取消、确认与去重；确认前不新增固定时间约束。
- 实走创建事项、安排确认、执行反馈、更正、延期、替换及历史恢复，检查可追溯的事实与回执。
- 使用不同系统语言、深色、字号、横竖屏及真实时区检查时间和截止提醒；分别记录 API 24/25、API 26、API 36 与真实硬件覆盖。
- 按构建类型验证联网差异；正式 release 只依赖本地功能，不因保留配置而获得远程适配器。

## 历史证据与本轮状态

| 日期与证据 | 当时记录的结果 | 使用边界 |
| --- | --- | --- |
| [2026-10-18 自动使用总结验证](verification/automatic-usage-summary-2026-10-18/README.md) | `0.1.35 / 1035`；218 JVM 用例、108 个不同设备用例通过。108 为去重后的数量，不能把 104 + 20 写成 124。 | 针对当时源码与专项范围的历史记录，不等于历史全套或当前工作区重测。 |
| [2026-10-11 验收记录](verification/final-0.1.35/README.md) | 同为 `0.1.35 / 1035`；150 JVM、310 QA 仪器用例通过。 | 更早源码、APK 与设备状态的证据，不可与 10 月 18 日结果累计或互相替代。 |
| 2026-10-22 文档整理 | 读取源码、Gradle 与 CI 配置，更新文档并保留备份。 | 没有新增构建、Lint、测试、模型请求或设备验收结论。 |

同一版本号可对应不同源码快照。复验时必须同时记录完整提交、未提交差异/源码清单、测试变体、APK SHA-256、设备 API / ABI / 时区、命令和原始结果，保留失败与跳过，不能只报通过数量。文档保留与恢复方式见[文档索引](DOCUMENTATION.md)。
