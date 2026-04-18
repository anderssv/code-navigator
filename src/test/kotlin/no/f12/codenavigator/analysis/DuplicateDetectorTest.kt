package no.f12.codenavigator.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class DuplicateDetectorTest {

    @Test
    fun `finds no duplicates when files have unique content`() {
        val files = mapOf(
            "A.kt" to "val alpha = computeAlpha()",
            "B.kt" to "fun process(input: String): Boolean = validate(input)",
        )

        val result = DuplicateDetector.detect(tokenizeFiles(files), minTokens = 5)

        assertTrue(result.isEmpty(), "Expected no duplicates but found: $result")
    }

    @Test
    fun `finds exact duplicate block across two files`() {
        val shared = "val x = computeResult(input)\nval y = transform(x)\nval z = validate(y)"
        val files = mapOf(
            "A.kt" to "fun foo() {\n$shared\n}",
            "B.kt" to "fun bar() {\n$shared\n}",
        )

        val result = DuplicateDetector.detect(tokenizeFiles(files), minTokens = 5)

        assertEquals(1, result.size)
        assertEquals(2, result[0].locations.size)
        assertEquals("A.kt", result[0].locations[0].file)
        assertEquals("B.kt", result[0].locations[1].file)
    }

    @Test
    fun `respects minTokens threshold`() {
        val shared = "val x = 1"  // only ~4 tokens
        val files = mapOf(
            "A.kt" to shared,
            "B.kt" to shared,
        )

        val resultHigh = DuplicateDetector.detect(tokenizeFiles(files), minTokens = 10)
        assertTrue(resultHigh.isEmpty())

        val resultLow = DuplicateDetector.detect(tokenizeFiles(files), minTokens = 3)
        assertEquals(1, resultLow.size)
    }

    @Test
    fun `finds duplicate within same file`() {
        val block = "val x = computeResult(input)\nval y = transform(x)"
        val source = "fun foo() {\n$block\n}\nfun bar() {\n$block\n}"
        val files = mapOf("A.kt" to source)

        val result = DuplicateDetector.detect(tokenizeFiles(files), minTokens = 5)

        assertEquals(1, result.size)
        assertEquals(2, result[0].locations.size)
        assertEquals("A.kt", result[0].locations[0].file)
        assertEquals("A.kt", result[0].locations[1].file)
    }

    @Test
    fun `reports token count for each duplicate group`() {
        val shared = "val x = computeResult(input)\nval y = transform(x)\nval z = validate(y)"
        val files = mapOf(
            "A.kt" to "fun foo() {\n$shared\n}",
            "B.kt" to "fun bar() {\n$shared\n}",
        )

        val result = DuplicateDetector.detect(tokenizeFiles(files), minTokens = 5)

        assertTrue(result[0].tokenCount >= 5)
    }

    @Test
    fun `reports line numbers for duplicate locations`() {
        val shared = "val x = computeResult(input)\nval y = transform(x)"
        val files = mapOf(
            "A.kt" to "package a\n\n$shared",
            "B.kt" to "package b\n\n$shared",
        )

        val result = DuplicateDetector.detect(tokenizeFiles(files), minTokens = 5)

        assertEquals(1, result.size)
        assertEquals(3, result[0].locations[0].startLine)
        assertEquals(3, result[0].locations[1].startLine)
    }

    private fun tokenizeFiles(files: Map<String, String>): List<TokenizedFile> =
        files.map { (name, content) ->
            TokenizedFile(name, SourceTokenizer.tokenize(content))
        }
}
