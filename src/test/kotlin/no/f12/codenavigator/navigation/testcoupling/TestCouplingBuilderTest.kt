package no.f12.codenavigator.navigation.testcoupling

import no.f12.codenavigator.navigation.method
import no.f12.codenavigator.navigation.relations.callgraph.CallGraph
import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import no.f12.codenavigator.navigation.relations.implementors.InterfaceRegistry
import no.f12.codenavigator.navigation.relations.implementors.ImplementorInfo
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.SourceSet
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCouplingBuilderTest {

    // [TEST] Test method calling port interface mutation method is flagged
    @Test
    fun testMethodCallingPortMutationIsFlagged() {
        val graph = callGraphWithSourceSets(
            edges = listOf(
                method("com.example.AppTest", "testRegister") to method("com.example.ApplicationRepository", "addApplication"),
            ),
            testClasses = setOf("com.example.AppTest"),
            declaredMethods = mapOf(
                ClassName("com.example.ApplicationRepository") to setOf("addApplication", "getApplication"),
            ),
        )
        val interfaceRegistry = interfaceRegistryWith(
            "com.example.ApplicationRepository" to listOf("com.example.ApplicationRepositoryFake"),
        )
        val config = TestCouplingConfig(ports = Regex(".*Repository"))

        val result = TestCouplingBuilder.analyze(graph, interfaceRegistry, config)

        assertEquals(1, result.violations.size)
        assertEquals("com.example.AppTest", result.violations[0].testClass.value)
        assertEquals("addApplication", result.violations[0].portMethod.methodName)
    }

    // [TEST] Test method calling service method is not flagged
    @Test
    fun testMethodCallingServiceIsNotFlagged() {
        val graph = callGraphWithSourceSets(
            edges = listOf(
                method("com.example.AppTest", "testRegister") to method("com.example.ApplicationService", "register"),
            ),
            testClasses = setOf("com.example.AppTest"),
        )
        val interfaceRegistry = interfaceRegistryWith(
            "com.example.ApplicationRepository" to listOf("com.example.ApplicationRepositoryFake"),
        )
        val config = TestCouplingConfig(ports = Regex(".*Repository"))

        val result = TestCouplingBuilder.analyze(graph, interfaceRegistry, config)

        assertEquals(0, result.violations.size)
    }

    // [TEST] Test method calling method NOT on port interface is not flagged (fake-only method)
    @Test
    fun fakeOnlyMethodNotFlagged() {
        val graph = callGraphWithSourceSets(
            edges = listOf(
                method("com.example.AppTest", "testError") to method("com.example.ApplicationRepositoryFake", "failOnNext"),
            ),
            testClasses = setOf("com.example.AppTest"),
            declaredMethods = mapOf(
                ClassName("com.example.ApplicationRepository") to setOf("addApplication", "getApplication"),
            ),
        )
        val interfaceRegistry = interfaceRegistryWith(
            "com.example.ApplicationRepository" to listOf("com.example.ApplicationRepositoryFake"),
        )
        val config = TestCouplingConfig(ports = Regex(".*Repository"))

        val result = TestCouplingBuilder.analyze(graph, interfaceRegistry, config)

        assertEquals(0, result.violations.size)
    }

    // [TEST] Production class calling port methods is not flagged
    @Test
    fun productionClassCallingPortIsNotFlagged() {
        val graph = callGraphWithSourceSets(
            edges = listOf(
                method("com.example.ApplicationService", "register") to method("com.example.ApplicationRepository", "addApplication"),
            ),
            testClasses = emptySet(),
        )
        val interfaceRegistry = interfaceRegistryWith(
            "com.example.ApplicationRepository" to listOf("com.example.ApplicationRepositoryImpl"),
        )
        val config = TestCouplingConfig(ports = Regex(".*Repository"))

        val result = TestCouplingBuilder.analyze(graph, interfaceRegistry, config)

        assertEquals(0, result.violations.size)
    }

    // [TEST] Test class with only service calls gets DOMAIN_ORIENTED verdict
    @Test
    fun testClassWithOnlyServiceCallsIsDomainOriented() {
        val graph = callGraphWithSourceSets(
            edges = listOf(
                method("com.example.AppTest", "testRegister") to method("com.example.ApplicationService", "register"),
                method("com.example.AppTest", "testApprove") to method("com.example.ApplicationService", "approve"),
            ),
            testClasses = setOf("com.example.AppTest"),
            declaredMethods = mapOf(
                ClassName("com.example.ApplicationRepository") to setOf("addApplication", "getApplication"),
            ),
        )
        val interfaceRegistry = interfaceRegistryWith(
            "com.example.ApplicationRepository" to listOf("com.example.ApplicationRepositoryFake"),
        )
        val config = TestCouplingConfig(ports = Regex(".*Repository"))

        val result = TestCouplingBuilder.analyze(graph, interfaceRegistry, config)

        val verdict = result.verdictFor(ClassName("com.example.AppTest"))
        assertEquals(TestCouplingVerdict.DOMAIN_ORIENTED, verdict)
    }

    // [TEST] Test class with only port mutations gets DATA_ORIENTED verdict
    @Test
    fun testClassWithOnlyPortMutationsIsDataOriented() {
        val graph = callGraphWithSourceSets(
            edges = listOf(
                method("com.example.AppTest", "testRegister") to method("com.example.ApplicationRepository", "addApplication"),
            ),
            testClasses = setOf("com.example.AppTest"),
            declaredMethods = mapOf(
                ClassName("com.example.ApplicationRepository") to setOf("addApplication", "getApplication"),
            ),
        )
        val interfaceRegistry = interfaceRegistryWith(
            "com.example.ApplicationRepository" to listOf("com.example.ApplicationRepositoryFake"),
        )
        val config = TestCouplingConfig(ports = Regex(".*Repository"))

        val result = TestCouplingBuilder.analyze(graph, interfaceRegistry, config)

        val verdict = result.verdictFor(ClassName("com.example.AppTest"))
        assertEquals(TestCouplingVerdict.DATA_ORIENTED, verdict)
    }

    // [TEST] Test class with both port mutations and service calls gets MIXED verdict
    @Test
    fun testClassWithBothPortAndServiceCallsIsMixed() {
        val graph = callGraphWithSourceSets(
            edges = listOf(
                method("com.example.AppTest", "testRegister") to method("com.example.ApplicationRepository", "addApplication"),
                method("com.example.AppTest", "testApprove") to method("com.example.ApplicationService", "approve"),
            ),
            testClasses = setOf("com.example.AppTest"),
            declaredMethods = mapOf(
                ClassName("com.example.ApplicationRepository") to setOf("addApplication", "getApplication"),
            ),
        )
        val interfaceRegistry = interfaceRegistryWith(
            "com.example.ApplicationRepository" to listOf("com.example.ApplicationRepositoryFake"),
        )
        val config = TestCouplingConfig(ports = Regex(".*Repository"))

        val result = TestCouplingBuilder.analyze(graph, interfaceRegistry, config)

        val verdict = result.verdictFor(ClassName("com.example.AppTest"))
        assertEquals(TestCouplingVerdict.MIXED, verdict)
    }

    // --- Test helpers ---

    private fun callGraphWithSourceSets(
        edges: List<Pair<MethodRef, MethodRef>>,
        testClasses: Set<String>,
        declaredMethods: Map<ClassName, Set<String>> = emptyMap(),
    ): CallGraph {
        val callerToCallees = mutableMapOf<MethodRef, MutableSet<MethodRef>>()
        val sourceFiles = mutableMapOf<ClassName, String>()
        val sourceSets = mutableMapOf<ClassName, SourceSet>()

        for ((caller, callee) in edges) {
            callerToCallees.getOrPut(caller) { mutableSetOf() }.add(callee)
        }

        val allClasses = edges.flatMap { listOf(it.first.className.value, it.second.className.value) }.toSet()
        for (cls in allClasses) {
            val cn = ClassName(cls)
            sourceFiles[cn] = "${cls.substringAfterLast('.')}.kt"
            sourceSets[cn] = if (cls in testClasses) SourceSet.TEST else SourceSet.MAIN
        }

        return CallGraph(callerToCallees, sourceFiles, sourceSets = sourceSets, declaredMethods = declaredMethods)
    }

    private fun interfaceRegistryWith(vararg entries: Pair<String, List<String>>): InterfaceRegistry {
        val map = entries.associate { (iface, impls) ->
            ClassName(iface) to impls.map { ImplementorInfo(ClassName(it), "${it.substringAfterLast('.')}.kt") }
        }
        return InterfaceRegistry(map)
    }
}
