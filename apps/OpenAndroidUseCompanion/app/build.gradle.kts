plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.openandroiduse.companion"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.openandroiduse.companion"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.3"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Deliberately no dependencies: the companion uses only the Android platform
// (org.json, ServerSocket, programmatic UI) to keep the supply-chain and
// review surface minimal. See docs/design-docs/on-device-companion.md.
