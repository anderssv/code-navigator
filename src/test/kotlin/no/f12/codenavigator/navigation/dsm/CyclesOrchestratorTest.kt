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
    fun `retains module provenance for packages in a cross-module cycle`() {
        val controller = ClassName("com.example.api.Controller")
        val service = ClassName("com.example.service.Service")

        val output = CyclesOrchestrator.run(
            config(),
            taggedDirs,
            emptyList(),
            reportFile,
            modulesOfClass = mapOf(controller to setOf(":web"), service to setOf(":service")),
        )

        assertEquals(setOf(":web"), output.moduleLabels[PackageName("api")])
        assertEquals(setOf(":service"), output.moduleLabels[PackageName("service")])
    }

    @Test
    fun `simulated move via plan-file can break the cycle`() {
        // Move Service into the api package — both classes now share a package, so the
        // cross-package cycle disappears (PlanMutator drops edges landing in the same package).
        val plan = listOf(PlanStep.Move(ClassName("com.example.service.Service"), PackageName("com.example.api")))

        val output = CyclesOrchestrator.run(config(), taggedDirs, plan, reportFile)

        assertTrue(output.details.isEmpty(), "Expected the simulated move to eliminate the cycle")
    }

    @Test
    fun `plan-file move drops only the newly same-package edge, not unrelated cross-package edges`() {
        // A third class in its own package still calls Service. After moving Service into
        // Controller's package, dropSamePackageEdges=true (the default used by cnavCycles/cnavDsm)
        // must only drop the edge that became intra-package (api<->service) — not this unrelated one.
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/report/Reporter", "Reporter.kt",
            "summarize", listOf(Call("com/example/service/Service", "process", "()V")),
        )
        val plan = listOf(PlanStep.Move(ClassName("com.example.service.Service"), PackageName("com.example.api")))

        val dsmConfig = DsmConfig(
            rootPackage = PackageName(""),
            packageFilter = null,
            includeExternal = false,
            depth = 2,
            htmlPath = null,
            format = no.f12.codenavigator.config.OutputFormat.TEXT,
            cyclesOnly = false,
            cycleFilter = null,
            scope = Scope.ALL,
        )
        val dsmOutput = DsmOrchestrator.run(dsmConfig, taggedDirs, plan, reportFile)

        // Cell keys are truncated relative to the auto-detected root prefix ("com.example"),
        // so "com.example.report" -> "com.example.api" shows up as "report" -> "api".
        assertTrue(
            dsmOutput.matrix.cells.containsKey(PackageName("report") to PackageName("api")),
            "Reporter -> (moved) Service edge should survive — it was cross-package before and after the move",
        )
        assertTrue(
            dsmOutput.matrix.cells.keys.none { (from, to) -> from == PackageName("api") && to == PackageName("api") },
            "Controller <-> Service should not appear as a same-package cell once Service moves into api",
        )

        val cyclesOutput = CyclesOrchestrator.run(config(), taggedDirs, plan, reportFile)
        assertTrue(cyclesOutput.details.isEmpty(), "Cycle should still be eliminated")
    }
}
