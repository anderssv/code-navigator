package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.complexity.ClassComplexityAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassComplexityAnalyzerTest {

    @Test
    fun `no matching class returns empty list`() {
        val graph = testCallGraph()

        val result = ClassComplexityAnalyzer.analyze(graph, "NonExistent", projectOnly = true)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `single outgoing call — fanOut 1, distinctOutgoing 1`() {
        val graph = testCallGraph(
            method("com.example.Service", "doWork") to method("com.example.Repo", "save"),
        )

        val result = ClassComplexityAnalyzer.analyze(graph, "Service", projectOnly = true)

        assertEquals(1, result.size)
        val c = result.first()
        assertEquals(1, c.fanOut)
        assertEquals(1, c.distinctOutgoingClasses)
        assertEquals(0, c.fanIn)
        assertEquals(0, c.distinctIncomingClasses)
    }

    @Test
    fun `single incoming call — fanIn 1, distinctIncoming 1`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "doWork"),
        )

        val result = ClassComplexityAnalyzer.analyze(graph, "Service", projectOnly = true)

        assertEquals(1, result.size)
        val c = result.first()
        assertEquals(0, c.fanOut)
        assertEquals(1, c.fanIn)
        assertEquals(1, c.distinctIncomingClasses)
    }

    @Test
    fun `self-calls are excluded from counts`() {
        val graph = testCallGraph(
            method("com.example.Service", "doWork") to method("com.example.Service", "helper"),
            method("com.example.Service", "doWork") to method("com.example.Repo", "save"),
        )

        val result = ClassComplexityAnalyzer.analyze(graph, "Service", projectOnly = true)

        val c = result.first()
        assertEquals(1, c.fanOut, "self-call should not count as outgoing")
        assertEquals(0, c.fanIn, "self-call should not count as incoming")
    }

    @Test
    fun `multiple method calls to same class count as one distinct class`() {
        val graph = testCallGraph(
            method("com.example.Service", "doWork") to method("com.example.Repo", "save"),
            method("com.example.Service", "doWork") to method("com.example.Repo", "find"),
            method("com.example.Service", "doWork") to method("com.example.Repo", "delete"),
        )

        val result = ClassComplexityAnalyzer.analyze(graph, "Service", projectOnly = true)

        val c = result.first()
        assertEquals(3, c.fanOut)
        assertEquals(1, c.distinctOutgoingClasses)
    }

    @Test
    fun `outgoingByClass groups calls by target class sorted by count desc`() {
        val graph = testCallGraph(
            method("com.example.Service", "a") to method("com.example.Repo", "save"),
            method("com.example.Service", "b") to method("com.example.Repo", "find"),
            method("com.example.Service", "c") to method("com.example.Cache", "get"),
        )

        val result = ClassComplexityAnalyzer.analyze(graph, "Service", projectOnly = true)

        val c = result.first()
        assertEquals(listOf(ClassName("com.example.Repo") to 2, ClassName("com.example.Cache") to 1), c.outgoingByClass)
    }

    @Test
    fun `incomingByClass groups calls by source class sorted by count desc`() {
        val graph = testCallGraph(
            method("com.example.Controller", "a") to method("com.example.Service", "work"),
            method("com.example.Controller", "b") to method("com.example.Service", "work"),
            method("com.example.Scheduler", "run") to method("com.example.Service", "work"),
        )

        val result = ClassComplexityAnalyzer.analyze(graph, "Service", projectOnly = true)

        val c = result.first()
        assertEquals(listOf(ClassName("com.example.Controller") to 2, ClassName("com.example.Scheduler") to 1), c.incomingByClass)
    }

    @Test
    fun `projectOnly filters edges to project classes only`() {
        val graph = testCallGraph(
            method("com.example.Service", "work") to method("java.util.List", "add"),
            method("com.example.Service", "work") to method("com.example.Repo", "save"),
            method("com.example.Controller", "handle") to method("com.example.Service", "work"),
            projectClasses = setOf("com.example.Service", "com.example.Repo", "com.example.Controller"),
        )

        val result = ClassComplexityAnalyzer.analyze(graph, "Service", projectOnly = true)

        val c = result.first()
        assertEquals(1, c.fanOut, "java.util.List should be excluded")
        assertEquals(1, c.distinctOutgoingClasses)
    }

    @Test
    fun `projectOnly false includes external classes`() {
        val graph = testCallGraph(
            method("com.example.Service", "work") to method("java.util.List", "add"),
            method("com.example.Service", "work") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Service", "com.example.Repo"),
        )

        val result = ClassComplexityAnalyzer.analyze(graph, "Service", projectOnly = false)

        val c = result.first()
        assertEquals(2, c.fanOut, "java.util.List should be included when projectOnly=false")
        assertEquals(2, c.distinctOutgoingClasses)
    }

    @Test
    fun `class pattern matches multiple classes via regex`() {
        val graph = testCallGraph(
            method("com.example.UserService", "work") to method("com.example.Repo", "save"),
            method("com.example.OrderService", "work") to method("com.example.Repo", "save"),
        )

        val result = ClassComplexityAnalyzer.analyze(graph, "Service", projectOnly = true)

        assertEquals(2, result.size)
        val classNames = result.map { it.className.value }.toSet()
        assertTrue("com.example.UserService" in classNames)
        assertTrue("com.example.OrderService" in classNames)
    }

    @Test
    fun `source file is resolved from call graph`() {
        val graph = testCallGraph(
            method("com.example.Service", "work") to method("com.example.Repo", "save"),
        )

        val result = ClassComplexityAnalyzer.analyze(graph, "Service", projectOnly = true)

        assertEquals("Service.kt", result.first().sourceFile)
    }

    @Test
    fun `generated inner classes are excluded from matching`() {
        val graph = testCallGraph(
            method("com.example.RAClient", "getInfo") to method("com.example.Repo", "save"),
            method("com.example.RAClient\$getInfo\$1", "invokeSuspend") to method("com.example.Repo", "save"),
            method("com.example.RAClient\$Companion", "create") to method("com.example.Repo", "save"),
        )

        val result = ClassComplexityAnalyzer.analyze(graph, "RAClient", projectOnly = true)

        val classNames = result.map { it.className.value }
        assertEquals(listOf("com.example.RAClient"), classNames, "Only the real class should match, not \$-inner classes")
    }

    @Test
    fun `results are sorted by fan-out descending`() {
        val graph = testCallGraph(
            method("com.example.Low", "a") to method("com.example.Ext", "x"),
            method("com.example.High", "a") to method("com.example.Ext", "x"),
            method("com.example.High", "b") to method("com.example.Ext", "y"),
            method("com.example.High", "c") to method("com.example.Other", "z"),
            method("com.example.Mid", "a") to method("com.example.Ext", "x"),
            method("com.example.Mid", "b") to method("com.example.Ext", "y"),
        )

        val result = ClassComplexityAnalyzer.analyze(graph, ".*", projectOnly = true)

        val classNames = result.map { it.className.value }
        assertEquals("com.example.High", classNames[0], "Highest fan-out first")
        assertEquals("com.example.Mid", classNames[1], "Second highest fan-out second")
        assertEquals("com.example.Low", classNames[2], "Lowest fan-out last")
    }

}
