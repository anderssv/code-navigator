package no.f12.codenavigator.navigation.declaration

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeclarationFormatterTest {

    private val place = DeclarationInfo(
        kind = DeclarationKind.FUNCTION, fqn = "com.example.OrderService.place", name = "place",
        signature = "place(orderId: String): Int", file = "/abs/src/com/example/OrderService.kt", sourcePath = "com/example/OrderService.kt",
        startLine = 12, startColumn = 5, endLine = 13, endColumn = 80, startOffset = 300, endOffset = 420, nameLine = 13,
    )

    @Test
    fun `json round-trips through the worker boundary`() {
        val json = DeclarationInfo.listToJson(listOf(place))
        assertEquals(listOf(place), DeclarationInfo.listFromJson(json))
        assertTrue(json.contains("\"kind\":\"function\""))
        assertTrue(json.contains("\"startLine\":12"))
    }

    @Test
    fun `json escapes quotes in signatures`() {
        val tricky = place.copy(signature = "place(s: String = \"x\")")
        assertEquals(listOf(tricky), DeclarationInfo.listFromJson(DeclarationInfo.listToJson(listOf(tricky))))
    }

    @Test
    fun `text output is a table with a count`() {
        val text = DeclarationFormatter.format(listOf(place), OutputFormat.TEXT)
        assertTrue(text.contains("function"))
        assertTrue(text.contains("12-13"))
        assertTrue(text.endsWith("1 declarations found."))
        assertEquals("No declarations found.", DeclarationFormatter.format(emptyList(), OutputFormat.TEXT))
    }

    @Test
    fun `llm output carries exact positions and the range contract`() {
        val llm = DeclarationFormatter.format(listOf(place), OutputFormat.LLM)
        assertTrue(llm.contains("lines: 12:5-13:80 (name on line 13), offsets 300-420"))
        assertTrue(llm.contains("/abs/src/com/example/OrderService.kt"))
        assertTrue(llm.contains("including KDoc"))
    }
}
