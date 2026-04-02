package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.callgraph.MethodRef
import no.f12.codenavigator.navigation.changedsince.ChangedSinceBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChangedSinceBuilderTest {

    @Test
    fun `single changed class with one caller shows that caller`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            projectClasses = setOf("com.example.Controller", "com.example.Service"),
        )

        val result = ChangedSinceBuilder.build(
            changedClasses = setOf(ClassName("com.example.Service")),
            graph = graph,
            projectOnly = false,
        )

        assertEquals(1, result.size)
        assertEquals(ClassName("com.example.Service"), result[0].className)
        assertEquals(1, result[0].callers.size)
        assertEquals(ClassName("com.example.Controller"), result[0].callers.first().className)
    }

    @Test
    fun `changed class with no callers shows empty blast radius`() {
        val graph = testCallGraph(
            method("com.example.Service", "process") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Service", "com.example.Repo"),
        )

        val result = ChangedSinceBuilder.build(
            changedClasses = setOf(ClassName("com.example.Service")),
            graph = graph,
            projectOnly = false,
        )

        assertEquals(1, result.size)
        assertTrue(result[0].callers.isEmpty())
    }

    @Test
    fun `multiple changed classes each show their own callers`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "process") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.Repo"),
        )

        val result = ChangedSinceBuilder.build(
            changedClasses = setOf(ClassName("com.example.Service"), ClassName("com.example.Repo")),
            graph = graph,
            projectOnly = false,
        )

        assertEquals(2, result.size)
        val serviceImpact = result.first { it.className.value == "com.example.Service" }
        val repoImpact = result.first { it.className.value == "com.example.Repo" }
        assertEquals(setOf(ClassName("com.example.Controller")), serviceImpact.callers.map { it.className }.toSet())
        assertEquals(setOf(ClassName("com.example.Service")), repoImpact.callers.map { it.className }.toSet())
    }

    @Test
    fun `callers from within the same changed class are excluded`() {
        val graph = testCallGraph(
            method("com.example.Service", "handle") to method("com.example.Service", "process"),
            method("com.example.Controller", "call") to method("com.example.Service", "handle"),
            projectClasses = setOf("com.example.Controller", "com.example.Service"),
        )

        val result = ChangedSinceBuilder.build(
            changedClasses = setOf(ClassName("com.example.Service")),
            graph = graph,
            projectOnly = false,
        )

        assertEquals(1, result.size)
        assertEquals(1, result[0].callers.size)
        assertEquals(ClassName("com.example.Controller"), result[0].callers.first().className)
    }

    @Test
    fun `respects project-only filter to exclude external callers`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("org.framework.Runner", "run") to method("com.example.Service", "process"),
            projectClasses = setOf("com.example.Controller", "com.example.Service"),
        )

        val result = ChangedSinceBuilder.build(
            changedClasses = setOf(ClassName("com.example.Service")),
            graph = graph,
            projectOnly = true,
        )

        assertEquals(1, result.size)
        assertEquals(1, result[0].callers.size)
        assertEquals(ClassName("com.example.Controller"), result[0].callers.first().className)
    }

    @Test
    fun `callers are deduplicated when multiple methods of changed class are called`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Controller", "handle") to method("com.example.Service", "validate"),
            projectClasses = setOf("com.example.Controller", "com.example.Service"),
        )

        val result = ChangedSinceBuilder.build(
            changedClasses = setOf(ClassName("com.example.Service")),
            graph = graph,
            projectOnly = false,
        )

        assertEquals(1, result.size)
        assertEquals(1, result[0].callers.size, "Same caller method calling two methods should appear once")
    }

    @Test
    fun `result is sorted by number of callers descending`() {
        val graph = testCallGraph(
            method("com.example.A", "a1") to method("com.example.Popular", "doWork"),
            method("com.example.B", "b1") to method("com.example.Popular", "doWork"),
            method("com.example.C", "c1") to method("com.example.Popular", "doWork"),
            method("com.example.A", "a1") to method("com.example.Lonely", "doWork"),
            projectClasses = setOf("com.example.A", "com.example.B", "com.example.C", "com.example.Popular", "com.example.Lonely"),
        )

        val result = ChangedSinceBuilder.build(
            changedClasses = setOf(ClassName("com.example.Popular"), ClassName("com.example.Lonely")),
            graph = graph,
            projectOnly = false,
        )

        assertEquals(2, result.size)
        assertEquals(ClassName("com.example.Popular"), result[0].className, "Most callers first")
        assertEquals(ClassName("com.example.Lonely"), result[1].className)
        assertEquals(3, result[0].callers.size)
        assertEquals(1, result[1].callers.size)
    }
}
