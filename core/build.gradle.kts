import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure-Kotlin detection engine + shared API models. No Android dependencies, so it is
// reused unchanged by the Android app and by the AI call-assistant server.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
