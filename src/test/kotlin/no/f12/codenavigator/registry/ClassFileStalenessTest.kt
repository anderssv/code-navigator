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

        val stale = assertIs<StalenessResult.Stale>(result)
        assertTrue(stale.warning.startsWith("STALE BUILD"), "expected warning to start with a clear STALE BUILD prefix, was: ${stale.warning}")
        assertTrue(stale.warning.contains("recompile"), "expected warning to instruct recompiling, was: ${stale.warning}")
    }

    @Test
    fun `error when no class files exist`() {
        File(sourceDir, "Foo.kt").writeText("class Foo")

        val result = ClassFileStaleness.check(listOf(sourceDir), listOf(classesDir))

        val noFiles = assertIs<StalenessResult.NoClassFiles>(result)
        assertTrue(noFiles.error.startsWith("NO COMPILED CLASSES"), "expected error to start with a clear NO COMPILED CLASSES prefix, was: ${noFiles.error}")
        assertTrue(noFiles.error.contains("compile"), "expected error to instruct compiling, was: ${noFiles.error}")
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

    @Test
    fun `ignores generated sources under build directory inside a source root`() {
        // A real .kt source compiled long ago
        File(sourceDir, "Real.kt").writeText("class Real")
        File(classesDir, "Real.class").writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte()))
        Thread.sleep(50)
        // A regenerated .kt under build/generated-sources within the source root — must NOT count as source
        val generated = File(sourceDir, "build/generated/source/kapt/main").also { it.mkdirs() }
        File(generated, "Generated.kt").writeText("class Generated")

        val result = ClassFileStaleness.check(listOf(sourceDir), listOf(classesDir))

        assertIs<StalenessResult.Fresh>(result)
    }

    @Test
    fun `class scan still works when class dir is under a build directory`() {
        // The class scan must NOT be pruned by build-dir filtering — classes legitimately live under build/.
        val buildClasses = File(tempDir.resolve("build/classes/kotlin/main").toFile().also { it.mkdirs() }, "Foo.class")
        buildClasses.writeBytes(byteArrayOf(0xCA.toByte(), 0xFE.toByte()))
        Thread.sleep(50)
        File(sourceDir, "Foo.kt").writeText("newer source")

        val result = ClassFileStaleness.check(listOf(sourceDir), listOf(buildClasses.parentFile))

        // source is newer than the class → stale (proves the class file under build/ was still found)
        assertIs<StalenessResult.Stale>(result)
    }
}
