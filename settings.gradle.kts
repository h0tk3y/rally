pluginManagement {
    fun RepositoryHandler.setup() {
        mavenCentral()
        google()
        maven("https://jitpack.io")
        if (this == pluginManagement.repositories) {
            gradlePluginPortal()
        }
    }
    repositories.setup()
    dependencyResolutionManagement {
        @Suppress("UnstableApiUsage")
        repositories.setup()
        versionCatalogs {
            create("libs") {
                library("clikt", "com.github.ajalt.clikt:clikt:3.5.0")
            }
        }
    }

    plugins {
        kotlin("jvm").version(extra["kotlin.version"] as String)
        kotlin("multiplatform").version(extra["kotlin.version"] as String)
        kotlin("android").version(extra["kotlin.version"] as String)
        kotlin("plugin.compose").version(extra["kotlin.version"] as String)
        kotlin("plugin.serialization").version(extra["kotlin.version"] as String)
        kotlin("plugin.power-assert").version(extra["kotlin.version"] as String)
        id("com.android.application").version(extra["agp.version"] as String)
        id("com.android.library").version(extra["agp.version"] as String)
        id("org.jetbrains.compose").version(extra["compose.version"] as String)
        id("app.cash.sqldelight") version "2.1.0"
        id("org.jetbrains.kotlin.android")
    }
}

include(":cli")
include(":common")
include(":android")