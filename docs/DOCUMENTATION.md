# 文档维护与备份

更新：2026-10-22（Asia/Shanghai）。本次将产品与主版本说明统一到当前源码，移出已被取代的方案、重复功能模板和阶段报告；所有原文件先备份并逐文件 SHA-256 校验，再清理。

## 当前有效文档

| 文档 | 唯一职责 |
| --- | --- |
| [README](../README.md) | 项目入口、当前版本、开发方式。 |
| [项目介绍](../PROJECT_INTRODUCTION.md) | 面向项目展示的背景、意义、技术方案与多层 Agent 创新点。 |
| [主思路](../REPLAND_IDEA.md) | 产品方向、当前行为、生效规则与后续范围。 |
| [领域术语](../CONTEXT.md) | 术语定义。 |
| [工程边界](EXTENSION_READINESS.md) | 当前源码定位、数据契约、扩展要求与验证缺口。 |
| [隐私](PRIVACY.md) | 本地数据、模型发送范围、密钥及清空。 |
| [测试](TESTING.md) | 复验命令、设备范围及证据口径。 |
| [发布](RELEASE.md) | 版本、构建身份、签名与交付要求。 |
| [内部数值契约](features/H01_NUMERIC_CONTRACT.md) | 保留的 numeric-v1 计算口径，独立于当前使用总结。 |
| [2026-10-18 验证](verification/automatic-usage-summary-2026-10-18/README.md) | 当时使用总结源码的历史证据及对应清单。 |
| [2026-10-11 验证](verification/final-0.1.35/README.md) | 更早同版本号源码的历史证据及对应清单。 |
| 本文件 | 文档维护、清理及恢复索引。 |

文档清理时保留 11 份；新增项目介绍后，当前共 12 份 Markdown 文档。两套验证目录中的 JSON、源码哈希及截图原样保留；仅修复其 README 的入口链接并明确历史范围。历史证据不能因版本号相同而作为当前源码的验收结果。

## 本次清理

已从工作区移出 **230 份旧 Markdown、34 份配套历史资产及 1 个过期文档生成脚本**，均可由备份恢复：

- 根目录旧 `DESIGN_*` 与 `IDEAL_PLAN_FEATURES.md`：有效的现行界面规则并入主思路，其余为旧设计交付。
- `docs/` 下 dated 计划、审查、交接、缺陷、答辩和阶段交付记录，以及旧的反复追加版本进度：当前边界归入主思路、工程、测试与发布文档。
- 旧 `docs/features/` 拆分模板、`docs/archive/`、`docs/adr/`、`exploration/` 与 `docs/design-preview/` 的历史文档和配套演示资产：保留完整外部副本，不再混入当前文档入口。
- `tools/split_feature_docs.py`：只用于从旧 0.1.29 盘点生成模板，随其输入归档，避免留下失效工具或重新生成旧文档。

保留 `docs/RELEASE.md` 与 `docs/EXTENSION_READINESS.md` 原路径，使现有 Gradle 提示引用继续有效。`Build-Design.ps1`、启动脚本、Android 代码、CI、版本配置及既有未提交源码修改均保留。本次未处理运行日志、构建缓存、`output/`、另一个工作区或根目录已有的上游 ZIP。

## 完整备份

本机备份目录：`D:\Repland-backups\docs-cleanup-2026-10-22-171342`。备份在仓库之外，Git 提交和推送不会自动携带它；迁移电脑时需另行复制。

| 备份内容 | 用途 |
| --- | --- |
| `original/` | 清理前 288 个文档、配套文件和旧生成脚本，保留相对路径。 |
| `documents-before.zip` | 同一批原文件的压缩副本，可独立解压查阅。 |
| `manifest.json` | 原文件相对路径、字节数及 SHA-256，原样副本已经逐一比对。 |
| `archive-sha256.json` | ZIP 文件自身的校验值。 |
| `removed-files.json` | 实际清理路径、原因及原始哈希。 |
| `source-baseline.json` | 整理开始时 334 个其余受版本控制文件的哈希，用于检查代码未被本轮改动。 |
| `git-status-before.txt`、`head-before.txt` | 整理前分支源码状态，包含已有未提交修改记录。 |
| `Restore-Documents.ps1` | 默认只校验，显式 `-Apply` 才恢复并覆盖相同路径。 |
| `cleanup-result.json` | 清理后的校验结果与保留文件清单。 |

单文件恢复：从 `original/` 中按原相对路径复制回项目即可，例如只恢复旧主思路时复制 `original/REPLAND_IDEA.md`。复制前先保留需要留下的当前文档。

全量恢复前，可在 PowerShell 中先执行只读校验：

```powershell
& 'D:\Repland-backups\docs-cleanup-2026-10-22-171342\Restore-Documents.ps1'
```

确认要用备份覆盖同名路径后执行：

```powershell
& 'D:\Repland-backups\docs-cleanup-2026-10-22-171342\Restore-Documents.ps1' -Apply
```

恢复脚本逐文件检查路径与哈希，只恢复这批原文件，不删除之后新增的文件。本次新建的 `docs/DOCUMENTATION.md` 会保留。备份不包含运行时数据库或设备数据，不能用于恢复 App；此处恢复仅针对仓库文档及所列旧工具。

## 后续维护

产品决定统一改主思路；工程约束写工程文档；术语只放词表。新版本修改时同步 README 与发布规则，不在多个文件中逐段追加“最新候选”。

验收记录须绑定源码快照、构建类型与具体范围，保留失败和未知项。若需要新历史证据，新增到独立的验证目录并从测试文档引用；不要把旧测试数字移成当前结论。

历史备份只供追溯。恢复旧方案不表示它重新生效；后续修改仍须对照当前主思路及实际代码。
