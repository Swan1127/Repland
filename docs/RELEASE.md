# Repland 内测构建与发布规则

## 版本

最新候选为 `0.1.16 (1016)`：大字号任务标题布局与横屏新增按钮安全区修复，保留整体风格。110 单元、19 同源设备专项通过，深浅色大字号竖屏及深色横屏手查通过（局部范围）；独立基线标签 `v0.1.16`。后续任务创建重设计另升版本，不改此标签。0.1.14 的 152 项全套和真实 API 成功为历史证据。下段 0.1.10 为更早身份说明；各版证据见 DELIVERY_PROGRESS，不跨版本沿用。

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
