package no.f12.codenavigator.navigation

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertContains

class BytecodeReadExceptionTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `throws UnsupportedBytecodeVersionException for unsupported major version`() {
        val classFile = writeClassFileWithVersion(99, "com/example/FutureClass")

        val exception = assertFailsWith<UnsupportedBytecodeVersionException> {
            createClassReader(classFile)
        }

        assertContains(exception.message!!, "FutureClass.class")
        assertContains(exception.message!!, "newer JVM than the code-navigator plugin supports")
    }

    @Test
    fun `exception message includes Java version derived from major version`() {
        val majorVersion = 80 // Java 36 (well beyond current support)
        val classFile = writeClassFileWithVersion(majorVersion, "com/example/FutureJavaClass")

        val exception = assertFailsWith<UnsupportedBytecodeVersionException> {
            createClassReader(classFile)
        }

        assertContains(exception.message!!, "Java 36")
    }

    @Test
    fun `succeeds for supported bytecode version`() {
        val classFile = writeClassFileWithVersion(65, "com/example/SupportedClass") // Java 21

        val reader = createClassReader(classFile)

        assertEquals("com/example/SupportedClass", reader.className)
    }

    @Test
    fun `ClassScanner returns skipped files when class files are unsupported`() {
        writeClassFileWithVersion(99, "com/example/UnsupportedClass")

        val result = ClassScanner.scan(listOf(tempDir.toFile()))

        assertEquals(1, result.skippedFiles.size)
        assertContains(result.skippedFiles.first().message!!, "UnsupportedClass.class")
    }

    @Test
    fun `ClassScanner returns valid classes alongside skipped files`() {
        writeClassFileWithVersion(65, "com/example/ValidClass")
        writeClassFileWithVersion(99, "com/example/UnsupportedClass")

        val result = ClassScanner.scan(listOf(tempDir.toFile()))

        assertEquals(1, result.data.size)
        assertEquals("com.example.ValidClass", result.data.first().className.value)
        assertEquals(1, result.skippedFiles.size)
    }

    private fun writeClassFileWithVersion(majorVersion: Int, className: String): File {
        val writer = ClassWriter(0)
        writer.visit(
            Opcodes.V21,
            Opcodes.ACC_PUBLIC,
            className,
            null,
            "java/lang/Object",
            null,
        )
        writer.visitEnd()
        val bytes = writer.toByteArray()

        // Patch the major version in the raw bytecode (bytes 6-7)
        bytes[6] = (majorVersion shr 8).toByte()
        bytes[7] = (majorVersion and 0xFF).toByte()

        val dir = tempDir.resolve(className.substringBeforeLast("/", "")).toFile()
        dir.mkdirs()
        val file = File(dir, className.substringAfterLast("/") + ".class")
        file.writeBytes(bytes)
        return file
    }
}
