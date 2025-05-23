// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.22" apply false
}

// Add Maven repositories for Microsoft dependencies
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://pkgs.dev.azure.com/MicrosoftDeviceSDK/DuoSDK-Public/_packaging/Duo-SDK-Feed/maven/v1") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
    }
}