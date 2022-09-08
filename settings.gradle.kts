pluginManagement {
    fun RepositoryHandler.setup() {
        mavenCentral()
        if (this == pluginManagement.repositories) {
            gradlePluginPortal()
        }
    }
    repositories.setup()
    dependencyResolutionManagement {
        repositories.setup()
        versionCatalogs {
            create("libs") {
                val kotlinVersion = version("kotlin", "1.7.10")
                plugin("kotlin.jvm", "org.jetbrains.kotlin.jvm").versionRef(kotlinVersion)
                plugin("kotlin.multiplatform", "org.jetbrains.kotlin.multiplatform").versionRef(kotlinVersion)
                
                library("clikt", "com.github.ajalt.clikt:clikt:3.5.0")
            }
        }
    }

    plugins {
        kotlin("jvm").version("1.7.10")
    }
}