package no.f12.codenavigator.navigation.refactor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertTrue

class DiffSupportTest {

    @Test
    fun `computeUnifiedDiff produces standard unified diff header`() {
        val before = "line1\nline2\nline3"
        val after = "line1\nchanged\nline3"

        val diff = computeUnifiedDiff("src/main/kotlin/Foo.kt", before, after)

        assertContains(diff, "--- a/src/main/kotlin/Foo.kt")
        assertContains(diff, "+++ b/src/main/kotlin/Foo.kt")
    }

    @Test
    fun `computeUnifiedDiff produces hunk header with line numbers`() {
        val before = "line1\nline2\nline3"
        val after = "line1\nchanged\nline3"

        val diff = computeUnifiedDiff("Foo.kt", before, after)

        assertContains(diff, "@@ -")
        assertContains(diff, "+")
    }

    @Test
    fun `computeUnifiedDiff marks removed lines with minus and added lines with plus`() {
        val before = "line1\nold\nline3"
        val after = "line1\nnew\nline3"

        val diff = computeUnifiedDiff("Foo.kt", before, after)

        assertContains(diff, "-old")
        assertContains(diff, "+new")
    }

    @Test
    fun `computeUnifiedDiff includes context lines`() {
        val before = "a\nb\nc\nd\ne\nf\ng"
        val after = "a\nb\nc\nX\ne\nf\ng"

        val diff = computeUnifiedDiff("Foo.kt", before, after, contextLines = 2)

        // Should include context around the change
        assertContains(diff, " b")
        assertContains(diff, " c")
        assertContains(diff, "-d")
        assertContains(diff, "+X")
        assertContains(diff, " e")
        assertContains(diff, " f")
    }

    @Test
    fun `computeUnifiedDiff returns empty string for identical files`() {
        val content = "line1\nline2\nline3"

        val diff = computeUnifiedDiff("Foo.kt", content, content)

        assertEquals("", diff)
    }

    @Test
    fun `computeUnifiedDiff handles added lines`() {
        val before = "line1\nline3"
        val after = "line1\nline2\nline3"

        val diff = computeUnifiedDiff("Foo.kt", before, after)

        assertContains(diff, "+line2")
    }

    @Test
    fun `computeUnifiedDiff handles removed lines`() {
        val before = "line1\nline2\nline3"
        val after = "line1\nline3"

        val diff = computeUnifiedDiff("Foo.kt", before, after)

        assertContains(diff, "-line2")
    }

    @Test
    fun `computeUnifiedDiff handles rename scenario`() {
        val before = """
            package com.example

            class Service {
                fun oldMethod(): String = "hello"

                fun caller(): String = oldMethod()
            }
        """.trimIndent()

        val after = """
            package com.example

            class Service {
                fun newMethod(): String = "hello"

                fun caller(): String = newMethod()
            }
        """.trimIndent()

        val diff = computeUnifiedDiff("src/main/kotlin/com/example/Service.kt", before, after)

        assertContains(diff, "--- a/src/main/kotlin/com/example/Service.kt")
        assertContains(diff, "+++ b/src/main/kotlin/com/example/Service.kt")
        assertContains(diff, "-    fun oldMethod(): String = \"hello\"")
        assertContains(diff, "+    fun newMethod(): String = \"hello\"")
        assertContains(diff, "-    fun caller(): String = oldMethod()")
        assertContains(diff, "+    fun caller(): String = newMethod()")
    }

    @Test
    fun `computeUnifiedDiff multiple files can be concatenated`() {
        val diff1 = computeUnifiedDiff("A.kt", "old", "new")
        val diff2 = computeUnifiedDiff("B.kt", "foo", "bar")

        val combined = "$diff1\n$diff2"

        assertContains(combined, "--- a/A.kt")
        assertContains(combined, "--- a/B.kt")
    }
}
