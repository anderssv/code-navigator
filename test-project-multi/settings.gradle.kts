pluginManagement {
    includeBuild("..")
    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "cnav-multi-fixture"
include("shared", "service", "unrelated")
