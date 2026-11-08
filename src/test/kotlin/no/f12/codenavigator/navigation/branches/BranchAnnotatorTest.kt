package no.f12.codenavigator.navigation.branches

import no.f12.codenavigator.navigation.relations.callgraph.CallDirection
import no.f12.codenavigator.navigation.relations.callgraph.CallGraph
import no.f12.codenavigator.navigation.relations.callgraph.CallGraphBuilder
import no.f12.codenavigator.navigation.relations.callgraph.CallPathFinder
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeBuilder
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeNode
import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import no.f12.codenavigator.navigation.types.ClassName
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Branches on call trees and call paths, end to end on test-project: bytecode from
 * com.example.variants.branches and the real source file it was compiled from.
 */
class BranchAnnotatorTest {

    private companion object {
        val classes = File("test-project/build/classes/kotlin/main")
        val sources = File("test-project/src/main/kotlin")
        val graph: CallGraph by lazy { CallGraphBuilder.build(listOf(classes)).data }
        const val PKG = "com.example.variants.branches"
    }

    private fun m(cls: String, method: String) = MethodRef(ClassName("$PKG.$cls"), method)
    private val outline: (MethodRef, String) -> String = { callee, indent -> "$indent→ ${callee.className.value.substringAfterLast('.')}.${callee.methodName}" }

    private fun tree(root: MethodRef, depth: Int = 1): List<CallTreeNode> =
        CallTreeBuilder.build(graph, listOf(root), depth, CallDirection.CALLEES, graph.projectClassFilter())

    private fun functionsFor(names: Set<String>) = BranchScanner.scan(BranchSources.filesNamed(names, listOf(sources)))

    // --- Source files ---

    @Test
    fun `finds source files by name under the roots`() {
        val files = BranchSources.filesNamed(setOf("Orders.kt", "Missing.kt"), listOf(sources))

        assertEquals(listOf("Orders.kt"), files.map { it.name })
    }

    @Test
    fun `source file names of a tree include every node that has children`() {
        val names = BranchAnnotator.sourceFileNames(tree(m("Controller", "remove"), depth = 2))

        assertEquals(setOf("Orders.kt"), names)
    }

    // --- Call trees ---

    @Test
    fun `call tree root gets its callees laid out in branches`() {
        val trees = tree(m("OrderService", "remove"))
        val annotated = BranchAnnotator.annotateTrees(trees, functionsFor(BranchAnnotator.sourceFileNames(trees)))

        assertEquals(
            """
            → Auth.canDelete
            if (!auth.canDelete(user))  :11
              → ForbiddenException.<init>
              throw ForbiddenException()  :12
            when (hard)  :14
              true ->  :15
                → Repo.delete
              false ->  :16
                → Repo.archive
            → Audit.log
            """.trimIndent(),
            BranchFormatter.renderText(annotated.single().branches!!, "", outline),
        )
        assertEquals(emptyList(), annotated.single().branches!!.unplaced)
    }

    @Test
    fun `nodes deeper in the tree get branches too, leaves do not`() {
        val trees = tree(m("Controller", "remove"), depth = 2)
        val annotated = BranchAnnotator.annotateTrees(trees, functionsFor(BranchAnnotator.sourceFileNames(trees))).single()

        assertEquals("try  :24\n  → OrderService.remove", BranchFormatter.renderText(annotated.branches!!, "", outline))
        val service = annotated.children.single { it.method == m("OrderService", "remove") }
        assertTrue(service.branches != null)
        assertNull(service.children.first().branches)
    }

    @Test
    fun `no source for a node leaves it without branches`() {
        val trees = tree(m("OrderService", "remove"))

        assertNull(BranchAnnotator.annotateTrees(trees, emptyList()).single().branches)
    }

    // --- Call paths ---

    @Test
    fun `each path step gets the conditions under which the step before calls it`() {
        val result = CallPathFinder.find(graph, listOf(m("Controller", "remove")), setOf(m("Repo", "archive")), maxDepth = 5, filter = graph.projectClassFilter())

        val annotated = BranchAnnotator.annotatePaths(result, functionsFor(BranchAnnotator.sourceFileNames(result)))

        val steps = annotated.reaching.single().paths.single()
        assertNull(steps[0].conditions)
        assertEquals(listOf(listOf("try")), steps[1].conditions)
        assertEquals(listOf(listOf("when (hard)", "false ->")), steps[2].conditions)
    }

    @Test
    fun `an unconditional step is an empty chain, a step without source is unknown`() {
        val result = CallPathFinder.find(graph, listOf(m("OrderService", "remove")), setOf(m("Audit", "log")), maxDepth = 5, filter = graph.projectClassFilter())

        val withSource = BranchAnnotator.annotatePaths(result, functionsFor(setOf("Orders.kt")))
        val withoutSource = BranchAnnotator.annotatePaths(result, emptyList())

        assertEquals(listOf(emptyList()), withSource.reaching.single().paths.single()[1].conditions)
        assertNull(withoutSource.reaching.single().paths.single()[1].conditions)
    }
}
