import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// ShazamKit for Android ships as an AAR downloaded from developer.apple.com (not on Maven).
// When it's present in app/libs, Shazam song identification is compiled in; otherwise a stub is used.
val shazamAar = file("libs/shazamkit-android-release.aar")
val hasShazam = shazamAar.exists()

android {
    namespace = "com.phlogistonistic.radiometry"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.phlogistonistic.radiometry"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        val token = localProperties.getProperty("shazam.developerToken", "")
        buildConfigField("String", "SHAZAM_DEVELOPER_TOKEN", "\"$token\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Personal sideloading: sign release builds with the debug key.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main").kotlin.directories.add(if (hasShazam) "src/shazam/kotlin" else "src/noShazam/kotlin")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    if (hasShazam) {
        implementation(files(shazamAar))
        // ShazamKit's own runtime dependencies.
        implementation("com.squareup.okhttp3:okhttp:4.12.0")
        implementation("com.squareup.retrofit2:retrofit:2.11.0")
        implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    }
}
