package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CallTreeBuilderTest {

    @Test
    fun `single method with no callers produces tree with empty children`() {
        val graph = CallGraph(emptyMap())
        val target = MethodRef(ClassName("com.example.Service"), "doWork")

        val result = CallTreeBuilder.build(graph, listOf(target), maxDepth = 3, CallDirection.CALLERS)

        assertEquals(1, result.size)
        assertEquals("com.example.Service.doWork", result[0].method.qualifiedName)
        assertTrue(result[0].children.isEmpty())
    }

    @Test
    fun `single method with one direct caller produces one child`() {
        val caller = MethodRef(ClassName("com.example.Controller"), "handleRequest")
        val target = MethodRef(ClassName("com.example.Service"), "doWork")
        val graph = CallGraph(
            mapOf(caller to setOf(target)),
            sourceFiles = mapOf(ClassName("com.example.Controller") to "Controller.kt"),
        )

        val result = CallTreeBuilder.build(graph, listOf(target), maxDepth = 3, CallDirection.CALLERS)

        assertEquals(1, result.size)
        val root = result[0]
        assertEquals(1, root.children.size)
        assertEquals("handleRequest", root.children[0].method.methodName)
        assertEquals("Controller.kt", root.children[0].sourceFile)
    }

    @Test
    fun `transitive callers produce nested children up to depth`() {
        val target = MethodRef(ClassName("com.example.C"), "end")
        val middle = MethodRef(ClassName("com.example.B"), "middle")
        val top = MethodRef(ClassName("com.example.A"), "start")
        val graph = CallGraph(
            mapOf(
                top to setOf(middle),
                middle to setOf(target),
            ),
            sourceFiles = mapOf(
                ClassName("com.example.A") to "A.kt",
                ClassName("com.example.B") to "B.kt",
            ),
        )

        val result = CallTreeBuilder.build(graph, listOf(target), maxDepth = 3, CallDirection.CALLERS)

        val root = result[0]
        assertEquals("end", root.method.methodName)
        assertEquals(1, root.children.size)
        assertEquals("middle", root.children[0].method.methodName)
        assertEquals(1, root.children[0].children.size)
        assertEquals("start", root.children[0].children[0].method.methodName)
    }

    @Test
    fun `depth limit stops recursion`() {
        val target = MethodRef(ClassName("com.example.C"), "end")
        val middle = MethodRef(ClassName("com.example.B"), "middle")
        val top = MethodRef(ClassName("com.example.A"), "start")
        val graph = CallGraph(
            mapOf(
                top to setOf(middle),
                middle to setOf(target),
            ),
        )

        val result = CallTreeBuilder.build(graph, listOf(target), maxDepth = 1, CallDirection.CALLERS)

        val root = result[0]
        assertEquals(1, root.children.size)
        assertEquals("middle", root.children[0].method.methodName)
        assertTrue(root.children[0].children.isEmpty(), "Should not recurse past depth 1")
    }

    @Test
    fun `cycle detection prevents infinite recursion`() {
        val a = MethodRef(ClassName("com.example.A"), "callB")
        val b = MethodRef(ClassName("com.example.B"), "callA")
        val graph = CallGraph(
            mapOf(
                a to setOf(b),
                b to setOf(a),
            ),
        )

        val result = CallTreeBuilder.build(graph, listOf(b), maxDepth = 10, CallDirection.CALLERS)

        val root = result[0]
        assertEquals("callA", root.method.methodName)
        assertEquals(1, root.children.size)
        assertEquals("callB", root.children[0].method.methodName)
        assertEquals(1, root.children[0].children.size, "Cycle should show visited node but no further children")
        assertTrue(root.children[0].children[0].children.isEmpty())
    }

    @Test
    fun `filter removes non-matching methods from children`() {
        val target = MethodRef(ClassName("com.example.Service"), "doWork")
        val projectCaller = MethodRef(ClassName("com.example.Controller"), "handle")
        val externalCaller = MethodRef(ClassName("org.springframework.Framework"), "invoke")
        val graph = CallGraph(
            mapOf(
                projectCaller to setOf(target),
                externalCaller to setOf(target),
            ),
        )
        val projectClasses = setOf("com.example.Service", "com.example.Controller")

        val result = CallTreeBuilder.build(
            graph,
            listOf(target),
            maxDepth = 3,
            CallDirection.CALLERS,
            filter = { it.className.value in projectClasses },
        )

        assertEquals(1, result[0].children.size)
        assertEquals("handle", result[0].children[0].method.methodName)
    }

    @Test
    fun `multiple root methods each produce their own tree`() {
        val targetA = MethodRef(ClassName("com.example.RepoA"), "save")
        val targetB = MethodRef(ClassName("com.example.RepoB"), "save")
        val callerA = MethodRef(ClassName("com.example.ServiceA"), "persist")
        val callerB = MethodRef(ClassName("com.example.ServiceB"), "store")
        val graph = CallGraph(
            mapOf(
                callerA to setOf(targetA),
                callerB to setOf(targetB),
            ),
        )

        val result = CallTreeBuilder.build(graph, listOf(targetA, targetB), maxDepth = 3, CallDirection.CALLERS)

        assertEquals(2, result.size)
        assertEquals("com.example.RepoA", result[0].method.className.value)
        assertEquals("com.example.RepoB", result[1].method.className.value)
        assertEquals(1, result[0].children.size)
        assertEquals(1, result[1].children.size)
    }

    @Test
    fun `branching transitive callers produce correct tree`() {
        val buildMsg = MethodRef(ClassName("com.example.UserService"), "buildNotificationMessage")
        val sendDeactivation = MethodRef(ClassName("com.example.UserService"), "sendDeactivationNotification")
        val sendReset = MethodRef(ClassName("com.example.UserService"), "sendResetNotification")
        val deactivateUser = MethodRef(ClassName("com.example.UserService"), "deactivateUser")
        val resetPassword = MethodRef(ClassName("com.example.UserService"), "resetPassword")
        val handleDeactivate = MethodRef(ClassName("com.example.UserRoute"), "handleDeactivate")
        val handleReset = MethodRef(ClassName("com.example.UserRoute"), "handleReset")
        val graph = CallGraph(
            mapOf(
                sendDeactivation to setOf(buildMsg),
                sendReset to setOf(buildMsg),
                deactivateUser to setOf(sendDeactivation),
                resetPassword to setOf(sendReset),
                handleDeactivate to setOf(deactivateUser),
                handleReset to setOf(resetPassword),
            ),
        )

        val result = CallTreeBuilder.build(graph, listOf(buildMsg), maxDepth = 5, CallDirection.CALLERS)

        val root = result[0]
        assertEquals(2, root.children.size, "buildNotificationMessage should have 2 direct callers")

        val sendDeactivationNode = root.children.first { it.method.methodName == "sendDeactivationNotification" }
        assertEquals(1, sendDeactivationNode.children.size)
        assertEquals("deactivateUser", sendDeactivationNode.children[0].method.methodName)
        assertEquals(1, sendDeactivationNode.children[0].children.size)
        assertEquals("handleDeactivate", sendDeactivationNode.children[0].children[0].method.methodName)

        val sendResetNode = root.children.first { it.method.methodName == "sendResetNotification" }
        assertEquals(1, sendResetNode.children.size)
        assertEquals("resetPassword", sendResetNode.children[0].method.methodName)
        assertEquals(1, sendResetNode.children[0].children.size)
        assertEquals("handleReset", sendResetNode.children[0].children[0].method.methodName)
    }

    @Test
    fun `callees direction resolves children as callees`() {
        val a = MethodRef(ClassName("com.example.A"), "start")
        val b = MethodRef(ClassName("com.example.B"), "middle")
        val c = MethodRef(ClassName("com.example.C"), "end")
        val graph = CallGraph(
            mapOf(
                a to setOf(b),
                b to setOf(c),
            ),
            sourceFiles = mapOf(
                ClassName("com.example.B") to "B.kt",
                ClassName("com.example.C") to "C.kt",
            ),
        )

        val result = CallTreeBuilder.build(graph, listOf(a), maxDepth = 3, CallDirection.CALLEES)

        val root = result[0]
        assertEquals("start", root.method.methodName)
        assertEquals(1, root.children.size)
        assertEquals("middle", root.children[0].method.methodName)
        assertEquals("B.kt", root.children[0].sourceFile)
        assertEquals(1, root.children[0].children.size)
        assertEquals("end", root.children[0].children[0].method.methodName)
        assertEquals("C.kt", root.children[0].children[0].sourceFile)
    }

    @Test
    fun `root node has source file when available`() {
        val target = MethodRef(ClassName("com.example.Service"), "doWork")
        val graph = CallGraph(
            emptyMap(),
            sourceFiles = mapOf(ClassName("com.example.Service") to "Service.kt"),
        )

        val result = CallTreeBuilder.build(graph, listOf(target), maxDepth = 3, CallDirection.CALLERS)

        assertEquals("Service.kt", result[0].sourceFile, "Root node should have source file resolved")
    }

    @Test
    fun `synthetic method filter removes equals, hashCode, copy, componentN from children`() {
        val target = MethodRef(ClassName("com.example.Service"), "doWork")
        val realCaller = MethodRef(ClassName("com.example.Controller"), "handle")
        val equalsCaller = MethodRef(ClassName("com.example.Model"), "equals")
        val hashCodeCaller = MethodRef(ClassName("com.example.Model"), "hashCode")
        val copyCaller = MethodRef(ClassName("com.example.Model"), "copy")
        val componentCaller = MethodRef(ClassName("com.example.Model"), "component1")
        val graph = CallGraph(
            mapOf(
                realCaller to setOf(target),
                equalsCaller to setOf(target),
                hashCodeCaller to setOf(target),
                copyCaller to setOf(target),
                componentCaller to setOf(target),
            ),
        )

        val syntheticFilter: (MethodRef) -> Boolean = { !KotlinMethodFilter.isGenerated(it.methodName) }
        val result = CallTreeBuilder.build(
            graph,
            listOf(target),
            maxDepth = 3,
            CallDirection.CALLERS,
            filter = syntheticFilter,
        )

        assertEquals(1, result[0].children.size)
        assertEquals("handle", result[0].children[0].method.methodName)
    }

    @Test
    fun `child nodes include line number from graph`() {
        val target = MethodRef(ClassName("com.example.Service"), "doWork")
        val caller = MethodRef(ClassName("com.example.Controller"), "handle")
        val graph = CallGraph(
            mapOf(caller to setOf(target)),
            sourceFiles = mapOf(
                ClassName("com.example.Controller") to "Controller.kt",
                ClassName("com.example.Service") to "Service.kt",
            ),
            lineNumbers = mapOf(caller to 42),
        )

        val result = CallTreeBuilder.build(graph, listOf(target), maxDepth = 3, CallDirection.CALLERS)

        assertEquals(42, result[0].children[0].lineNumber)
    }

    @Test
    fun `root node includes line number from graph`() {
        val target = MethodRef(ClassName("com.example.Service"), "doWork")
        val graph = CallGraph(
            emptyMap(),
            sourceFiles = mapOf(ClassName("com.example.Service") to "Service.kt"),
            lineNumbers = mapOf(target to 15),
        )

        val result = CallTreeBuilder.build(graph, listOf(target), maxDepth = 3, CallDirection.CALLERS)

        assertEquals(15, result[0].lineNumber)
    }

    @Test
    fun `line number is null when method has no line number in graph`() {
        val target = MethodRef(ClassName("com.example.Service"), "doWork")
        val graph = CallGraph(
            emptyMap(),
            sourceFiles = mapOf(ClassName("com.example.Service") to "Service.kt"),
        )

        val result = CallTreeBuilder.build(graph, listOf(target), maxDepth = 3, CallDirection.CALLERS)

        assertEquals(null, result[0].lineNumber)
    }

    // === Interface dispatch resolution tests ===

    @Test
    fun `callers of impl method includes callers of interface method`() {
        val controller = MethodRef(ClassName("com.example.Controller"), "handle")
        val interfaceMethod = MethodRef(ClassName("com.example.Service"), "process")
        val implMethod = MethodRef(ClassName("com.example.ServiceImpl"), "process")
        val graph = CallGraph(
            mapOf(controller to setOf(interfaceMethod)),
            sourceFiles = mapOf(
                ClassName("com.example.Controller") to "Controller.kt",
                ClassName("com.example.Service") to "Service.kt",
                ClassName("com.example.ServiceImpl") to "ServiceImpl.kt",
            ),
        )
        val interfaceImplementors = mapOf(
            ClassName("com.example.Service") to setOf(ClassName("com.example.ServiceImpl")),
        )
        val classToInterfaces = mapOf(
            ClassName("com.example.ServiceImpl") to setOf(ClassName("com.example.Service")),
        )

        val result = CallTreeBuilder.build(
            graph, listOf(implMethod), maxDepth = 3, CallDirection.CALLERS,
            interfaceImplementors = interfaceImplementors,
            classToInterfaces = classToInterfaces,
        )

        assertEquals(1, result.size)
        val callers = result[0].children.map { it.method.qualifiedName }
        assertTrue("com.example.Controller.handle" in callers, "Should find caller via interface dispatch")
    }

    @Test
    fun `callees of interface method includes implementor methods`() {
        val controller = MethodRef(ClassName("com.example.Controller"), "handle")
        val interfaceMethod = MethodRef(ClassName("com.example.Service"), "process")
        val graph = CallGraph(
            mapOf(controller to setOf(interfaceMethod)),
            sourceFiles = mapOf(
                ClassName("com.example.Controller") to "Controller.kt",
                ClassName("com.example.Service") to "Service.kt",
                ClassName("com.example.ServiceImpl") to "ServiceImpl.kt",
            ),
        )
        val interfaceImplementors = mapOf(
            ClassName("com.example.Service") to setOf(ClassName("com.example.ServiceImpl")),
        )

        val result = CallTreeBuilder.build(
            graph, listOf(controller), maxDepth = 3, CallDirection.CALLEES,
            interfaceImplementors = interfaceImplementors,
        )

        assertEquals(1, result.size)
        val calleeNames = result[0].children.map { it.method.qualifiedName }
        assertTrue("com.example.Service.process" in calleeNames, "Should still show interface call")
        assertTrue("com.example.ServiceImpl.process" in calleeNames, "Should also show implementor")
    }

    @Test
    fun `interface dispatch is not applied when no registry provided`() {
        val controller = MethodRef(ClassName("com.example.Controller"), "handle")
        val interfaceMethod = MethodRef(ClassName("com.example.Service"), "process")
        val implMethod = MethodRef(ClassName("com.example.ServiceImpl"), "process")
        val graph = CallGraph(
            mapOf(controller to setOf(interfaceMethod)),
            sourceFiles = mapOf(
                ClassName("com.example.Controller") to "Controller.kt",
            ),
        )

        val result = CallTreeBuilder.build(
            graph, listOf(implMethod), maxDepth = 3, CallDirection.CALLERS,
        )

        assertTrue(result[0].children.isEmpty(), "Without interface dispatch, impl has no callers")
    }

    // === Annotation tag tests ===

    @Test
    fun `node includes method annotations when annotation map provided`() {
        val target = MethodRef(ClassName("com.example.Controller"), "getOwner")
        val graph = CallGraph(
            emptyMap(),
            sourceFiles = mapOf(ClassName("com.example.Controller") to "Controller.kt"),
        )
        val methodAnnotations = mapOf(
            target to setOf(
                "org.springframework.web.bind.annotation.GetMapping",
                "org.springframework.web.bind.annotation.ResponseBody",
            ),
        )

        val result = CallTreeBuilder.build(
            graph, listOf(target), maxDepth = 3, CallDirection.CALLERS,
            methodAnnotations = methodAnnotations,
        )

        assertEquals(
            listOf(
                AnnotationTag("org.springframework.web.bind.annotation.GetMapping", "spring"),
                AnnotationTag("org.springframework.web.bind.annotation.ResponseBody", "spring"),
            ),
            result[0].annotations.sortedBy { it.name },
        )
    }

    @Test
    fun `node includes class annotations when method has no annotations`() {
        val target = MethodRef(ClassName("com.example.Controller"), "getOwner")
        val graph = CallGraph(
            emptyMap(),
            sourceFiles = mapOf(ClassName("com.example.Controller") to "Controller.kt"),
        )
        val classAnnotations = mapOf(
            ClassName("com.example.Controller") to setOf("org.springframework.web.bind.annotation.RestController"),
        )

        val result = CallTreeBuilder.build(
            graph, listOf(target), maxDepth = 3, CallDirection.CALLERS,
            classAnnotations = classAnnotations,
        )

        assertEquals(
            listOf(AnnotationTag("org.springframework.web.bind.annotation.RestController", "spring")),
            result[0].annotations,
        )
    }

    @Test
    fun `node has empty annotations when no annotation maps provided`() {
        val target = MethodRef(ClassName("com.example.Service"), "doWork")
        val graph = CallGraph(emptyMap())

        val result = CallTreeBuilder.build(graph, listOf(target), maxDepth = 3, CallDirection.CALLERS)

        assertEquals(emptyList<AnnotationTag>(), result[0].annotations)
    }

    @Test
    fun `child nodes also get annotations from maps`() {
        val target = MethodRef(ClassName("com.example.Service"), "doWork")
        val caller = MethodRef(ClassName("com.example.Controller"), "handle")
        val graph = CallGraph(
            mapOf(caller to setOf(target)),
            sourceFiles = mapOf(ClassName("com.example.Controller") to "Controller.kt"),
        )
        val methodAnnotations = mapOf(
            caller to setOf("org.springframework.web.bind.annotation.GetMapping"),
        )

        val result = CallTreeBuilder.build(
            graph, listOf(target), maxDepth = 3, CallDirection.CALLERS,
            methodAnnotations = methodAnnotations,
        )

        assertEquals(
            listOf(AnnotationTag("org.springframework.web.bind.annotation.GetMapping", "spring")),
            result[0].children[0].annotations,
        )
    }

    @Test
    fun `annotations without known framework have null framework`() {
        val target = MethodRef(ClassName("com.example.Controller"), "doWork")
        val graph = CallGraph(
            emptyMap(),
            sourceFiles = mapOf(ClassName("com.example.Controller") to "Controller.kt"),
        )
        val methodAnnotations = mapOf(
            target to setOf("com.example.CustomAnnotation"),
        )

        val result = CallTreeBuilder.build(
            graph, listOf(target), maxDepth = 3, CallDirection.CALLERS,
            methodAnnotations = methodAnnotations,
        )

        assertEquals(listOf(AnnotationTag("com.example.CustomAnnotation", null)), result[0].annotations)
    }

    @Test
    fun `mixed known and unknown annotations have correct framework tags`() {
        val target = MethodRef(ClassName("com.example.Controller"), "doWork")
        val graph = CallGraph(
            emptyMap(),
            sourceFiles = mapOf(ClassName("com.example.Controller") to "Controller.kt"),
        )
        val methodAnnotations = mapOf(
            target to setOf("org.springframework.web.bind.annotation.GetMapping", "com.example.CustomAnnotation"),
        )

        val result = CallTreeBuilder.build(
            graph, listOf(target), maxDepth = 3, CallDirection.CALLERS,
            methodAnnotations = methodAnnotations,
        )

        assertEquals(
            listOf(
                AnnotationTag("com.example.CustomAnnotation", null),
                AnnotationTag("org.springframework.web.bind.annotation.GetMapping", "spring"),
            ),
            result[0].annotations,
        )
    }
}
