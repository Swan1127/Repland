import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val semanticVersion = Regex("^\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.-]+)?$")

val replandVersionCode = providers.gradleProperty("REPLAND_VERSION_CODE")
    .orElse("1000")
    .map { value ->
        value.toIntOrNull()?.takeIf { it > 0 }
            ?: error("REPLAND_VERSION_CODE 必须是正整数，当前值：$value")
    }
    .get()
val replandVersionName = providers.gradleProperty("REPLAND_VERSION_NAME")
    .orElse("0.1.0")
    .map { value ->
        require(semanticVersion.matches(value)) {
            "REPLAND_VERSION_NAME 必须是语义化版本，例如 0.1.0，当前值：$value"
        }
        value
    }
    .get()

val releaseSigningProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use { input -> load(input) }
    }
}

fun signingValue(name: String): String? =
    providers.gradleProperty(name).orNull
        ?: providers.environmentVariable(name).orNull
        ?: releaseSigningProperties.getProperty(name)

val releaseStoreFile = signingValue("REPLAND_RELEASE_STORE_FILE")
val releaseStorePassword = signingValue("REPLAND_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("REPLAND_RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("REPLAND_RELEASE_KEY_PASSWORD")
val releaseSigningValues = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
val isReleaseSigningConfigured = releaseSigningValues.all { it.isNullOrBlank().not() }

android {
    namespace = "com.swan1127.repland"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.swan1127.repland"
        minSdk = 24
        targetSdk = 36
        versionCode = replandVersionCode
        versionName = replandVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseSigningConfig = if (isReleaseSigningConfigured) {
        signingConfigs.create("release") {
            storeFile = rootProject.file(requireNotNull(releaseStoreFile))
            storePassword = requireNotNull(releaseStorePassword)
            keyAlias = requireNotNull(releaseKeyAlias)
            keyPassword = requireNotNull(releaseKeyPassword)
        }
    } else {
        null
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = releaseSigningConfig
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("internal") {
            initWith(getByName("release"))
            applicationIdSuffix = ".internal"
            versionNameSuffix = "-internal"
            // An installable internal build is always signed. A supplied release key is
            // preferred; the debug key is strictly a local/closed-test fallback.
            signingConfig = releaseSigningConfig ?: signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        animationsDisabled = true
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

val validateReleaseSigning by tasks.registering {
    group = "verification"
    description = "Verifies that a release artifact cannot be built without an explicit signing key."
    doLast {
        check(isReleaseSigningConfigured) {
            "Release 签名尚未配置。请设置 REPLAND_RELEASE_STORE_FILE、" +
                "REPLAND_RELEASE_STORE_PASSWORD、REPLAND_RELEASE_KEY_ALIAS 和 " +
                "REPLAND_RELEASE_KEY_PASSWORD；详情见 docs/RELEASE.md。"
        }
    }
}

val verifyOfflineMvpBoundary by tasks.registering {
    group = "verification"
    description = "Guards the current local-only MVP against accidental network or embedded-key additions."
    inputs.dir(layout.projectDirectory.dir("src/main"))
    doLast {
        val forbiddenMarkers = listOf(
            "android.permission.INTERNET",
            "HttpURLConnection",
            "OkHttpClient",
            "Retrofit.Builder",
        )
        val offenders = fileTree("src/main") {
            include("**/*.kt", "**/*.xml")
        }.files.flatMap { source ->
            val text = source.readText()
            forbiddenMarkers.filter(text::contains).map { marker ->
                "${source.relativeTo(projectDir)} contains $marker"
            }
        }
        check(offenders.isEmpty()) {
            "当前 MVP 不允许联网实现或内嵌服务密钥。请先完成 docs/EXTENSION_READINESS.md 的" +
                "后端、隐私和用户授权门槛：${offenders.joinToString()}"
        }
    }
}

tasks.configureEach {
    if (name in setOf("packageRelease", "bundleRelease", "signReleaseBundle")) {
        dependsOn(validateReleaseSigning)
    }
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn(verifyOfflineMvpBoundary)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.pdfbox.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
