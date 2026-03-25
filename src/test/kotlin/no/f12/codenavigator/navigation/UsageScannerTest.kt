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

class UsageScannerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var classesDir: File

    @BeforeEach
    fun setUp() {
        classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
    }

    // --- Method call usages ---

    @Test
    fun `finds method call usage by owner and method name`() {
        writeClassWithCalls(
            "com/example/Caller", "Caller.kt",
            "doWork", listOf(Call("com/example/Target", "process", "()V")),
        )
        writeEmptyClass("com/example/Target", "Target.kt")

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
            method = "process",
        ).data

        assertEquals(1, usages.size)
        assertEquals("com.example.Caller", usages[0].callerClass)
        assertEquals("doWork", usages[0].callerMethod)
        assertEquals("com.example.Target", usages[0].targetOwner)
        assertEquals("process", usages[0].targetName)
    }

    @Test
    fun `returns empty list when no usages match`() {
        writeClassWithCalls(
            "com/example/Caller", "Caller.kt",
            "doWork", listOf(Call("com/example/Target", "process", "()V")),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.NonExistent",
            method = "missing",
        ).data

        assertTrue(usages.isEmpty())
    }

    @Test
    fun `finds multiple call sites for the same target method`() {
        writeClassWithCalls(
            "com/example/CallerA", "CallerA.kt",
            "fromA", listOf(Call("com/example/Target", "process", "()V")),
        )
        writeClassWithCalls(
            "com/example/CallerB", "CallerB.kt",
            "fromB", listOf(Call("com/example/Target", "process", "()V")),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
            method = "process",
        ).data

        assertEquals(2, usages.size)
        val callerMethods = usages.map { it.callerMethod }.toSet()
        assertTrue("fromA" in callerMethods)
        assertTrue("fromB" in callerMethods)
    }

    @Test
    fun `finds all methods called on an owner when no method filter given`() {
        writeClassWithCalls(
            "com/example/Caller", "Caller.kt",
            "doWork", listOf(
                Call("com/example/Target", "process", "()V"),
                Call("com/example/Target", "validate", "(Ljava/lang/String;)Z"),
            ),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
        ).data

        assertEquals(2, usages.size)
        val targetNames = usages.map { it.targetName }.toSet()
        assertTrue("process" in targetNames)
        assertTrue("validate" in targetNames)
    }

    // --- Field access usages ---

    @Test
    fun `finds field read (GETFIELD) usage`() {
        writeClassWithFieldAccess(
            "com/example/Caller", "Caller.kt",
            "doWork",
            FieldAccess("com/example/Target", "name", "Ljava/lang/String;", Opcodes.GETFIELD),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
            method = "name",
        ).data

        assertEquals(1, usages.size)
        assertEquals("com.example.Caller", usages[0].callerClass)
        assertEquals("doWork", usages[0].callerMethod)
        assertEquals("com.example.Target", usages[0].targetOwner)
        assertEquals("name", usages[0].targetName)
        assertEquals(UsageKind.FIELD_ACCESS, usages[0].kind)
    }

    @Test
    fun `finds static field access (GETSTATIC) usage`() {
        writeClassWithFieldAccess(
            "com/example/Caller", "Caller.kt",
            "doWork",
            FieldAccess("com/example/Constants", "MAX_SIZE", "I", Opcodes.GETSTATIC),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Constants",
            method = "MAX_SIZE",
        ).data

        assertEquals(1, usages.size)
        assertEquals(UsageKind.FIELD_ACCESS, usages[0].kind)
        assertEquals("MAX_SIZE", usages[0].targetName)
    }

    // --- Type references ---

    @Test
    fun `finds type used in NEW instruction`() {
        writeClassWithTypeInsn(
            "com/example/Caller", "Caller.kt",
            "doWork", Opcodes.NEW, "com/example/Target",
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            type = "com.example.Target",
        ).data

        assertEquals(1, usages.size)
        assertEquals("com.example.Target", usages[0].targetOwner)
        assertEquals(UsageKind.TYPE_REFERENCE, usages[0].kind)
    }

    @Test
    fun `finds type used in CHECKCAST instruction`() {
        writeClassWithTypeInsn(
            "com/example/Caller", "Caller.kt",
            "doWork", Opcodes.CHECKCAST, "com/example/Target",
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            type = "com.example.Target",
        ).data

        assertEquals(1, usages.size)
        assertEquals(UsageKind.TYPE_REFERENCE, usages[0].kind)
    }

    @Test
    fun `finds type used as method parameter in descriptor`() {
        writeClassWithMethodDescriptor(
            "com/example/Caller", "Caller.kt",
            "doWork", "(Lcom/example/Target;)V",
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            type = "com.example.Target",
        ).data

        assertEquals(1, usages.size)
        assertEquals("com.example.Caller", usages[0].callerClass)
        assertEquals("doWork", usages[0].callerMethod)
        assertEquals(UsageKind.TYPE_REFERENCE, usages[0].kind)
    }

    @Test
    fun `finds type used as method return type in descriptor`() {
        writeClassWithMethodDescriptor(
            "com/example/Caller", "Caller.kt",
            "getTarget", "()Lcom/example/Target;",
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            type = "com.example.Target",
        ).data

        assertEquals(1, usages.size)
        assertEquals("getTarget", usages[0].callerMethod)
        assertEquals(UsageKind.TYPE_REFERENCE, usages[0].kind)
    }

    @Test
    fun `finds type used as field declaration type`() {
        writeClassWithField(
            "com/example/Caller", "Caller.kt",
            "target", "Lcom/example/Target;",
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            type = "com.example.Target",
        ).data

        assertEquals(1, usages.size)
        assertEquals("com.example.Caller", usages[0].callerClass)
        assertEquals("target", usages[0].targetName)
        assertEquals(UsageKind.TYPE_REFERENCE, usages[0].kind)
    }

    // --- Type parameter also matches method call/field owners ---

    // [TEST] -Ptype finds method calls where owner matches the type (INVOKESTATIC on file-facade class)
    // [TEST] -Ptype finds field access where owner matches the type
    // [TEST] -Ptype finds both method calls AND type references for same type (comprehensive)
    // [TEST] -Ptype with method filter narrows method call results by method name

    @Test
    fun `type parameter with method filter narrows to specific method`() {
        writeClassWithCalls(
            "com/example/Caller", "Caller.kt",
            "doWork", listOf(
                Call("com/example/Target", "process", "()V"),
                Call("com/example/Target", "validate", "(Ljava/lang/String;)Z"),
            ),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            type = "com.example.Target",
            method = "process",
        ).data

        val methodCalls = usages.filter { it.kind == UsageKind.METHOD_CALL }
        assertEquals(1, methodCalls.size)
        assertEquals("process", methodCalls[0].targetName)
    }

    @Test
    fun `type parameter finds method calls and type references for same type`() {
        writeClassWithStaticCall(
            "com/example/Caller", "Caller.kt",
            "doWork", Call("com/example/Target", "process", "()V"),
        )
        writeClassWithTypeInsn(
            "com/example/Creator", "Creator.kt",
            "create", Opcodes.NEW, "com/example/Target",
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            type = "com.example.Target",
        ).data

        val kinds = usages.map { it.kind }.toSet()
        assertTrue(UsageKind.METHOD_CALL in kinds, "Expected METHOD_CALL in results")
        assertTrue(UsageKind.TYPE_REFERENCE in kinds, "Expected TYPE_REFERENCE in results")
    }

    @Test
    fun `type parameter finds field access where owner matches the type`() {
        writeClassWithFieldAccess(
            "com/example/Caller", "Caller.kt",
            "doWork",
            FieldAccess("com/example/Constants", "MAX_SIZE", "I", Opcodes.GETSTATIC),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            type = "com.example.Constants",
        ).data

        val fieldAccesses = usages.filter { it.kind == UsageKind.FIELD_ACCESS }
        assertEquals(1, fieldAccesses.size)
        assertEquals("com.example.Constants", fieldAccesses[0].targetOwner)
        assertEquals("MAX_SIZE", fieldAccesses[0].targetName)
    }

    @Test
    fun `type parameter finds method calls where owner matches the type`() {
        writeClassWithStaticCall(
            "com/example/Caller", "Caller.kt",
            "doWork", Call("com/example/ContextKt", "locateResourceFile", "(Ljava/lang/String;)Ljava/lang/String;"),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            type = "com.example.ContextKt",
        ).data

        val methodCalls = usages.filter { it.kind == UsageKind.METHOD_CALL }
        assertEquals(1, methodCalls.size)
        assertEquals("com.example.Caller", methodCalls[0].callerClass)
        assertEquals("doWork", methodCalls[0].callerMethod)
        assertEquals("com.example.ContextKt", methodCalls[0].targetOwner)
        assertEquals("locateResourceFile", methodCalls[0].targetName)
    }

    // --- Filtering ---

    @Test
    fun `filters usages to project classes only`() {
        writeClassWithCalls(
            "com/example/ProjectCaller", "ProjectCaller.kt",
            "doWork", listOf(Call("com/external/Library", "process", "()V")),
        )
        writeClassWithCalls(
            "com/external/OtherCaller", "OtherCaller.kt",
            "doOther", listOf(Call("com/external/Library", "process", "()V")),
        )

        val allUsages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.external.Library",
            method = "process",
        ).data

        assertEquals(2, allUsages.size)

        val projectSources = setOf("com.example.ProjectCaller")
        val projectOnly = allUsages.filter { it.callerClass in projectSources }
        assertEquals(1, projectOnly.size)
        assertEquals("com.example.ProjectCaller", projectOnly[0].callerClass)
    }

    @Test
    fun `ownerClass filter matches case-insensitively`() {
        writeClassWithCalls(
            "com/example/Caller", "Caller.kt",
            "doWork", listOf(Call("com/example/Target", "process", "()V")),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "COM.EXAMPLE.TARGET",
            method = "process",
        ).data

        assertEquals(1, usages.size)
    }

    @Test
    fun `method filter matches exact bytecode method name`() {
        writeClassWithCalls(
            "com/example/Caller", "Caller.kt",
            "doWork", listOf(
                Call("com/example/Target", "process", "()V"),
                Call("com/example/Target", "processAll", "()V"),
            ),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
            method = "process",
        ).data

        assertEquals(1, usages.size)
        assertEquals("process", usages[0].targetName)
    }

    // --- Edge cases ---

    @Test
    fun `empty class directory produces no usages`() {
        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
        ).data

        assertTrue(usages.isEmpty())
    }

    @Test
    fun `includes descriptor in method usage results`() {
        writeClassWithCalls(
            "com/example/Caller", "Caller.kt",
            "doWork", listOf(Call("com/example/Target", "process", "(Ljava/lang/String;)I")),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
            method = "process",
        ).data

        assertEquals(1, usages.size)
        assertEquals("(Ljava/lang/String;)I", usages[0].targetDescriptor)
        assertEquals(UsageKind.METHOD_CALL, usages[0].kind)
    }

    @Test
    fun `tracks source file for caller class`() {
        writeClassWithCalls(
            "com/example/MyService", "MyService.kt",
            "doWork", listOf(Call("com/example/Target", "process", "()V")),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
            method = "process",
        ).data

        assertEquals(1, usages.size)
        assertEquals("MyService.kt", usages[0].sourceFile)
    }

    // --- Deduplication ---

    @Test
    fun `deduplicates when same method is called multiple times from same caller`() {
        writeClassWithCalls(
            "com/example/Caller", "Caller.kt",
            "doWork", listOf(
                Call("com/example/Target", "process", "()V"),
                Call("com/example/Target", "process", "()V"),
                Call("com/example/Target", "process", "()V"),
            ),
        )

        val usages = UsageScanner.scan(
            listOf(classesDir),
            ownerClass = "com.example.Target",
            method = "process",
        ).data

        assertEquals(1, usages.size, "Duplicate calls from same caller should be deduplicated")
    }

    // --- helpers ---

    private data class Call(val owner: String, val name: String, val descriptor: String)
    private data class FieldAccess(val owner: String, val name: String, val descriptor: String, val opcode: Int)

    private fun writeClassWithCalls(
        className: String,
        sourceFile: String,
        methodName: String,
        calls: List<Call>,
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

        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()V", null, null)
        mv.visitCode()
        for (call in calls) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, call.owner, call.name, call.descriptor, false)
        }
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()

        writer.visitEnd()
        writeClassFile(className, writer)
    }

    private fun writeClassWithTypeInsn(
        className: String,
        sourceFile: String,
        methodName: String,
        opcode: Int,
        typeRef: String,
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

        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()V", null, null)
        mv.visitCode()
        if (opcode == Opcodes.CHECKCAST || opcode == Opcodes.INSTANCEOF) {
            mv.visitInsn(Opcodes.ACONST_NULL)
        }
        mv.visitTypeInsn(opcode, typeRef)
        if (opcode == Opcodes.NEW || opcode == Opcodes.CHECKCAST) {
            mv.visitInsn(Opcodes.POP)
        } else if (opcode == Opcodes.INSTANCEOF) {
            mv.visitInsn(Opcodes.POP)
        }
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(2, 2)
        mv.visitEnd()

        writer.visitEnd()
        writeClassFile(className, writer)
    }

    private fun writeClassWithMethodDescriptor(
        className: String,
        sourceFile: String,
        methodName: String,
        descriptor: String,
    ) {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)

        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, descriptor, null, null)
        mv.visitCode()
        mv.visitInsn(Opcodes.ACONST_NULL)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(1, 2)
        mv.visitEnd()

        writer.visitEnd()
        writeClassFile(className, writer)
    }

    private fun writeClassWithField(
        className: String,
        sourceFile: String,
        fieldName: String,
        fieldDescriptor: String,
    ) {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)
        writer.visitField(Opcodes.ACC_PRIVATE, fieldName, fieldDescriptor, null, null)
        writer.visitEnd()
        writeClassFile(className, writer)
    }

    private fun writeClassWithFieldAccess(
        className: String,
        sourceFile: String,
        methodName: String,
        fieldAccess: FieldAccess,
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

        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()V", null, null)
        mv.visitCode()
        if (fieldAccess.opcode == Opcodes.GETFIELD || fieldAccess.opcode == Opcodes.PUTFIELD) {
            mv.visitVarInsn(Opcodes.ALOAD, 0)
        }
        mv.visitFieldInsn(fieldAccess.opcode, fieldAccess.owner, fieldAccess.name, fieldAccess.descriptor)
        if (fieldAccess.opcode == Opcodes.GETFIELD || fieldAccess.opcode == Opcodes.GETSTATIC) {
            mv.visitInsn(Opcodes.POP)
        }
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(2, 2)
        mv.visitEnd()

        writer.visitEnd()
        writeClassFile(className, writer)
    }

    private fun writeClassWithStaticCall(
        className: String,
        sourceFile: String,
        methodName: String,
        call: Call,
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

        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()V", null, null)
        mv.visitCode()
        mv.visitInsn(Opcodes.ACONST_NULL)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, call.owner, call.name, call.descriptor, false)
        mv.visitInsn(Opcodes.POP)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(2, 2)
        mv.visitEnd()

        writer.visitEnd()
        writeClassFile(className, writer)
    }

    private fun writeEmptyClass(className: String, sourceFile: String) {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)
        writer.visitEnd()
        writeClassFile(className, writer)
    }

    private fun writeClassFile(className: String, writer: ClassWriter) {
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
