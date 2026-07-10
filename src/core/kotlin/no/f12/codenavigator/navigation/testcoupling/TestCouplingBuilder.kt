package no.f12.codenavigator.navigation.testcoupling

import no.f12.codenavigator.navigation.relations.callgraph.CallGraph
import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistry
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.SourceSet

data class TestCouplingConfig(
    val ports: Regex,
    val exclude: Regex? = null,
)

data class TestCouplingViolation(
    val testClass: ClassName,
    val testMethod: String,
    val portInterface: ClassName,
    val portMethod: MethodRef,
)

data class TestCouplingResult(
    val violations: List<TestCouplingViolation>,
    val testClassNonPortCalls: Map<ClassName, Int>,
    val portImplementors: Set<ClassName> = emptySet(),
    val portInterfaces: Set<ClassName> = emptySet(),
    val testClassCallTargets: Map<ClassName, Map<ClassName, Int>> = emptyMap(),
) {
    /** [violations] excluding ones from adapter tests — adapter tests are expected to call ports directly, so their "violations" aren't real TTTD problems. This is what every formatter (text/detail/LLM) reports. */
    val actionableViolations: List<TestCouplingViolation>
        get() = violations.filter { verdictFor(it.testClass) != TestCouplingVerdict.ADAPTER_TEST }

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

        val violations = mutableListOf<TestCouplingViolation>()
        val nonPortCalls = mutableMapOf<ClassName, Int>()
        val testClassCallTargets = mutableMapOf<ClassName, MutableMap<ClassName, Int>>()

        callGraph.forEachEdge { caller, callee ->
            if (callGraph.sourceSetOf(caller.className) != SourceSet.TEST) return@forEachEdge
            val effectiveTestClass = outerClassName(caller.className)
            if (!callGraph.hasTestAnnotations(effectiveTestClass)) return@forEachEdge
            if (config.exclude != null && config.exclude.containsMatchIn(effectiveTestClass.value)) return@forEachEdge
            if (isAssertionLibraryCall(callee.className)) return@forEachEdge

            // Track all call targets per (outer) test class
            testClassCallTargets
                .getOrPut(effectiveTestClass) { mutableMapOf() }
                .merge(callee.className, 1) { a, b -> a + b }

            val portInterface = resolvePortInterface(callee, portMethods, interfaceRegistry)

            if (portInterface != null) {
                violations.add(
                    TestCouplingViolation(
                        testClass = effectiveTestClass,
                        testMethod = caller.methodName,
                        portInterface = portInterface,
                        portMethod = callee,
                    )
                )
            } else {
                nonPortCalls[effectiveTestClass] = (nonPortCalls[effectiveTestClass] ?: 0) + 1
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
