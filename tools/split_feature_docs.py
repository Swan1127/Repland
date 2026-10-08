"""Create missing per-feature documents from the retained handover inventory.

Existing files are never overwritten: subsequent feature work belongs in those files.
"""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
inventory = root / "docs/FEATURE_FRONTEND_INVENTORY_2026-10-06.md"
destination = root / "docs/features"
destination.mkdir(exist_ok=True)
rows = []
for line in inventory.read_text(encoding="utf-8").splitlines():
    if not re.match(r"\| [NTKPES]\d{2} \|", line):
        continue
    fields = [part.strip() for part in line.strip().strip("|").split("|")]
    if len(fields) != 5:
        raise ValueError(f"Unexpected feature row: {fields[0]}")
    feature_id, title, frontend, business, evidence = fields
    rows.append((feature_id, title))
    target = destination / f"{feature_id}.md"
    if target.exists():
        continue
    target.write_text(
        f"# {feature_id} — {title}\n\n"
        "盘点来源：2026-10-06，源码 5ea3338 / 0.1.29。"
        "这是保留的能力定位与证据索引，不代表本机或后续构建已验收。\n\n"
        f"## 用户入口与前端\n\n{frontend}\n\n"
        f"## 业务逻辑与数据来源\n\n{business}\n\n"
        f"## 实现状态、测试与边界\n\n{evidence}\n\n"
        "代码路径相对 android/app/src/main/java/com/swan1127/repland/；"
        "设备测试在 android/app/src/androidTest/java/com/swan1127/repland/，"
        "标明单元的测试在 android/app/src/test/java/com/swan1127/repland/。\n\n"
        "后续更新须记录规则、源码、具体构建、实际结果与未验证范围；"
        "规格确认、代码实现、自动化通过和设备验证分别记录。\n\n"
        "[原盘点](../FEATURE_FRONTEND_INVENTORY_2026-10-06.md) · "
        "[状态与环境交接](../ACCOUNT_HANDOVER_2026-10-06.md)\n",
        encoding="utf-8",
    )
if len(rows) != 74:
    raise ValueError(f"Expected 74 features, found {len(rows)}")
index = destination / "README.md"
if not index.exists():
    index.write_text(
        "# Repland 独立功能文档\n\n"
        "每个入口使用独立文档维护前端、业务、数据来源与测试。"
        "初始 74 项来自 0.1.29 交接盘点；新增批次在对应文档追加，"
        "不将旧证据转为新版本验收。\n\n"
        + "\n".join(f"- [{feature_id} — {title}]({feature_id}.md)" for feature_id, title in rows)
        + "\n",
        encoding="utf-8",
    )
print(f"Indexed {len(rows)} independent feature documents; existing files preserved.")
