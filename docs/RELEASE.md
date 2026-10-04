# Repland 内测构建与发布规则

## 版本

最新候选 `0.1.20 (1020)`：时间设置入口、课程/例外/学期编辑接入；稳定身份与重建保留、保存失败恢复、数据及约束版本原子提交、结束 24:00 支持及 Snackbar 避让。来源 `8149a8ed66356c061094e144b801bb35dce7c5c5`，115 单元、196 同源设备全套全部通过（失败/错误/跳过 0），internal、release Kotlin/Manifest、离线边界通过。同 APK 正常及深色 2 倍字号横屏各 3 项补测通过，截图已查看；当前包原位升级 1020，未读取凭据或调用真实 API。v0.1.20 独立保留，旧标签不移动。首跑专项遮挡失败报告保留；系统返回导航 QA-051 与执行反馈 QA-044/045 后续处理，M5 外部验收仍待完成。以下为历史版本证据。

最新候选 `0.1.19 (1019)`：课表只读识别、选择/编辑预览、默认钟点核对、明确确认后原子导入与去重、失败保留重试及实际数量回执。来源 `31c8e8b17c6688fa30755d7b07932d0c906ef496`，113 单元、188 同源设备全套全部通过（失败/跳过 0），internal、release Kotlin/Manifest、离线边界通过；同源真实 Activity 2 项补拍通过，已查看无遮挡预览/回执截图。当前包原位升级至 1019，原 3 个 QA 事项和 45 分钟仍在，未读取密钥、未调用真实 API。v0.1.19 独立保留，不移动旧标签。系统 watchdog 中断报告保留，同一 AVD 禁用快照/软件渲染冷启动后复测成功，不断言已定位具体显卡原因。真实用户 PDF、完整外部验收、时间设置编辑 QA-039 仍待完成。以下为历史版本证据，不跨版本沿用。

最新候选为 `0.1.18 (1018)`：补齐已有任务稳定编辑身份、重建输入恢复、数值边界、保存失败保留/重试及真实回执。来源 `2247e8379e84fa97401413d9e0a6a4019ec56c9b`，113 单元、181 同源设备全套均通过（失败/跳过 0），internal、release Kotlin/Manifest、离线边界通过。当前版原位升级，自己的 QA-draft17-only 实际编辑为 45 分钟、返回任务页且总数未增加；未读取凭据。v0.1.18 独立保留，v0.1.16 / v0.1.17 不移动。时间约束编辑 QA-039、PDF 确认 QA-040 与完整真机验收仍未完成。最终通过不抹去之前全套 180/181 和两次专项失败记录。历史真实 API 证据不跨版本沿用。下段 0.1.10 为更早身份说明；各版证据见 DELIVERY_PROGRESS。

当前候选为 `0.1.10 (1010)`。debug 使用 `.qa` application ID 隔离测试，图标名“Repland 测试”；internal 从 0.1.8 起使用 `.current`，图标名“Repland 当前版”。模拟器旧 `.internal` 签名与当前签名不同，不能保留数据覆盖；因此采用新身份，不卸载、不清空旧包，不提取旧包密钥。用户已在当前版重新配置，后续保持同一签名原位升级；0.1.10 安装后原测试事项和已确认时段仍在，未读取凭据，未重复调用模型。从仓库根目录运行 `./android/gradlew.bat -p android :app:recordBuildArtifacts` 可记录已生成 APK 的版本、来源和 SHA-256；应与打包任务一起运行。设置页显示版本、版本码及构建来源。

版本名使用语义化格式 `MAJOR.MINOR.PATCH`，例如 `0.1.0`。版本号使用整数并必须递增；推荐计算方式为：

```text
MAJOR * 1_000_000 + MINOR * 1_000 + PATCH
```

例如 `0.1.0` 对应 `1000`。默认值在 `android/gradle.properties` 中定义，也可由 CI 或构建命令通过 `-PREPLAND_VERSION_CODE=…` 与 `-PREPLAND_VERSION_NAME=…` 覆盖。

## 签名

不得提交密钥、密码或 `android/keystore.properties`。正式 `release` 构建只接受以下四个完整的 Gradle 属性、环境变量或 `android/keystore.properties` 条目：

```properties
REPLAND_RELEASE_STORE_FILE=C:/secure/repland-release.jks
REPLAND_RELEASE_STORE_PASSWORD=...
REPLAND_RELEASE_KEY_ALIAS=repland
REPLAND_RELEASE_KEY_PASSWORD=...
```

缺少任一值时，`assembleRelease` / `bundleRelease` 会在打包前失败，避免意外生成未签名发布包。可使用：

```powershell
cd android
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:bundleRelease
```

`internal` 是带 `.current` application ID 的封闭内测构建，启用压缩和资源收缩。配置正式签名后复用正式密钥；未配置时仅使用 Android debug key 便于本地和 CI 验证，不能用于公开发布：

```powershell
.\gradlew.bat :app:assembleInternal
```

## 自动化与真实设备回归

GitHub Actions 在 API 26 模拟器上执行仪器测试，并在每次推送和拉取请求执行 debug、internal、单元测试和仪器测试。发布候选还必须按 [TESTING.md](TESTING.md) 在真实设备完成通知、后台、时区、文件访问和升级迁移回归。

当前不接入崩溃报告或分析 SDK。若未来新增，必须先满足 [PRIVACY.md](PRIVACY.md) 的数据说明和用户同意要求。
