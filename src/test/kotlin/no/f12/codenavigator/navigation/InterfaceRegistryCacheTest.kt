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

class InterfaceRegistryCacheTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var cacheFile: File
    private lateinit var classesDir: File

    @BeforeEach
    fun setUp() {
        cacheFile = tempDir.resolve("cache/interface-registry.txt").toFile()
        classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
    }

    @Test
    fun `writes and reads back empty registry`() {
        val registry = InterfaceRegistry(emptyMap())

        InterfaceRegistryCache.write(cacheFile, registry)
        val result = InterfaceRegistryCache.read(cacheFile)

        assertTrue(result.findInterfaces(".*").isEmpty())
    }

    @Test
    fun `writes and reads back registry with implementors`() {
        val registry = InterfaceRegistry(
            mapOf(
                "com.example.Repository" to listOf(
                    ImplementorInfo("com.example.JpaRepository", "JpaRepository.kt"),
                    ImplementorInfo("com.example.InMemoryRepository", "InMemoryRepository.kt"),
                ),
                "com.example.Service" to listOf(
                    ImplementorInfo("com.example.UserService", "UserService.kt"),
                ),
            ),
        )

        InterfaceRegistryCache.write(cacheFile, registry)
        val result = InterfaceRegistryCache.read(cacheFile)

        val repoImpls = result.implementorsOf("com.example.Repository")
        assertEquals(2, repoImpls.size)
        assertEquals("com.example.JpaRepository", repoImpls[0].className)
        assertEquals("JpaRepository.kt", repoImpls[0].sourceFile)
        assertEquals("com.example.InMemoryRepository", repoImpls[1].className)

        val serviceImpls = result.implementorsOf("com.example.Service")
        assertEquals(1, serviceImpls.size)
        assertEquals("com.example.UserService", serviceImpls[0].className)
    }

    @Test
    fun `findInterfaces works on deserialized registry`() {
        val registry = InterfaceRegistry(
            mapOf(
                "com.example.Repository" to listOf(
                    ImplementorInfo("com.example.JpaRepository", "JpaRepository.kt"),
                ),
            ),
        )

        InterfaceRegistryCache.write(cacheFile, registry)
        val result = InterfaceRegistryCache.read(cacheFile)

        val found = result.findInterfaces("Repository")
        assertEquals(1, found.size)
        assertEquals("com.example.Repository", found[0])
    }

    @Test
    fun `cache is fresh when newer than all class files`() {
        writeClassFile("com/example/Foo")
        Thread.sleep(50)

        InterfaceRegistryCache.write(cacheFile, InterfaceRegistry(emptyMap()))

        assertTrue(InterfaceRegistryCache.isFresh(cacheFile, listOf(classesDir)))
    }

    @Test
    fun `cache is stale when older than a class file`() {
        InterfaceRegistryCache.write(cacheFile, InterfaceRegistry(emptyMap()))
        Thread.sleep(50)

        writeClassFile("com/example/NewClass")

        assertFalse(InterfaceRegistryCache.isFresh(cacheFile, listOf(classesDir)))
    }

    @Test
    fun `cache is stale when cache file does not exist`() {
        assertFalse(InterfaceRegistryCache.isFresh(cacheFile, listOf(classesDir)))
    }

    @Test
    fun `creates parent directories when writing`() {
        val deepCacheFile = tempDir.resolve("a/b/c/cache.txt").toFile()

        InterfaceRegistryCache.write(deepCacheFile, InterfaceRegistry(emptyMap()))

        assertTrue(deepCacheFile.exists())
    }

    @Test
    fun `getOrBuild rebuilds when cache file is corrupt`() {
        writeClassFile("com/example/Foo")

        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText("only-one-field\n")
        cacheFile.setLastModified(System.currentTimeMillis() + 10_000)

        val result = InterfaceRegistryCache.getOrBuild(cacheFile, listOf(classesDir))

        assertTrue(result.data.findInterfaces(".*").isEmpty())
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
