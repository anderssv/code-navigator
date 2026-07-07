package no.f12.codenavigator.navigation.context

import no.f12.codenavigator.navigation.*

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.types.SourceSet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextOrchestratorTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var classesDir: File
    private lateinit var taggedDirs: List<Pair<File, SourceSet>>
    private lateinit var cacheDir: File

    private fun config(pattern: String) = ContextConfig(
        pattern = pattern,
        maxDepth = 3,
        projectOnly = false,
        filterSynthetic = false,
        scope = Scope.ALL,
        format = OutputFormat.TEXT,
    )

    @BeforeEach
    fun setUp() {
        classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
        taggedDirs = listOf(classesDir to SourceSet.MAIN)
        cacheDir = tempDir.resolve("cnav").toFile()

        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Controller", "Controller.kt",
            "handle", listOf(Call("com/example/Service", "process", "()V")),
        )
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Service", "Service.kt",
            "process", emptyList(),
        )
    }

    @Test
    fun `gathers class detail, callers, and callees for a matching class`() {
        val output = ContextOrchestrator.run(config("Service"), taggedDirs, cacheDir)

        assertEquals(1, output.results.size)
        val result = output.results[0]
        assertEquals("com.example.Service", result.classDetail.className.value)
        val processRoot = result.callers.first { it.method.qualifiedName == "com.example.Service.process" }
        assertTrue(processRoot.children.any { it.method.qualifiedName == "com.example.Controller.handle" }, "Should find Controller as a caller of Service.process")
    }

    @Test
    fun `returns empty results when pattern matches no classes`() {
        val output = ContextOrchestrator.run(config("NoSuchClassXYZ"), taggedDirs, cacheDir)

        assertTrue(output.results.isEmpty())
    }

    @Test
    fun `no skipped file warnings for well-formed classes`() {
        val output = ContextOrchestrator.run(config("Service"), taggedDirs, cacheDir)

        assertTrue(output.skippedFileWarnings.isEmpty())
    }
}
