// Top-level build file — plugin declarations only (apply false keeps them off the root project).
// All dependency versions live in gradle/libs.versions.toml.
// Repository declarations are in settings.gradle.kts (dependencyResolutionManagement).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics.gradle) apply false
}
