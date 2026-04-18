package no.f12.codenavigator.analysis

import kotlin.test.Test
import kotlin.test.assertEquals

class SourceTokenizerTest {

    @Test
    fun `tokenizes identifiers`() {
        val tokens = SourceTokenizer.tokenize("foo bar baz")

        assertEquals(
            listOf(
                SourceToken(SourceTokenType.IDENTIFIER, "foo", 1),
                SourceToken(SourceTokenType.IDENTIFIER, "bar", 1),
                SourceToken(SourceTokenType.IDENTIFIER, "baz", 1),
            ),
            tokens,
        )
    }

    @Test
    fun `tokenizes keywords as KEYWORD type`() {
        val tokens = SourceTokenizer.tokenize("fun class val")

        assertEquals(
            listOf(
                SourceToken(SourceTokenType.KEYWORD, "fun", 1),
                SourceToken(SourceTokenType.KEYWORD, "class", 1),
                SourceToken(SourceTokenType.KEYWORD, "val", 1),
            ),
            tokens,
        )
    }

    @Test
    fun `skips single-line comments`() {
        val tokens = SourceTokenizer.tokenize("foo // this is a comment\nbar")

        assertEquals(
            listOf(
                SourceToken(SourceTokenType.IDENTIFIER, "foo", 1),
                SourceToken(SourceTokenType.IDENTIFIER, "bar", 2),
            ),
            tokens,
        )
    }

    @Test
    fun `skips block comments`() {
        val tokens = SourceTokenizer.tokenize("foo /* block\ncomment */ bar")

        assertEquals(
            listOf(
                SourceToken(SourceTokenType.IDENTIFIER, "foo", 1),
                SourceToken(SourceTokenType.IDENTIFIER, "bar", 2),
            ),
            tokens,
        )
    }

    @Test
    fun `tokenizes string literals as single LITERAL token`() {
        val tokens = SourceTokenizer.tokenize("""val x = "hello world"""")

        assertEquals(
            listOf(
                SourceToken(SourceTokenType.KEYWORD, "val", 1),
                SourceToken(SourceTokenType.IDENTIFIER, "x", 1),
                SourceToken(SourceTokenType.OPERATOR, "=", 1),
                SourceToken(SourceTokenType.LITERAL, "\"hello world\"", 1),
            ),
            tokens,
        )
    }

    @Test
    fun `tokenizes numeric literals`() {
        val tokens = SourceTokenizer.tokenize("42 3.14 0xFF")

        assertEquals(
            listOf(
                SourceToken(SourceTokenType.LITERAL, "42", 1),
                SourceToken(SourceTokenType.LITERAL, "3.14", 1),
                SourceToken(SourceTokenType.LITERAL, "0xFF", 1),
            ),
            tokens,
        )
    }

    @Test
    fun `tokenizes operators`() {
        val tokens = SourceTokenizer.tokenize("a + b == c")

        assertEquals(
            listOf(
                SourceToken(SourceTokenType.IDENTIFIER, "a", 1),
                SourceToken(SourceTokenType.OPERATOR, "+", 1),
                SourceToken(SourceTokenType.IDENTIFIER, "b", 1),
                SourceToken(SourceTokenType.OPERATOR, "==", 1),
                SourceToken(SourceTokenType.IDENTIFIER, "c", 1),
            ),
            tokens,
        )
    }

    @Test
    fun `handles empty input`() {
        assertEquals(emptyList(), SourceTokenizer.tokenize(""))
    }

    @Test
    fun `handles triple-quoted multiline string literals`() {
        val source = "val x = \"\"\"hello\nworld\"\"\""
        val tokens = SourceTokenizer.tokenize(source)

        assertEquals(
            listOf(
                SourceToken(SourceTokenType.KEYWORD, "val", 1),
                SourceToken(SourceTokenType.IDENTIFIER, "x", 1),
                SourceToken(SourceTokenType.OPERATOR, "=", 1),
                SourceToken(SourceTokenType.LITERAL, "\"\"\"hello\nworld\"\"\"", 1),
            ),
            tokens,
        )
    }

    @Test
    fun `tracks line numbers across newlines`() {
        val tokens = SourceTokenizer.tokenize("foo\nbar\nbaz")

        assertEquals(
            listOf(
                SourceToken(SourceTokenType.IDENTIFIER, "foo", 1),
                SourceToken(SourceTokenType.IDENTIFIER, "bar", 2),
                SourceToken(SourceTokenType.IDENTIFIER, "baz", 3),
            ),
            tokens,
        )
    }

    @Test
    fun `tokenizes braces and brackets as operators`() {
        val tokens = SourceTokenizer.tokenize("{ ( ) }")

        assertEquals(
            listOf(
                SourceToken(SourceTokenType.OPERATOR, "{", 1),
                SourceToken(SourceTokenType.OPERATOR, "(", 1),
                SourceToken(SourceTokenType.OPERATOR, ")", 1),
                SourceToken(SourceTokenType.OPERATOR, "}", 1),
            ),
            tokens,
        )
    }

    @Test
    fun `handles string with escaped quote`() {
        val source = """val x = "hello \"world\"" """
        val tokens = SourceTokenizer.tokenize(source)

        assertEquals(SourceTokenType.KEYWORD, tokens[0].type)
        assertEquals(SourceTokenType.IDENTIFIER, tokens[1].type)
        assertEquals(SourceTokenType.OPERATOR, tokens[2].type)
        assertEquals(SourceTokenType.LITERAL, tokens[3].type)
    }
}
