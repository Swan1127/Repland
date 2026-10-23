// Repland — 顶层构建脚本
// Keep the build toolchain pinned for reproducible builds.
//   AGP 9.0.1 / Kotlin 2.2.10 / KSP 2.2.10-2.0.2 / Room 2.7.2 / Compose BOM 2024.09.00
plugins {
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
}
