package no.f12.codenavigator.navigation.classmetrics

import no.f12.codenavigator.navigation.types.ClassName
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassMetricsAnalyzerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var classesDir: File

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
    }

    @Test
    fun `empty directory produces no results`() {
        val result = ClassMetricsAnalyzer.analyze(listOf(classesDir))

        assertTrue(result.data.isEmpty())
        assertTrue(result.skippedFiles.isEmpty())
    }

    @Test
    fun `interfaces are excluded from results`() {
        writeSimpleClass("com/example/Port", access = Opcodes.ACC_PUBLIC or Opcodes.ACC_INTERFACE or Opcodes.ACC_ABSTRACT)

        val result = ClassMetricsAnalyzer.analyze(listOf(classesDir))

        assertTrue(result.data.none { it.className == ClassName("com.example.Port") })
    }

    @Test
    fun `generated inner classes are excluded from results`() {
        writeSimpleClass("com/example/Outer")
        writeSimpleClass("com/example/Outer\$1")

        val result = ClassMetricsAnalyzer.analyze(listOf(classesDir))

        assertEquals(listOf(ClassName("com.example.Outer")), result.data.map { it.className })
    }

    @Test
    fun `class with no explicit superclass has DIT zero`() {
        writeSimpleClass("com/example/Root")

        val result = ClassMetricsAnalyzer.analyze(listOf(classesDir))

        val root = result.data.single { it.className == ClassName("com.example.Root") }
        assertEquals(0, root.dit)
    }

    @Test
    fun `DIT counts each hop up the project superclass chain`() {
        writeSimpleClass("com/example/Base")
        writeSimpleClass("com/example/Mid", superName = "com/example/Base")
        writeSimpleClass("com/example/Leaf", superName = "com/example/Mid")

        val result = ClassMetricsAnalyzer.analyze(listOf(classesDir))

        assertEquals(0, result.data.single { it.className == ClassName("com.example.Base") }.dit)
        assertEquals(1, result.data.single { it.className == ClassName("com.example.Mid") }.dit)
        assertEquals(2, result.data.single { it.className == ClassName("com.example.Leaf") }.dit)
    }

    @Test
    fun `DIT counts one hop for an external unresolvable superclass then stops`() {
        writeSimpleClass("com/example/CustomException", superName = "java/lang/RuntimeException")

        val result = ClassMetricsAnalyzer.analyze(listOf(classesDir))

        val entry = result.data.single { it.className == ClassName("com.example.CustomException") }
        assertEquals(1, entry.dit)
    }

    @Test
    fun `packageName is derived from the className`() {
        writeSimpleClass("com/example/nested/Widget")

        val result = ClassMetricsAnalyzer.analyze(listOf(classesDir))

        val entry = result.data.single()
        assertEquals("com.example.nested", entry.packageName.value)
    }

    @Test
    fun `single trivial method class produces MONOLITH verdict with wmc 1 and dit 0`() {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Trivial", null, "java/lang/Object", null)
        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "doIt", "()V", null, null)
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
        writer.visitEnd()
        writeBytes("com/example/Trivial", writer)

        val result = ClassMetricsAnalyzer.analyze(listOf(classesDir))

        val entry = result.data.single()
        assertEquals(1, entry.totalMethods)
        assertEquals(1, entry.wmc)
        assertEquals(0, entry.dit)
        assertEquals(ClassCohesionVerdict.MONOLITH, entry.verdict)
    }

    private fun writeSimpleClass(className: String, superName: String = "java/lang/Object", access: Int = Opcodes.ACC_PUBLIC) {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, access, className, null, superName, null)
        writer.visitEnd()
        writeBytes(className, writer)
    }

    private fun writeBytes(className: String, writer: ClassWriter): File {
        val packageDir = className.substringBeforeLast("/", "")
        val simpleFileName = className.substringAfterLast("/") + ".class"
        val dir = if (packageDir.isNotEmpty()) classesDir.resolve(packageDir).also { it.mkdirs() } else classesDir
        val file = File(dir, simpleFileName)
        file.writeBytes(writer.toByteArray())
        return file
    }
}
