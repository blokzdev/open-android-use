plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    // Kotlin Compose compiler plugin (replaces the old AGP composeOptions
    // mechanism); its version tracks the Kotlin version. Applied in :app to
    // enable Compose for the presentation layer only; the control surface stays
    // androidx-free. Kotlin bumped to 2.3.x in Phase 5.5b so the LiteRT-LM
    // runtime (compiled with Kotlin 2.3 metadata) can be consumed.
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
}
