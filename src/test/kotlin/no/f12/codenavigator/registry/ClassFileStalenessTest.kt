package no.f12.codenavigator.registry

import no.f12.codenavigator.registry.ClassFileStaleness
import no.f12.codenavigator.registry.StalenessResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ClassFileStalenessTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var sourceDir: File
    private lateinit var classesDir: File

    @BeforeEach
    fun setUp() {
        sourceDir = tempDir.resolve("src").toFile().also { it.mkdirs() }
        classesDir = tempDir.resolve("classes").toFile().also { it.mkdirs() }
    }

    @Test
    fun `fresh when class files are newer than source files`() {
        File(sourceDir, "Foo.kt").writeText("class Foo")
        Thread.sleep(50)
        File(classesDir, "Foo.class").writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte()))

        val result = ClassFileStaleness.check(listOf(sourceDir), listOf(classesDir))

        assertIs<StalenessResult.Fresh>(result)
    }

    @Test
    fun `stale when source files are newer than class files`() {
        File(classesDir, "Foo.class").writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte()))
        Thread.sleep(50)
        File(sourceDir, "Foo.kt").writeText("class Foo")

        val result = ClassFileStaleness.check(listOf(sourceDir), listOf(classesDir))

        assertIs<StalenessResult.Stale>(result)
    }

    @Test
    fun `error when no class files exist`() {
        File(sourceDir, "Foo.kt").writeText("class Foo")

        val result = ClassFileStaleness.check(listOf(sourceDir), listOf(classesDir))

        val noFiles = assertIs<StalenessResult.NoClassFiles>(result)
        assertTrue(noFiles.error.contains("No class files found"))
    }

    @Test
    fun `fresh when source directories are empty`() {
        File(classesDir, "Foo.class").writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte()))

        val result = ClassFileStaleness.check(listOf(sourceDir), listOf(classesDir))

        assertIs<StalenessResult.Fresh>(result)
    }

    @Test
    fun `fresh when source directories do not exist`() {
        File(classesDir, "Foo.class").writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte()))
        val nonExistent = tempDir.resolve("no-such-dir").toFile()

        val result = ClassFileStaleness.check(listOf(nonExistent), listOf(classesDir))

        assertIs<StalenessResult.Fresh>(result)
    }

    @Test
    fun `stale across multiple directories`() {
        val sourceDir2 = tempDir.resolve("src2").toFile().also { it.mkdirs() }
        File(classesDir, "Foo.class").writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte()))
        Thread.sleep(50)
        File(sourceDir2, "Bar.kt").writeText("class Bar")

        val result = ClassFileStaleness.check(listOf(sourceDir, sourceDir2), listOf(classesDir))

        assertIs<StalenessResult.Stale>(result)
    }

    @Test
    fun `ignores non-source files in source directories`() {
        File(classesDir, "Foo.class").writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte()))
        Thread.sleep(50)
        File(sourceDir, "README.md").writeText("newer file")

        val result = ClassFileStaleness.check(listOf(sourceDir), listOf(classesDir))

        assertIs<StalenessResult.Fresh>(result)
    }
}
