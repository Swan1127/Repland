# Repland 内测构建与发布规则

## 版本

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
