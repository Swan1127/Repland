# 探索日志

## 2026-09-25：原生实现与设备验收

- 实现 Room v15 参与模式与最小事件、多轨今日时间线、可编辑语音入口、暖纸白/墨绿视觉及 debug Agnes 本机代理；新增 [实施与验证记录](08-implementation-and-verification.md)。
- Android 36.1 模拟器 20/20 仪器测试和本地单元测试通过，内部包构建成功。在线 Agnes 调用仍待用户轮换密钥后验证，生产网关与完整画像/执行会话仍在后续阶段。
- 用领域建模规范把“计划时间状态不等于实际执行证据”写进 `CONTEXT.md`，并以 [ADR 0002](../docs/adr/0002-debug-ai-gateway-and-execution-evidence.md)记录调试网关边界。

## 2026-09-25：竞品取经与 UI 重设计基线

- 根据模拟器截图和 `ReplandApp.kt` 的当前实现，确认主要问题不是功能缺失，而是首屏解释过多、任务卡过重、创建表单一次性要求过多字段、详情和列表缺少层级。
- 调研 Structured、Todoist、TickTick、Sunsama、Routine、Apple Reminders 及 Android 官方自适应 list-detail 规范，形成 [`09-ui-redesign-competitor-research.md`](09-ui-redesign-competitor-research.md)。
- 新基线采用 Today / Tasks / Plan / Me 四个工作面；引入任务总览台、渐进式创建和小屏列表到详情/大屏双栏的自适应关系。
- 本轮只产出重设计报告，不直接修改代码；现有业务数据、Room 和测试作为实现基座，待方案确认后按 Phase 1 → Phase 2 开始重做。

## 2026-09-25：补充组件级对标

- 复核 Apple HIG 的列表/层级原则、Material 3 的 Card/ListItem/Bottom Sheet 语义及 Android 自适应布局；确认“卡片不是任务列表默认容器”。
- 新增 [`10-component-level-ui-benchmark.md`](10-component-level-ui-benchmark.md)，详细规定 TaskRow、NowCard、CapacitySummary、ScheduleBlock、TaskCaptureSheet、TaskDetail、DraftChangeCard 的结构、状态、尺寸、动效和验收标准。
- 对当前实现的下一步要求从“改页面”升级为“先建立组件级视觉语言，再按 Today → Tasks → Capture → Plan/Detail 逐条重做”。

## 2026-09-25：多模式、画像与实时日程扩展

- 画像从精确原始事件出发，经可追溯、带样本量和时间范围的总结反哺估时、分段与提醒；情境变化时旧证据降权，学习复习仅在有真实回忆结果时用间隔练习模型。
- 语音先转为可编辑文字，再经过同一个对话意图与业务 Workflow；课程与伴随活动支持多轨并行，日程详情精确区分计划时间、真实计时、未开始、进行中和超时。
- Agnes 官方当前列出 `agnes-3.0-flash` 免费；这不是永久免费承诺。API 凭据不得进入 APK 或仓库，调试走本机代理，正式产品走网关。


## 2026-09-25：能力优先的整体设计复审

### 用户纠偏

- 旧 MVP 边界先不作为目标产品的上限；过早固定大量“不做/必须确认”会压低能力和体验。
- 先把不依赖 Agent 的完整功能链路做实，但架构应容纳后续 Workflow、Agent 与可授权自动化。

### 本轮产出与仍待验证

- 新增 [`06-overall-product-and-system-design.md`](06-overall-product-and-system-design.md)，覆盖用户旅程、功能全景、界面/动效、Android 能力、本地与未来服务端、数据一致性、实施验收和对抗性审查。
- 改用手动/辅助/委托三种控制模式作为设计方向；委托阈值和默认模式需真实用户验证，不是已定产品规则。
- `01`–`04` 与竞品报告中“永久禁止”或 P2 排除的语句保留为历史探索材料，不自动约束新目标方案。正式开发若改变旧规范，须同步更新 `CONTEXT.md` 与相关 ADR。


## 2026-09-25：建立探索区

### 本轮确认方向

- 先跑通强约束框架下的非 Agent、非 Workflow 智能参与的传统功能链路。
- 在可靠底座上盘点重复性工作，再判断哪些适合 Workflow 和 Agent 辅助。
- 整体架构需要渐进式调整，不能继续把跨模块流程全部堆在 UI 层。
- 这是 Repland 的产品与架构探索阶段，探索结论和正式产品约束分开管理。

### 本轮形成的暂定判断

- Workflow 是跨步骤的一致性编排，不是把每个按钮都包装成流程。
- Agent 只生成受限范围内的建议、解释或草案，不能直接成为事实写入者。
- 第一批优先抽取的 Workflow 是：任务收集、计划失效与草案生成、冲突处理、执行反馈、计划确认提交。
- 第一批 Agent 方向是：任务理解、任务拆分、冲突取舍解释、反馈后的重规划建议、每日总结和画像证据归纳。
- 传统核心底座先用确定性规则完成；Agent 不应成为核心链路的可用性依赖。

### 待下一轮验证

- 当前本地核心闭环的真实缺口，而不是文档中“已实现”的能力清单。
- `ReplandApp.kt`、各 ViewModel 和 Repository 之间规则重复的具体位置。
- 计划失效与草案生成是否可以在不改 Room schema 的情况下先抽出 Workflow。
- 任务反馈 → 调整草案这条链路是否已有足够测试，可以作为第一条重构样板。
- 哪些用户决策最值得做成 Agent 提供多个方案，而不是单一答案。

### 决策记录规则

- 探索假设不自动写入根目录 `CONTEXT.md`。
- 涉及难以回退、会影响数据模型或安全边界的决定，先形成候选 ADR，再确认。
- 每次代码调整都记录“改善了哪条核心链路、是否改变用户可见语义、如何验证”。
