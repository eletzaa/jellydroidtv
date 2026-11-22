plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Lokal (bauen.ps1): Build-Ausgabe außerhalb von OneDrive, sonst sperrt die Synchronisation Dateien.
System.getenv("ELIAS_BUILD_DIR")?.let { layout.buildDirectory.set(file("$it/app")) }

android {
    namespace = "de.elias.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.elias.launcher"
        minSdk = 28
        targetSdk = 34
        versionCode = 19
        versionName = "1.9"
    }

    // Fester Schlüssel im Projekt (launcher/signing/debug.keystore, nur im privaten Repo), damit jedes
    // Gerät/Konto dieselbe Signatur baut und "adb install -r" über die installierte Version geht.
    val tvKey = rootProject.file("signing/debug.keystore")
    signingConfigs {
        if (tvKey.exists()) create("tv") {
            storeFile = tvKey
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
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
