package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.Call
import no.f12.codenavigator.navigation.TestClassWriter
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
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
import kotlin.test.assertTrue

class PackageDepsOrchestratorTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var mainDir: File
    private lateinit var testDir: File
    private lateinit var cacheDir: File
    private lateinit var reportFile: File
    private lateinit var taggedDirs: List<Pair<File, SourceSet>>

    private fun config(scope: Scope, packagePattern: String? = null) = PackageDepsConfig(
        packagePattern = packagePattern,
        projectOnly = true,
        reverse = false,
        scope = scope,
        format = OutputFormat.LLM,
    )

    @BeforeEach
    fun setUp() {
        mainDir = tempDir.resolve("main").toFile().also { it.mkdirs() }
        testDir = tempDir.resolve("test").toFile().also { it.mkdirs() }
        cacheDir = tempDir.resolve("cnav").toFile().also { it.mkdirs() }
        reportFile = File(cacheDir, "skipped.txt")
        taggedDirs = listOf(mainDir to SourceSet.MAIN, testDir to SourceSet.TEST)

        TestClassWriter.writeClassWithCalls(
            mainDir, "com/example/web/Routes", "Routes.kt",
            "handle", listOf(Call("com/example/polls/Service", "process", "()V")),
        )
        TestClassWriter.writeClassWithCalls(
            mainDir, "com/example/polls/Service", "Service.kt", "process", emptyList(),
        )
        // Test-only source set: fakes is called from a test, and calls into production.
        TestClassWriter.writeClassWithCalls(
            testDir, "com/example/fakes/FakeService", "FakeService.kt",
            "process", listOf(Call("com/example/polls/Service", "process", "()V")),
        )
        TestClassWriter.writeClassWithCalls(
            testDir, "com/example/polls/ServiceTest", "ServiceTest.kt",
            "test", listOf(Call("com/example/fakes/FakeService", "process", "()V")),
        )
    }

    @Test
    fun `scope=prod excludes test-only packages`() {
        val output = PackageDepsOrchestrator.run(config(Scope.PROD), taggedDirs, cacheDir, reportFile)

        assertFalse(output.packages.contains(PackageName("com.example.fakes")), "test-only package leaked into prod scope")
        assertTrue(output.packages.contains(PackageName("com.example.web")))
        assertEquals(emptyList(), output.deps.dependenciesOf(PackageName("com.example.polls")))
    }

    @Test
    fun `scope=prod is not poisoned by an all-scope call graph cache`() {
        // Another task (cnavRank, cnavComplexity, ...) writes the full tagged graph to the shared
        // cache file first. CacheFreshness only compares timestamps, so a scope-limited run used to
        // read that wider graph straight back and report test packages as production dependencies.
        CallGraphCache.getOrBuildTagged(CallGraphCache.cacheFileFor(cacheDir, Scope.ALL), taggedDirs)

        val output = PackageDepsOrchestrator.run(config(Scope.PROD), taggedDirs, cacheDir, reportFile)

        assertFalse(output.packages.contains(PackageName("com.example.fakes")), "test-only package leaked into prod scope")
        assertFalse(
            output.deps.dependenciesOf(PackageName("com.example.polls")).contains(PackageName("com.example.fakes")),
            "prod -> test edge leaked into prod scope",
        )
    }

    @Test
    fun `scope=all keeps test packages`() {
        val output = PackageDepsOrchestrator.run(config(Scope.ALL), taggedDirs, cacheDir, reportFile)

        assertTrue(output.packages.contains(PackageName("com.example.fakes")))
    }

    @Test
    fun `reports an empty message when the package pattern matches nothing`() {
        val output = PackageDepsOrchestrator.run(config(Scope.ALL, packagePattern = "nope"), taggedDirs, cacheDir, reportFile)

        assertTrue(output.packages.isEmpty())
        assertEquals("No packages found matching 'nope'", output.emptyMessage)
    }
}
