import org.gradle.plugin.compatibility.compatibility

plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "2.1.0"
}

group = "no.f12"
version = "0.1.13"

gradlePlugin {
    website.set("https://github.com/anderssv/gradle-code-navigator")
    vcsUrl.set("https://github.com/anderssv/gradle-code-navigator")

    plugins {
        create("code-navigator") {
            id = "no.f12.code-navigator"
            displayName = "Code Navigator - Bytecode-level code exploration for Gradle"
            description = "Analyzes compiled JVM bytecode to provide class listing, symbol search, " +
                "call graph traversal, class detail inspection, interface implementation lookup, " +
                "and package dependency analysis. Designed for coding agents and human developers."
            tags.set(listOf("code-navigation", "bytecode", "call-graph", "analysis", "architecture"))
            implementationClass = "no.f12.codenavigator.gradle.CodeNavigatorPlugin"
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm:9.7.1")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

sourceSets {
    main {
        kotlin {
            setSrcDirs(listOf("src/core/kotlin", "src/gradle/kotlin"))
        }
    }
}
