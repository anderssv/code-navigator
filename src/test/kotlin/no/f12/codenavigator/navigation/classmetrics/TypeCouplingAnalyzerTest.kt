package no.f12.codenavigator.navigation.classmetrics

import no.f12.codenavigator.navigation.bytecode.createClassReader
import no.f12.codenavigator.navigation.types.ClassName
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class TypeCouplingAnalyzerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `field of a project type counts toward CBO`() {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Service", null, "java/lang/Object", null)
        writer.visitField(Opcodes.ACC_PRIVATE, "repo", "Lcom/example/Repository;", null, null)
        writer.visitEnd()
        val file = writeBytes("com/example/Service", writer)

        val cbo = TypeCouplingAnalyzer.analyze(createClassReader(file), ClassName("com.example.Service"))

        assertEquals(1, cbo)
    }

    @Test
    fun `java lang and java util field types are excluded`() {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Service", null, "java/lang/Object", null)
        writer.visitField(Opcodes.ACC_PRIVATE, "name", "Ljava/lang/String;", null, null)
        writer.visitField(Opcodes.ACC_PRIVATE, "items", "Ljava/util/List;", null, null)
        writer.visitEnd()
        val file = writeBytes("com/example/Service", writer)

        val cbo = TypeCouplingAnalyzer.analyze(createClassReader(file), ClassName("com.example.Service"))

        assertEquals(0, cbo)
    }

    @Test
    fun `kotlin stdlib types are excluded`() {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Service", null, "java/lang/Object", null)
        writer.visitField(Opcodes.ACC_PRIVATE, "range", "Lkotlin/ranges/IntRange;", null, null)
        writer.visitEnd()
        val file = writeBytes("com/example/Service", writer)

        val cbo = TypeCouplingAnalyzer.analyze(createClassReader(file), ClassName("com.example.Service"))

        assertEquals(0, cbo)
    }

    @Test
    fun `primitive field types do not count`() {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Service", null, "java/lang/Object", null)
        writer.visitField(Opcodes.ACC_PRIVATE, "count", "I", null, null)
        writer.visitEnd()
        val file = writeBytes("com/example/Service", writer)

        val cbo = TypeCouplingAnalyzer.analyze(createClassReader(file), ClassName("com.example.Service"))

        assertEquals(0, cbo)
    }

    @Test
    fun `method parameter and return types count toward CBO`() {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Service", null, "java/lang/Object", null)
        writer.visitMethod(
            Opcodes.ACC_PUBLIC, "process",
            "(Lcom/example/Request;)Lcom/example/Response;", null, null,
        )
        writer.visitEnd()
        val file = writeBytes("com/example/Service", writer)

        val cbo = TypeCouplingAnalyzer.analyze(createClassReader(file), ClassName("com.example.Service"))

        assertEquals(2, cbo)
    }

    @Test
    fun `array element type is unwrapped for CBO`() {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Service", null, "java/lang/Object", null)
        writer.visitField(Opcodes.ACC_PRIVATE, "items", "[Lcom/example/Item;", null, null)
        writer.visitEnd()
        val file = writeBytes("com/example/Service", writer)

        val cbo = TypeCouplingAnalyzer.analyze(createClassReader(file), ClassName("com.example.Service"))

        assertEquals(1, cbo)
    }

    @Test
    fun `self-referencing field type is excluded from CBO`() {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Node", null, "java/lang/Object", null)
        writer.visitField(Opcodes.ACC_PRIVATE, "next", "Lcom/example/Node;", null, null)
        writer.visitEnd()
        val file = writeBytes("com/example/Node", writer)

        val cbo = TypeCouplingAnalyzer.analyze(createClassReader(file), ClassName("com.example.Node"))

        assertEquals(0, cbo)
    }

    @Test
    fun `duplicate referenced types are only counted once`() {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Service", null, "java/lang/Object", null)
        writer.visitField(Opcodes.ACC_PRIVATE, "a", "Lcom/example/Widget;", null, null)
        writer.visitMethod(Opcodes.ACC_PUBLIC, "process", "(Lcom/example/Widget;)V", null, null)
        writer.visitEnd()
        val file = writeBytes("com/example/Service", writer)

        val cbo = TypeCouplingAnalyzer.analyze(createClassReader(file), ClassName("com.example.Service"))

        assertEquals(1, cbo)
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
