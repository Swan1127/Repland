import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9.0.1 ships built-in Kotlin support: applying org.jetbrains.kotlin.android
    // on top of com.android.application fails with "Cannot add extension with name
    // 'kotlin'" because AGP already registers it (see BuiltInKotlinServices).
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.swan1127.repland"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.swan1127.repland"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // No instrumentation runner: this module ships no androidTest sources.
        // JVM unit tests under app/src/test are wired up separately (see dependencies).
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".redesign"
            versionNameSuffix = "-redesign"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // The app has no BuildConfig usage; leaving it off keeps the class surface minimal.
        buildConfig = false
    }

    // NOTE: the 87 Kotlin sources are reached through a directory junction at
    // app/src/main/java/com/swan1127/repland -> <root>/repland. AGP 9's built-in
    // Kotlin rejects any attempt to add source dirs via the sourceSets DSL
    // (KgpUtils.reportErrorIfKotlinSourceSetsAreUsed), so the conventional layout
    // is used instead and the junction keeps a single copy of the sources.

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }

    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.8.2")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    // Only the 12 bundled icons are used; material-icons-extended is deliberately absent.
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Local PDF timetable parsing. Never leaves the device.
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // JVM unit tests for the pure-Kotlin domain layer (no Android APIs, no Robolectric).
    testImplementation("junit:junit:4.13.2")

    // Lets a JVM test install a test dispatcher as Dispatchers.Main, which is what the
    // ViewModels' viewModelScope needs. Test-only; the app itself never uses it.
    // 1.9.0 matches kotlinx-coroutines-android above.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
