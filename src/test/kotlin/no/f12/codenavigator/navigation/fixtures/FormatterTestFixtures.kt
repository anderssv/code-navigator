package no.f12.codenavigator.navigation.fixtures

import no.f12.codenavigator.analysis.CoupledPair
import no.f12.codenavigator.analysis.FileChurn
import no.f12.codenavigator.analysis.Hotspot
import no.f12.codenavigator.navigation.annotation.AnnotationMatch
import no.f12.codenavigator.navigation.annotation.MethodAnnotationMatch
import no.f12.codenavigator.navigation.callgraph.CallTreeNode
import no.f12.codenavigator.navigation.callgraph.MethodRef
import no.f12.codenavigator.navigation.classinfo.ClassDetail
import no.f12.codenavigator.navigation.classinfo.MethodDetail
import no.f12.codenavigator.navigation.complexity.ClassComplexity
import no.f12.codenavigator.navigation.context.ContextResult
import no.f12.codenavigator.navigation.core.AnnotationName
import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.PackageName
import no.f12.codenavigator.navigation.deadcode.DeadCode
import no.f12.codenavigator.navigation.deadcode.DeadCodeConfidence
import no.f12.codenavigator.navigation.deadcode.DeadCodeKind
import no.f12.codenavigator.navigation.deadcode.DeadCodeReason
import no.f12.codenavigator.navigation.dsm.CycleDetail
import no.f12.codenavigator.navigation.dsm.CycleEdge
import no.f12.codenavigator.navigation.dsm.IntegrationStrength
import no.f12.codenavigator.navigation.dsm.PackageStrengthEntry
import no.f12.codenavigator.navigation.dsm.StrengthResult
import no.f12.codenavigator.navigation.interfaces.ImplementorInfo
import no.f12.codenavigator.navigation.metrics.MetricsResult
import no.f12.codenavigator.navigation.rank.RankedType
import no.f12.codenavigator.navigation.stringconstant.StringConstantMatch

fun aContextResult(
    callers: List<CallTreeNode> = emptyList(),
    callees: List<CallTreeNode> = emptyList(),
    implementors: List<ImplementorInfo> = emptyList(),
    implementedInterfaces: List<ClassName> = emptyList(),
): ContextResult = ContextResult(
    classDetail = ClassDetail(
        className = ClassName("com.example.MyService"),
        sourceFile = "MyService.kt",
        superClass = null,
        interfaces = emptyList(),
        fields = emptyList(),
        methods = listOf(MethodDetail("doWork", listOf("String"), "void", emptyList())),
        annotations = emptyList(),
    ),
    callers = callers,
    callees = callees,
    implementors = implementors,
    implementedInterfaces = implementedInterfaces,
)

fun aDeadCodePair(): List<DeadCode> = listOf(
    DeadCode(ClassName("com.example.Orphan"), null, DeadCodeKind.CLASS, "Orphan.kt", DeadCodeConfidence.HIGH, DeadCodeReason.NO_REFERENCES),
    DeadCode(ClassName("com.example.Service"), "unused", DeadCodeKind.METHOD, "Service.kt", DeadCodeConfidence.MEDIUM, DeadCodeReason.TEST_ONLY),
)

fun aHotspotPair(): List<Hotspot> = listOf(
    Hotspot("src/Foo.kt", 10, 150),
    Hotspot("src/Bar.kt", 5, 30),
)

fun aCoupledPair(): List<CoupledPair> = listOf(
    CoupledPair("src/Foo.kt", "src/Bar.kt", 85, 10, 12),
)

fun aChurnPair(): List<FileChurn> = listOf(
    FileChurn("src/Foo.kt", 100, 50, 10),
    FileChurn("src/Bar.kt", 30, 10, 5),
)

fun aStringConstantPair(): List<StringConstantMatch> = listOf(
    StringConstantMatch(ClassName("com.example.Routes"), "getUsers", "/api/v1/users", "Routes.kt"),
    StringConstantMatch(ClassName("com.example.Config"), "setup", "application/json", "Config.kt"),
)

fun anAnnotationMatch(): AnnotationMatch = AnnotationMatch(
    className = ClassName("com.example.MyController"),
    sourceFile = "MyController.kt",
    classAnnotations = setOf(AnnotationName("RestController")),
    matchedMethods = emptyList(),
)

fun anAnnotationMatchWithMethods(): AnnotationMatch = AnnotationMatch(
    className = ClassName("com.example.MyController"),
    sourceFile = "MyController.kt",
    classAnnotations = setOf(AnnotationName("RestController")),
    matchedMethods = listOf(
        MethodAnnotationMatch(
            method = MethodRef(ClassName("com.example.MyController"), "getUsers"),
            annotations = setOf(AnnotationName("GetMapping")),
        ),
    ),
)

fun aRankedTypePair(): List<RankedType> = listOf(
    RankedType(ClassName("com.example.Core"), 0.42, inDegree = 5, outDegree = 2),
    RankedType(ClassName("com.example.Service"), 0.15, inDegree = 2, outDegree = 3),
)

fun aClassComplexity(): List<ClassComplexity> = listOf(
    ClassComplexity(
        className = ClassName("com.example.Service"),
        sourceFile = "Service.kt",
        fanOut = 5,
        fanIn = 3,
        distinctOutgoingClasses = 2,
        distinctIncomingClasses = 1,
        outgoingByClass = listOf(ClassName("com.example.Repo") to 3, ClassName("com.example.Cache") to 2),
        incomingByClass = listOf(ClassName("com.example.Controller") to 3),
    ),
)

fun aSingleCycle(): List<CycleDetail> = listOf(
    CycleDetail(
        packages = listOf(PackageName("api"), PackageName("service")),
        edges = listOf(
            CycleEdge(PackageName("api"), PackageName("service"), setOf(ClassName("api.Controller") to ClassName("service.Service"))),
            CycleEdge(PackageName("service"), PackageName("api"), setOf(ClassName("service.Service") to ClassName("api.Controller"))),
        ),
    ),
)

fun aMultiCycle(): List<CycleDetail> = listOf(
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

fun aMetricsResult(): MetricsResult = MetricsResult(
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

fun aStrengthResultPair(): StrengthResult = StrengthResult(
    listOf(
        PackageStrengthEntry(
            PackageName("com.example.api"), PackageName("com.example.model"),
            IntegrationStrength.MODEL, contractCount = 1, modelCount = 2, functionalCount = 0, unknownCount = 0, totalDeps = 3,
        ),
        PackageStrengthEntry(
            PackageName("com.example.api"), PackageName("org.other.service"),
            IntegrationStrength.FUNCTIONAL, contractCount = 0, modelCount = 0, functionalCount = 4, unknownCount = 0, totalDeps = 4,
        ),
    ),
)
