plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Kotlin 2.0 Compose compiler plugin (replaces the old AGP composeOptions
    // mechanism). Applied in :app to enable Compose for the presentation layer
    // only; the control surface stays androidx-free.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
