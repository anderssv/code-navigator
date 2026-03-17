package no.f12.codenavigator

import kotlin.test.Test
import kotlin.test.assertEquals

class CallerTreeFormatterTest {

    // [TEST] Single method with no callers shows "No callers found" message
    @Test
    fun `method with no callers shows no callers message`() {
        val graph = CallGraph(emptyMap())
        val target = MethodRef("com.example.Service", "doWork")

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
        val caller = MethodRef("com.example.Controller", "handleRequest")
        val target = MethodRef("com.example.Service", "doWork")
        val graph = CallGraph(
            mapOf(caller to setOf(target)),
            sourceFiles = mapOf("com.example.Controller" to "Controller.kt"),
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
        val target = MethodRef("com.example.Repo", "save")
        val callerZ = MethodRef("com.example.ZService", "store")
        val callerA = MethodRef("com.example.AService", "persist")
        val graph = CallGraph(
            mapOf(
                callerZ to setOf(target),
                callerA to setOf(target),
            ),
            sourceFiles = mapOf(
                "com.example.ZService" to "ZService.kt",
                "com.example.AService" to "AService.kt",
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
        val target = MethodRef("com.example.C", "end")
        val middle = MethodRef("com.example.B", "middle")
        val top = MethodRef("com.example.A", "start")
        val graph = CallGraph(
            mapOf(
                top to setOf(middle),
                middle to setOf(target),
            ),
            sourceFiles = mapOf(
                "com.example.A" to "A.kt",
                "com.example.B" to "B.kt",
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
        val target = MethodRef("com.example.C", "end")
        val middle = MethodRef("com.example.B", "middle")
        val top = MethodRef("com.example.A", "start")
        val graph = CallGraph(
            mapOf(
                top to setOf(middle),
                middle to setOf(target),
            ),
            sourceFiles = mapOf(
                "com.example.A" to "A.kt",
                "com.example.B" to "B.kt",
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
        val a = MethodRef("com.example.A", "callB")
        val b = MethodRef("com.example.B", "callA")
        val graph = CallGraph(
            mapOf(
                a to setOf(b),
                b to setOf(a),
            ),
            sourceFiles = mapOf(
                "com.example.A" to "A.kt",
                "com.example.B" to "B.kt",
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
        val target = MethodRef("com.example.Service", "run")
        val caller = MethodRef("com.example.External", "invoke")
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
        val targetA = MethodRef("com.example.RepoA", "save")
        val targetB = MethodRef("com.example.RepoB", "save")
        val callerA = MethodRef("com.example.ServiceA", "persist")
        val callerB = MethodRef("com.example.ServiceB", "store")
        val graph = CallGraph(
            mapOf(
                callerA to setOf(targetA),
                callerB to setOf(targetB),
            ),
            sourceFiles = mapOf(
                "com.example.ServiceA" to "ServiceA.kt",
                "com.example.ServiceB" to "ServiceB.kt",
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
}
