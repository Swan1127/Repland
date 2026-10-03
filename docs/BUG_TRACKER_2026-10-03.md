# Repland 缺陷记录（2026-10-03）

本文件只记录缺陷和验证证据；本轮**没有修改产品代码**。

2026-10-03 后续实施更新：用户已授权执行交付计划。QA-001 的错误捕获与领域冲突检查已在 `0.1.1` 候选代码实现，新增领域和设备 Repository 回归通过；旧正式安装包尚未替换，不表示旧包已修复。QA-002 增加版本码、构建来源和包哈希记录；QA-003 已修正 README/TESTING 的项目目录参数。完整实施验证见 DELIVERY_PROGRESS.md。

## 测试范围与环境

| 项目 | 值 |
| --- | --- |
| 当前安装包（发现崩溃） | `com.swan1127.repland`，`versionName 0.1.0`，`versionCode 1000`，安装/更新于 2026-10-01 |
| 隔离测试包 | `com.swan1127.repland.internal`，现有 `app-internal.apk`，避免改动当前安装包的数据 |
| 设备 | Android API 36.1 模拟器（`emulator-5554`） |
| 时间 | 2026-10-03（Asia/Shanghai） |

已实际走通且未发现新缺陷的路径：

- 分步创建任务（安排今天 → 具体时段 → 选择时长 → 保存）。
- 任务开始、完成；可编辑语音文字转为任务草稿（未保存）。
- 创建课程、创建命名轨道、同一轨道的重叠拦截。
- 首页的日 / 周 / 月视图切换。
- 助手本地草案生成和预览（未确认写入）。
- 课表 PDF 选择器可打开并取消，未写入任何课程。
- 从 `android/` 目录运行 `:app:testDebugUnitTest`：通过。

下列路径未测，因此不能标记为通过：真实 PDF 解析与确认导入、通知授权/后台提醒、配置凭据后的外部 AI 请求、JSON 导出、清除本地数据、升级迁移、时区/语言/深色模式回归。

## 缺陷清单

### QA-001 — 在硬约束时间段放入任务会使正式包崩溃

| 字段 | 内容 |
| --- | --- |
| 优先级 | P0 / 阻断核心排程 |
| 状态 | 已确认（当前安装包日志） |
| 影响 | 任务放入首页日轨道时，应用主进程终止；用户输入流程被打断。 |

复现步骤：

1. 在首页日轨道中已有课程、固定事项或休息等硬约束。
2. 从“待安排”选择一个新任务，指定与该硬约束重叠的时间后确认放入轨道。
3. 当前安装包崩溃。

期望：在确认前拦截冲突或显示可恢复的错误提示；应用不能终止。

实际：设备 crash buffer 记录了 `FATAL EXCEPTION: main`，进程为 `com.swan1127.repland`。首个异常为：

```text
java.lang.IllegalArgumentException:
A task cannot overlap a course, fixed commitment, or rest block.
  at RoomPlanRepository.requireNoHardTimeConstraintOverlap(...:254)
  at RoomPlanRepository.requireValidPlanSegments(...:220)
  at RoomPlanRepository.placeTask(...:135)
```

初步定位（非修复方案）：异常从数据层校验直接穿透了 `PlanViewModel` 的协程。日轨道 UI 只判断“同一轨道”是否重叠，并提示可换轨并行；旧正式包的数据层却把课程/固定事项/休息视为跨轨道也不能重叠，二者规则不一致。

### QA-002 — 崩溃包与当前工作区的版本标识无法区分

| 字段 | 内容 |
| --- | --- |
| 优先级 | P1 / 发布与排障 |
| 状态 | 已确认 |
| 影响 | 无法仅凭用户提供的版本号判断其是否仍在运行会崩溃的旧实现，难以指导升级、聚合崩溃或验证修复覆盖。 |

证据：当前设备上崩溃的正式包为 `0.1.0 (1000)`；当前工作区的 Gradle 默认版本仍为 `0.1.0 (1000)`，但工作区已经不含 crash trace 中的 `requireValidPlanSegments` / `requireNoHardTimeConstraintOverlap` 实现，隔离内测包会在同轨冲突的前置检查中禁用确认且不崩溃。这说明同一发布标识可能对应了不同的行为版本。

期望：每次可分发行为变化均使用新的、可识别的 `versionCode`，并保留可追溯的构建来源。

### QA-003 — 文档中的根目录 Gradle 回归命令无法执行

| 字段 | 内容 |
| --- | --- |
| 优先级 | P2 / 开发工作流 |
| 状态 | 已确认 |
| 影响 | 按 README / 测试文档在仓库根目录执行回归命令会立即失败，降低复现和发布验证的可靠性。 |

复现：在 `D:\Repland` 执行文档列出的：

```powershell
.\android\gradlew.bat :app:testDebugUnitTest
```

实际结果：Gradle 报 `Directory 'D:\Repland' does not contain a Gradle build.`

对照：进入 `D:\Repland\android` 后执行 `./gradlew.bat :app:testDebugUnitTest` 成功。

期望：文档命令应能从声明的工作目录执行，或显式传入 Android 项目目录。

## 复测建议（仅记录，不在本轮执行）

1. 用新的正式候选包复测 QA-001：课程、固定事项、休息三类硬约束分别覆盖同轨与异轨的重叠尝试。
2. 给所有数据层 `require` 失败路径验证非崩溃的 UI 反馈，再测试任务拖动、事件库放置和草案确认三个入口。
3. 发布候选前升级版本号，并用安装包哈希/版本号记录验证所用构建。
