package no.f12.codenavigator

import no.f12.codenavigator.analysis.CoupledPair
import no.f12.codenavigator.analysis.FileChurn
import no.f12.codenavigator.analysis.Hotspot
import no.f12.codenavigator.navigation.CallDirection
import no.f12.codenavigator.navigation.CallTreeNode
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

class LlmFormatterTest {

    @Test
    fun `formats class list as one line per class`() {
        val classes = listOf(
            ClassInfo(ClassName("com.example.Foo"), "Foo.kt", "com/example/Foo.kt", true),
            ClassInfo(ClassName("com.example.Bar"), "Bar.kt", "com/example/Bar.kt", true),
        )

        val result = LlmFormatter.formatClasses(classes)

        assertEquals("com.example.Bar Bar.kt\ncom.example.Foo Foo.kt", result)
    }

    @Test
    fun `empty class list returns empty string`() {
        assertEquals("", LlmFormatter.formatClasses(emptyList()))
    }

    @Test
    fun `formats symbols compactly`() {
        val symbols = listOf(
            SymbolInfo("com.example", "Service", "doWork", SymbolKind.METHOD, "Service.kt"),
            SymbolInfo("com.example", "Service", "name", SymbolKind.FIELD, "Service.kt"),
        )

        val result = LlmFormatter.formatSymbols(symbols)

        assertEquals("com.example.Service.doWork method Service.kt\ncom.example.Service.name field Service.kt", result)
    }

    @Test
    fun `formats class details compactly`() {
        val details = listOf(
            ClassDetail(
                className = ClassName("com.example.UserService"),
                sourceFile = "UserService.kt",
                superClass = null,
                interfaces = listOf(ClassName("UserOperations")),
                fields = listOf(FieldDetail("repo", "UserRepository")),
                methods = listOf(MethodDetail("findById", listOf("long"), "User")),
            )
        )

        val result = LlmFormatter.formatClassDetails(details)

        assertEquals(
            "com.example.UserService UserService.kt implements:UserOperations fields:repo:UserRepository methods:findById(long):User",
            result
        )
    }

    @Test
    fun `formats call trees compactly`() {
        val trees = listOf(
            CallTreeNode(
                method = MethodRef(ClassName("com.example.Service"), "doWork"),
                sourceFile = "Service.kt",
                children = listOf(
                    CallTreeNode(
                        method = MethodRef(ClassName("com.example.Controller"), "handle"),
                        sourceFile = "Controller.kt",
                        children = emptyList(),
                    )
                ),
            )
        )

        val result = LlmFormatter.renderCallTrees(trees, CallDirection.CALLERS)

        assertEquals("com.example.Service.doWork Service.kt\n  ← com.example.Controller.handle Controller.kt", result)
    }

    @Test
    fun `formats interfaces compactly`() {
        val registry = InterfaceRegistry(mapOf(
            ClassName("com.example.Repository") to listOf(
                ImplementorInfo(ClassName("com.example.SqlRepo"), "SqlRepo.kt"),
                ImplementorInfo(ClassName("com.example.MemRepo"), "MemRepo.kt"),
            )
        ))

        val result = LlmFormatter.formatInterfaces(registry, listOf(ClassName("com.example.Repository")))

        assertEquals("com.example.Repository: com.example.MemRepo(MemRepo.kt),com.example.SqlRepo(SqlRepo.kt)", result)
    }

    @Test
    fun `formats package deps compactly`() {
        val deps = PackageDependencies(mapOf(
            PackageName("com.example.api") to listOf(PackageName("com.example.service"), PackageName("com.example.model")),
        ))

        val result = LlmFormatter.formatPackageDeps(deps, listOf(PackageName("com.example.api")), false)

        assertEquals("com.example.api -> com.example.service,com.example.model", result)
    }

    @Test
    fun `formats reverse package deps`() {
        val deps = PackageDependencies(mapOf(
            PackageName("com.example.api") to listOf(PackageName("com.example.model")),
            PackageName("com.example.service") to listOf(PackageName("com.example.model")),
        ))

        val result = LlmFormatter.formatPackageDeps(deps, listOf(PackageName("com.example.model")), true)

        assertEquals("com.example.model <- com.example.api,com.example.service", result)
    }

    // === Hotspot formatting ===

    @Test
    fun `formats hotspots compactly`() {
        val hotspots = listOf(
            Hotspot("src/Foo.kt", 10, 150),
            Hotspot("src/Bar.kt", 5, 30),
        )

        val result = LlmFormatter.formatHotspots(hotspots)

        assertEquals("src/Foo.kt revisions=10 churn=150\nsrc/Bar.kt revisions=5 churn=30", result)
    }

    // === Coupling formatting ===

    @Test
    fun `formats coupling compactly`() {
        val pairs = listOf(
            CoupledPair("src/Foo.kt", "src/Bar.kt", 85, 10, 12),
        )

        val result = LlmFormatter.formatCoupling(pairs)

        assertEquals("src/Foo.kt -- src/Bar.kt degree=85% shared=10 avg=12", result)
    }

    // === Churn formatting ===

    @Test
    fun `formats churn compactly`() {
        val churn = listOf(
            FileChurn("src/Foo.kt", 100, 50, 10),
            FileChurn("src/Bar.kt", 30, 10, 5),
        )

        val result = LlmFormatter.formatChurn(churn)

        assertEquals("src/Foo.kt added=100 deleted=50 commits=10\nsrc/Bar.kt added=30 deleted=10 commits=5", result)
    }

    // === DSM formatting ===

    @Test
    fun `formats dsm compactly`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("model")),
            cells = mapOf((PackageName("api") to PackageName("model")) to 3),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("model")) to setOf(ClassName("Controller") to ClassName("User")),
            ),
        )

        val result = LlmFormatter.formatDsm(matrix)

        assertEquals("packages:api,model\napi->model:3 [Controller->User]", result)
    }

    @Test
    fun `formats empty dsm`() {
        val matrix = DsmMatrix(emptyList(), emptyMap(), emptyMap())

        val result = LlmFormatter.formatDsm(matrix)

        assertEquals("packages:\n(no dependencies)", result)
    }

    @Test
    fun `formats dsm with cycles`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("service")),
            cells = mapOf(
                (PackageName("api") to PackageName("service")) to 2,
                (PackageName("service") to PackageName("api")) to 1,
            ),
            classDependencies = emptyMap(),
        )

        val result = LlmFormatter.formatDsm(matrix)

        assertEquals("packages:api,service\napi->service:2\nservice->api:1\nCYCLES: api<->service", result)
    }

    // === DSM cycles-only formatting ===

    @Test
    fun `formatDsmCycles with no cycles produces no-cycles message`() {
        val matrix = DsmMatrix(emptyList(), emptyMap(), emptyMap())

        val result = LlmFormatter.formatDsmCycles(matrix)

        assertEquals("(no cycles)", result)
    }

    @Test
    fun `formatDsmCycles shows compact cycle with class edges`() {
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

        val result = LlmFormatter.formatDsmCycles(matrix)

        assertEquals(
            "CYCLE api<->service 2/1\n  api->service: Controller->Service\n  service->api: Service->Controller",
            result,
        )
    }

    // === Rank formatting ===

    @Test
    fun `empty rank list returns empty string`() {
        assertEquals("", LlmFormatter.formatRank(emptyList()))
    }

    @Test
    fun `formats ranked types compactly`() {
        val ranked = listOf(
            RankedType("com.example.Core", 0.42, inDegree = 5, outDegree = 2),
            RankedType("com.example.Service", 0.15, inDegree = 2, outDegree = 3),
        )

        val result = LlmFormatter.formatRank(ranked)

        assertEquals(
            "com.example.Core rank=0.4200 in=5 out=2\ncom.example.Service rank=0.1500 in=2 out=3",
            result,
        )
    }

    // === Dead code formatting ===

    @Test
    fun `empty dead code list returns empty string`() {
        assertEquals("", LlmFormatter.formatDead(emptyList()))
    }

    @Test
    fun `formats dead code compactly`() {
        val dead = listOf(
            DeadCode(ClassName("com.example.Orphan"), null, DeadCodeKind.CLASS, "Orphan.kt"),
            DeadCode(ClassName("com.example.Service"), "unused", DeadCodeKind.METHOD, "Service.kt"),
        )

        val result = LlmFormatter.formatDead(dead)

        assertEquals(
            "com.example.Orphan CLASS Orphan.kt\ncom.example.Service.unused METHOD Service.kt",
            result,
        )
    }

    // === Complexity formatting ===

    @Test
    fun `empty complexity list returns empty string`() {
        assertEquals("", LlmFormatter.formatComplexity(emptyList()))
    }

    @Test
    fun `formats complexity compactly`() {
        val results = listOf(
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

        val result = LlmFormatter.formatComplexity(results)

        assertEquals(
            "com.example.Service out=5/2 in=3/1\n" +
                "  outgoing:\n" +
                "    com.example.Repo(3)\n" +
                "    com.example.Cache(2)\n" +
                "  incoming:\n" +
                "    com.example.Controller(3)",
            result,
        )
    }

    @Test
    fun `formats complexity with no outgoing or incoming`() {
        val results = listOf(
            ClassComplexity(
                className = "com.example.Orphan",
                sourceFile = "Orphan.kt",
                fanOut = 0,
                fanIn = 0,
                distinctOutgoingClasses = 0,
                distinctIncomingClasses = 0,
                outgoingByClass = emptyList(),
                incomingByClass = emptyList(),
            ),
        )

        val result = LlmFormatter.formatComplexity(results)

        assertEquals(
            "com.example.Orphan out=0/0 in=0/0\n" +
                "  outgoing: none\n" +
                "  incoming: none",
            result,
        )
    }

    @Test
    fun `formats multiple complexity results separated by blank line`() {
        val results = listOf(
            ClassComplexity(
                className = "com.example.A",
                sourceFile = "A.kt",
                fanOut = 1,
                fanIn = 0,
                distinctOutgoingClasses = 1,
                distinctIncomingClasses = 0,
                outgoingByClass = listOf("com.example.B" to 1),
                incomingByClass = emptyList(),
            ),
            ClassComplexity(
                className = "com.example.B",
                sourceFile = "B.kt",
                fanOut = 0,
                fanIn = 1,
                distinctOutgoingClasses = 0,
                distinctIncomingClasses = 1,
                outgoingByClass = emptyList(),
                incomingByClass = listOf("com.example.A" to 1),
            ),
        )

        val result = LlmFormatter.formatComplexity(results)

        assertTrue(result.contains("com.example.A out=1/1 in=0/0"))
        assertTrue(result.contains("com.example.B out=0/0 in=1/1"))
        assertTrue(result.contains("\n\n"), "Classes should be separated by blank line")
    }

    // === Metrics formatting ===

    @Test
    fun `formats metrics compactly`() {
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
                Hotspot("src/main/Bar.kt", 10, 100),
            ),
        )

        val result = LlmFormatter.formatMetrics(metrics)

        assertEquals(
            "classes=42 packages=5 avg-fan-in=8.5 avg-fan-out=3.2 cycles=2 dead-classes=3 dead-methods=7\n" +
                "hotspots:\n" +
                "src/main/Foo.kt revisions=15 churn=200\n" +
                "src/main/Bar.kt revisions=10 churn=100",
            result,
        )
    }

    @Test
    fun `formats metrics without hotspots`() {
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

        val result = LlmFormatter.formatMetrics(metrics)

        assertEquals(
            "classes=10 packages=2 avg-fan-in=0.0 avg-fan-out=0.0 cycles=0 dead-classes=0 dead-methods=0",
            result,
        )
    }

    // === Cycles formatting ===

    @Test
    fun `formatCycles returns no cycles message for empty list`() {
        val result = LlmFormatter.formatCycles(emptyList())

        assertEquals("(no cycles)", result)
    }

    @Test
    fun `formatCycles formats cycle with class edges`() {
        val details = listOf(
            CycleDetail(
                packages = listOf(PackageName("api"), PackageName("service")),
                edges = listOf(
                    CycleEdge(PackageName("api"), PackageName("service"), setOf(ClassName("api.Controller") to ClassName("service.Service"))),
                    CycleEdge(PackageName("service"), PackageName("api"), setOf(ClassName("service.Service") to ClassName("api.Controller"))),
                ),
            ),
        )

        val result = LlmFormatter.formatCycles(details)

        assertTrue(result.contains("CYCLE api,service"))
        assertTrue(result.contains("api->service: api.Controller->service.Service"))
        assertTrue(result.contains("service->api: service.Service->api.Controller"))
    }

    @Test
    fun `formatCycles separates multiple cycles with newlines`() {
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

        val result = LlmFormatter.formatCycles(details)

        assertTrue(result.contains("CYCLE a,b"))
        assertTrue(result.contains("CYCLE x,y,z"))
    }
}
