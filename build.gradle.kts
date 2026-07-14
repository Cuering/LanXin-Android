// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.hilt) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.auto.license) apply false
    alias(libs.plugins.objectbox) apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21" apply false
}