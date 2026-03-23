plugins {
    kotlin("jvm") version "2.1.20"
    id("no.f12.code-navigator") version "0.1.9-SNAPSHOT"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
