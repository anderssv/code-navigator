package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.scanProjectClasses
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectClassScannerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `returns empty set for empty directory`() {
        val result = scanProjectClasses(listOf(tempDir))

        assertEquals(emptySet(), result)
    }

    @Test
    fun `returns empty set for non-existent directory`() {
        val nonExistent = File(tempDir, "does-not-exist")

        val result = scanProjectClasses(listOf(nonExistent))

        assertEquals(emptySet(), result)
    }

    @Test
    fun `scans single class file`() {
        val classesDir = File(tempDir, "classes")
        TestClassWriter.writeClassFile(classesDir, "com/example/MyService", "MyService.kt")

        val result = scanProjectClasses(listOf(classesDir))

        assertEquals(setOf(ClassName("com.example.MyService")), result)
    }

    @Test
    fun `scans multiple class files across packages`() {
        val classesDir = File(tempDir, "classes")
        TestClassWriter.writeClassFile(classesDir, "com/example/api/Controller", "Controller.kt")
        TestClassWriter.writeClassFile(classesDir, "com/example/service/Service", "Service.kt")

        val result = scanProjectClasses(listOf(classesDir))

        assertEquals(
            setOf(
                ClassName("com.example.api.Controller"),
                ClassName("com.example.service.Service"),
            ),
            result,
        )
    }

    @Test
    fun `collapses inner classes to top-level class`() {
        val classesDir = File(tempDir, "classes")
        TestClassWriter.writeClassFile(classesDir, "com/example/Outer", "Outer.kt")
        TestClassWriter.writeClassFile(classesDir, "com/example/Outer\$Inner", "Outer.kt")
        TestClassWriter.writeClassFile(classesDir, "com/example/Outer\$lambda\$1", "Outer.kt")

        val result = scanProjectClasses(listOf(classesDir))

        assertEquals(setOf(ClassName("com.example.Outer")), result)
    }

    @Test
    fun `scans multiple class directories`() {
        val dir1 = File(tempDir, "kotlin-classes")
        val dir2 = File(tempDir, "java-classes")
        TestClassWriter.writeClassFile(dir1, "com/example/KotlinClass", "KotlinClass.kt")
        TestClassWriter.writeClassFile(dir2, "com/example/JavaClass", "JavaClass.java")

        val result = scanProjectClasses(listOf(dir1, dir2))

        assertEquals(
            setOf(
                ClassName("com.example.KotlinClass"),
                ClassName("com.example.JavaClass"),
            ),
            result,
        )
    }

    @Test
    fun `ignores non-class files`() {
        val classesDir = File(tempDir, "classes")
        TestClassWriter.writeClassFile(classesDir, "com/example/Real", "Real.kt")
        File(classesDir, "com/example").mkdirs()
        File(classesDir, "com/example/notes.txt").writeText("not a class")

        val result = scanProjectClasses(listOf(classesDir))

        assertEquals(setOf(ClassName("com.example.Real")), result)
    }

    @Test
    fun `returns set — no duplicates from same class in multiple dirs`() {
        val dir1 = File(tempDir, "dir1")
        val dir2 = File(tempDir, "dir2")
        TestClassWriter.writeClassFile(dir1, "com/example/Shared", "Shared.kt")
        TestClassWriter.writeClassFile(dir2, "com/example/Shared", "Shared.kt")

        val result = scanProjectClasses(listOf(dir1, dir2))

        assertEquals(setOf(ClassName("com.example.Shared")), result)
    }
}
