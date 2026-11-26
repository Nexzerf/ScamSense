import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// AI call assistant: answers calls forwarded from the user's SIM, transcribes the caller
// (provider speech recognition), runs the SAME detection engine as the app (:core),
// then hangs up on scammers + SMS-alerts, or whispers a summary and connects safe calls.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

application {
    mainClass.set("com.scamsense.server.MainKt")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.sqlite.jdbc)
    implementation(libs.logback.classic)
    testImplementation(libs.junit)
    testImplementation(libs.ktor.server.test.host)
}

/** Single runnable jar: java -jar server/build/libs/scamsense-server.jar */
tasks.register<Jar>("fatJar") {
    group = "build"
    archiveFileName.set("scamsense-server.jar")
    manifest { attributes["Main-Class"] = "com.scamsense.server.MainKt" }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/INDEX.LIST")
}
