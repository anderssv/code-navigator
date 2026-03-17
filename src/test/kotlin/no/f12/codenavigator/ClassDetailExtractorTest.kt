package no.f12.codenavigator

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassDetailExtractorTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `extracts class name and source file`() {
        val classFile = writeClassFile("com/example/MyService", "MyService.kt")

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals("com.example.MyService", detail.className)
        assertEquals("MyService.kt", detail.sourceFile)
    }

    // [TEST-DONE] Extracts class name and source file
    @Test
    fun `extracts superclass name when not Object`() {
        val classFile = writeClassFile(
            "com/example/AdminService",
            "AdminService.kt",
            superName = "com/example/BaseService",
        )

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals("com.example.BaseService", detail.superClass)
    }

    // [TEST-DONE] Extracts superclass name (non-Object)
    @Test
    fun `extracts implemented interfaces`() {
        val classFile = writeClassFile(
            "com/example/MyRepo",
            "MyRepo.kt",
            interfaces = arrayOf("com/example/Repository", "java/io/Serializable"),
        )

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(listOf("com.example.Repository", "java.io.Serializable"), detail.interfaces)
    }

    // [TEST-DONE] Extracts implemented interfaces
    @Test
    fun `extracts public methods with parameter types and return type`() {
        val classFile = writeClassFile("com/example/Service", "Service.kt") {
            visitMethod(
                Opcodes.ACC_PUBLIC,
                "findUser",
                "(Ljava/lang/String;I)Lcom/example/User;",
                null,
                null,
            )
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(1, detail.methods.size)
        val method = detail.methods.first()
        assertEquals("findUser", method.name)
        assertEquals(listOf("String", "int"), method.parameterTypes)
        assertEquals("User", method.returnType)
    }

    // [TEST-DONE] Extracts public methods with parameter types and return type
    @Test
    fun `extracts public fields with type`() {
        val classFile = writeClassFile("com/example/Config", "Config.kt") {
            visitField(Opcodes.ACC_PUBLIC, "name", "Ljava/lang/String;", null, null)
            visitField(Opcodes.ACC_PUBLIC, "count", "I", null, null)
            visitField(Opcodes.ACC_PUBLIC, "items", "[Ljava/lang/String;", null, null)
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(3, detail.fields.size)
        assertEquals(FieldDetail("name", "String"), detail.fields[0])
        assertEquals(FieldDetail("count", "int"), detail.fields[1])
        assertEquals(FieldDetail("items", "String[]"), detail.fields[2])
    }

    // [TEST-DONE] Extracts public fields with type
    @Test
    fun `filters out constructors and synthetic methods`() {
        val classFile = writeClassFile("com/example/Foo", "Foo.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
            visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null)
            visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_SYNTHETIC, "bridge\$method", "()V", null, null)
            visitMethod(Opcodes.ACC_STATIC or Opcodes.ACC_SYNTHETIC, "access\$getName", "()Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(1, detail.methods.size)
        assertEquals("doWork", detail.methods.first().name)
    }

    // [TEST-DONE] Filters out constructors and synthetic methods
    @Test
    fun `filters out Kotlin property accessors for fields`() {
        val classFile = writeClassFile("com/example/User", "User.kt") {
            visitField(Opcodes.ACC_PRIVATE, "name", "Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "getName", "()Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "setName", "(Ljava/lang/String;)V", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "doSomething", "()V", null, null)
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(listOf("name"), detail.fields.map { it.name })
        assertEquals(listOf("doSomething"), detail.methods.map { it.name })
    }

    // [TEST-DONE] Filters out Kotlin property accessors for fields
    @Test
    fun `filters out data class generated methods`() {
        val classFile = writeClassFile("com/example/Data", "Data.kt") {
            visitField(Opcodes.ACC_PRIVATE, "value", "Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "component1", "()Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "copy", "(Ljava/lang/String;)Lcom/example/Data;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "copy\$default", "(Lcom/example/Data;Ljava/lang/String;ILjava/lang/Object;)Lcom/example/Data;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "toString", "()Ljava/lang/String;", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "hashCode", "()I", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "equals", "(Ljava/lang/Object;)Z", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "transform", "()V", null, null)
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(listOf("transform"), detail.methods.map { it.name })
    }

    // [TEST-DONE] Filters out data class generated methods
    @Test
    fun `default superclass Object is shown as null`() {
        val classFile = writeClassFile("com/example/Simple", "Simple.kt")

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(null, detail.superClass)
    }

    // [TEST-DONE] Default superclass is Object (shown as empty/null)

    @Test
    fun `class with no interfaces has empty list`() {
        val classFile = writeClassFile("com/example/Plain", "Plain.kt")

        val detail = ClassDetailExtractor.extract(classFile)

        assertTrue(detail.interfaces.isEmpty())
    }

    // [TEST-DONE] Class with no interfaces has empty list

    private fun writeClassFile(
        className: String,
        sourceFile: String?,
        superName: String = "java/lang/Object",
        interfaces: Array<String>? = null,
        configure: ClassWriter.() -> Unit = {},
    ): File {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, superName, interfaces)
        if (sourceFile != null) {
            writer.visitSource(sourceFile, null)
        }
        writer.configure()
        writer.visitEnd()

        val packageDir = className.substringBeforeLast("/", "")
        val simpleFileName = className.substringAfterLast("/") + ".class"
        val dir = if (packageDir.isNotEmpty()) {
            tempDir.resolve(packageDir).toFile().also { it.mkdirs() }
        } else {
            tempDir.toFile()
        }
        val file = File(dir, simpleFileName)
        file.writeBytes(writer.toByteArray())
        return file
    }
}
