import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is read from keystore.properties (git-ignored). When absent, the
// release build falls back to the debug key so the optimized APK is still installable
// for testing. NEVER publish a debug-signed build to Google Play.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.scamsense.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.scamsense.app"
        minSdk = 23          // Android 6.0+ (~99% of active devices); needed for Keystore AES-GCM
        targetSdk = 35
        versionCode = 18
        versionName = "1.6.2"
        vectorDrawables.useSupportLibrary = true
        @Suppress("UnstableApiUsage")
        resourceConfigurations += listOf("th", "en")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // Live call listening runs whisper.cpp natively. 32-bit ARM is kept so the app
            // still installs on budget phones (live listening itself needs Android 11+).
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    ndkVersion = "27.2.12479018"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Do not embed the dependency list blob in the APK (privacy / smaller APK).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.maxHeapSize = "2g" }
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        disable += listOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":libadb"))
    implementation(libs.bouncycastle.pkix)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}

// Robolectric brings an older BouncyCastle (bcprov-jdk18on) with the same classes as the
// app's bcprov-jdk15to18; on the unit-test classpath it shadows the newer one. Tests only.
configurations.matching { it.name.contains("UnitTest") }.configureEach {
    exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
}
