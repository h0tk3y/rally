pluginManagement {
    fun RepositoryHandler.setup() {
        mavenCentral()
        google()
        if (this == pluginManagement.repositories) {
            gradlePluginPortal()
        }
    }
    repositories.setup()
    dependencyResolutionManagement {
        repositories.setup()
        versionCatalogs {
            create("libs") {
                library("clikt", "com.github.ajalt.clikt:clikt:3.5.0")
            }
        }
    }

    plugins {
        kotlin("jvm").version("1.9.0")
        kotlin("multiplatform").version(extra["kotlin.version"] as String)
        kotlin("android").version(extra["kotlin.version"] as String)
        kotlin("plugin.compose").version(extra["kotlin.version"] as String)
        id("com.android.application").version(extra["agp.version"] as String)
        id("com.android.library").version(extra["agp.version"] as String)
        id("org.jetbrains.compose").version(extra["compose.version"] as String)
        id("app.cash.sqldelight") version "2.0.0-alpha05"
    }
}

include(":cli")
include(":common")
include(":android")