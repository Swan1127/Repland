# 前端改造运行验证 · 2026-10-18

本轮基于 Swan1127/Repland 的最新 main 提交 `32cac605e8cd61ee7dd608dc4d1f5fe707a4aa9a`，实现于独立分支 `codex/frontend-refresh-2026-10-18`。参考桌面 2026-10-17 ZIP 的提示词、九张截图和两段实际录屏；功能、数据和自动处理规则以最新主分支为准。

## 打开与查看

- [打开前端新版](../../../Open-Frontend.cmd)：独立包 `com.swan1127.repland.frontend`，显示名“Repland 前端新版”。
- [安装包](../../../android/app/build/outputs/apk/frontendPreview/app-frontendPreview.apk)。
- [截图与录屏查看页](index.html)。[主应用操作录屏](main-app.mp4) 51.8 秒；[只读日程演示](schedule-demo.mp4) 36.37 秒，两段均通过完整解码检查。
- [设计、页面与业务对应](../../../FRONTEND_REFRESH.md)。[构建及文件校验](build-manifest.json)。

## 本轮修改

保留日程、任务、我的三项导航；日程悬浮“一句话处理”、自动使用总结及设置拆分继续采用最新行为。为新增助手标题、输入卡片、真实处理结果、自动总结与设置入口统一浅蓝、薄荷和浅紫层次。悬浮按钮增加与参考一致的按压反馈，设置往返增加定向过渡；原生日程总计/详细、个人资料、状态和数据流程保留。

实际横屏检查发现用户名弹窗按钮被软键盘遮挡。本轮修复键盘避让，横屏采用输入与保存/取消并排的紧凑布局；常规手机保留参考的居中弹窗。输入框和操作按钮均在实际 IME 上方，并真实点击验证了保存、取消，而非仅依靠 UI 节点存在。

## 本次验证结果

| 检查 | 本轮结果 |
| --- | --- |
| 最新源码构建 | Debug、AndroidTest、FrontendPreview 均成功 |
| 交付脚本 | 实际执行 `Build-Frontend.ps1` 成功，`Start-Frontend.ps1` 安装与启动成功 |
| JVM | 最终 FrontendPreview 218 项，39 套件，0 失败 / 错误；Debug 218 项也通过 |
| 设备回归 | 147 个不同的针对性用例全部通过；键盘修复后另补跑资料与设置 9 项全部通过，不重复计入 147 项 |
| 静态检查 | `lintDebug` 0 错误、79 警告；离线边界检查通过 |
| 颜色 | 31 组浅色 / 深色正文、次文字、图标与按钮色对，最低对比度 5.09:1 |
| 实际屏幕 | 约 375dp 窄屏、横屏、800dp 平板、200% 字体与深色模式；共用一个模拟器 |
| 软键盘 | 横屏、窄屏及 200% 深色字体下，实际窗口 IME 边界与控件坐标校验、原生点击保存 / 取消均通过 |

设备回归覆盖助手防重复提交、失败保留输入、真实任务新增/进度/撤销、总计与详细、重叠区间与原始事项、时间与计划保护、资料存储与校验、设置返回、自动总结的真实统计、授权/缓存/退避/过期响应及读取失败恢复。详见 [147 项回归结果](device-tests.json)、[选择的用例](selected-device-cases.json)、[原始日志](device-regression.log)、[最终 9 项补跑](keyboard-fix-device-tests.json) 和 [补跑日志](keyboard-fix-device-regression.log)。未将上游旧版测试数量作为本轮结果，也未声称执行了全部历史设备套件。

## 实际操作与图片

在独立新版通过 UI 输入 `Read chapter 30min`，真实本地保存为 30 分钟事项；未设置可用时间时如实显示待安排，未虚构日程。录屏展示任务详情、处理回执与整次撤销，结束后测试事项已撤销。个人资料验证了空用户名错误、保存与重启；系统照片选择器选入自建测试图片后，私有头像在源文件移除和 APK 覆盖安装后仍保留，恢复默认头像成功。原版及参考副本的数据没有复制或修改。

- 修改前：来自 `32cac60` 实际构建的 [助手 / 我的 / 设置总览](before-overview.jpg)。
- 修改后：[助手 / 我的 / 设置总览](after-overview.jpg)、[真实处理回执](actual-assistant-result.png)、[真实保存的任务](actual-task-saved.png)。
- 日程：[总计](after-summary-demo.png)、[详细](after-details-demo.png)。两者使用明确标注的只读固定示例，不代表用户计划。
- 资料：[用户名校验](actual-username-validation.png)、[重启后用户名](actual-profile-restarted.png)、[系统选图后](actual-avatar-selected.png)、[升级后头像](actual-avatar-after-upgrade.png)、[恢复默认](actual-avatar-reset.png)。
- 最终键盘修复：[横屏](landscape-username-keyboard-final.png)、[窄屏](narrow-username-keyboard-final.png)、[200% 深色](large-dark-username-keyboard-final.png)、[IME 边界与操作记录](keyboard-verification.json)。同目录不带 final 的图片保留中途检查状态，不作为修复后证据。
- [平板](tablet-profile.png)、[200% 深色主页](large-dark-profile.png)、[色对测量](contrast.json)、[录屏事件时间点](main-app-events.json)、[日程演示时间点](schedule-demo-events.json)。

## 边界与环境

本轮没有修改领域服务、Room 数据模型、自动规划规则、AI 请求或使用总结统计口径。真实付费模型请求未运行；网络适配与授权边界通过既有替身及本地事务回归验证。未执行完整 TalkBack 人工遍历或实体手机验收。

系统动画、字体倍率、屏幕尺寸、密度和深色设置恢复至任务开始值。复用现有 SDK、JDK、Gradle 缓存及一个模拟器，无新增前端依赖。测试图片和录屏远端临时文件已移除；保留模拟器显示新版供查看。
