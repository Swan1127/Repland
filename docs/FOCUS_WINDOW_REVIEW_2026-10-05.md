# 专注横屏窗口复核计划

状态：0.1.26/cdae808 已完成同源验证、原位安装及版本/样例核对。QA-056 在本机原生窗口测量、旋转/重建、输入/重试/提交、IME/返回范围关闭，不代表真机/平板/完整无障碍矩阵通过。没有七天试用或用户研究。下面保留原复核计划。

## 定位与改动

原 0.1.25/b7b6b90 APK 保持不变，以新测试 runner a1cfde6 测量：屏幕 2400×1080，聚焦窗口 `Rect(128,63–2274,1080)`（2146×1017），Compose 内容 2399×1079。原生窗口没有越出屏幕，但内容比窗口大，故原“窗口在屏幕内”断言不足。新增内容尺寸断言在原 APK 明确失败（1 项，1.805 秒）。本地依赖为 Compose 1.7.0，其非默认宽度路径绕过原生 measureSpec，采用 configuration 屏幕尺寸；本轮只修正本组件测量，不升级依赖或全局主题。

| Before | After | Why |
| --- | --- | --- |
| 非默认宽度路径将屏幕尺寸用作浮动窗口内容尺寸 | 原生 MATCH_PARENT 窗口配合默认测量路径，接受窗口实际约束 | 保持大面积专注面板，同时避免横向与高度越界 |
| 仅 safeDrawingPadding | safeDrawingPadding + imePadding | 输入与滚动避让系统栏和软键盘；不改变计时/提交规则 |
| 大字号错误提示与重试横排，说明被挤成窄列 | 字号大于 1.3 时上下排列，重试保留 48dp 最小高度 | 说明更易读，恢复操作保持原生按钮语义 |

最终 cdae808：真实横屏仍为同一原生窗口 2146×1017，内容现在恰为 2146×1017。长标题自然换行，字段完整至右边界；重试、保存、返回分别滚动后断言完整位于窗口内。截图 `reports/ui/repland26-final-dark-landscape-open.png`、`repland26-final-dark-landscape-recreated.png` 已查看。滚动到部分输入时顶部提示可滚出视口，这不再误报为窗口测量裁切；不能用任意滚动位置截图声称全部文字同时可见。

保留失败历史：a174c46 的原生窗口-only 检查通过（4.25 秒），仍见截图裁切；0cb6f94 单独 decorFitsSystemWindows=false 和 4341c9e LocalContext 主题尝试均为 6/8（10.175/10.089 秒），未通过几何检查，后者已撤除。Compose 1.7 Dialog 使用 LocalView.context，不能假定 LocalContext 覆盖改变了窗口。b4b3669 默认测量 8/8（14.067 秒）；a72a181 测试错误导入 onNode 导致编译失败（55 秒），9689516 修正。9689516 是中间窄窗口版本，最终 cdae808 增加原生窗口 MATCH_PARENT 并重新全部验收，不沿用中间包结果。

## 同源验收与版本保留

- 来源 `cdae808fd082a4f6178a2d7460e7121726baf249`，版本 0.1.26/1026。
- 123 单元与 249 设备全套：失败/错误/跳过均 0；设备 XML `reports/repland-026-final-full.xml`，121.563 秒。internal、release Kotlin/Manifest、离线边界与清单成功，整体 3 分 40 秒；未构建正式签名 release APK。
- 专项 12/12（15.968 秒）：读取禁写、原始数字、旋转/重建、真实键盘两次返回、不自动结束。最终实际深色 2 倍字号横屏单项 1/1（4.402 秒），五项实际 Activity 同 APK 5/5（12.688 秒）。横屏冷启动、打开后旋转、故障后重建分别有证据；强制横屏复测中不把“旋转至已为横屏”视为独立旋转证据，旋转证据来自正常专项。
- 中间包专项启动前模拟器退出，adb offline；同一 Pixel_6 无快照软件渲染重启，保留 userdata，不归因 GPU 或应用。首次重启缺测试 runner/目标，原位重装 QA/test 后继续；中断不算通过。两次 wm lock 后截图实际为竖屏（4.375/4.565 秒），窗口日志显示系统恢复 rotation0；使用 fixed-to-user-rotation enabled 后实测 2400×1080，结束恢复 default/free/自动旋转、字体 1.0 与浅色。
- 此次恢复时设备实际为 0.1.18，不沿用历史“已安装25”的表述。当前包 install -r 升级 1026，版本页 `0.1.26-internal (1026) · cdae808fd082`，升级前后 QA-uncommitted17 / QA-API-English-review / QA-draft17-only 和各 45 分钟一致。无当前包业务写入、密钥读取或真实 API 请求；旧包/QA 停止，仅当前包运行。隔离 QA 全套前仅清除自己的可再生样例，不清除当前包。
- APK 与清单本地独立保留 `reports/retained/0.1.26`，源码/文档以 v0.1.26 保留，不移动旧标签。

| 文件 | SHA-256 |
| --- | --- |
| app-internal.apk | `3ffb6a4106663340e458b4b65ba45ce62bff19f17d65d97a5bfada1153fb1218` |
| app-debug.apk | `dfa3be5f792717df8d1f45aa807a5849f48f034ea5b2683bcc61c14a1d459690` |
| app-debug-androidTest.apk | `63d43660c8518770d3efe96abc2b1ad71c2216c90f8691d1522d6b3c675cbb8d` |

技能：ui-ux-pro-max 指导大字号重排、安全区与原生控件，insets Stack 两次无匹配，采用原生实测与一般原则；emil-design-eng 保留前后/原因记录、不新增装饰动画；apple-design 保留可预测的原生键盘/返回，不移植 web/iOS 弹簧。官方接口与项目 1.7 源码分别核对，不能将最新接口实现当旧版本事实：[DialogProperties](https://developer.android.com/reference/kotlin/androidx/compose/ui/window/DialogProperties)。目前只有 Pixel_6 模拟器证据；小屏/平板/真机/TalkBack 完整矩阵未验收。

## 原复核计划

1. 在隔离 QA 同 APK 的专注故障/重建点，先通过原生 AccessibilityWindowInfo 确认聚焦窗口与本轮标题，再记录窗口 boundsInScreen、实际屏幕旋转/尺寸、Compose 面板及重试/返回按钮边界。现 NativeSheetTestInput 只校验聚焦和标题，不能证明窗口尺寸正确。
2. 字体/主题/旋转提前设置后冷启动、打开后旋转、读取失败后 Activity 重建三条路径分别复核。比较实际窗口与截图，不预先归因 GPU、框架或帧时序，不以固定 sleep 替代绘制/几何判断。
3. 检查 ExecutionSessionDialog 的 DialogProperties(usePlatformDefaultWidth=false)、fillMaxSize 与 safeDrawingPadding 在不同窗口上的结果；MainActivity 的 enableEdgeToEdge 不能单独作为 Dialog 正确性的证据。只在定位后按原生 Compose/Material 3 和现有 ui-ux-pro-max/emil-design-eng/apple-design 原则修正窗口、安全区或滚动，不重设计业务功能。
4. 验收须断言重试和返回按钮完整在实际视口内，文字不横向丢失；长标题、1/2 倍字号、横竖屏、深浅色、重建及 IME 分别检查。全部保留原会话/输入、错误禁写、主动重试和提交断言，不改产品课程/锁定/30 天范围。

随后继续 QA-055 真实分段保存回执和失败保留、QA-053 剩余数字入口、M2 容量与 M3 AI/M4/M5 技术工作流。真机/真实 PDF/通知缺少证据照实记录，后置竞品报告只提意见，不自动实施。
