package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.callgraph.CallGraph
import no.f12.codenavigator.navigation.callgraph.MethodRef
import no.f12.codenavigator.navigation.deadcode.DeadCode
import no.f12.codenavigator.navigation.deadcode.DeadCodeConfidence
import no.f12.codenavigator.navigation.deadcode.DeadCodeFinder
import no.f12.codenavigator.navigation.deadcode.DeadCodeKind
import no.f12.codenavigator.navigation.deadcode.DeadCodeReason
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
        classAnnotations: Map<ClassName, Set<AnnotationName>> = emptyMap(),
        methodAnnotations: Map<MethodRef, Set<AnnotationName>> = emptyMap(),
        testGraph: CallGraph? = null,
        interfaceImplementors: Map<ClassName, Set<ClassName>> = emptyMap(),
        classFields: Map<ClassName, Set<String>> = emptyMap(),
        inlineMethods: Set<MethodRef> = emptySet(),
        classExternalInterfaces: Map<ClassName, Set<ClassName>> = emptyMap(),
        prodOnly: Boolean = false,
        testOnly: Boolean = false,
        modifierAnnotated: Set<String> = emptySet(),
        supertypeEntryPoints: Set<ClassName> = emptySet(),
        testClasses: Set<ClassName> = emptySet(),
        classReceiverTypes: Map<ClassName, Set<ClassName>> = emptyMap(),
        receiverTypeEntryPoints: Set<ClassName> = emptySet(),
    ): List<DeadCode> = DeadCodeFinder.find(
        graph = graph,
        filter = filter,
        exclude = exclude,
        classesOnly = classesOnly,
        excludeAnnotated = excludeAnnotated,
        classAnnotations = classAnnotations,
        methodAnnotations = methodAnnotations,
        testGraph = testGraph,
        interfaceImplementors = interfaceImplementors,
        classFields = classFields,
        inlineMethods = inlineMethods,
        classExternalInterfaces = classExternalInterfaces,
        prodOnly = prodOnly,
        testOnly = testOnly,
        modifierAnnotated = modifierAnnotated,
        supertypeEntryPoints = supertypeEntryPoints,
        testClasses = testClasses,
        classReceiverTypes = classReceiverTypes,
        receiverTypeEntryPoints = receiverTypeEntryPoints,
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
    fun `method called within same class by alive method is not dead`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "process") to method("com.example.Service", "helper"),
            projectClasses = setOf("com.example.Controller", "com.example.Service"),
        )

        val dead = findDead(graph)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }
        val deadMethodNames = deadMethods.map { "${it.className.value}.${it.memberName}" }
        assertTrue("com.example.Service.helper" !in deadMethodNames, "helper() is called by process() which is alive — should not be dead")
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
            classAnnotations = mapOf(ClassName("com.example.Controller") to setOf(AnnotationName("RestController"))),
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
                MethodRef(ClassName("com.example.Service"), "scheduledTask") to setOf(AnnotationName("Scheduled")),
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
            classAnnotations = mapOf(ClassName("com.example.Service") to setOf(AnnotationName("Service"))),
        )

        val deadClassNames = dead.filter { it.kind == DeadCodeKind.CLASS }.map { it.className.value }
        assertTrue("com.example.Service" in deadClassNames, "Service is not annotated with RestController, so still dead")
        assertTrue("com.example.Util" in deadClassNames, "Util has no annotations, so still dead")
    }

    @Test
    fun `method called within same class transitively through alive chain is not dead`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "process") to method("com.example.Service", "validate"),
            method("com.example.Service", "validate") to method("com.example.Service", "sanitize"),
            projectClasses = setOf("com.example.Controller", "com.example.Service"),
        )

        val dead = findDead(graph)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }.map { "${it.className.value}.${it.memberName}" }
        assertTrue("com.example.Service.validate" !in deadMethods, "validate() is reachable from alive process()")
        assertTrue("com.example.Service.sanitize" !in deadMethods, "sanitize() is transitively reachable from alive process()")
    }

    @Test
    fun `method called within same class but no alive caller is still dead`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "orphan") to method("com.example.Service", "orphanHelper"),
            projectClasses = setOf("com.example.Controller", "com.example.Service"),
        )

        val dead = findDead(graph)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }.map { "${it.className.value}.${it.memberName}" }
        assertTrue("com.example.Service.orphan" in deadMethods, "orphan() is not called from outside, so it is dead")
        assertTrue("com.example.Service.orphanHelper" in deadMethods, "orphanHelper() is only called by dead orphan(), so it is dead too")
    }

    @Test
    fun `self-recursive method within same class called by alive method is not dead`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "process") to method("com.example.Service", "recurse"),
            method("com.example.Service", "recurse") to method("com.example.Service", "recurse"),
            projectClasses = setOf("com.example.Controller", "com.example.Service"),
        )

        val dead = findDead(graph)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }.map { "${it.className.value}.${it.memberName}" }
        assertTrue("com.example.Service.recurse" !in deadMethods, "recurse() is reachable from alive process()")
    }

    // === Interface dispatch resolution tests ===

    @Test
    fun `method called via interface dispatch is not dead`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Controller", "init") to method("com.example.ServiceImpl", "setup"),
            method("com.example.ServiceImpl", "process") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.ServiceImpl", "com.example.Repo"),
        )
        val interfaceImplementors = mapOf(
            ClassName("com.example.Service") to setOf(ClassName("com.example.ServiceImpl")),
        )

        val dead = findDead(graph, interfaceImplementors = interfaceImplementors)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }.map { "${it.className.value}.${it.memberName}" }
        assertTrue("com.example.ServiceImpl.process" !in deadMethods, "process() on ServiceImpl should not be dead — called via interface dispatch on Service")
    }

    @Test
    fun `interface dispatch marks implementor class as alive`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.ServiceImpl", "process") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.ServiceImpl", "com.example.Repo"),
        )
        val interfaceImplementors = mapOf(
            ClassName("com.example.Service") to setOf(ClassName("com.example.ServiceImpl")),
        )

        val dead = findDead(graph, interfaceImplementors = interfaceImplementors)

        val deadClasses = dead.filter { it.kind == DeadCodeKind.CLASS }.map { it.className.value }
        assertTrue("com.example.ServiceImpl" !in deadClasses, "ServiceImpl should not be dead — it implements Service which is called")
    }

    @Test
    fun `without interface dispatch info implementor method is still dead`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Controller", "init") to method("com.example.ServiceImpl", "setup"),
            method("com.example.ServiceImpl", "process") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.ServiceImpl", "com.example.Repo"),
        )

        val dead = findDead(graph, interfaceImplementors = emptyMap())

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }.map { "${it.className.value}.${it.memberName}" }
        assertTrue("com.example.ServiceImpl.process" in deadMethods, "Without interface info, process() on ServiceImpl should be dead")
    }

    @Test
    fun `interface dispatch resolves to multiple implementors`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Controller", "init") to method("com.example.ServiceImplA", "setup"),
            method("com.example.Controller", "init") to method("com.example.ServiceImplB", "setup"),
            method("com.example.ServiceImplA", "process") to method("com.example.Repo", "save"),
            method("com.example.ServiceImplB", "process") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.ServiceImplA", "com.example.ServiceImplB", "com.example.Repo"),
        )
        val interfaceImplementors = mapOf(
            ClassName("com.example.Service") to setOf(ClassName("com.example.ServiceImplA"), ClassName("com.example.ServiceImplB")),
        )

        val dead = findDead(graph, interfaceImplementors = interfaceImplementors)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }.map { "${it.className.value}.${it.memberName}" }
        assertTrue("com.example.ServiceImplA.process" !in deadMethods, "process() on ServiceImplA should not be dead")
        assertTrue("com.example.ServiceImplB.process" !in deadMethods, "process() on ServiceImplB should not be dead")
    }

    // === Kotlin property accessor filtering tests ===

    @Test
    fun `property accessor for declared field is filtered from dead methods`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "getName") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.Repo"),
        )
        val classFields = mapOf(
            ClassName("com.example.Service") to setOf("name"),
        )

        val dead = findDead(graph, classFields = classFields)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }.map { "${it.className.value}.${it.memberName}" }
        assertTrue("com.example.Service.getName" !in deadMethods, "getName() is a property accessor for field 'name' and should be filtered")
    }

    @Test
    fun `non-accessor method with get prefix is still reported as dead`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "getData") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.Repo"),
        )
        val classFields = mapOf(
            ClassName("com.example.Service") to setOf("name"),
        )

        val dead = findDead(graph, classFields = classFields)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }.map { "${it.className.value}.${it.memberName}" }
        assertTrue("com.example.Service.getData" in deadMethods, "getData() does not match any field and should still be dead")
    }

    @Test
    fun `setter accessor for declared field is filtered from dead methods`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "setName") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.Repo"),
        )
        val classFields = mapOf(
            ClassName("com.example.Service") to setOf("name"),
        )

        val dead = findDead(graph, classFields = classFields)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }.map { "${it.className.value}.${it.memberName}" }
        assertTrue("com.example.Service.setName" !in deadMethods, "setName() is a property accessor for field 'name' and should be filtered")
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
            classAnnotations = mapOf(ClassName("com.example.Controller") to setOf(AnnotationName("RestController"))),
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
                MethodRef(ClassName("com.example.Service"), "scheduled") to setOf(AnnotationName("Scheduled")),
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
            classAnnotations = mapOf(ClassName("com.example.Controller") to setOf(AnnotationName("RestController"))),
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

    // === Kotlin inline function filtering tests ===

    @Test
    fun `inline method is filtered from dead method results`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "inlineHelper") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.Repo"),
        )
        val inlineMethods = setOf(
            MethodRef(ClassName("com.example.Service"), "inlineHelper"),
        )

        val dead = findDead(graph, inlineMethods = inlineMethods)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }.map { "${it.className.value}.${it.memberName}" }
        assertTrue("com.example.Service.inlineHelper" !in deadMethods, "inlineHelper() is inline and should be filtered from dead methods")
    }

    @Test
    fun `non-inline method is still reported as dead alongside inline filtering`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "inlineHelper") to method("com.example.Repo", "save"),
            method("com.example.Service", "reallyUnused") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.Repo"),
        )
        val inlineMethods = setOf(
            MethodRef(ClassName("com.example.Service"), "inlineHelper"),
        )

        val dead = findDead(graph, inlineMethods = inlineMethods)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }.map { "${it.className.value}.${it.memberName}" }
        assertTrue("com.example.Service.reallyUnused" in deadMethods, "reallyUnused() is not inline and should still be dead")
        assertTrue("com.example.Service.inlineHelper" !in deadMethods, "inlineHelper() is inline and should be filtered")
    }

    @Test
    fun `inline methods do not affect dead class detection`() {
        val graph = testCallGraph(
            method("com.example.Orphan", "inlineMethod") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.Orphan"),
        )
        val inlineMethods = setOf(
            MethodRef(ClassName("com.example.Orphan"), "inlineMethod"),
        )

        val dead = findDead(graph, inlineMethods = inlineMethods)

        val deadClasses = dead.filter { it.kind == DeadCodeKind.CLASS }.map { it.className.value }
        assertTrue("com.example.Orphan" in deadClasses, "Inline filtering only affects methods, not class-level dead code detection")
    }

    // === External interface implementation flagging tests ===

    @Test
    fun `dead method on class implementing external interface has LOW confidence`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Adapter", "process"),
            method("com.example.Adapter", "unmarshal") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Adapter", "com.example.Repo"),
        )
        val classExternalInterfaces = mapOf(
            ClassName("com.example.Adapter") to setOf(ClassName("javax.xml.bind.XmlAdapter")),
        )

        val dead = findDead(graph, classExternalInterfaces = classExternalInterfaces)

        val unmarshalDead = dead.first { it.memberName == "unmarshal" }
        assertEquals(DeadCodeConfidence.LOW, unmarshalDead.confidence, "unmarshal() on class implementing external XmlAdapter should have LOW confidence")
    }

    @Test
    fun `dead method on class implementing only in-scope interface stays HIGH`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.ServiceImpl", "unused") to method("com.example.Repo", "save"),
            method("com.example.Controller", "init") to method("com.example.ServiceImpl", "setup"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.ServiceImpl", "com.example.Repo"),
        )
        val classExternalInterfaces = emptyMap<ClassName, Set<ClassName>>()

        val dead = findDead(graph, classExternalInterfaces = classExternalInterfaces)

        val unusedDead = dead.first { it.memberName == "unused" }
        assertEquals(DeadCodeConfidence.HIGH, unusedDead.confidence, "Method on class with no external interfaces should have HIGH confidence")
    }

    @Test
    fun `dead class implementing external interface still has HIGH confidence`() {
        val graph = testCallGraph(
            method("com.example.Adapter", "unmarshal") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.Adapter"),
        )
        val classExternalInterfaces = mapOf(
            ClassName("com.example.Adapter") to setOf(ClassName("javax.xml.bind.XmlAdapter")),
        )

        val dead = findDead(graph, classExternalInterfaces = classExternalInterfaces)

        val deadClass = dead.first { it.kind == DeadCodeKind.CLASS }
        assertEquals(DeadCodeConfidence.HIGH, deadClass.confidence, "Dead class should stay HIGH even if it implements external interface — if nobody constructs it, the interface methods are never called either")
    }

    @Test
    fun `external interface LOW takes priority over test-graph MEDIUM`() {
        val prodGraph = testCallGraph(
            method("com.example.Caller", "main") to method("com.example.Adapter", "process"),
            method("com.example.Adapter", "unmarshal") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Caller", "com.example.Adapter", "com.example.Repo"),
        )
        val testGraph = testCallGraph(
            method("com.example.AdapterTest", "testUnmarshal") to method("com.example.Adapter", "unmarshal"),
        )
        val classExternalInterfaces = mapOf(
            ClassName("com.example.Adapter") to setOf(ClassName("javax.xml.bind.XmlAdapter")),
        )

        val dead = findDead(graph = prodGraph, testGraph = testGraph, classExternalInterfaces = classExternalInterfaces)

        val unmarshalDead = dead.first { it.memberName == "unmarshal" }
        assertEquals(DeadCodeConfidence.LOW, unmarshalDead.confidence, "External interface LOW should take priority over test-graph MEDIUM")
    }

    // === Dead code reason tests ===

    @Test
    fun `class unreferenced in both prod and test has reason NO_REFERENCES`() {
        val prodGraph = testCallGraph(
            method("com.example.Orphan", "run") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.Orphan"),
        )
        val testGraph = testCallGraph()

        val dead = findDead(graph = prodGraph, testGraph = testGraph)

        assertEquals(1, dead.size)
        assertEquals(DeadCodeReason.NO_REFERENCES, dead[0].reason)
    }

    @Test
    fun `class unreferenced in prod but referenced in test has reason TEST_ONLY`() {
        val prodGraph = testCallGraph(
            method("com.example.Orphan", "run") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.Orphan"),
        )
        val testGraph = testCallGraph(
            method("com.example.OrphanTest", "testRun") to method("com.example.Orphan", "run"),
        )

        val dead = findDead(graph = prodGraph, testGraph = testGraph)

        assertEquals(1, dead.size)
        assertEquals(DeadCodeReason.TEST_ONLY, dead[0].reason)
    }

    @Test
    fun `dead class with no test graph has reason NO_REFERENCES`() {
        val graph = testCallGraph(
            method("com.example.Orphan", "run") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.Orphan"),
        )

        val dead = findDead(graph = graph, testGraph = null)

        assertEquals(1, dead.size)
        assertEquals(DeadCodeReason.NO_REFERENCES, dead[0].reason)
    }

    @Test
    fun `dead method unreferenced in prod but referenced in test has reason TEST_ONLY`() {
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
        assertEquals(DeadCodeReason.TEST_ONLY, helperDead.reason)
    }

    @Test
    fun `dead method unreferenced in both prod and test has reason NO_REFERENCES`() {
        val prodGraph = testCallGraph(
            method("com.example.Caller", "main") to method("com.example.Service", "process"),
            method("com.example.Service", "helper") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Caller", "com.example.Service", "com.example.Repo"),
        )
        val testGraph = testCallGraph()

        val dead = findDead(graph = prodGraph, testGraph = testGraph)

        val helperDead = dead.first { it.memberName == "helper" }
        assertEquals(DeadCodeReason.NO_REFERENCES, helperDead.reason)
    }

    // === Extension function tests ===

    @Test
    fun `extension function on Kt file-facade class called from other class is not dead`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.PollExtKt", "withAdminPoll"),
            method("com.example.PollExtKt", "withAdminPoll") to method("com.example.Poll", "copy"),
            method("com.example.Service", "process") to method("com.example.PollExtKt", "withAdminPoll"),
            projectClasses = setOf("com.example.Controller", "com.example.PollExtKt", "com.example.Poll", "com.example.Service"),
        )

        val dead = findDead(graph)

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }.map { "${it.className.value}.${it.memberName}" }
        val deadClasses = dead.filter { it.kind == DeadCodeKind.CLASS }.map { it.className.value }
        assertTrue("com.example.PollExtKt.withAdminPoll" !in deadMethods, "withAdminPoll is called from Controller and Service — should not be dead")
        assertTrue("com.example.PollExtKt" !in deadClasses, "PollExtKt is called from Controller and Service — should not be dead")
    }

    // === prodOnly flag tests ===

    @Test
    fun `prodOnly filters out TEST_ONLY items and keeps NO_REFERENCES`() {
        val prodGraph = testCallGraph(
            method("com.example.Service", "process") to method("com.example.External", "call"),
            method("com.example.Util", "help") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.Service", "com.example.Util"),
        )
        val testGraph = testCallGraph(
            method("com.example.ServiceTest", "test") to method("com.example.Service", "process"),
        )

        val dead = findDead(graph = prodGraph, testGraph = testGraph, prodOnly = true)

        val deadClassNames = dead.map { it.className.value }
        assertTrue("com.example.Util" in deadClassNames, "Util has NO_REFERENCES and should appear with prodOnly")
        assertTrue("com.example.Service" !in deadClassNames, "Service is TEST_ONLY and should be filtered with prodOnly")
    }

    @Test
    fun `prodOnly excludes test source class even when reason is NO_REFERENCES`() {
        val prodGraph = testCallGraph(
            method("com.example.Service", "process") to method("com.example.External", "call"),
            method("com.example.ServiceTest", "testProcess") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.Service", "com.example.ServiceTest"),
        )

        val dead = findDead(
            graph = prodGraph,
            testGraph = null,
            prodOnly = true,
            testClasses = setOf(ClassName("com.example.ServiceTest")),
        )

        val deadClassNames = dead.map { it.className.value }
        assertTrue("com.example.Service" in deadClassNames, "Prod class with NO_REFERENCES should still appear")
        assertTrue("com.example.ServiceTest" !in deadClassNames, "Test source class should be excluded by prodOnly even with NO_REFERENCES")
    }

    @Test
    fun `prodOnly excludes dead method on test source class`() {
        val prodGraph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.ServiceTest", "testProcess") to method("com.example.External", "call"),
            method("com.example.ServiceTest", "helper") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.ServiceTest"),
        )

        val dead = findDead(
            graph = prodGraph,
            testGraph = null,
            prodOnly = true,
            testClasses = setOf(ClassName("com.example.ServiceTest")),
        )

        val deadClassNames = dead.filter { it.kind == DeadCodeKind.CLASS }.map { it.className.value }
        val deadMethodClasses = dead.filter { it.kind == DeadCodeKind.METHOD }.map { it.className.value }
        assertTrue("com.example.ServiceTest" !in deadClassNames, "Test source class should be excluded")
        assertTrue("com.example.ServiceTest" !in deadMethodClasses, "Dead methods on test source class should be excluded")
    }

    @Test
    fun `testClasses has no effect when prodOnly is false`() {
        val prodGraph = testCallGraph(
            method("com.example.Service", "process") to method("com.example.External", "call"),
            method("com.example.ServiceTest", "testProcess") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.Service", "com.example.ServiceTest"),
        )

        val dead = findDead(
            graph = prodGraph,
            testGraph = null,
            prodOnly = false,
            testClasses = setOf(ClassName("com.example.ServiceTest")),
        )

        val deadClassNames = dead.map { it.className.value }
        assertTrue("com.example.ServiceTest" in deadClassNames, "Without prodOnly, test classes should still appear")
        assertTrue("com.example.Service" in deadClassNames, "Prod class should still appear")
    }

    @Test
    fun `prodOnly false shows both NO_REFERENCES and TEST_ONLY items`() {
        val prodGraph = testCallGraph(
            method("com.example.Service", "process") to method("com.example.External", "call"),
            method("com.example.Util", "help") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.Service", "com.example.Util"),
        )
        val testGraph = testCallGraph(
            method("com.example.ServiceTest", "test") to method("com.example.Service", "process"),
        )

        val dead = findDead(graph = prodGraph, testGraph = testGraph, prodOnly = false)

        val deadClassNames = dead.map { it.className.value }
        assertTrue("com.example.Util" in deadClassNames, "Util should appear without prodOnly")
        assertTrue("com.example.Service" in deadClassNames, "Service should appear without prodOnly")
    }

    // === testOnly flag tests ===

    @Test
    fun `testOnly filters to only TEST_ONLY items`() {
        val prodGraph = testCallGraph(
            method("com.example.Service", "process") to method("com.example.External", "call"),
            method("com.example.Util", "help") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.Service", "com.example.Util"),
        )
        val testGraph = testCallGraph(
            method("com.example.ServiceTest", "test") to method("com.example.Service", "process"),
        )

        val dead = findDead(graph = prodGraph, testGraph = testGraph, testOnly = true)

        val deadClassNames = dead.map { it.className.value }
        assertTrue("com.example.Service" in deadClassNames, "Service is TEST_ONLY and should appear with testOnly")
        assertTrue("com.example.Util" !in deadClassNames, "Util is NO_REFERENCES and should be filtered with testOnly")
    }

    @Test
    fun `testOnly false shows both NO_REFERENCES and TEST_ONLY items`() {
        val prodGraph = testCallGraph(
            method("com.example.Service", "process") to method("com.example.External", "call"),
            method("com.example.Util", "help") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.Service", "com.example.Util"),
        )
        val testGraph = testCallGraph(
            method("com.example.ServiceTest", "test") to method("com.example.Service", "process"),
        )

        val dead = findDead(graph = prodGraph, testGraph = testGraph, testOnly = false)

        val deadClassNames = dead.map { it.className.value }
        assertTrue("com.example.Util" in deadClassNames, "Util should appear without testOnly")
        assertTrue("com.example.Service" in deadClassNames, "Service should appear without testOnly")
    }

    @Test
    fun `package-info classes are excluded from dead code results`() {
        val graph = testCallGraph(
            method("com.example.package-info", "<clinit>") to method("java.lang.Object", "<init>"),
            method("com.example.Service", "process") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.package-info", "com.example.Service"),
        )

        val dead = findDead(graph = graph)

        val deadClassNames = dead.map { it.className.value }
        assertTrue("com.example.package-info" !in deadClassNames, "package-info should be auto-filtered")
        assertTrue("com.example.Service" in deadClassNames, "Service should still appear")
    }

    // === Modifier annotations vs entry-point annotations ===

    @Test
    fun `method with modifier annotation is reported as dead with LOW confidence not excluded`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "transactionalMethod") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.Repo"),
        )

        val dead = findDead(
            graph = graph,
            modifierAnnotated = setOf("Transactional"),
            methodAnnotations = mapOf(
                MethodRef(ClassName("com.example.Service"), "transactionalMethod") to setOf(AnnotationName("Transactional")),
            ),
        )

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }
        val transactionalDead = deadMethods.first { it.memberName == "transactionalMethod" }
        assertEquals(DeadCodeConfidence.LOW, transactionalDead.confidence, "Modifier annotation should set LOW confidence")
    }

    @Test
    fun `method with entry-point annotation is excluded but modifier annotation is not`() {
        val graph = testCallGraph(
            method("com.example.Controller", "handle") to method("com.example.Service", "process"),
            method("com.example.Service", "scheduled") to method("com.example.Repo", "save"),
            method("com.example.Service", "transactional") to method("com.example.Repo", "save"),
            projectClasses = setOf("com.example.Controller", "com.example.Service", "com.example.Repo"),
        )

        val dead = findDead(
            graph = graph,
            excludeAnnotated = setOf("Scheduled"),
            modifierAnnotated = setOf("Transactional"),
            methodAnnotations = mapOf(
                MethodRef(ClassName("com.example.Service"), "scheduled") to setOf(AnnotationName("Scheduled")),
                MethodRef(ClassName("com.example.Service"), "transactional") to setOf(AnnotationName("Transactional")),
            ),
        )

        val deadMethods = dead.filter { it.kind == DeadCodeKind.METHOD }
        assertTrue(deadMethods.none { it.memberName == "scheduled" }, "Entry-point @Scheduled should be excluded")
        assertTrue(deadMethods.any { it.memberName == "transactional" }, "Modifier @Transactional should NOT be excluded, just LOW confidence")
    }

    @Test
    fun `class with modifier annotation is reported as dead with LOW confidence not excluded`() {
        val graph = testCallGraph(
            method("com.example.TransactionalService", "run") to method("com.example.External", "call"),
            projectClasses = setOf("com.example.TransactionalService"),
        )

        val dead = findDead(
            graph = graph,
            modifierAnnotated = setOf("Transactional"),
            classAnnotations = mapOf(
                ClassName("com.example.TransactionalService") to setOf(AnnotationName("Transactional")),
            ),
        )

        assertEquals(1, dead.size)
        assertEquals(DeadCodeConfidence.LOW, dead[0].confidence)
    }

    @Test
    fun `class implementing supertype entry point is excluded from dead code`() {
        val graph = testCallGraph(
            method("com.example.OwnerRepository", "findAll") to method("org.springframework.data.jpa.repository.JpaRepository", "findAll"),
            projectClasses = setOf("com.example.OwnerRepository"),
        )

        val dead = findDead(
            graph = graph,
            classExternalInterfaces = mapOf(
                ClassName("com.example.OwnerRepository") to setOf(ClassName("org.springframework.data.jpa.repository.JpaRepository")),
            ),
            supertypeEntryPoints = setOf(ClassName("org.springframework.data.jpa.repository.JpaRepository")),
        )

        assertTrue(dead.isEmpty(), "Spring Data repository should be excluded from dead code")
    }

    @Test
    fun `class implementing unknown external interface is not excluded by supertype check`() {
        val graph = testCallGraph(
            method("com.example.MyImpl", "doWork") to method("com.external.SomeInterface", "doWork"),
            projectClasses = setOf("com.example.MyImpl"),
        )

        val dead = findDead(
            graph = graph,
            classExternalInterfaces = mapOf(
                ClassName("com.example.MyImpl") to setOf(ClassName("com.external.SomeInterface")),
            ),
            supertypeEntryPoints = setOf(ClassName("org.springframework.data.jpa.repository.JpaRepository")),
        )

        assertEquals(1, dead.size)
        assertEquals(DeadCodeConfidence.HIGH, dead[0].confidence, "Unknown external interface does not lower class-level confidence")
    }

    @Test
    fun `supertype exclusion is disabled when supertypeEntryPoints is empty`() {
        val graph = testCallGraph(
            method("com.example.OwnerRepository", "findAll") to method("org.springframework.data.jpa.repository.JpaRepository", "findAll"),
            projectClasses = setOf("com.example.OwnerRepository"),
        )

        val dead = findDead(
            graph = graph,
            classExternalInterfaces = mapOf(
                ClassName("com.example.OwnerRepository") to setOf(ClassName("org.springframework.data.jpa.repository.JpaRepository")),
            ),
            supertypeEntryPoints = emptySet(),
        )

        assertEquals(1, dead.size, "Without supertype entry points, repository is reported as dead")
        assertEquals(DeadCodeConfidence.HIGH, dead[0].confidence, "No supertype entry points means no special handling")
    }

    @Test
    fun `class extending abstract superclass entry point is excluded from dead code`() {
        val graph = testCallGraph(
            method("com.example.DPoPClaimVerifier", "verify") to method("com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier", "verify"),
            projectClasses = setOf("com.example.DPoPClaimVerifier"),
        )

        val dead = findDead(
            graph = graph,
            classExternalInterfaces = mapOf(
                ClassName("com.example.DPoPClaimVerifier") to setOf(ClassName("com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier")),
            ),
            supertypeEntryPoints = setOf(ClassName("com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier")),
        )

        assertTrue(dead.isEmpty(), "Class extending abstract superclass entry point should be excluded")
    }

    // === Receiver type entry point tests ===

    @Test
    fun `Kt class with matching receiver type is excluded from dead code`() {
        val graph = testCallGraph(
            method("com.example.RoutesKt", "registerRoute") to method("com.example.Service", "process"),
            projectClasses = setOf("com.example.RoutesKt", "com.example.Service"),
        )

        val dead = findDead(
            graph = graph,
            classReceiverTypes = mapOf(
                ClassName("com.example.RoutesKt") to setOf(ClassName("io.ktor.server.routing.Route")),
            ),
            receiverTypeEntryPoints = setOf(ClassName("io.ktor.server.routing.Route")),
        )

        val deadClassNames = dead.filter { it.kind == DeadCodeKind.CLASS }.map { it.className.value }
        assertTrue("com.example.RoutesKt" !in deadClassNames, "Kt class with Route receiver should be excluded as framework entry point")
    }

    @Test
    fun `non-Kt class with matching receiver type is not excluded`() {
        val graph = testCallGraph(
            method("com.example.Routes", "registerRoute") to method("com.example.Service", "process"),
            projectClasses = setOf("com.example.Routes", "com.example.Service"),
        )

        val dead = findDead(
            graph = graph,
            classReceiverTypes = mapOf(
                ClassName("com.example.Routes") to setOf(ClassName("io.ktor.server.routing.Route")),
            ),
            receiverTypeEntryPoints = setOf(ClassName("io.ktor.server.routing.Route")),
        )

        val deadClassNames = dead.filter { it.kind == DeadCodeKind.CLASS }.map { it.className.value }
        assertTrue("com.example.Routes" in deadClassNames, "Non-Kt class should NOT be excluded by receiver type check")
    }

    @Test
    fun `class with non-matching receiver type is not excluded`() {
        val graph = testCallGraph(
            method("com.example.UtilKt", "doSomething") to method("com.example.Service", "process"),
            projectClasses = setOf("com.example.UtilKt", "com.example.Service"),
        )

        val dead = findDead(
            graph = graph,
            classReceiverTypes = mapOf(
                ClassName("com.example.UtilKt") to setOf(ClassName("java.lang.String")),
            ),
            receiverTypeEntryPoints = setOf(ClassName("io.ktor.server.routing.Route")),
        )

        val deadClassNames = dead.filter { it.kind == DeadCodeKind.CLASS }.map { it.className.value }
        assertTrue("com.example.UtilKt" in deadClassNames, "Kt class with non-matching receiver type should still be dead")
    }

    @Test
    fun `receiver type exclusion is disabled when receiverTypeEntryPoints is empty`() {
        val graph = testCallGraph(
            method("com.example.RoutesKt", "registerRoute") to method("com.example.Service", "process"),
            projectClasses = setOf("com.example.RoutesKt", "com.example.Service"),
        )

        val dead = findDead(
            graph = graph,
            classReceiverTypes = mapOf(
                ClassName("com.example.RoutesKt") to setOf(ClassName("io.ktor.server.routing.Route")),
            ),
            receiverTypeEntryPoints = emptySet(),
        )

        val deadClassNames = dead.filter { it.kind == DeadCodeKind.CLASS }.map { it.className.value }
        assertTrue("com.example.RoutesKt" in deadClassNames, "Without receiver type entry points, Kt class should be dead")
    }
}
