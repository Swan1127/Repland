# 0.1.35 可携带验收证据

记录日期：2026-10-11（Asia/Shanghai）。本目录只包含公共源码身份、去除系统输出的用例结果与已核对的非敏感页面截图，不包含应用数据备份、模型配置或密钥。完整入口与边界见[最终技术验收](../../FINAL_TECH_ACCEPTANCE_2026-10-10.md)。

| 证据 | 含义 |
| --- | --- |
| [source-manifest.json](source-manifest.json) | 每个公共 Android 源文件 / 构建输入的原字节及 LF 标准化 SHA-256；生产清单摘要 25a6c9f4e7e1917886c05d0a5dee7b3be6262adebaa5c8a412dafab32108ec75。 |
| [build.json](build.json) | 0.1.35 / 1035 / Room 21 的实际 APK 哈希和包名，区分完整自动化 QA 与实际使用 internal。 |
| [unit-tests.json](unit-tests.json) | 2026-10-11 01:14:22 UTC 的 150 JVM 用例全部通过，合计 0.630 秒；后续生产 / 单元源码未改变，未重复计入旧版结果。 |
| [device-tests.json](device-tests.json) | 最终 310 QA 仪器化用例全部通过，413.933 秒，0 失败 / 错误 / 跳过；Gradle 整体 7 分 34 秒。保留完整类名 / 方法名 / 时长。 |
| [test-history.json](test-history.json) | 本批红色复现、专项、307 / 309 / 310 失败全套及原用例单项结果；成功不抹去失败。 |
| [numeric-payload-comparison.json](numeric-payload-comparison.json) | 同一 12 任务隔离夹具：必要全量证据 2872 字节，参数请求 354 字节，数值一致；不是实际 token / 费用或真实模型质量结论。 |
| [真实 AI 确认回执](remote-1035-confirm-receipt.png) | .current 1035 经真实返回、未确认检查及冷恢复后，确认新增 1 / 写入 1。 |
| [安排后的任务详情](current-1035-task-detail.png) | .current 1035 仍未开始、暂无执行记录，未知类别 / 优先级 / 天数保留。 |
| [实际未知画像](current-1035-numeric-unknown.png) | .current 1035 只有安排、没有执行证据时，0 样本保持未知。 |

最终设备全套在重启后的 GMT 时区运行；实际模型复验与结束后的当前包检查使用 Asia/Shanghai。不是完整跨时区验收。模拟器仅 Repland_API_36 / API 36 / x86_64，不代表手机或 OEM。

APK 内 BuildConfig 来源仍为 85c75f04117b 加当时工作区，不能称为干净的 85c75f0 构建。验收后的源码提交 32d766f95224b3851dbb832bd9644b541b3436cf 与清单 261 项逐文件对应，提交文档不更改安装 APK。internal SHA-256 为 9c5a54385aad72ca6e0d8ac2e4477862c83bb93ac4f23acccfd12722267341b2，实际安装 base.apk 已只读拉取并比对一致。debug 为 184c98ced09237f09aa91a5b3548fd37bae38599d1002fb69bf6c79fbbaa4d07，最终 test APK 为 eb081a78253ba71358900695b6158f3c55622da41cffe6e531ca32a59e589ebc。

原始 XML、运行日志、失败截图与三份 APK 保留在本机 `android/app/build/reports/final-delivery-2026-10-10/`，最终 APK 独立保存在其 `retained-0.1.35-final/`；0.1.33 / 0.1.34 历史版本保留，不提交大体积二进制或私有运行配置。JSON 用例清单删除了 JUnit system-out / system-err / properties，不能用作完整原始日志。

复验需自行安装 JDK 17、SDK 36 / Build Tools 36.0.0，并使用隔离 QA 设备。项目根目录的 Android 工程执行：

```text
gradlew.bat --offline --max-workers=1 :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleInternal :app:compileReleaseKotlin :app:processReleaseMainManifest :app:verifyOfflineMvpBoundary :app:recordBuildArtifacts
```

上述命令不是已授权清空数据的指令；不卸载、不清数据，不在用户当前包运行仪器化测试。离线重建需要已缓存依赖；不同签名 / 工具链可能产生不同 APK 字节。原接手测试 UnavoidablePlanningTest.kt 字节哈希仍为 85611f9da5c034ad7dfc2cf0c85f5ec1471501609717cf7b8ba1c092068a6be5；原有文件末尾空行保留，检查只对此项原样空白作例外。
