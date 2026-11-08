package no.f12.codenavigator.navigation.branches

import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Printing placed branches, on hand-built input. Calls are printed by a stand-in callback, as the call tree would. */
class BranchFormatterTest {

    private fun ref(cls: String, method: String) = MethodRef(ClassName("p.$cls"), method)
    private val delete = ref("Repo", "delete")
    private val log = ref("Audit", "log")
    private val map = ref("Mapper", "map")

    private val body = PlacedBody(
        items = listOf(
            PlacedCall(log, 3),
            PlacedBranch(BranchKind.IF, "if (!auth.canDelete(user))", 4, listOf(PlacedExit(ExitKind.THROW, "throw ForbiddenException()", 5))),
            PlacedBranch(
                BranchKind.WHEN, "when (mode)", 7,
                listOf(PlacedBranch(BranchKind.WHEN_ENTRY, "HARD ->", 8, listOf(PlacedCall(delete, 8)))),
            ),
        ),
        unplaced = listOf(map),
    )

    private val textCall: (MethodRef, String) -> String = { callee, indent -> "$indent→ ${callee.methodName}" }

    @Test
    fun `text - branches, exits, nested entries and unplaced calls`() {
        assertEquals(
            """
              → log
              if (!auth.canDelete(user))  :4
                throw ForbiddenException()  :5
              when (mode)  :7
                HARD ->  :8
                  → delete
              (branch not known)
                → map
            """.trimIndent().prependIndent("  "),
            BranchFormatter.renderText(body, "  ", textCall),
        )
    }

    @Test
    fun `lambda bodies get their own label in every format`() {
        val lambda = MethodRef(ClassName("p.Svc\$f\$1"), "invokeSuspend")
        val withLambda = PlacedBody(listOf(PlacedCall(delete, 2)), unplaced = emptyList(), lambdas = listOf(lambda))

        assertEquals("→ delete\n(lambda body)\n  → invokeSuspend", BranchFormatter.renderText(withLambda, "", textCall))
        assertEquals("→ delete\n(lambda body)\n  → invokeSuspend", BranchFormatter.renderLlm(withLambda, "", textCall))
        assertEquals(
            """{"items":[{"call":"p.Repo.delete","line":2}],"unplaced":[],"lambdas":["p.Svc${'$'}f${'$'}1.invokeSuspend"]}""",
            BranchFormatter.renderJson(withLambda),
        )
    }

    @Test
    fun `text - multi-line call output keeps the callback's own lines`() {
        val nested: (MethodRef, String) -> String = { callee, indent -> "$indent→ ${callee.methodName}\n$indent  → child" }

        assertEquals(
            "if (a)  :1\n  → delete\n    → child",
            BranchFormatter.renderText(PlacedBody(listOf(PlacedBranch(BranchKind.IF, "if (a)", 1, listOf(PlacedCall(delete, 2)))), emptyList()), "", nested),
        )
    }

    @Test
    fun `text - empty body prints nothing`() {
        assertEquals("", BranchFormatter.renderText(PlacedBody(emptyList(), emptyList()), "  ", textCall))
    }

    @Test
    fun `llm - same layout, line numbers as L`() {
        assertEquals(
            """
            → log
            if (!auth.canDelete(user)) L4
              throw ForbiddenException() L5
            when (mode) L7
              HARD -> L8
                → delete
            (branch not known)
              → map
            """.trimIndent(),
            BranchFormatter.renderLlm(body, "", textCall),
        )
    }

    @Test
    fun `json - nested items with kinds and lines, unplaced listed`() {
        assertEquals(
            """{"items":[{"call":"p.Audit.log","line":3},""" +
                """{"branch":"if (!auth.canDelete(user))","kind":"IF","line":4,"items":[{"exit":"throw ForbiddenException()","kind":"THROW","line":5}]},""" +
                """{"branch":"when (mode)","kind":"WHEN","line":7,"items":[{"branch":"HARD ->","kind":"WHEN_ENTRY","line":8,"items":[{"call":"p.Repo.delete","line":8}]}]}],""" +
                """"unplaced":["p.Mapper.map"],"lambdas":[]}""",
            BranchFormatter.renderJson(body),
        )
    }

    // --- Conditions on a single call (call-paths) ---

    @Test
    fun `conditions - one chain, alternatives, unconditional and unknown`() {
        assertEquals("if (a) › try", BranchFormatter.conditionText(listOf(listOf("if (a)", "try"))))
        assertEquals("if (a) | else", BranchFormatter.conditionText(listOf(listOf("if (a)"), listOf("else"))))
        assertEquals("always", BranchFormatter.conditionText(listOf(emptyList())))
        assertEquals("always | if (a)", BranchFormatter.conditionText(listOf(emptyList(), listOf("if (a)"))))
        assertNull(BranchFormatter.conditionText(null))
    }

    @Test
    fun `conditions - json keeps the structure, null when unknown`() {
        assertEquals("""[["if (a)","try"],[]]""", BranchFormatter.conditionsJson(listOf(listOf("if (a)", "try"), emptyList())))
        assertNull(BranchFormatter.conditionsJson(null))
    }
}
