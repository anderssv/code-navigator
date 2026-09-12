package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.navigation.bytecode.InlineMethodDetector
import no.f12.codenavigator.navigation.types.ClassName
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * cnavFindCallers reporting a bare "(no callers)" for an inline function is the shape that makes an
 * agent delete live code: the compiler leaves no call edge, and cnav's own guidance tells agents not
 * to cross-check with grep. These tests pin the warning that closes that gap, against real Kotlin
 * compiler output rather than synthetic bytecode.
 */
class InlineCallersTest {

    private val testProjectClasses = File("test-project/build/classes/kotlin/main")
    private val facade = ClassName("com.example.variants.inlinecallers.InlineAccessKt")

    private val graph = CallGraphBuilder.build(listOf(testProjectClasses)).data
    private val inlineMethods = InlineMethodDetector.scanAll(listOf(testProjectClasses))

    private fun tree(methodName: String, direction: CallDirection = CallDirection.CALLERS) =
        CallTreeBuilder.build(
            graph,
            listOf(MethodRef(facade, methodName)),
            maxDepth = 3,
            direction = direction,
            inlineMethods = inlineMethods,
        ).single()

    @Test
    fun `the inline function really has no call edges in bytecode`() {
        assertTrue(graph.callersOf(facade, "withPoll").isEmpty(), "fixture assumption: the compiler inlined every call site")
    }

    @Test
    fun `an inline function is flagged as inline`() {
        assertTrue(tree("withPoll").isInline)
    }

    @Test
    fun `a normal function in the same file is not flagged`() {
        assertFalse(tree("notInlined").isInline)
    }

    @Test
    fun `the forInline twin is recognised as inline too`() {
        val twin = MethodRef(facade, "withPoll\$\$forInline")

        assertTrue(CallTreeBuilder.isInlineMethod(twin, inlineMethods))
    }

    @Test
    fun `text output warns instead of stating there are no callers`() {
        val text = CallTreeFormatter.renderTrees(listOf(tree("withPoll")), CallDirection.CALLERS)

        assertContains(text, "inline function")
        assertContains(text, "INCOMPLETE")
    }

    @Test
    fun `llm output carries the same warning`() {
        val text = CallTreeFormatter.formatLlm(listOf(tree("withPoll")), CallDirection.CALLERS)

        assertContains(text, "INCOMPLETE")
    }

    @Test
    fun `json exposes the warning as structured fields`() {
        val json = CallTreeFormatter.formatJson(listOf(tree("withPoll")), CallDirection.CALLERS)

        assertContains(json, "\"inline\":true")
        assertContains(json, "\"incompleteReason\"")
    }

    @Test
    fun `a normal function in the same file reports its real callers with no warning`() {
        val text = CallTreeFormatter.renderTrees(listOf(tree("notInlined")), CallDirection.CALLERS)

        assertContains(text, "AdminRoutes.handleList")
        assertFalse(text.contains("INCOMPLETE"))
    }

    @Test
    fun `callees direction is unaffected`() {
        val text = CallTreeFormatter.renderTrees(listOf(tree("withPoll", CallDirection.CALLEES)), CallDirection.CALLEES)

        assertFalse(text.contains("INCOMPLETE"))
    }

    // cnavDead already filtered inline methods via this detector while cnavFindCallers did not.
    // Both now read the same set, so the two tasks can no longer disagree about which functions
    // bytecode cannot answer for.
    @Test
    fun `the same detector backs both tasks`() {
        assertEquals(true, MethodRef(facade, "withPoll") in inlineMethods)
    }
}
