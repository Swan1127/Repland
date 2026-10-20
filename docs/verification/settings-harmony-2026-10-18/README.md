# 我的与设置排版 · 2026-10-18

接续已提交的前端版本 `0343c1e050e2d750d9782a64409ce9d0232bbefd`，本轮按用户反馈统一“我的”与设置的色系，并重新整理设置页。底层功能和数据规则保持不变。

[查看截图与实际操作录屏](index.html) · [构建与验证标识](build-manifest.json)

## 设计与页面

沿用背景 `#F7F8FC`、白色卡片、清晰的正文 `#242831` 和次文字 `#606572`。我的与设置使用同一青绿强调色 `#326B75`，相近的淡青、雾蓝和浅鼠尾草色分别从已有 `#E8F3F5`、`#EAF2FF`、`#EAF7EF` 颜色族生成。

“我的”头像、编辑操作、总结图标、设置入口和底部选中态统一色系；短资料卡片只有接近白色的轻微色晕。设置分为日常使用、智能与个性化、数据与隐私三组，24dp 外卡片、统一图标列与入口行，开关和导航箭头保持对齐。仅分组标题使用轻微的同色系水平渐变，内容区域保持中性白色，版本号置于底部。系统中文无衬线和原有字号保留，文字允许换行，控件高度随内容增长。

- 新版：[我的](after-profile.png)、[设置顶部](after-settings-top.png)、[模型与智能设置](after-settings-middle.png)、[设置底部](after-settings-bottom.png)。
- 操作展开：[类别偏好](after-preferences-expanded.png)、[本地数据](after-data-expanded.png)、[模型服务弹窗](after-provider-dialog.png)、[时间设置入口](after-time-entry.png)。
- 对照：[此前我的](before-profile.png)、[此前设置顶部](before-settings-top.png)、[此前设置中部](before-settings-middle.png)、[此前设置底部](before-settings-bottom.png)。
- 大字体：[200% 深色设置顶部](large-dark-settings-top.png)、[智能设置](large-dark-settings-middle.png)、[设置底部](large-dark-settings-bottom.png)、[模型弹窗](large-dark-provider-dialog.png)、[我的](large-dark-profile.png)。
- [实际录屏](settings-harmony.mp4)、[操作时间点](settings-harmony-events.json)、[完整解码检查](video-verification.json)。录屏展示我的→设置→时间设置→返回、模型配置查看、类别偏好展开与收起。设置原有的滚动位置恢复行为保留。

## 本轮实测

| 检查 | 本轮结果 |
| --- | --- |
| 构建 | FrontendPreview、Debug、AndroidTest 成功；[构建日志](build.log) |
| JVM | 218 项，39 套件，0 失败 / 错误；[结果](unit-tests.json) |
| 设备回归 | 21 个不同的针对性用例全部通过，57.1 秒；[结果](device-tests.json)、[用例](selected-device-cases.json)、[日志](device-regression.log) |
| 静态检查 | Lint 0 错误、79 警告；离线边界检查通过；[摘要](lint.json) |
| 色彩 | 24 个浅色/深色源颜色锚点组合通过 4.5:1，浅色最低 5.155713:1、深色最低 5.981492:1；[方法与范围](contrast.json) |
| 实际运行 | 新版页面、时间/模型入口、偏好和数据展开、大字体与深色检查通过；[显示设置恢复记录](display-settings.json) |
| 桌面入口 | 既有“Repland 前端新版”快捷方式继续指向最新 APK；[启动与包哈希核对](desktop-launch.json) |

21 项设备回归覆盖个人资料 5 项、自动总结界面 5 项、设置导航 4 项、模型配置弹窗 4 项、类别偏好读写失败及重建保护 1 项、数据清除确认流程 1 项、展开动画中断 1 项。数据清除回归仅作用于隔离 QA 包 `com.swan1127.repland.qa`；新版包为 `com.swan1127.repland.frontend`。未将此前 147 项计入本轮执行数量。

模型服务在实际前端中仅打开查看，没有保存密钥或调用连接测试；偏好和本地数据也仅展开查看，没有通过界面改写用户数据。显示设置恢复为操作前值。复用单个 API 36 模拟器，没有新增依赖；原有头像、用户名键盘避让、设置返回、授权和保存规则均保留。

色对结果为静态源颜色锚点检查，不把简单 sRGB 混色当作 Compose 的颜色插值，也不声称测量了所有渐变像素或交互状态。实际画面另外经过截图复核。
