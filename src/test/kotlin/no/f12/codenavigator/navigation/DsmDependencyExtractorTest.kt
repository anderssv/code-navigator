package no.f12.codenavigator.navigation

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DsmDependencyExtractorTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var classesDir: File

    @BeforeEach
    fun setUp() {
        classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
    }

    @Test
    fun `empty directory produces no dependencies`() {
        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("")).data

        assertTrue(deps.isEmpty())
    }

    @Test
    fun `detects method call dependency between packages`() {
        writeClassWithCalls(
            "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/example/service/Service", "process", "()V")),
        )
        writeEmptyClass("com/example/service/Service", "Service.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.example.service.Service") }
        assertTrue(dep != null, "Expected dependency from Controller to Service")
        assertEquals(PackageName("com.example.api"), dep.sourcePackage)
        assertEquals(PackageName("com.example.service"), dep.targetPackage)
    }

    @Test
    fun `excludes same-package dependencies`() {
        writeClassWithCalls(
            "com/example/api/ControllerA", "ControllerA.kt",
            "handle", listOf(Call("com/example/api/ControllerB", "other", "()V")),
        )
        writeEmptyClass("com/example/api/ControllerB", "ControllerB.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        assertTrue(deps.isEmpty(), "Same-package deps should be excluded")
    }

    @Test
    fun `detects field type dependency`() {
        writeClassWithField(
            "com/example/api/Controller", "Controller.kt",
            "service", "Lcom/example/service/Service;",
        )
        writeEmptyClass("com/example/service/Service", "Service.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.example.service.Service") }
        assertTrue(dep != null, "Expected dependency from field type")
    }

    @Test
    fun `detects superclass dependency`() {
        writeClassWithSuperclass(
            "com/example/impl/ConcreteService", "ConcreteService.kt",
            "com/example/base/AbstractService",
        )
        writeEmptyClass("com/example/base/AbstractService", "AbstractService.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.impl.ConcreteService") && it.targetClass == ClassName("com.example.base.AbstractService") }
        assertTrue(dep != null, "Expected dependency from superclass")
    }

    @Test
    fun `detects interface implementation dependency`() {
        writeClassWithInterface(
            "com/example/impl/UserRepo", "UserRepo.kt",
            "com/example/domain/Repository",
        )
        writeEmptyClass("com/example/domain/Repository", "Repository.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.impl.UserRepo") && it.targetClass == ClassName("com.example.domain.Repository") }
        assertTrue(dep != null, "Expected dependency from interface implementation")
    }

    @Test
    fun `filters by root prefix`() {
        writeClassWithCalls(
            "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/other/lib/Helper", "help", "()V")),
        )
        writeEmptyClass("com/other/lib/Helper", "Helper.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        assertTrue(deps.isEmpty(), "Dependencies outside root prefix should be excluded")
    }

    @Test
    fun `empty root prefix includes all packages`() {
        writeClassWithCalls(
            "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/other/lib/Helper", "help", "()V")),
        )
        writeEmptyClass("com/other/lib/Helper", "Helper.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.other.lib.Helper") }
        assertTrue(dep != null, "Empty root prefix should include all packages")
    }

    @Test
    fun `strips inner class names to base class`() {
        writeClassWithCalls(
            "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/example/service/Service\$Companion", "getInstance", "()Lcom/example/service/Service;")),
        )
        writeEmptyClass("com/example/service/Service", "Service.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.example.service.Service") }
        assertTrue(dep != null, "Inner class reference should resolve to base class")
    }

    @Test
    fun `produces unique dependencies per class pair`() {
        writeClassWithCalls(
            "com/example/api/Controller", "Controller.kt",
            "handle", listOf(
                Call("com/example/service/Service", "process", "()V"),
                Call("com/example/service/Service", "validate", "()V"),
            ),
        )
        writeEmptyClass("com/example/service/Service", "Service.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val matching = deps.filter { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.example.service.Service") }
        assertEquals(1, matching.size, "Should deduplicate to one dependency per class pair")
    }

    @Test
    fun `detects dependency from field access instruction in method body`() {
        writeClassWithFieldAccess(
            "com/example/api/Controller", "Controller.kt",
            "handle",
            fieldOwner = "com/example/service/Service",
            fieldName = "instance",
            fieldDescriptor = "Lcom/example/service/Service;",
        )
        writeEmptyClass("com/example/service/Service", "Service.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.example.service.Service") }
        assertTrue(dep != null, "Expected dependency from field access in method body")
    }

    @Test
    fun `detects dependency from type instruction (NEW)`() {
        writeClassWithTypeInsn(
            "com/example/api/Controller", "Controller.kt",
            "create",
            typeOperand = "com/example/model/Entity",
        )
        writeEmptyClass("com/example/model/Entity", "Entity.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.example.model.Entity") }
        assertTrue(dep != null, "Expected dependency from NEW type instruction")
    }

    @Test
    fun `detects dependency from class literal (LDC Type)`() {
        writeClassWithLdcType(
            "com/example/api/Controller", "Controller.kt",
            "getType",
            referencedType = "com/example/model/Entity",
        )
        writeEmptyClass("com/example/model/Entity", "Entity.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.example.model.Entity") }
        assertTrue(dep != null, "Expected dependency from class literal LDC")
    }

    @Test
    fun `detects dependency through method parameter descriptor`() {
        writeClassWithMethodDescriptor(
            "com/example/api/Controller", "Controller.kt",
            "handle", "(Lcom/example/model/Request;)Lcom/example/model/Response;",
        )
        writeEmptyClass("com/example/model/Request", "Request.kt")
        writeEmptyClass("com/example/model/Response", "Response.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val request = deps.find { it.targetClass == ClassName("com.example.model.Request") }
        val response = deps.find { it.targetClass == ClassName("com.example.model.Response") }
        assertTrue(request != null, "Expected dependency from method parameter type")
        assertTrue(response != null, "Expected dependency from method return type")
    }

    @Test
    fun `detects dependency through exception declaration`() {
        writeClassWithException(
            "com/example/api/Controller", "Controller.kt",
            "handle",
            exception = "com/example/errors/AppException",
        )
        writeEmptyClass("com/example/errors/AppException", "AppException.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.targetClass == ClassName("com.example.errors.AppException") }
        assertTrue(dep != null, "Expected dependency from throws declaration")
    }

    @Test
    fun `detects dependency through array field type`() {
        writeClassWithField(
            "com/example/api/Controller", "Controller.kt",
            "services", "[Lcom/example/service/Service;",
        )
        writeEmptyClass("com/example/service/Service", "Service.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val dep = deps.find { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.example.service.Service") }
        assertTrue(dep != null, "Expected dependency from array element type")
    }

    @Test
    fun `detects dependency from method call with object return type in descriptor`() {
        writeClassWithCalls(
            "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/example/service/Service", "getModel", "()Lcom/example/model/Entity;")),
        )
        writeEmptyClass("com/example/service/Service", "Service.kt")
        writeEmptyClass("com/example/model/Entity", "Entity.kt")

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        val entityDep = deps.find { it.sourceClass == ClassName("com.example.api.Controller") && it.targetClass == ClassName("com.example.model.Entity") }
        assertTrue(entityDep != null, "Expected dependency from call descriptor return type")
    }

    @Test
    fun `extracts dependencies from real compiled Kotlin classes`() {
        val classesDir = File("test-project/build/classes/kotlin/main")
        if (!classesDir.exists()) {
            buildTestProject()
        }

        val deps = DsmDependencyExtractor.extract(listOf(classesDir), PackageName("com.example")).data

        assertTrue(deps.isNotEmpty(), "Expected inter-package dependencies from test-project, but got none")
        val packages = deps.flatMap { listOf(it.sourcePackage, it.targetPackage) }.toSet()
        assertTrue(packages.contains(PackageName("com.example.services")), "Expected com.example.services in dependencies")
        assertTrue(packages.contains(PackageName("com.example.domain")), "Expected com.example.domain in dependencies")
    }

    private fun buildTestProject() {
        val testProjectDir = File("test-project")
        val gradlew = File(testProjectDir.parentFile, "gradlew").absolutePath
        val process = ProcessBuilder(gradlew, "classes")
            .directory(testProjectDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        check(exitCode == 0) { "Failed to build test-project (exit $exitCode): $output" }
    }

    // --- helpers ---

    private data class Call(val owner: String, val name: String, val descriptor: String)

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

    private fun writeClassWithSuperclass(
        className: String,
        sourceFile: String,
        superName: String,
    ) {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, superName, null)
        writer.visitSource(sourceFile, null)
        writer.visitEnd()
        writeClassFile(className, writer)
    }

    private fun writeClassWithInterface(
        className: String,
        sourceFile: String,
        interfaceName: String,
    ) {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", arrayOf(interfaceName))
        writer.visitSource(sourceFile, null)
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

    private fun writeClassWithFieldAccess(
        className: String,
        sourceFile: String,
        methodName: String,
        fieldOwner: String,
        fieldName: String,
        fieldDescriptor: String,
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
        mv.visitFieldInsn(Opcodes.GETSTATIC, fieldOwner, fieldName, fieldDescriptor)
        mv.visitInsn(Opcodes.POP)
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
        typeOperand: String,
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
        mv.visitTypeInsn(Opcodes.NEW, typeOperand)
        mv.visitInsn(Opcodes.POP)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()

        writer.visitEnd()
        writeClassFile(className, writer)
    }

    private fun writeClassWithLdcType(
        className: String,
        sourceFile: String,
        methodName: String,
        referencedType: String,
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

        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()Ljava/lang/Class;", null, null)
        mv.visitCode()
        mv.visitLdcInsn(Type.getObjectType(referencedType))
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(1, 1)
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
        writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, descriptor, null, null)
        writer.visitEnd()
        writeClassFile(className, writer)
    }

    private fun writeClassWithException(
        className: String,
        sourceFile: String,
        methodName: String,
        exception: String,
    ) {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)
        writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()V", null, arrayOf(exception))
        writer.visitEnd()
        writeClassFile(className, writer)
    }
}
