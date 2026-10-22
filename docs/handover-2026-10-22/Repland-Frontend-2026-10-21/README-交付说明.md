# Repland 新版前端 + 多 Agent / 记忆模型 · 本地交付

基于你提供的最新源码附件 `5a15288` 完成前端改造后，继续从 `repland-0.1.35.zip` 逻辑融合本地六角色规划团队与 FSRS-inspired 自适应记忆模型。保留暖纸 / 薄荷 / 淡青视觉、现有云端群聊、任务子项 / 重复规则及数据库，不整包覆盖旧版本。

## 启动

双击桌面 **Repland 新版 2026-10-22**。入口启动本机已有模拟器并打开独立测试包，不覆盖原版数据。

也可在 `repland-upstream-design` 内运行 `Start-Redesign.ps1`。

## 文件

- `Repland-多Agent与记忆模型融合交付-2026-10-22.zip`：本次融合源码 ZIP、APK、界面预览及验证资料的完整交接包。转移到别处后需解开其中源码 ZIP；本机源码目录已展开。
- `verification/preview.html`：融合功能截图，以及前端阶段页面、原版对照和实际操作录屏。
- `repland-upstream-design/`：本轮修改后的可继续开发源码。
- `Repland-新版-2026-10-22.apk`：本轮最终独立调试安装包，非商店发布签名。
- `Repland-前端改造源码-2026-10-22.zip`：名称沿用前端阶段，内容为本次融合后的最新源码；不含构建缓存、SDK、本机路径和目录链接。
- `Repland-前端阶段基线-F631-2026-10-22.zip`：融合前的源码回退点，独立保留，不是本次最新版。
- `repland-upstream-design/docs/MULTI_AGENT_FSRS_INTEGRATION_2026-10-22.md`：融合边界、功能入口、数据兼容和模型说明。
- `verification/INTEGRATION_QA_2026-10-22.md`：本次融合的构建、测试及设备验证结果。
- `repland-upstream-design/docs/FRONTEND_REDESIGN_2026-10-22.md`：基线、功能 / 数据映射、实现范围、构建方式与限制。
- `verification/RELEASE_QA_2026-10-22.md`：融合前 F631 前端阶段的验证记录，仅作历史基线。
- `verification/source-change-manifest.csv`：相对原 ZIP 的源码变更清单。

入口：功能与操作 → 本地规划团队；我的 → 智能与个性化 → 记忆参考（默认关闭）；功能与操作 → 复习点。团队离线运行，草案需确认才落库；模型为供体自定义 FSRS-inspired 算法，不冒称官方完整 FSRS。

没有擅自提交 GitHub、创建分支 / PR 或发布。云端密钥未配置的真实服务成功流程，以及原快照的通知权限 lint 问题，均单列说明。
