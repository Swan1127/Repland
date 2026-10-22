# 融合版实际运行验证

日期：2026-10-22。设备为现有 `Repland_API_36` / `emulator-5554`，只操作隔离包 `com.swan1127.repland.redesign`，没有卸载、`pm clear`、重置模拟器或修改其他安装包的数据。

最终安装包 SHA-256：`E118D22F99A6968625B97DBADE8675E53F557C8513392FBFEBF1A2DA8886EC24`。最终运行后通过 `pm path` 读取设备实际安装的 APK 并计算哈希，与构建产物一致，见 `integration-final-runtime.json`。

## 1. 本地团队与确认

1. 通过原有任务界面创建 `QA Integration Memory`，明确保存为高优先级、30 分钟、未开始；不是直接写入数据库的夹具。
2. 初次缺少时长时，草稿明确显示缺少估时，没有用默认数字冒充用户填写。展开记录看到实际 6 角色 / 17 次工具调用。
3. 在安排编辑界面将 `QA Available Window` 明确改为周四“可用”08:00–14:00。测试准备中曾误建为“课程”，随后通过界面修改，并以只读数据库回读确认，不把标题含 Available 当作真实可用类型。
4. 团队生成 1 个分段、0 个待安排，界面显示“尚未写入”；未确认和丢弃时没有写入计划。
5. 点击“采纳草稿”，今日实际出现 11:30–12:00 的 `QA Integration Memory`。数据库回读为当前日期、startMinute 690 / endMinute 720，位于真实可用窗口内。
6. 已有计划之后再次生成并丢弃，计划 / 分段数量不变。最终 E118 包也再次实际确认成功；两个历史计划各有 1 个分段，没有修改任务的高优先级、30 分钟或 `NOT_STARTED` 状态。

全过程 `ai_settings` 数量 0 / `ai_enabled=false`，未配置密钥。本地成功不能推论云端群聊或六个云模型联调通过。

证据：
- `integration-team-six-role-log.png`：7262 中间构建的真实展开工作记录，非最终 E118 截图。
- `integration-final-team-draft.png`、`integration-final-plan-accepted.png`：最终 E118 的生成与实际采纳。
- `release-store-integration-accepted.json`、`release-store-integration-cancel-after-accepted.json`、`release-store-integration-final-accepted.json`：只读回读摘要。

## 2. 记忆参考与三档复习

- 首次设置界面“记忆参考”为关闭；启用后通过强制停止 / 重开实际保留。
- 在 KWL 表的“我学到了”输入测试文本并保存，立即显示本地复习点从 0 变 1；无需切换开关来触发刷新。设备中文输入法会转换部分符号，证据保留实际保存的文本，不替换为事后编造的内容。
- 该点继承真实高优先级的重要程度 4 / 5，捕获日 10 月 22 日、初始下次复习 10 月 23 日；显示到期 0 / 后续 1，未到期也可主动回忆。
- 实际依次点击“想起来了”“再次练习 → 有点模糊”“再次练习 → 忘了”。三档分别写入 reviews 1 / 2 / 3；遗忘后 lapses 1、difficulty 6.4046875、gain 0.85。
- 第一次评分连续快速点击相同位置，实际计数仍为 1，并显示“已记录本次回忆反馈”；显式“再次练习”后才再给分。
- 同日练习稳定度保持 1 天，下一日期仍为 10 月 23 日，没有为了演示效果伪造间隔增长。不同日期的增长与旧数据兼容由 JVM 用例覆盖，未修改设备日期来冒充长周期实测。
- 强制停止 / 重开后，开关、点内容、三次复习、一次遗忘、难度与 gain 和重开前完全一致。
- 关闭参考后，仍保留这 1 个复习点和 gain 0.85；关闭不是删除。

证据：`integration-review-three-grades.png`、`integration-review-recorded.png`、`integration-final-memory-settings.png`，以及 `integration-memory-before-grade.json`、`recalled.json`、`fuzzy.json`、`forgot.json`、`restarted.json`、`disabled.json`（后五个文件均有 `integration-memory-` 前缀）。

## 3. 真实 JSON 导出

从“我的 → 本地数据 → 导出为 JSON”实际打开系统文件选择器，保存到 Downloads，界面显示“已导出”；不是直接调用导出类伪装 UI 成功。

实际读取导出文件并验证：`format=repland-local-export/v1`；`learningMemory.version=1`；referenceEnabled=true、gain=0.85；1 个点的 memory 含 reviews=3、lapses=1、importance=4 和完整状态。导出未含 API Key。文件归档为 `integration-export-actual.json`，选择器与成功反馈见 `integration-export-picker.png` / `integration-export-succeeded.png`。

没有测试未实现的导入还原；XLSX 仍不是完整记忆备份。下载目录原有 10 月 11 日备份未改动，本轮创建的 10 月 22 日 QA 导出在复制证据后单独移除。

## 4. 显示与启动

- 最终 E118 复习页在 200% 字体下实际滚动到三档按钮，按钮自动换行、日期 / 重要程度分行，无横向溢出；见 `integration-review-font200-top.png` / `integration-review-font200-actions.png`。
- 最终 E118 深色复习页实际运行，见 `integration-review-dark.png`。截图不是动画性能基准。
- 用桌面现有“Repland 新版 2026-10-22”快捷方式实际启动，Windows PowerShell 5.1 记录安装 Success 并打开 MainActivity，最终清洁首页见 `integration-final-clean-today.png`；日志追加在 `desktop-launch.log`。
- 字体 1.0、亮色、原始旋转、1080×2400 / density 420、三项动画 1.0 均恢复，与原备份逐项相等；测试时临时关闭的手写输入设置恢复原 null。系统时区 / 日期未修改，模拟器时钟为 UTC，安排时间按设备本地时钟解释，不与桌面北京时间混算。

## 5. 测试清理与边界

清理前核实所有任务均以 QA 开头。通过应用“清空本地数据 → 确认清空”成功清理：所有业务数据库表为 0；学习记忆点为空、引用关闭、gain 回到默认 1.0；没有手写 SQL 删除来伪造清理结果。

当前旧 KWL 写作不在新增的 `clearLearningMemory()` 清理范围内。为不遗留本轮孤立 QA 写作，在确认 `repland-aux.xml` 清理后只剩本轮唯一 QA 任务的 `kwl` 键、且本轮开始前该文件不存在后，停止隔离应用并单独移除该测试偏好文件。这是测试收尾，不声称应用自动清除了全部旧工具写作，也没有借此修改生产数据。随后实际桌面快捷方式重开，新增记忆配置为空 map、无 QA 辅助偏好文件。

见 `integration-clear-confirm.png`、`integration-clear-success.png`、`integration-memory-cleared.json`、`release-store-integration-final-clear.json`、`integration-cleanup-audit.json`、`integration-final-runtime.json`。

本轮未配置真实云密钥、未做真人跨日长期记忆训练、未覆盖所有机型，也没有把前端阶段旧录屏当成新增功能录屏。完整自动化结果及既有通知权限 lint Error 见 `INTEGRATION_QA_2026-10-22.md`。
