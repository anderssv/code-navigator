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
            if (config.exclude != null && config.exclude.containsMatchIn(caller.className.value)) return@forEachEdge

            // Track all call targets per test class
            testClassCallTargets
                .getOrPut(caller.className) { mutableMapOf() }
                .merge(callee.className, 1) { a, b -> a + b }

            val portInterface = resolvePortInterface(callee, portMethods, interfaceRegistry)

            if (portInterface != null) {
                violations.add(
                    TestCouplingViolation(
                        testClass = caller.className,
                        testMethod = caller.methodName,
                        portInterface = portInterface,
                        portMethod = callee,
                    )
                )
            } else {
                nonPortCalls[caller.className] = (nonPortCalls[caller.className] ?: 0) + 1
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
}
