package no.f12.codenavigator.navigation.relations.callgraph

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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallTreeOrchestratorTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var classesDir: File
    private lateinit var taggedDirs: List<Pair<File, SourceSet>>
    private lateinit var cacheDir: File

    private fun config(method: String) = CallGraphConfig(
        method = method,
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
        TestClassWriter.writeClassFile(classesDir, "com/example/Service", "Service.kt")
    }

    @Test
    fun `finds callers of a method`() {
        val output = CallTreeOrchestrator.run(config("Service.process"), taggedDirs, cacheDir, CallDirection.CALLERS)

        assertEquals(1, output.trees.size)
        assertEquals("com.example.Service.process", output.trees[0].method.qualifiedName)
        assertTrue(output.trees[0].children.any { it.method.qualifiedName == "com.example.Controller.handle" })
    }

    @Test
    fun `finds callees of a method`() {
        val output = CallTreeOrchestrator.run(config("Controller.handle"), taggedDirs, cacheDir, CallDirection.CALLEES)

        assertEquals(1, output.trees.size)
        assertEquals("com.example.Controller.handle", output.trees[0].method.qualifiedName)
        assertTrue(output.trees[0].children.any { it.method.qualifiedName == "com.example.Service.process" })
    }

    @Test
    fun `returns empty trees and no class hint when pattern matches nothing`() {
        val output = CallTreeOrchestrator.run(config("NoSuchMethod"), taggedDirs, cacheDir, CallDirection.CALLERS)

        assertTrue(output.trees.isEmpty())
        assertNull(output.classHint)
    }

    @Test
    fun `no skipped files for well-formed classes`() {
        val output = CallTreeOrchestrator.run(config("Service.process"), taggedDirs, cacheDir, CallDirection.CALLERS)

        assertNull(output.skippedFileWarning)
    }

    @Test
    fun `finds a lambda-body caller even with filterSynthetic enabled`() {
        // A DSL-block body (e.g. route("/x") { target() }) compiles to a $lambda$-named method —
        // it is a real call site, not synthetic noise, and must survive filterSynthetic=true
        // when resolving CALLERS.
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/SetupKt", "Setup.kt",
            "registerRoutes\$lambda\$0\$0", listOf(Call("com/example/Service", "process", "()V")),
        )
        val filteredConfig = config("Service.process").copy(filterSynthetic = true)

        val output = CallTreeOrchestrator.run(filteredConfig, taggedDirs, cacheDir, CallDirection.CALLERS)

        assertEquals(1, output.trees.size)
        assertTrue(
            output.trees[0].children.any { it.method.qualifiedName == "com.example.SetupKt.registerRoutes\$lambda\$0\$0" },
            "expected the lambda-body caller to survive filterSynthetic, got children: ${output.trees[0].children.map { it.method.qualifiedName }}",
        )
    }

    @Test
    fun `still filters a lambda-named method as noise when resolving CALLEES`() {
        // The orchestrator must pass the actual traversal direction into buildFilter — CALLEES
        // resolution should keep the existing lambda-as-noise behavior (opposite of the CALLERS case above).
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Controller2", "Controller2.kt",
            "handle", listOf(Call("com/example/Service", "process\$lambda\$0", "()V")),
        )
        val filteredConfig = config("Controller2.handle").copy(filterSynthetic = true)

        val output = CallTreeOrchestrator.run(filteredConfig, taggedDirs, cacheDir, CallDirection.CALLEES)

        assertEquals(1, output.trees.size)
        assertTrue(
            output.trees[0].children.none { it.method.qualifiedName == "com.example.Service.process\$lambda\$0" },
            "expected the lambda-named callee to still be filtered as noise, got children: ${output.trees[0].children.map { it.method.qualifiedName }}",
        )
    }
}
