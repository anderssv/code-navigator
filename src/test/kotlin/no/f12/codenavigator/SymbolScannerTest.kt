package no.f12.codenavigator

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SymbolScannerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var classesDir: File

    @BeforeEach
    fun setUp() {
        classesDir = tempDir.resolve("build/classes/kotlin/main").toFile()
        classesDir.mkdirs()
    }

    @Test
    fun `scans directory and finds symbols from all class files`() {
        writeClassFile("com/example/ServiceA", "ServiceA.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)
        }
        writeClassFile("com/example/ServiceB", "ServiceB.kt") {
            visitField(Opcodes.ACC_PUBLIC, "count", "I", null, null)
        }

        val results = SymbolScanner.scan(listOf(classesDir))

        assertEquals(2, results.size)
        assertEquals("doWork", results.first { it.kind == SymbolKind.METHOD }.symbolName)
        assertEquals("count", results.first { it.kind == SymbolKind.FIELD }.symbolName)
    }

    @Test
    fun `returns empty list for empty directory`() {
        val results = SymbolScanner.scan(listOf(classesDir))

        assertTrue(results.isEmpty())
    }

    @Test
    fun `results are sorted by package then class then symbol name`() {
        writeClassFile("com/example/Zebra", "Zebra.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "zzz", "()V", null, null)
        }
        writeClassFile("com/example/Alpha", "Alpha.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "aaa", "()V", null, null)
        }

        val results = SymbolScanner.scan(listOf(classesDir))

        assertEquals(
            listOf("aaa", "zzz"),
            results.map { it.symbolName },
        )
    }

    @Test
    fun `handles non-existent directory gracefully`() {
        val nonExistent = tempDir.resolve("does-not-exist").toFile()

        val results = SymbolScanner.scan(listOf(nonExistent))

        assertTrue(results.isEmpty())
    }

    @Test
    fun `skips synthetic and lambda class files`() {
        writeClassFile("com/example/Foo", "Foo.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "realMethod", "()V", null, null)
        }
        writeClassFile("com/example/Foo\$1", "Foo.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "invoke", "()V", null, null)
        }

        val results = SymbolScanner.scan(listOf(classesDir))

        assertEquals(1, results.size)
        assertEquals("realMethod", results.first().symbolName)
    }

    private fun writeClassFile(
        className: String,
        sourceFile: String,
        targetDir: File = classesDir,
        configure: ClassWriter.() -> Unit = {},
    ) {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
        writer.visitSource(sourceFile, null)
        writer.configure()
        writer.visitEnd()

        val packageDir = className.substringBeforeLast("/", "")
        val simpleFileName = className.substringAfterLast("/") + ".class"
        val dir = if (packageDir.isNotEmpty()) {
            targetDir.resolve(packageDir).also { it.mkdirs() }
        } else {
            targetDir
        }
        File(dir, simpleFileName).writeBytes(writer.toByteArray())
    }
}
