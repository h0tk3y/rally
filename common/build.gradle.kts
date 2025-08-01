plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin.jvmToolchain(11)

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test"))
}