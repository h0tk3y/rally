import org.apache.tools.ant.filters.StringInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    kotlin("plugin.power-assert")
    id("org.jetbrains.compose")
    id("app.cash.sqldelight")
}

group = "com.h0tk3y.flashcards"
version = providers.gradleProperty("releaseVersion").getOrElse("0.1.1-SNAPSHOT")

dependencies {
    implementation(project(":common"))

    implementation(compose.ui)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material.icons.extended.android)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coroutines.extensions1)
    implementation(libs.better.parse)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.android.driver)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material3.window.size.class1)

    implementation(libs.androidx.activity.compose)
    implementation(libs.accompanist.webview)
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.accompanist.insets)

    implementation(libs.obd.java.api)

    implementation(libs.androidx.datastore.preferences)
    
    testImplementation(libs.mockk)
    testImplementation(libs.mockk.agent)
    testImplementation(libs.mockk.android)
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.jetbrains.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

sqldelight {
    databases.create("AppDatabase") {
        packageName.set("com.h0tk3y.rally.db")
        schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
    }
}

kotlin {
    sourceSets.all { 
        languageSettings { 
            optIn("kotlin.time.ExperimentalTime")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xopt-in=kotlin.time.ExperimentalTime")
    }
}

android {
    namespace = "com.h0tk3y.rally"
    compileSdk = 37
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            val keystoreProperties = Properties().apply {
                load(
                    keystorePropertiesFile.takeIf { it.exists() }?.inputStream()
                        ?: StringInputStream(
                            "keyFile=../keystore.jks\n" +
                                    "storePassword=${System.getenv("KEYSTORE_PASSWORD")}\n" +
                                    "keyPassword=${System.getenv("KEY_PASSWORD")}\n" +
                                    "keyAlias=${System.getenv("KEY_ALIAS")}\n"
                        )
                )
            }

            storeFile = file(keystoreProperties["keyFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
    }
    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
    defaultConfig {
        minSdk = 26
        targetSdk = 35
        versionCode = 100
        versionName = project.version.toString()
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
//    applicationVariants.all {
//        val variantName = name
//        outputs.all {
//            if (this is ApkVariantOutput) {
//                this.outputFileName = "com.h0tk3y.rally.${variantName}.apk"
//            }
//        }
//    }
}

kotlin {
    jvmToolchain(11)
}