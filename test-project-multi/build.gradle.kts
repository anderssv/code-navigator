plugins {
    java
    id("no.f12.code-navigator")
}

allprojects {
    repositories { mavenCentral() }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "no.f12.code-navigator")
}
