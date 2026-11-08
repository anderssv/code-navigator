package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.branches.BranchScan
import no.f12.codenavigator.navigation.branches.BranchScanner
import no.f12.codenavigator.navigation.branches.PlacedBranch
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.types.SourceSet
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** find-callees and call-paths with branches, through the orchestrators, on test-project (com.example.variants.branches). */
class BranchesOrchestrationTest {

    @TempDir
    lateinit var tempDir: Path

    private val classes = File("test-project/build/classes/kotlin/main")
    private val tagged = listOf(classes to SourceSet.MAIN)
    private val scan = BranchScan(listOf(File("test-project/src/main/kotlin")), BranchScanner::scan)
    private val pkg = "com\\.example\\.variants\\.branches"

    private fun treeConfig(branches: Boolean) = CallGraphConfig(
        method = "$pkg\\.OrderService\\.remove", maxDepth = 2, projectOnly = true, filterSynthetic = true,
        scope = Scope.ALL, format = OutputFormat.TEXT, branches = branches,
    )

    private fun cache() = tempDir.resolve("cnav").toFile()

    @Test
    fun `find-callees with branches lays out the callees`() {
        val output = CallTreeOrchestrator.run(treeConfig(branches = true), tagged, cache(), CallDirection.CALLEES, scan)

        val branches = assertNotNull(output.trees.single().branches)
        assertEquals(listOf("if (!auth.canDelete(user))", "when (hard)"), branches.items.filterIsInstance<PlacedBranch>().map { it.label })
    }

    @Test
    fun `find-callees without branches, or with no scan available, has none`() {
        assertNull(CallTreeOrchestrator.run(treeConfig(branches = false), tagged, cache(), CallDirection.CALLEES, scan).trees.single().branches)
        assertNull(CallTreeOrchestrator.run(treeConfig(branches = true), tagged, cache(), CallDirection.CALLEES, null).trees.single().branches)
    }

    @Test
    fun `find-callers ignores branches`() {
        val config = treeConfig(branches = true).copy(method = "$pkg\\.Repo\\.archive")

        val output = CallTreeOrchestrator.run(config, tagged, cache(), CallDirection.CALLERS, scan)

        assertNull(output.trees.single().branches)
    }

    @Test
    fun `call-paths with branches adds the conditions of each step`() {
        val config = CallPathConfig.parse(mapOf("from" to "$pkg\\.Controller\\.remove", "to" to "$pkg\\.Repo\\.archive", "branches" to "true"))

        val output = CallPathOrchestrator.run(config, tagged, cache(), scan)

        val steps = output.report!!.result.reaching.single().paths.single()
        assertEquals(listOf(null, listOf(listOf("try")), listOf(listOf("when (hard)", "false ->"))), steps.map { it.conditions })
    }

    @Test
    fun `call-paths without branches has no conditions`() {
        val config = CallPathConfig.parse(mapOf("from" to "$pkg\\.Controller\\.remove", "to" to "$pkg\\.Repo\\.archive"))

        val output = CallPathOrchestrator.run(config, tagged, cache(), scan)

        assertTrue(output.report!!.result.reaching.single().paths.single().all { it.conditions == null })
    }
}
