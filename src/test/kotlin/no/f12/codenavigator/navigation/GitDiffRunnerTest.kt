package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.changedsince.GitDiffRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitDiffRunnerTest {

    @Test
    fun `buildCommand produces git diff with name-only and triple-dot for merge base`() {
        val command = GitDiffRunner.buildCommand("main")

        assertEquals(listOf("git", "diff", "--name-only", "main...HEAD"), command)
    }

    @Test
    fun `parseOutput splits lines and filters blanks`() {
        val output = "src/main/kotlin/Foo.kt\nsrc/main/kotlin/Bar.kt\n"

        val result = GitDiffRunner.parseOutput(output)

        assertEquals(listOf("src/main/kotlin/Foo.kt", "src/main/kotlin/Bar.kt"), result)
    }

    @Test
    fun `parseOutput handles empty output`() {
        val result = GitDiffRunner.parseOutput("")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseOutput ignores whitespace-only lines`() {
        val output = "src/main/kotlin/Foo.kt\n  \n\nsrc/main/kotlin/Bar.kt\n"

        val result = GitDiffRunner.parseOutput(output)

        assertEquals(listOf("src/main/kotlin/Foo.kt", "src/main/kotlin/Bar.kt"), result)
    }
}
