package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.SourceSet
import no.f12.codenavigator.navigation.callgraph.UsageFormatter
import no.f12.codenavigator.navigation.callgraph.UsageKind
import no.f12.codenavigator.navigation.callgraph.UsageSite
import no.f12.codenavigator.navigation.callgraph.CollapsedUsage
import no.f12.codenavigator.navigation.callgraph.SmartUsageResult
import no.f12.codenavigator.navigation.interfaces.ImplementorInfo
import no.f12.codenavigator.formatting.JsonFormatter
import no.f12.codenavigator.formatting.LlmFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsageFormatterTest {

    // [TEST] JSON formats single method call usage
    @Test
    fun `JSON formats single method call usage`() {
        val usages = listOf(
            UsageSite(
                callerClass = ClassName("com.example.Caller"),
                callerMethod = "doWork",
                sourceFile = "Caller.kt",
                targetOwner = ClassName("com.example.Target"),
                targetName = "process",
                targetDescriptor = "()V",
                kind = UsageKind.METHOD_CALL,
                sourceSet = null,
            ),
        )

        val json = JsonFormatter.formatUsages(usages)

        assertTrue(json.contains("\"callerClass\":\"com.example.Caller\""))
        assertTrue(json.contains("\"callerMethod\":\"doWork\""))
        assertTrue(json.contains("\"targetOwner\":\"com.example.Target\""))
        assertTrue(json.contains("\"targetMethod\":\"process\""))
        assertTrue(json.contains("\"targetDescriptor\":\"()V\""))
        assertTrue(json.contains("\"sourceFile\":\"Caller.kt\""))
        assertTrue(json.contains("\"kind\":\"method_call\""))
    }

    // [TEST] JSON formats empty usages as empty array
    @Test
    fun `JSON formats empty usages as empty array`() {
        val json = JsonFormatter.formatUsages(emptyList())

        assertEquals("[]", json)
    }

    // [TEST] LLM formats single method call usage
    @Test
    fun `LLM formats single method call usage`() {
        val usages = listOf(
            UsageSite(
                callerClass = ClassName("com.example.Caller"),
                callerMethod = "doWork",
                sourceFile = "Caller.kt",
                targetOwner = ClassName("com.example.Target"),
                targetName = "process",
                targetDescriptor = "()V",
                kind = UsageKind.METHOD_CALL,
                sourceSet = null,
            ),
        )

        val result = LlmFormatter.formatUsages(usages)

        assertEquals("com.example.Caller.doWork -> com.example.Target.process()V method_call Caller.kt", result)
    }

    // [TEST] LLM formats multiple usages
    @Test
    fun `LLM formats multiple usages on separate lines`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.A"), "fromA", "A.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, null),
            UsageSite(ClassName("com.example.B"), "fromB", "B.kt", ClassName("com.example.Target"), "name", "Ljava/lang/String;", UsageKind.FIELD_ACCESS, null),
        )

        val result = LlmFormatter.formatUsages(usages)

        val lines = result.lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("com.example.A.fromA"))
        assertTrue(lines[1].contains("com.example.B.fromB"))
    }

    // [TEST] TEXT formats usages as table
    @Test
    fun `TEXT formats usages as readable list`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, null),
        )

        val result = UsageFormatter.format(usages)

        assertTrue(result.contains("com.example.Caller.doWork"))
        assertTrue(result.contains("com.example.Target.process"))
        assertTrue(result.contains("Caller.kt"))
    }

    // [TEST] TEXT formats empty usages with message
    @Test
    fun `TEXT shows message when no usages found`() {
        val result = UsageFormatter.format(emptyList())

        assertEquals("No usages found.", result)
    }

    // [TEST] JSON sorts usages by caller class then method
    @Test
    fun `JSON sorts usages by caller class then method`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Z"), "z", "Z.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, null),
            UsageSite(ClassName("com.example.A"), "a", "A.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, null),
        )

        val json = JsonFormatter.formatUsages(usages)

        val aIndex = json.indexOf("com.example.A")
        val zIndex = json.indexOf("com.example.Z")
        assertTrue(aIndex < zIndex, "Expected A before Z in sorted output")
    }

    // [TEST] noResultsHints with ownerClass param suggests trying type
    // [TEST] noResultsHints with type param suggests checking FQN
    // [TEST] noResultsTarget includes ownerClass.method

    @Test
    fun `TEXT summary shows single file with 1 reference for single usage`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, null),
        )

        val result = UsageFormatter.formatSummary(usages)

        assertEquals("Caller.kt (1 reference)", result)
    }

    @Test
    fun `TEXT summary shows no-usages message when list is empty`() {
        val result = UsageFormatter.formatSummary(emptyList())

        assertEquals("No usages found.", result)
    }

    @Test
    fun `TEXT summary collapses multiple usages from same file into one line with total count`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, null),
            UsageSite(ClassName("com.example.Caller"), "other", "Caller.kt", ClassName("com.example.Target"), "name", "Ljava/lang/String;", UsageKind.FIELD_ACCESS, null),
            UsageSite(ClassName("com.example.Caller"), "third", "Caller.kt", ClassName("com.example.Target"), "run", "()V", UsageKind.METHOD_CALL, null),
        )

        val result = UsageFormatter.formatSummary(usages)

        assertEquals("Caller.kt (3 references)", result)
    }

    @Test
    fun `TEXT summary shows one line per distinct source file`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.A"), "a", "A.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, null),
            UsageSite(ClassName("com.example.B"), "b", "B.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, null),
        )

        val result = UsageFormatter.formatSummary(usages)

        assertEquals("A.kt (1 reference)\nB.kt (1 reference)", result)
    }

    @Test
    fun `TEXT summary sorts files alphabetically`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Z"), "z", "Zebra.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, null),
            UsageSite(ClassName("com.example.A"), "a", "Apple.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, null),
            UsageSite(ClassName("com.example.M"), "m", "Mango.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, null),
        )

        val result = UsageFormatter.formatSummary(usages)

        assertEquals("Apple.kt (1 reference)\nMango.kt (1 reference)\nZebra.kt (1 reference)", result)
    }

    @Test
    fun `JSON summary returns empty array for empty list`() {
        val json = JsonFormatter.formatUsagesSummary(emptyList())

        assertEquals("[]", json)
    }

    @Test
    fun `JSON summary returns array of sourceFile and referenceCount objects`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.A"), "a", "A.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, null),
            UsageSite(ClassName("com.example.A"), "b", "A.kt", ClassName("com.example.Target"), "name", "Ljava/lang/String;", UsageKind.FIELD_ACCESS, null),
            UsageSite(ClassName("com.example.C"), "c", "Beta.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, null),
        )

        val json = JsonFormatter.formatUsagesSummary(usages)

        assertTrue(json.contains("\"sourceFile\":\"A.kt\""), "Expected A.kt entry in: $json")
        assertTrue(json.contains("\"sourceFile\":\"Beta.kt\""), "Expected Beta.kt entry in: $json")
        assertTrue(json.contains("\"referenceCount\":2"), "Expected referenceCount 2 in: $json")
        assertTrue(json.contains("\"referenceCount\":1"), "Expected referenceCount 1 in: $json")
        assertTrue(json.indexOf("A.kt") < json.indexOf("Beta.kt"), "Expected alphabetical ordering in: $json")
    }

    @Test
    fun `LLM summary returns empty string for empty list`() {
        val result = LlmFormatter.formatUsagesSummary(emptyList())

        assertEquals("", result)
    }

    @Test
    fun `LLM summary returns one line per file with count`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.A"), "a", "Apple.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, null),
            UsageSite(ClassName("com.example.A"), "b", "Apple.kt", ClassName("com.example.Target"), "name", "Ljava/lang/String;", UsageKind.FIELD_ACCESS, null),
            UsageSite(ClassName("com.example.C"), "c", "Beta.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, null),
        )

        val result = LlmFormatter.formatUsagesSummary(usages)

        assertEquals("Apple.kt 2\nBeta.kt 1", result)
    }

    @Test
    fun `noResultsTarget includes ownerClass and method`() {
        val target = UsageFormatter.noResultsTarget(ownerClass = "com.example.Target", method = "process", field = null, type = null)

        assertTrue(target.contains("com.example.Target.process"), "Should include owner.method")
    }

    @Test
    fun `noResultsHints with type suggests checking FQN`() {
        val hints = UsageFormatter.noResultsHints(ownerClass = null, method = null, field = null, type = "ContextKt")

        assertTrue(hints.any { it.contains("fully-qualified") }, "Should suggest checking FQN")
    }

    @Test
    fun `noResultsHints with ownerClass suggests trying type`() {
        val hints = UsageFormatter.noResultsHints(ownerClass = "com.example.Target", method = null, field = null, type = null)

        assertTrue(hints.any { it.contains("type") }, "Should suggest trying -Ptype")
    }

    @Test
    fun `noResultsHints with method suggests trying field`() {
        val hints = UsageFormatter.noResultsHints(ownerClass = "com.example.Target", method = "accountNumber", field = null, type = null)

        assertTrue(hints.any { it.contains("-Pfield=accountNumber") }, "Should suggest trying -Pfield")
    }

    @Test
    fun `noResultsTarget with field includes field in target`() {
        val target = UsageFormatter.noResultsTarget(ownerClass = "com.example.Target", method = null, field = "accountNumber", type = null)

        assertTrue(target.contains("com.example.Target.accountNumber"), "Should include owner.field")
    }

    @Test
    fun `TEXT formats usage with source set tag`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, SourceSet.TEST),
        )

        val result = UsageFormatter.format(usages)

        assertTrue(result.contains("[test]"))
    }

    @Test
    fun `TEXT formats usage without source set tag when null`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, null),
        )

        val result = UsageFormatter.format(usages)

        assertFalse(result.contains("[test]"))
        assertFalse(result.contains("[prod]"))
    }

    @Test
    fun `LLM formats usage with source set tag`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, SourceSet.TEST),
        )

        val result = LlmFormatter.formatUsages(usages)

        assertTrue(result.contains("[test]"))
    }

    @Test
    fun `LLM formats usage without source set tag when null`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, null),
        )

        val result = LlmFormatter.formatUsages(usages)

        assertFalse(result.contains("[test]"))
        assertFalse(result.contains("[prod]"))
    }

    @Test
    fun `JSON includes sourceSet field when present`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, SourceSet.TEST),
        )

        val json = JsonFormatter.formatUsages(usages)

        assertTrue(json.contains("\"sourceSet\":\"test\""))
    }

    @Test
    fun `JSON omits sourceSet field when null`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, null),
        )

        val json = JsonFormatter.formatUsages(usages)

        assertFalse(json.contains("sourceSet"))
    }

    // --- Collapsed usage formatting ---

    @Test
    fun `LLM formats collapsed usage with single kind`() {
        val collapsed = listOf(
            CollapsedUsage(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), setOf("method-call"), SourceSet.MAIN),
        )

        val result = LlmFormatter.formatCollapsedUsages(collapsed)

        assertEquals("com.example.Caller.doWork -> com.example.Target method-call Caller.kt [prod]", result)
    }

    @Test
    fun `LLM formats collapsed usage with multiple kinds`() {
        val collapsed = listOf(
            CollapsedUsage(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), setOf("instantiation", "method-call"), SourceSet.MAIN),
        )

        val result = LlmFormatter.formatCollapsedUsages(collapsed)

        assertEquals("com.example.Caller.doWork -> com.example.Target instantiation,method-call Caller.kt [prod]", result)
    }

    @Test
    fun `TEXT formats collapsed usages`() {
        val collapsed = listOf(
            CollapsedUsage(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), setOf("instantiation"), SourceSet.MAIN),
        )

        val result = UsageFormatter.formatCollapsed(collapsed)

        assertTrue(result.contains("com.example.Caller.doWork"))
        assertTrue(result.contains("com.example.Target"))
        assertTrue(result.contains("instantiation"))
        assertTrue(result.contains("Caller.kt"))
    }

    @Test
    fun `JSON formats collapsed usages`() {
        val collapsed = listOf(
            CollapsedUsage(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), setOf("instantiation", "method-call"), SourceSet.TEST),
        )

        val json = JsonFormatter.formatCollapsedUsages(collapsed)

        assertTrue(json.contains("\"callerClass\":\"com.example.Caller\""))
        assertTrue(json.contains("\"callerMethod\":\"doWork\""))
        assertTrue(json.contains("\"targetOwner\":\"com.example.Target\""))
        assertTrue(json.contains("\"kinds\":[\"instantiation\",\"method-call\"]"))
        assertTrue(json.contains("\"sourceSet\":\"test\""))
    }

    @Test
    fun `LLM collapsed formats empty list as empty string`() {
        val result = LlmFormatter.formatCollapsedUsages(emptyList())

        assertEquals("", result)
    }

    @Test
    fun `TEXT collapsed formats empty list as no usages message`() {
        val result = UsageFormatter.formatCollapsed(emptyList())

        assertEquals("No usages found.", result)
    }

    @Test
    fun `JSON collapsed formats empty list as empty array`() {
        val result = JsonFormatter.formatCollapsedUsages(emptyList())

        assertEquals("[]", result)
    }

    // --- Smart usages formatting ---

    private fun smartResult() = SmartUsageResult(
        implementations = listOf(
            ImplementorInfo(ClassName("com.example.TargetImpl"), "TargetImpl.kt"),
            ImplementorInfo(ClassName("com.example.FakeTarget"), "FakeTarget.kt"),
        ),
        usages = emptyList(), // usages passed separately as collapsed
    )

    private fun smartCollapsed() = listOf(
        CollapsedUsage(ClassName("com.example.Service"), "run", "Service.kt", ClassName("com.example.Target"), setOf("method-call"), SourceSet.MAIN),
    )

    @Test
    fun `LLM formats smart usages with impl and ref lines`() {
        val result = LlmFormatter.formatSmartUsages(smartResult(), smartCollapsed())

        assertTrue(result.contains("[impl] com.example.TargetImpl TargetImpl.kt"))
        assertTrue(result.contains("[impl] com.example.FakeTarget FakeTarget.kt"))
        assertTrue(result.contains("[ref] com.example.Service.run -> com.example.Target method-call Service.kt [prod]"))
    }

    @Test
    fun `TEXT formats smart usages with impl and ref lines`() {
        val result = UsageFormatter.formatSmartUsages(smartResult(), smartCollapsed())

        assertTrue(result.contains("[impl] com.example.TargetImpl"))
        assertTrue(result.contains("[ref] com.example.Service.run"))
    }

    @Test
    fun `JSON formats smart usages with implementations and usages`() {
        val result = JsonFormatter.formatSmartUsages(smartResult(), smartCollapsed())

        assertTrue(result.contains("\"implementations\""))
        assertTrue(result.contains("\"TargetImpl.kt\""))
        assertTrue(result.contains("\"usages\""))
        assertTrue(result.contains("\"method-call\""))
    }

    @Test
    fun `smart usages with no implementations falls back to ref-only`() {
        val emptyImpls = SmartUsageResult(emptyList(), emptyList())
        val result = LlmFormatter.formatSmartUsages(emptyImpls, smartCollapsed())

        assertFalse(result.contains("[impl]"))
        assertTrue(result.contains("[ref]"))
    }

    // --- Disambiguation hint ---

    @Test
    fun `LLM smart usages shows disambiguation hint when multiple types matched`() {
        val smart = SmartUsageResult(
            implementations = listOf(ImplementorInfo(ClassName("com.example.TargetImpl"), "TargetImpl.kt")),
            usages = emptyList(),
            matchedTypes = listOf(ClassName("com.example.Target"), ClassName("com.example.TargetImpl")),
            interfaceTypes = setOf(ClassName("com.example.Target")),
        )
        val collapsed = listOf(
            CollapsedUsage(ClassName("com.example.Service"), "run", "Service.kt", ClassName("com.example.Target"), setOf("method-call"), SourceSet.MAIN),
        )

        val result = LlmFormatter.formatSmartUsages(smart, collapsed)

        assertTrue(result.contains("[matched]"), "Should contain matched header")
        assertTrue(result.contains("com.example.Target (interface)"), "Should label interface")
        assertTrue(result.contains("com.example.TargetImpl"), "Should list non-interface type")
        assertTrue(result.contains("[hint]"), "Should contain hint")
        assertTrue(result.contains("-Ptype=com.example.Target"), "Hint should suggest FQN")
    }

    @Test
    fun `TEXT smart usages shows disambiguation hint when multiple types matched`() {
        val smart = SmartUsageResult(
            implementations = listOf(ImplementorInfo(ClassName("com.example.TargetImpl"), "TargetImpl.kt")),
            usages = emptyList(),
            matchedTypes = listOf(ClassName("com.example.Target"), ClassName("com.example.TargetImpl")),
            interfaceTypes = setOf(ClassName("com.example.Target")),
        )
        val collapsed = listOf(
            CollapsedUsage(ClassName("com.example.Service"), "run", "Service.kt", ClassName("com.example.Target"), setOf("method-call"), SourceSet.MAIN),
        )

        val result = UsageFormatter.formatSmartUsages(smart, collapsed)

        assertTrue(result.contains("[matched]"), "Should contain matched header")
        assertTrue(result.contains("[hint]"), "Should contain hint")
    }

    @Test
    fun `smart usages omits disambiguation hint when single type matched`() {
        val smart = SmartUsageResult(
            implementations = listOf(ImplementorInfo(ClassName("com.example.TargetImpl"), "TargetImpl.kt")),
            usages = emptyList(),
            matchedTypes = listOf(ClassName("com.example.Target")),
            interfaceTypes = setOf(ClassName("com.example.Target")),
        )

        val result = LlmFormatter.formatSmartUsages(smart, smartCollapsed())

        assertFalse(result.contains("[matched]"), "Should not show matched for single type")
        assertFalse(result.contains("[hint]"), "Should not show hint for single type")
    }

    @Test
    fun `smart usages omits disambiguation hint when no matched types`() {
        val result = LlmFormatter.formatSmartUsages(smartResult(), smartCollapsed())

        assertFalse(result.contains("[matched]"), "Should not show matched when matchedTypes is empty")
    }

    @Test
    fun `JSON smart usages includes matchedTypes and interfaceTypes`() {
        val smart = SmartUsageResult(
            implementations = listOf(ImplementorInfo(ClassName("com.example.TargetImpl"), "TargetImpl.kt")),
            usages = emptyList(),
            matchedTypes = listOf(ClassName("com.example.Target"), ClassName("com.example.TargetImpl")),
            interfaceTypes = setOf(ClassName("com.example.Target")),
        )

        val json = JsonFormatter.formatSmartUsages(smart, smartCollapsed())

        assertTrue(json.contains("\"matchedTypes\""), "Should include matchedTypes")
        assertTrue(json.contains("\"interfaceTypes\""), "Should include interfaceTypes")
        assertTrue(json.contains("com.example.Target"), "Should include interface type")
    }
}
