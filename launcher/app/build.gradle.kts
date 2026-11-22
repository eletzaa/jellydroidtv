import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Lokal (bauen.ps1): Build-Ausgabe außerhalb von OneDrive, sonst sperrt die Synchronisation Dateien.
System.getenv("JELLYDROID_BUILD_DIR")?.let { layout.buildDirectory.set(file("$it/app")) }

android {
    namespace = "de.jellydroid.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.jellydroid.launcher"
        minSdk = 28
        targetSdk = 34
        versionCode = 110
        versionName = "1.10"
    }

    // Signaturschlüssel liegt NICHT im Repo (öffentlich). Reihenfolge:
    // 1. Umgebungsvariable JELLYDROID_KEYSTORE_B64 (Keystore als Base64, z. B. Cloud-Umgebung oder GitHub-Secret)
    // 2. Umgebungsvariable JELLYDROID_KEYSTORE (Pfad zur Datei)
    // 3. launcher/signing/debug.keystore (lokal, per .gitignore ausgeschlossen)
    // Ohne Schlüssel wird mit dem lokalen Debug-Schlüssel signiert; die APK geht dann nicht per
    // "adb install -r" über die installierte Version.
    val tvKey: File? = run {
        val b64 = System.getenv("JELLYDROID_KEYSTORE_B64")?.trim()
        val path = System.getenv("JELLYDROID_KEYSTORE")?.trim()
        when {
            !b64.isNullOrEmpty() -> layout.buildDirectory.file("signing/tv.keystore").get().asFile.also {
                it.parentFile.mkdirs()
                it.writeBytes(Base64.getMimeDecoder().decode(b64))
            }
            !path.isNullOrEmpty() -> file(path)
            else -> rootProject.file("signing/debug.keystore")
        }.takeIf { it.exists() }
    }
    signingConfigs {
        if (tvKey != null) create("tv") {
            storeFile = tvKey
            storePassword = System.getenv("JELLYDROID_KEYSTORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("JELLYDROID_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("JELLYDROID_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("tv") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation(project(":airplay"))
    // Home Assistant: WebSocket + Anmeldung, QR-Code für die Anmeldung per Handy
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.zxing:core:3.5.3")
}
