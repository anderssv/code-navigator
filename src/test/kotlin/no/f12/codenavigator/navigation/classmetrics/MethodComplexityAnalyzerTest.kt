package no.f12.codenavigator.navigation.classmetrics

import no.f12.codenavigator.navigation.bytecode.createClassReader
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class MethodComplexityAnalyzerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `method with no branches has WMC 1`() {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Straightline", null, "java/lang/Object", null)
        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "doIt", "()V", null, null)
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
        writer.visitEnd()
        val file = writeBytes("com/example/Straightline", writer)

        val wmc = MethodComplexityAnalyzer.analyze(createClassReader(file), emptySet())

        assertEquals(1, wmc)
    }

    @Test
    fun `one conditional jump adds one to WMC`() {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/OneBranch", null, "java/lang/Object", null)
        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "classify", "(I)V", null, null)
        mv.visitCode()
        val elseLabel = Label()
        val endLabel = Label()
        mv.visitVarInsn(Opcodes.ILOAD, 1)
        mv.visitJumpInsn(Opcodes.IFLE, elseLabel)
        mv.visitJumpInsn(Opcodes.GOTO, endLabel)
        mv.visitLabel(elseLabel)
        mv.visitLabel(endLabel)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(1, 2)
        mv.visitEnd()
        writer.visitEnd()
        val file = writeBytes("com/example/OneBranch", writer)

        val wmc = MethodComplexityAnalyzer.analyze(createClassReader(file), emptySet())

        assertEquals(2, wmc, "base 1 + 1 conditional jump; GOTO must not count")
    }

    @Test
    fun `two methods sum their WMC across the class`() {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/TwoMethods", null, "java/lang/Object", null)

        val simple = writer.visitMethod(Opcodes.ACC_PUBLIC, "simple", "()V", null, null)
        simple.visitCode()
        simple.visitInsn(Opcodes.RETURN)
        simple.visitMaxs(0, 1)
        simple.visitEnd()

        val branchy = writer.visitMethod(Opcodes.ACC_PUBLIC, "branchy", "(I)V", null, null)
        branchy.visitCode()
        val label1 = Label()
        val label2 = Label()
        branchy.visitVarInsn(Opcodes.ILOAD, 1)
        branchy.visitJumpInsn(Opcodes.IFEQ, label1)
        branchy.visitVarInsn(Opcodes.ILOAD, 1)
        branchy.visitJumpInsn(Opcodes.IFLT, label2)
        branchy.visitLabel(label1)
        branchy.visitLabel(label2)
        branchy.visitInsn(Opcodes.RETURN)
        branchy.visitMaxs(1, 2)
        branchy.visitEnd()

        writer.visitEnd()
        val file = writeBytes("com/example/TwoMethods", writer)

        val wmc = MethodComplexityAnalyzer.analyze(createClassReader(file), emptySet())

        assertEquals(4, wmc, "simple=1, branchy=1+2 conditional jumps=3, total=4")
    }

    @Test
    fun `switch cases each add one to WMC`() {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/SwitchMethod", null, "java/lang/Object", null)
        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "classify", "(I)V", null, null)
        mv.visitCode()
        val case0 = Label()
        val case1 = Label()
        val case2 = Label()
        val default = Label()
        val end = Label()
        mv.visitVarInsn(Opcodes.ILOAD, 1)
        mv.visitTableSwitchInsn(0, 2, default, case0, case1, case2)
        mv.visitLabel(case0)
        mv.visitJumpInsn(Opcodes.GOTO, end)
        mv.visitLabel(case1)
        mv.visitJumpInsn(Opcodes.GOTO, end)
        mv.visitLabel(case2)
        mv.visitJumpInsn(Opcodes.GOTO, end)
        mv.visitLabel(default)
        mv.visitLabel(end)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(1, 2)
        mv.visitEnd()
        writer.visitEnd()
        val file = writeBytes("com/example/SwitchMethod", writer)

        val wmc = MethodComplexityAnalyzer.analyze(createClassReader(file), emptySet())

        assertEquals(4, wmc, "base 1 + 3 switch cases (default excluded)")
    }

    @Test
    fun `catch block adds one to WMC`() {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/TryCatch", null, "java/lang/Object", null)
        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "attempt", "()V", null, null)
        mv.visitCode()
        val start = Label()
        val end = Label()
        val handler = Label()
        val after = Label()
        mv.visitTryCatchBlock(start, end, handler, "java/lang/Exception")
        mv.visitLabel(start)
        mv.visitInsn(Opcodes.NOP)
        mv.visitLabel(end)
        mv.visitJumpInsn(Opcodes.GOTO, after)
        mv.visitLabel(handler)
        mv.visitInsn(Opcodes.POP)
        mv.visitLabel(after)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()
        writer.visitEnd()
        val file = writeBytes("com/example/TryCatch", writer)

        val wmc = MethodComplexityAnalyzer.analyze(createClassReader(file), emptySet())

        assertEquals(2, wmc, "base 1 + 1 catch block")
    }

    @Test
    fun `constructors and property accessors are excluded from WMC`() {
        val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/ExcludedMethods", null, "java/lang/Object", null)

        val init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        init.visitCode()
        val l1 = Label()
        init.visitVarInsn(Opcodes.ALOAD, 0)
        init.visitJumpInsn(Opcodes.IFNULL, l1)
        init.visitLabel(l1)
        init.visitInsn(Opcodes.RETURN)
        init.visitMaxs(1, 1)
        init.visitEnd()

        val getter = writer.visitMethod(Opcodes.ACC_PUBLIC, "getValue", "()I", null, null)
        getter.visitCode()
        getter.visitInsn(Opcodes.ICONST_0)
        getter.visitInsn(Opcodes.IRETURN)
        getter.visitMaxs(1, 1)
        getter.visitEnd()

        writer.visitEnd()
        val file = writeBytes("com/example/ExcludedMethods", writer)

        val wmc = MethodComplexityAnalyzer.analyze(createClassReader(file), setOf("value"))

        assertEquals(0, wmc, "constructor and value's default accessor must both be excluded")
    }

    private fun writeBytes(className: String, writer: ClassWriter): File {
        val packageDir = className.substringBeforeLast("/", "")
        val simpleFileName = className.substringAfterLast("/") + ".class"
        val targetDir = tempDir.toFile()
        val dir = if (packageDir.isNotEmpty()) targetDir.resolve(packageDir).also { it.mkdirs() } else targetDir
        val file = File(dir, simpleFileName)
        file.writeBytes(writer.toByteArray())
        return file
    }
}
