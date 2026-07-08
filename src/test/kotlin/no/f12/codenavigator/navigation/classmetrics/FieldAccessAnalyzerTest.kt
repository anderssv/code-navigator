package no.f12.codenavigator.navigation.classmetrics

import no.f12.codenavigator.navigation.bytecode.createClassReader
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FieldAccessAnalyzerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `method touching one field is recorded against that field`() {
        val file = writeFieldTouchingClass(
            "com/example/OneField",
            fields = listOf("a"),
            methods = listOf("touchA" to listOf("a")),
        )

        val data = FieldAccessAnalyzer.analyze(createClassReader(file))

        assertEquals(setOf("a"), data.fields)
        assertEquals(1, data.fieldAccessByMethod.size)
        assertEquals(setOf("a"), data.fieldAccessByMethod.values.single())
    }

    @Test
    fun `method touching two fields is recorded against both`() {
        val file = writeFieldTouchingClass(
            "com/example/TwoFields",
            fields = listOf("a", "b"),
            methods = listOf("touchBoth" to listOf("a", "b")),
        )

        val data = FieldAccessAnalyzer.analyze(createClassReader(file))

        assertEquals(setOf("a", "b"), data.fieldAccessByMethod.values.single())
    }

    @Test
    fun `constructor is excluded from field access map`() {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/WithInit", null, "java/lang/Object", null)
        writer.visitField(Opcodes.ACC_PRIVATE, "a", "I", null, null)
        val init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        init.visitCode()
        init.visitVarInsn(Opcodes.ALOAD, 0)
        init.visitInsn(Opcodes.ICONST_0)
        init.visitFieldInsn(Opcodes.PUTFIELD, "com/example/WithInit", "a", "I")
        init.visitInsn(Opcodes.RETURN)
        init.visitMaxs(2, 1)
        init.visitEnd()
        writer.visitEnd()
        val file = writeBytes(tempDir.toFile(), "com/example/WithInit", writer)

        val data = FieldAccessAnalyzer.analyze(createClassReader(file))

        assertTrue(data.fieldAccessByMethod.isEmpty(), "Constructor must not appear in the field-access map")
    }

    @Test
    fun `default property accessor for a field is excluded`() {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/WithAccessor", null, "java/lang/Object", null)
        writer.visitField(Opcodes.ACC_PRIVATE, "value", "I", null, null)

        val getter = writer.visitMethod(Opcodes.ACC_PUBLIC, "getValue", "()I", null, null)
        getter.visitCode()
        getter.visitVarInsn(Opcodes.ALOAD, 0)
        getter.visitFieldInsn(Opcodes.GETFIELD, "com/example/WithAccessor", "value", "I")
        getter.visitInsn(Opcodes.IRETURN)
        getter.visitMaxs(1, 1)
        getter.visitEnd()

        val realMethod = writer.visitMethod(Opcodes.ACC_PUBLIC, "touchValue", "()V", null, null)
        realMethod.visitCode()
        realMethod.visitVarInsn(Opcodes.ALOAD, 0)
        realMethod.visitInsn(Opcodes.ICONST_0)
        realMethod.visitFieldInsn(Opcodes.PUTFIELD, "com/example/WithAccessor", "value", "I")
        realMethod.visitInsn(Opcodes.RETURN)
        realMethod.visitMaxs(2, 1)
        realMethod.visitEnd()

        writer.visitEnd()
        val file = writeBytes(tempDir.toFile(), "com/example/WithAccessor", writer)

        val data = FieldAccessAnalyzer.analyze(createClassReader(file))

        assertEquals(1, data.fieldAccessByMethod.size, "Only touchValue should remain; getValue is a synthetic accessor")
        assertTrue(data.fieldAccessByMethod.keys.single().startsWith("touchValue"))
    }

    /** Writes a class with the given int fields and methods, each method doing PUTFIELD for the listed field names. */
    private fun writeFieldTouchingClass(
        className: String,
        fields: List<String>,
        methods: List<Pair<String, List<String>>>,
    ): File {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        for (field in fields) {
            writer.visitField(Opcodes.ACC_PRIVATE, field, "I", null, null)
        }
        for ((methodName, touchedFields) in methods) {
            val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()V", null, null)
            mv.visitCode()
            for (field in touchedFields) {
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitInsn(Opcodes.ICONST_0)
                mv.visitFieldInsn(Opcodes.PUTFIELD, className, field, "I")
            }
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(2, 1)
            mv.visitEnd()
        }
        writer.visitEnd()
        return writeBytes(tempDir.toFile(), className, writer)
    }

    private fun writeBytes(targetDir: File, className: String, writer: ClassWriter): File {
        val packageDir = className.substringBeforeLast("/", "")
        val simpleFileName = className.substringAfterLast("/") + ".class"
        val dir = if (packageDir.isNotEmpty()) targetDir.resolve(packageDir).also { it.mkdirs() } else targetDir
        val file = File(dir, simpleFileName)
        file.writeBytes(writer.toByteArray())
        return file
    }
}
