package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeadCodeFinderTest {

    @Test
    fun `empty call graph produces empty result`() {
        val graph = callGraph()

        val dead = DeadCodeFinder.find(graph)

        assertTrue(dead.isEmpty())
    }
    @Test
    fun `single class with no callers is dead`() {
        val graph = callGraph(
            method("com.example.Lonely", "doWork") to method("com.example.External", "process"),
            projectClasses = setOf("com.example.Lonely"),
        )

        val dead = DeadCodeFinder.find(graph)

        val deadClasses = dead.filter { it.kind == DeadCodeKind.CLASS }
        assertEquals(1, deadClasses.size)
        assertEquals("com.example.Lonely", deadClasses[0].className)
    }
    @Test
    fun `class called by another class is not dead`() {
        val graph = callGraph(
            method("com.example.Caller", "handle") to method("com.example.Service", "process"),
            projectClasses = setOf("com.example.Caller", "com.example.Service"),
        )

        val dead = DeadCodeFinder.find(graph)

        val deadClassNames = dead.filter { it.kind == DeadCodeKind.CLASS }.map { it.className }
        assertTrue("com.example.Service" !in deadClassNames, "Service is called by Caller so should not be dead")
        assertTrue("com.example.Caller" in deadClassNames, "Caller has no callers so should be dead")
    }
    @Test
    fun `class that calls others but is never called is dead`() {
        val graph = callGraph(
            method("com.example.Orphan", "run") to method("com.example.Service", "process"),
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            projectClasses = setOf("com.example.Orphan", "com.example.Service", "com.example.Controller"),
        )

        val dead = DeadCodeFinder.find(graph)

        val deadClassNames = dead.filter { it.kind == DeadCodeKind.CLASS }.map { it.className }
        assertTrue("com.example.Orphan" in deadClassNames)
        assertTrue("com.example.Controller" in deadClassNames)
        assertTrue("com.example.Service" !in deadClassNames)
    }
    @Test
    fun `self-referencing class with no external callers is dead`() {
        val graph = callGraph(
            method("com.example.Recursive", "start") to method("com.example.Recursive", "step"),
            projectClasses = setOf("com.example.Recursive"),
        )

        val dead = DeadCodeFinder.find(graph)

        val deadClassNames = dead.filter { it.kind == DeadCodeKind.CLASS }.map { it.className }
        assertTrue("com.example.Recursive" in deadClassNames, "Self-referencing class with no external callers is dead")
    }
    @Test
    fun `method with no callers from any class is dead method`() {
        val graph = callGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "unused") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.Repo"),
        )

        val dead = DeadCodeFinder.find(graph)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }
        val deadMethodNames = deadMethods.map { "${it.className}.${it.memberName}" }
        assertTrue("com.example.Service.unused" in deadMethodNames, "unused() has no callers so should be dead")
        assertTrue("com.example.Service.process" !in deadMethodNames, "process() is called by Controller")
    }
    @Test
    fun `method called by another method in a different class is not dead`() {
        val graph = callGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Controller", "handle") to method("com.example.Service", "validate"),
            projectClasses = setOf("com.example.Controller", "com.example.Service"),
        )

        val dead = DeadCodeFinder.find(graph)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }
        assertTrue(deadMethods.isEmpty(), "Both process() and validate() are called by Controller, no dead methods")
    }
    @Test
    fun `filter regex limits results to matching classes`() {
        val graph = callGraph(
            method("com.example.OrphanService", "run") to method("com.example.Repo", "save"),
            method("com.example.OrphanUtil", "help") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.OrphanService", "com.example.OrphanUtil", "com.example.Repo"),
        )

        val dead = DeadCodeFinder.find(graph, filter = Regex("Service"))

        val deadClassNames = dead.map { it.className }
        assertTrue("com.example.OrphanService" in deadClassNames)
        assertTrue("com.example.OrphanUtil" !in deadClassNames, "OrphanUtil doesn't match filter")
    }
    @Test
    fun `exclude regex removes matching classes from results`() {
        val graph = callGraph(
            method("com.example.Main", "main") to method("com.example.Service", "run"),
            method("com.example.TestHelper", "setup") to method("com.example.Service", "run"),
            projectClasses = setOf("com.example.Main", "com.example.TestHelper", "com.example.Service"),
        )

        val dead = DeadCodeFinder.find(graph, exclude = Regex("Main|Test"))

        val deadClassNames = dead.map { it.className }
        assertTrue("com.example.Main" !in deadClassNames, "Main excluded by regex")
        assertTrue("com.example.TestHelper" !in deadClassNames, "TestHelper excluded by regex")
    }
    @Test
    fun `results are sorted by kind then by class name then by member name`() {
        val graph = callGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "unusedB") to method("com.example.Repo", "save"),
            method("com.example.Service", "unusedA") to method("com.example.Repo", "save"),
            method("com.example.Zombie", "run") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.Repo", "com.example.Zombie"),
        )

        val dead = DeadCodeFinder.find(graph)

        val classes = dead.filter { it.kind == DeadCodeKind.CLASS }
        val methods = dead.filter { it.kind == DeadCodeKind.METHOD }

        assertTrue(dead.indexOf(classes.first()) < dead.indexOf(methods.first()), "CLASSes come before METHODs")
        assertEquals(listOf("com.example.Controller", "com.example.Zombie"), classes.map { it.className })
        assertEquals(listOf("unusedA", "unusedB"), methods.map { it.memberName })
    }
    @Test
    fun `method called only within same class is dead method`() {
        val graph = callGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "process") to method("com.example.Service", "helper"),
            projectClasses = setOf("com.example.Controller", "com.example.Service"),
        )

        val dead = DeadCodeFinder.find(graph)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }
        val deadMethodNames = deadMethods.map { "${it.className}.${it.memberName}" }
        assertTrue("com.example.Service.helper" in deadMethodNames, "helper() is only called within Service itself")
        assertTrue("com.example.Service.process" !in deadMethodNames, "process() is called by Controller")
    }

    private fun callGraph(
        vararg edges: Pair<MethodRef, MethodRef>,
        projectClasses: Set<String> = emptySet(),
    ): CallGraph {
        val callerToCallees = mutableMapOf<MethodRef, MutableSet<MethodRef>>()
        val sourceFiles = mutableMapOf<String, String>()

        for ((caller, callee) in edges) {
            callerToCallees.getOrPut(caller) { mutableSetOf() }.add(callee)
        }

        val allClasses = edges.flatMap { listOf(it.first.className, it.second.className) }.toSet()
        val classesWithSource = if (projectClasses.isNotEmpty()) projectClasses else allClasses
        for (cls in classesWithSource) {
            sourceFiles[cls] = "${cls.substringAfterLast('.')}.kt"
        }

        return CallGraph(callerToCallees, sourceFiles)
    }

    private fun method(className: String, methodName: String) = MethodRef(className, methodName)
}
