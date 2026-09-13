plugins {
    // AGP 9+ has built-in Kotlin support; no separate kotlin.android plugin required.
    alias(libs.plugins.android.application)
}

android {
    namespace = "net.shehane.aicorecheck"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.shehane.aicorecheck"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // The same two ML Kit artifacts, at the same versions, as the main app.
    implementation(libs.mlkit.genai.prompt)
    implementation(libs.mlkit.genai.summarization)
    implementation(libs.kotlinx.coroutines.android)
}
