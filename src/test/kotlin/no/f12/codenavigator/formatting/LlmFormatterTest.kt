package no.f12.codenavigator.formatting

import no.f12.codenavigator.analysis.ChangeCouplingFormatter
import no.f12.codenavigator.analysis.ChurnFormatter
import no.f12.codenavigator.analysis.CodeAgeFormatter
import no.f12.codenavigator.analysis.FileSizeEntry
import no.f12.codenavigator.analysis.Hotspot
import no.f12.codenavigator.analysis.HotspotFormatter
import no.f12.codenavigator.navigation.classinfo.AnnotationDetail
import no.f12.codenavigator.navigation.types.AnnotationName
import no.f12.codenavigator.navigation.relations.callgraph.CallDirection
import no.f12.codenavigator.navigation.relations.callgraph.AnnotationTag
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeNode
import no.f12.codenavigator.navigation.classinfo.ClassDetail
import no.f12.codenavigator.navigation.classinfo.ClassInfo
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.classinfo.FieldDetail
import no.f12.codenavigator.navigation.relations.implementors.ImplementorInfo
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistry
import no.f12.codenavigator.navigation.classinfo.MethodDetail
import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import no.f12.codenavigator.navigation.dsm.PackageDependencies
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.symbol.SymbolInfo
import no.f12.codenavigator.navigation.symbol.SymbolKind
import no.f12.codenavigator.navigation.dsm.DsmMatrix
import no.f12.codenavigator.navigation.rank.RankedType
import no.f12.codenavigator.navigation.rank.RankFormatter
import no.f12.codenavigator.navigation.complexity.ClassComplexity
import no.f12.codenavigator.navigation.complexity.ComplexityFormatter
import no.f12.codenavigator.navigation.dsm.CycleDetail
import no.f12.codenavigator.navigation.dsm.CycleEdge
import no.f12.codenavigator.navigation.dsm.TestInvolvement
import no.f12.codenavigator.navigation.annotation.AnnotationMatch
import no.f12.codenavigator.navigation.annotation.FieldAnnotationMatch
import no.f12.codenavigator.navigation.annotation.FieldRef
import no.f12.codenavigator.navigation.metrics.MetricsResult
import no.f12.codenavigator.navigation.types.SourceSet
import no.f12.codenavigator.navigation.context.ContextResult
import no.f12.codenavigator.navigation.fixtures.aContextResult
import no.f12.codenavigator.navigation.fixtures.aDeadCodePair
import no.f12.codenavigator.navigation.fixtures.aHotspotPair
import no.f12.codenavigator.navigation.fixtures.aCoupledPair
import no.f12.codenavigator.navigation.fixtures.aChurnPair
import no.f12.codenavigator.navigation.fixtures.aStringConstantPair
import no.f12.codenavigator.navigation.fixtures.anAnnotationMatch
import no.f12.codenavigator.navigation.fixtures.anAnnotationMatchWithMethods
import no.f12.codenavigator.navigation.fixtures.aRankedTypePair
import no.f12.codenavigator.navigation.fixtures.aClassComplexity
import no.f12.codenavigator.navigation.fixtures.aSingleCycle
import no.f12.codenavigator.navigation.fixtures.aMultiCycle
import no.f12.codenavigator.navigation.fixtures.aStrengthResultPair
import no.f12.codenavigator.navigation.dsm.PackageDistanceEntry
import no.f12.codenavigator.navigation.dsm.PackageDistanceFormatter
import no.f12.codenavigator.navigation.dsm.PackageDistanceResult
import no.f12.codenavigator.navigation.dsm.IntegrationStrength
import no.f12.codenavigator.navigation.dsm.PackageStrengthEntry
import no.f12.codenavigator.navigation.dsm.StrengthFormatter
import no.f12.codenavigator.navigation.dsm.StrengthResult
import no.f12.codenavigator.navigation.classmetrics.ClassCohesionVerdict
import no.f12.codenavigator.navigation.classmetrics.ClassMetricsFormatter
import no.f12.codenavigator.navigation.classmetrics.ClassMetricsResult
import no.f12.codenavigator.analysis.DuplicateGroup
import no.f12.codenavigator.analysis.DuplicateLocation
import no.f12.codenavigator.analysis.FileAge
import no.f12.codenavigator.navigation.relations.hierarchy.SupertypeInfo
import no.f12.codenavigator.navigation.relations.hierarchy.SupertypeKind
import no.f12.codenavigator.navigation.relations.hierarchy.TypeHierarchyResult
import no.f12.codenavigator.navigation.changedsince.ChangedClassImpact
import no.f12.codenavigator.navigation.dsm.BalanceEntry
import no.f12.codenavigator.navigation.dsm.BalanceFormatter
import no.f12.codenavigator.navigation.dsm.BalanceResult
import no.f12.codenavigator.navigation.dsm.BalanceVerdict
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import no.f12.codenavigator.navigation.annotation.AnnotationQueryFormatter
import no.f12.codenavigator.navigation.relations.callgraph.CallTreeFormatter
import no.f12.codenavigator.navigation.changedsince.ChangedSinceFormatter
import no.f12.codenavigator.navigation.classinfo.ClassDetailFormatter
import no.f12.codenavigator.navigation.classinfo.ClassInfoFormatter
import no.f12.codenavigator.navigation.context.ContextFormatter
import no.f12.codenavigator.navigation.dsm.CyclesFormatter
import no.f12.codenavigator.navigation.deadcode.DeadCodeFormatter
import no.f12.codenavigator.navigation.dsm.DsmFormatter
import no.f12.codenavigator.analysis.DuplicateFormatter
import no.f12.codenavigator.analysis.FileSizeFormatter
import no.f12.codenavigator.navigation.relations.implementors.InterfaceFormatter
import no.f12.codenavigator.navigation.metrics.MetricsFormatter
import no.f12.codenavigator.navigation.dsm.PackageDependencyFormatter
import no.f12.codenavigator.navigation.stringconstant.StringConstantFormatter
import no.f12.codenavigator.navigation.symbol.SymbolTableFormatter
import no.f12.codenavigator.navigation.relations.hierarchy.TypeHierarchyFormatter

class LlmFormatterTest {

    @Test
    fun `formats class list as one line per class`() {
        val classes = listOf(
            ClassInfo(ClassName("com.example.Foo"), "Foo.kt", "com/example/Foo.kt", true),
            ClassInfo(ClassName("com.example.Bar"), "Bar.kt", "com/example/Bar.kt", true),
        )

        val result = ClassInfoFormatter.formatLlm(classes)

        assertEquals("com.example.Bar Bar.kt\ncom.example.Foo Foo.kt", result)
    }

    @Test
    fun `empty class list returns empty string`() {
        assertEquals("", ClassInfoFormatter.formatLlm(emptyList()))
    }

    @Test
    fun `formats symbols compactly`() {
        val symbols = listOf(
            SymbolInfo(PackageName("com.example"), ClassName("com.example.Service"), "doWork", SymbolKind.METHOD, "Service.kt"),
            SymbolInfo(PackageName("com.example"), ClassName("com.example.Service"), "name", SymbolKind.FIELD, "Service.kt"),
        )

        val result = SymbolTableFormatter.formatLlm(symbols)

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
                fields = listOf(FieldDetail("repo", "UserRepository", emptyList())),
                methods = listOf(MethodDetail("findById", listOf("long"), "User", emptyList())),
                annotations = emptyList(),
            )
        )

        val result = ClassDetailFormatter.formatLlm(details)

        assertEquals(
            "com.example.UserService UserService.kt implements:UserOperations fields:repo:UserRepository methods:findById(long):User",
            result
        )
    }

    @Test
    fun `formats class details with annotations compactly`() {
        val details = listOf(
            ClassDetail(
                className = ClassName("com.example.UserService"),
                sourceFile = "UserService.kt",
                superClass = null,
                interfaces = emptyList(),
                fields = listOf(FieldDetail("repo", "UserRepository", listOf(AnnotationDetail(AnnotationName("Inject"), emptyMap())))),
                methods = listOf(MethodDetail("findById", listOf("long"), "User", listOf(
                    AnnotationDetail(AnnotationName("Cacheable"), mapOf("value" to "users")),
                ))),
                annotations = listOf(AnnotationDetail(AnnotationName("Service"), emptyMap())),
            )
        )

        val result = ClassDetailFormatter.formatLlm(details)

        assertEquals(
            "com.example.UserService UserService.kt annotations:@Service fields:@Inject+repo:UserRepository methods:@Cacheable(value=\"users\")+findById(long):User",
            result,
        )
    }

    @Test
    fun `formats call trees compactly`() {
        val trees = listOf(
            CallTreeNode(
                method = MethodRef(ClassName("com.example.Service"), "doWork"),
                sourceFile = "Service.kt",
                lineNumber = null,
                children = listOf(
                    CallTreeNode(
                        method = MethodRef(ClassName("com.example.Controller"), "handle"),
                        sourceFile = "Controller.kt",
                        lineNumber = null,
                        children = emptyList(),
                    )
                ),
            )
        )

        val result = CallTreeFormatter.formatLlm(trees, CallDirection.CALLERS)

        assertEquals("com.example.Service.doWork Service.kt\n  ← com.example.Controller.handle Controller.kt", result)
    }

    @Test
    fun `formats call trees with line numbers`() {
        val trees = listOf(
            CallTreeNode(
                method = MethodRef(ClassName("com.example.Service"), "doWork"),
                sourceFile = "Service.kt",
                lineNumber = 15,
                children = listOf(
                    CallTreeNode(
                        method = MethodRef(ClassName("com.example.Controller"), "handle"),
                        sourceFile = "Controller.kt",
                        lineNumber = 42,
                        children = emptyList(),
                    )
                ),
            )
        )

        val result = CallTreeFormatter.formatLlm(trees, CallDirection.CALLERS)

        assertEquals("com.example.Service.doWork Service.kt:15\n  ← com.example.Controller.handle Controller.kt:42", result)
    }

    @Test
    fun `formats collapsed implementor count as a suffix note`() {
        val trees = listOf(
            CallTreeNode(
                method = MethodRef(ClassName("com.example.Controller"), "handle"),
                sourceFile = "Controller.kt",
                lineNumber = null,
                children = listOf(
                    CallTreeNode(
                        method = MethodRef(ClassName("com.example.Repository"), "save"),
                        sourceFile = "Repository.kt",
                        lineNumber = null,
                        children = emptyList(),
                        collapsedImplementorCount = 5,
                    )
                ),
            )
        )

        val result = CallTreeFormatter.formatLlm(trees, CallDirection.CALLEES)

        assertEquals(
            "com.example.Controller.handle Controller.kt\n  → com.example.Repository.save Repository.kt (+5 more implementors, use --max-implementors to see all)",
            result,
        )
    }

    @Test
    fun `formats interfaces compactly`() {
        val registry = InterfaceRegistry(mapOf(
            ClassName("com.example.Repository") to listOf(
                ImplementorInfo(ClassName("com.example.SqlRepo"), "SqlRepo.kt"),
                ImplementorInfo(ClassName("com.example.MemRepo"), "MemRepo.kt"),
            )
        ))

        val result = InterfaceFormatter.formatLlm(registry, listOf(ClassName("com.example.Repository")))

        assertEquals("com.example.Repository: com.example.MemRepo(MemRepo.kt),com.example.SqlRepo(SqlRepo.kt)", result)
    }

    @Test
    fun `formats package deps compactly`() {
        val deps = PackageDependencies(mapOf(
            PackageName("com.example.api") to listOf(PackageName("com.example.service"), PackageName("com.example.model")),
        ))

        val result = PackageDependencyFormatter.formatLlm(deps, listOf(PackageName("com.example.api")), false)

        assertEquals("com.example.api -> com.example.service,com.example.model", result)
    }

    @Test
    fun `formats reverse package deps`() {
        val deps = PackageDependencies(mapOf(
            PackageName("com.example.api") to listOf(PackageName("com.example.model")),
            PackageName("com.example.service") to listOf(PackageName("com.example.model")),
        ))

        val result = PackageDependencyFormatter.formatLlm(deps, listOf(PackageName("com.example.model")), true)

        assertEquals("com.example.model <- com.example.api,com.example.service", result)
    }

    // === Hotspot formatting ===

    @Test
    fun `formats hotspots compactly`() {
        val hotspots = aHotspotPair()

        val result = HotspotFormatter.formatLlm(hotspots)

        assertEquals("src/Foo.kt revisions=10 churn=150\nsrc/Bar.kt revisions=5 churn=30\n\n${HotspotFormatter.HOTSPOT_INTERPRETATION}", result)
    }

    // === Coupling formatting ===

    @Test
    fun `formats coupling compactly`() {
        val pairs = aCoupledPair()

        val result = ChangeCouplingFormatter.formatLlm(pairs)

        assertEquals("src/Foo.kt -- src/Bar.kt degree=85% shared=10 avg=12\n\n${ChangeCouplingFormatter.COUPLING_INTERPRETATION}", result)
    }

    // === Churn formatting ===

    @Test
    fun `formats churn compactly`() {
        val churn = aChurnPair()

        val result = ChurnFormatter.formatLlm(churn)

        assertEquals("src/Foo.kt added=100 deleted=50 commits=10\nsrc/Bar.kt added=30 deleted=10 commits=5\n\n${ChurnFormatter.CHURN_INTERPRETATION}", result)
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

        val result = DsmFormatter.formatLlm(matrix)

        assertEquals("packages:api,model\napi->model:3 [Controller->User]", result)
    }

    @Test
    fun `formats empty dsm`() {
        val matrix = DsmMatrix(emptyList(), emptyMap(), emptyMap())

        val result = DsmFormatter.formatLlm(matrix)

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

        val result = DsmFormatter.formatLlm(matrix)

        assertEquals("packages:api,service\napi->service:2\nservice->api:1\nCYCLES: api<->service", result)
    }

    @Test
    fun `formats dsm with module labels when moduleLabels is provided`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("model")),
            cells = emptyMap(),
            classDependencies = emptyMap(),
        )
        val moduleLabels = mapOf(PackageName("api") to setOf(":service"), PackageName("model") to setOf(":shared"))

        val result = DsmFormatter.formatLlm(matrix, moduleLabels)

        assertEquals("packages:[:service] api,[:shared] model\n(no dependencies)", result)
    }

    // === DSM cycles-only formatting ===

    @Test
    fun `formatDsmCycles with no cycles produces no-cycles message`() {
        val matrix = DsmMatrix(emptyList(), emptyMap(), emptyMap())

        val result = DsmFormatter.formatCyclesLlm(matrix)

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

        val result = DsmFormatter.formatCyclesLlm(matrix)

        assertEquals(
            "CYCLE api<->service 2/1\n  api->service: Controller->Service\n  service->api: Service->Controller",
            result,
        )
    }

    // === Rank formatting ===

    @Test
    fun `empty rank list returns empty string`() {
        assertEquals("", RankFormatter.formatLlm(emptyList()))
    }

    @Test
    fun `formats ranked types compactly`() {
        val ranked = aRankedTypePair()

        val result = RankFormatter.formatLlm(ranked)

        assertEquals(
            "com.example.Core rank=0.4200 in=5 out=2\ncom.example.Service rank=0.1500 in=2 out=3\n\n${RankFormatter.RANK_INTERPRETATION}",
            result,
        )
    }

    // === Dead code formatting ===

    @Test
    fun `empty dead code list returns empty string`() {
        assertEquals("", DeadCodeFormatter.formatLlm(emptyList()))
    }

    @Test
    fun `formats dead code compactly`() {
        val dead = aDeadCodePair()

        val result = DeadCodeFormatter.formatLlm(dead)

        assertEquals(
            "com.example.Orphan CLASS Orphan.kt confidence=HIGH reason=NO_REFERENCES\n" +
                "com.example.Service.unused METHOD Service.kt confidence=MEDIUM reason=TEST_ONLY\n" +
                "\n" +
                "Note: Dead code detection is a hard problem with many edge cases (reflection, serialization, generated code). Use exclude=<regex> to filter out packages or classes you know are not dead.",
            result,
        )
    }

    // === String constant formatting ===

    @Test
    fun `empty string constant list returns empty string`() {
        assertEquals("", StringConstantFormatter.formatLlm(emptyList()))
    }

    @Test
    fun `formats string constants compactly`() {
        val matches = aStringConstantPair()

        val result = StringConstantFormatter.formatLlm(matches)

        assertEquals(
            "com.example.Routes.getUsers: \"/api/v1/users\" Routes.kt\ncom.example.Config.setup: \"application/json\" Config.kt",
            result,
        )
    }

    // === Complexity formatting ===

    @Test
    fun `empty complexity list returns empty string`() {
        assertEquals("", ComplexityFormatter.formatLlm(emptyList()))
    }

    @Test
    fun `formats complexity compactly`() {
        val results = aClassComplexity()

        val result = ComplexityFormatter.formatLlm(results)

        assertEquals(
            "com.example.Service out=5/2 in=3/1\n" +
                "  outgoing:\n" +
                "    com.example.Repo(3)\n" +
                "    com.example.Cache(2)\n" +
                "  incoming:\n" +
                "    com.example.Controller(3)\n\n${ComplexityFormatter.COMPLEXITY_INTERPRETATION}",
            result,
        )
    }

    @Test
    fun `formats complexity with no outgoing or incoming`() {
        val results = listOf(
            ClassComplexity(
                className = ClassName("com.example.Orphan"),
                sourceFile = "Orphan.kt",
                fanOut = 0,
                fanIn = 0,
                distinctOutgoingClasses = 0,
                distinctIncomingClasses = 0,
                outgoingByClass = emptyList(),
                incomingByClass = emptyList(),
            ),
        )

        val result = ComplexityFormatter.formatLlm(results)

        assertEquals(
            "com.example.Orphan out=0/0 in=0/0\n" +
                "  outgoing: none\n" +
                "  incoming: none\n\n${ComplexityFormatter.COMPLEXITY_INTERPRETATION}",
            result,
        )
    }

    @Test
    fun `formats multiple complexity results separated by blank line`() {
        val results = listOf(
            ClassComplexity(
                className = ClassName("com.example.A"),
                sourceFile = "A.kt",
                fanOut = 1,
                fanIn = 0,
                distinctOutgoingClasses = 1,
                distinctIncomingClasses = 0,
                outgoingByClass = listOf(ClassName("com.example.B") to 1),
                incomingByClass = emptyList(),
            ),
            ClassComplexity(
                className = ClassName("com.example.B"),
                sourceFile = "B.kt",
                fanOut = 0,
                fanIn = 1,
                distinctOutgoingClasses = 0,
                distinctIncomingClasses = 1,
                outgoingByClass = emptyList(),
                incomingByClass = listOf(ClassName("com.example.A") to 1),
            ),
        )

        val result = ComplexityFormatter.formatLlm(results)

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

        val result = MetricsFormatter.formatLlm(metrics)

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

        val result = MetricsFormatter.formatLlm(metrics)

        assertEquals(
            "classes=10 packages=2 avg-fan-in=0.0 avg-fan-out=0.0 cycles=0 dead-classes=0 dead-methods=0",
            result,
        )
    }

    // === Cycles formatting ===

    @Test
    fun `formatCycles returns no cycles message for empty list`() {
        val result = CyclesFormatter.formatLlm(emptyList())

        assertEquals("(no cycles)", result)
    }

    @Test
    fun `formatCycles formats cycle with class edges`() {
        val details = aSingleCycle()

        val result = CyclesFormatter.formatLlm(details)

        assertTrue(result.contains("CYCLE api,service"))
        assertTrue(result.contains("api->service(1): api.Controller->service.Service"))
        assertTrue(result.contains("service->api(1): service.Service->api.Controller"))
        assertTrue(result.contains("break:"), "Should include break suggestions, got:\n$result")
    }

    @Test
    fun `formatCycles separates multiple cycles with newlines`() {
        val details = aMultiCycle()

        val result = CyclesFormatter.formatLlm(details)

        assertTrue(result.contains("CYCLE a,b"))
        assertTrue(result.contains("CYCLE x,y,z"))
    }

    @Test
    fun `formatCycles includes prefix line when displayPrefix is non-empty`() {
        val details = aSingleCycle()

        val result = CyclesFormatter.formatLlm(details, displayPrefix = PackageName("com.example"))

        assertTrue(result.startsWith("prefix:com.example\n"), "Should start with prefix line, got:\n$result")
        assertTrue(result.contains("CYCLE"), "Should contain cycle info")
    }

    @Test
    fun `formatCycles appends test-involvement notice when provided`() {
        val details = aSingleCycle()

        val result = CyclesFormatter.formatLlm(details, testInvolvement = TestInvolvement.Counts(testInvolved = 1, total = 2))

        assertTrue(result.contains("test-involvement: 1 of 2 cycle edges involve test sources"), "Should render the notice, got:\n$result")
    }

    // === Annotation query formatting ===

    @Test
    fun `formats annotation matches compactly`() {
        val matches = listOf(anAnnotationMatch())

        val result = AnnotationQueryFormatter.formatLlm(matches)

        assertEquals("com.example.MyController MyController.kt @RestController", result)
    }

    @Test
    fun `formats annotation matches with methods`() {
        val matches = listOf(anAnnotationMatchWithMethods())

        val result = AnnotationQueryFormatter.formatLlm(matches)

        assertEquals("com.example.MyController MyController.kt @RestController\n  method getUsers @GetMapping", result)
    }

    @Test
    fun `formats annotation matches with fields`() {
        val matches = listOf(
            AnnotationMatch(
                className = ClassName("com.example.MyService"),
                sourceFile = "MyService.kt",
                classAnnotations = emptySet(),
                matchedMethods = emptyList(),
                matchedFields = listOf(
                    FieldAnnotationMatch(
                        field = FieldRef(ClassName("com.example.MyService"), "repo"),
                        annotations = setOf(AnnotationName("Inject")),
                    ),
                ),
            ),
        )

        val result = AnnotationQueryFormatter.formatLlm(matches)

        assertEquals("com.example.MyService MyService.kt\n  field repo @Inject", result)
    }

    @Test
    fun `formats empty annotation matches`() {
        assertEquals("(no matches)", AnnotationQueryFormatter.formatLlm(emptyList()))
    }

    // === Call tree annotation tags ===

    @Test
    fun `renders annotations on call tree child nodes`() {
        val trees = listOf(
            CallTreeNode(
                method = MethodRef(ClassName("com.example.Service"), "doWork"),
                sourceFile = "Service.kt",
                lineNumber = null,
                children = listOf(
                    CallTreeNode(
                        method = MethodRef(ClassName("com.example.Controller"), "getOwner"),
                        sourceFile = "Controller.kt",
                        lineNumber = 42,
                        children = emptyList(),
                        annotations = listOf(AnnotationTag(AnnotationName("GetMapping"), "spring")),
                    ),
                ),
            ),
        )

        val result = CallTreeFormatter.formatLlm(trees, CallDirection.CALLERS)

        assertEquals(
            "com.example.Service.doWork Service.kt\n  ← com.example.Controller.getOwner Controller.kt:42 [@GetMapping [spring]]",
            result,
        )
    }

    @Test
    fun `renders multiple annotations on call tree root node`() {
        val trees = listOf(
            CallTreeNode(
                method = MethodRef(ClassName("com.example.Controller"), "getOwner"),
                sourceFile = "Controller.kt",
                lineNumber = null,
                children = emptyList(),
                annotations = listOf(AnnotationTag(AnnotationName("GetMapping"), "spring"), AnnotationTag(AnnotationName("ResponseBody"), "spring")),
            ),
        )

        val result = CallTreeFormatter.formatLlm(trees, CallDirection.CALLERS)

        assertEquals(
            "com.example.Controller.getOwner Controller.kt [@GetMapping [spring], @ResponseBody [spring]]\n  (no callers) — @GetMapping is a spring entry point; invoked by the framework at runtime.",
            result,
        )
    }

    @Test
    fun `renders call tree node without annotations normally`() {
        val trees = listOf(
            CallTreeNode(
                method = MethodRef(ClassName("com.example.Service"), "doWork"),
                sourceFile = "Service.kt",
                lineNumber = null,
                children = emptyList(),
            ),
        )

        val result = CallTreeFormatter.formatLlm(trees, CallDirection.CALLERS)

        assertEquals("com.example.Service.doWork Service.kt\n  (no callers)", result)
    }

    @Test
    fun `renders mixed known and unknown annotations with framework tags`() {
        val trees = listOf(
            CallTreeNode(
                method = MethodRef(ClassName("com.example.Controller"), "doWork"),
                sourceFile = "Controller.kt",
                lineNumber = null,
                children = emptyList(),
                annotations = listOf(AnnotationTag(AnnotationName("GetMapping"), "spring"), AnnotationTag(AnnotationName("CustomAnnotation"))),
            ),
        )

        val result = CallTreeFormatter.formatLlm(trees, CallDirection.CALLERS)

        assertEquals(
            "com.example.Controller.doWork Controller.kt [@GetMapping [spring], @CustomAnnotation]\n  (no callers) — @GetMapping is a spring entry point; invoked by the framework at runtime.",
            result,
        )
    }

    @Test
    fun `renders annotation parameters in LLM call tree output`() {
        val trees = listOf(
            CallTreeNode(
                method = MethodRef(ClassName("com.example.Controller"), "getUsers"),
                sourceFile = "Controller.kt",
                lineNumber = null,
                children = emptyList(),
                annotations = listOf(
                    AnnotationTag(
                        AnnotationName("GetMapping"),
                        "spring",
                        mapOf("value" to "/users"),
                    ),
                ),
            ),
        )

        val result = CallTreeFormatter.formatLlm(trees, CallDirection.CALLERS)

        assertEquals(
            "com.example.Controller.getUsers Controller.kt [@GetMapping(value=\"/users\") [spring]]\n  (no callers) — @GetMapping is a spring entry point; invoked by the framework at runtime.",
            result,
        )
    }

    @Test
    fun `renders test source set tag on call tree child node`() {
        val trees = listOf(
            CallTreeNode(
                method = MethodRef(ClassName("com.example.Service"), "doWork"),
                sourceFile = "Service.kt",
                lineNumber = null,
                children = listOf(
                    CallTreeNode(
                        method = MethodRef(ClassName("com.example.ServiceTest"), "testDoWork"),
                        sourceFile = "ServiceTest.kt",
                        lineNumber = 10,
                        children = emptyList(),
                        sourceSet = SourceSet.TEST,
                    ),
                ),
                sourceSet = SourceSet.MAIN,
            ),
        )

        val result = CallTreeFormatter.formatLlm(trees, CallDirection.CALLERS)

        assertEquals(
            "com.example.Service.doWork Service.kt\n  ← com.example.ServiceTest.testDoWork ServiceTest.kt:10 [test]",
            result,
        )
    }

    @Test
    fun `renders prod source set tag on call tree child node`() {
        val trees = listOf(
            CallTreeNode(
                method = MethodRef(ClassName("com.example.Service"), "doWork"),
                sourceFile = "Service.kt",
                lineNumber = null,
                children = listOf(
                    CallTreeNode(
                        method = MethodRef(ClassName("com.example.Controller"), "handle"),
                        sourceFile = "Controller.kt",
                        lineNumber = null,
                        children = emptyList(),
                        sourceSet = SourceSet.MAIN,
                    ),
                ),
                sourceSet = SourceSet.MAIN,
            ),
        )

        val result = CallTreeFormatter.formatLlm(trees, CallDirection.CALLERS)

        assertEquals(
            "com.example.Service.doWork Service.kt\n  ← com.example.Controller.handle Controller.kt [prod]",
            result,
        )
    }

    @Test
    fun `renders no source set tag on call tree node when null`() {
        val trees = listOf(
            CallTreeNode(
                method = MethodRef(ClassName("com.example.Service"), "doWork"),
                sourceFile = "Service.kt",
                lineNumber = null,
                children = listOf(
                    CallTreeNode(
                        method = MethodRef(ClassName("com.example.Controller"), "handle"),
                        sourceFile = "Controller.kt",
                        lineNumber = null,
                        children = emptyList(),
                    ),
                ),
            ),
        )

        val result = CallTreeFormatter.formatLlm(trees, CallDirection.CALLERS)

        assertEquals(
            "com.example.Service.doWork Service.kt\n  ← com.example.Controller.handle Controller.kt",
            result,
        )
    }

    // === Context formatting ===

    @Test
    fun `formats context with class detail`() {
        val result = ContextFormatter.formatLlm(aContextResult())

        assertTrue(result.contains("com.example.MyService"), "Should contain class name")
        assertTrue(result.contains("MyService.kt"), "Should contain source file")
    }

    @Test
    fun `formats context with callers section`() {
        val callerRoot = CallTreeNode(
            method = MethodRef(ClassName("com.example.MyService"), "doWork"),
            sourceFile = "MyService.kt",
            lineNumber = 10,
            children = listOf(
                CallTreeNode(
                    method = MethodRef(ClassName("com.example.Caller"), "run"),
                    sourceFile = "Caller.kt",
                    lineNumber = 5,
                    children = emptyList(),
                ),
            ),
        )
        val result = ContextFormatter.formatLlm(aContextResult(callers = listOf(callerRoot)))

        assertTrue(result.contains("callers:"), "Should have callers section")
        assertTrue(result.contains("com.example.Caller.run"), "Should contain caller method")
    }

    @Test
    fun `formats context with callees section`() {
        val calleeRoot = CallTreeNode(
            method = MethodRef(ClassName("com.example.MyService"), "doWork"),
            sourceFile = "MyService.kt",
            lineNumber = 10,
            children = listOf(
                CallTreeNode(
                    method = MethodRef(ClassName("com.example.Repo"), "save"),
                    sourceFile = "Repo.kt",
                    lineNumber = 30,
                    children = emptyList(),
                ),
            ),
        )
        val result = ContextFormatter.formatLlm(aContextResult(callees = listOf(calleeRoot)))

        assertTrue(result.contains("callees:"), "Should have callees section")
        assertTrue(result.contains("com.example.Repo.save"), "Should contain callee method")
    }

    @Test
    fun `formats context with implementors`() {
        val result = ContextFormatter.formatLlm(aContextResult(
            implementors = listOf(
                ImplementorInfo(ClassName("com.example.ImplA"), "ImplA.kt"),
            ),
        ))

        assertTrue(result.contains("implementors:"), "Should have implementors section")
        assertTrue(result.contains("com.example.ImplA(ImplA.kt)"), "Should contain implementor")
    }

    @Test
    fun `formats context with implemented interfaces`() {
        val result = ContextFormatter.formatLlm(aContextResult(
            implementedInterfaces = listOf(ClassName("com.example.Service")),
        ))

        assertTrue(result.contains("implements:"), "Should have implements section")
        assertTrue(result.contains("com.example.Service"), "Should contain interface name")
    }

    @Test
    fun `omits empty sections from context`() {
        val result = ContextFormatter.formatLlm(aContextResult())

        assertTrue(!result.contains("callers:"), "Should not have callers when empty")
        assertTrue(!result.contains("callees:"), "Should not have callees when empty")
        assertTrue(!result.contains("implementors:"), "Should not have implementors when empty")
        assertTrue(!result.contains("implements:"), "Should not have implements when empty")
    }

    // === Distance formatting ===

    @Test
    fun `formatDistance with empty result produces empty string`() {
        val result = PackageDistanceResult(emptyList())

        assertEquals("", PackageDistanceFormatter.formatLlm(result))
    }

    @Test
    fun `formatDistance produces compact LLM lines`() {
        val result = PackageDistanceResult(
            listOf(
                PackageDistanceEntry(PackageName("com.example.api"), PackageName("org.other.service"), 6, 3),
                PackageDistanceEntry(PackageName("com.example.api"), PackageName("com.example.model"), 2, 5),
            ),
        )

        val output = PackageDistanceFormatter.formatLlm(result)

        assertEquals(
            "com.example.api->org.other.service distance=6 deps=3\ncom.example.api->com.example.model distance=2 deps=5\n\n${PackageDistanceFormatter.DISTANCE_INTERPRETATION}",
            output,
        )
    }

    @Test
    fun `formatDistance includes prefix line when displayPrefix is non-empty`() {
        val result = PackageDistanceResult(
            entries = listOf(
                PackageDistanceEntry(PackageName("api"), PackageName("model"), 2, 3),
            ),
            displayPrefix = PackageName("com.example"),
        )

        val output = PackageDistanceFormatter.formatLlm(result)

        assertTrue(output.startsWith("prefix:com.example\n"), "Should start with prefix line, got:\n$output")
        assertTrue(output.contains("api->model"), "Should contain entry data")
    }

    @Test
    fun `formatDistance omits prefix line when displayPrefix is empty`() {
        val result = PackageDistanceResult(
            entries = listOf(
                PackageDistanceEntry(PackageName("api"), PackageName("model"), 2, 3),
            ),
            displayPrefix = PackageName(""),
        )

        val output = PackageDistanceFormatter.formatLlm(result)

        assertTrue(!output.contains("prefix:"), "Should not contain prefix line when empty")
    }

    @Test
    fun `formatDsm includes prefix line when displayPrefix is non-empty`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("model")),
            cells = mapOf((PackageName("api") to PackageName("model")) to 3),
            classDependencies = emptyMap(),
            displayPrefix = PackageName("com.example"),
        )

        val output = DsmFormatter.formatLlm(matrix)

        assertTrue(output.startsWith("prefix:com.example\n"), "Should start with prefix line, got:\n$output")
        assertTrue(output.contains("packages:api,model"), "Should contain package list")
    }

    @Test
    fun `formatDsmCycles includes prefix line when displayPrefix is non-empty`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("service")),
            cells = mapOf(
                (PackageName("api") to PackageName("service")) to 1,
                (PackageName("service") to PackageName("api")) to 1,
            ),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("service")) to setOf(ClassName("Controller") to ClassName("Service")),
                (PackageName("service") to PackageName("api")) to setOf(ClassName("Service") to ClassName("Controller")),
            ),
            displayPrefix = PackageName("com.example"),
        )

        val output = DsmFormatter.formatCyclesLlm(matrix)

        assertTrue(output.startsWith("prefix:com.example\n"), "Should start with prefix line, got:\n$output")
        assertTrue(output.contains("CYCLE"), "Should contain cycle info")
    }

    // === Class name stripping tests ===

    @Test
    fun `formatDsm strips class names when displayPrefix is non-empty`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("model")),
            cells = mapOf((PackageName("api") to PackageName("model")) to 3),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("model")) to setOf(ClassName("com.example.api.Controller") to ClassName("com.example.model.User")),
            ),
            displayPrefix = PackageName("com.example"),
        )

        val result = DsmFormatter.formatLlm(matrix)

        assertTrue(result.contains("[api.Controller->model.User]"), "Should show stripped class names, got:\n$result")
        assertTrue(!result.contains("com.example.api.Controller"), "Should not show full class name, got:\n$result")
    }

    @Test
    fun `formatDsmCycles strips class names when displayPrefix is non-empty`() {
        val matrix = DsmMatrix(
            packages = listOf(PackageName("api"), PackageName("service")),
            cells = mapOf(
                (PackageName("api") to PackageName("service")) to 1,
                (PackageName("service") to PackageName("api")) to 1,
            ),
            classDependencies = mapOf(
                (PackageName("api") to PackageName("service")) to setOf(ClassName("com.example.api.Controller") to ClassName("com.example.service.Service")),
                (PackageName("service") to PackageName("api")) to setOf(ClassName("com.example.service.Service") to ClassName("com.example.api.Controller")),
            ),
            displayPrefix = PackageName("com.example"),
        )

        val result = DsmFormatter.formatCyclesLlm(matrix)

        assertTrue(result.contains("api.Controller->service.Service"), "Should show stripped class names, got:\n$result")
        assertTrue(!result.contains("com.example.api.Controller"), "Should not show full class name, got:\n$result")
    }

    @Test
    fun `formatCycles strips class names when displayPrefix is non-empty`() {
        val details = listOf(
            CycleDetail(
                packages = listOf(PackageName("api"), PackageName("service")),
                edges = listOf(
                    CycleEdge(PackageName("api"), PackageName("service"), setOf(ClassName("com.example.api.Controller") to ClassName("com.example.service.Service"))),
                    CycleEdge(PackageName("service"), PackageName("api"), setOf(ClassName("com.example.service.Service") to ClassName("com.example.api.Controller"))),
                ),
            ),
        )

        val result = CyclesFormatter.formatLlm(details, displayPrefix = PackageName("com.example"))

        assertTrue(result.contains("api.Controller->service.Service"), "Should show stripped class names, got:\n$result")
        assertTrue(!result.contains("com.example.api.Controller"), "Should not show full class name, got:\n$result")
    }

    // === Framework entry point hint tests (LLM) ===

    @Test
    fun `LLM call tree shows framework entry point hint for no-callers`() {
        val trees = listOf(
            CallTreeNode(
                method = MethodRef(ClassName("com.example.Controller"), "getUsers"),
                sourceFile = "Controller.kt",
                lineNumber = null,
                children = emptyList(),
                annotations = listOf(AnnotationTag(AnnotationName("GetMapping"), "spring")),
            ),
        )

        val result = CallTreeFormatter.formatLlm(trees, CallDirection.CALLERS)

        assertEquals(
            "com.example.Controller.getUsers Controller.kt [@GetMapping [spring]]\n  (no callers) — @GetMapping is a spring entry point; invoked by the framework at runtime.",
            result,
        )
    }

    @Test
    fun `LLM call tree shows no hint for non-framework annotations with no callers`() {
        val trees = listOf(
            CallTreeNode(
                method = MethodRef(ClassName("com.example.Service"), "doWork"),
                sourceFile = "Service.kt",
                lineNumber = null,
                children = emptyList(),
                annotations = listOf(AnnotationTag(AnnotationName("CustomAnnotation"))),
            ),
        )

        val result = CallTreeFormatter.formatLlm(trees, CallDirection.CALLERS)

        assertEquals(
            "com.example.Service.doWork Service.kt [@CustomAnnotation]\n  (no callers)",
            result,
        )
    }

    @Test
    fun `LLM call tree shows no hint for CALLEES direction`() {
        val trees = listOf(
            CallTreeNode(
                method = MethodRef(ClassName("com.example.Controller"), "getUsers"),
                sourceFile = "Controller.kt",
                lineNumber = null,
                children = emptyList(),
                annotations = listOf(AnnotationTag(AnnotationName("GetMapping"), "spring")),
            ),
        )

        val result = CallTreeFormatter.formatLlm(trees, CallDirection.CALLEES)

        assertEquals(
            "com.example.Controller.getUsers Controller.kt [@GetMapping [spring]]\n  (no callees)",
            result,
        )
    }

    @Test
    fun `LLM call tree shows no hint when method has callers`() {
        val trees = listOf(
            CallTreeNode(
                method = MethodRef(ClassName("com.example.Controller"), "getUsers"),
                sourceFile = "Controller.kt",
                lineNumber = null,
                children = listOf(
                    CallTreeNode(
                        method = MethodRef(ClassName("com.example.Test"), "testGetUsers"),
                        sourceFile = "Test.kt",
                        lineNumber = 10,
                        children = emptyList(),
                    ),
                ),
                annotations = listOf(AnnotationTag(AnnotationName("GetMapping"), "spring")),
            ),
        )

        val result = CallTreeFormatter.formatLlm(trees, CallDirection.CALLERS)

        assertEquals(
            "com.example.Controller.getUsers Controller.kt [@GetMapping [spring]]\n  ← com.example.Test.testGetUsers Test.kt:10",
            result,
        )
    }

    // === Strength formatting ===

    @Test
    fun `formatStrength with empty result produces empty string`() {
        val result = StrengthResult(emptyList())

        assertEquals("", StrengthFormatter.formatLlm(result))
    }

    @Test
    fun `formatStrength produces compact LLM lines`() {
        val result = aStrengthResultPair()

        val output = StrengthFormatter.formatLlm(result)

        assertEquals(
            "com.example.api->com.example.model strength=MODEL contract=1 model=2 functional=0\ncom.example.api->org.other.service strength=FUNCTIONAL contract=0 model=0 functional=4\n\n${StrengthFormatter.STRENGTH_INTERPRETATION}",
            output,
        )
    }

    @Test
    fun `formatStrength includes unknown count when greater than zero`() {
        val result = StrengthResult(
            listOf(
                PackageStrengthEntry(
                    PackageName("com.example.api"), PackageName("org.external.lib"),
                    IntegrationStrength.CONTRACT, contractCount = 1, modelCount = 0, functionalCount = 0, unknownCount = 3, totalDeps = 4,
                ),
            ),
        )

        val output = StrengthFormatter.formatLlm(result)

        assertEquals(
            "com.example.api->org.external.lib strength=CONTRACT contract=1 model=0 functional=0 unknown=3\n\n${StrengthFormatter.STRENGTH_INTERPRETATION}",
            output,
        )
    }

    // === Size formatting ===

    @Test
    fun `formats size entries compactly`() {
        val entries = listOf(
            FileSizeEntry("services/UserService.kt", 61),
            FileSizeEntry("domain/Domain.kt", 22),
        )

        val result = FileSizeFormatter.formatLlm(entries)

        assertEquals("services/UserService.kt lines=61\ndomain/Domain.kt lines=22", result)
    }

    // === Class metrics formatting ===

    @Test
    fun `formats class metrics with interpretation footer`() {
        val entry = ClassMetricsResult(
            className = ClassName("com.example.OrderService"),
            packageName = PackageName("com.example"),
            totalMethods = 5,
            tcc = 0.12,
            lcc = 0.3,
            verdict = ClassCohesionVerdict.MONOLITH,
            wmc = 34,
            cbo = 12,
            dit = 3,
        )

        val result = ClassMetricsFormatter.formatLlm(listOf(entry))

        assertEquals(
            "com.example.OrderService methods=5 tcc=0.12 lcc=0.30 verdict=MONOLITH wmc=34 cbo=12 dit=3\n\n${ClassMetricsFormatter.CLASS_METRICS_INTERPRETATION}",
            result,
        )
    }

    // === TypeHierarchy formatting ===

    @Test
    fun `formats type hierarchy with nested supertypes and implementors`() {
        val results = listOf(
            TypeHierarchyResult(
                className = ClassName("com.example.Child"),
                sourceFile = "Child.kt",
                supertypes = listOf(
                    SupertypeInfo(
                        ClassName("com.example.Base"),
                        SupertypeKind.CLASS,
                        listOf(SupertypeInfo(ClassName("com.example.Marker"), SupertypeKind.INTERFACE, emptyList())),
                    ),
                ),
                implementors = listOf(ImplementorInfo(ClassName("com.example.GrandChild"), "GrandChild.kt")),
            ),
        )

        val result = TypeHierarchyFormatter.formatLlm(results)

        assertEquals(
            "com.example.Child Child.kt\n" +
                "  extends com.example.Base\n" +
                "    implements com.example.Marker\n" +
                "  implementors: com.example.GrandChild(GrandChild.kt)",
            result,
        )
    }

    @Test
    fun `formats type hierarchy with no supertypes or implementors`() {
        val results = listOf(
            TypeHierarchyResult(className = ClassName("com.example.Plain"), sourceFile = "Plain.kt", supertypes = emptyList(), implementors = emptyList()),
        )

        val result = TypeHierarchyFormatter.formatLlm(results)

        assertEquals("com.example.Plain Plain.kt", result)
    }

    // === Duplicates formatting ===

    @Test
    fun `formats duplicate groups with locations`() {
        val groups = listOf(
            DuplicateGroup(tokenCount = 25, locations = listOf(DuplicateLocation("A.kt", 10, 15), DuplicateLocation("B.kt", 20, 25))),
        )

        val result = DuplicateFormatter.formatLlm(groups)

        assertEquals("tokens=25\n  A.kt:10-15\n  B.kt:20-25", result)
    }

    // === Age formatting ===

    @Test
    fun `formats file ages with interpretation footer`() {
        val ages = listOf(FileAge("src/Old.kt", 12, LocalDate.of(2023, 1, 1)))

        val result = CodeAgeFormatter.formatLlm(ages)

        assertEquals("src/Old.kt age=12months last=2023-01-01\n\n${CodeAgeFormatter.AGE_INTERPRETATION}", result)
    }

    // === ChangedSince formatting ===

    @Test
    fun `formats changed class impacts with callers and unresolved files`() {
        val impacts = listOf(
            ChangedClassImpact(
                className = ClassName("com.example.Service"),
                sourceFile = "Service.kt",
                callers = setOf(MethodRef(ClassName("com.example.Controller"), "handle")),
            ),
        )

        val result = ChangedSinceFormatter.formatLlm(impacts, unresolved = listOf("missing.kt"))

        assertEquals(
            "com.example.Service Service.kt\n  <- com.example.Controller.handle\nUNRESOLVED: missing.kt",
            result,
        )
    }

    @Test
    fun `formats changed class impact with no callers`() {
        val impacts = listOf(
            ChangedClassImpact(className = ClassName("com.example.Orphan"), sourceFile = "Orphan.kt", callers = emptySet()),
        )

        val result = ChangedSinceFormatter.formatLlm(impacts, unresolved = emptyList())

        assertEquals("com.example.Orphan Orphan.kt (no callers)", result)
    }

    // === Balance formatting ===

    @Test
    fun `formats balance entries with suggestion and interpretation footer`() {
        val result = BalanceResult(
            listOf(
                BalanceEntry(
                    source = PackageName("com.example.web"),
                    target = PackageName("com.example.persistence"),
                    strength = IntegrationStrength.FUNCTIONAL,
                    distance = 4,
                    sourceVolatility = 50,
                    targetVolatility = 40,
                    verdict = BalanceVerdict.DANGER,
                    suggestion = "Consider co-locating.",
                ),
            ),
        )

        val llm = BalanceFormatter.formatLlm(result)

        assertEquals(
            "com.example.web->com.example.persistence verdict=DANGER strength=FUNCTIONAL distance=4 volatility=50/40 | Consider co-locating.\n\n${BalanceFormatter.BALANCE_INTERPRETATION}",
            llm,
        )
    }
}
