package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.callgraph.CallGraph
import no.f12.codenavigator.navigation.callgraph.MethodRef
import no.f12.codenavigator.navigation.context.ContextConfig
import no.f12.codenavigator.navigation.core.Scope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ContextConfigTest {

    @Test
    fun `parses pattern from property map`() {
        val config = ContextConfig.parse(mapOf("pattern" to "MyService"))

        assertEquals("MyService", config.pattern)
    }

    @Test
    fun `throws when pattern is missing`() {
        assertFailsWith<IllegalArgumentException> {
            ContextConfig.parse(emptyMap())
        }
    }

    @Test
    fun `parses maxdepth from property map`() {
        val config = ContextConfig.parse(mapOf("pattern" to "Foo", "maxdepth" to "5"))

        assertEquals(5, config.maxDepth)
    }

    @Test
    fun `defaults maxdepth to 2`() {
        val config = ContextConfig.parse(mapOf("pattern" to "Foo"))

        assertEquals(2, config.maxDepth)
    }

    @Test
    fun `parses project-only from property map`() {
        val config = ContextConfig.parse(mapOf("pattern" to "Foo", "project-only" to "false"))

        assertEquals(false, config.projectOnly)
    }

    @Test
    fun `defaults project-only to true`() {
        val config = ContextConfig.parse(mapOf("pattern" to "Foo"))

        assertEquals(true, config.projectOnly)
    }

    @Test
    fun `parses filter-synthetic from property map`() {
        val config = ContextConfig.parse(mapOf("pattern" to "Foo", "filter-synthetic" to "false"))

        assertEquals(false, config.filterSynthetic)
    }

    @Test
    fun `defaults filter-synthetic to true`() {
        val config = ContextConfig.parse(mapOf("pattern" to "Foo"))

        assertEquals(true, config.filterSynthetic)
    }

    @Test
    fun `parses scope prod from property map`() {
        val config = ContextConfig.parse(mapOf("pattern" to "Foo", "scope" to "prod"))

        assertEquals(Scope.PROD, config.scope)
    }

    @Test
    fun `parses scope test from property map`() {
        val config = ContextConfig.parse(mapOf("pattern" to "Foo", "scope" to "test"))

        assertEquals(Scope.TEST, config.scope)
    }

    @Test
    fun `defaults format to TEXT`() {
        val config = ContextConfig.parse(mapOf("pattern" to "Foo"))

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses JSON format`() {
        val config = ContextConfig.parse(mapOf("pattern" to "Foo", "format" to "json"))

        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = ContextConfig.parse(mapOf("pattern" to "Foo", "llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `buildFilter returns null when no filters active`() {
        val config = ContextConfig.parse(
            mapOf("pattern" to "Foo", "project-only" to "false", "filter-synthetic" to "false"),
        )
        val graph = CallGraph(emptyMap())

        val filter = config.buildFilter(graph)

        assertNull(filter)
    }

    @Test
    fun `buildFilter filters synthetic methods when filterSynthetic is true`() {
        val config = ContextConfig.parse(
            mapOf("pattern" to "Foo", "project-only" to "false", "filter-synthetic" to "true"),
        )
        val graph = CallGraph(emptyMap())

        val filter = config.buildFilter(graph)

        assertNotNull(filter)
        assertEquals(true, filter(MethodRef(ClassName("com.example.Foo"), "doWork")))
        assertEquals(false, filter(MethodRef(ClassName("com.example.Foo"), "equals")))
    }
}
