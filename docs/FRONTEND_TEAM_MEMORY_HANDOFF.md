# 前端、多角色规划与记忆模块交接

日期：2026-10-22。按委托人最新要求，停止继续扩展，先以草稿 PR 交接当前成果，由下一位开发者完成验收。**本 PR 不是已验收发行版，不应直接合并或覆盖正式安装。**

## 基础与范围

- 基于 `Swan1127/Repland` 的 `main`：`08a686b8c371a54b4eb2228fa9141217906ebe5c`。
- 改动直接融合到现有 `android/` 主应用，不是另附一个独立预览应用。
- 不替换上游 Room 数据库、迁移、Task 输入来源、执行会话、数值画像、自动安排、持久规划工作区、草案版本校验与原子确认。
- 保留上游签名规则、最低 Android 版本及离线发布边界；未加入新的云模型调用、凭据或个人数据库。
- 早期独立改造版的 1654 条测试及模拟器记录不适用于本主应用 PR，不能用作本次验收结论。

## 已实现的增量

1. **视觉共享层**：暖纸底、奶油表面、薄荷主操作色与深青文字；静态山景插画、语义类别色、按压与页面/展开动效。保留现有界面操作，兼容深色及系统减少动画设置。
2. **真实本地六角色规划**：在 `PlanningOperationService` 中连接协调器和真实工具事件，接回原生生成器与持久草案；保留仅排序、仅安排今天、锁定段、执行中任务与人工确认规则。界面可查看角色/工具记录，取消生成不等于确认计划。
3. **记忆模块**：独立本地复习存储、忘记/模糊/记得反馈、默认关闭的只读规划参考、软时段偏好、完整 JSON 导出和清理接线。
4. **主界面接线**：个人页及设置提供“学习与复习”，可选择真实任务并主动填写学习内容；设置可切换记忆参考，规划预览展示真实团队记录。
5. **回归测试源码**：补充记忆模型、编解码、旧复习迁移、导出、团队运行及主应用 VM 测试；主服务的新团队边界仍需补充专项回归。

记忆模型是附件中的 **FSRS-inspired 自适应模型，并非官方完整 FSRS 实现**，没有拟合用户个人参数。可提取率是模型估计，不是测得的能力。六角色使用本地确定性工具和 `NoOpAiAdvisor`，不应宣传成六个在线大模型。

## 关键代码入口

- `android/app/src/main/java/com/swan1127/repland/ui/ReplandApp.kt`：主界面入口、复习、导出与清除后的界面刷新。
- `android/app/src/main/java/com/swan1127/repland/ui/theme/`：色板、语义色、排版与动效。
- `android/app/src/main/java/com/swan1127/repland/ui/plan/PlanningTeamLogPanel.kt`：规划记录及生成状态。
- `android/app/src/main/java/com/swan1127/repland/domain/planteam/`：六角色、工具、日志与协调器。
- `android/app/src/main/java/com/swan1127/repland/domain/model/PlanningOperationService.kt`：原生业务边界与团队接线。
- `android/app/src/main/java/com/swan1127/repland/ui/plan/PlanViewModel.kt`：持久草案、生成取消与团队显示状态。
- `android/app/src/main/java/com/swan1127/repland/data/AuxiliaryStore.kt`：本地复习记录、参考开关及清理。
- `android/app/src/main/java/com/swan1127/repland/domain/model/MemoryModel.kt`：自适应复习模型。
- `android/app/src/main/java/com/swan1127/repland/ui/tools/Tools.kt`：真实手写录入及复习反馈界面。
- `android/app/src/main/java/com/swan1127/repland/ReplandApplication.kt`：生产依赖注入、实际执行日志提供者及全量清理回调。

## 设计方向（继续沿用，不重新推倒）

- 以信息清晰、轻量陪伴感为主，不做高饱和仪表盘或大面积装饰遮挡任务。
- 暖纸背景 `#F8F6F1`、奶油表面 `#FFFCF6`、文字 `#263D43`、薄荷主操作 `#DAF2E4`；暗色使用独立语义色，不机械反转。
- 山景只做局部静态装饰；大字体优先可读性和触达，隐藏非必要插画。
- 按压约 90/140ms、页面约 280ms、详情约 340ms，避免持续漂浮/闪烁；遵守系统减少动画。
- 不用假进度、随机类别色、未发生的专注或硬编码“验收通过”充当功能。

## 下一位开发者优先完成

1. 先检查 PR 的实际 CI 与本文件末尾的验证记录，修正编译/测试问题，不根据旧独立版报告推断通过。
2. 在独立测试安装/模拟器验证主应用全流程：建任务→配置可用时间→团队生成→查看变化→取消/确认→编辑草案→重启恢复。
3. 验证执行会话开始、暂停、恢复、结束及冷启动恢复；确认只排序不改时段、只安排今天不改变其他日期，人工锁定与执行中任务不受团队影响。
4. 验证记忆默认关闭；真实文本录入、重复输入处理、三档反馈、快速重复点击、重启持久化；关闭参考仍保留记录；参考不得改用户时长、优先级或任务状态。
5. 验证原 JSON 字段仍完整，并追加 `learningMemory`；全量清理后任务/复习/团队内存均清除，失败时不得误报成功。不要从过期 UI 状态自动重新播种复习记录。
6. 验证生成期间任务/时间/复习变化、旧草案、人工编辑与生成竞争时，不覆盖新草案，不绕过 `sourceRevision`、CAS 或仓库事务；编辑后不展示旧的团队“通过”结论。
7. 完成主应用截图与动效/可访问性验收：深色、字体 200%、软键盘、返回导航、48dp 触达、减少动画；补充复习入口及团队面板的仪器化 UI 测试。
8. 最后运行 internal 构建与 lint，并查看现有 API 26 CI 回归。通过后再将 PR 标记为 ready；本次没有构建正式签名 release。

推荐沿用项目现有 JDK 17 和 Gradle wrapper，在 `android/` 执行：

```sh
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:verifyOfflineMvpBoundary
./gradlew --no-daemon :app:assembleInternal :app:lintDebug :app:lintInternal
./gradlew --no-daemon :app:assembleDebugAndroidTest :app:connectedDebugAndroidTest
```

仪器化测试可能清理测试应用的数据，务必使用独立模拟器；不要直接对使用者已有安装执行清空。

## 验证记录

- 未修改上游基线：debug 构建、220 条 JVM 测试（39 个套件，0 失败/错误/跳过）、离线边界及 lint 均通过；基线 lint 有 89 条 warning。
- 当前融合代码：`:app:assembleDebug`、`:app:testDebugUnitTest`、`:app:verifyOfflineMvpBoundary` 通过；49 个 JVM 套件，共 315 条测试，0 失败/错误/跳过。
- 最小编译预检中发现的插画参数与 `Alignment` 导入问题已修正。完整仪器化验证、主应用运行截图、internal 构建、本次融合代码的 lint 与发行验收尚未完成；基线 lint 不代表融合后 lint 已通过。
- 原桌面的独立改造版快捷方式保持不变；本主应用融合分支尚未替换桌面安装或生成新的主应用快捷方式。
