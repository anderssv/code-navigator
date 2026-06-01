package no.f12.codenavigator.navigation.refactor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsonSupportTest {

    @Test
    fun `parses object with boolean values`() {
        val json = """{"deleted":false,"reason":"has usages"}"""

        val result = parseJsonObject(json)

        assertEquals(false, result["deleted"])
        assertEquals("has usages", result["reason"])
    }

    @Test
    fun `parses object with true boolean`() {
        val json = """{"deleted":true,"changes":[]}"""

        val result = parseJsonObject(json)

        assertEquals(true, result["deleted"])
    }

    @Test
    fun `parses object with null value`() {
        val json = """{"reason":null,"deleted":true}"""

        val result = parseJsonObject(json)

        assertNull(result["reason"])
        assertEquals(true, result["deleted"])
    }

    @Test
    fun `parses object with numeric values`() {
        val json = """{"count":42,"name":"test"}"""

        val result = parseJsonObject(json)

        assertEquals("42", result["count"])
        assertEquals("test", result["name"])
    }
}
