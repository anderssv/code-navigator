package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.*

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.types.SourceSet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RingsOrchestratorTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var classesDir: File
    private lateinit var taggedDirs: List<Pair<File, SourceSet>>
    private lateinit var reportFile: File
    private lateinit var projectDir: File

    @BeforeEach
    fun setUp() {
        classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
        taggedDirs = listOf(classesDir to SourceSet.MAIN)
        reportFile = tempDir.resolve("skipped.txt").toFile()
        projectDir = tempDir.resolve("project").toFile().apply { mkdirs() }

        // domain.Controller calls domain.Service (internal) and an external framework class (external).
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/domain/Controller", "Controller.kt",
            "handle",
            listOf(
                Call("com/example/domain/Service", "process", "()V"),
                Call("org/framework/Runtime", "run", "()V"),
            ),
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/domain/Service", "Service.kt")
    }

    @Test
    fun `package mode detects no violations for a simple layering`() {
        val analysis = RingsOrchestrator.run(
            taggedDirs, Scope.ALL, mode = "package", bootstrap = false,
            plan = emptyList(), projectDir = projectDir, reportFile = reportFile,
        )

        val output = (analysis as RingsAnalysis.Package).output
        assertNull(output.skippedFileWarning)
    }

    @Test
    fun `emergent mode splits project vs external dependencies from a single extraction`() {
        val analysis = RingsOrchestrator.run(
            taggedDirs, Scope.ALL, mode = "emergent", bootstrap = false,
            plan = emptyList(), projectDir = projectDir, reportFile = reportFile,
        )

        val output = (analysis as RingsAnalysis.Emergent).output
        assertNull(output.skippedFileWarning)
        assertTrue(ClassName("com.example.domain.Controller") in output.result.classRings)
        assertTrue(ClassName("com.example.domain.Service") in output.result.classRings)
    }

    @Test
    fun `emergent mode plan-file move renames the moved class in the ring assignment`() {
        val plan = listOf(PlanStep.Move(ClassName("com.example.domain.Service"), PackageName("com.example.moved")))

        val analysis = RingsOrchestrator.run(
            taggedDirs, Scope.ALL, mode = "emergent", bootstrap = false,
            plan = plan, projectDir = projectDir, reportFile = reportFile,
        )

        val output = (analysis as RingsAnalysis.Emergent).output
        assertTrue(ClassName("com.example.moved.Service") in output.result.classRings, "Moved class should appear under its new FQCN")
        assertFalse(ClassName("com.example.domain.Service") in output.result.classRings, "Old FQCN should be gone after the simulated move")
    }

    @Test
    fun `emergent mode retains module provenance after a simulated move`() {
        val service = ClassName("com.example.domain.Service")
        val movedService = ClassName("com.example.moved.Service")
        val plan = listOf(PlanStep.Move(service, PackageName("com.example.moved")))

        val analysis = RingsOrchestrator.run(
            taggedDirs, Scope.ALL, mode = "emergent", bootstrap = false,
            plan = plan, projectDir = projectDir, reportFile = reportFile,
            modulesOfClass = mapOf(service to setOf(":core")),
        )

        val output = (analysis as RingsAnalysis.Emergent).output
        assertEquals(setOf(":core"), output.modulesOfClass[movedService])
        assertFalse(service in output.modulesOfClass)
    }

    @Test
    fun `bootstrap mode returns hints config JSON without applying a plan`() {
        val analysis = RingsOrchestrator.run(
            taggedDirs, Scope.ALL, mode = "emergent", bootstrap = true,
            plan = emptyList(), projectDir = projectDir, reportFile = reportFile,
        )

        val json = (analysis as RingsAnalysis.Bootstrap).hintsConfigJson
        assertTrue(json.isNotBlank())
    }
}
