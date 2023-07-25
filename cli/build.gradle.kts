plugins {
    kotlin("jvm")
    application
}

group = "com.h0tk3y.rally"
version = "1.0"

dependencies {
    implementation(project(":common"))
    implementation(libs.clikt)
}

application {
    mainClass.set("com.h0tk3y.rally.RallyKt")
}