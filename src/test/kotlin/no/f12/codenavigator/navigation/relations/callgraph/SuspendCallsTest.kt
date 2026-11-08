package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.branches.BranchFormatter
import no.f12.codenavigator.navigation.branches.BranchScan
import no.f12.codenavigator.navigation.branches.BranchScanner
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.types.SourceSet
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Coroutines through find-callees and call-paths, with and without branches, on test-project
 * (com.example.variants.branches.Suspending.kt): suspend functions compile to a state machine plus a
 * continuation class, and suspend lambdas to their own classes.
 */
class SuspendCallsTest {

    @TempDir
    lateinit var tempDir: Path

    private val tagged = listOf(File("test-project/build/classes/kotlin/main") to SourceSet.MAIN)
    private val scan = BranchScan(listOf(File("test-project/src/main/kotlin")), BranchScanner::scan)
    private val pkg = "com\\.example\\.variants\\.branches"
    private val outline: (MethodRef, String) -> String = { callee, indent -> "$indent→ ${callee.className.value.substringAfterLast('.')}.${callee.methodName}" }

    private fun callees(pattern: String, depth: Int = 1, branches: Boolean = false) = CallTreeOrchestrator.run(
        CallGraphConfig("$pkg\\.$pattern", depth, projectOnly = true, filterSynthetic = true, scope = Scope.ALL, format = OutputFormat.TEXT, branches = branches),
        tagged, tempDir.resolve("cnav").toFile(), CallDirection.CALLEES, scan,
    ).trees.single()

    private fun paths(from: String, to: String, branches: Boolean = false) = CallPathOrchestrator.run(
        CallPathConfig.parse(buildMap {
            put("from", "$pkg\\.$from")
            put("to", "$pkg\\.$to")
            if (branches) put("branches", "true")
        }),
        tagged, tempDir.resolve("cnav").toFile(), scan,
    ).report!!.result

    private fun names(node: CallTreeNode): Set<String> =
        node.children.flatMap { names(it) + "${it.method.className.value.substringAfterLast('.')}.${it.method.methodName}" }.toSet()

    // --- Suspend functions ---

    @Test
    fun `find-callees sees the calls of a suspend function`() {
        val tree = callees("AsyncOrders\\.remove$")

        assertTrue(names(tree).containsAll(listOf("Repo.delete", "Repo.archive", "Notifier.send")), names(tree).toString())
    }

    @Test
    fun `find-callees with branches lays out a suspend function's calls`() {
        val branches = assertNotNull(callees("AsyncOrders\\.remove$", branches = true).branches)

        assertEquals(
            """
            if (hard)  :12
              → Repo.delete
            else  :14
              → Repo.archive
            → Notifier.send
            """.trimIndent(),
            BranchFormatter.renderText(branches, "", outline),
        )
        assertEquals(emptyList(), branches.unplaced)
    }

    @Test
    fun `call-paths through suspend functions, with the conditions of each step`() {
        val steps = paths("AsyncController\\.remove", "Repo\\.archive", branches = true).reaching.single().paths.single()

        assertEquals(
            listOf("AsyncController.remove", "AsyncOrders.remove", "Repo.archive"),
            steps.map { "${it.method.className.value.substringAfterLast('.')}.${it.method.methodName}" },
        )
        assertEquals(listOf(null, listOf(listOf("try")), listOf(listOf("else"))), steps.map { it.conditions })
    }

    @Test
    fun `a suspend function's continuation class is not an entry point`() {
        val result = paths("AsyncOrders\\.remove", "Repo\\.archive")

        assertEquals(listOf("AsyncOrders.remove"), result.reaching.map { "${it.entry.className.value.substringAfterLast('.')}.${it.entry.methodName}" })
    }

    // --- Suspend lambdas ---

    @Test
    fun `find-callees reaches calls made inside a suspend lambda`() {
        val tree = callees("AsyncOrders\\.removeAll", depth = 3)

        assertTrue("Repo.delete" in names(tree), names(tree).toString())
    }

    @Test
    fun `call-paths reaches the target through a suspend lambda, with its conditions`() {
        val result = paths("AsyncOrders\\.removeAll", "Repo\\.delete", branches = true)

        val path = result.reaching.single().paths.first()
        assertEquals("Repo.delete", path.last().method.let { "${it.className.value.substringAfterLast('.')}.${it.methodName}" })
        assertEquals(listOf(listOf("for (id in ids)", "if (id > 0)")), path.last().conditions)
    }
}
