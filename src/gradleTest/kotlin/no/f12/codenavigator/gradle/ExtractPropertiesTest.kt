package no.f12.codenavigator.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExtractPropertiesTest {

    @Test
    fun `extracts matching properties from source`() {
        val source = mapOf("type" to "com.example.Service", "scope" to "prod")

        val result = extractProperties(source, listOf("type", "scope", "method"), emptyList())

        assertEquals("com.example.Service", result["type"])
        assertEquals("prod", result["scope"])
        assertEquals(null, result["method"])
        assertEquals(2, result.size)
    }

    @Test
    fun `ignores properties not in the requested names`() {
        val source = mapOf("type" to "Service", "unrelated" to "value")

        val result = extractProperties(source, listOf("type"), emptyList())

        assertEquals(mapOf("type" to "Service"), result)
    }

    @Test
    fun `does not pick up Gradle-internal properties like jar task reference`() {
        // Simulates what happens when Gradle's findProperty("jar") returns "task ':jar'"
        // The source should only contain CLI -P properties, not project-level properties.
        // This test verifies the function itself doesn't invent values.
        val source = mapOf<String, String>() // No CLI properties passed

        val result = extractProperties(source, listOf("jar", "pattern", "type"), emptyList())

        assertNull(result["jar"], "jar should not be present when not passed via CLI")
        assertEquals(emptyMap(), result)
    }

    @Test
    fun `extracts jar when explicitly passed via CLI`() {
        val source = mapOf("jar" to "/path/to/lib.jar", "pattern" to "MyClass")

        val result = extractProperties(source, listOf("jar", "pattern"), emptyList())

        assertEquals("/path/to/lib.jar", result["jar"])
        assertEquals("MyClass", result["pattern"])
    }

    @Test
    fun `extracts flags as null values`() {
        val source = mapOf("llm" to "", "raw" to "")

        val result = extractProperties(source, emptyList(), listOf("llm", "raw", "verbose"))

        assertNull(result["llm"])
        assertNull(result["raw"])
        assertEquals(false, result.containsKey("verbose"))
        assertEquals(2, result.size)
    }

    @Test
    fun `handles mix of properties and flags`() {
        val source = mapOf("type" to "Service", "llm" to "")

        val result = extractProperties(source, listOf("type", "method"), listOf("llm", "raw"))

        assertEquals("Service", result["type"])
        assertNull(result["llm"])
        assertEquals(2, result.size)
    }
}
