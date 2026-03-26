package no.f12.codenavigator

import no.f12.codenavigator.analysis.CoupledPair
import no.f12.codenavigator.analysis.FileChurn
import no.f12.codenavigator.analysis.Hotspot
import no.f12.codenavigator.navigation.CallDirection
import no.f12.codenavigator.navigation.CallGraph
import no.f12.codenavigator.navigation.ClassDetail
import no.f12.codenavigator.navigation.ClassInfo
import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.FieldDetail
import no.f12.codenavigator.navigation.ImplementorInfo
import no.f12.codenavigator.navigation.InterfaceRegistry
import no.f12.codenavigator.navigation.MethodDetail
import no.f12.codenavigator.navigation.MethodRef
import no.f12.codenavigator.navigation.PackageDependencies
import no.f12.codenavigator.navigation.PackageName
import no.f12.codenavigator.navigation.SymbolInfo
import no.f12.codenavigator.navigation.SymbolKind
import no.f12.codenavigator.navigation.DsmMatrix
import no.f12.codenavigator.navigation.RankedType
import no.f12.codenavigator.navigation.ClassComplexity
import no.f12.codenavigator.navigation.CycleDetail
import no.f12.codenavigator.navigation.CycleEdge
import no.f12.codenavigator.navigation.DeadCode
import no.f12.codenavigator.navigation.DeadCodeKind
import no.f12.codenavigator.navigation.MetricsResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonFormatterTest {

    // === ClassInfo formatting ===

    @Test
    fun `empty class list produces empty JSON array`() {
        val result = JsonFormatter.formatClasses(emptyList())

        assertEquals("[]", result)
    }
    @Test
    fun `single class produces JSON array with one object`() {
        val classes = listOf(
            ClassInfo(ClassName("com.example.Foo"), "Foo.kt", "com/example/Foo.kt", isUserDefinedClass = true),
        )

        val result = JsonFormatter.formatClasses(classes)

        assertEquals(
            """[{"className":"com.example.Foo","sourceFile":"Foo.kt","sourcePath":"com/example/Foo.kt"}]""",
            result,
        )
    }
    @Test
    fun `multiple classes produce JSON array sorted by className`() {
        val classes = listOf(
            ClassInfo(ClassName("com.example.Zebra"), "Zebra.kt", "com/example/Zebra.kt", isUserDefinedClass = true),
            ClassInfo(ClassName("com.example.Alpha"), "Alpha.kt", "com/example/Alpha.kt", isUserDefinedClass = true),
        )

        val result = JsonFormatter.formatClasses(classes)

        assertEquals(
            """[{"className":"com.example.Alpha","sourceFile":"Alpha.kt","sourcePath":"com/example/Alpha.kt"},""" +
                """{"className":"com.example.Zebra","sourceFile":"Zebra.kt","sourcePath":"com/example/Zebra.kt"}]""",
            result,
        )
    }

    @Test
    fun `special characters in class names are escaped in JSON`() {
        val classes = listOf(
            ClassInfo(ClassName("com.example.Foo\"Bar"), "Foo\"Bar.kt", "com/example/Foo\"Bar.kt", isUserDefinedClass = true),
        )

        val result = JsonFormatter.formatClasses(classes)

        assertEquals(
            """[{"className":"com.example.Foo\"Bar","sourceFile":"Foo\"Bar.kt","sourcePath":"com/example/Foo\"Bar.kt"}]""",
            result,
        )
    }

    // === SymbolInfo formatting ===

    @Test
    fun `empty symbol list produces empty JSON array`() {
        val result = JsonFormatter.formatSymbols(emptyList())

        assertEquals("[]", result)
    }

    @Test
    fun `single symbol produces JSON object with all fields`() {
        val symbols = listOf(
            SymbolInfo("com.example", "Service", "doWork", SymbolKind.METHOD, "Service.kt"),
        )

        val result = JsonFormatter.formatSymbols(symbols)

        assertEquals(
            """[{"package":"com.example","class":"Service","symbol":"doWork","kind":"method","sourceFile":"Service.kt"}]""",
            result,
        )
    }

    @Test
    fun `field symbol kind is lowercase string`() {
        val symbols = listOf(
            SymbolInfo("com.example", "Entity", "name", SymbolKind.FIELD, "Entity.kt"),
        )

        val result = JsonFormatter.formatSymbols(symbols)

        assertEquals(
            """[{"package":"com.example","class":"Entity","symbol":"name","kind":"field","sourceFile":"Entity.kt"}]""",
            result,
        )
    }

    // === ClassDetail formatting ===

    @Test
    fun `empty class detail list produces empty JSON array`() {
        val result = JsonFormatter.formatClassDetails(emptyList())

        assertEquals("[]", result)
    }

    @Test
    fun `single class detail with fields and methods`() {
        val details = listOf(
            ClassDetail(
                className = "com.example.Order",
                sourceFile = "Order.kt",
                superClass = "com.example.BaseEntity",
                interfaces = listOf("com.example.Identifiable"),
                fields = listOf(FieldDetail("id", "Long"), FieldDetail("name", "String")),
                methods = listOf(MethodDetail("getName", emptyList(), "String")),
            ),
        )

        val result = JsonFormatter.formatClassDetails(details)

        assertEquals(
            """[{"className":"com.example.Order","sourceFile":"Order.kt","superClass":"com.example.BaseEntity",""" +
                """"interfaces":["com.example.Identifiable"],""" +
                """"fields":[{"name":"id","type":"Long"},{"name":"name","type":"String"}],""" +
                """"methods":[{"name":"getName","parameters":[],"returnType":"String"}]}]""",
            result,
        )
    }

    @Test
    fun `class detail with null superClass omits superClass field`() {
        val details = listOf(
            ClassDetail(
                className = "com.example.Simple",
                sourceFile = "Simple.kt",
                superClass = null,
                interfaces = emptyList(),
                fields = emptyList(),
                methods = emptyList(),
            ),
        )

        val result = JsonFormatter.formatClassDetails(details)

        assertEquals(
            """[{"className":"com.example.Simple","sourceFile":"Simple.kt","interfaces":[],"fields":[],"methods":[]}]""",
            result,
        )
    }

    // === Call tree formatting ===

    @Test
    fun `method with no callees produces empty children array`() {
        val graph = CallGraph(emptyMap())
        val method = MethodRef(ClassName("com.example.Service"), "doWork")

        val result = JsonFormatter.formatCallTree(graph, listOf(method), maxDepth = 3, CallDirection.CALLEES)

        assertEquals(
            """[{"method":"com.example.Service.doWork","children":[]}]""",
            result,
        )
    }

    @Test
    fun `method with callees produces nested tree structure`() {
        val caller = MethodRef(ClassName("com.example.Controller"), "handle")
        val callee = MethodRef(ClassName("com.example.Service"), "doWork")
        val graph = CallGraph(
            mapOf(caller to setOf(callee)),
            sourceFiles = mapOf(ClassName("com.example.Service") to "Service.kt"),
        )

        val result = JsonFormatter.formatCallTree(graph, listOf(caller), maxDepth = 3, CallDirection.CALLEES)

        assertEquals(
            """[{"method":"com.example.Controller.handle","children":[""" +
                """{"method":"com.example.Service.doWork","sourceFile":"Service.kt","children":[]}]}]""",
            result,
        )
    }

    @Test
    fun `transitive call tree produces nested children`() {
        val a = MethodRef(ClassName("com.example.A"), "start")
        val b = MethodRef(ClassName("com.example.B"), "middle")
        val c = MethodRef(ClassName("com.example.C"), "end")
        val graph = CallGraph(
            mapOf(a to setOf(b), b to setOf(c)),
            sourceFiles = mapOf(ClassName("com.example.B") to "B.kt", ClassName("com.example.C") to "C.kt"),
        )

        val result = JsonFormatter.formatCallTree(graph, listOf(a), maxDepth = 3, CallDirection.CALLEES)

        assertEquals(
            """[{"method":"com.example.A.start","children":[""" +
                """{"method":"com.example.B.middle","sourceFile":"B.kt","children":[""" +
                """{"method":"com.example.C.end","sourceFile":"C.kt","children":[]}]}]}]""",
            result,
        )
    }

    @Test
    fun `depth limit stops recursion in JSON output`() {
        val a = MethodRef(ClassName("com.example.A"), "start")
        val b = MethodRef(ClassName("com.example.B"), "middle")
        val c = MethodRef(ClassName("com.example.C"), "end")
        val graph = CallGraph(
            mapOf(a to setOf(b), b to setOf(c)),
            sourceFiles = mapOf(ClassName("com.example.B") to "B.kt", ClassName("com.example.C") to "C.kt"),
        )

        val result = JsonFormatter.formatCallTree(graph, listOf(a), maxDepth = 1, CallDirection.CALLEES)

        assertEquals(
            """[{"method":"com.example.A.start","children":[""" +
                """{"method":"com.example.B.middle","sourceFile":"B.kt","children":[]}]}]""",
            result,
        )
    }

    @Test
    fun `cycle detection prevents infinite recursion in JSON`() {
        val a = MethodRef(ClassName("com.example.A"), "callB")
        val b = MethodRef(ClassName("com.example.B"), "callA")
        val graph = CallGraph(
            mapOf(a to setOf(b), b to setOf(a)),
            sourceFiles = mapOf(ClassName("com.example.A") to "A.kt", ClassName("com.example.B") to "B.kt"),
        )

        val result = JsonFormatter.formatCallTree(graph, listOf(a), maxDepth = 10, CallDirection.CALLEES)

        assertEquals(
            """[{"method":"com.example.A.callB","children":[""" +
                """{"method":"com.example.B.callA","sourceFile":"B.kt","children":[""" +
                """{"method":"com.example.A.callB","sourceFile":"A.kt","children":[]}]}]}]""",
            result,
        )
    }

    // === Interface formatting ===

    @Test
    fun `empty interface list produces empty JSON array`() {
        val registry = InterfaceRegistry(emptyMap())

        val result = JsonFormatter.formatInterfaces(registry, emptyList())

        assertEquals("[]", result)
    }

    @Test
    fun `interface with implementors produces nested structure`() {
        val registry = InterfaceRegistry(
            mapOf(
                ClassName("com.example.Repository") to listOf(
                    ImplementorInfo(ClassName("com.example.SqlRepo"), "SqlRepo.kt"),
                    ImplementorInfo(ClassName("com.example.FakeRepo"), "FakeRepo.kt"),
                ),
            ),
        )

        val result = JsonFormatter.formatInterfaces(registry, listOf(ClassName("com.example.Repository")))

        assertEquals(
            """[{"interface":"com.example.Repository","implementors":[""" +
                """{"className":"com.example.FakeRepo","sourceFile":"FakeRepo.kt"},""" +
                """{"className":"com.example.SqlRepo","sourceFile":"SqlRepo.kt"}]}]""",
            result,
        )
    }

    // === Package dependencies formatting ===

    @Test
    fun `empty package list produces empty JSON array`() {
        val deps = PackageDependencies(emptyMap())

        val result = JsonFormatter.formatPackageDeps(deps, emptyList())

        assertEquals("[]", result)
    }

    @Test
    fun `package with dependencies produces nested structure`() {
        val deps = PackageDependencies(
            mapOf(PackageName("com.example.services") to listOf(PackageName("com.example.domain"), PackageName("com.example.repo"))),
        )

        val result = JsonFormatter.formatPackageDeps(deps, listOf(PackageName("com.example.services")))

        assertEquals(
            """[{"package":"com.example.services","dependencies":["com.example.domain","com.example.repo"]}]""",
            result,
        )
    }

    @Test
    fun `reverse mode shows dependents instead of dependencies`() {
        val deps = PackageDependencies(
            mapOf(
                PackageName("com.example.services") to listOf(PackageName("com.example.domain")),
                PackageName("com.example.ktor") to listOf(PackageName("com.example.domain")),
            ),
        )

        val result = JsonFormatter.formatPackageDeps(
            deps,
            listOf(PackageName("com.example.domain")),
            reverse = true,
        )

        assertEquals(
            """[{"package":"com.example.domain","dependents":["com.example.ktor","com.example.services"]}]""",
            result,
        )
    }

    // === Hotspot formatting ===

    @Test
    fun `empty hotspots produce empty JSON array`() {
        val result = JsonFormatter.formatHotspots(emptyList())

        assertEquals("[]", result)
    }

    @Test
    fun `hotspots produce JSON array with file, revisions, totalChurn`() {
        val hotspots = listOf(
            Hotspot("src/Foo.kt", 10, 150),
            Hotspot("src/Bar.kt", 5, 30),
        )

        val result = JsonFormatter.formatHotspots(hotspots)

        assertEquals(
            """[{"file":"src/Foo.kt","revisions":10,"totalChurn":150},{"file":"src/Bar.kt","revisions":5,"totalChurn":30}]""",
            result,
        )
    }

    // === Coupling formatting ===

    @Test
    fun `empty coupling produces empty JSON array`() {
        val result = JsonFormatter.formatCoupling(emptyList())

        assertEquals("[]", result)
    }

    @Test
    fun `coupling produces JSON with entity, coupled, degree, sharedRevs, avgRevs`() {
        val pairs = listOf(
            CoupledPair("src/Foo.kt", "src/Bar.kt", 85, 10, 12),
        )

        val result = JsonFormatter.formatCoupling(pairs)

        assertEquals(
            """[{"entity":"src/Foo.kt","coupled":"src/Bar.kt","degree":85,"sharedRevs":10,"avgRevs":12}]""",
            result,
        )
    }

    // === Churn formatting ===

    @Test
    fun `empty churn produces empty JSON array`() {
        val result = JsonFormatter.formatChurn(emptyList())

        assertEquals("[]", result)
    }

    @Test
    fun `churn produces JSON with file, added, deleted, commits`() {
        val churn = listOf(
            FileChurn("src/Foo.kt", 100, 50, 10),
            FileChurn("src/Bar.kt", 30, 10, 5),
        )

        val result = JsonFormatter.formatChurn(churn)

        assertEquals(
            """[{"file":"src/Foo.kt","added":100,"deleted":50,"commits":10},{"file":"src/Bar.kt","added":30,"deleted":10,"commits":5}]""",
            result,
        )
    }

    // === DSM formatting ===

    @Test
    fun `empty dsm produces JSON with empty packages and cells`() {
        val matrix = DsmMatrix(emptyList(), emptyMap(), emptyMap())

        val result = JsonFormatter.formatDsm(matrix)

        assertEquals("""{"packages":[],"cells":[],"cycles":[]}""", result)
    }

    @Test
    fun `dsm produces JSON with packages, cells, and class dependencies`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("model")),
            cells = mapOf((PackageName("api") to PackageName("model")) to 2),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("model")) to setOf(ClassName("Controller") to ClassName("User")),
            ),
        )

        val result = JsonFormatter.formatDsm(matrix)

        assertTrue(result.contains("\"packages\":[\"api\",\"model\"]"))
        assertTrue(result.contains("\"from\":\"api\""))
        assertTrue(result.contains("\"to\":\"model\""))
        assertTrue(result.contains("\"count\":2"))
        assertTrue(result.contains("\"Controller\""))
        assertTrue(result.contains("\"User\""))
    }

    @Test
    fun `dsm includes cyclic pairs in output`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("service")),
            cells = mapOf(
                (PackageName("api") to PackageName("service")) to 3,
                (PackageName("service") to PackageName("api")) to 1,
            ),
            classDependencies = emptyMap(),
        )

        val result = JsonFormatter.formatDsm(matrix)

        assertTrue(result.contains("\"cycles\""))
        assertTrue(result.contains("\"api\""))
        assertTrue(result.contains("\"service\""))
    }

    // === DSM cycles-only formatting ===

    @Test
    fun `dsm cycles-only with no cycles produces empty array`() {
        val matrix = DsmMatrix(emptyList(), emptyMap(), emptyMap())

        val result = JsonFormatter.formatDsmCycles(matrix)

        assertEquals("[]", result)
    }

    @Test
    fun `dsm cycles-only includes class edges in each direction`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("service")),
            cells = mapOf(
                (PackageName("api") to PackageName("service")) to 2,
                (PackageName("service") to PackageName("api")) to 1,
            ),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("service")) to setOf(ClassName("Controller") to ClassName("Service")),
                (PackageName("service") to PackageName("api")) to setOf(ClassName("Service") to ClassName("Controller")),
            ),
        )

        val result = JsonFormatter.formatDsmCycles(matrix)

        assertTrue(result.contains("\"packageA\":\"api\""))
        assertTrue(result.contains("\"packageB\":\"service\""))
        assertTrue(result.contains("\"forwardRefs\":2"))
        assertTrue(result.contains("\"backwardRefs\":1"))
        assertTrue(result.contains("\"forwardEdges\""))
        assertTrue(result.contains("\"backwardEdges\""))
        assertTrue(result.contains("\"Controller\""))
        assertTrue(result.contains("\"Service\""))
    }

    // === Rank formatting ===

    @Test
    fun `empty rank list produces empty JSON array`() {
        val result = JsonFormatter.formatRank(emptyList())

        assertEquals("[]", result)
    }

    @Test
    fun `formats ranked types as JSON array`() {
        val ranked = listOf(
            RankedType("com.example.Core", 0.42, inDegree = 5, outDegree = 2),
            RankedType("com.example.Service", 0.15, inDegree = 2, outDegree = 3),
        )

        val result = JsonFormatter.formatRank(ranked)

        assertTrue(result.contains("\"className\":\"com.example.Core\""))
        assertTrue(result.contains("\"rank\":0.42"))
        assertTrue(result.contains("\"inDegree\":5"))
        assertTrue(result.contains("\"outDegree\":2"))
        assertTrue(result.contains("\"className\":\"com.example.Service\""))
        assertTrue(result.contains("\"rank\":0.15"))
    }

    // === Dead code formatting ===

    @Test
    fun `empty dead code list produces empty JSON array`() {
        val result = JsonFormatter.formatDead(emptyList())

        assertEquals("[]", result)
    }

    @Test
    fun `formats dead code as JSON array with all fields`() {
        val dead = listOf(
            DeadCode("com.example.Orphan", null, DeadCodeKind.CLASS, "Orphan.kt"),
            DeadCode("com.example.Service", "unused", DeadCodeKind.METHOD, "Service.kt"),
        )

        val result = JsonFormatter.formatDead(dead)

        assertTrue(result.contains("\"className\":\"com.example.Orphan\""))
        assertTrue(result.contains("\"kind\":\"class\""))
        assertTrue(result.contains("\"sourceFile\":\"Orphan.kt\""))
        assertTrue(result.contains("\"className\":\"com.example.Service\""))
        assertTrue(result.contains("\"memberName\":\"unused\""))
        assertTrue(result.contains("\"kind\":\"method\""))
    }

    // === Complexity formatting ===

    @Test
    fun `empty complexity list produces empty JSON array`() {
        val result = JsonFormatter.formatComplexity(emptyList())

        assertEquals("[]", result)
    }

    @Test
    fun `formats complexity as JSON with all fields`() {
        val complexity = listOf(
            ClassComplexity(
                className = "com.example.Service",
                sourceFile = "Service.kt",
                fanOut = 5,
                fanIn = 3,
                distinctOutgoingClasses = 2,
                distinctIncomingClasses = 1,
                outgoingByClass = listOf("com.example.Repo" to 3, "com.example.Cache" to 2),
                incomingByClass = listOf("com.example.Controller" to 3),
            ),
        )

        val result = JsonFormatter.formatComplexity(complexity)

        assertTrue(result.contains("\"className\":\"com.example.Service\""))
        assertTrue(result.contains("\"sourceFile\":\"Service.kt\""))
        assertTrue(result.contains("\"fanOut\":5"))
        assertTrue(result.contains("\"fanIn\":3"))
        assertTrue(result.contains("\"distinctOutgoingClasses\":2"))
        assertTrue(result.contains("\"distinctIncomingClasses\":1"))
        assertTrue(result.contains("\"outgoingByClass\":["))
        assertTrue(result.contains("\"incomingByClass\":["))
    }

    // === Metrics formatting ===

    @Test
    fun `formats metrics as JSON object with all fields`() {
        val metrics = MetricsResult(
            totalClasses = 42,
            packageCount = 5,
            averageFanIn = 8.5,
            averageFanOut = 3.2,
            cycleCount = 2,
            deadClassCount = 3,
            deadMethodCount = 7,
            topHotspots = listOf(
                Hotspot("src/main/Foo.kt", 15, 200),
            ),
        )

        val result = JsonFormatter.formatMetrics(metrics)

        assertTrue(result.contains("\"totalClasses\":42"))
        assertTrue(result.contains("\"packageCount\":5"))
        assertTrue(result.contains("\"averageFanIn\":8.5"))
        assertTrue(result.contains("\"averageFanOut\":3.2"))
        assertTrue(result.contains("\"cycleCount\":2"))
        assertTrue(result.contains("\"deadClassCount\":3"))
        assertTrue(result.contains("\"deadMethodCount\":7"))
        assertTrue(result.contains("\"topHotspots\":["))
        assertTrue(result.contains("\"file\":\"src/main/Foo.kt\""))
    }

    @Test
    fun `formats metrics with empty hotspots`() {
        val metrics = MetricsResult(
            totalClasses = 10,
            packageCount = 2,
            averageFanIn = 0.0,
            averageFanOut = 0.0,
            cycleCount = 0,
            deadClassCount = 0,
            deadMethodCount = 0,
            topHotspots = emptyList(),
        )

        val result = JsonFormatter.formatMetrics(metrics)

        assertTrue(result.contains("\"topHotspots\":[]"))
    }

    // === Cycles formatting ===

    @Test
    fun `formatCycles returns empty array for no cycles`() {
        val result = JsonFormatter.formatCycles(emptyList())

        assertEquals("[]", result)
    }

    @Test
    fun `formatCycles includes cycle packages and edges`() {
        val details = listOf(
            CycleDetail(
                packages = listOf(PackageName("api"), PackageName("service")),
                edges = listOf(
                    CycleEdge(PackageName("api"), PackageName("service"), setOf(ClassName("api.Controller") to ClassName("service.Service"))),
                    CycleEdge(PackageName("service"), PackageName("api"), setOf(ClassName("service.Service") to ClassName("api.Controller"))),
                ),
            ),
        )

        val result = JsonFormatter.formatCycles(details)

        assertTrue(result.contains("\"packages\":[\"api\",\"service\"]"))
        assertTrue(result.contains("\"from\":\"api\""))
        assertTrue(result.contains("\"to\":\"service\""))
        assertTrue(result.contains("\"source\":\"api.Controller\""))
        assertTrue(result.contains("\"target\":\"service.Service\""))
    }

    @Test
    fun `formatCycles handles multiple cycles`() {
        val details = listOf(
            CycleDetail(
                packages = listOf(PackageName("a"), PackageName("b")),
                edges = listOf(
                    CycleEdge(PackageName("a"), PackageName("b"), setOf(ClassName("a.X") to ClassName("b.Y"))),
                    CycleEdge(PackageName("b"), PackageName("a"), setOf(ClassName("b.Y") to ClassName("a.X"))),
                ),
            ),
            CycleDetail(
                packages = listOf(PackageName("x"), PackageName("y"), PackageName("z")),
                edges = listOf(
                    CycleEdge(PackageName("x"), PackageName("y"), setOf(ClassName("x.A") to ClassName("y.B"))),
                ),
            ),
        )

        val result = JsonFormatter.formatCycles(details)

        assertTrue(result.startsWith("[{"))
        assertTrue(result.endsWith("}]"))
        assertTrue(result.contains("\"packages\":[\"a\",\"b\"]"))
        assertTrue(result.contains("\"packages\":[\"x\",\"y\",\"z\"]"))
    }
}
