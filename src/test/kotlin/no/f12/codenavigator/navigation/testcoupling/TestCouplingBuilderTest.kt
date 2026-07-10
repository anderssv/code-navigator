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
import kotlin.test.assertTrue

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

    // [TEST] Test class that tests a port implementor gets ADAPTER_TEST verdict
    // Detection: the test's primary callee (most-called class) is a port implementor
    @Test
    fun testClassTestingPortImplementorGetsAdapterTestVerdict() {
        // SomeAdapterTest calls RAClient.search() — RAClient implements RARepository
        val graph = callGraphWithSourceSets(
            edges = listOf(
                method("com.example.infra.SomeAdapterTest", "testSearch") to method("com.example.infra.RAClient", "search"),
                method("com.example.infra.SomeAdapterTest", "testFindById") to method("com.example.infra.RAClient", "findById"),
            ),
            testClasses = setOf("com.example.infra.SomeAdapterTest"),
            declaredMethods = mapOf(
                ClassName("com.example.RARepository") to setOf("search", "findById"),
            ),
        )
        val interfaceRegistry = interfaceRegistryWith(
            "com.example.RARepository" to listOf("com.example.infra.RAClient"),
        )
        val config = TestCouplingConfig(ports = Regex(".*Repository"))

        val result = TestCouplingBuilder.analyze(graph, interfaceRegistry, config)

        val verdict = result.verdictFor(ClassName("com.example.infra.SomeAdapterTest"))
        assertEquals(TestCouplingVerdict.ADAPTER_TEST, verdict)
    }

    // [TEST] Adapter test stays ADAPTER_TEST despite heavy assertion-library call noise
    @Test
    fun adapterTestWithHeavyAssertionChainsStillGetsAdapterTestVerdict() {
        // Same shape as testClassTestingPortImplementorGetsAdapterTestVerdict, but each test method
        // also chains several assertion-library calls — without filtering these out, primary-target
        // calls (2) wouldn't clear the majority-of-totalCalls threshold once diluted by the noise.
        val graph = callGraphWithSourceSets(
            edges = listOf(
                method("com.example.infra.SomeAdapterTest", "testSearch") to method("com.example.infra.RAClient", "search"),
                method("com.example.infra.SomeAdapterTest", "testFindById") to method("com.example.infra.RAClient", "findById"),
                method("com.example.infra.SomeAdapterTest", "testSearch") to method("kotlin.test.AssertionsKt", "assertEquals"),
                method("com.example.infra.SomeAdapterTest", "testSearch") to method("kotlin.test.AssertionsKt", "assertTrue"),
                method("com.example.infra.SomeAdapterTest", "testFindById") to method("org.assertj.core.api.Assertions", "assertThat"),
            ),
            testClasses = setOf("com.example.infra.SomeAdapterTest"),
            declaredMethods = mapOf(
                ClassName("com.example.RARepository") to setOf("search", "findById"),
            ),
        )
        val interfaceRegistry = interfaceRegistryWith(
            "com.example.RARepository" to listOf("com.example.infra.RAClient"),
        )
        val config = TestCouplingConfig(ports = Regex(".*Repository"))

        val result = TestCouplingBuilder.analyze(graph, interfaceRegistry, config)

        val verdict = result.verdictFor(ClassName("com.example.infra.SomeAdapterTest"))
        assertEquals(TestCouplingVerdict.ADAPTER_TEST, verdict)
    }

    // [TEST] Test class in service package calling port methods is NOT an adapter test
    @Test
    fun serviceTestCallingPortIsNotAdapterTest() {
        // SearchServiceTest calls RAClient.search() but also calls SearchService — it's MIXED
        val graph = callGraphWithSourceSets(
            edges = listOf(
                method("com.example.services.SearchServiceTest", "testSearch") to method("com.example.infra.RAClient", "search"),
                method("com.example.services.SearchServiceTest", "testFind") to method("com.example.services.SearchService", "find"),
            ),
            testClasses = setOf("com.example.services.SearchServiceTest"),
            declaredMethods = mapOf(
                ClassName("com.example.RARepository") to setOf("search", "findById"),
            ),
        )
        val interfaceRegistry = interfaceRegistryWith(
            "com.example.RARepository" to listOf("com.example.infra.RAClient"),
        )
        val config = TestCouplingConfig(ports = Regex(".*Repository"))

        val result = TestCouplingBuilder.analyze(graph, interfaceRegistry, config)

        val verdict = result.verdictFor(ClassName("com.example.services.SearchServiceTest"))
        assertEquals(TestCouplingVerdict.MIXED, verdict)
    }

    // [TEST] Confidence score reflects ratio of port calls to total calls
    @Test
    fun confidenceScoreReflectsPortCallRatio() {
        val graph = callGraphWithSourceSets(
            edges = listOf(
                method("com.example.HighRatioTest", "test1") to method("com.example.Repo", "save"),
                method("com.example.HighRatioTest", "test2") to method("com.example.Repo", "delete"),
                method("com.example.HighRatioTest", "test3") to method("com.example.Repo", "find"),
                method("com.example.HighRatioTest", "test4") to method("com.example.Repo", "update"),
                method("com.example.HighRatioTest", "test5") to method("com.example.Service", "register"),
                method("com.example.LowRatioTest", "test1") to method("com.example.Repo", "save"),
                method("com.example.LowRatioTest", "test2") to method("com.example.Service", "register"),
                method("com.example.LowRatioTest", "test3") to method("com.example.Service", "approve"),
                method("com.example.LowRatioTest", "test4") to method("com.example.Service", "find"),
                method("com.example.LowRatioTest", "test5") to method("com.example.Service", "delete"),
            ),
            testClasses = setOf("com.example.HighRatioTest", "com.example.LowRatioTest"),
            declaredMethods = mapOf(
                ClassName("com.example.Repo") to setOf("save", "delete", "find", "update"),
            ),
        )
        val interfaceRegistry = interfaceRegistryWith(
            "com.example.Repo" to listOf("com.example.RepoImpl"),
        )
        val config = TestCouplingConfig(ports = Regex(".*Repo"))

        val result = TestCouplingBuilder.analyze(graph, interfaceRegistry, config)

        val highScore = result.confidenceFor(ClassName("com.example.HighRatioTest"))
        val lowScore = result.confidenceFor(ClassName("com.example.LowRatioTest"))
        assertTrue(highScore > lowScore, "High ratio ($highScore) should be greater than low ratio ($lowScore)")
        // 4 port calls out of 5 total = 0.8
        assertEquals(0.8, highScore, 0.01)
        // 1 port call out of 5 total = 0.2
        assertEquals(0.2, lowScore, 0.01)
    }

    // [TEST] Excluded test classes are filtered from results
    @Test
    fun excludedTestClassesAreFiltered() {
        val graph = callGraphWithSourceSets(
            edges = listOf(
                method("com.example.RepoImplTest", "testSave") to method("com.example.Repo", "save"),
                method("com.example.ServiceTest", "testRegister") to method("com.example.Repo", "save"),
            ),
            testClasses = setOf("com.example.RepoImplTest", "com.example.ServiceTest"),
            declaredMethods = mapOf(
                ClassName("com.example.Repo") to setOf("save"),
            ),
        )
        val interfaceRegistry = interfaceRegistryWith(
            "com.example.Repo" to listOf("com.example.RepoImpl"),
        )
        val config = TestCouplingConfig(ports = Regex(".*Repo"), exclude = Regex(".*ImplTest"))

        val result = TestCouplingBuilder.analyze(graph, interfaceRegistry, config)

        val violatingClasses = result.violations.map { it.testClass.value }.distinct()
        assertEquals(listOf("com.example.ServiceTest"), violatingClasses)
    }

    // [TEST] Constructor calls (<init>) on port classes are not flagged as violations
    @Test
    fun constructorCallsOnPortClassesAreNotFlagged() {
        // RAClientResult implements RAClient (or is found via interfacesOf) and <init> is in declaredMethods
        val graph = callGraphWithSourceSets(
            edges = listOf(
                method("com.example.ServiceTest", "testOrder") to method("com.example.RAClientResult", "<init>"),
                method("com.example.ServiceTest", "testOrder") to method("com.example.OrderService", "placeOrder"),
            ),
            testClasses = setOf("com.example.ServiceTest"),
            declaredMethods = mapOf(
                ClassName("com.example.RAClient") to setOf("reissue", "getInfo", "<init>"),
            ),
        )
        val interfaceRegistry = interfaceRegistryWith(
            "com.example.RAClient" to listOf("com.example.RAClientResult"),
        )
        val config = TestCouplingConfig(ports = Regex(".*Client"))

        val result = TestCouplingBuilder.analyze(graph, interfaceRegistry, config)

        assertEquals(0, result.violations.size)
        assertEquals(TestCouplingVerdict.DOMAIN_ORIENTED, result.verdictFor(ClassName("com.example.ServiceTest")))
    }

    // [TEST] Test whose primary callee is a port interface gets ADAPTER_TEST verdict
    @Test
    fun testWhosePrimaryCalleeIsPortInterfaceGetsAdapterTestVerdict() {
        // PollsRepositoryDatabaseTest primarily calls PollsRepository (the interface) directly
        val graph = callGraphWithSourceSets(
            edges = listOf(
                method("com.example.PollsRepositoryDatabaseTest", "testAdd") to method("com.example.PollsRepository", "addPoll"),
                method("com.example.PollsRepositoryDatabaseTest", "testGet") to method("com.example.PollsRepository", "getPoll"),
                method("com.example.PollsRepositoryDatabaseTest", "testUpdate") to method("com.example.PollsRepository", "updatePoll"),
            ),
            testClasses = setOf("com.example.PollsRepositoryDatabaseTest"),
            declaredMethods = mapOf(
                ClassName("com.example.PollsRepository") to setOf("addPoll", "getPoll", "updatePoll"),
            ),
        )
        val interfaceRegistry = interfaceRegistryWith(
            "com.example.PollsRepository" to listOf("com.example.PollsRepositoryImpl"),
        )
        val config = TestCouplingConfig(ports = Regex(".*Repository"))

        val result = TestCouplingBuilder.analyze(graph, interfaceRegistry, config)

        val verdict = result.verdictFor(ClassName("com.example.PollsRepositoryDatabaseTest"))
        assertEquals(TestCouplingVerdict.ADAPTER_TEST, verdict)
    }

    // [TEST] Inner class lambdas are aggregated with outer class for verdict
    @Test
    fun innerClassLambdasAggregatedWithOuterClassForVerdict() {
        // RAClientTest$testReissue$1 calls RAClient.reissue — should be seen as part of RAClientTest
        val graph = callGraphWithSourceSets(
            edges = listOf(
                method("com.example.RAClientTest\$testReissue\$1", "invokeSuspend") to method("com.example.RAClient", "reissue"),
                method("com.example.RAClientTest\$testGetInfo\$1", "invokeSuspend") to method("com.example.RAClient", "getInfo"),
                method("com.example.RAClientTest\$testUnblock\$1", "invokeSuspend") to method("com.example.RAClient", "unblock"),
                method("com.example.RAClientTest", "setup") to method("com.example.TestUtils", "createConfig"),
            ),
            testClasses = setOf(
                "com.example.RAClientTest\$testReissue\$1",
                "com.example.RAClientTest\$testGetInfo\$1",
                "com.example.RAClientTest\$testUnblock\$1",
                "com.example.RAClientTest",
            ),
            declaredMethods = mapOf(
                ClassName("com.example.RAClient") to setOf("reissue", "getInfo", "unblock"),
            ),
        )
        val interfaceRegistry = interfaceRegistryWith(
            "com.example.RAClient" to listOf("com.example.RAClientImpl"),
        )
        val config = TestCouplingConfig(ports = Regex(".*Client"))

        val result = TestCouplingBuilder.analyze(graph, interfaceRegistry, config)

        // Inner class violations should be aggregated to outer class
        val outerVerdict = result.verdictFor(ClassName("com.example.RAClientTest"))
        assertEquals(TestCouplingVerdict.ADAPTER_TEST, outerVerdict)
        // Inner classes should not appear separately in violations
        val innerViolations = result.violations.filter { it.testClass.value.contains("\$") }
        assertEquals(0, innerViolations.size)
    }

    // [TEST] Non-test classes (no @Test annotations) are excluded from results
    @Test
    fun nonTestClassesAreExcludedFromResults() {
        val graph = callGraphWithSourceSets(
            edges = listOf(
                method("com.example.RAClientFake", "addResult") to method("com.example.RAClient", "reissue"),
                method("com.example.TestExtensionsKt", "asErrResult") to method("com.example.RAClient", "getInfo"),
                method("com.example.TestUtils", "setup") to method("com.example.RAClient", "unblock"),
                method("com.example.SearchServiceTest", "testSearch") to method("com.example.RAClient", "getInfo"),
            ),
            testClasses = setOf("com.example.RAClientFake", "com.example.TestExtensionsKt", "com.example.TestUtils", "com.example.SearchServiceTest"),
            declaredMethods = mapOf(
                ClassName("com.example.RAClient") to setOf("reissue", "getInfo", "unblock"),
            ),
            testAnnotatedClasses = setOf("com.example.SearchServiceTest"),
        )
        val interfaceRegistry = interfaceRegistryWith(
            "com.example.RAClient" to listOf("com.example.RAClientImpl"),
        )
        val config = TestCouplingConfig(ports = Regex(".*Client"))

        val result = TestCouplingBuilder.analyze(graph, interfaceRegistry, config)

        val violatingClasses = result.violations.map { it.testClass.value }.distinct()
        assertEquals(listOf("com.example.SearchServiceTest"), violatingClasses)
    }

    // --- Test helpers ---

    private fun callGraphWithSourceSets(
        edges: List<Pair<MethodRef, MethodRef>>,
        testClasses: Set<String>,
        declaredMethods: Map<ClassName, Set<String>> = emptyMap(),
        testAnnotatedClasses: Set<String>? = null,
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

        val annotated = (testAnnotatedClasses ?: testClasses).map { ClassName(it) }.toSet()
        return CallGraph(callerToCallees, sourceFiles, sourceSets = sourceSets, declaredMethods = declaredMethods, testAnnotatedClasses = annotated)
    }

    private fun interfaceRegistryWith(vararg entries: Pair<String, List<String>>): InterfaceRegistry {
        val map = entries.associate { (iface, impls) ->
            ClassName(iface) to impls.map { ImplementorInfo(ClassName(it), "${it.substringAfterLast('.')}.kt") }
        }
        return InterfaceRegistry(map)
    }
}
