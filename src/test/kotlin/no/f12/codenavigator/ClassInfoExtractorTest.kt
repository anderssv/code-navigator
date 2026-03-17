package no.f12.codenavigator

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassInfoExtractorTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `reads class name from a compiled class file`() {
        val classFile = writeClassFile(
            className = "com/example/MyService",
            sourceFile = "MyService.kt",
        )

        val result = ClassInfoExtractor.extract(classFile)

        assertEquals("com.example.MyService", result.className)
    }

    @Test
    fun `reads source file attribute from a compiled class file`() {
        val classFile = writeClassFile(
            className = "com/example/MyService",
            sourceFile = "MyService.kt",
        )

        val result = ClassInfoExtractor.extract(classFile)

        assertEquals("MyService.kt", result.sourceFileName)
    }

    @Test
    fun `reconstructs source path from package and source file name`() {
        val classFile = writeClassFile(
            className = "com/example/deep/MyService",
            sourceFile = "MyService.kt",
        )

        val result = ClassInfoExtractor.extract(classFile)

        assertEquals("com/example/deep/MyService.kt", result.reconstructedSourcePath)
    }

    @Test
    fun `handles class file without source file attribute`() {
        val classFile = writeClassFile(
            className = "com/example/Generated",
            sourceFile = null,
        )

        val result = ClassInfoExtractor.extract(classFile)

        assertEquals("com.example.Generated", result.className)
        assertEquals("<unknown>", result.sourceFileName)
        assertEquals("<unknown>", result.reconstructedSourcePath)
    }

    @Test
    fun `anonymous inner classes are not user-defined`() {
        val classFile = writeClassFile(
            className = "com/example/Foo\$1",
            sourceFile = "Foo.kt",
        )

        val result = ClassInfoExtractor.extract(classFile)

        assertTrue(!result.isUserDefinedClass)
    }

    @Test
    fun `lambda generated classes are not user-defined`() {
        val classFile = writeClassFile(
            className = "com/example/Foo\$lambda\$1",
            sourceFile = "Foo.kt",
        )

        val result = ClassInfoExtractor.extract(classFile)

        assertTrue(!result.isUserDefinedClass)
    }

    @Test
    fun `named inner classes are user-defined`() {
        val classFile = writeClassFile(
            className = "com/example/Foo\$Bar",
            sourceFile = "Foo.kt",
        )

        val result = ClassInfoExtractor.extract(classFile)

        assertTrue(result.isUserDefinedClass)
        assertEquals("com.example.Foo.Bar", result.className)
    }

    @Test
    fun `regular top-level class is user-defined`() {
        val classFile = writeClassFile(
            className = "com/example/MyService",
            sourceFile = "MyService.kt",
        )

        val result = ClassInfoExtractor.extract(classFile)

        assertTrue(result.isUserDefinedClass)
    }

    private fun writeClassFile(className: String, sourceFile: String?): File {
        val writer = ClassWriter(0)
        writer.visit(
            Opcodes.V21,
            Opcodes.ACC_PUBLIC,
            className,
            null,
            "java/lang/Object",
            null,
        )
        if (sourceFile != null) {
            writer.visitSource(sourceFile, null)
        }
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
