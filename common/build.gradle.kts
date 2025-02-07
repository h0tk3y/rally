plugins {
    kotlin("jvm")
}

kotlin.jvmToolchain(11)

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    
    testImplementation(kotlin("test"))
}