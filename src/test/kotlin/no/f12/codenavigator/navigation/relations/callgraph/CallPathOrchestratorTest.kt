package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.navigation.types.SourceSet
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** End to end through config parsing, on bytecode compiled from test-project (com.example.variants.callpaths). */
class CallPathOrchestratorTest {

    @TempDir
    lateinit var tempDir: Path

    private val classes = File("test-project/build/classes/kotlin/main")
    private val pkg = "com\\.example\\.variants\\.callpaths"

    private fun run(vararg props: Pair<String, String>, sourceSet: SourceSet = SourceSet.MAIN): CallPathOutput =
        CallPathOrchestrator.run(CallPathConfig.parse(props.toMap()), listOf(classes to sourceSet), tempDir.resolve("cnav").toFile())

    private fun entryNames(output: CallPathOutput) = output.report!!.result.reaching.map { it.entry.qualifiedName }

    @Test
    fun `guard separates unguarded from guarded entry points`() {
        val output = run("from" to "$pkg\\.guarded\\.Controller\\.", "to" to "$pkg\\.guarded\\.Repo\\.delete", "guard" to "authorize")

        val report = assertNotNull(output.report)
        assertEquals(listOf("com.example.variants.callpaths.guarded.Controller.deleteOrPurge"), entryNames(output))
        assertEquals(listOf("com.example.variants.callpaths.guarded.Controller.delete"), report.result.guardedEntries.map { it.qualifiedName })
        assertEquals("authorize", report.guard)
        assertEquals(8, report.maxDepth)
    }

    @Test
    fun `entry points by annotation, guarded by annotation`() {
        val output = run("from-annotation" to "callpaths\\.Post$", "to" to "$pkg\\.annotated\\.Repo\\.delete", "guard" to "RequiresRole")

        assertEquals(listOf("com.example.variants.callpaths.annotated.PublicController.delete"), entryNames(output))
        assertEquals(listOf("com.example.variants.callpaths.annotated.AdminController.delete"), output.report!!.result.guardedEntries.map { it.qualifiedName })
    }

    @Test
    fun `interface dispatch is followed`() {
        val output = run("from" to "$pkg\\.dispatch\\.Controller\\.delete", "to" to "$pkg\\.dispatch\\.Repo\\.delete", "guard" to "authorize")

        val path = output.report!!.result.reaching.single().paths.single()
        assertEquals("com.example.variants.callpaths.dispatch.UnsafeAdapter.delete", path[1].method.qualifiedName)
        assertEquals("com.example.variants.callpaths.dispatch.Port", path[1].via.toString())
    }

    @Test
    fun `a library target is found with project-only on`() {
        val output = run("from" to "$pkg\\.guarded\\.Controller\\.delete$", "to" to "Intrinsics\\.checkNotNullParameter")

        val path = output.report!!.result.reaching.single().paths.first()
        assertEquals("kotlin.jvm.internal.Intrinsics.checkNotNullParameter", path.last().method.qualifiedName)
        assertNull(path.last().sourceFile)
    }

    @Test
    fun `maxdepth and top reach the finder`() {
        val shallow = run("from" to "$pkg\\.shapes\\.Step1\\.run", "to" to "$pkg\\.shapes\\.Repo\\.delete", "maxdepth" to "2")
        val capped = run("from" to "$pkg\\.shapes\\.[ABC]\\.go", "to" to "$pkg\\.shapes\\.Repo\\.delete", "top" to "2")

        assertTrue(shallow.report!!.result.reaching.isEmpty())
        assertEquals(2, capped.report!!.result.reaching.size)
        assertTrue(capped.report!!.result.truncated)
    }

    @Test
    fun `no matching entry point gives a message naming the parameters`() {
        val output = run("from" to "NoSuchController", "to" to "$pkg\\.guarded\\.Repo\\.delete")

        assertNull(output.report)
        assertTrue("from" in output.noMatch!! && "NoSuchController" in output.noMatch!!, output.noMatch)
    }

    @Test
    fun `no matching target gives a message naming the target`() {
        val output = run("from" to "$pkg\\.guarded\\.Controller\\.", "to" to "NoSuchRepo\\.delete")

        assertNull(output.report)
        assertTrue("to" in output.noMatch!! && "NoSuchRepo" in output.noMatch!!, output.noMatch)
    }

    @Test
    fun `scope filters entry points by source set`() {
        val output = run("from" to "$pkg\\.guarded\\.Controller\\.", "to" to "$pkg\\.guarded\\.Repo\\.delete", "scope" to "test")

        assertNull(output.report)
        assertNotNull(output.noMatch)
    }
}
