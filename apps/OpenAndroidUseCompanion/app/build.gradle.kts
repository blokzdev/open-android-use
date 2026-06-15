plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        release {
            // Phase 5.3: begin R8 code shrink (the big win is pruning unused
            // material-icons-extended + androidx). Resource shrink and final
            // tuning land in Phase 6; keep-rules live in proguard-rules.pro.
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            // The Anthropic SDK's transitive jars ship duplicate license/notice
            // metadata that the APK merger rejects; none of it is runtime code.
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.md",
                "META-INF/INDEX.LIST",
            )
            // Phase 5.2: the Gemini SDK's transitive HTTP stack (google-http-client)
            // bundles the Mozilla public-suffix list; more than one copy lands on
            // the merge path. Keep one — it's a runtime data file, not metadata.
            pickFirsts += listOf("mozilla/public-suffix-list.txt")
        }
    }
}

// Dependency policy (docs/SUPPLY_CHAIN_SECURITY.md, docs/exec-plans/active/
// 20260612-phase3-on-device-agent.md): the *control surface* — accessibility
// service, loopback endpoint, snapshot/action code — stays dependency-free
// (org.json, ServerSocket, programmatic UI). The on-device agent feature
// (the `agent` package) talks to the Claude API through the official Anthropic
// Java SDK and (Phase 5.2) to the Gemini API through the official Google Gen AI
// Java SDK. Phase 4 adds Jetpack Compose / Material 3 for the *presentation
// layer only* (Activities, theme, screens); the control surface must not import
// androidx/compose, and nothing under it may import com.anthropic or
// com.google.genai (those stay inside the `agent` package).
dependencies {
    implementation("com.anthropic:anthropic-java:2.40.1")
    // Phase 5.2: the second model provider (Gemini, BYOK) talks to the Gemini
    // API through the official Google Gen AI Java SDK. Same agent-package-only
    // rule as the Anthropic SDK (see the dependency policy note below).
    implementation("com.google.genai:google-genai:1.58.0")

    // Presentation layer only (Phase 4): Jetpack Compose + Material 3. The BOM
    // pins mutually-compatible Compose library versions; the Compose compiler
    // comes from the Kotlin 2.0 plugin applied above.
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    // Phase 4.6e: window size classes for the tablet/foldable two-pane layout.
    implementation("androidx.compose.material3:material3-window-size-class")
    // Phase 4.7a: real Material icons (replace emoji glyphs). Pruned by R8 in the
    // Play release build (4.8); harmless in debug.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    // Phase 4.7a: Android 12+ splash screen (with back-compat).
    implementation("androidx.core:core-splashscreen:1.0.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // JVM unit tests only: a real org.json so SnapshotBuilder-format JSON can
    // be exercised without a device (android.jar ships stubs in unit tests).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    // Instrumentation tests only (emulator smoke): drive the real agent loop
    // against an on-device stub model server, plus Compose UI smoke for the
    // settings/privacy/history screens. Never in the shipped APK.
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.03"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
