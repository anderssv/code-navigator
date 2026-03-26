package no.f12.codenavigator.navigation

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
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/MyService", "MyService.kt")

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals("com.example.MyService", detail.className.value)
        assertEquals("MyService.kt", detail.sourceFile)
    }

    // [TEST-DONE] Extracts class name and source file
    @Test
    fun `extracts superclass name when not Object`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/AdminService", "AdminService.kt",
            superName = "com/example/BaseService",
        )

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals("com.example.BaseService", detail.superClass?.value)
    }

    // [TEST-DONE] Extracts superclass name (non-Object)
    @Test
    fun `extracts implemented interfaces`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/MyRepo", "MyRepo.kt",
            interfaces = arrayOf("com/example/Repository", "java/io/Serializable"),
        )

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(listOf("com.example.Repository", "java.io.Serializable"), detail.interfaces.map { it.value })
    }

    // [TEST-DONE] Extracts implemented interfaces
    @Test
    fun `extracts public methods with parameter types and return type`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Service", "Service.kt") {
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
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Config", "Config.kt") {
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
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Foo", "Foo.kt") {
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
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/User", "User.kt") {
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
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Data", "Data.kt") {
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
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Simple", "Simple.kt")

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(null, detail.superClass)
    }

    // [TEST-DONE] Default superclass is Object (shown as empty/null)

    @Test
    fun `class with no interfaces has empty list`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Plain", "Plain.kt")

        val detail = ClassDetailExtractor.extract(classFile)

        assertTrue(detail.interfaces.isEmpty())
    }

    // [TEST-DONE] Class with no interfaces has empty list

    @Test
    fun `INSTANCE field is excluded from fields`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Singleton", "Singleton.kt") {
            visitField(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                "INSTANCE",
                "Lcom/example/Singleton;",
                null,
                null,
            )
            visitField(Opcodes.ACC_PUBLIC, "name", "Ljava/lang/String;", null, null)
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(listOf("name"), detail.fields.map { it.name })
    }

    @Test
    fun `is-prefix accessor for boolean field is filtered from methods`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Flags", "Flags.kt") {
            visitField(Opcodes.ACC_PRIVATE, "active", "Z", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "isActive", "()Z", null, null)
            visitMethod(Opcodes.ACC_PUBLIC, "toggle", "()V", null, null)
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(listOf("active"), detail.fields.map { it.name })
        assertEquals(listOf("toggle"), detail.methods.map { it.name })
    }

    @Test
    fun `simplifyType handles all primitive types`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/Primitives", "Primitives.kt") {
            visitField(Opcodes.ACC_PUBLIC, "flag", "Z", null, null)
            visitField(Opcodes.ACC_PUBLIC, "letter", "C", null, null)
            visitField(Opcodes.ACC_PUBLIC, "tiny", "B", null, null)
            visitField(Opcodes.ACC_PUBLIC, "small", "S", null, null)
            visitField(Opcodes.ACC_PUBLIC, "ratio", "F", null, null)
            visitField(Opcodes.ACC_PUBLIC, "big", "J", null, null)
            visitField(Opcodes.ACC_PUBLIC, "precise", "D", null, null)
        }

        val detail = ClassDetailExtractor.extract(classFile)

        val fieldsByName = detail.fields.associateBy { it.name }
        assertEquals("boolean", fieldsByName["flag"]?.type)
        assertEquals("char", fieldsByName["letter"]?.type)
        assertEquals("byte", fieldsByName["tiny"]?.type)
        assertEquals("short", fieldsByName["small"]?.type)
        assertEquals("float", fieldsByName["ratio"]?.type)
        assertEquals("long", fieldsByName["big"]?.type)
        assertEquals("double", fieldsByName["precise"]?.type)
    }

    @Test
    fun `sourceFile defaults to unknown when no source is provided`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/NoSource", null)

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals("<unknown>", detail.sourceFile)
    }

    @Test
    fun `synthetic fields are excluded`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/WithSynthetic", "WithSynthetic.kt") {
            visitField(Opcodes.ACC_SYNTHETIC, "\$\$delegatedProperties", "[Ljava/lang/Object;", null, null)
            visitField(Opcodes.ACC_PUBLIC, "realField", "Ljava/lang/String;", null, null)
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(listOf("realField"), detail.fields.map { it.name })
    }

    @Test
    fun `method with return type using array of primitives`() {
        val classFile = TestClassWriter.writeClassFile(tempDir.toFile(), "com/example/ArrayMethods", "ArrayMethods.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "getIds", "()[I", null, null)
        }

        val detail = ClassDetailExtractor.extract(classFile)

        assertEquals(1, detail.methods.size)
        assertEquals("int[]", detail.methods.first().returnType)
    }
}
