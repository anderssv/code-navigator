package no.f12.codenavigator.navigation.deadcode

import no.f12.codenavigator.navigation.*

import no.f12.codenavigator.navigation.types.ClassName
import org.objectweb.asm.Opcodes
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FieldExtractorTest {

    @Test
    fun `extracts declared field names for a class`() {
        val dir = createTempDirectory("field-extractor-test").toFile()
        TestClassWriter.writeClassFile(dir, "com/example/PaymentProcessor", "PaymentProcessor.kt") {
            visitField(Opcodes.ACC_PRIVATE, "gateway", "Ljava/lang/String;", null, null)
            visitField(Opcodes.ACC_PRIVATE, "amount", "D", null, null)
        }

        val result = FieldExtractor.scanAll(listOf(dir))

        assertEquals(setOf("gateway", "amount"), result[ClassName("com.example.PaymentProcessor")])

        dir.deleteRecursively()
    }

    @Test
    fun `excludes the Kotlin singleton INSTANCE field`() {
        val dir = createTempDirectory("field-extractor-test").toFile()
        TestClassWriter.writeClassFile(dir, "com/example/Config", "Config.kt") {
            visitField(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "INSTANCE", "Lcom/example/Config;", null, null)
            visitField(Opcodes.ACC_PRIVATE, "value", "I", null, null)
        }

        val result = FieldExtractor.scanAll(listOf(dir))

        assertEquals(setOf("value"), result[ClassName("com.example.Config")])

        dir.deleteRecursively()
    }

    @Test
    fun `class with no fields is absent from the result map`() {
        val dir = createTempDirectory("field-extractor-test").toFile()
        TestClassWriter.writeClassFile(dir, "com/example/PlainService", "PlainService.kt")

        val result = FieldExtractor.scanAll(listOf(dir))

        assertTrue(ClassName("com.example.PlainService") !in result)

        dir.deleteRecursively()
    }

    @Test
    fun `scans multiple classes independently`() {
        val dir = createTempDirectory("field-extractor-test").toFile()
        TestClassWriter.writeClassFile(dir, "com/example/A", "A.kt") {
            visitField(Opcodes.ACC_PRIVATE, "fieldA", "I", null, null)
        }
        TestClassWriter.writeClassFile(dir, "com/example/B", "B.kt") {
            visitField(Opcodes.ACC_PRIVATE, "fieldB", "I", null, null)
        }

        val result = FieldExtractor.scanAll(listOf(dir))

        assertEquals(setOf("fieldA"), result[ClassName("com.example.A")])
        assertEquals(setOf("fieldB"), result[ClassName("com.example.B")])

        dir.deleteRecursively()
    }

    @Test
    fun `scans across multiple class directories`() {
        val dir1 = createTempDirectory("field-extractor-test-1").toFile()
        val dir2 = createTempDirectory("field-extractor-test-2").toFile()
        TestClassWriter.writeClassFile(dir1, "com/example/A", "A.kt") {
            visitField(Opcodes.ACC_PRIVATE, "fieldA", "I", null, null)
        }
        TestClassWriter.writeClassFile(dir2, "com/example/B", "B.kt") {
            visitField(Opcodes.ACC_PRIVATE, "fieldB", "I", null, null)
        }

        val result = FieldExtractor.scanAll(listOf(dir1, dir2))

        assertEquals(setOf("fieldA"), result[ClassName("com.example.A")])
        assertEquals(setOf("fieldB"), result[ClassName("com.example.B")])

        dir1.deleteRecursively()
        dir2.deleteRecursively()
    }

    @Test
    fun `returns an empty map when no class directories exist`() {
        val result = FieldExtractor.scanAll(listOf(java.io.File("does/not/exist")))

        assertTrue(result.isEmpty())
    }
}
