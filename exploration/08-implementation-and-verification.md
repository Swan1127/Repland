# 2026-09-25 实现切片、验证与剩余工作

## 已落地并验证

- Android 首页从单列计划卡扩展为课程、休息、固定事项与已确认任务的同日多轨投影。重叠项分轨，日程卡展开显示未开始、计划进行中和计划超时的时间读数；计划超时不等于真实执行超时。
- 视觉改为暖纸白/墨绿主轴、课程蓝/休息灰紫辅助轨，完善了空态的示意轨与四个自绘底栏图标。原生 Compose 以 220ms 的局部展开动效表达层级，不使用 Web 式玻璃与循环“呼吸”动画。已在 Android 36.1 模拟器截图复核。
- 三种参与模式存在 Room v15，默认中度；轻度首页先显示最近三项并可展开。模式变更和打开日程以最小化事件追加记录，随本地 JSON 一起导出。它们**还不是**完整的个性化策略与执行会话证据。
- 首页提供可编辑的语音/文字入口。用户可自行调用系统语音识别，转写后审阅，再预填任务草稿；语音服务可能联网，未自动保存任务、原始音频或修改课表。
- Android debug 适配器通过 `10.0.2.2:8787` 调用宿主机 Agnes 代理；代理默认 `agnes-3.0-flash`，凭据仅从 `REPLAND_AGNES_API_KEY` 环境变量读取。已有 AI 同意、请求预览和响应本地校验仍生效；正式/internal 构建无此适配器。
- `:app:testDebugUnitTest`、`:app:verifyOfflineMvpBoundary`、`:app:assembleDebug` 和 `:app:assembleInternal` 成功；Android 36.1 模拟器 **20/20** 仪器测试通过，其中包含双轨重叠与任务入口测试；Node 代理契约测试通过。internal 合并清单不含 `INTERNET` 权限。旧 Espresso 3.5.1 与 Android 16 不兼容，已升级到官方 3.7.0；本机项目 Gradle 代理端口失效，测试命令用一次性 JVM 参数绕过，未修改用户网络配置。[AndroidX Test 发布说明](https://developer.android.com/jetpack/androidx/releases/test)。
- 亮色空态最终模拟器截图：[android-home-2026-09-25.png](artifacts/android-home-2026-09-25.png)。它只证明空态排版，不能代表有课表、有并行项时的视觉验收。

## Agnes 本机配置

1. 先在 Agnes 控制台轮换曾贴进对话的密钥。官方 [API 概览](https://agnes-ai.com/en/docs/overview)提醒不要把密钥暴露在前端或公开配置；[定价页](https://agnes-ai.com/en/docs/pricing)本次显示 `agnes-3.0-flash` 输入/输出暂为免费促销，以实际账号账单为准。
2. 在 PowerShell 中用安全输入把新密钥仅传给当前进程及其启动的 Node 子进程，不写入仓库或命令历史：

   ```powershell
   $replandSecret = Read-Host '新的 Agnes API Key' -AsSecureString
   $replandPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($replandSecret)
   try {
       $env:REPLAND_AGNES_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($replandPointer)
       node tools/agnes-dev-gateway.mjs
   } finally {
       [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($replandPointer)
       Remove-Item Env:REPLAND_AGNES_API_KEY -ErrorAction SilentlyContinue
   }
   ```

3. 保持代理窗口运行，在模拟器 debug 版“我的”显式启用并同意 AI；任务详情先预览请求再确认。代理只监听宿主机 `127.0.0.1:8787`，模拟器通过 `10.0.2.2` 访问。`GET http://127.0.0.1:8787/health` 只回报是否配置和模型名，不回报密钥。**本轮未获得轮换后的密钥，因此没有执行带凭据的 Agnes 在线请求；不能把契约测试当作在线成功。**

## 尚未达到目标方案的能力

精确执行开始/暂停/结束与时钟更正、课程地点/教师和伴随活动的完整资源模型、以足够样本浓缩并反哺估时的画像、真实回忆测验驱动的复习建议、自由对话的结构化意图与课表更改、授权委托的 Agent/Workflow、生产网关/鉴权限流、折叠屏布局与系统减少动画下的专项复核。这些不因当前切片通过测试而被视为完成；下一步应先补执行会话与画像证据，再把对话意图接到同一任务/课表事务，最后扩展可委托策略。

## 对抗性审查

- 若只记录“打开日程”就推断用户执行，画像必然失真；当前代码禁止这种推断，执行会话仍需实现。
- 若把轻度模式只做成“少显示两张卡”，维护成本并未真正下降；需要用一周真实任务测试减少配置步骤与重复确认次数。
- 当前多轨是显示层，规划器仍以互斥可用槽为主；不能据此声称已支持用户授权的高/低注意力伴随安排。
- 系统语音服务可能上传音频；不能把“本地应用未存音频”等同“完全离线识别”。
- `agnes-3.0-flash` 免费与可用性可能变化；生产环境需要成本上限、鉴权和故障隔离。调试代理没有这些生产能力。
- 深色、放大字体、TalkBack、减少动画和真实低端设备帧率尚未实测；比赛展示前不能仅凭模拟器亮色截图判定完成。
