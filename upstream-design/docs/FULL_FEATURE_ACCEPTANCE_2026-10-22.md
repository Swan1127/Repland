# 跨模块验收：2026-10-22

> 本报告保留 2026-10-22 原本机源码及私人 fixture 的验收结论。公开提交已移除个人文件路径、原课表数据与所有私有日志/导出文件；JVM 改用合成课表，私人 PDF 设备测试的期望清单改为 ignored 外部 fixture。当前提交结果以 CI 为准，本文数字不作为重新运行的证明。


本组用例位于 `app/src/androidTest/java/com/swan1127/repland/data/FullFeatureInstrumentedTest.kt`，只使用 in-memory Room。私人 PDF 从测试 APK 的 androidTest ignored fixture 只读读取。Instrumentation 实际运行在目标 APK UID 下，因此文件产物使用 `instrumentation.targetContext` 的独立 verification 子目录；不读取或改写主应用数据库、preferences、头像、密钥或云服务。

当前状态：**最终 build9 构建及全量 JVM 1537 项通过；device-run-4-final 的 33 项全部通过，其中本组 4 项全部通过。最终 12 份文件已拉取，计划/真实课表/长名称 PNG 已目视检查。**

2026-10-22 定向执行：`WeekPatternFormatTest` 7 项、`XlsxExporterTest` 30 项、`RoomEntityMappingTest` 44 项，共 81 项，0 失败、0 跳过；Gradle 显示 `BUILD SUCCESSFUL`，耗时 44 秒。其中严格的 19 条完整 XLSX 往返断言已通过。完整 JVM/Debug/Release 与设备结果由主执行另行记录。

| 用例 | 跨模块过程与实际核对项 | 产物 |
|---|---|---|
| checklistAndConfirmedFeedbackProduceTraceableEditableEvidenceWithoutRewritingHistory | 导入真实 19 条课表 → 创建课程任务及清单 → 全勾选不自动完成父任务 → 开始/部分完成/完成/更正 → 4 条真实 SQLite 日志不可改写且严格排序 → 画像只关联有效反馈，排除被更正的原记录 → 重复生成不重复 → 编辑结论保留来源 → JSON 可解析 | confirmed-feedback.json |
| recurringCompletionAndReplacementKeepOccurrenceEvidenceAndOriginalIdentity | 导入真实课表 → 每周任务延期/完成 → 下一期日期正确推进、清除结果、清单重置但保留定义 → 旧 COMPLETED 日志保留 → 替换任务产生新身份 → 旧任务/日志/替换关系及画像来源保留 → JSON 可解析 | recurrence-and-replacement.json |
| actualCoursesConstrainConfirmedPlansAndHistoryRestoresExportableSegments | 真实课程 + 显式可用时间 + 临时预约 → 480 分钟学习任务完全排入且避开有效课程和预约 → 确认/锁定/新增任务/重排 → 恢复旧版本时新建版本且历史保留 → 所有时间段关联真实任务 → ICS 回读时间/标题一致，应用自己的 ICS 重导入不会变成外部占用 → PNG 实际落盘且可解码 | confirmed-plan.ics、confirmed-plan.png、plan-history.json |
| realTimetableExportsProduceReadableJsonXlsxAndPngWithNoLostCourseIdentity | 真实课表 + 含引号/反斜杠/换行的任务、子任务、反馈 → 通过真实 DataManagementViewModel 导出 → JSON 全字段可解析 → XLSX 的工作簿和 5 张表 XML 可解析 → 第 1 张周课表重新导入得到完全一致的 19 条 → 写进第二个 in-memory Room 仍相同 → 课表 PNG 实际落盘且可解码 → 原数据库未改变 | real-timetable.json、real-timetable.xlsx、real-timetable.png |

产物目录通常为 `/sdcard/Android/data/com.swan1127.repland/files/verification/full-feature/`，由 `instrumentation.targetContext.getExternalFilesDir(null)` 确定。只向此验收专用子目录写 JSON/XLSX/ICS/PNG，不改写其他应用文件。应先核对实际路径，再拉取到本地私有验收目录。产物含用户提供的课程内容，不应加入共享源码归档。

设备第 2 轮初始化发现测试 APK context 的外部文件路径为 null：运行进程使用的是目标 UID，与测试 APK 的写存储身份不匹配。现已分离只读 fixture context 与可写 target context；后续第 3 轮和最终第 4 轮的 4 项测试均通过，首次初始化失败记录保留。

课表开学第一周周一在用例中显式设为 2026-08-31，**只是测试设定，不是从 PDF 推断的校历结论**。周期推进用例采用执行当天的设备日期，与真实仓库读取同一日期语义。

## 本组发现并补修的问题

1. 周期任务完成后，父任务变为下一期但旧子任务仍为已完成。修复为推进日期时将子任务 `isDone` 重置为 false，保留其 ID、标题、创建时间。纯映射回归和真实 Room 跨模块用例同时验证；历史完成日志保留。
2. XLSX 周课表导出依赖 `WeekPatternFormat.split`，原先只识别 `N-N周`，实际 PDF 的断续周、单周后缀、孤立的 `10周` 无法完整回读，导出器会将这些课程排除出可重导入的第一张表。修复完整提取有界周次表达式，并复用 `CourseWeekPattern` 对范围的解释；新增纯格式回归及 19 条完整 XLSX 回读回归。该修复没有把“仅保留在明细表”当作通过。
3. 分离周次时原先对剩余标题全局裁掉末尾括号，使“示例课程（高级）”变成残缺标题，导出器也会因此排除它。修复只删除包围周次的那一对括号，保留属于课程名的括号，并加入中英文括号回归。
4. 严格 19 条回读首次运行只得到 11 条，进一步定位到 XLSX 分组排序只比较起始节次：`1-2` 与 `1-4`、`5-6` 与 `5-8` 被 TreeMap 认成相同键，长节次行覆盖前者整组，共丢 8 条。修复排序键同时包含开始与结束节次，保留全部分组；补充最小双跨度回归及原有严格 19 条断言。
5. 第 3 轮设备共 33 项通过后，拉取实际 PNG 目视发现计划首张卡片盖住副标题，且课表图遗漏周次。现 PNG 导出使用 Android StaticLayout 测量完整标题/副标题/提示和行高，内容从上一段真实底边之后排起；长名称自动换行，课程下方显示完整周次，并标注“各周课程总览，不筛选当前教学周”。4 项 FullFeature 用例增加真实布局非重叠、19 条名称/周次完整、长文本不省略、空状态检查；补 2 项本地化周次文本 JVM 回归。此轮 PNG 修复已由 build9 和最终设备 run4 重新验证；拉图目视确认副标题不被覆盖、19 条均带周次、长文本完整换行。

PNG 额外边界产物：`wrapped-plan.png`、`wrapped-timetable.png`、`empty-plan.png`、`empty-timetable.png`。它们与实际课表和确认计划 PNG 一同写入原 verification/full-feature 目录。

## 结果的边界

- 用例验证实际仓库、Android PNG 写入、文件格式及回读；不等于点击过系统文档选择器、分享面板或外部 Excel/日历应用。
- PNG 自动核对尺寸、可解码及非空绘制；已对拉取的实际计划、课表和长标题 PNG 完成文字截断/对齐目视复核。
- JSON 只验收当前业务快照格式，不声称全应用数据可恢复；应用暂没有完整 JSON 恢复入口。
- 生产行为修改后按 AGENTS 由主执行统一运行完整 JVM、Debug/Release 和设备测试。本子任务只在主执行确认独占后运行上述 81 项定向 JVM 回归，未操作模拟器。
