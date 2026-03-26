package no.f12.codenavigator.navigation

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassDetailScannerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var classesDir: File

    @BeforeEach
    fun setUp() {
        classesDir = tempDir.resolve("build/classes/kotlin/main").toFile()
        classesDir.mkdirs()
    }

    @Test
    fun `returns matching classes with details`() {
        writeClassFile("com/example/ServiceA", "ServiceA.kt")
        writeClassFile("com/example/ServiceB", "ServiceB.kt")

        val results = ClassDetailScanner.scan(listOf(classesDir), "Service").data

        assertEquals(2, results.size)
        val classNames = results.map { it.className.value }
        assertTrue("com.example.ServiceA" in classNames)
        assertTrue("com.example.ServiceB" in classNames)
    }

    @Test
    fun `filters by regex pattern`() {
        writeClassFile("com/example/UserService", "UserService.kt")
        writeClassFile("com/example/OrderController", "OrderController.kt")

        val results = ClassDetailScanner.scan(listOf(classesDir), "Service").data

        assertEquals(1, results.size)
        assertEquals("com.example.UserService", results.single().className.value)
    }

    @Test
    fun `pattern matching is case insensitive`() {
        writeClassFile("com/example/UserService", "UserService.kt")

        val results = ClassDetailScanner.scan(listOf(classesDir), "userservice").data

        assertEquals(1, results.size)
    }

    @Test
    fun `returns empty list when no classes match pattern`() {
        writeClassFile("com/example/ServiceA", "ServiceA.kt")

        val results = ClassDetailScanner.scan(listOf(classesDir), "Controller").data

        assertTrue(results.isEmpty())
    }

    @Test
    fun `returns empty list for empty directory`() {
        val results = ClassDetailScanner.scan(listOf(classesDir), ".*").data

        assertTrue(results.isEmpty())
    }

    @Test
    fun `handles non-existent directory gracefully`() {
        val nonExistent = tempDir.resolve("does-not-exist").toFile()

        val results = ClassDetailScanner.scan(listOf(nonExistent), ".*").data

        assertTrue(results.isEmpty())
    }

    @Test
    fun `results are sorted by class name`() {
        writeClassFile("com/example/Zebra", "Zebra.kt")
        writeClassFile("com/example/Alpha", "Alpha.kt")
        writeClassFile("com/example/Middle", "Middle.kt")

        val results = ClassDetailScanner.scan(listOf(classesDir), ".*").data

        assertEquals(
            listOf("com.example.Alpha", "com.example.Middle", "com.example.Zebra"),
            results.map { it.className.value },
        )
    }

    @Test
    fun `skips synthetic and anonymous classes`() {
        writeClassFile("com/example/Foo", "Foo.kt")
        writeClassFile("com/example/Foo\$1", "Foo.kt")
        writeClassFile("com/example/Foo\$lambda\$1", "Foo.kt")

        val results = ClassDetailScanner.scan(listOf(classesDir), "Foo").data

        assertEquals(1, results.size)
        assertEquals("com.example.Foo", results.single().className.value)
    }

    @Test
    fun `scans multiple class directories`() {
        val javaClassesDir = tempDir.resolve("build/classes/java/main").toFile()
        javaClassesDir.mkdirs()

        writeClassFile("com/example/KotlinService", "KotlinService.kt")
        writeClassFile("com/example/JavaService", "JavaService.java", targetDir = javaClassesDir)

        val results = ClassDetailScanner.scan(listOf(classesDir, javaClassesDir), "Service").data

        assertEquals(2, results.size)
    }

    @Test
    fun `populates source file in results`() {
        writeClassFile("com/example/MyService", "MyService.kt")

        val result = ClassDetailScanner.scan(listOf(classesDir), "MyService").data.single()

        assertEquals("MyService.kt", result.sourceFile)
    }

    private fun writeClassFile(
        className: String,
        sourceFile: String,
        targetDir: File = classesDir,
    ) {
        val writer = ClassWriter(0)
        writer.visit(
            Opcodes.V21,
            Opcodes.ACC_PUBLIC,
            className,
            null,
            "java/lang/Object",
            null,
        )
        writer.visitSource(sourceFile, null)
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
