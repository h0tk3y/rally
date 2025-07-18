import com.android.build.gradle.api.ApkVariantOutput
import org.apache.tools.ant.filters.StringInputStream
import java.util.Properties

plugins {
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")

    id("org.jetbrains.compose")
    id("com.android.application")
    id("app.cash.sqldelight")
}

group = "com.h0tk3y.flashcards"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":common"))

    implementation(compose.ui)
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation("androidx.compose.material:material-icons-extended:1.7.6")
    implementation("androidx.compose.material:material-icons-extended-android:1.7.6")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("app.cash.sqldelight:coroutines-extensions:2.0.0-alpha05")
    implementation("com.github.h0tk3y.betterParse:better-parse:0.4.4")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("app.cash.sqldelight:android-driver:2.0.0-alpha05")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation(project(":common"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("com.google.accompanist:accompanist-webview:0.31.2-alpha")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.27.0")
    implementation("com.google.accompanist:accompanist-insets:0.27.0")

    implementation("com.github.pires:obd-java-api:1.0")

    implementation("androidx.datastore:datastore-preferences:1.1.2")
}

sqldelight {
    databases.create("AppDatabase") {
        packageName.set("com.h0tk3y.rally.db")
    }
}

android {
    namespace = "com.h0tk3y.rally"
    compileSdk = 35
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            val keystoreProperties = Properties().apply {
                load(
                    keystorePropertiesFile.takeIf { it.exists() }?.inputStream()
                        ?: StringInputStream(
                            "keyFile=/Users/sergey.igushkin/keys/release-local\nstorePassword=mypass\nkeyPassword=mypass\nkeyAlias=key0\n"
                        )
                )
            }

            storeFile = file(keystoreProperties["keyFile"] as String)
            storePassword = keystoreProperties["storePassword"] as String
            keyAlias = keystoreProperties["keyAlias"] as String
            keyPassword = keystoreProperties["keyPassword"] as String
        }
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
        versionName = "0.1.0"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        jvmToolchain(11)
    }

    applicationVariants.all {
        val variantName = name
        outputs.all {
            if (this is ApkVariantOutput) {
                this.outputFileName = "com.h0tk3y.rally.${variantName}.apk"
            }
        }
    }
}