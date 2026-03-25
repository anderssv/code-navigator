package no.f12.codenavigator.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class CalleeTreeFormatterTest {

    @Test
    fun `method with no callees shows no callees message`() {
        val graph = CallGraph(emptyMap())
        val target = MethodRef(ClassName("com.example.Service"), "doWork")

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
        val caller = MethodRef(ClassName("com.example.Controller"), "handleRequest")
        val callee = MethodRef(ClassName("com.example.Service"), "doWork")
        val graph = CallGraph(
            mapOf(caller to setOf(callee)),
            sourceFiles = mapOf(ClassName("com.example.Service") to "Service.kt"),
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
        val caller = MethodRef(ClassName("com.example.Orchestrator"), "run")
        val graph = CallGraph(
            mapOf(
                caller to setOf(
                    MethodRef(ClassName("com.example.ZService"), "execute"),
                    MethodRef(ClassName("com.example.AService"), "execute"),
                ),
            ),
            sourceFiles = mapOf(
                ClassName("com.example.AService") to "AService.kt",
                ClassName("com.example.ZService") to "ZService.kt",
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
        val caller = MethodRef(ClassName("com.example.Service"), "run")
        val callee = MethodRef(ClassName("com.example.External"), "invoke")
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
    fun `filter removes external callees from output`() {
        val caller = MethodRef(ClassName("com.example.Service"), "run")
        val projectCallee = MethodRef(ClassName("com.example.Repo"), "save")
        val jdkCallee = MethodRef(ClassName("java.lang.Object"), "toString")
        val kotlinCallee = MethodRef(ClassName("kotlin.jvm.internal.Intrinsics"), "checkNotNullParameter")
        val graph = CallGraph(
            mapOf(caller to setOf(projectCallee, jdkCallee, kotlinCallee)),
            sourceFiles = mapOf(
                ClassName("com.example.Service") to "Service.kt",
                ClassName("com.example.Repo") to "Repo.kt",
            ),
        )
        val projectClasses = setOf("com.example.Service", "com.example.Repo")

        val result = CalleeTreeFormatter.format(
            graph,
            listOf(caller),
            maxDepth = 3,
            filter = { it.className.value in projectClasses },
        )

        assertEquals(
            """
            com.example.Service.run
              → com.example.Repo.save (Repo.kt)
            """.trimIndent(),
            result,
        )
    }

    @Test
    fun `filter with all callees filtered shows no callees message`() {
        val caller = MethodRef(ClassName("com.example.Service"), "run")
        val jdkCallee = MethodRef(ClassName("java.lang.Object"), "toString")
        val graph = CallGraph(
            mapOf(caller to setOf(jdkCallee)),
        )
        val projectClasses = setOf("com.example.Service")

        val result = CalleeTreeFormatter.format(
            graph,
            listOf(caller),
            maxDepth = 3,
            filter = { it.className.value in projectClasses },
        )

        assertEquals(
            """
            com.example.Service.run
              (no callees)
            """.trimIndent(),
            result,
        )
    }

    @Test
    fun `filter applies to transitive callees`() {
        val a = MethodRef(ClassName("com.example.A"), "start")
        val b = MethodRef(ClassName("com.example.B"), "middle")
        val jdk = MethodRef(ClassName("java.lang.String"), "valueOf")
        val c = MethodRef(ClassName("com.example.C"), "end")
        val graph = CallGraph(
            mapOf(
                a to setOf(b),
                b to setOf(jdk, c),
            ),
            sourceFiles = mapOf(
                ClassName("com.example.B") to "B.kt",
                ClassName("com.example.C") to "C.kt",
            ),
        )
        val projectClasses = setOf("com.example.A", "com.example.B", "com.example.C")

        val result = CalleeTreeFormatter.format(
            graph,
            listOf(a),
            maxDepth = 3,
            filter = { it.className.value in projectClasses },
        )

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
    fun `multiple matched methods each get their own tree`() {
        val a = MethodRef(ClassName("com.example.ServiceA"), "run")
        val b = MethodRef(ClassName("com.example.ServiceB"), "run")
        val calleeA = MethodRef(ClassName("com.example.RepoA"), "save")
        val calleeB = MethodRef(ClassName("com.example.RepoB"), "save")
        val graph = CallGraph(
            mapOf(
                a to setOf(calleeA),
                b to setOf(calleeB),
            ),
            sourceFiles = mapOf(
                ClassName("com.example.RepoA") to "RepoA.kt",
                ClassName("com.example.RepoB") to "RepoB.kt",
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
