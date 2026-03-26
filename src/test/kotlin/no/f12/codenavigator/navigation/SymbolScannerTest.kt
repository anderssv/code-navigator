package no.f12.codenavigator.navigation

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
        TestClassWriter.writeClassFile(classesDir, "com/example/ServiceA", "ServiceA.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "doWork", "()V", null, null)
        }
        TestClassWriter.writeClassFile(classesDir, "com/example/ServiceB", "ServiceB.kt") {
            visitField(Opcodes.ACC_PUBLIC, "count", "I", null, null)
        }

        val results = SymbolScanner.scan(listOf(classesDir)).data

        assertEquals(2, results.size)
        assertEquals("doWork", results.first { it.kind == SymbolKind.METHOD }.symbolName)
        assertEquals("count", results.first { it.kind == SymbolKind.FIELD }.symbolName)
    }

    @Test
    fun `returns empty list for empty directory`() {
        val results = SymbolScanner.scan(listOf(classesDir)).data

        assertTrue(results.isEmpty())
    }

    @Test
    fun `results are sorted by package then class then symbol name`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/Zebra", "Zebra.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "zzz", "()V", null, null)
        }
        TestClassWriter.writeClassFile(classesDir, "com/example/Alpha", "Alpha.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "aaa", "()V", null, null)
        }

        val results = SymbolScanner.scan(listOf(classesDir)).data

        assertEquals(
            listOf("aaa", "zzz"),
            results.map { it.symbolName },
        )
    }

    @Test
    fun `handles non-existent directory gracefully`() {
        val nonExistent = tempDir.resolve("does-not-exist").toFile()

        val results = SymbolScanner.scan(listOf(nonExistent)).data

        assertTrue(results.isEmpty())
    }

    @Test
    fun `skips synthetic and lambda class files`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/Foo", "Foo.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "realMethod", "()V", null, null)
        }
        TestClassWriter.writeClassFile(classesDir, "com/example/Foo\$1", "Foo.kt") {
            visitMethod(Opcodes.ACC_PUBLIC, "invoke", "()V", null, null)
        }

        val results = SymbolScanner.scan(listOf(classesDir)).data

        assertEquals(1, results.size)
        assertEquals("realMethod", results.first().symbolName)
    }
}
