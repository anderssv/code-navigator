package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.callgraph.CallGraph
import no.f12.codenavigator.navigation.callgraph.CallGraphConfig
import no.f12.codenavigator.navigation.callgraph.MethodRef
import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.core.SourceSet
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
    fun `falls back to method property when pattern is absent`() {
        val config = CallGraphConfig.parse(
            mapOf("method" to "com.example.MyClass.myMethod"),
        )

        assertEquals("com.example.MyClass.myMethod", config.method)
    }

    @Test
    fun `pattern takes precedence over method when both present`() {
        val config = CallGraphConfig.parse(
            mapOf("pattern" to "MyClass.fromPattern", "method" to "MyClass.fromMethod"),
        )

        assertEquals("MyClass.fromPattern", config.method)
    }

    @Test
    fun `defaults maxdepth to 3 when not provided`() {
        val config = CallGraphConfig.parse(
            mapOf("pattern" to "com.example.MyClass.myMethod"),
        )

        assertEquals(3, config.maxDepth)
    }

    @Test
    fun `defaults projectOnly to true`() {
        val config = CallGraphConfig.parse(
            mapOf("pattern" to "com.example.MyClass.myMethod", "maxdepth" to "3")
        )

        assertEquals(true, config.projectOnly)
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

    private fun config(projectOnly: Boolean = false, filterSynthetic: Boolean = false, prodOnly: Boolean = false, testOnly: Boolean = false) =
        CallGraphConfig(
            method = ".*",
            maxDepth = 3,
            projectOnly = projectOnly,
            filterSynthetic = filterSynthetic,
            prodOnly = prodOnly,
            testOnly = testOnly,
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

    @Test
    fun `parses prod-only from properties`() {
        val config = CallGraphConfig.parse(
            mapOf("pattern" to "MyClass.doWork", "prod-only" to "true"),
        )

        assertEquals(true, config.prodOnly)
    }

    @Test
    fun `parses test-only from properties`() {
        val config = CallGraphConfig.parse(
            mapOf("pattern" to "MyClass.doWork", "test-only" to "true"),
        )

        assertEquals(true, config.testOnly)
    }

    @Test
    fun `defaults prodOnly to false when not provided`() {
        val config = CallGraphConfig.parse(
            mapOf("pattern" to "MyClass.doWork"),
        )

        assertEquals(false, config.prodOnly)
    }

    @Test
    fun `defaults testOnly to false when not provided`() {
        val config = CallGraphConfig.parse(
            mapOf("pattern" to "MyClass.doWork"),
        )

        assertEquals(false, config.testOnly)
    }

    @Test
    fun `buildFilter with prodOnly filters out test source set classes`() {
        val prodClass = ClassName("com.example.Service")
        val testClass = ClassName("com.example.ServiceTest")
        val graphWithSets = CallGraph(
            callerToCallees = mapOf(
                MethodRef(prodClass, "doWork") to setOf(MethodRef(testClass, "testDoWork")),
            ),
            sourceFiles = mapOf(
                prodClass to "Service.kt",
                testClass to "ServiceTest.kt",
            ),
            sourceSets = mapOf(
                prodClass to SourceSet.MAIN,
                testClass to SourceSet.TEST,
            ),
        )

        val filter = config(prodOnly = true).buildFilter(graphWithSets)

        assertNotNull(filter)
        assertTrue(filter(MethodRef(prodClass, "doWork")))
        assertTrue(!filter(MethodRef(testClass, "testDoWork")))
    }

    @Test
    fun `buildFilter with testOnly filters out prod source set classes`() {
        val prodClass = ClassName("com.example.Service")
        val testClass = ClassName("com.example.ServiceTest")
        val graphWithSets = CallGraph(
            callerToCallees = mapOf(
                MethodRef(prodClass, "doWork") to setOf(MethodRef(testClass, "testDoWork")),
            ),
            sourceFiles = mapOf(
                prodClass to "Service.kt",
                testClass to "ServiceTest.kt",
            ),
            sourceSets = mapOf(
                prodClass to SourceSet.MAIN,
                testClass to SourceSet.TEST,
            ),
        )

        val filter = config(testOnly = true).buildFilter(graphWithSets)

        assertNotNull(filter)
        assertTrue(!filter(MethodRef(prodClass, "doWork")))
        assertTrue(filter(MethodRef(testClass, "testDoWork")))
    }
}
