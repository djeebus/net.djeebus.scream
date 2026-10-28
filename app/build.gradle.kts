import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Android refuses to update an app whose signature changed, so every build (local and CI) must use
// the same key. CI passes it via SCREAM_* env vars; locally it comes from ~/.android/scream-release.properties.
val localSigning = Properties().apply {
    file("${System.getProperty("user.home")}/.android/scream-release.properties")
        .takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(env: String, prop: String): String? = System.getenv(env) ?: localSigning.getProperty(prop)
val signingStoreFile = signingValue("SCREAM_KEYSTORE_FILE", "storeFile")

android {
    namespace = "net.djeebus.scream"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.djeebus.scream"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    signingConfigs {
        if (signingStoreFile != null) {
            create("scream") {
                storeFile = file(signingStoreFile)
                storePassword = signingValue("SCREAM_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("SCREAM_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("SCREAM_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        // Without a key configured (e.g. a fresh clone) this falls back to the default debug key.
        // Debug uses the same key so local installs can update release installs and vice versa.
        val scream = signingConfigs.findByName("scream")
        getByName("debug") { if (scream != null) signingConfig = scream }
        getByName("release") { signingConfig = scream ?: signingConfigs.getByName("debug") }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
}
