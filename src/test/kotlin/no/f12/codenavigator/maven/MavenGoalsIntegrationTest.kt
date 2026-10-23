package no.f12.codenavigator.maven

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Runs the real Maven goals the way a user does: as separate `mvn <goal>` invocations against a Kotlin Maven
 * project (test-project-maven) that does not declare the plugin. Run with `./gradlew mavenTest`, which first
 * installs this build of the plugin into the local Maven repository.
 *
 * The goals' unit tests can't catch what these do: how a mojo turns a MavenProject into source roots, and
 * whether the plugin's own classpath is complete. Both were broken (every refactoring goal reported "No changes
 * needed" in Kotlin projects) with a fully green unit suite.
 */
@Tag("maven")
class MavenGoalsIntegrationTest {

    @TempDir
    lateinit var workDir: File

    private val repoRoot = File(System.getProperty("cnav.repoRoot") ?: error("cnav.repoRoot not set: run ./gradlew mavenTest"))
    private val version = System.getProperty("cnav.version") ?: error("cnav.version not set: run ./gradlew mavenTest")

    private data class Run(val exitCode: Int, val output: String)

    private fun freshProject(): File {
        val copy = File(workDir, "project-${System.nanoTime()}")
        val source = File(repoRoot, "test-project-maven")
        source.walkTopDown()
            .filter { it.isFile && !it.relativeTo(source).path.startsWith("target") }
            .forEach { file -> file.copyTo(File(copy, file.relativeTo(source).path)) }
        return copy
    }

    private fun mvn(pom: File, vararg args: String): Run {
        val process = ProcessBuilder(listOf(File(repoRoot, "mvnw").path, "-q", "-f", pom.path) + args)
            .directory(repoRoot)
            .redirectErrorStream(true)
            .also { it.environment()["JAVA_HOME"] = System.getProperty("java.home") }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(10, TimeUnit.MINUTES)) { "mvn timed out: ${args.toList()}" }
        return Run(process.exitValue(), output)
    }

    private fun goal(name: String) = "no.f12:code-navigator-maven-plugin:$version:$name"

    private fun compile(pom: File) {
        val run = mvn(pom, "compile")
        assertEquals(0, run.exitCode, "fixture failed to compile:\n${run.output}")
    }

    private val moveAuditService = arrayOf(
        "-Dfrom=com.example.services.AuditService",
        "-Dto=com.example.domain.AuditService",
    )

    @Test
    fun `move-class previews the move in a Kotlin project whose sources are only declared on kotlin-maven-plugin`() {
        val project = freshProject()
        compile(File(project, "pom.xml"))

        val run = mvn(File(project, "pom.xml"), goal("move-class"), *moveAuditService, "-Dpreview")

        assertEquals(0, run.exitCode, run.output)
        assertFalse(run.output.contains("No changes needed"), "reported success for a scan that found nothing:\n${run.output}")
        assertTrue(run.output.contains("Preview: move com.example.services.AuditService"), run.output)
        assertTrue(run.output.contains("+ package com.example.domain"), run.output)
        assertTrue(File(project, "src/main/kotlin/com/example/services/AuditService.kt").exists(), "preview must not touch the disk")
    }

    @Test
    fun `move-class applies the move, rewrites the caller's import, and the project still compiles`() {
        val project = freshProject()
        val pom = File(project, "pom.xml")
        compile(pom)

        val run = mvn(pom, goal("move-class"), *moveAuditService)

        assertEquals(0, run.exitCode, run.output)
        assertTrue(run.output.contains("Applied: move"), run.output)
        assertFalse(File(project, "src/main/kotlin/com/example/services/AuditService.kt").exists())
        assertTrue(File(project, "src/main/kotlin/com/example/domain/AuditService.kt").readText().contains("package com.example.domain"))
        val caller = File(project, "src/main/kotlin/com/example/routes/UserRoute.kt").readText()
        assertTrue(caller.contains("import com.example.domain.AuditService"), caller)
        assertFalse(caller.contains("import com.example.services.AuditService"), caller)

        compile(pom)
    }

    @Test
    fun `move-class works when sourceDirs is configured on the plugin rather than on an execution`() {
        val project = freshProject()
        val pom = File(project, "pom-plugin-level.xml")
        compile(pom)

        val run = mvn(pom, goal("move-class"), *moveAuditService, "-Dpreview")

        assertTrue(run.output.contains("Preview: move com.example.services.AuditService"), run.output)
    }

    @Test
    fun `move-file works against the same project`() {
        val project = freshProject()
        compile(File(project, "pom.xml"))

        val run = mvn(
            File(project, "pom.xml"), goal("move-file"),
            "-Dfrom-file=src/main/kotlin/com/example/services/AuditService.kt", "-Dto-package=com.example.domain", "-Dpreview",
        )

        assertTrue(run.output.contains("Preview: move-file"), run.output)
        assertFalse(run.output.contains("No changes needed"), run.output)
    }

    @Test
    fun `a class that does not exist is reported as not found with the number of files scanned`() {
        val project = freshProject()
        compile(File(project, "pom.xml"))

        val run = mvn(File(project, "pom.xml"), goal("move-class"), "-Dfrom=com.example.services.Nope", "-Dto=com.example.domain.Nope", "-Dpreview")

        assertFalse(run.output.contains("No changes needed"), run.output)
        assertTrue(run.output.contains("com.example.services.Nope was not found in the"), run.output)
        assertTrue(Regex("""\d+ Kotlin source file""").containsMatchIn(run.output), run.output)
    }

    @Test
    fun `size sees the Kotlin sources too`() {
        val project = freshProject()
        compile(File(project, "pom.xml"))

        val run = mvn(File(project, "pom.xml"), goal("size"), "-Dover=1")

        assertTrue(run.output.contains("UserService.kt"), run.output)
    }
}
