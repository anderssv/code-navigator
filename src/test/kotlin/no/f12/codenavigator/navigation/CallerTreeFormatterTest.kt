package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class CallerTreeFormatterTest {

    // [TEST] Single method with no callers shows "No callers found" message
    @Test
    fun `method with no callers shows no callers message`() {
        val graph = CallGraph(emptyMap())
        val target = MethodRef(ClassName("com.example.Service"), "doWork")

        val result = CallerTreeFormatter.format(graph, listOf(target), maxDepth = 3)

        assertEquals(
            """
            com.example.Service.doWork
              (no callers)
            """.trimIndent(),
            result,
        )
    }

    // [TEST] Single method with one direct caller shows two-level tree
    @Test
    fun `method with one direct caller shows caller indented`() {
        val caller = MethodRef(ClassName("com.example.Controller"), "handleRequest")
        val target = MethodRef(ClassName("com.example.Service"), "doWork")
        val graph = CallGraph(
            mapOf(caller to setOf(target)),
            sourceFiles = mapOf(ClassName("com.example.Controller") to "Controller.kt"),
        )

        val result = CallerTreeFormatter.format(graph, listOf(target), maxDepth = 3)

        assertEquals(
            """
            com.example.Service.doWork
              ← com.example.Controller.handleRequest (Controller.kt)
            """.trimIndent(),
            result,
        )
    }

    // [TEST] Single method with multiple direct callers sorts them alphabetically
    @Test
    fun `multiple direct callers are sorted alphabetically`() {
        val target = MethodRef(ClassName("com.example.Repo"), "save")
        val callerZ = MethodRef(ClassName("com.example.ZService"), "store")
        val callerA = MethodRef(ClassName("com.example.AService"), "persist")
        val graph = CallGraph(
            mapOf(
                callerZ to setOf(target),
                callerA to setOf(target),
            ),
            sourceFiles = mapOf(
                ClassName("com.example.ZService") to "ZService.kt",
                ClassName("com.example.AService") to "AService.kt",
            ),
        )

        val result = CallerTreeFormatter.format(graph, listOf(target), maxDepth = 3)

        assertEquals(
            """
            com.example.Repo.save
              ← com.example.AService.persist (AService.kt)
              ← com.example.ZService.store (ZService.kt)
            """.trimIndent(),
            result,
        )
    }

    // [TEST] Transitive callers render as indented tree up to depth
    @Test
    fun `transitive callers render as nested indented tree`() {
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

        val result = CallerTreeFormatter.format(graph, listOf(target), maxDepth = 3)

        assertEquals(
            """
            com.example.C.end
              ← com.example.B.middle (B.kt)
                ← com.example.A.start (A.kt)
            """.trimIndent(),
            result,
        )
    }

    // [TEST] Depth limit stops recursion
    @Test
    fun `depth limit stops recursion at specified depth`() {
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

        val result = CallerTreeFormatter.format(graph, listOf(target), maxDepth = 1)

        assertEquals(
            """
            com.example.C.end
              ← com.example.B.middle (B.kt)
            """.trimIndent(),
            result,
        )
    }

    // [TEST] Cycle detection prevents infinite recursion
    @Test
    fun `cycle detection prevents infinite recursion`() {
        val a = MethodRef(ClassName("com.example.A"), "callB")
        val b = MethodRef(ClassName("com.example.B"), "callA")
        val graph = CallGraph(
            mapOf(
                a to setOf(b),
                b to setOf(a),
            ),
            sourceFiles = mapOf(
                ClassName("com.example.A") to "A.kt",
                ClassName("com.example.B") to "B.kt",
            ),
        )

        val result = CallerTreeFormatter.format(graph, listOf(b), maxDepth = 10)

        assertEquals(
            """
            com.example.B.callA
              ← com.example.A.callB (A.kt)
                ← com.example.B.callA (B.kt)
            """.trimIndent(),
            result,
        )
    }

    // [TEST] Source file is shown as <unknown> when not available
    @Test
    fun `source file shown as unknown when not tracked`() {
        val target = MethodRef(ClassName("com.example.Service"), "run")
        val caller = MethodRef(ClassName("com.example.External"), "invoke")
        val graph = CallGraph(
            mapOf(caller to setOf(target)),
            sourceFiles = emptyMap(),
        )

        val result = CallerTreeFormatter.format(graph, listOf(target), maxDepth = 3)

        assertEquals(
            """
            com.example.Service.run
              ← com.example.External.invoke (<unknown>)
            """.trimIndent(),
            result,
        )
    }

    // [TEST] Multiple matched methods each get their own tree
    @Test
    fun `multiple matched methods each get their own tree`() {
        val targetA = MethodRef(ClassName("com.example.RepoA"), "save")
        val targetB = MethodRef(ClassName("com.example.RepoB"), "save")
        val callerA = MethodRef(ClassName("com.example.ServiceA"), "persist")
        val callerB = MethodRef(ClassName("com.example.ServiceB"), "store")
        val graph = CallGraph(
            mapOf(
                callerA to setOf(targetA),
                callerB to setOf(targetB),
            ),
            sourceFiles = mapOf(
                ClassName("com.example.ServiceA") to "ServiceA.kt",
                ClassName("com.example.ServiceB") to "ServiceB.kt",
            ),
        )

        val result = CallerTreeFormatter.format(graph, listOf(targetA, targetB), maxDepth = 3)

        assertEquals(
            """
            com.example.RepoA.save
              ← com.example.ServiceA.persist (ServiceA.kt)
            
            com.example.RepoB.save
              ← com.example.ServiceB.store (ServiceB.kt)
            """.trimIndent(),
            result,
        )
    }

    @Test
    fun `branching transitive callers each show their own caller chains`() {
        // buildNotificationMessage ← sendDeactivationNotification ← deactivateUser ← handleDeactivate
        // buildNotificationMessage ← sendResetNotification ← resetPassword ← handleReset
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
            sourceFiles = mapOf(
                ClassName("com.example.UserService") to "UserService.kt",
                ClassName("com.example.UserRoute") to "UserRoute.kt",
            ),
        )

        val result = CallerTreeFormatter.format(graph, listOf(buildMsg), maxDepth = 5)

        assertEquals(
            """
            com.example.UserService.buildNotificationMessage
              ← com.example.UserService.sendDeactivationNotification (UserService.kt)
                ← com.example.UserService.deactivateUser (UserService.kt)
                  ← com.example.UserRoute.handleDeactivate (UserRoute.kt)
              ← com.example.UserService.sendResetNotification (UserService.kt)
                ← com.example.UserService.resetPassword (UserService.kt)
                  ← com.example.UserRoute.handleReset (UserRoute.kt)
            """.trimIndent(),
            result,
        )
    }

    @Test
    fun `filter removes external callers from output`() {
        val target = MethodRef(ClassName("com.example.Service"), "doWork")
        val projectCaller = MethodRef(ClassName("com.example.Controller"), "handle")
        val externalCaller = MethodRef(ClassName("org.springframework.Framework"), "invoke")
        val graph = CallGraph(
            mapOf(
                projectCaller to setOf(target),
                externalCaller to setOf(target),
            ),
            sourceFiles = mapOf(
                ClassName("com.example.Controller") to "Controller.kt",
            ),
        )
        val projectClasses = setOf("com.example.Service", "com.example.Controller")

        val result = CallerTreeFormatter.format(
            graph,
            listOf(target),
            maxDepth = 3,
            filter = { it.className.value in projectClasses },
        )

        assertEquals(
            """
            com.example.Service.doWork
              ← com.example.Controller.handle (Controller.kt)
            """.trimIndent(),
            result,
        )
    }
}
