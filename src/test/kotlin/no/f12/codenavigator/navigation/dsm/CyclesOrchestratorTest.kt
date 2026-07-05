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
import kotlin.test.assertTrue

class CyclesOrchestratorTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var classesDir: File
    private lateinit var taggedDirs: List<Pair<File, SourceSet>>
    private lateinit var reportFile: File

    private fun config(maxCycles: Int = 0, failOnViolation: Boolean = false) = CyclesConfig(
        rootPackage = PackageName(""),
        packageFilter = null,
        includeExternal = false,
        depth = 2,
        scope = Scope.ALL,
        format = no.f12.codenavigator.config.OutputFormat.TEXT,
        failOnViolation = failOnViolation,
        maxCycles = maxCycles,
    )

    @BeforeEach
    fun setUp() {
        classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
        taggedDirs = listOf(classesDir to SourceSet.MAIN)
        reportFile = tempDir.resolve("skipped.txt").toFile()

        // com.example.api.Controller <-> com.example.service.Service form a package cycle
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/example/service/Service", "process", "()V")),
        )
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/service/Service", "Service.kt",
            "process", listOf(Call("com/example/api/Controller", "handle", "()V")),
        )
    }

    @Test
    fun `detects a package cycle with no plan`() {
        val output = CyclesOrchestrator.run(config(), taggedDirs, emptyList(), reportFile)

        assertEquals(1, output.details.size)
    }

    @Test
    fun `simulated move via plan-file can break the cycle`() {
        // Move Service into the api package — both classes now share a package, so the
        // cross-package cycle disappears (PlanMutator drops edges landing in the same package).
        val plan = listOf(PlanStep.Move(ClassName("com.example.service.Service"), PackageName("com.example.api")))

        val output = CyclesOrchestrator.run(config(), taggedDirs, plan, reportFile)

        assertTrue(output.details.isEmpty(), "Expected the simulated move to eliminate the cycle")
    }
}
