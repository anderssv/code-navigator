package no.f12.codenavigator.navigation.refactor

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Field report (greitt, v0.1.116): after `cnavMoveClass` moved a test into `polls.model`, the file
 * kept its now-same-package `import no.mikill.greitt.polls.model.*` lines.
 */
class MoveClassImportCleanupTest {

    @Test
    fun `imports pointing at the new package are removed`() {
        val content = """
            package polls.model

            import polls.model.Poll
            import polls.model.Slot
            import kotlin.test.Test

            class PollDomainTest
        """.trimIndent()

        val result = MoveClassRewriter.removeSamePackageImports(content, "polls.model")

        assertEquals(
            """
            package polls.model

            import kotlin.test.Test

            class PollDomainTest
            """.trimIndent(),
            result,
        )
    }

    @Test
    fun `a star import of the new package is removed`() {
        val content = "package polls.model\n\nimport polls.model.*\n\nclass X\n"

        val result = MoveClassRewriter.removeSamePackageImports(content, "polls.model")

        assertEquals("package polls.model\n\nclass X\n", result)
    }

    @Test
    fun `an aliased import is kept because it still binds a name`() {
        val content = "package polls.model\n\nimport polls.model.Poll as DomainPoll\n\nclass X\n"

        assertEquals(content, MoveClassRewriter.removeSamePackageImports(content, "polls.model"))
    }

    @Test
    fun `a nested class import is kept because it is not implicit in the same package`() {
        val content = "package polls.model\n\nimport polls.model.Outer.Inner\n\nclass X\n"

        assertEquals(content, MoveClassRewriter.removeSamePackageImports(content, "polls.model"))
    }

    @Test
    fun `imports of other packages are untouched`() {
        val content = "package polls.model\n\nimport polls.Service\nimport kotlin.test.Test\n\nclass X\n"

        assertEquals(content, MoveClassRewriter.removeSamePackageImports(content, "polls.model"))
    }
}
