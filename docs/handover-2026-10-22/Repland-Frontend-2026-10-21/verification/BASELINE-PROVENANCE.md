# Repland 原始版本对照构建

核验日期：2026-10-22（Asia/Shanghai）。

## 来源与完整性

- 原始附件：`C:/Users/x16/Desktop/repland-upstream-design.zip`。
- 附件 SHA256：`81C3D2997F54B1340345DE0A0FB4C4A26B7DDE20C5F6FB4306D3A1E7FC27E3AA`。
- 原附件 `SETUP.md` 标明源码快照为 `feature/upstream-design-adoption` 的 `5a15288`。
- 解压前逐项校验全部 352 个 ZIP 条目的绝对目标路径均在本目录内；拒绝绝对路径、路径越界、重复路径、流名称和符号链接。
- 构建前后均将 272 个原始文件与附件逐个进行 SHA256 比较，唯一变化的原始文件是 `app/build.gradle.kts`，用于增加调试包名后缀。
- `repland/` 下全部 141 个原文件和原始界面资源字节不变。

## 本地隔离配置

- 仅为 debug 构建增加 `applicationIdSuffix = ".redesignbaseline"`。
- 新增 debug 默认语言及英文 `app_name` 资源：`Repland Baseline`。
- 新增本地 `local.properties` 指向工作区现有 SDK。
- 恢复源码 junction：`app/src/main/java/com/swan1127/repland` 指向本副本的 `repland/`。
- 工具链复用工作区 `outputs/Environment` 中的 JDK17、Gradle 9.1.0、Android SDK 和 Gradle 缓存。

## 构建产物

- 命令：`./Build-Baseline.ps1`，内部执行 `:app:assembleDebug --offline --no-daemon --max-workers=2`。
- 构建结果：`BUILD SUCCESSFUL in 1m 44s`；37 个可执行任务，其中 30 个执行、7 个来自缓存。
- 日志：`build-baseline.log`。
- APK：`repland-upstream-design/app/build/outputs/apk/debug/app-debug.apk`。
- APK 大小：21,033,593 字节。
- APK SHA256：`92A880ECFA6181F7B4323814289A57F9648F64DF76D68246B96DC55B6CAFC35F`。
- `aapt dump badging` 核验包名：`com.swan1127.repland.redesignbaseline`。
- 默认及英文应用标签：`Repland Baseline`。
- 启动 Activity：`com.swan1127.repland.MainActivity`。

附件自带 APK 原样保留于 `repland-upstream-design/apk/app-debug.apk`，包名为 `com.swan1127.repland`，SHA256 为 `EC8C21537C030DC312BB5164A9C4741FCFE5F63BA3A33C1CB6DAC2BF4D69E7EB`。它可能与本机原安装冲突，供安装对照使用的是上方独立包名 APK。

## 验证边界

本任务完成了安全解压、原文件完整性比较、独立包构建以及 APK 元数据核验。未执行 ADB，未安装或启动 APK，未启动模拟器，未截图；运行与视觉对照由协调代理后续验证。
