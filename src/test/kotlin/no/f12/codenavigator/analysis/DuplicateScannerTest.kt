package no.f12.codenavigator.analysis

import no.f12.codenavigator.navigation.types.SourceSet
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DuplicateScannerTest {

    @TempDir
    lateinit var tempDir: Path

    private fun sourceRoot(): File = tempDir.resolve("src").toFile().also { it.mkdirs() }

    private fun write(root: File, relativePath: String, content: String) {
        val file = File(root, relativePath)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    @Test
    fun `identical import headers are not reported as duplicates`() {
        val root = sourceRoot()
        val imports = """
            package com.example.web

            import io.ktor.server.application.ApplicationCall
            import io.ktor.server.application.call
            import io.ktor.server.response.respondText
            import io.ktor.server.routing.RoutingContext
            import io.ktor.http.Parameters
        """.trimIndent()
        write(root, "AdminRoutes.kt", "$imports\n\nfun admin() = 1\n")
        write(root, "ParticipantRoutes.kt", "$imports\n\nfun participant() = 2\n")

        val groups = DuplicateScanner.scan(listOf(root to SourceSet.MAIN), minTokens = 10, top = 20)

        assertTrue(groups.isEmpty(), "import headers were reported as duplication: $groups")
    }

    @Test
    fun `duplicated bodies are still reported when headers differ`() {
        val root = sourceRoot()
        val body = """
            fun calculate(start: Int, end: Int): List<Int> {
                val result = mutableListOf<Int>()
                for (day in start..end) {
                    if (day % 2 == 0) {
                        result.add(day * 3)
                    } else {
                        result.add(day + 7)
                    }
                }
                return result
            }
        """.trimIndent()
        write(root, "Admin.kt", "package com.example.admin\n\nimport kotlin.math.abs\n\n$body\n")
        write(root, "Participant.kt", "package com.example.participant\n\n$body\n")

        val groups = DuplicateScanner.scan(listOf(root to SourceSet.MAIN), minTokens = 20, top = 20)

        assertEquals(1, groups.size)
        assertEquals(
            setOf("Admin.kt", "Participant.kt"),
            groups.single().locations.map { it.file }.toSet(),
        )
    }

    @Test
    fun `stripHeaderDeclarations keeps line numbers of the remaining tokens`() {
        val tokens = SourceTokenizer.tokenize("package a.b\n\nimport c.D\n\nval x = 1\n")

        val stripped = DuplicateScanner.stripHeaderDeclarations(tokens)

        assertEquals(listOf("val", "x", "=", "1"), stripped.map { it.text })
        assertTrue(stripped.all { it.line == 5 })
    }
}
