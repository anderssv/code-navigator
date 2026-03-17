package no.f12.codenavigator

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClassIndexCacheTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var cacheFile: File
    private lateinit var classesDir: File

    @BeforeEach
    fun setUp() {
        cacheFile = tempDir.resolve("cache/class-index.txt").toFile()
        classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
    }

    @Test
    fun `writes and reads back class info`() {
        val classes = listOf(
            ClassInfo("com.example.Foo", "Foo.kt", "com/example/Foo.kt", true),
            ClassInfo("com.example.Bar", "Bar.kt", "com/example/Bar.kt", true),
        )

        ClassIndexCache.write(cacheFile, classes)
        val result = ClassIndexCache.read(cacheFile)

        assertEquals(classes, result)
    }

    @Test
    fun `cache is fresh when newer than all class files`() {
        writeClassFile("com/example/Foo", "Foo.kt")
        Thread.sleep(50)

        ClassIndexCache.write(cacheFile, listOf(ClassInfo("com.example.Foo", "Foo.kt", "com/example/Foo.kt", true)))

        assertTrue(ClassIndexCache.isFresh(cacheFile, listOf(classesDir)))
    }

    @Test
    fun `cache is stale when older than a class file`() {
        ClassIndexCache.write(cacheFile, listOf(ClassInfo("com.example.Foo", "Foo.kt", "com/example/Foo.kt", true)))
        Thread.sleep(50)

        writeClassFile("com/example/NewClass", "NewClass.kt")

        assertFalse(ClassIndexCache.isFresh(cacheFile, listOf(classesDir)))
    }

    @Test
    fun `cache is stale when cache file does not exist`() {
        assertFalse(ClassIndexCache.isFresh(cacheFile, listOf(classesDir)))
    }

    @Test
    fun `cache is stale when class directories are empty but cache does not exist`() {
        assertFalse(ClassIndexCache.isFresh(cacheFile, listOf(classesDir)))
    }

    @Test
    fun `handles class info with unknown source file`() {
        val classes = listOf(
            ClassInfo("com.example.Generated", "<unknown>", "<unknown>", true),
        )

        ClassIndexCache.write(cacheFile, classes)
        val result = ClassIndexCache.read(cacheFile)

        assertEquals(classes, result)
    }

    @Test
    fun `handles empty class list`() {
        ClassIndexCache.write(cacheFile, emptyList())
        val result = ClassIndexCache.read(cacheFile)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `creates parent directories when writing`() {
        val deepCacheFile = tempDir.resolve("a/b/c/cache.txt").toFile()

        ClassIndexCache.write(deepCacheFile, listOf(ClassInfo("com.Foo", "Foo.kt", "com/Foo.kt", true)))

        assertTrue(deepCacheFile.exists())
    }

    @Test
    fun `checks freshness across multiple class directories`() {
        val secondDir = tempDir.resolve("classes2").toFile()
        secondDir.mkdirs()

        ClassIndexCache.write(cacheFile, listOf(ClassInfo("com.example.Foo", "Foo.kt", "com/example/Foo.kt", true)))
        Thread.sleep(50)

        writeClassFile("com/example/New", "New.kt", secondDir)

        assertFalse(ClassIndexCache.isFresh(cacheFile, listOf(classesDir, secondDir)))
    }

    private fun writeClassFile(className: String, sourceFile: String, targetDir: File = classesDir) {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
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
