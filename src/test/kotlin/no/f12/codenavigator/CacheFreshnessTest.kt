package no.f12.codenavigator

import no.f12.codenavigator.registry.CacheFreshness
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CacheFreshnessTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var cacheFile: File
    private lateinit var classesDir: File

    @BeforeEach
    fun setUp() {
        cacheFile = tempDir.resolve("cache/test.txt").toFile()
        classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
    }

    @Test
    fun `stale when cache file does not exist`() {
        assertFalse(CacheFreshness.isFresh(cacheFile, listOf(classesDir)))
    }

    @Test
    fun `fresh when cache is newer than all class files`() {
        writeClassFile("com/example/Foo")
        Thread.sleep(50)

        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText("cached")

        assertTrue(CacheFreshness.isFresh(cacheFile, listOf(classesDir)))
    }

    @Test
    fun `stale when a class file is newer than cache`() {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText("cached")
        Thread.sleep(50)

        writeClassFile("com/example/New")

        assertFalse(CacheFreshness.isFresh(cacheFile, listOf(classesDir)))
    }

    @Test
    fun `fresh when class directories are empty`() {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText("cached")

        assertTrue(CacheFreshness.isFresh(cacheFile, listOf(classesDir)))
    }

    @Test
    fun `fresh when class directories do not exist`() {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText("cached")

        val nonExistent = tempDir.resolve("no-such-dir").toFile()

        assertTrue(CacheFreshness.isFresh(cacheFile, listOf(nonExistent)))
    }

    @Test
    fun `stale across multiple directories when one has newer file`() {
        val secondDir = tempDir.resolve("classes2").toFile()
        secondDir.mkdirs()

        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText("cached")
        Thread.sleep(50)

        writeClassFile("com/example/New", secondDir)

        assertFalse(CacheFreshness.isFresh(cacheFile, listOf(classesDir, secondDir)))
    }

    private fun writeClassFile(className: String, targetDir: File = classesDir) {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
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
