package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeadCodeFinderTest {

    private fun findDead(
        graph: CallGraph,
        filter: Regex? = null,
        exclude: Regex? = null,
        classesOnly: Boolean = false,
        excludeAnnotated: Set<String> = emptySet(),
        classAnnotations: Map<ClassName, Set<String>> = emptyMap(),
        methodAnnotations: Map<MethodRef, Set<String>> = emptyMap(),
        testGraph: CallGraph? = null,
    ): List<DeadCode> = DeadCodeFinder.find(
        graph = graph,
        filter = filter,
        exclude = exclude,
        classesOnly = classesOnly,
        excludeAnnotated = excludeAnnotated,
        classAnnotations = classAnnotations,
        methodAnnotations = methodAnnotations,
        testGraph = testGraph,
    )

    @Test
    fun `empty call graph produces empty result`() {
        val graph = testCallGraph()

        val dead = findDead(graph)
    }
    @Test
    fun `single class with no callers is dead`() {
        val graph = testCallGraph(
            method("com.example.Lonely", "doWork") to method("com.example.External", "process"),
            projectClasses = setOf("com.example.Lonely"),
        )

        val dead = findDead(graph)

        val deadClasses = dead.filter { it.kind == DeadCodeKind.CLASS }
        assertEquals(1, deadClasses.size)
        assertEquals("com.example.Lonely", deadClasses[0].className.value)
    }
    @Test
    fun `class called by another class is not dead`() {
        val graph = testCallGraph(
            method("com.example.Caller", "handle") to method("com.example.Service", "process"),
            projectClasses = setOf("com.example.Caller", "com.example.Service"),
        )

        val dead = findDead(graph)

        val deadClassNames = dead.filter { it.kind == DeadCodeKind.CLASS }.map { it.className.value }
        assertTrue("com.example.Service" !in deadClassNames, "Service is called by Caller so should not be dead")
        assertTrue("com.example.Caller" in deadClassNames, "Caller has no callers so should be dead")
    }
    @Test
    fun `class that calls others but is never called is dead`() {
        val graph = testCallGraph(
            method("com.example.Orphan", "run") to method("com.example.Service", "process"),
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            projectClasses = setOf("com.example.Orphan", "com.example.Service", "com.example.Controller"),
        )

        val dead = findDead(graph)

        val deadClassNames = dead.filter { it.kind == DeadCodeKind.CLASS }.map { it.className.value }
        assertTrue("com.example.Orphan" in deadClassNames)
        assertTrue("com.example.Controller" in deadClassNames)
        assertTrue("com.example.Service" !in deadClassNames)
    }
    @Test
    fun `self-referencing class with no external callers is dead`() {
        val graph = testCallGraph(
            method("com.example.Recursive", "start") to method("com.example.Recursive", "step"),
            projectClasses = setOf("com.example.Recursive"),
        )

        val dead = findDead(graph)

        val deadClassNames = dead.filter { it.kind == DeadCodeKind.CLASS }.map { it.className.value }
        assertTrue("com.example.Recursive" in deadClassNames, "Self-referencing class with no external callers is dead")
    }
    @Test
    fun `method with no callers from any class is dead method`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "unused") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.Repo"),
        )

        val dead = findDead(graph)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }
        val deadMethodNames = deadMethods.map { "${it.className.value}.${it.memberName}" }
        assertTrue("com.example.Service.unused" in deadMethodNames, "unused() has no callers so should be dead")
        assertTrue("com.example.Service.process" !in deadMethodNames, "process() is called by Controller")
    }
    @Test
    fun `method called by another method in a different class is not dead`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Controller", "handle") to method("com.example.Service", "validate"),
            projectClasses = setOf("com.example.Controller", "com.example.Service"),
        )

        val dead = findDead(graph)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }
        assertTrue(deadMethods.isEmpty(), "Both process() and validate() are called by Controller, no dead methods")
    }
    @Test
    fun `filter regex limits results to matching classes`() {
        val graph = testCallGraph(
            method("com.example.OrphanService", "run") to method("com.example.Repo", "save"),
            method("com.example.OrphanUtil", "help") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.OrphanService", "com.example.OrphanUtil", "com.example.Repo"),
        )

        val dead = findDead(graph, filter = Regex("Service"))

        val deadClassNames = dead.map { it.className.value }
        assertTrue("com.example.OrphanService" in deadClassNames)
        assertTrue("com.example.OrphanUtil" !in deadClassNames, "OrphanUtil doesn't match filter")
    }
    @Test
    fun `exclude regex removes matching classes from results`() {
        val graph = testCallGraph(
            method("com.example.Main", "main") to method("com.example.Service", "run"),
            method("com.example.TestHelper", "setup") to method("com.example.Service", "run"),
            projectClasses = setOf("com.example.Main", "com.example.TestHelper", "com.example.Service"),
        )

        val dead = findDead(graph, exclude = Regex("Main|Test"))

        val deadClassNames = dead.map { it.className.value }
        assertTrue("com.example.Main" !in deadClassNames, "Main excluded by regex")
        assertTrue("com.example.TestHelper" !in deadClassNames, "TestHelper excluded by regex")
    }
    @Test
    fun `results are sorted by kind then by class name then by member name`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "unusedB") to method("com.example.Repo", "save"),
            method("com.example.Service", "unusedA") to method("com.example.Repo", "save"),
            method("com.example.Zombie", "run") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.Repo", "com.example.Zombie"),
        )

        val dead = findDead(graph)

        val classes = dead.filter { it.kind == DeadCodeKind.CLASS }
        val methods = dead.filter { it.kind == DeadCodeKind.METHOD }

        assertTrue(dead.indexOf(classes.first()) < dead.indexOf(methods.first()), "CLASSes come before METHODs")
        assertEquals(listOf("com.example.Controller", "com.example.Zombie"), classes.map { it.className.value })
        assertEquals(listOf("unusedA", "unusedB"), methods.map { it.memberName })
    }
    @Test
    fun `method called only within same class is dead method`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "process") to method("com.example.Service", "helper"),
            projectClasses = setOf("com.example.Controller", "com.example.Service"),
        )

        val dead = findDead(graph)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }
        val deadMethodNames = deadMethods.map { "${it.className.value}.${it.memberName}" }
        assertTrue("com.example.Service.helper" in deadMethodNames, "helper() is only called within Service itself")
        assertTrue("com.example.Service.process" !in deadMethodNames, "process() is called by Controller")
    }

    @Test
    fun `filters out Kotlin generated methods from dead method results`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "copy") to method("com.example.Repo", "save"),
            method("com.example.Service", "hashCode") to method("com.example.Repo", "save"),
            method("com.example.Service", "equals") to method("com.example.Repo", "save"),
            method("com.example.Service", "toString") to method("com.example.Repo", "save"),
            method("com.example.Service", "component1") to method("com.example.Repo", "save"),
            method("com.example.Service", "copy\$default") to method("com.example.Repo", "save"),
            method("com.example.Service", "access\$getDb\$p") to method("com.example.Repo", "save"),
            method("com.example.Service", "<init>") to method("com.example.Repo", "save"),
            method("com.example.Service", "<clinit>") to method("com.example.Repo", "save"),
            method("com.example.Service", "unusedReal") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.Repo"),
        )

        val dead = findDead(graph)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }
        val deadMethodNames = deadMethods.map { it.memberName }
        assertTrue("unusedReal" in deadMethodNames, "Real unused method should be reported")
        assertTrue("copy" !in deadMethodNames, "Generated copy should be filtered")
        assertTrue("hashCode" !in deadMethodNames, "Generated hashCode should be filtered")
        assertTrue("equals" !in deadMethodNames, "Generated equals should be filtered")
        assertTrue("toString" !in deadMethodNames, "Generated toString should be filtered")
        assertTrue("component1" !in deadMethodNames, "Generated componentN should be filtered")
        assertTrue("copy\$default" !in deadMethodNames, "Generated copy\$default should be filtered")
        assertTrue("access\$getDb\$p" !in deadMethodNames, "Generated access\$ should be filtered")
        assertTrue("<init>" !in deadMethodNames, "Constructor should be filtered")
        assertTrue("<clinit>" !in deadMethodNames, "Static initializer should be filtered")
    }

    @Test
    fun `filters out inner classes with dollar sign from dead class results`() {
        val graph = testCallGraph(
            method("com.example.Service", "process") to method("com.example.Repo", "save"),
            method("com.example.Service\$Companion", "create") to method("com.example.Repo", "save"),
            method("com.example.Service\$process\$1", "invokeSuspend") to method("com.example.Repo", "save"),
            projectClasses = setOf(
                "com.example.Service",
                "com.example.Service\$Companion",
                "com.example.Service\$process\$1",
                "com.example.Repo",
            ),
        )

        val dead = findDead(graph)

        val deadClasses = dead.filter { it.kind == DeadCodeKind.CLASS }.map { it.className.value }
        assertTrue("com.example.Service" in deadClasses, "Service has no callers")
        assertTrue("com.example.Service\$Companion" !in deadClasses, "Companion inner class should be filtered")
        assertTrue("com.example.Service\$process\$1" !in deadClasses, "Coroutine inner class should be filtered")
    }

    @Test
    fun `filters out dead methods on generated inner classes`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "process") to method("com.example.Service\$process\$1", "invokeSuspend"),
            method("com.example.Service\$process\$1", "invokeSuspend") to method("com.example.Repo", "save"),
            method("com.example.Service\$process\$1", "create") to method("com.example.Repo", "save"),
            method("com.example.Service\$Companion", "create") to method("com.example.Repo", "save"),
            method("com.example.Service", "unused") to method("com.example.Repo", "save"),
            projectClasses = setOf(
                "com.example.Controller",
                "com.example.Service",
                "com.example.Service\$process\$1",
                "com.example.Service\$Companion",
                "com.example.Repo",
            ),
        )

        val dead = findDead(graph)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }
        val deadMethodEntries = deadMethods.map { "${it.className.value}.${it.memberName}" }
        assertTrue("com.example.Service.unused" in deadMethodEntries, "Real unused method should be reported")
        assertTrue(
            deadMethodEntries.none { it.contains("\$") },
            "No methods on generated inner classes should appear, but found: ${deadMethodEntries.filter { it.contains("\$") }}",
        )
    }

    @Test
    fun `filters out data class boilerplate on sealed class variants`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.ServiceResult\$Success", "getMessage"),
            method("com.example.Controller", "handle") to method("com.example.ServiceResult\$Failure", "getError"),
            method("com.example.ServiceResult\$Success", "copy") to method("com.example.Repo", "save"),
            method("com.example.ServiceResult\$Success", "hashCode") to method("com.example.Repo", "save"),
            method("com.example.ServiceResult\$Failure", "equals") to method("com.example.Repo", "save"),
            method("com.example.ServiceResult\$Failure", "copy\$default") to method("com.example.Repo", "save"),
            projectClasses = setOf(
                "com.example.Controller",
                "com.example.ServiceResult\$Success",
                "com.example.ServiceResult\$Failure",
                "com.example.Repo",
            ),
        )

        val dead = findDead(graph)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }
        assertTrue(
            deadMethods.isEmpty(),
            "Data class boilerplate on sealed variants should be filtered, but found: ${deadMethods.map { "${it.className.value}.${it.memberName}" }}",
        )
    }

    @Test
    fun `classesOnly suppresses all dead methods`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "unused") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.Repo"),
        )

        val dead = findDead(graph, classesOnly = true)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }
        val deadClasses = dead.filter { it.kind == DeadCodeKind.CLASS }
        assertTrue(deadMethods.isEmpty(), "classesOnly should suppress all dead methods")
        assertTrue(deadClasses.isNotEmpty(), "classesOnly should still report dead classes")
    }

    // [TEST] Class with excluded annotation is not reported as dead
    // [TEST] Method with excluded annotation is not reported as dead method
    // [TEST] Class without excluded annotation is still reported as dead
    // [TEST] Empty excludeAnnotated set has no effect

    @Test
    fun `class with excluded annotation is not reported as dead`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.External", "process"),
            projectClasses = setOf("com.example.Controller"),
        )

        val dead = findDead(
            graph = graph,
            excludeAnnotated = setOf("RestController"),
            classAnnotations = mapOf(ClassName("com.example.Controller") to setOf("RestController")),
        )

        assertTrue(dead.isEmpty(), "Controller annotated with @RestController should be excluded")
    }

    @Test
    fun `method with excluded annotation is not reported as dead method`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "scheduledTask") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.Repo"),
        )

        val dead = findDead(
            graph = graph,
            excludeAnnotated = setOf("Scheduled"),
            methodAnnotations = mapOf(
                MethodRef(ClassName("com.example.Service"), "scheduledTask") to setOf("Scheduled"),
            ),
        )

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }
        assertTrue(
            deadMethods.none { it.memberName == "scheduledTask" },
            "scheduledTask annotated with @Scheduled should be excluded",
        )
    }

    @Test
    fun `class without excluded annotation is still reported as dead`() {
        val graph = testCallGraph(
            method("com.example.Service", "process") to method("com.example.External", "call"),
            method("com.example.Util", "help") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.Service", "com.example.Util"),
        )

        val dead = findDead(
            graph = graph,
            excludeAnnotated = setOf("RestController"),
            classAnnotations = mapOf(ClassName("com.example.Service") to setOf("Service")),
        )

        val deadClassNames = dead.filter { it.kind == DeadCodeKind.CLASS }.map { it.className.value }
        assertTrue("com.example.Service" in deadClassNames, "Service is not annotated with RestController, so still dead")
        assertTrue("com.example.Util" in deadClassNames, "Util has no annotations, so still dead")
    }

    // === Confidence scoring tests ===

    @Test
    fun `unreferenced class with no annotations and no test graph has HIGH confidence`() {
        val graph = testCallGraph(
            method("com.example.Orphan", "run") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.Orphan"),
        )

        val dead = findDead(graph)

        assertEquals(1, dead.size)
        assertEquals(DeadCodeConfidence.HIGH, dead[0].confidence)
    }

    @Test
    fun `unreferenced class with annotations has LOW confidence`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.Controller"),
        )

        val dead = findDead(
            graph = graph,
            classAnnotations = mapOf(ClassName("com.example.Controller") to setOf("RestController")),
        )

        assertEquals(1, dead.size)
        assertEquals(DeadCodeConfidence.LOW, dead[0].confidence)
    }

    @Test
    fun `unreferenced method with annotations has LOW confidence`() {
        val graph = testCallGraph(
            method("com.example.Caller", "main") to method("com.example.Service", "process"),
            method("com.example.Service", "scheduled") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Caller", "com.example.Service", "com.example.Repo"),
        )

        val dead = findDead(
            graph = graph,
            methodAnnotations = mapOf(
                MethodRef(ClassName("com.example.Service"), "scheduled") to setOf("Scheduled"),
            ),
        )

        val scheduledDead = dead.first { it.memberName == "scheduled" }
        assertEquals(DeadCodeConfidence.LOW, scheduledDead.confidence)
    }

    @Test
    fun `unreferenced in prod but referenced in test graph has MEDIUM confidence`() {
        val prodGraph = testCallGraph(
            method("com.example.Orphan", "run") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.Orphan"),
        )
        val testGraph = testCallGraph(
            method("com.example.OrphanTest", "testRun") to method("com.example.Orphan", "run"),
        )

        val dead = findDead(graph = prodGraph, testGraph = testGraph)

        assertEquals(1, dead.size)
        assertEquals(DeadCodeConfidence.MEDIUM, dead[0].confidence)
    }

    @Test
    fun `unreferenced method in prod but referenced in test graph has MEDIUM confidence`() {
        val prodGraph = testCallGraph(
            method("com.example.Caller", "main") to method("com.example.Service", "process"),
            method("com.example.Service", "helper") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Caller", "com.example.Service", "com.example.Repo"),
        )
        val testGraph = testCallGraph(
            method("com.example.ServiceTest", "testHelper") to method("com.example.Service", "helper"),
        )

        val dead = findDead(graph = prodGraph, testGraph = testGraph)

        val helperDead = dead.first { it.memberName == "helper" }
        assertEquals(DeadCodeConfidence.MEDIUM, helperDead.confidence)
    }

    @Test
    fun `annotation LOW takes priority over test graph MEDIUM`() {
        val prodGraph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.Controller"),
        )
        val testGraph = testCallGraph(
            method("com.example.ControllerTest", "test") to method("com.example.Controller", "handle"),
        )

        val dead = findDead(
            graph = prodGraph,
            testGraph = testGraph,
            classAnnotations = mapOf(ClassName("com.example.Controller") to setOf("RestController")),
        )

        assertEquals(1, dead.size)
        assertEquals(DeadCodeConfidence.LOW, dead[0].confidence, "Annotation LOW should take priority over test-referenced MEDIUM")
    }

    @Test
    fun `no test graph provided means no MEDIUM confidence possible`() {
        val graph = testCallGraph(
            method("com.example.Orphan", "run") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.Orphan"),
        )

        val dead = findDead(graph = graph, testGraph = null)

        assertEquals(1, dead.size)
        assertEquals(DeadCodeConfidence.HIGH, dead[0].confidence, "Without test graph, confidence should be HIGH not MEDIUM")
    }
}
