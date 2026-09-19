package no.f12.codenavigator.navigation.metrics

import no.f12.codenavigator.navigation.Call
import no.f12.codenavigator.navigation.TestClassWriter
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.types.SourceSet
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Same shape as the `cnavPackageDeps --scope` bug: the task narrowed its class directories by scope
 * but read a cache file that full-project tasks also write, and CacheFreshness only compares
 * timestamps.
 */
class MetricsScopeCacheTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `a prod-scoped run is not poisoned by an all-scope call graph cache`() {
        val mainDir = tempDir.resolve("main").toFile().also { it.mkdirs() }
        val testDir = tempDir.resolve("test").toFile().also { it.mkdirs() }
        val cacheDir = tempDir.resolve("cnav").toFile().also { it.mkdirs() }
        TestClassWriter.writeClassWithCalls(mainDir, "com/example/polls/Service", "Service.kt", "process", emptyList())
        TestClassWriter.writeClassWithCalls(
            testDir, "com/example/fakes/FakeService", "FakeService.kt",
            "process", listOf(Call("com/example/polls/Service", "process", "()V")),
        )
        val taggedDirs = listOf(mainDir to SourceSet.MAIN, testDir to SourceSet.TEST)

        // Another task writes the full tagged graph to the shared cache file first.
        CallGraphCache.getOrBuildTagged(CallGraphCache.cacheFileFor(cacheDir, Scope.ALL), taggedDirs)

        val scopedDirs = taggedDirs.filter { Scope.PROD.matchesSourceSet(it.second) }
        val graph = CallGraphCache.getOrBuildTagged(CallGraphCache.cacheFileFor(cacheDir, Scope.PROD), scopedDirs).data

        assertFalse(
            graph.projectClasses().any { it.value.contains("fakes") },
            "test-only class leaked into the prod-scoped graph: ${graph.projectClasses()}",
        )
    }

    @Test
    fun `each scope keeps its own cache file so switching scope does not thrash`() {
        val cacheDir = tempDir.resolve("cnav").toFile()

        assertFalse(
            CallGraphCache.cacheFileFor(cacheDir, Scope.PROD) == CallGraphCache.cacheFileFor(cacheDir, Scope.ALL),
        )
        assertFalse(
            CallGraphCache.cacheFileFor(cacheDir, Scope.PROD) == CallGraphCache.cacheFileFor(cacheDir, Scope.TEST),
        )
    }
}
