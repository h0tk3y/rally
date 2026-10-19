plugins {
    id("com.android.test")
}

/**
 * End-to-end tests of the app that run on a device or an emulator: `./gradlew :android-e2e:connectedDebugAndroidTest`.
 *
 * They drive the installed app with UiAutomator from a separate APK that instruments itself rather than the app. This way, the tests
 * survive when the app process dies, which is what Android does when a permission of a running app is revoked.
 */
android {
    namespace = "com.h0tk3y.rally.e2e"
    compileSdk = 37
    targetProjectPath = ":android"

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    @Suppress("UnstableApiUsage")
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.androidx.test.core)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
}
