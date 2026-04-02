package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.SourceSet
import no.f12.codenavigator.navigation.callgraph.FindUsagesConfig
import no.f12.codenavigator.navigation.callgraph.UsageKind
import no.f12.codenavigator.navigation.callgraph.UsageSite
import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FindUsagesConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "owner-class" to "com.example.MyService",
            "method" to "doStuff",
            "type" to "com.example.MyType",
            "outside-package" to "com.example.other",
            "format" to "json",
        )

        val config = FindUsagesConfig.parse(props)

        assertEquals("com.example.MyService", config.ownerClass)
        assertEquals("doStuff", config.method)
        assertEquals("com.example.MyType", config.type)
        assertEquals("com.example.other", config.outsidePackage)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `throws when both owner-class and type are absent`() {
        assertFailsWith<IllegalArgumentException> {
            FindUsagesConfig.parse(emptyMap())
        }
    }

    @Test
    fun `accepts owner-class without type`() {
        val config = FindUsagesConfig.parse(mapOf("owner-class" to "com.example.Foo"))

        assertEquals("com.example.Foo", config.ownerClass)
        assertNull(config.type)
    }

    @Test
    fun `accepts type without owner-class`() {
        val config = FindUsagesConfig.parse(mapOf("type" to "com.example.Bar"))

        assertNull(config.ownerClass)
        assertEquals("com.example.Bar", config.type)
    }

    @Test
    fun `defaults method to null when absent`() {
        val config = FindUsagesConfig.parse(mapOf("owner-class" to "com.example.Foo"))

        assertNull(config.method)
    }

    @Test
    fun `defaults outsidePackage to null when absent`() {
        val config = FindUsagesConfig.parse(mapOf("owner-class" to "com.example.Foo"))

        assertNull(config.outsidePackage)
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = FindUsagesConfig.parse(mapOf("owner-class" to "com.example.Foo"))

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = FindUsagesConfig.parse(
            mapOf(
                "owner-class" to "com.example.Foo",
                "llm" to "true",
            ),
        )

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `parses field property`() {
        val config = FindUsagesConfig.parse(
            mapOf(
                "owner-class" to "com.example.Foo",
                "field" to "accountNumber",
            ),
        )

        assertEquals("accountNumber", config.field)
        assertNull(config.method)
    }

    @Test
    fun `throws when both field and method are specified`() {
        assertFailsWith<IllegalArgumentException> {
            FindUsagesConfig.parse(
                mapOf(
                    "owner-class" to "com.example.Foo",
                    "field" to "accountNumber",
                    "method" to "doStuff",
                ),
            )
        }
    }

    @Test
    fun `throws when field is specified without owner-class`() {
        assertFailsWith<IllegalArgumentException> {
            FindUsagesConfig.parse(
                mapOf(
                    "type" to "com.example.Bar",
                    "field" to "accountNumber",
                ),
            )
        }
    }

    @Test
    fun `defaults field to null when absent`() {
        val config = FindUsagesConfig.parse(mapOf("owner-class" to "com.example.Foo"))

        assertNull(config.field)
    }

    @Test
    fun `parses prod-only flag`() {
        val config = FindUsagesConfig.parse(
            mapOf("owner-class" to "com.example.Foo", "prod-only" to "true"),
        )

        assertEquals(true, config.prodOnly)
    }

    @Test
    fun `parses test-only flag`() {
        val config = FindUsagesConfig.parse(
            mapOf("owner-class" to "com.example.Foo", "test-only" to "true"),
        )

        assertEquals(true, config.testOnly)
    }

    @Test
    fun `defaults prod-only and test-only to false`() {
        val config = FindUsagesConfig.parse(mapOf("owner-class" to "com.example.Foo"))

        assertEquals(false, config.prodOnly)
        assertEquals(false, config.testOnly)
    }

    private fun usageSite(callerClass: String, sourceSet: SourceSet?) = UsageSite(
        callerClass = ClassName(callerClass),
        callerMethod = "doWork",
        sourceFile = "Test.kt",
        targetOwner = ClassName("com.example.Target"),
        targetName = "handle",
        targetDescriptor = "()V",
        kind = UsageKind.METHOD_CALL,
        sourceSet = sourceSet,
    )

    private fun config(prodOnly: Boolean, testOnly: Boolean) = FindUsagesConfig(
        ownerClass = "com.example.Target",
        method = null,
        field = null,
        type = null,
        outsidePackage = null,
        prodOnly = prodOnly,
        testOnly = testOnly,
        format = OutputFormat.TEXT,
    )

    @Test
    fun `filterBySourceSet returns all usages when neither prodOnly nor testOnly set`() {
        val usages = listOf(
            usageSite("com.example.ProdCaller", SourceSet.MAIN),
            usageSite("com.example.TestCaller", SourceSet.TEST),
        )

        val filtered = config(prodOnly = false, testOnly = false).filterBySourceSet(usages)

        assertEquals(2, filtered.size)
    }

    @Test
    fun `filterBySourceSet with prodOnly keeps only MAIN source set usages`() {
        val usages = listOf(
            usageSite("com.example.ProdCaller", SourceSet.MAIN),
            usageSite("com.example.TestCaller", SourceSet.TEST),
        )

        val filtered = config(prodOnly = true, testOnly = false).filterBySourceSet(usages)

        assertEquals(1, filtered.size)
        assertEquals(ClassName("com.example.ProdCaller"), filtered[0].callerClass)
    }

    @Test
    fun `filterBySourceSet with testOnly keeps only TEST source set usages`() {
        val usages = listOf(
            usageSite("com.example.ProdCaller", SourceSet.MAIN),
            usageSite("com.example.TestCaller", SourceSet.TEST),
        )

        val filtered = config(prodOnly = false, testOnly = true).filterBySourceSet(usages)

        assertEquals(1, filtered.size)
        assertEquals(ClassName("com.example.TestCaller"), filtered[0].callerClass)
    }

    @Test
    fun `filterBySourceSet with prodOnly excludes usages with null source set`() {
        val usages = listOf(
            usageSite("com.example.ProdCaller", SourceSet.MAIN),
            usageSite("com.example.UnknownCaller", null),
        )

        val filtered = config(prodOnly = true, testOnly = false).filterBySourceSet(usages)

        assertEquals(1, filtered.size)
        assertEquals(ClassName("com.example.ProdCaller"), filtered[0].callerClass)
    }
}
