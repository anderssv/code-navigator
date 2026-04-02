package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.stringconstant.StringConstantExtractor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringConstantExtractorTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `extracts string constant from method body`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/MyService", "MyService.kt",
            classWriterFlags = org.objectweb.asm.ClassWriter.COMPUTE_FRAMES,
        ) {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "process", "()V", null, null)
            mv.visitCode()
            mv.visitLdcInsn("/api/v1/users")
            mv.visitInsn(Opcodes.POP)
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(1, 1)
            mv.visitEnd()
        }

        val result = StringConstantExtractor.extract(classFile)

        assertEquals(1, result.size)
        assertEquals(ClassName("com.example.MyService"), result[0].className)
        assertEquals("process", result[0].methodName)
        assertEquals("/api/v1/users", result[0].value)
        assertEquals("MyService.kt", result[0].sourceFile)
    }

    @Test
    fun `extracts multiple string constants from same method`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/Config", "Config.kt",
            classWriterFlags = org.objectweb.asm.ClassWriter.COMPUTE_FRAMES,
        ) {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "setup", "()V", null, null)
            mv.visitCode()
            mv.visitLdcInsn("Content-Type")
            mv.visitInsn(Opcodes.POP)
            mv.visitLdcInsn("application/json")
            mv.visitInsn(Opcodes.POP)
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(1, 1)
            mv.visitEnd()
        }

        val result = StringConstantExtractor.extract(classFile)

        assertEquals(2, result.size)
        assertEquals("Content-Type", result[0].value)
        assertEquals("application/json", result[1].value)
    }

    @Test
    fun `extracts strings from multiple methods`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/Routes", "Routes.kt",
            classWriterFlags = org.objectweb.asm.ClassWriter.COMPUTE_FRAMES,
        ) {
            val mv1 = visitMethod(Opcodes.ACC_PUBLIC, "getUsers", "()V", null, null)
            mv1.visitCode()
            mv1.visitLdcInsn("/users")
            mv1.visitInsn(Opcodes.POP)
            mv1.visitInsn(Opcodes.RETURN)
            mv1.visitMaxs(1, 1)
            mv1.visitEnd()

            val mv2 = visitMethod(Opcodes.ACC_PUBLIC, "getOrders", "()V", null, null)
            mv2.visitCode()
            mv2.visitLdcInsn("/orders")
            mv2.visitInsn(Opcodes.POP)
            mv2.visitInsn(Opcodes.RETURN)
            mv2.visitMaxs(1, 1)
            mv2.visitEnd()
        }

        val result = StringConstantExtractor.extract(classFile)

        assertEquals(2, result.size)
        assertEquals("getUsers", result[0].methodName)
        assertEquals("/users", result[0].value)
        assertEquals("getOrders", result[1].methodName)
        assertEquals("/orders", result[1].value)
    }

    @Test
    fun `returns empty list for class with no string constants`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/Empty", "Empty.kt",
        )

        val result = StringConstantExtractor.extract(classFile)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `skips non-string LDC instructions`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/Numbers", "Numbers.kt",
            classWriterFlags = org.objectweb.asm.ClassWriter.COMPUTE_FRAMES,
        ) {
            val mv = visitMethod(Opcodes.ACC_PUBLIC, "compute", "()V", null, null)
            mv.visitCode()
            mv.visitLdcInsn(42)
            mv.visitInsn(Opcodes.POP)
            mv.visitLdcInsn(3.14)
            mv.visitInsn(Opcodes.POP2)
            mv.visitLdcInsn(99L)
            mv.visitInsn(Opcodes.POP2)
            mv.visitLdcInsn(Type.getObjectType("java/lang/String"))
            mv.visitInsn(Opcodes.POP)
            mv.visitLdcInsn("only-this")
            mv.visitInsn(Opcodes.POP)
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(2, 2)
            mv.visitEnd()
        }

        val result = StringConstantExtractor.extract(classFile)

        assertEquals(1, result.size)
        assertEquals("only-this", result[0].value)
    }

    @Test
    fun `skips constructors and synthetic methods`() {
        val classFile = TestClassWriter.writeClassFile(
            tempDir.toFile(), "com/example/WithInit", "WithInit.kt",
            classWriterFlags = org.objectweb.asm.ClassWriter.COMPUTE_FRAMES,
        ) {
            val init = visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
            init.visitCode()
            init.visitVarInsn(Opcodes.ALOAD, 0)
            init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            init.visitLdcInsn("in-constructor")
            init.visitInsn(Opcodes.POP)
            init.visitInsn(Opcodes.RETURN)
            init.visitMaxs(2, 2)
            init.visitEnd()

            val clinit = visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null)
            clinit.visitCode()
            clinit.visitLdcInsn("in-clinit")
            clinit.visitInsn(Opcodes.POP)
            clinit.visitInsn(Opcodes.RETURN)
            clinit.visitMaxs(1, 1)
            clinit.visitEnd()

            val real = visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)
            real.visitCode()
            real.visitLdcInsn("real-string")
            real.visitInsn(Opcodes.POP)
            real.visitInsn(Opcodes.RETURN)
            real.visitMaxs(1, 1)
            real.visitEnd()
        }

        val result = StringConstantExtractor.extract(classFile)

        assertEquals(1, result.size)
        assertEquals("doWork", result[0].methodName)
        assertEquals("real-string", result[0].value)
    }
}
