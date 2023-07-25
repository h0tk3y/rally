plugins {
    kotlin("android")
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
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
    implementation("app.cash.sqldelight:coroutines-extensions:2.0.0-alpha05")
    implementation("com.github.h0tk3y.betterParse:better-parse:0.4.4")
    implementation("moe.tlaster:precompose:1.4.1")
    implementation("moe.tlaster:precompose-viewmodel:1.4.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("app.cash.sqldelight:android-driver:2.0.0-alpha05")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")

    implementation(project(":common"))
    implementation("androidx.activity:activity-compose:1.7.2")
    implementation("com.google.accompanist:accompanist-webview:0.31.2-alpha")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.27.0")
    implementation("com.google.accompanist:accompanist-insets:0.27.0")
}

sqldelight {
    databases.create("AppDatabase") {
        packageName.set("com.h0tk3y.rally.db")
    }
}

android {
    namespace = "com.h0tk3y.rally"
    compileSdk = 33
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = 24
        targetSdk = 33
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
}