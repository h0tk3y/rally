@Suppress("DSL_SCOPE_VIOLATION") // https://youtrack.jetbrains.com/issue/KTIJ-19369
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

group = "com.h0tk3y.rally"
version = "1.0"

dependencies {
    implementation(libs.clikt)
}

application {
    mainClass.set("com.h0tk3y.rally.RallyKt")
}