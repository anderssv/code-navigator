package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.Call
import no.f12.codenavigator.navigation.TestClassWriter
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.types.SourceSet
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DsmOrchestratorTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var classesDir: File
    private lateinit var reportFile: File

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        classesDir = tempDir.resolve("classes").toFile().apply { mkdirs() }
        reportFile = tempDir.resolve("skipped.txt").toFile()

        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/example/service/Service", "process", "()V")),
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/service/Service", "Service.kt")
    }

    @Test
    fun `moduleLabels is empty when moduleOfClass is not provided`() {
        val config = DsmConfig.parse(emptyMap())
        val taggedDirs = listOf(classesDir to SourceSet.MAIN)

        val output = DsmOrchestrator.run(config, taggedDirs, emptyList(), reportFile)

        assertTrue(output.moduleLabels.isEmpty())
    }

    @Test
    fun `moduleLabels tags each displayed package with its module`() {
        val config = DsmConfig.parse(emptyMap())
        val taggedDirs = listOf(classesDir to SourceSet.MAIN)
        val moduleOfClass = mapOf(
            ClassName("com.example.api.Controller") to setOf(":web"),
            ClassName("com.example.service.Service") to setOf(":service"),
        )

        val output = DsmOrchestrator.run(config, taggedDirs, emptyList(), reportFile, moduleOfClass)

        assertEquals(setOf(":web"), output.moduleLabels[PackageName("api")])
        assertEquals(setOf(":service"), output.moduleLabels[PackageName("service")])
    }

    @Test
    fun `moduleLabels merges modules when a package spans more than one`() {
        val config = DsmConfig.parse(emptyMap())
        val taggedDirs = listOf(classesDir to SourceSet.MAIN)
        // Both classes are in the "api" package's own package after truncation is a no-op here;
        // simulate ambiguity by mapping both to different modules for the SAME package instead.
        TestClassWriter.writeClassFile(classesDir, "com/example/api/Filter", "Filter.kt")
        val moduleOfClass = mapOf(
            ClassName("com.example.api.Controller") to setOf(":web"),
            ClassName("com.example.api.Filter") to setOf(":gateway"),
            ClassName("com.example.service.Service") to setOf(":service"),
        )

        val output = DsmOrchestrator.run(config, taggedDirs, emptyList(), reportFile, moduleOfClass)

        assertEquals(setOf(":web", ":gateway"), output.moduleLabels[PackageName("api")])
    }
}
