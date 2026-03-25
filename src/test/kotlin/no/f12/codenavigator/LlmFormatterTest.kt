package no.f12.codenavigator

import no.f12.codenavigator.analysis.CoupledPair
import no.f12.codenavigator.analysis.FileChurn
import no.f12.codenavigator.analysis.Hotspot
import no.f12.codenavigator.navigation.CallDirection
import no.f12.codenavigator.navigation.CallTreeNode
import no.f12.codenavigator.navigation.ClassDetail
import no.f12.codenavigator.navigation.ClassInfo
import no.f12.codenavigator.navigation.FieldDetail
import no.f12.codenavigator.navigation.ImplementorInfo
import no.f12.codenavigator.navigation.InterfaceRegistry
import no.f12.codenavigator.navigation.MethodDetail
import no.f12.codenavigator.navigation.MethodRef
import no.f12.codenavigator.navigation.PackageDependencies
import no.f12.codenavigator.navigation.SymbolInfo
import no.f12.codenavigator.navigation.SymbolKind
import no.f12.codenavigator.navigation.DsmMatrix
import no.f12.codenavigator.navigation.RankedType
import kotlin.test.Test
import kotlin.test.assertEquals

class LlmFormatterTest {

    @Test
    fun `formats class list as one line per class`() {
        val classes = listOf(
            ClassInfo("com.example.Foo", "Foo.kt", "com/example/Foo.kt", true),
            ClassInfo("com.example.Bar", "Bar.kt", "com/example/Bar.kt", true),
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
                className = "com.example.UserService",
                sourceFile = "UserService.kt",
                superClass = null,
                interfaces = listOf("UserOperations"),
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
                method = MethodRef("com.example.Service", "doWork"),
                sourceFile = "Service.kt",
                children = listOf(
                    CallTreeNode(
                        method = MethodRef("com.example.Controller", "handle"),
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
            "com.example.Repository" to listOf(
                ImplementorInfo("com.example.SqlRepo", "SqlRepo.kt"),
                ImplementorInfo("com.example.MemRepo", "MemRepo.kt"),
            )
        ))

        val result = LlmFormatter.formatInterfaces(registry, listOf("com.example.Repository"))

        assertEquals("com.example.Repository: com.example.MemRepo(MemRepo.kt),com.example.SqlRepo(SqlRepo.kt)", result)
    }

    @Test
    fun `formats package deps compactly`() {
        val deps = PackageDependencies(mapOf(
            "com.example.api" to listOf("com.example.service", "com.example.model"),
        ))

        val result = LlmFormatter.formatPackageDeps(deps, listOf("com.example.api"), false)

        assertEquals("com.example.api -> com.example.service,com.example.model", result)
    }

    @Test
    fun `formats reverse package deps`() {
        val deps = PackageDependencies(mapOf(
            "com.example.api" to listOf("com.example.model"),
            "com.example.service" to listOf("com.example.model"),
        ))

        val result = LlmFormatter.formatPackageDeps(deps, listOf("com.example.model"), true)

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
            packages = listOf("api", "model"),
            cells = mapOf("api" to "model" to 3),
            classDependencies = mapOf(
                ("api" to "model") to setOf("Controller" to "User"),
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
            packages = listOf("api", "service"),
            cells = mapOf(
                "api" to "service" to 2,
                "service" to "api" to 1,
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
            packages = listOf("api", "service"),
            cells = mapOf(
                "api" to "service" to 2,
                "service" to "api" to 1,
            ),
            classDependencies = mapOf(
                ("api" to "service") to setOf("Controller" to "Service"),
                ("service" to "api") to setOf("Service" to "Controller"),
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
}
