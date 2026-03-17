package no.f12.codenavigator

import kotlin.test.Test
import kotlin.test.assertEquals

class CalleeTreeFormatterTest {

    @Test
    fun `method with no callees shows no callees message`() {
        val graph = CallGraph(emptyMap())
        val target = MethodRef("com.example.Service", "doWork")

        val result = CalleeTreeFormatter.format(graph, listOf(target), maxDepth = 3)

        assertEquals(
            """
            com.example.Service.doWork
              (no callees)
            """.trimIndent(),
            result,
        )
    }

    @Test
    fun `method with one callee shows it indented`() {
        val caller = MethodRef("com.example.Controller", "handleRequest")
        val callee = MethodRef("com.example.Service", "doWork")
        val graph = CallGraph(
            mapOf(caller to setOf(callee)),
            sourceFiles = mapOf("com.example.Service" to "Service.kt"),
        )

        val result = CalleeTreeFormatter.format(graph, listOf(caller), maxDepth = 3)

        assertEquals(
            """
            com.example.Controller.handleRequest
              → com.example.Service.doWork (Service.kt)
            """.trimIndent(),
            result,
        )
    }

    @Test
    fun `multiple callees sorted alphabetically`() {
        val caller = MethodRef("com.example.Orchestrator", "run")
        val graph = CallGraph(
            mapOf(
                caller to setOf(
                    MethodRef("com.example.ZService", "execute"),
                    MethodRef("com.example.AService", "execute"),
                ),
            ),
            sourceFiles = mapOf(
                "com.example.AService" to "AService.kt",
                "com.example.ZService" to "ZService.kt",
            ),
        )

        val result = CalleeTreeFormatter.format(graph, listOf(caller), maxDepth = 3)

        assertEquals(
            """
            com.example.Orchestrator.run
              → com.example.AService.execute (AService.kt)
              → com.example.ZService.execute (ZService.kt)
            """.trimIndent(),
            result,
        )
    }

    @Test
    fun `transitive callees render nested`() {
        val a = MethodRef("com.example.A", "start")
        val b = MethodRef("com.example.B", "middle")
        val c = MethodRef("com.example.C", "end")
        val graph = CallGraph(
            mapOf(
                a to setOf(b),
                b to setOf(c),
            ),
            sourceFiles = mapOf(
                "com.example.B" to "B.kt",
                "com.example.C" to "C.kt",
            ),
        )

        val result = CalleeTreeFormatter.format(graph, listOf(a), maxDepth = 3)

        assertEquals(
            """
            com.example.A.start
              → com.example.B.middle (B.kt)
                → com.example.C.end (C.kt)
            """.trimIndent(),
            result,
        )
    }

    @Test
    fun `depth limit stops recursion`() {
        val a = MethodRef("com.example.A", "start")
        val b = MethodRef("com.example.B", "middle")
        val c = MethodRef("com.example.C", "end")
        val graph = CallGraph(
            mapOf(
                a to setOf(b),
                b to setOf(c),
            ),
            sourceFiles = mapOf(
                "com.example.B" to "B.kt",
                "com.example.C" to "C.kt",
            ),
        )

        val result = CalleeTreeFormatter.format(graph, listOf(a), maxDepth = 1)

        assertEquals(
            """
            com.example.A.start
              → com.example.B.middle (B.kt)
            """.trimIndent(),
            result,
        )
    }

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

        val result = CalleeTreeFormatter.format(graph, listOf(a), maxDepth = 10)

        assertEquals(
            """
            com.example.A.callB
              → com.example.B.callA (B.kt)
                → com.example.A.callB (A.kt)
            """.trimIndent(),
            result,
        )
    }

    @Test
    fun `source file shown as unknown when not tracked`() {
        val caller = MethodRef("com.example.Service", "run")
        val callee = MethodRef("com.example.External", "invoke")
        val graph = CallGraph(
            mapOf(caller to setOf(callee)),
            sourceFiles = emptyMap(),
        )

        val result = CalleeTreeFormatter.format(graph, listOf(caller), maxDepth = 3)

        assertEquals(
            """
            com.example.Service.run
              → com.example.External.invoke (<unknown>)
            """.trimIndent(),
            result,
        )
    }

    @Test
    fun `multiple matched methods each get their own tree`() {
        val a = MethodRef("com.example.ServiceA", "run")
        val b = MethodRef("com.example.ServiceB", "run")
        val calleeA = MethodRef("com.example.RepoA", "save")
        val calleeB = MethodRef("com.example.RepoB", "save")
        val graph = CallGraph(
            mapOf(
                a to setOf(calleeA),
                b to setOf(calleeB),
            ),
            sourceFiles = mapOf(
                "com.example.RepoA" to "RepoA.kt",
                "com.example.RepoB" to "RepoB.kt",
            ),
        )

        val result = CalleeTreeFormatter.format(graph, listOf(a, b), maxDepth = 3)

        assertEquals(
            """
            com.example.ServiceA.run
              → com.example.RepoA.save (RepoA.kt)
            
            com.example.ServiceB.run
              → com.example.RepoB.save (RepoB.kt)
            """.trimIndent(),
            result,
        )
    }
}
