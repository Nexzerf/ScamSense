// Vendored third-party code, kept in its own module so its licences stay separate:
//  - io.github.muntashirakon.adb:    libadb-android (GPL-3.0-or-later OR Apache-2.0; parts BSD-3-Clause / MIT)
//    https://github.com/MuntashirAkon/libadb-android @ c849886
//  - io.github.muntashirakon.crypto: spake2-java, pure-Java module (LGPL-3.0)
//    https://github.com/MuntashirAkon/spake2-java @ c24e0aa
// Licence texts: libadb/licenses/. Changes for ScamSense: build file only (sources unmodified).
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.muntashirakon.adb"
    compileSdk = 35
    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        // Third-party sources: reported, not enforced, so upstream code stays unmodified.
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    api(libs.bouncycastle.prov)
    api(libs.conscrypt.android)
}
