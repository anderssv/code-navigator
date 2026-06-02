package no.f12.codenavigator.navigation.classinfo

import no.f12.codenavigator.navigation.classinfo.ClassInfoExtractor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassInfoExtractorTest {

    private val testProjectClasses = File("test-project/build/classes/kotlin/main")

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `reads class name from a compiled class file`() {
        val classFile = testProjectClasses.resolve("com/example/domain/UserFormatter.class")

        val result = ClassInfoExtractor.extract(classFile)

        assertEquals("com.example.domain.UserFormatter", result.className.value)
    }

    @Test
    fun `reads source file attribute from a compiled class file`() {
        val classFile = testProjectClasses.resolve("com/example/domain/UserFormatter.class")

        val result = ClassInfoExtractor.extract(classFile)

        assertEquals("UserFormatter.kt", result.sourceFileName)
    }

    @Test
    fun `reconstructs source path from package and source file name`() {
        val classFile = testProjectClasses.resolve("com/example/infra/EventSender.class")

        val result = ClassInfoExtractor.extract(classFile)

        assertEquals("com/example/infra/EventSender.kt", result.reconstructedSourcePath)
    }

    // Synthetic: the Kotlin compiler always emits source file attributes,
    // so we need synthetic bytecode to test the missing-source-file edge case.
    @Test
    fun `handles class file without source file attribute`() {
        val classFile = syntheticClassFile(tempDir.toFile(), "com/example/Generated", null)

        val result = ClassInfoExtractor.extract(classFile)

        assertEquals("com.example.Generated", result.className.value)
        assertEquals("<unknown>", result.sourceFileName)
        assertEquals("<unknown>", result.reconstructedSourcePath)
    }

    @Test
    fun `anonymous inner classes are not user-defined`() {
        val classFile = testProjectClasses.resolve("com/example/routes/UserRoute\$handleReset\$1.class")

        val result = ClassInfoExtractor.extract(classFile)

        assertTrue(!result.isUserDefinedClass)
    }

    @Test
    fun `named inner classes are user-defined`() {
        val classFile = testProjectClasses.resolve("com/example/infra/EventSender\$Companion.class")

        val result = ClassInfoExtractor.extract(classFile)

        assertTrue(result.isUserDefinedClass)
        assertEquals("com.example.infra.EventSender\$Companion", result.className.value)
        assertEquals("com.example.infra.EventSender.Companion", result.className.displayName())
    }

    @Test
    fun `sealed subclass inner classes are user-defined`() {
        val classFile = testProjectClasses.resolve("com/example/domain/UserError\$ValidationFailed.class")

        val result = ClassInfoExtractor.extract(classFile)

        assertTrue(result.isUserDefinedClass)
        assertEquals("com.example.domain.UserError\$ValidationFailed", result.className.value)
    }

    @Test
    fun `regular top-level class is user-defined`() {
        val classFile = testProjectClasses.resolve("com/example/domain/UserFormatter.class")

        val result = ClassInfoExtractor.extract(classFile)

        assertTrue(result.isUserDefinedClass)
    }

    // Synthetic: tests the ByteArray overload API which requires raw bytes
    // (no file path). Real class files always come from disk.
    @Test
    fun `extracts class info from ByteArray`() {
        val bytes = syntheticClassBytes("com/example/JarClass", "JarClass.kt")

        val result = ClassInfoExtractor.extract(bytes)

        assertEquals("com.example.JarClass", result.className.value)
        assertEquals("JarClass.kt", result.sourceFileName)
        assertEquals("com/example/JarClass.kt", result.reconstructedSourcePath)
        assertTrue(result.isUserDefinedClass)
    }

    private fun syntheticClassFile(dir: File, className: String, sourceFile: String?): File {
        val bytes = syntheticClassBytes(className, sourceFile)
        val file = File(dir, "$className.class")
        file.parentFile.mkdirs()
        file.writeBytes(bytes)
        return file
    }

    private fun syntheticClassBytes(className: String, sourceFile: String?): ByteArray {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        if (sourceFile != null) writer.visitSource(sourceFile, null)
        writer.visitEnd()
        return writer.toByteArray()
    }
}
