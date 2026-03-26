package no.f12.codenavigator.navigation

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassScannerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var classesDir: File

    @BeforeEach
    fun setUp() {
        classesDir = tempDir.resolve("build/classes/kotlin/main").toFile()
        classesDir.mkdirs()
    }

    @Test
    fun `scans directory and finds all class files`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/ServiceA", "ServiceA.kt")
        TestClassWriter.writeClassFile(classesDir, "com/example/ServiceB", "ServiceB.kt")

        val results = ClassScanner.scan(listOf(classesDir)).data

        assertEquals(2, results.size)
    }

    @Test
    fun `returns empty list for empty directory`() {
        val results = ClassScanner.scan(listOf(classesDir)).data

        assertTrue(results.isEmpty())
    }

    @Test
    fun `filters out anonymous and synthetic classes`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/Foo", "Foo.kt")
        TestClassWriter.writeClassFile(classesDir, "com/example/Foo\$1", "Foo.kt")
        TestClassWriter.writeClassFile(classesDir, "com/example/Foo\$lambda\$1", "Foo.kt")

        val results = ClassScanner.scan(listOf(classesDir)).data

        assertEquals(1, results.size)
        assertEquals("com.example.Foo", results.single().className.value)
    }

    @Test
    fun `keeps named inner classes`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/Outer", "Outer.kt")
        TestClassWriter.writeClassFile(classesDir, "com/example/Outer\$Inner", "Outer.kt")

        val results = ClassScanner.scan(listOf(classesDir)).data

        assertEquals(2, results.size)
        val classNames = results.map { it.className.value }.toSet()
        assertTrue("com.example.Outer" in classNames)
        assertTrue("com.example.Outer.Inner" in classNames)
    }

    @Test
    fun `scans multiple class directories`() {
        val javaClassesDir = tempDir.resolve("build/classes/java/main").toFile()
        javaClassesDir.mkdirs()

        TestClassWriter.writeClassFile(classesDir, "com/example/KotlinService", "KotlinService.kt")
        TestClassWriter.writeClassFile(javaClassesDir, "com/example/JavaService", "JavaService.java")

        val results = ClassScanner.scan(listOf(classesDir, javaClassesDir)).data

        assertEquals(2, results.size)
    }

    @Test
    fun `results are sorted alphabetically by class name`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/Zebra", "Zebra.kt")
        TestClassWriter.writeClassFile(classesDir, "com/example/Alpha", "Alpha.kt")
        TestClassWriter.writeClassFile(classesDir, "com/example/Middle", "Middle.kt")

        val results = ClassScanner.scan(listOf(classesDir)).data

        assertEquals(
            listOf("com.example.Alpha", "com.example.Middle", "com.example.Zebra"),
            results.map { it.className.value },
        )
    }

    @Test
    fun `handles non-existent directory gracefully`() {
        val nonExistent = tempDir.resolve("does-not-exist").toFile()

        val results = ClassScanner.scan(listOf(nonExistent)).data

        assertTrue(results.isEmpty())
    }
}
