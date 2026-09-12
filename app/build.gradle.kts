import java.util.Base64
import java.util.Properties

plugins {
    // AGP 9+ has built-in Kotlin support; no separate kotlin.android plugin required.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------- credentials
//
// Secrets live in ~/.api-keys.json, outside every repository, because this repo
// is public. local.properties now holds nothing but sdk.dir.
//
// A missing file or key yields an empty string so a fresh clone still builds;
// the app then degrades and says which key it is missing.

val apiKeysFile = File(System.getProperty("user.home"), ".api-keys.json")

@Suppress("UNCHECKED_CAST")
val apiKeys: Map<String, Any?> =
    if (apiKeysFile.exists()) {
        runCatching {
            groovy.json.JsonSlurper().parse(apiKeysFile) as? Map<String, Any?>
        }.getOrNull() ?: emptyMap()
    } else {
        logger.warn("No ~/.api-keys.json found. Building without API credentials.")
        emptyMap()
    }

@Suppress("UNCHECKED_CAST")
fun secret(group: String, key: String): String {
    val section = apiKeys[group] as? Map<String, Any?> ?: return ""
    return (section[key] as? String)?.trim().orEmpty()
}

// Still read for sdk.dir and for anyone who wants a local override.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun credential(group: String, key: String, legacyProperty: String): String =
    secret(group, key).ifBlank { localProps.getProperty(legacyProperty).orEmpty().trim() }

// ------------------------------------------------------------------- starter
//
// See starter-file.example.json for the shape. Absent is a supported state: the
// app is fully usable without it, and people are added from the setup screen.

val starterFile = File(System.getProperty("user.home"), ".watching-starter.json")

val starterSeed: String =
    if (starterFile.exists()) {
        Base64.getEncoder().encodeToString(starterFile.readBytes())
    } else {
        logger.lifecycle("No ~/.watching-starter.json - the app will seed a single person.")
        ""
    }

android {
    namespace = "net.shehane.watching"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.shehane.watching"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField(
            "String", "TMDB_READ_TOKEN",
            "\"${credential("tmdb", "readToken", "TMDB_READ_TOKEN")}\"",
        )
        buildConfigField(
            "String", "TMDB_API_KEY",
            "\"${credential("tmdb", "apiKey", "TMDB_API_KEY")}\"",
        )

        // The Drive sign-in carries no client id: Google matches the OAuth client by
        // package name plus signing certificate at runtime. Nothing to configure here,
        // and nothing secret to keep out of the repository.

        // Optional first-run seed. This repository is public, so no household's names
        // live in it; they come from a file in the builder's own home directory and
        // are embedded here. Base64 so arbitrary JSON needs no escaping.
        // Without the file the app seeds one person called "Me" and says so.
        buildConfigField("String", "STARTER_SEED", "\"$starterSeed\"")
        // Empty in a clone of this repo, which is the point: the cloud summariser
        // is simply not offered unless whoever built it supplied a key.
        buildConfigField("String", "GEMINI_API_KEY", "\"${credential("gemini", "apiKey", "GEMINI_API_KEY")}\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            // No applicationIdSuffix on purpose. A suffix would let debug and
            // release sit on the phone side by side, but it also means a second
            // package name, and Google ties an Android OAuth client to exactly
            // one package name. One app, one package, one client to create.
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            // The Google API client brings duplicate metadata from its transitive jars.
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/NOTICE.md"
            excludes += "/META-INF/LICENSE.md"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Foundation only. Every surface in this app is drawn to match the mockups,
    // so Material components would only be restyled away.
    implementation(libs.compose.runtime)
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.text)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.serialization.json)

    // Drive sync. Same versions as IdeasApp, which already builds and runs on this
    // machine, so this is a proven combination rather than a guess.
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)

    // On-device generation, through AICore. Two APIs because they are provisioned
    // separately: a phone can have one feature and not the other, and the prompt
    // one is worth far more because it takes an instruction.
    implementation(libs.mlkit.genai.prompt)
    implementation(libs.mlkit.genai.summarization)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
