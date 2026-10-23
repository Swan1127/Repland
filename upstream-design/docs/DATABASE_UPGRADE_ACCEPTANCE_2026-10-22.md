# 真实数据库升级验收

> 本报告保留 2026-10-22 原本机源码及私人 fixture 的验收结论。公开提交已移除个人文件路径、原课表数据与所有私有日志/导出文件；JVM 改用合成课表，私人 PDF 设备测试的期望清单改为 ignored 外部 fixture。当前提交结果以 CI 为准，本文数字不作为重新运行的证明。


用例：`app/src/androidTest/java/com/swan1127/repland/data/room/DatabaseUpgradeInstrumentedTest.kt`，新增 **2 项设备测试**。最终设备 run4 中这 2 项均已通过；完整构建及 JVM 结果见主报告。

现有 `DatabaseMigrationChainTest` 只检查迁移声明、注册和连续性。本组使用真实磁盘 SQLite 文件，由 Room 选择迁移链、执行 SQL、验证 v21 最终 schema，再通过仓库读取业务关联，补足运行时覆盖。

| 用例 | 历史文件构造 | 升级后的核对 |
|---|---|---|
| v1 → v21 | 独立手写 v1 `tasks` DDL，写入带旧反馈快照的任务；不预建任何新版本表 | 20 次迁移真实执行；任务 ID、名称、状态、时长、进度、延期计数和时间戳保留；新列为 null/空清单，新表为空；不伪造历史日志；升级后通过真实仓库仍可写反馈 |
| v20 → v21 | 从相同 v1 DDL 顺序重放 `MIGRATION_1_2` 至 `MIGRATION_19_20` 的历史 SQL，形成实际 v20 schema；向全部 14 张业务表写入有关系的合成数据；先证实没有 `repeatRule` 列 | Room 仅执行 20 → 21；任务的原子任务 ID、标题、勾选、创建时间保留；已有任务的 `repeatRule` 为 null；课表周次、备注、时间例外、学期、日程锁定/排序、日志、画像来源、分类偏好、提醒、AI 开关、参与模式、使用事件全部保留 |

Instrumentation 运行在目标 APK UID 下，数据库使用 `instrumentation.targetContext.getDatabasePath("repland-migration-verification-<UUID>.db")`，并明确创建其数据库父目录。每个用例在 `finally` 中只关闭并删除这个 UUID 文件及其 SQLite 附属文件。不会打开或替换用户的 `repland.db`，不会清空共享 databases 目录，也不使用 destructive migration fallback。测试 APK context 仅适合读取它自己的 fixture assets，不用于跨 UID 写数据库。

首次设备执行因使用测试 APK context 写入不匹配 UID 的数据库路径，初始化报 SQLiteCantOpenDatabaseException(code 14)；现已修正 context 和目录创建，后续 run3 及最终 run4 两项迁移测试均通过。初始化失败日志保留，不计为当次迁移已验证。

历史 schema 的来源与限制：当前工程 `exportSchema=false`，没有历史 JSON 快照。因此 v1 的 `tasks` DDL 根据仍然保留的完整迁移链推导：只有 5 → 6、18 → 19、19 → 20、20 → 21 分别增加 `completionResult`、`scheduledForEpochDay`、`subtasks`、`repeatRule`；其余 task 列未见结构变化。v20 由这份基线逐步执行历史迁移 SQL 构造。测试没有创建当前 Room schema 后把 `user_version` 降级伪装成旧库，也不声称拥有独立存档的真实历史用户数据库。
