// Root build file. AGP 9.x enables "built-in Kotlin", so no `org.jetbrains.kotlin.android`
// plugin is declared here. The Compose Compiler plugin is still required for Compose modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
}