# Repland 回归测试清单

## 自动化命令

在仓库根目录执行：

```powershell
.\android\gradlew.bat :app:assembleDebug
.\android\gradlew.bat :app:assembleInternal
.\android\gradlew.bat :app:testDebugUnitTest
.\android\gradlew.bat :app:connectedDebugAndroidTest
```

CI 使用 API 26 模拟器覆盖最低兼容范围。每个候选内测包还应在 API 36 模拟器启动并完成核心流程。

## 真实设备发布前清单

- 安装旧版本并保留任务、计划、时间约束和执行日志，升级到候选包；确认 Room migration 完成且数据完整。
- 开启与关闭提醒，分别检查通知权限被拒绝、授予、设备重启、系统时区变化和后台限制后的行为；草案不得创建提醒。
- 通过系统文件选择器导入有效和无效 PDF，检查导入预览、编辑、取消和确认后写入；确认前不得新增固定时间约束。
- 创建任务、确认计划、记录反馈、部分完成、延期、更正日志、替换任务与历史计划恢复；确认历史只追加、不被覆盖。
- 断网、关闭 AI 与 AI no-op 失败时，确认本地排程和数据导出仍可用，且没有任务内容离开设备。
- 使用不同系统语言、深色模式与真实时区检查日期、时间段和截止提醒展示。

在包含用户真实数据的设备上不得运行自动化仪器测试；应使用专用测试设备或模拟器。
