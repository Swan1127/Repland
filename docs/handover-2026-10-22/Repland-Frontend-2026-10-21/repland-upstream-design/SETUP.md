# Repland · 上游设计版

> 本目录已完成 2026-10-22 前端改造，并继续融合本地六角色规划团队及自定义 FSRS-inspired 记忆模型。请先阅读 [融合说明](docs/MULTI_AGENT_FSRS_INTEGRATION_2026-10-22.md) 与 [前端说明](docs/FRONTEND_REDESIGN_2026-10-22.md)。下文保留附件原始快照说明；其中旧配色、测试数量及版本状态不是本轮最终结果。

这是本地分支 `feature/upstream-design-adoption` 的完整源码快照（commit `5a15288`）。
这一版把 swan1127 上游的配色、字体、动效和图标整套搬进来，并按同一套设计重做了本地独有功能的界面。

上游的原始代码在 `swan-latest/`（HEAD `08a686b`），只作为参考，没有包含在本包里。

## 环境要求

| 项目 | 版本 |
| --- | --- |
| JDK | 21 |
| Gradle | 9.1.0（本仓库没有 gradle wrapper，请自行安装） |
| Android SDK | compileSdk 36 / minSdk 26 / targetSdk 36 |
| 构建插件 | AGP 9.0.1 · Kotlin 2.2.10 · KSP 2.2.10-2.0.2 · Room 2.7.2 · Compose BOM 2024.09.00 |

## 第一次使用必须做两件事

### 1. 建好 SDK 路径

仓库里没有 `local.properties`（含本机绝对路径，不入库）。新建一个：

```properties
sdk.dir=D\:\\A_android_studio-sdk
```

路径换成你自己的 Android SDK 位置。

### 2. 重建目录链接

`app/src/main/java/com/swan1127/repland` 原本是一个指向 `<root>/repland` 的链接
（junction / symlink），这样 87 个 Kotlin 源文件只存一份。AGP 9 不允许再用
`sourceSets` 追加源目录，所以约定俗成用链接来保持单一副本。解压后的包没有这个链接，
**不建的话编译会报找不到源文件**。

Windows（PowerShell 或 cmd，在包根目录执行）：

```powershell
.\setup-junction.ps1
```

或手动：

```cmd
mkdir app\src\main\java\com\swan1127
mklink /J app\src\main\java\com\swan1127\repland repland
```

macOS / Linux：

```bash
./setup-junction.sh
```

或手动：

```bash
mkdir -p app/src/main/java/com/swan1127
ln -s ../../../../../../repland app/src/main/java/com/swan1127/repland
```

## 构建

```bash
gradle :app:assembleDebug        # 产物：app/build/outputs/apk/debug/app-debug.apk
gradle :app:testDebugUnitTest    # 单元测试
```

> 改了源码后如果 Gradle 报 `UP-TO-DATE`，加 `--rerun-tasks` 强制重编，
> 否则真实的编译错误会被缓存掩盖。

## 已验证状态

- 单元测试：87 个套件 / 1476 个用例 / 0 失败
- `:app:assembleDebug` 构建通过
- 真机与模拟器上人工核对了今日 / 计划 / 我的三页与任务详情页的配色

## 这一版改了什么

- 主题：暖白纸面背景（`#F7F8FC`）、卡片纯白（`#FFFFFF`）、主色青（`#326B75`）、
  淡紫与暖沙点缀色，夜间模式整套对应
- 新增共享组件：`DesignButton`、`DesignFilterChip`、焦点卡片视觉变量、图标集
- 今日 / 计划 / 任务详情 / 排期总览 / 任务中心 / 空状态全部按新主题重绘
- 启动图标、窗口底色、桌面小组件配色换成上游美术资源
- 本地功能全部保留：多智能体群聊、语音输入、命令面板、工具与洞察、
  导入导出、子任务与周期任务、个人画像、撤销

## 还没接进来的上游功能

上游 `main` 上比这个分支多的一部分能力（用量摘要、执行会话、数值画像、
自主规划等）依赖上游的数据库结构（`inputSources`、`planning_workspace`），
而本地库结构已经走到 v21（`subtasks`、`repeatRule`），两边同号不同义。
接这些功能之前需要先做一版 v22 的合并迁移，这一步还没做。
