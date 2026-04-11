package no.f12.codenavigator.navigation

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File

/**
 * Shared test utility for generating synthetic `.class` files using ASM.
 *
 * Provides a common base with sensible defaults and a `configure` lambda for local variations.
 * Each test adds only the ASM instructions it needs for its specific scenario.
 *
 * ## Usage patterns
 *
 * **Simple empty class:**
 * ```
 * TestClassWriter.writeClassFile(dir, "com/example/Foo", "Foo.kt")
 * ```
 *
 * **Class with custom fields/methods (lambda variation):**
 * ```
 * TestClassWriter.writeClassFile(dir, "com/example/Foo", "Foo.kt") {
 *     visitField(Opcodes.ACC_PUBLIC, "name", "Ljava/lang/String;", null, null)
 *     visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)
 * }
 * ```
 *
 * **Class with method calls (common scenario):**
 * ```
 * TestClassWriter.writeClassWithCalls(dir, "com/example/Caller", "Caller.kt", "doWork",
 *     listOf(Call("com/example/Target", "process", "()V")))
 * ```
 */
object TestClassWriter {

    /**
     * Generates a `.class` file with sensible defaults and an optional configuration lambda
     * for adding fields, methods, or other class members.
     *
     * @param targetDir directory to write the class file into (package subdirs are created automatically)
     * @param className internal name using `/` separators (e.g. `"com/example/MyService"`)
     * @param sourceFile source file name (e.g. `"MyService.kt"`), or `null` to omit
     * @param superName internal name of superclass, defaults to `"java/lang/Object"`
     * @param interfaces internal names of implemented interfaces, or `null`
     * @param access class access flags, defaults to `ACC_PUBLIC`
     * @param classWriterFlags flags for [ClassWriter] constructor, defaults to `0`
     * @param configure lambda to add fields, methods, or other members to the [ClassWriter]
     * @return the written [File]
     */
    fun writeClassFile(
        targetDir: File,
        className: String,
        sourceFile: String?,
        superName: String = "java/lang/Object",
        interfaces: Array<String>? = null,
        access: Int = Opcodes.ACC_PUBLIC,
        classWriterFlags: Int = 0,
        configure: ClassWriter.() -> Unit = {},
    ): File {
        val writer = ClassWriter(classWriterFlags)
        writer.visit(Opcodes.V17, access, className, null, superName, interfaces)
        if (sourceFile != null) {
            writer.visitSource(sourceFile, null)
        }
        writer.configure()
        writer.visitEnd()
        return writeBytes(targetDir, className, writer)
    }

    /**
     * Generates a class with a default constructor and one method that calls the given targets.
     * This is the most common pattern in call-graph and usage tests.
     */
    fun writeClassWithCalls(
        targetDir: File,
        className: String,
        sourceFile: String,
        methodName: String,
        calls: List<Call>,
    ): File {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)
        writeDefaultConstructor(writer)

        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()V", null, null)
        mv.visitCode()
        for (call in calls) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, call.owner, call.name, call.descriptor, false)
        }
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()

        writer.visitEnd()
        return writeBytes(targetDir, className, writer)
    }

    /**
     * Generates a class with a default constructor and one method that makes a static call.
     */
    fun writeClassWithStaticCall(
        targetDir: File,
        className: String,
        sourceFile: String,
        methodName: String,
        call: Call,
    ): File {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)
        writeDefaultConstructor(writer)

        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()V", null, null)
        mv.visitCode()
        mv.visitInsn(Opcodes.ACONST_NULL)
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, call.owner, call.name, call.descriptor, false)
        mv.visitInsn(Opcodes.POP)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(2, 2)
        mv.visitEnd()

        writer.visitEnd()
        return writeBytes(targetDir, className, writer)
    }

    /**
     * Generates a class with a default constructor and multiple named methods, each with its own call list.
     */
    fun writeClassWithMultipleMethods(
        targetDir: File,
        className: String,
        sourceFile: String,
        methods: List<MethodDef>,
    ): File {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)
        writeDefaultConstructor(writer)

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
        return writeBytes(targetDir, className, writer)
    }

    /**
     * Generates a class with methods that have line number tables.
     */
    fun writeClassWithLineNumbers(
        targetDir: File,
        className: String,
        sourceFile: String,
        methods: List<MethodWithLines>,
    ): File {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)
        writeDefaultConstructor(writer)

        for (method in methods) {
            val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, method.name, "()V", null, null)
            mv.visitCode()
            val startLabel = Label()
            mv.visitLabel(startLabel)
            mv.visitLineNumber(method.lineNumber, startLabel)
            for (call in method.calls) {
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, call.owner, call.name, call.descriptor, false)
            }
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(1, 1)
            mv.visitEnd()
        }

        writer.visitEnd()
        return writeBytes(targetDir, className, writer)
    }

    /**
     * Generates a class with a method that accesses a field (GETFIELD/GETSTATIC/PUTFIELD/PUTSTATIC),
     * optionally followed by method calls.
     */
    fun writeClassWithFieldAccessAndCalls(
        targetDir: File,
        className: String,
        sourceFile: String,
        methodName: String,
        fieldAccess: FieldAccess,
        calls: List<Call> = emptyList(),
    ): File {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)
        writeDefaultConstructor(writer)

        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()V", null, null)
        mv.visitCode()
        if (fieldAccess.opcode == Opcodes.GETFIELD || fieldAccess.opcode == Opcodes.PUTFIELD) {
            mv.visitVarInsn(Opcodes.ALOAD, 0)
        }
        mv.visitFieldInsn(fieldAccess.opcode, fieldAccess.owner, fieldAccess.name, fieldAccess.descriptor)
        if (fieldAccess.opcode == Opcodes.GETFIELD || fieldAccess.opcode == Opcodes.GETSTATIC) {
            mv.visitInsn(Opcodes.POP)
        }
        for (call in calls) {
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, call.owner, call.name, call.descriptor, false)
        }
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(2, 2)
        mv.visitEnd()

        writer.visitEnd()
        return writeBytes(targetDir, className, writer)
    }

    /**
     * Generates a class with a method containing a type instruction (NEW, CHECKCAST, INSTANCEOF).
     */
    fun writeClassWithTypeInsn(
        targetDir: File,
        className: String,
        sourceFile: String,
        methodName: String,
        opcode: Int,
        typeOperand: String,
    ): File {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)
        writeDefaultConstructor(writer)

        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()V", null, null)
        mv.visitCode()
        if (opcode == Opcodes.CHECKCAST || opcode == Opcodes.INSTANCEOF) {
            mv.visitInsn(Opcodes.ACONST_NULL)
        }
        mv.visitTypeInsn(opcode, typeOperand)
        if (opcode == Opcodes.NEW || opcode == Opcodes.CHECKCAST) {
            mv.visitInsn(Opcodes.POP)
        } else if (opcode == Opcodes.INSTANCEOF) {
            mv.visitInsn(Opcodes.POP)
        }
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(2, 2)
        mv.visitEnd()

        writer.visitEnd()
        return writeBytes(targetDir, className, writer)
    }

    /**
     * Generates a class with a method that loads a class literal via LDC.
     */
    fun writeClassWithLdcType(
        targetDir: File,
        className: String,
        sourceFile: String,
        methodName: String,
        referencedType: String,
    ): File {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)
        writeDefaultConstructor(writer)

        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()Ljava/lang/Class;", null, null)
        mv.visitCode()
        mv.visitLdcInsn(Type.getObjectType(referencedType))
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()

        writer.visitEnd()
        return writeBytes(targetDir, className, writer)
    }

    /**
     * Generates a class with a single method that has the given descriptor (no body).
     * Useful for testing method parameter/return type extraction.
     */
    fun writeClassWithMethodDescriptor(
        targetDir: File,
        className: String,
        sourceFile: String,
        methodName: String,
        descriptor: String,
    ): File {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)
        writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, descriptor, null, null)
        writer.visitEnd()
        return writeBytes(targetDir, className, writer)
    }

    /**
     * Generates a class with a single method that declares a throws clause.
     */
    fun writeClassWithException(
        targetDir: File,
        className: String,
        sourceFile: String,
        methodName: String,
        exception: String,
    ): File {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)
        writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()V", null, arrayOf(exception))
        writer.visitEnd()
        return writeBytes(targetDir, className, writer)
    }

    /**
     * Generates a class that models Kotlin's lambda compilation pattern:
     * a method uses INVOKEDYNAMIC to reference a lambda method (on the same or different class),
     * and that lambda method makes regular method calls.
     *
     * This models the bytecode for code like:
     * ```kotlin
     * fun getPoll(id: UUID): Poll =
     *     transaction { rowToPoll(query(id).single()) }
     * ```
     * where the Kotlin compiler generates:
     * 1. `getPoll` with an INVOKEDYNAMIC referencing `getPoll$lambda$0`
     * 2. `getPoll$lambda$0` with a regular INVOKEVIRTUAL to `rowToPoll`
     */
    fun writeClassWithLambdaCall(
        targetDir: File,
        className: String,
        sourceFile: String,
        callerMethod: String,
        lambdaMethodName: String,
        lambdaTargetCalls: List<Call>,
    ): File {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)
        writeDefaultConstructor(writer)

        // The main method uses INVOKEDYNAMIC to reference the lambda method
        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, callerMethod, "()V", null, null)
        mv.visitCode()

        val bootstrapHandle = Handle(
            Opcodes.H_INVOKESTATIC,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            false,
        )
        val lambdaHandle = Handle(
            Opcodes.H_INVOKESTATIC,
            className,
            lambdaMethodName,
            "()V",
            false,
        )
        mv.visitInvokeDynamicInsn(
            "invoke",
            "()Ljava/lang/Runnable;",
            bootstrapHandle,
            Type.getType("()V"),
            lambdaHandle,
            Type.getType("()V"),
        )
        mv.visitInsn(Opcodes.POP)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()

        // The lambda method makes regular calls
        val lambdaMv = writer.visitMethod(
            Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC,
            lambdaMethodName, "()V", null, null,
        )
        lambdaMv.visitCode()
        for (call in lambdaTargetCalls) {
            lambdaMv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, call.owner, call.name, call.descriptor, false)
        }
        lambdaMv.visitInsn(Opcodes.RETURN)
        lambdaMv.visitMaxs(1, 1)
        lambdaMv.visitEnd()

        writer.visitEnd()
        return writeBytes(targetDir, className, writer)
    }

    /**
     * Generates a class with a method that uses INVOKEDYNAMIC to directly reference a method
     * on another class (modeling Kotlin's `::methodRef` syntax or a direct method reference).
     *
     * Unlike [writeClassWithLambdaCall], there is no intermediate lambda method —
     * the INVOKEDYNAMIC bootstrap arg directly points to the target class and method.
     *
     * This models bytecode for code like:
     * ```kotlin
     * val ref = items.map(Service::process)
     * ```
     */
    fun writeClassWithMethodRef(
        targetDir: File,
        className: String,
        sourceFile: String,
        callerMethod: String,
        targetClass: String,
        targetMethod: String,
        targetDescriptor: String = "()V",
    ): File {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)
        writeDefaultConstructor(writer)

        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, callerMethod, "()V", null, null)
        mv.visitCode()

        val bootstrapHandle = Handle(
            Opcodes.H_INVOKESTATIC,
            "java/lang/invoke/LambdaMetafactory",
            "metafactory",
            "(Ljava/lang/invoke/MethodHandles\$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
            false,
        )
        val targetHandle = Handle(
            Opcodes.H_INVOKEVIRTUAL,
            targetClass,
            targetMethod,
            targetDescriptor,
            false,
        )
        mv.visitInvokeDynamicInsn(
            "apply",
            "()Ljava/util/function/Function;",
            bootstrapHandle,
            Type.getType("(Ljava/lang/Object;)Ljava/lang/Object;"),
            targetHandle,
            Type.getType("(Ljava/lang/Object;)Ljava/lang/Object;"),
        )
        mv.visitInsn(Opcodes.POP)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()

        writer.visitEnd()
        return writeBytes(targetDir, className, writer)
    }

    private fun writeDefaultConstructor(writer: ClassWriter) {
        val init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        init.visitCode()
        init.visitVarInsn(Opcodes.ALOAD, 0)
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        init.visitInsn(Opcodes.RETURN)
        init.visitMaxs(1, 1)
        init.visitEnd()
    }

    private fun writeBytes(targetDir: File, className: String, writer: ClassWriter): File {
        val packageDir = className.substringBeforeLast("/", "")
        val simpleFileName = className.substringAfterLast("/") + ".class"
        val dir = if (packageDir.isNotEmpty()) {
            targetDir.resolve(packageDir).also { it.mkdirs() }
        } else {
            targetDir
        }
        val file = File(dir, simpleFileName)
        file.writeBytes(writer.toByteArray())
        return file
    }
}

/** A method call target: owner class (internal name), method name, descriptor. */
data class Call(val owner: String, val name: String, val descriptor: String)

/** A method definition with its outgoing calls. */
data class MethodDef(val name: String, val calls: List<Call>)

/** A method definition with a line number and optional outgoing calls. */
data class MethodWithLines(val name: String, val lineNumber: Int, val calls: List<Call>)

/** A field access instruction: owner, field name, descriptor, opcode (GETFIELD/GETSTATIC/PUTFIELD/PUTSTATIC). */
data class FieldAccess(val owner: String, val name: String, val descriptor: String, val opcode: Int)
