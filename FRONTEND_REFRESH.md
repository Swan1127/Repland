# Repland 前端更新 · 2026-10-18

## 来源

基于 `Swan1127/Repland` 的最新 `main` 提交 `32cac605e8cd61ee7dd608dc4d1f5fe707a4aa9a`，分支 `codex/frontend-refresh-2026-10-18`，独立工作树 `outputs/Repland-Frontend`。原项目未提交文件保留。原仓库抓取规则仅包含旧功能分支，现已追加 main 并显式抓取，避免沿用过期的远端跟踪缓存。

桌面 `Repland-frontend-reference-2026-10-17.zip` 已解压；读取提示词、九张截图和两段录屏（包含时间点与抽帧检查）。参考材料作为视觉依据，业务与入口以最新主分支为准。最新主分支已经合入参考版设计；本轮将新增自主处理、自动总结和设置导航接续到这套视觉及动效中。

## 设计方案

沿用背景 `#F7F8FC`、卡片 `#FFFFFF`、主文字 `#242831`、次文字 `#606572`。按用户最新反馈收敛配色：日程浅蓝、任务薄荷；我的与设置统一青绿文字 `#326B75` 及淡青、雾蓝、浅鼠尾草色。资料短卡片使用接近白色的轻微色晕，设置只在分组标题使用同色系渐变，内容主体保持中性白底；不使用随机任务彩色卡片或彩色总结分段。主按钮与悬浮入口沿用淡薄荷底 `#DAF2E4` 和清晰文字 `#39634D`，次要操作使用中性色描边。语义警告保留文字提示，深色模式保留对应可读表面。使用系统中文无衬线、现有字号，20dp 侧边距、24dp 主卡片圆角。仅使用现有矢量图标与设计令牌，不新增依赖。

```
日程                   我的
日期 / 新增日程        头像 / 用户名 / 编辑
日 / 周 / 月           自动使用总结 / 查看依据
总计 / 详细            设置入口
时间侧栏 | 实际事项       → 时间 / 自动安排 / 模型 / 数据
         一句话处理
日程       任务       我的
```

助手输入置于白色圆角卡片，真实处理结果使用统一薄荷图标；我的资料、总结、导航选中态和设置入口采用同一青绿强调色。总结正文、状态、时间和依据按钮通过留白、字号和细分隔线建立层次，避免多色大面积拼接。设置分为日常使用、智能与个性化、数据与隐私三组，统一图标列、说明文字、开关与导航箭头，版本号置底。按压 90ms、切页 280ms、展开 260ms / 收起 180ms；底部页面与任务筛选按固定顺序短距离定向过渡，快速切换保持当前动画进度，系统关闭动画时直接显示最终内容。

设计复核：保留最新产品要求的三项导航、日程悬浮助手、自动总结与设置拆分；参考版四页导航和手动画像流程不作为本轮业务要求。

## 页面与业务对应

| 页面 / 操作 | 最新模型与本地能力 | 状态与边界 |
| --- | --- | --- |
| 总计 / 详细、日周月 | `PlanViewModel`、`TimeViewModel`、`PriorityScheduleProjection` | 同一真实数据的只读投影；切换不写入计划、优先级或轨道 |
| 新增与执行任务 | `TaskCaptureViewModel`、`TaskViewModel`、Room Repository | 保留草稿、真实校验、保存回执和反馈；时间已过不自动完成 |
| 一句话处理 | `AutonomyViewModel` → 意图与事实解析 → `AutonomyRepository` 事务 | 新增、查询、明确进度、安排和整次撤销；失败留输入、处理时防重复与关闭 |
| 自动使用总结 | `UsageSummaryViewModel`、`UsageSummaryRepository` | 最近 30 天真实事实，自动本地整理；既有授权与模型有效时才远程解释，缓存、退避与旧响应保护沿用主分支 |
| 我的 / 设置 | 既有个人资料与设置状态 | 个人资料、总结、依据与设置入口；时间、课表、历史、模型、提醒与数据集中到设置，返回保留位置 |
| 头像与用户名 | `LocalPersonalProfileStore` | 本地保存、系统选图、384px 私有头像；失败保留旧值、Unicode 校验，不声称云同步 |

不改变领域服务、数据库 schema、自动规划保护规则、AI 授权、请求内容或统计口径。默认独立安装包 `com.swan1127.repland.frontend`，显示名“Repland 前端新版”，不覆盖当前版与参考副本。

## 使用与验证

双击桌面 **Repland 前端新版** 快捷方式或 `Open-Frontend.cmd` 打开；`Create-Desktop-Shortcut.ps1` 可重建快捷方式。启动器安装同一最新 APK 并还原模拟器窗口；`Open-Frontend-Schedule.cmd` 打开明确标注的只读演示。`Build-Frontend.ps1` 构建与运行单元测试，`-DeviceTests` 额外使用隔离 QA 包执行完整设备回归。SDK、JDK、Gradle 缓存和单个模拟器均复用现有环境。

在其他 Windows 机器上，先安装 JDK 17、Android SDK（platform-tools、emulator、Android 36 和对应 system image），设置 `JAVA_HOME`、`ANDROID_HOME` 并在 Android Studio 创建 AVD。默认名为 `Repland_API_36`；已有其他 AVD 可运行 `./Start-Frontend.ps1 -AvdName <名称>`。相邻的 `Environment/Activate-Repland.ps1` 仅为本机可选环境，不包含在仓库中；APK 为本地构建产物，首次打开时会构建。

最新设置排版实测、前后截图、录屏和构建标识见 [设置排版与配色验证](docs/verification/settings-harmony-2026-10-18/README.md)。此前记录保留在 [配色与动效验证](docs/verification/color-motion-2026-10-18/README.md) 与 [前端改造验证](docs/verification/frontend-refresh-2026-10-18/README.md)。
