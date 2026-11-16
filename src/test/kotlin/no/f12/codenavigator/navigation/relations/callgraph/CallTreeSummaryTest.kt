package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.SourceSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallTreeSummaryTest {

    private val target = MethodRef(ClassName("com.example.Service"), "doWork")
    private val middle = MethodRef(ClassName("com.example.Route"), "section")
    private val top = MethodRef(ClassName("com.example.App"), "main")
    private val test = MethodRef(ClassName("com.example.ServiceTest"), "works")
    private val graph = CallGraph(mapOf(top to setOf(middle), middle to setOf(target), test to setOf(target)))

    @Test
    fun `a node whose callers were not followed because of the depth limit is marked`() {
        val tree = CallTreeBuilder.build(graph, listOf(target), maxDepth = 1, CallDirection.CALLERS).single()

        val section = tree.children.first { it.method == middle }
        assertTrue(section.cutAtDepth)
        assertFalse(tree.children.first { it.method == test }.cutAtDepth)
    }

    @Test
    fun `llm output starts with a summary of how many references, in which files and source sets`() {
        val tree = CallTreeNode(
            target, "Service.kt", 3,
            listOf(
                CallTreeNode(middle, "Route.kt", 7, listOf(CallTreeNode(top, "App.kt", 2, emptyList(), sourceSet = SourceSet.MAIN)), sourceSet = SourceSet.MAIN),
                CallTreeNode(test, "ServiceTest.kt", 9, emptyList(), sourceSet = SourceSet.TEST),
            ),
        )

        val summary = CallTreeFormatter.formatLlmWithSummary(listOf(tree), CallDirection.CALLERS).lines().first()

        assertEquals("3 callers (2 direct) in 3 files (prod 2, test 1), 2 levels deep", summary)
    }

    @Test
    fun `the summary says when the depth limit cut the tree off`() {
        val tree = CallTreeBuilder.build(graph, listOf(target), maxDepth = 1, CallDirection.CALLERS).single()

        val summary = CallTreeFormatter.formatLlmWithSummary(listOf(tree), CallDirection.CALLERS).lines().first()

        assertTrue(summary.endsWith("; 1 branch cut at the depth limit, raise maxdepth to see further"), summary)
    }

    @Test
    fun `depth is accepted as another name for maxdepth`() {
        assertEquals(5, CallGraphConfig.parse(mapOf("pattern" to "x", "depth" to "5")).maxDepth)
    }
}
