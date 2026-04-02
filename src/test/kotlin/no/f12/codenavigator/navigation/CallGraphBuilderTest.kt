package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.callgraph.CallGraph
import no.f12.codenavigator.navigation.callgraph.CallGraphBuilder
import no.f12.codenavigator.navigation.callgraph.CallDirection
import no.f12.codenavigator.navigation.callgraph.CallTreeFormatter
import no.f12.codenavigator.navigation.callgraph.MethodRef
import no.f12.codenavigator.navigation.core.SourceSet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CallGraphBuilderTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var classesDir: File

    @BeforeEach
    fun setUp() {
        classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
    }

    @Test
    fun `detects a method call from caller to callee`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", listOf(Call("com/example/Target", "process", "()V")),
        )
        TestClassWriter.writeClassWithCalls(classesDir, "com/example/Target", "Target.kt", "process", emptyList())

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        val callers = graph.callersOf(ClassName("com.example.Target"), "process")
        assertEquals(1, callers.size)
        assertEquals("com.example.Caller", callers.first().className.value)
        assertEquals("doWork", callers.first().methodName)
    }

    @Test
    fun `returns empty set for method with no callers`() {
        TestClassWriter.writeClassWithCalls(classesDir, "com/example/Lonely", "Lonely.kt", "alone", emptyList())

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        val callers = graph.callersOf(ClassName("com.example.Lonely"), "alone")
        assertTrue(callers.isEmpty())
    }

    @Test
    fun `detects multiple callers of the same method`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/CallerA", "CallerA.kt",
            "fromA", listOf(Call("com/example/Target", "shared", "()V")),
        )
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/CallerB", "CallerB.kt",
            "fromB", listOf(Call("com/example/Target", "shared", "()V")),
        )
        TestClassWriter.writeClassWithCalls(classesDir, "com/example/Target", "Target.kt", "shared", emptyList())

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        val callers = graph.callersOf(ClassName("com.example.Target"), "shared")
        assertEquals(2, callers.size)
        val callerNames = callers.map { it.methodName }.toSet()
        assertTrue("fromA" in callerNames)
        assertTrue("fromB" in callerNames)
    }

    @Test
    fun `detects transitive callers through the graph`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/A", "A.kt",
            "start", listOf(Call("com/example/B", "middle", "()V")),
        )
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/B", "B.kt",
            "middle", listOf(Call("com/example/C", "end", "()V")),
        )
        TestClassWriter.writeClassWithCalls(classesDir, "com/example/C", "C.kt", "end", emptyList())

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        val directCallers = graph.callersOf(ClassName("com.example.C"), "end")
        assertEquals(1, directCallers.size)
        assertEquals("middle", directCallers.first().methodName)

        val transitiveCallers = graph.callersOf(ClassName("com.example.B"), "middle")
        assertEquals(1, transitiveCallers.size)
        assertEquals("start", transitiveCallers.first().methodName)
    }

    @Test
    fun `detects method calling multiple other methods`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Orchestrator", "Orchestrator.kt",
            "orchestrate", listOf(
                Call("com/example/StepA", "execute", "()V"),
                Call("com/example/StepB", "execute", "()V"),
            ),
        )
        TestClassWriter.writeClassWithCalls(classesDir, "com/example/StepA", "StepA.kt", "execute", emptyList())
        TestClassWriter.writeClassWithCalls(classesDir, "com/example/StepB", "StepB.kt", "execute", emptyList())

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        val callersOfA = graph.callersOf(ClassName("com.example.StepA"), "execute")
        val callersOfB = graph.callersOf(ClassName("com.example.StepB"), "execute")
        assertEquals(1, callersOfA.size)
        assertEquals(1, callersOfB.size)
        assertEquals("orchestrate", callersOfA.first().methodName)
        assertEquals("orchestrate", callersOfB.first().methodName)
    }

    @Test
    fun `matches callers by pattern`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/ServiceA", "ServiceA.kt",
            "handle", listOf(Call("com/example/Repo", "save", "()V")),
        )
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/ServiceB", "ServiceB.kt",
            "process", listOf(Call("com/example/Repo", "save", "()V")),
        )
        TestClassWriter.writeClassWithCalls(classesDir, "com/example/Repo", "Repo.kt", "save", emptyList())

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        val matches = graph.findMethods("Repo\\.save")
        assertEquals(1, matches.size)
        assertEquals("com.example.Repo", matches.first().className.value)
        assertEquals("save", matches.first().methodName)
    }

    @Test
    fun `handles empty class directories`() {
        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        val callers = graph.callersOf(ClassName("com.example.Any"), "method")
        assertTrue(callers.isEmpty())
    }

    @Test
    fun `tracks source file for classes`() {
        TestClassWriter.writeClassWithCalls(classesDir, "com/example/MyService", "MyService.kt", "execute", emptyList())
        TestClassWriter.writeClassWithCalls(classesDir, "com/example/OtherService", "OtherService.kt", "run", emptyList())

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        assertEquals("MyService.kt", graph.sourceFileOf(ClassName("com.example.MyService")))
        assertEquals("OtherService.kt", graph.sourceFileOf(ClassName("com.example.OtherService")))
        assertEquals("<unknown>", graph.sourceFileOf(ClassName("com.example.Missing")))
    }

    @Test
    fun `calleesOf returns methods called by a given method`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Orchestrator", "Orchestrator.kt",
            "orchestrate", listOf(
                Call("com/example/StepA", "execute", "()V"),
                Call("com/example/StepB", "run", "()V"),
            ),
        )
        TestClassWriter.writeClassWithCalls(classesDir, "com/example/StepA", "StepA.kt", "execute", emptyList())
        TestClassWriter.writeClassWithCalls(classesDir, "com/example/StepB", "StepB.kt", "run", emptyList())

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        val callees = graph.calleesOf(ClassName("com.example.Orchestrator"), "orchestrate")
        assertEquals(2, callees.size)
        val calleeNames = callees.map { "${it.className.value}.${it.methodName}" }.toSet()
        assertTrue("com.example.StepA.execute" in calleeNames)
        assertTrue("com.example.StepB.run" in calleeNames)
    }

    @Test
    fun `calleesOf returns empty set for method with no calls`() {
        TestClassWriter.writeClassWithCalls(classesDir, "com/example/Leaf", "Leaf.kt", "doNothing", emptyList())

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        val callees = graph.calleesOf(ClassName("com.example.Leaf"), "doNothing")
        assertTrue(callees.isEmpty())
    }

    @Test
    fun `handles constructor calls`() {
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Factory", "Factory.kt",
            "create", listOf(Call("com/example/Product", "<init>", "()V")),
        )
        TestClassWriter.writeClassFile(classesDir, "com/example/Product", "Product.kt")

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        val callers = graph.callersOf(ClassName("com.example.Product"), "<init>")
        assertEquals(1, callers.size)
        assertEquals("create", callers.first().methodName)
    }

    @Test
    fun `projectClasses returns classes with known source files`() {
        TestClassWriter.writeClassWithCalls(classesDir, "com/example/MyService", "MyService.kt", "execute", emptyList())
        TestClassWriter.writeClassWithCalls(classesDir, "com/example/OtherService", "OtherService.kt", "run", emptyList())

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        val projectClasses = graph.projectClasses()
        assertTrue(ClassName("com.example.MyService") in projectClasses)
        assertTrue(ClassName("com.example.OtherService") in projectClasses)
        assertTrue(ClassName("java.lang.Object") !in projectClasses)
    }

    @Test
    fun `sourceFileOf falls back to outer class when inner class not found`() {
        val graph = CallGraph(
            emptyMap(),
            sourceFiles = mapOf(ClassName("com.example.MyService") to "MyService.kt"),
        )

        assertEquals("MyService.kt", graph.sourceFileOf(ClassName("com.example.MyService\$InnerClass")))
    }

    @Test
    fun `sourceFileOf falls back through multiple inner class levels`() {
        val graph = CallGraph(
            emptyMap(),
            sourceFiles = mapOf(ClassName("com.example.MyService") to "MyService.kt"),
        )

        assertEquals("MyService.kt", graph.sourceFileOf(ClassName("com.example.MyService\$Lambda\$1")))
    }

    @Test
    fun `sourceFileOf returns unknown when no outer class matches either`() {
        val graph = CallGraph(
            emptyMap(),
            sourceFiles = mapOf(ClassName("com.example.Other") to "Other.kt"),
        )

        assertEquals("<unknown>", graph.sourceFileOf(ClassName("com.example.Missing\$Inner")))
    }

    @Test
    fun `findMethods expands qualified property name to getter when no direct match`() {
        val getter = MethodRef(ClassName("com.example.Account"), "getAccountNumber")
        val graph = CallGraph(
            mapOf(getter to emptySet()),
        )

        val result = graph.findMethods("Account\\.accountNumber")

        assertEquals(1, result.size)
        assertEquals("getAccountNumber", result[0].methodName)
    }

    @Test
    fun `findMethods expands to setter when no direct match`() {
        val setter = MethodRef(ClassName("com.example.Account"), "setAccountNumber")
        val graph = CallGraph(
            mapOf(setter to emptySet()),
        )

        val result = graph.findMethods("Account\\.accountNumber")

        assertEquals(1, result.size)
        assertEquals("setAccountNumber", result[0].methodName)
    }

    @Test
    fun `findMethods expands to is-getter for boolean properties`() {
        val isGetter = MethodRef(ClassName("com.example.Account"), "isActive")
        val graph = CallGraph(
            mapOf(isGetter to emptySet()),
        )

        val result = graph.findMethods("Account\\.active")

        assertEquals(1, result.size)
        assertEquals("isActive", result[0].methodName)
    }

    @Test
    fun `findMethods does not expand when direct match exists`() {
        val direct = MethodRef(ClassName("com.example.Service"), "accountNumber")
        val getter = MethodRef(ClassName("com.example.Service"), "getAccountNumber")
        val graph = CallGraph(
            mapOf(direct to emptySet(), getter to emptySet()),
        )

        val result = graph.findMethods("Service\\.accountNumber")

        assertEquals(1, result.size)
        assertEquals("accountNumber", result[0].methodName)
    }

    @Test
    fun `findMethods expansion finds both getter and setter`() {
        val getter = MethodRef(ClassName("com.example.Account"), "getAccountNumber")
        val setter = MethodRef(ClassName("com.example.Account"), "setAccountNumber")
        val graph = CallGraph(
            mapOf(getter to emptySet(), setter to emptySet()),
        )

        val result = graph.findMethods("Account\\.accountNumber")

        assertEquals(2, result.size)
        val methodNames = result.map { it.methodName }.toSet()
        assertTrue("getAccountNumber" in methodNames)
        assertTrue("setAccountNumber" in methodNames)
    }

    @Test
    fun `findMethods expands with unescaped dot`() {
        val getter = MethodRef(ClassName("com.example.Account"), "getAccountNumber")
        val graph = CallGraph(
            mapOf(getter to emptySet()),
        )

        val result = graph.findMethods("Account.accountNumber")

        assertEquals(1, result.size)
        assertEquals("getAccountNumber", result[0].methodName)
    }

    @Test
    fun `findMethods expands property with fully qualified class name`() {
        val getter = MethodRef(ClassName("no.mikill.greitt.AppDependencies"), "getParticipantService")
        val graph = CallGraph(
            mapOf(getter to emptySet()),
        )

        val result = graph.findMethods("AppDependencies.participantService")

        assertEquals(1, result.size)
        assertEquals("getParticipantService", result[0].methodName)
    }

    @Test
    fun `findMethods expands property across inner class boundary`() {
        val getter = MethodRef(ClassName("no.mikill.greitt.AppDependencies\$Services"), "getParticipantService")
        val graph = CallGraph(
            mapOf(getter to emptySet()),
        )

        val result = graph.findMethods("AppDependencies.participantService")

        assertEquals(1, result.size)
        assertEquals("getParticipantService", result[0].methodName)
    }

    @Test
    fun `multiple methods on same class each have independent caller chains`() {
        TestClassWriter.writeClassWithMultipleMethods(
            classesDir, "com/example/UserService", "UserService.kt",
            listOf(
                MethodDef("buildNotificationMessage", emptyList()),
                MethodDef("sendResetNotification", listOf(Call("com/example/UserService", "buildNotificationMessage", "()V"))),
                MethodDef("sendDeactivationNotification", listOf(Call("com/example/UserService", "buildNotificationMessage", "()V"))),
                MethodDef("resetPassword", listOf(Call("com/example/UserService", "sendResetNotification", "()V"))),
                MethodDef("deactivateUser", listOf(Call("com/example/UserService", "sendDeactivationNotification", "()V"))),
            ),
        )
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/UserRoute", "UserRoute.kt",
            "handleReset", listOf(Call("com/example/UserService", "resetPassword", "()V")),
        )

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        // Direct callers of buildNotificationMessage
        val directCallers = graph.callersOf(ClassName("com.example.UserService"), "buildNotificationMessage")
        assertEquals(2, directCallers.size, "Expected 2 direct callers of buildNotificationMessage")
        val directCallerNames = directCallers.map { it.methodName }.toSet()
        assertTrue("sendResetNotification" in directCallerNames)
        assertTrue("sendDeactivationNotification" in directCallerNames)

        // Transitive: callers of sendResetNotification
        val resetCallers = graph.callersOf(ClassName("com.example.UserService"), "sendResetNotification")
        assertEquals(1, resetCallers.size)
        assertEquals("resetPassword", resetCallers.first().methodName)

        // Transitive: callers of resetPassword
        val passwordCallers = graph.callersOf(ClassName("com.example.UserService"), "resetPassword")
        assertEquals(1, passwordCallers.size)
        assertEquals("handleReset", passwordCallers.first().methodName)

        // Full tree via formatter should show 3 levels deep
        val result = CallTreeFormatter.format(graph, listOf(MethodRef(ClassName("com.example.UserService"), "buildNotificationMessage")), maxDepth = 5, direction = CallDirection.CALLERS)
        assertTrue(result.contains("sendResetNotification"), "Should contain sendResetNotification")
        assertTrue(result.contains("resetPassword"), "Should contain resetPassword at depth 2")
        assertTrue(result.contains("handleReset"), "Should contain handleReset at depth 3")
    }

    @Test
    fun `extracts first line number for a method with line number table`() {
        TestClassWriter.writeClassWithLineNumbers(
            classesDir, "com/example/Service", "Service.kt",
            listOf(MethodWithLines("doWork", 42, emptyList())),
        )

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        assertEquals(42, graph.lineNumberOf(MethodRef(ClassName("com.example.Service"), "doWork")))
    }

    @Test
    fun `returns null line number for method without line number table`() {
        TestClassWriter.writeClassWithCalls(classesDir, "com/example/Stripped", "Stripped.kt", "execute", emptyList())

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        assertEquals(null, graph.lineNumberOf(MethodRef(ClassName("com.example.Stripped"), "execute")))
    }

    @Test
    fun `extracts independent line numbers for multiple methods in same class`() {
        TestClassWriter.writeClassWithLineNumbers(
            classesDir, "com/example/Service", "Service.kt",
            listOf(
                MethodWithLines("doWork", 10, emptyList()),
                MethodWithLines("doOtherWork", 25, emptyList()),
            ),
        )

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        assertEquals(10, graph.lineNumberOf(MethodRef(ClassName("com.example.Service"), "doWork")))
        assertEquals(25, graph.lineNumberOf(MethodRef(ClassName("com.example.Service"), "doOtherWork")))
    }

    @Test
    fun `sourceSetOf returns the source set assigned to a class`() {
        val graph = CallGraph(
            callerToCallees = emptyMap(),
            sourceFiles = mapOf(
                ClassName("com.example.Service") to "Service.kt",
                ClassName("com.example.ServiceTest") to "ServiceTest.kt",
            ),
            sourceSets = mapOf(
                ClassName("com.example.Service") to SourceSet.MAIN,
                ClassName("com.example.ServiceTest") to SourceSet.TEST,
            ),
        )

        assertEquals(SourceSet.MAIN, graph.sourceSetOf(ClassName("com.example.Service")))
        assertEquals(SourceSet.TEST, graph.sourceSetOf(ClassName("com.example.ServiceTest")))
    }

    @Test
    fun `sourceSetOf returns null for unknown class`() {
        val graph = CallGraph(
            callerToCallees = emptyMap(),
            sourceFiles = mapOf(ClassName("com.example.Service") to "Service.kt"),
            sourceSets = mapOf(ClassName("com.example.Service") to SourceSet.MAIN),
        )

        assertEquals(null, graph.sourceSetOf(ClassName("com.example.Missing")))
    }

    @Test
    fun `sourceSetOf defaults to null when no source sets provided`() {
        val graph = CallGraph(
            callerToCallees = emptyMap(),
            sourceFiles = mapOf(ClassName("com.example.Service") to "Service.kt"),
        )

        assertEquals(null, graph.sourceSetOf(ClassName("com.example.Service")))
    }

    @Test
    fun `build with tagged directories assigns source sets`() {
        val mainDir = tempDir.resolve("main-classes").toFile()
        mainDir.mkdirs()
        val testDir = tempDir.resolve("test-classes").toFile()
        testDir.mkdirs()

        TestClassWriter.writeClassWithCalls(mainDir, "com/example/Service", "Service.kt", "handle", emptyList())
        TestClassWriter.writeClassWithCalls(testDir, "com/example/ServiceTest", "ServiceTest.kt", "testHandle", emptyList())

        val graph = CallGraphBuilder.buildTagged(
            listOf(mainDir to SourceSet.MAIN, testDir to SourceSet.TEST),
        ).data

        assertEquals(SourceSet.MAIN, graph.sourceSetOf(ClassName("com.example.Service")))
        assertEquals(SourceSet.TEST, graph.sourceSetOf(ClassName("com.example.ServiceTest")))
    }
}
