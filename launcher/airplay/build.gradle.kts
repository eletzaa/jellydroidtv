plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Lokal (bauen.ps1): Build-Ausgabe außerhalb von OneDrive, sonst sperrt die Synchronisation Dateien.
System.getenv("JELLYDROID_BUILD_DIR")?.let { layout.buildDirectory.set(file("$it/airplay")) }

val nativeDeps: Configuration by configurations.creating { isTransitive = false }
val nativeDepsDir = layout.buildDirectory.dir("nativeDeps")

android {
    namespace = "de.jellydroid.launcher.airplay"
    compileSdk = 34
    ndkVersion = "27.0.12077973"

    defaultConfig {
        minSdk = 28
        // Philips-Fernseher sind ARM; x86 nur für den Emulator.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared", "-DNATIVE_DEPS=" + nativeDepsDir.get().asFile.absolutePath)
            }
        }
        consumerProguardFiles("consumer-rules.pro")
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
            // Zwischendateien des C-Builds ebenfalls außerhalb von OneDrive
            System.getenv("JELLYDROID_BUILD_DIR")?.let { buildStagingDirectory = file("$it/airplay-cxx") }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
    }
    sourceSets["main"].jniLibs.srcDir(nativeDepsDir.map { it.dir("jniLibs") })
}

// Oboe und OpenSSL kommen als fertige Bibliotheken aus Maven (Prefab-AARs). Statt des Prefab-Werkzeugs,
// das an JAVA_TOOL_OPTIONS scheitert (bauen.ps1 setzt sie), werden .so und Header hier selbst entpackt.
tasks.register<Sync>("extractNativeDeps") {
    from({ nativeDeps.files.map { zipTree(it) } })
    for (m in listOf("crypto", "oboe")) {
        include("prefab/modules/$m/include/**")
        include("prefab/modules/$m/libs/android.arm64-v8a/*.so", "prefab/modules/$m/libs/android.armeabi-v7a/*.so")
    }
    eachFile {
        val seg = relativePath.segments // prefab, modules, <modul>, include|libs, ...
        relativePath = if (seg[3] == "include") RelativePath(true, "include", seg[2], *seg.drop(4).toTypedArray())
        else RelativePath(true, "jniLibs", seg[4].removePrefix("android."), seg.last())
    }
    includeEmptyDirs = false
    into(nativeDepsDir)
}
tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake") || name.contains("JniLib")) dependsOn("extractNativeDeps")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.tv:tv-material:1.0.0")
    nativeDeps("com.google.oboe:oboe:1.9.3@aar")
    nativeDeps("com.android.ndk.thirdparty:openssl:1.1.1q-beta-1@aar")
}
