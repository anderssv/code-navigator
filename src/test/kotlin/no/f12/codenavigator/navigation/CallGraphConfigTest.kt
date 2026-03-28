package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CallGraphConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "pattern" to "com.example.MyClass.myMethod",
            "maxdepth" to "5",
            "project-only" to "true",
            "format" to "json",
            "llm" to "false",
        )

        val config = CallGraphConfig.parse(props)

        assertEquals("com.example.MyClass.myMethod", config.method)
        assertEquals(5, config.maxDepth)
        assertEquals(true, config.projectOnly)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `throws when pattern is missing`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            CallGraphConfig.parse(mapOf("maxdepth" to "3"))
        }

        assertEquals(true, exception.message?.contains("pattern"))
    }

    @Test
    fun `defaults maxdepth to 3 when not provided`() {
        val config = CallGraphConfig.parse(
            mapOf("pattern" to "com.example.MyClass.myMethod"),
        )

        assertEquals(3, config.maxDepth)
    }

    @Test
    fun `defaults projectOnly to false`() {
        val config = CallGraphConfig.parse(
            mapOf("pattern" to "com.example.MyClass.myMethod", "maxdepth" to "3")
        )

        assertEquals(false, config.projectOnly)
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = CallGraphConfig.parse(
            mapOf("pattern" to "com.example.MyClass.myMethod", "maxdepth" to "3")
        )

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = CallGraphConfig.parse(
            mapOf("pattern" to "com.example.MyClass.myMethod", "maxdepth" to "3", "llm" to "true")
        )

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `parses filter-synthetic as false when explicitly set`() {
        val config = CallGraphConfig.parse(
            mapOf("pattern" to "com.example.MyClass.myMethod", "filter-synthetic" to "false"),
        )

        assertEquals(false, config.filterSynthetic)
    }

    @Test
    fun `defaults filterSynthetic to true when not provided`() {
        val config = CallGraphConfig.parse(
            mapOf("pattern" to "com.example.MyClass.myMethod"),
        )

        assertEquals(true, config.filterSynthetic)
    }

    private val projectClass = ClassName("com.example.MyClass")
    private val externalClass = ClassName("org.external.Lib")
    private val graph = CallGraph(
        callerToCallees = mapOf(
            MethodRef(projectClass, "doWork") to setOf(MethodRef(externalClass, "helper")),
        ),
        sourceFiles = mapOf(projectClass to "MyClass.kt"),
    )

    private fun config(projectOnly: Boolean = false, filterSynthetic: Boolean = false) =
        CallGraphConfig(
            method = ".*",
            maxDepth = 3,
            projectOnly = projectOnly,
            filterSynthetic = filterSynthetic,
            format = OutputFormat.TEXT,
        )

    @Test
    fun `buildFilter returns null when no filters active`() {
        assertNull(config(projectOnly = false, filterSynthetic = false).buildFilter(graph))
    }

    @Test
    fun `buildFilter with projectOnly rejects external classes`() {
        val filter = config(projectOnly = true).buildFilter(graph)

        assertNotNull(filter)
        assertTrue(filter(MethodRef(projectClass, "doWork")))
        assertTrue(!filter(MethodRef(externalClass, "helper")))
    }

    @Test
    fun `buildFilter with filterSynthetic rejects generated methods`() {
        val filter = config(filterSynthetic = true).buildFilter(graph)

        assertNotNull(filter)
        assertTrue(filter(MethodRef(projectClass, "doWork")))
        assertTrue(!filter(MethodRef(projectClass, "access\$doWork")))
    }

    @Test
    fun `buildFilter with both filters rejects external and synthetic`() {
        val filter = config(projectOnly = true, filterSynthetic = true).buildFilter(graph)

        assertNotNull(filter)
        assertTrue(filter(MethodRef(projectClass, "doWork")))
        assertTrue(!filter(MethodRef(externalClass, "helper")))
        assertTrue(!filter(MethodRef(projectClass, "access\$doWork")))
    }
}
