package no.f12.codenavigator.navigation

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

class SymbolIndexCacheTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var cacheFile: File
    private lateinit var classesDir: File

    @BeforeEach
    fun setUp() {
        cacheFile = tempDir.resolve("cache/symbol-index.txt").toFile()
        classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
    }

    @Test
    fun `writes and reads back empty symbol list`() {
        SymbolIndexCache.write(cacheFile, emptyList())
        val result = SymbolIndexCache.read(cacheFile)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `writes and reads back symbols with methods and fields`() {
        val symbols = listOf(
            SymbolInfo(PackageName("com.example"), ClassName("com.example.Service"), "handle", SymbolKind.METHOD, "Service.kt"),
            SymbolInfo(PackageName("com.example"), ClassName("com.example.Service"), "name", SymbolKind.FIELD, "Service.kt"),
            SymbolInfo(PackageName("com.example"), ClassName("com.example.Repo"), "save", SymbolKind.METHOD, "Repo.kt"),
        )

        SymbolIndexCache.write(cacheFile, symbols)
        val result = SymbolIndexCache.read(cacheFile)

        assertEquals(symbols, result)
    }

    @Test
    fun `cache is fresh when newer than all class files`() {
        writeClassFile("com/example/Foo")
        Thread.sleep(50)

        SymbolIndexCache.write(cacheFile, emptyList())

        assertTrue(SymbolIndexCache.isFresh(cacheFile, listOf(classesDir)))
    }

    @Test
    fun `cache is stale when older than a class file`() {
        SymbolIndexCache.write(cacheFile, emptyList())
        Thread.sleep(50)

        writeClassFile("com/example/NewClass")

        assertFalse(SymbolIndexCache.isFresh(cacheFile, listOf(classesDir)))
    }

    @Test
    fun `cache is stale when cache file does not exist`() {
        assertFalse(SymbolIndexCache.isFresh(cacheFile, listOf(classesDir)))
    }

    @Test
    fun `creates parent directories when writing`() {
        val deepCacheFile = tempDir.resolve("a/b/c/cache.txt").toFile()

        SymbolIndexCache.write(deepCacheFile, emptyList())

        assertTrue(deepCacheFile.exists())
    }

    @Test
    fun `handles symbol with unknown source file`() {
        val symbols = listOf(
            SymbolInfo(PackageName("com.example"), ClassName("com.example.Generated"), "process", SymbolKind.METHOD, "<unknown>"),
        )

        SymbolIndexCache.write(cacheFile, symbols)
        val result = SymbolIndexCache.read(cacheFile)

        assertEquals(symbols, result)
    }

    @Test
    fun `getOrScan rebuilds when cache file is corrupt`() {
        writeClassFile("com/example/Foo")

        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText("only-one-field\n")
        cacheFile.setLastModified(System.currentTimeMillis() + 10_000)

        val result = SymbolIndexCache.getOrScan(cacheFile, listOf(classesDir))

        // Corrupt cache was deleted and rebuilt — empty class has no symbols
        assertTrue(result.data.isEmpty())
        assertFalse(cacheFile.readText().contains("only-one-field"), "Corrupt cache should have been replaced")
    }

    private fun writeClassFile(className: String, targetDir: File = classesDir) {
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null)
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
