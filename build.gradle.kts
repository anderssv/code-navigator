import org.gradle.plugin.compatibility.compatibility

plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "2.1.1"
    jacoco
}

group = "no.f12"
version = "0.1.123"

gradlePlugin {
    website.set("https://github.com/anderssv/code-navigator")
    vcsUrl.set("https://github.com/anderssv/code-navigator")

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
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.0")
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.0")
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
    // Tests tagged "maven" run real mvn against test-project-maven and need network and a few minutes: see mavenTest.
    useJUnitPlatform { excludeTags("maven") }
    dependsOn(gradle.includedBuild("test-project").task(":compileKotlin"))
    finalizedBy(tasks.jacocoTestReport)
}

// Installs this build of the Maven plugin into the local Maven repository so mavenTest can run its goals the way a
// user does, as separate `mvn <goal>` invocations against a project that doesn't declare the plugin.
val installMavenPlugin by tasks.registering(Exec::class) {
    group = "verification"
    description = "Installs the Maven plugin (skipping tests) into ~/.m2 for mavenTest"
    workingDir = projectDir
    environment("JAVA_HOME", System.getProperty("java.home"))
    commandLine("./mvnw", "-q", "install", "-DskipTests")
}

// Runs the real Maven goals against test-project-maven. Separate from `test`: it needs network on first use and
// takes minutes. Run with `./gradlew mavenTest`.
val mavenTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs the Maven goals end to end against test-project-maven (needs network the first time)"
    dependsOn(installMavenPlugin)
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("maven") }
    systemProperty("cnav.repoRoot", projectDir.absolutePath)
    systemProperty("cnav.version", project.version.toString())
    outputs.upToDateWhen { false }
    testLogging { showStandardStreams = false; events("passed", "failed", "skipped") }
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
