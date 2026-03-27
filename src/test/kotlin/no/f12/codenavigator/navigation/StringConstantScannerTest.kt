package no.f12.codenavigator.navigation

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringConstantScannerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `scans directory and filters by pattern`() {
        val dir = tempDir.toFile()
        TestClassWriter.writeClassFile(
            dir, "com/example/Routes", "Routes.kt",
            classWriterFlags = ClassWriter.COMPUTE_FRAMES,
        ) {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "get", "()V", null, null)
            mv.visitCode()
            mv.visitLdcInsn("/api/v1/users")
            mv.visitInsn(Opcodes.POP)
            mv.visitLdcInsn("/api/v2/orders")
            mv.visitInsn(Opcodes.POP)
            mv.visitLdcInsn("unrelated-string")
            mv.visitInsn(Opcodes.POP)
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(1, 1)
            mv.visitEnd()
        }

        val result = StringConstantScanner.scan(listOf(dir), Regex("/api/"))

        assertEquals(2, result.data.size)
        assertEquals("/api/v1/users", result.data[0].value)
        assertEquals("/api/v2/orders", result.data[1].value)
    }

    @Test
    fun `returns empty result for non-existent directory`() {
        val result = StringConstantScanner.scan(
            listOf(tempDir.resolve("nonexistent").toFile()),
            Regex("anything"),
        )

        assertTrue(result.data.isEmpty())
    }

    @Test
    fun `scans multiple directories`() {
        val dir1 = tempDir.resolve("dir1").toFile().also { it.mkdirs() }
        val dir2 = tempDir.resolve("dir2").toFile().also { it.mkdirs() }

        TestClassWriter.writeClassFile(
            dir1, "com/example/A", "A.kt",
            classWriterFlags = ClassWriter.COMPUTE_FRAMES,
        ) {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null)
            mv.visitCode()
            mv.visitLdcInsn("key-alpha")
            mv.visitInsn(Opcodes.POP)
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(1, 1)
            mv.visitEnd()
        }

        TestClassWriter.writeClassFile(
            dir2, "com/example/B", "B.kt",
            classWriterFlags = ClassWriter.COMPUTE_FRAMES,
        ) {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null)
            mv.visitCode()
            mv.visitLdcInsn("key-beta")
            mv.visitInsn(Opcodes.POP)
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(1, 1)
            mv.visitEnd()
        }

        val result = StringConstantScanner.scan(listOf(dir1, dir2), Regex("key-"))

        assertEquals(2, result.data.size)
        assertEquals("key-alpha", result.data[0].value)
        assertEquals("key-beta", result.data[1].value)
    }

    @Test
    fun `results are sorted by class name then method name then value`() {
        val dir = tempDir.toFile()
        TestClassWriter.writeClassFile(
            dir, "com/example/Zebra", "Zebra.kt",
            classWriterFlags = ClassWriter.COMPUTE_FRAMES,
        ) {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null)
            mv.visitCode()
            mv.visitLdcInsn("z-value")
            mv.visitInsn(Opcodes.POP)
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(1, 1)
            mv.visitEnd()
        }
        TestClassWriter.writeClassFile(
            dir, "com/example/Alpha", "Alpha.kt",
            classWriterFlags = ClassWriter.COMPUTE_FRAMES,
        ) {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null)
            mv.visitCode()
            mv.visitLdcInsn("a-value")
            mv.visitInsn(Opcodes.POP)
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(1, 1)
            mv.visitEnd()
        }

        val result = StringConstantScanner.scan(listOf(dir), Regex("value"))

        assertEquals("com.example.Alpha", result.data[0].className.value)
        assertEquals("com.example.Zebra", result.data[1].className.value)
    }
}
