package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.SourceSet
import no.f12.codenavigator.navigation.callgraph.UsageCollapser
import no.f12.codenavigator.navigation.callgraph.UsageKind
import no.f12.codenavigator.navigation.callgraph.UsageSite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsageCollapserTest {

    @Test
    fun `new + init for same caller and target collapses to single instantiation`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "new", "", UsageKind.TYPE_REFERENCE, SourceSet.MAIN),
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "<init>", "()V", UsageKind.METHOD_CALL, SourceSet.MAIN),
        )

        val collapsed = UsageCollapser.collapse(usages)

        assertEquals(1, collapsed.size)
        assertEquals(setOf("instantiation"), collapsed[0].kinds)
    }

    @Test
    fun `new + init + checkcast collapses to single instantiation`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "new", "", UsageKind.TYPE_REFERENCE, SourceSet.MAIN),
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "<init>", "()V", UsageKind.METHOD_CALL, SourceSet.MAIN),
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "checkcast", "", UsageKind.TYPE_REFERENCE, SourceSet.MAIN),
        )

        val collapsed = UsageCollapser.collapse(usages)

        assertEquals(1, collapsed.size)
        assertEquals(setOf("instantiation"), collapsed[0].kinds)
    }

    @Test
    fun `standalone method_call stays as method-call`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, SourceSet.MAIN),
        )

        val collapsed = UsageCollapser.collapse(usages)

        assertEquals(1, collapsed.size)
        assertEquals(setOf("method-call"), collapsed[0].kinds)
    }

    @Test
    fun `standalone field_access stays as field-access`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "name", "Ljava/lang/String;", UsageKind.FIELD_ACCESS, SourceSet.MAIN),
        )

        val collapsed = UsageCollapser.collapse(usages)

        assertEquals(1, collapsed.size)
        assertEquals(setOf("field-access"), collapsed[0].kinds)
    }

    @Test
    fun `lambda caller class collapses to enclosing class`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Service\$getCurrentStatus\$2"), "invokeSuspend", "Service.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, SourceSet.MAIN),
        )

        val collapsed = UsageCollapser.collapse(usages)

        assertEquals(1, collapsed.size)
        assertEquals(ClassName("com.example.Service"), collapsed[0].callerClass)
    }

    @Test
    fun `multiple kinds from same caller-method-target merge into combined set`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, SourceSet.MAIN),
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "name", "Ljava/lang/String;", UsageKind.FIELD_ACCESS, SourceSet.MAIN),
        )

        val collapsed = UsageCollapser.collapse(usages)

        assertEquals(1, collapsed.size)
        assertEquals(setOf("field-access", "method-call"), collapsed[0].kinds)
    }

    @Test
    fun `usages from different callers stay separate`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.A"), "a", "A.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, SourceSet.MAIN),
            UsageSite(ClassName("com.example.B"), "b", "B.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, SourceSet.MAIN),
        )

        val collapsed = UsageCollapser.collapse(usages)

        assertEquals(2, collapsed.size)
    }

    @Test
    fun `collapsing preserves sourceFile and sourceSet`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "new", "", UsageKind.TYPE_REFERENCE, SourceSet.TEST),
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "<init>", "()V", UsageKind.METHOD_CALL, SourceSet.TEST),
        )

        val collapsed = UsageCollapser.collapse(usages)

        assertEquals("Caller.kt", collapsed[0].sourceFile)
        assertEquals(SourceSet.TEST, collapsed[0].sourceSet)
    }

    @Test
    fun `instantiation + method call keeps both kinds`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "new", "", UsageKind.TYPE_REFERENCE, SourceSet.MAIN),
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "<init>", "()V", UsageKind.METHOD_CALL, SourceSet.MAIN),
            UsageSite(ClassName("com.example.Caller"), "doWork", "Caller.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, SourceSet.MAIN),
        )

        val collapsed = UsageCollapser.collapse(usages)

        assertEquals(1, collapsed.size)
        assertEquals(setOf("instantiation", "method-call"), collapsed[0].kinds)
    }

    @Test
    fun `deeply nested lambda collapses through multiple levels`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Service\$getCurrentStatus\$2\$raClientDeferred\$1"), "invokeSuspend", "Service.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, SourceSet.MAIN),
        )

        val collapsed = UsageCollapser.collapse(usages)

        assertEquals(ClassName("com.example.Service"), collapsed[0].callerClass)
    }

    @Test
    fun `empty input returns empty output`() {
        val collapsed = UsageCollapser.collapse(emptyList())

        assertTrue(collapsed.isEmpty())
    }

    @Test
    fun `output is sorted by callerClass then callerMethod`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Z"), "z", "Z.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, SourceSet.MAIN),
            UsageSite(ClassName("com.example.A"), "a", "A.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, SourceSet.MAIN),
        )

        val collapsed = UsageCollapser.collapse(usages)

        assertEquals(ClassName("com.example.A"), collapsed[0].callerClass)
        assertEquals(ClassName("com.example.Z"), collapsed[1].callerClass)
    }

    @Test
    fun `self-referential entries are filtered out`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Target"), "doWork", "Target.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, SourceSet.MAIN),
            UsageSite(ClassName("com.example.Caller"), "run", "Caller.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, SourceSet.MAIN),
        )

        val collapsed = UsageCollapser.collapse(usages)

        assertEquals(1, collapsed.size)
        assertEquals(ClassName("com.example.Caller"), collapsed[0].callerClass)
    }

    @Test
    fun `inner class self-referential entries are filtered`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Target\$Inner"), "getField", "Target.kt", ClassName("com.example.Target\$Inner"), "field", "", UsageKind.FIELD_ACCESS, SourceSet.MAIN),
            UsageSite(ClassName("com.example.Caller"), "run", "Caller.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, SourceSet.MAIN),
        )

        val collapsed = UsageCollapser.collapse(usages)

        assertEquals(1, collapsed.size)
        assertEquals(ClassName("com.example.Caller"), collapsed[0].callerClass)
    }

    @Test
    fun `coroutine methods collapse to coroutine marker`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Service\$getCurrentStatus\$2"), "invoke", "Service.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, SourceSet.MAIN),
            UsageSite(ClassName("com.example.Service\$getCurrentStatus\$2"), "invokeSuspend", "Service.kt", ClassName("com.example.Target"), "process", "()V", UsageKind.METHOD_CALL, SourceSet.MAIN),
            UsageSite(ClassName("com.example.Service\$getCurrentStatus\$2"), "create", "Service.kt", ClassName("com.example.Target"), "new", "", UsageKind.TYPE_REFERENCE, SourceSet.MAIN),
        )

        val collapsed = UsageCollapser.collapse(usages)

        assertEquals(1, collapsed.size)
        assertEquals(ClassName("com.example.Service"), collapsed[0].callerClass)
        assertEquals("<coroutine>", collapsed[0].callerMethod)
    }

    @Test
    fun `target owner lambda classes collapse via collapseLambda`() {
        val usages = listOf(
            UsageSite(ClassName("com.example.Caller"), "run", "Caller.kt", ClassName("com.example.Target\$doWork\$1"), "process", "()V", UsageKind.METHOD_CALL, SourceSet.MAIN),
            UsageSite(ClassName("com.example.Caller"), "run", "Caller.kt", ClassName("com.example.Target"), "other", "()V", UsageKind.METHOD_CALL, SourceSet.MAIN),
        )

        val collapsed = UsageCollapser.collapse(usages)

        // Target$doWork$1 collapses via collapseLambda to Target, merging with Target
        assertEquals(1, collapsed.size)
        assertEquals(ClassName("com.example.Target"), collapsed[0].targetOwner)
    }
}
