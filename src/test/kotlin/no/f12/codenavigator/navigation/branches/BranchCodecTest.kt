package no.f12.codenavigator.navigation.branches

import kotlin.test.Test
import kotlin.test.assertEquals

/** The text encoding that carries scan results out of the Gradle PSI worker. */
class BranchCodecTest {

    private val functions = listOf(
        FunctionBranches(
            "p.Svc", "f", 3, 20,
            listOf(
                CallSite("load", 4),
                Branch(
                    BranchKind.IF, "if (name == \"a\\tb\")", 5,
                    listOf(
                        CallSite("isAdmin", 6, property = true),
                        Branch(BranchKind.TRY, "try", 7, listOf(Exit(ExitKind.THROW, "throw X(\"line1\\nline2 …\")", 8))),
                        Branch(BranchKind.ELSE, "else", 9, emptyList()),
                    ),
                ),
                Exit(ExitKind.RETURN, "return", 12),
            ),
        ),
        FunctionBranches("p.FileKt", "top", 1, 2, emptyList()),
    )

    @Test
    fun `round trip keeps nesting, kinds, lines and text with tabs, newlines and backslashes`() {
        assertEquals(functions, BranchCodec.decode(BranchCodec.encode(functions)))
    }

    @Test
    fun `round trip of raw control characters in text`() {
        val tricky = listOf(FunctionBranches("p.A", "g", 1, 1, listOf(Exit(ExitKind.RETURN, "a\tb\nc\\d", 1))))

        assertEquals(tricky, BranchCodec.decode(BranchCodec.encode(tricky)))
    }

    @Test
    fun `empty list`() {
        assertEquals(emptyList(), BranchCodec.decode(BranchCodec.encode(emptyList())))
    }
}
