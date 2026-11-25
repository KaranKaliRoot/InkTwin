pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.9.0"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
        id("org.jetbrains.kotlin.kapt") version "2.0.21"
        id("com.google.dagger.hilt.android") version "2.51.1"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "InkTwin"
include(":app")