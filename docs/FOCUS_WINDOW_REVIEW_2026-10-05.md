# 专注横屏窗口复核计划

状态：QA-056 复核准备；未修改产品、未开始 0.1.26。当前候选仍为已保留的 0.1.25/b7b6b90，两次深色 2 倍字号横屏截图均出现相同裁切。没有七天试用或用户研究。

1. 在隔离 QA 同 APK 的专注故障/重建点，先通过原生 AccessibilityWindowInfo 确认聚焦窗口与本轮标题，再记录窗口 boundsInScreen、实际屏幕旋转/尺寸、Compose 面板及重试/返回按钮边界。现 NativeSheetTestInput 只校验聚焦和标题，不能证明窗口尺寸正确。
2. 字体/主题/旋转提前设置后冷启动、打开后旋转、读取失败后 Activity 重建三条路径分别复核。比较实际窗口与截图，不预先归因 GPU、框架或帧时序，不以固定 sleep 替代绘制/几何判断。
3. 检查 ExecutionSessionDialog 的 DialogProperties(usePlatformDefaultWidth=false)、fillMaxSize 与 safeDrawingPadding 在不同窗口上的结果；MainActivity 的 enableEdgeToEdge 不能单独作为 Dialog 正确性的证据。只在定位后按原生 Compose/Material 3 和现有 ui-ux-pro-max/emil-design-eng/apple-design 原则修正窗口、安全区或滚动，不重设计业务功能。
4. 验收须断言重试和返回按钮完整在实际视口内，文字不横向丢失；长标题、1/2 倍字号、横竖屏、深浅色、重建及 IME 分别检查。全部保留原会话/输入、错误禁写、主动重试和提交断言，不改产品课程/锁定/30 天范围。

随后继续 QA-055 真实分段保存回执和失败保留、QA-053 剩余数字入口、M2 容量与 M3 AI/M4/M5 技术工作流。真机/真实 PDF/通知缺少证据照实记录，后置竞品报告只提意见，不自动实施。
