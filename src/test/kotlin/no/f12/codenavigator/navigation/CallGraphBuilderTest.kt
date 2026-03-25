package no.f12.codenavigator.navigation

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
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
        writeClassWithCalls(
            "com/example/Caller", "Caller.kt",
            "doWork", listOf(Call("com/example/Target", "process", "()V")),
        )
        writeClassWithCalls("com/example/Target", "Target.kt", "process", emptyList())

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        val callers = graph.callersOf(ClassName("com.example.Target"), "process")
        assertEquals(1, callers.size)
        assertEquals("com.example.Caller", callers.first().className.value)
        assertEquals("doWork", callers.first().methodName)
    }

    @Test
    fun `returns empty set for method with no callers`() {
        writeClassWithCalls("com/example/Lonely", "Lonely.kt", "alone", emptyList())

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        val callers = graph.callersOf(ClassName("com.example.Lonely"), "alone")
        assertTrue(callers.isEmpty())
    }

    @Test
    fun `detects multiple callers of the same method`() {
        writeClassWithCalls(
            "com/example/CallerA", "CallerA.kt",
            "fromA", listOf(Call("com/example/Target", "shared", "()V")),
        )
        writeClassWithCalls(
            "com/example/CallerB", "CallerB.kt",
            "fromB", listOf(Call("com/example/Target", "shared", "()V")),
        )
        writeClassWithCalls("com/example/Target", "Target.kt", "shared", emptyList())

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        val callers = graph.callersOf(ClassName("com.example.Target"), "shared")
        assertEquals(2, callers.size)
        val callerNames = callers.map { it.methodName }.toSet()
        assertTrue("fromA" in callerNames)
        assertTrue("fromB" in callerNames)
    }

    @Test
    fun `detects transitive callers through the graph`() {
        writeClassWithCalls(
            "com/example/A", "A.kt",
            "start", listOf(Call("com/example/B", "middle", "()V")),
        )
        writeClassWithCalls(
            "com/example/B", "B.kt",
            "middle", listOf(Call("com/example/C", "end", "()V")),
        )
        writeClassWithCalls("com/example/C", "C.kt", "end", emptyList())

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
        writeClassWithCalls(
            "com/example/Orchestrator", "Orchestrator.kt",
            "orchestrate", listOf(
                Call("com/example/StepA", "execute", "()V"),
                Call("com/example/StepB", "execute", "()V"),
            ),
        )
        writeClassWithCalls("com/example/StepA", "StepA.kt", "execute", emptyList())
        writeClassWithCalls("com/example/StepB", "StepB.kt", "execute", emptyList())

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
        writeClassWithCalls(
            "com/example/ServiceA", "ServiceA.kt",
            "handle", listOf(Call("com/example/Repo", "save", "()V")),
        )
        writeClassWithCalls(
            "com/example/ServiceB", "ServiceB.kt",
            "process", listOf(Call("com/example/Repo", "save", "()V")),
        )
        writeClassWithCalls("com/example/Repo", "Repo.kt", "save", emptyList())

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
        writeClassWithCalls("com/example/MyService", "MyService.kt", "execute", emptyList())
        writeClassWithCalls("com/example/OtherService", "OtherService.kt", "run", emptyList())

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        assertEquals("MyService.kt", graph.sourceFileOf(ClassName("com.example.MyService")))
        assertEquals("OtherService.kt", graph.sourceFileOf(ClassName("com.example.OtherService")))
        assertEquals("<unknown>", graph.sourceFileOf(ClassName("com.example.Missing")))
    }

    @Test
    fun `calleesOf returns methods called by a given method`() {
        writeClassWithCalls(
            "com/example/Orchestrator", "Orchestrator.kt",
            "orchestrate", listOf(
                Call("com/example/StepA", "execute", "()V"),
                Call("com/example/StepB", "run", "()V"),
            ),
        )
        writeClassWithCalls("com/example/StepA", "StepA.kt", "execute", emptyList())
        writeClassWithCalls("com/example/StepB", "StepB.kt", "run", emptyList())

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        val callees = graph.calleesOf(ClassName("com.example.Orchestrator"), "orchestrate")
        assertEquals(2, callees.size)
        val calleeNames = callees.map { "${it.className.value}.${it.methodName}" }.toSet()
        assertTrue("com.example.StepA.execute" in calleeNames)
        assertTrue("com.example.StepB.run" in calleeNames)
    }

    @Test
    fun `calleesOf returns empty set for method with no calls`() {
        writeClassWithCalls("com/example/Leaf", "Leaf.kt", "doNothing", emptyList())

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        val callees = graph.calleesOf(ClassName("com.example.Leaf"), "doNothing")
        assertTrue(callees.isEmpty())
    }

    @Test
    fun `handles constructor calls`() {
        writeClassWithCalls(
            "com/example/Factory", "Factory.kt",
            "create", listOf(Call("com/example/Product", "<init>", "()V")),
        )
        writeEmptyClass("com/example/Product", "Product.kt")

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        val callers = graph.callersOf(ClassName("com.example.Product"), "<init>")
        assertEquals(1, callers.size)
        assertEquals("create", callers.first().methodName)
    }

    @Test
    fun `projectClasses returns classes with known source files`() {
        writeClassWithCalls("com/example/MyService", "MyService.kt", "execute", emptyList())
        writeClassWithCalls("com/example/OtherService", "OtherService.kt", "run", emptyList())

        val graph = CallGraphBuilder.build(listOf(classesDir)).data

        val projectClasses = graph.projectClasses()
        assertTrue(ClassName("com.example.MyService") in projectClasses)
        assertTrue(ClassName("com.example.OtherService") in projectClasses)
        assertTrue(ClassName("java.lang.Object") !in projectClasses)
    }

    @Test
    fun `multiple methods on same class each have independent caller chains`() {
        // UserService has: buildNotificationMessage, sendResetNotification, sendDeactivationNotification,
        //                  resetPassword, deactivateUser
        // Chain: buildNotificationMessage ← sendResetNotification ← resetPassword
        //        buildNotificationMessage ← sendDeactivationNotification ← deactivateUser
        writeClassWithMultipleMethods(
            "com/example/UserService", "UserService.kt",
            listOf(
                MethodDef("buildNotificationMessage", emptyList()),
                MethodDef("sendResetNotification", listOf(Call("com/example/UserService", "buildNotificationMessage", "()V"))),
                MethodDef("sendDeactivationNotification", listOf(Call("com/example/UserService", "buildNotificationMessage", "()V"))),
                MethodDef("resetPassword", listOf(Call("com/example/UserService", "sendResetNotification", "()V"))),
                MethodDef("deactivateUser", listOf(Call("com/example/UserService", "sendDeactivationNotification", "()V"))),
            ),
        )
        writeClassWithCalls(
            "com/example/UserRoute", "UserRoute.kt",
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
        val result = CallerTreeFormatter.format(graph, listOf(MethodRef(ClassName("com.example.UserService"), "buildNotificationMessage")), maxDepth = 5)
        assertTrue(result.contains("sendResetNotification"), "Should contain sendResetNotification")
        assertTrue(result.contains("resetPassword"), "Should contain resetPassword at depth 2")
        assertTrue(result.contains("handleReset"), "Should contain handleReset at depth 3")
    }

    private data class MethodDef(val name: String, val calls: List<Call>)

    private data class Call(val owner: String, val name: String, val descriptor: String)

    private fun writeClassWithMultipleMethods(
        className: String,
        sourceFile: String,
        methods: List<MethodDef>,
    ) {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)

        val init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        init.visitCode()
        init.visitVarInsn(Opcodes.ALOAD, 0)
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        init.visitInsn(Opcodes.RETURN)
        init.visitMaxs(1, 1)
        init.visitEnd()

        for (method in methods) {
            val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, method.name, "()V", null, null)
            mv.visitCode()
            for (call in method.calls) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, call.owner, call.name, call.descriptor, false)
            }
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(1, 1)
            mv.visitEnd()
        }

        writer.visitEnd()

        val packageDir = className.substringBeforeLast("/", "")
        val simpleFileName = className.substringAfterLast("/") + ".class"
        val dir = if (packageDir.isNotEmpty()) {
            classesDir.resolve(packageDir).also { it.mkdirs() }
        } else {
            classesDir
        }
        File(dir, simpleFileName).writeBytes(writer.toByteArray())
    }

    private fun writeClassWithCalls(
        className: String,
        sourceFile: String,
        methodName: String,
        calls: List<Call>,
    ) {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)

        // Default constructor
        val init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        init.visitCode()
        init.visitVarInsn(Opcodes.ALOAD, 0)
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        init.visitInsn(Opcodes.RETURN)
        init.visitMaxs(1, 1)
        init.visitEnd()

        // Method with calls
        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()V", null, null)
        mv.visitCode()
        for (call in calls) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, call.owner, call.name, call.descriptor, false)
        }
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()

        writer.visitEnd()

        val packageDir = className.substringBeforeLast("/", "")
        val simpleFileName = className.substringAfterLast("/") + ".class"
        val dir = if (packageDir.isNotEmpty()) {
            classesDir.resolve(packageDir).also { it.mkdirs() }
        } else {
            classesDir
        }
        File(dir, simpleFileName).writeBytes(writer.toByteArray())
    }

    private fun writeEmptyClass(className: String, sourceFile: String) {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)
        writer.visitEnd()

        val packageDir = className.substringBeforeLast("/", "")
        val simpleFileName = className.substringAfterLast("/") + ".class"
        val dir = if (packageDir.isNotEmpty()) {
            classesDir.resolve(packageDir).also { it.mkdirs() }
        } else {
            classesDir
        }
        File(dir, simpleFileName).writeBytes(writer.toByteArray())
    }
}
