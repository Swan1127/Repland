# Repland 构建与发布规则

更新日期：2026-10-22（Asia/Shanghai）。当前版本配置为 `0.1.35 / 1035`，主分支 HEAD 为 `08a686b8c371a54b4eb2228fa9141217906ebe5c`（2026-10-20）。版本配置、源码快照、生成的 APK、设备安装与发布状态必须分别记录。本次只整理文档，没有构建、安装、发布 APK 或确认 CI 成功；原有 7 个未提交 Kotlin 文件变更仍属于工作区待验证状态。

产品目标见[主思路](../REPLAND_IDEA.md)，构建入口见[README](../README.md)，工程约束见[工程边界](EXTENSION_READINESS.md)。历史逐版候选说明已由[文档索引与备份记录](DOCUMENTATION.md) 管理，不再作为“当前版本”重复堆叠。

## 四种构建身份

基础包名为 `com.swan1127.repland`；以下配置以 `android/app/build.gradle.kts` 为准。

| 构建类型 | 包名 / 应用名 | 版本名 | 能力与用途 |
| --- | --- | --- | --- |
| `debug` | `.qa` / Repland 测试 | `0.1.35-qa` | 独立 QA 数据库；含可选 BYOK 远程适配器，默认仪器测试目标。 |
| `designPreview` | `.design` / Repland 设计副本 | `0.1.35-design` | 独立设计副本与效果演示 Activity；含可选远程适配器，需显式选择测试构建。 |
| `internal` | `.current` / Repland 当前版 | `0.1.35-internal` | 封闭内测，启用压缩与资源收缩，含可选远程适配器；保持同签名原位升级。 |
| `release` | 基础包名 / Repland | `0.1.35` | 正式发布构建，启用压缩与资源收缩；不编入远程源码和联网 Manifest，使用本地能力。 |

远程源码只加入 debug、designPreview、internal，Manifest 声明 Internet 权限并禁止明文流量。包含适配器不等于已经配置或获准发送数据；运行时仍需对应功能开启、用户同意与有效配置。release 没有该远程能力，保存配置不会使其联网。`verifyOfflineMvpBoundary` 检查 `src/main` 的禁止标记，不能据其通过断言 QA / design / internal 没有网络能力。

包名隔离不能替代签名和数据保护。旧 `.internal` 与 `.current` 是不同身份，不得为了覆盖旧安装而卸载、清空数据或读取旧包密钥；也不得把历史设备记录写成当前安装状态。各包的个人数据与凭据不应相互复制来完成测试。

## 版本、签名与构建

版本默认值位于 `android/gradle.properties`，可用 `-PREPLAND_VERSION_CODE=…` 与 `-PREPLAND_VERSION_NAME=…` 覆盖。版本名采用语义化格式，版本码为正整数；同包后续更新须使用递增版本码并保持签名。推荐计算规则为 `MAJOR * 1_000_000 + MINOR * 1_000 + PATCH`，例如 `0.1.35 → 1035`。本轮没有调整版本号。

不得提交密钥、密码或 `android/keystore.properties`。正式 release 需要以下四项完整配置，来源优先级为 Gradle 属性、环境变量、`android/keystore.properties`：

```properties
REPLAND_RELEASE_STORE_FILE=C:/secure/repland-release.jks
REPLAND_RELEASE_STORE_PASSWORD=...
REPLAND_RELEASE_KEY_ALIAS=repland
REPLAND_RELEASE_KEY_PASSWORD=...
```

缺少任一值时，release 打包/签名任务会失败。internal 在完整配置存在时复用正式密钥，否则使用 Android debug key 作为本地/封闭测试回退；这种回退不能当成公开发布签名。更换 internal 的签名来源可能阻止原位升级，操作前必须核对已有安装身份。

在仓库根目录使用 PowerShell：

```powershell
.\android\gradlew.bat -p android --no-daemon :app:assembleDebug
.\android\gradlew.bat -p android --no-daemon :app:assembleDesignPreview
.\android\gradlew.bat -p android --no-daemon :app:assembleInternal
# 仅在完整发布签名配置就绪后执行：
.\android\gradlew.bat -p android --no-daemon :app:assembleRelease
.\android\gradlew.bat -p android --no-daemon :app:bundleRelease
```

构建所需 JDK、SDK、隔离测试与 Windows 编译设置见[测试清单](TESTING.md)。命令示例是复验入口，不表示本轮已经执行。

## 发布必要验收

1. 固定交付源码身份：完整提交、工作区差异、版本属性和工具链；如交付包含未提交变更，必须有可恢复的对应快照及独立验证结果，不能只引用 HEAD。
2. 对相同源码运行 debug / internal 构建、JVM 测试、debug / internal Lint、离线边界与隔离设备回归；按实际交付补测 designPreview 或 release。CI 的三个状态名称为 `Build and unit tests`、`Android Lint`、`Instrumented tests (API 26)`，应设为主分支必需检查；不能据文档推定分支保护已经配置。
3. 按[测试清单](TESTING.md) 补齐最低支持版本 API 24/25、API 36 和真实设备的通知、后台、时区、PDF 文件访问、升级迁移及核心流程；明确未覆盖设备和失败/跳过项目。CI API 26 不等于最低版本验收。
4. 对正式 release 核对正式签名、包名、版本、压缩后的运行和本地能力；对封闭远程构建补验授权、取消、超时与无效输出。同步核对[隐私说明](PRIVACY.md)，不得把真实凭据、数据库或个人模型正文放入公共证据。
5. 记录交付 APK / AAB 的 SHA-256、签名身份、源码对应关系与实际验收范围；区分“已生成”“已安装验收”“已上传”“已公开发布”。未完成的阶段保持待验收，不提前宣称完成。

当前未接入崩溃报告或分析 SDK；未来新增时须先更新隐私说明与授权机制。

## 产物与证据身份

将 `:app:recordBuildArtifacts` 与目标 APK 构建一起运行，可在 `android/app/build/outputs/build-manifest.txt` 记录当前版本、HEAD 和输出目录内 APK 的 SHA-256，例如：

```powershell
.\android\gradlew.bat -p android --no-daemon :app:assembleInternal :app:recordBuildArtifacts
```

该任务读取 APK 输出目录中的所有 APK，不证明每个文件都由本次命令生成；必须核对实际交付文件及其生成来源。`BUILD_SOURCE` 只记录 HEAD 的短提交，清单的 `source` 也只记录 HEAD，均不能表达未提交差异。AAB 的哈希需单独记录。安装验收还需核对设备上包名、版本、签名与实际 APK，不能以设置页或旧安装记录代替产物一致性检查。

保留的公共历史证据为：

- [2026-10-18 自动使用总结验证](verification/automatic-usage-summary-2026-10-18/README.md)：同版本 `0.1.35 / 1035`，当时 218 JVM、108 个不同设备用例；针对对应源码和专项范围。
- [2026-10-11 验收记录](verification/final-0.1.35/README.md)：更早的同版本源码与 APK，150 JVM、310 QA 设备用例；包含当时设备与来源限制。

两批证据不能累计、不能因版本号相同而互相替代，也不能证明 2026-10-20 HEAD 或当前未提交变更已通过。每次交付应在对应源码清单与新结果之间建立关系，并保留失败记录。文档清理的完整备份和恢复入口见[文档索引](DOCUMENTATION.md)。
