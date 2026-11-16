// Top-level build file. AGP 9 compiles Kotlin itself (built-in Kotlin), so only
// compiler plugins are declared here.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}