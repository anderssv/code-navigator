package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.GroupBy
import no.f12.codenavigator.navigation.core.Scope
import no.f12.codenavigator.navigation.core.SourceSet
import no.f12.codenavigator.navigation.callgraph.FindUsagesConfig
import no.f12.codenavigator.navigation.callgraph.UsageKind
import no.f12.codenavigator.navigation.callgraph.UsageSite
import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `parses scope prod`() {
        val config = FindUsagesConfig.parse(
            mapOf("owner-class" to "com.example.Foo", "scope" to "prod"),
        )

        assertEquals(Scope.PROD, config.scope)
    }

    @Test
    fun `parses scope test`() {
        val config = FindUsagesConfig.parse(
            mapOf("owner-class" to "com.example.Foo", "scope" to "test"),
        )

        assertEquals(Scope.TEST, config.scope)
    }

    @Test
    fun `defaults scope to ALL`() {
        val config = FindUsagesConfig.parse(mapOf("owner-class" to "com.example.Foo"))

        assertEquals(Scope.ALL, config.scope)
    }

    @Test
    fun `defaults filterSynthetic to true when not provided`() {
        val config = FindUsagesConfig.parse(mapOf("owner-class" to "com.example.Foo"))

        assertEquals(true, config.filterSynthetic)
    }

    @Test
    fun `defaults groupBy to NONE`() {
        val config = FindUsagesConfig.parse(mapOf("owner-class" to "com.example.Foo"))

        assertEquals(GroupBy.NONE, config.groupBy)
    }

    @Test
    fun `parses group-by=file as GroupBy FILE`() {
        val config = FindUsagesConfig.parse(
            mapOf("owner-class" to "com.example.Foo", "group-by" to "file"),
        )

        assertEquals(GroupBy.FILE, config.groupBy)
    }

    @Test
    fun `defaults groupBy to NONE for unknown group-by value`() {
        val config = FindUsagesConfig.parse(
            mapOf("owner-class" to "com.example.Foo", "group-by" to "bogus"),
        )

        assertEquals(GroupBy.NONE, config.groupBy)
    }

    @Test
    fun `parses filter-synthetic as false when explicitly set`() {
        val config = FindUsagesConfig.parse(
            mapOf("owner-class" to "com.example.Foo", "filter-synthetic" to "false"),
        )

        assertEquals(false, config.filterSynthetic)
    }

    private fun usageSite(callerClass: String, sourceSet: SourceSet?, callerMethod: String = "doWork") = UsageSite(
        callerClass = ClassName(callerClass),
        callerMethod = callerMethod,
        sourceFile = "Test.kt",
        targetOwner = ClassName("com.example.Target"),
        targetName = "handle",
        targetDescriptor = "()V",
        kind = UsageKind.METHOD_CALL,
        sourceSet = sourceSet,
    )

    private fun config(scope: Scope) = FindUsagesConfig(
        ownerClass = "com.example.Target",
        method = null,
        field = null,
        type = null,
        outsidePackage = null,
        filterSynthetic = true,
        scope = scope,
        groupBy = GroupBy.NONE,
        format = OutputFormat.TEXT,
    )

    @Test
    fun `filterBySourceSet returns all usages when scope is ALL`() {
        val usages = listOf(
            usageSite("com.example.ProdCaller", SourceSet.MAIN),
            usageSite("com.example.TestCaller", SourceSet.TEST),
        )

        val filtered = config(scope = Scope.ALL).filterBySourceSet(usages)

        assertEquals(2, filtered.size)
    }

    @Test
    fun `filterBySourceSet with scope PROD keeps only MAIN source set usages`() {
        val usages = listOf(
            usageSite("com.example.ProdCaller", SourceSet.MAIN),
            usageSite("com.example.TestCaller", SourceSet.TEST),
        )

        val filtered = config(scope = Scope.PROD).filterBySourceSet(usages)

        assertEquals(1, filtered.size)
        assertEquals(ClassName("com.example.ProdCaller"), filtered[0].callerClass)
    }

    @Test
    fun `filterBySourceSet with scope TEST keeps only TEST source set usages`() {
        val usages = listOf(
            usageSite("com.example.ProdCaller", SourceSet.MAIN),
            usageSite("com.example.TestCaller", SourceSet.TEST),
        )

        val filtered = config(scope = Scope.TEST).filterBySourceSet(usages)

        assertEquals(1, filtered.size)
        assertEquals(ClassName("com.example.TestCaller"), filtered[0].callerClass)
    }

    @Test
    fun `filterBySourceSet with scope PROD keeps usages with null source set`() {
        val usages = listOf(
            usageSite("com.example.ProdCaller", SourceSet.MAIN),
            usageSite("com.example.UnknownCaller", null),
        )

        val filtered = config(scope = Scope.PROD).filterBySourceSet(usages)

        assertEquals(2, filtered.size)
    }

    // --- filterSyntheticCallers ---

    @Test
    fun `filterSyntheticCallers removes usages where caller is a generated method`() {
        val usages = listOf(
            usageSite("com.example.Caller", SourceSet.MAIN, callerMethod = "doWork"),
            usageSite("com.example.Caller", SourceSet.MAIN, callerMethod = "copy"),
            usageSite("com.example.Caller", SourceSet.MAIN, callerMethod = "component1"),
            usageSite("com.example.Caller", SourceSet.MAIN, callerMethod = "hashCode"),
            usageSite("com.example.Caller", SourceSet.MAIN, callerMethod = "equals"),
            usageSite("com.example.Caller", SourceSet.MAIN, callerMethod = "toString"),
            usageSite("com.example.Caller", SourceSet.MAIN, callerMethod = "copy\$default"),
        )

        val cfg = config(scope = Scope.ALL)
        val filtered = cfg.filterSyntheticCallers(usages)

        assertEquals(1, filtered.size)
        assertEquals("doWork", filtered[0].callerMethod)
    }

    @Test
    fun `filterSyntheticCallers returns all usages when filterSynthetic is false`() {
        val usages = listOf(
            usageSite("com.example.Caller", SourceSet.MAIN, callerMethod = "doWork"),
            usageSite("com.example.Caller", SourceSet.MAIN, callerMethod = "copy"),
            usageSite("com.example.Caller", SourceSet.MAIN, callerMethod = "component1"),
        )

        val cfg = FindUsagesConfig(
            ownerClass = "com.example.Target",
            method = null,
            field = null,
            type = null,
            outsidePackage = null,
            filterSynthetic = false,
            scope = Scope.ALL,
            groupBy = GroupBy.NONE,
            format = OutputFormat.TEXT,
        )
        val filtered = cfg.filterSyntheticCallers(usages)

        assertEquals(3, filtered.size)
    }

    @Test
    fun `filterSyntheticCallers keeps field declarations`() {
        val usages = listOf(
            usageSite("com.example.Caller", SourceSet.MAIN, callerMethod = "<field>"),
            usageSite("com.example.Caller", SourceSet.MAIN, callerMethod = "doWork"),
        )

        val cfg = config(scope = Scope.ALL)
        val filtered = cfg.filterSyntheticCallers(usages)

        assertEquals(2, filtered.size)
    }

    @Test
    fun `filterSyntheticCallers removes init and clinit`() {
        val usages = listOf(
            usageSite("com.example.Caller", SourceSet.MAIN, callerMethod = "<init>"),
            usageSite("com.example.Caller", SourceSet.MAIN, callerMethod = "<clinit>"),
            usageSite("com.example.Caller", SourceSet.MAIN, callerMethod = "doWork"),
        )

        val cfg = config(scope = Scope.ALL)
        val filtered = cfg.filterSyntheticCallers(usages)

        assertEquals(1, filtered.size)
        assertEquals("doWork", filtered[0].callerMethod)
    }

    @Test
    fun `filterSyntheticCallers removes accessor methods`() {
        val usages = listOf(
            usageSite("com.example.Caller", SourceSet.MAIN, callerMethod = "access\$doWork"),
            usageSite("com.example.Caller", SourceSet.MAIN, callerMethod = "doWork"),
        )

        val cfg = config(scope = Scope.ALL)
        val filtered = cfg.filterSyntheticCallers(usages)

        assertEquals(1, filtered.size)
        assertEquals("doWork", filtered[0].callerMethod)
    }
}
