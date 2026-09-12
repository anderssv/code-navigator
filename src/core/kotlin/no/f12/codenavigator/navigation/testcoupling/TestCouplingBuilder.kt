package no.f12.codenavigator.navigation.testcoupling

import no.f12.codenavigator.navigation.relations.callgraph.CallGraph
import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistry
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.SourceSet

/**
 * Which kind of caller a [TestCouplingViolation] was found in. [TEST] is the original TTTD check
 * (a test calling a port directly instead of through the domain service it's testing); [ADAPTER]
 * generalizes the same machinery one layer over — a driving adapter (a route/controller, not a
 * service-tier class) calling a port *write* method directly, meaning the use-case orchestration
 * that write implies has nowhere to live but the adapter itself. The dependency graph a class
 * *does* have never shows this — cnavRings would call this same edge a completely healthy adapter
 * calling a port. Only the fact that no service-tier class sits between them reveals it.
 */
enum class CouplingSubjectKind {
    TEST,
    ADAPTER,
}

data class TestCouplingConfig(
    val ports: Regex,
    val exclude: Regex? = null,
    val subjects: Set<CouplingSubjectKind> = setOf(CouplingSubjectKind.TEST),
    /**
     * Candidate driving-adapter classes for [CouplingSubjectKind.ADAPTER] — precomputed by the
     * orchestrator from `cnavRings`' own classification (adapters minus service-tier minus
     * framework-generated proxies), not derived here. [TestCouplingBuilder.analyze] further
     * narrows this to exclude classes that implement one of [ports] themselves (a port's own
     * implementor calling its own interface isn't the shape this check is for).
     */
    val candidateAdapterClasses: Set<ClassName> = emptySet(),
    val writeMethods: Set<String> = emptySet(),
    val readMethods: Set<String> = emptySet(),
)

data class TestCouplingViolation(
    val testClass: ClassName,
    val testMethod: String,
    val portInterface: ClassName,
    val portMethod: MethodRef,
    val subjectKind: CouplingSubjectKind = CouplingSubjectKind.TEST,
)

data class TestCouplingResult(
    val violations: List<TestCouplingViolation>,
    val testClassNonPortCalls: Map<ClassName, Int>,
    val portImplementors: Set<ClassName> = emptySet(),
    val portInterfaces: Set<ClassName> = emptySet(),
    val testClassCallTargets: Map<ClassName, Map<ClassName, Int>> = emptyMap(),
) {
    /**
     * [violations] excluding ones from adapter *tests* — adapter tests are expected to call ports
     * directly, so their "violations" aren't real TTTD problems. This exclusion only ever applied
     * to [CouplingSubjectKind.TEST] violations; [CouplingSubjectKind.ADAPTER] violations have no
     * equivalent "expected to call the port directly" carve-out (that's the whole point of the
     * check), so they're always actionable. This is what every formatter (text/detail/LLM) reports.
     */
    val actionableViolations: List<TestCouplingViolation>
        get() = violations.filter { v ->
            when (v.subjectKind) {
                CouplingSubjectKind.TEST -> verdictFor(v.testClass) != TestCouplingVerdict.ADAPTER_TEST
                CouplingSubjectKind.ADAPTER -> true
            }
        }

    fun verdictFor(testClass: ClassName): TestCouplingVerdict {
        if (isAdapterTest(testClass)) return TestCouplingVerdict.ADAPTER_TEST

        val classViolations = violations.filter { it.testClass == testClass }
        val nonPortCalls = testClassNonPortCalls[testClass] ?: 0
        return when {
            classViolations.isEmpty() -> TestCouplingVerdict.DOMAIN_ORIENTED
            nonPortCalls == 0 -> TestCouplingVerdict.DATA_ORIENTED
            else -> TestCouplingVerdict.MIXED
        }
    }

    fun confidenceFor(testClass: ClassName): Double {
        val portCalls = violations.count { it.testClass == testClass }
        val nonPortCalls = testClassNonPortCalls[testClass] ?: 0
        val total = portCalls + nonPortCalls
        if (total == 0) return 0.0
        return portCalls.toDouble() / total.toDouble()
    }

    private fun isAdapterTest(testClass: ClassName): Boolean {
        val targets = testClassCallTargets[testClass] ?: return false
        val totalCalls = targets.values.sum()
        val primaryTarget = targets.maxByOrNull { it.value }?.key ?: return false
        val primaryCalls = targets[primaryTarget] ?: 0

        // For port implementors: majority of calls is enough
        if (primaryTarget in portImplementors && primaryCalls * 2 > totalCalls) return true

        // For port interfaces: must be the dominant target with multiple calls
        // (a single call to a port is more likely a violation than an adapter test)
        if (primaryTarget in portInterfaces && primaryCalls * 2 > totalCalls && primaryCalls >= 3) return true

        return false
    }
}

enum class TestCouplingVerdict {
    DOMAIN_ORIENTED,
    ADAPTER_TEST,
    MIXED,
    DATA_ORIENTED,
}

object TestCouplingBuilder {

    /**
     * Package/class prefixes for assertion libraries — excluded from [TestCouplingResult]'s call-target
     * tracking so they don't dilute the adapter-test detection ratio. A DAO/adapter test that chains
     * several `assertThat(...).isEqualTo(...)` calls per test method can otherwise have port calls fall
     * below the majority threshold purely from assertion-library noise, misclassifying a real adapter
     * test as MIXED or DOMAIN_ORIENTED.
     */
    private val ASSERTION_LIBRARY_PREFIXES = listOf(
        "kotlin.test.",
        "org.junit.jupiter.api.Assertions",
        "org.junit.Assert",
        "org.assertj.core.api.",
        "org.hamcrest.",
        "io.kotest.matchers.",
        "io.kotest.assertions.",
        "strikt.api.",
        "strikt.assertions.",
    )

    private fun isAssertionLibraryCall(className: ClassName): Boolean =
        ASSERTION_LIBRARY_PREFIXES.any { className.value.startsWith(it) }

    fun analyze(
        callGraph: CallGraph,
        interfaceRegistry: InterfaceRegistry,
        config: TestCouplingConfig,
    ): TestCouplingResult {
        val portInterfaces = interfaceRegistry.findInterfaces(config.ports.pattern).toSet()
        val portMethods: Map<ClassName, Set<String>> = portInterfaces.associateWith { iface ->
            callGraph.declaredMethodsOf(iface)
        }
        val portImplementors: Set<ClassName> = portInterfaces.flatMap { iface ->
            interfaceRegistry.implementorsOf(iface).map { it.className }
        }.toSet()

        // A port's own implementor calling its own interface isn't the shape this check is for —
        // exclude it from the adapter-subject candidate set (the caller here has to be a *different*
        // class reaching directly into the port, not the port's own implementation).
        val adapterClasses: Set<ClassName> = config.candidateAdapterClasses - portImplementors

        val violations = mutableListOf<TestCouplingViolation>()
        val nonPortCalls = mutableMapOf<ClassName, Int>()
        val testClassCallTargets = mutableMapOf<ClassName, MutableMap<ClassName, Int>>()

        callGraph.forEachEdge { caller, callee ->
            val callerClass = outerClassName(caller.className)
            if (config.exclude != null && config.exclude.containsMatchIn(callerClass.value)) return@forEachEdge
            if (isAssertionLibraryCall(callee.className)) return@forEachEdge

            val callerSourceSet = callGraph.sourceSetOf(caller.className)
            val isTestSubject = CouplingSubjectKind.TEST in config.subjects &&
                callerSourceSet == SourceSet.TEST && callGraph.hasTestAnnotations(callerClass)
            val isAdapterSubject = CouplingSubjectKind.ADAPTER in config.subjects &&
                callerSourceSet != SourceSet.TEST && callerClass in adapterClasses

            if (isTestSubject) {
                // Track all call targets per (outer) test class — used by the adapter-test heuristic below.
                testClassCallTargets
                    .getOrPut(callerClass) { mutableMapOf() }
                    .merge(callee.className, 1) { a, b -> a + b }

                val portInterface = resolvePortInterface(callee, portMethods, interfaceRegistry)
                if (portInterface != null) {
                    violations.add(
                        TestCouplingViolation(
                            testClass = callerClass,
                            testMethod = caller.methodName,
                            portInterface = portInterface,
                            portMethod = callee,
                            subjectKind = CouplingSubjectKind.TEST,
                        )
                    )
                } else {
                    nonPortCalls[callerClass] = (nonPortCalls[callerClass] ?: 0) + 1
                }
            }

            if (isAdapterSubject) {
                val portInterface = resolvePortInterface(callee, portMethods, interfaceRegistry)
                if (portInterface != null && PortMethodClassifier.isWrite(portInterface, callee.methodName, config.writeMethods, config.readMethods)) {
                    violations.add(
                        TestCouplingViolation(
                            testClass = callerClass,
                            testMethod = caller.methodName,
                            portInterface = portInterface,
                            portMethod = callee,
                            subjectKind = CouplingSubjectKind.ADAPTER,
                        )
                    )
                }
            }
        }

        return TestCouplingResult(

            violations = violations,
            testClassNonPortCalls = nonPortCalls,
            portImplementors = portImplementors,
            portInterfaces = portInterfaces,
            testClassCallTargets = testClassCallTargets,
        )
    }

    private fun resolvePortInterface(
        callee: MethodRef,
        portMethods: Map<ClassName, Set<String>>,
        interfaceRegistry: InterfaceRegistry,
    ): ClassName? {
        // Constructors and static initializers are never behavioral port calls
        if (callee.methodName == "<init>" || callee.methodName == "<clinit>") return null

        // Direct call to the port interface itself
        portMethods[callee.className]?.let { methods ->
            if (callee.methodName in methods) return callee.className
        }

        // Call to an implementor of a port interface, to a method declared on the interface
        for (iface in interfaceRegistry.interfacesOf(callee.className)) {
            val methods = portMethods[iface] ?: continue
            if (callee.methodName in methods) return iface
        }

        return null
    }

    private fun outerClassName(className: ClassName): ClassName {
        val name = className.value
        val dollarIndex = name.indexOf('$')
        return if (dollarIndex > 0) ClassName(name.substring(0, dollarIndex)) else className
    }
}
