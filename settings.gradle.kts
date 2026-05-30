rootProject.name = "code-navigator"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

includeBuild("test-project")
