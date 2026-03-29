import org.gradle.plugin.compatibility.compatibility

plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "2.1.1"
    jacoco
}

group = "no.f12"
version = "0.1.44-SNAPSHOT"

gradlePlugin {
    website.set("https://github.com/anderssv/gradle-code-navigator")
    vcsUrl.set("https://github.com/anderssv/gradle-code-navigator")

    plugins {
        create("code-navigator") {
            id = "no.f12.code-navigator"
            displayName = "Code Navigator - Reliable code navigation and analysis for Gradle"
            description = "Provides reliable code navigation, code smell detection, and complexity analysis " +
                "for JVM projects. Includes call graph traversal, dependency analysis, dead code detection, " +
                "and git-history-based metrics. Designed for coding agents and human developers."
            tags.set(listOf("code-navigation", "call-graph", "analysis", "architecture", "code-smells"))
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
    implementation("org.ow2.asm:asm:9.9.1")
    implementation("org.jetbrains.kotlin:kotlin-metadata-jvm:2.2.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(false)
        csv.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("jacocoHtml"))
    }
}

sourceSets {
    main {
        kotlin {
            setSrcDirs(listOf("src/core/kotlin", "src/gradle/kotlin"))
        }
    }
    test {
        kotlin {
            setSrcDirs(listOf("src/test/kotlin", "src/gradleTest/kotlin"))
        }
    }
}
