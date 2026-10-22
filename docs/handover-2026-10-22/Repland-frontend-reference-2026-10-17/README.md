# Repland 当前版本前端参考包

采集日期：2026-10-17（Asia/Shanghai）。版本：0.1.35-design（1035），与已交付的桌面“Repland 设计副本”使用相同安装包。

双击 [index.html](index.html) 可在浏览器中查看视频与截图；也可直接打开下列文件。所有媒体均来自本次 Android 模拟器实际运行采集。

## 录屏

| 文件 | 内容 | 时长 |
| --- | --- | --- |
| [01-main-app.mp4](01-main-app.mp4) | 主应用实际操作 | 40.9 秒 |
| [02-schedule-demo.mp4](02-schedule-demo.mp4) | 日程视图只读演示 | 29.8 秒 |

- 主应用：日程首页、任务切页与详情、录入抽屉、我的、用户名编辑、系统照片选择器、助手及返回首页。用户名编辑和选图均取消，保留现有资料。
- 日程演示：总计与详细切换、优先级排序、事项详情、同一时段横向查看、已完成分组折叠与展开。
- 日程演示使用应用已有的只读演示入口，画面明确标注“演示日程 · 不写入你的计划”，示例日期固定为 2026/10/15。主应用日期为 2026/10/17；日期和示例任务差异不代表设计改动。
- 主应用当天没有具体安排，所以首页展示空状态；另用只读演示展示重叠任务的视觉与交互。

## 截图

原始截图为 1080×2400 PNG；[overview.jpg](overview.jpg) 为九张截图总览。

| 文件 | 页面 |
| --- | --- |
| [01-home.png](01-home.png) | 日程首页 |
| [02-tasks.png](02-tasks.png) | 任务列表 |
| [03-assistant.png](03-assistant.png) | 安排助手 |
| [04-profile.png](04-profile.png) | 我的与个人资料 |
| [05-edit-username.png](05-edit-username.png) | 编辑用户名 |
| [06-task-detail.png](06-task-detail.png) | 任务详情 |
| [07-task-capture.png](07-task-capture.png) | 任务录入抽屉 |
| [08-summary-demo.png](08-summary-demo.png) | 总计（只读演示） |
| [09-details-demo.png](09-details-demo.png) | 详细（只读演示） |

## 用于后续改版

将 [frontend-prompt.md](frontend-prompt.md)、这些截图和两段录屏一起交给开发助手，并提供新项目的功能需求与接口资料。视频展示当前交互节奏，截图用于对照颜色、布局与组件细节。

视频为 540×1200 H.264，采用系统原生录屏；系统动画倍率 1.0，浅色主题、字体倍率 1.0。截图与视频未隐藏原有界面内容。采集没有提交用户名、头像或计划修改，也没有执行真实模型请求。

采集设备为现有 Repland_API_36 模拟器，复用单个实例，启动内存参数 1536 MiB。本次未重新运行完整测试套件。视频已通过完整解码检查，操作时间点见两个 events.json，安装包标识及文件校验值见 [capture-manifest.json](capture-manifest.json)。
