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

class CallGraphCacheTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var cacheFile: File
    private lateinit var classesDir: File

    @BeforeEach
    fun setUp() {
        cacheFile = tempDir.resolve("cache/call-graph.txt").toFile()
        classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
    }

    @Test
    fun `writes and reads back empty call graph`() {
        val graph = CallGraph(emptyMap(), emptyMap())

        CallGraphCache.write(cacheFile, graph)
        val result = CallGraphCache.read(cacheFile)

        assertTrue(result.calleesOf("any.Class", "anyMethod").isEmpty())
        assertTrue(result.callersOf("any.Class", "anyMethod").isEmpty())
    }

    @Test
    fun `writes and reads back call graph with edges`() {
        val edges = mapOf(
            MethodRef("com.example.Caller", "doWork") to setOf(
                MethodRef("com.example.Target", "process"),
            ),
        )
        val graph = CallGraph(edges, emptyMap())

        CallGraphCache.write(cacheFile, graph)
        val result = CallGraphCache.read(cacheFile)

        val callees = result.calleesOf("com.example.Caller", "doWork")
        assertEquals(1, callees.size)
        assertEquals(MethodRef("com.example.Target", "process"), callees.first())

        val callers = result.callersOf("com.example.Target", "process")
        assertEquals(1, callers.size)
        assertEquals(MethodRef("com.example.Caller", "doWork"), callers.first())
    }

    @Test
    fun `writes and reads back source file mappings`() {
        val edges = mapOf(
            MethodRef("com.example.Service", "handle") to setOf(
                MethodRef("com.example.Repo", "save"),
            ),
        )
        val sourceFiles = mapOf(
            "com.example.Service" to "Service.kt",
            "com.example.Repo" to "Repo.kt",
        )
        val graph = CallGraph(edges, sourceFiles)

        CallGraphCache.write(cacheFile, graph)
        val result = CallGraphCache.read(cacheFile)

        assertEquals("Service.kt", result.sourceFileOf("com.example.Service"))
        assertEquals("Repo.kt", result.sourceFileOf("com.example.Repo"))
        assertEquals("<unknown>", result.sourceFileOf("com.example.Missing"))
    }

    @Test
    fun `cache is fresh when newer than all class files`() {
        writeClassFile("com/example/Foo")
        Thread.sleep(50)

        val graph = CallGraph(emptyMap(), emptyMap())
        CallGraphCache.write(cacheFile, graph)

        assertTrue(CallGraphCache.isFresh(cacheFile, listOf(classesDir)))
    }

    @Test
    fun `cache is stale when older than a class file`() {
        val graph = CallGraph(emptyMap(), emptyMap())
        CallGraphCache.write(cacheFile, graph)
        Thread.sleep(50)

        writeClassFile("com/example/NewClass")

        assertFalse(CallGraphCache.isFresh(cacheFile, listOf(classesDir)))
    }

    @Test
    fun `cache is stale when cache file does not exist`() {
        assertFalse(CallGraphCache.isFresh(cacheFile, listOf(classesDir)))
    }

    @Test
    fun `creates parent directories when writing`() {
        val deepCacheFile = tempDir.resolve("a/b/c/cache.txt").toFile()
        val graph = CallGraph(emptyMap(), emptyMap())

        CallGraphCache.write(deepCacheFile, graph)

        assertTrue(deepCacheFile.exists())
    }

    @Test
    fun `handles multiple edges from same caller`() {
        val edges = mapOf(
            MethodRef("com.example.Orchestrator", "run") to setOf(
                MethodRef("com.example.StepA", "execute"),
                MethodRef("com.example.StepB", "execute"),
            ),
        )
        val graph = CallGraph(edges, emptyMap())

        CallGraphCache.write(cacheFile, graph)
        val result = CallGraphCache.read(cacheFile)

        val callees = result.calleesOf("com.example.Orchestrator", "run")
        assertEquals(2, callees.size)
        val calleeNames = callees.map { it.className }.toSet()
        assertTrue("com.example.StepA" in calleeNames)
        assertTrue("com.example.StepB" in calleeNames)
    }

    @Test
    fun `checks freshness across multiple class directories`() {
        val secondDir = tempDir.resolve("classes2").toFile()
        secondDir.mkdirs()

        val graph = CallGraph(emptyMap(), emptyMap())
        CallGraphCache.write(cacheFile, graph)
        Thread.sleep(50)

        writeClassFile("com/example/New", secondDir)

        assertFalse(CallGraphCache.isFresh(cacheFile, listOf(classesDir, secondDir)))
    }

    @Test
    fun `getOrBuild rebuilds when cache file is corrupt`() {
        writeClassFile("com/example/Foo")

        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText("[EDGES]\nonly-one-field\n")
        cacheFile.setLastModified(System.currentTimeMillis() + 10_000)

        val result = CallGraphCache.getOrBuild(cacheFile, listOf(classesDir))

        assertTrue(result.calleesOf("any.Class", "anyMethod").isEmpty())
    }

    @Test
    fun `cache round-trip preserves transitive caller chains for same-class methods`() {
        val edges = mapOf(
            MethodRef("com.example.UserService", "sendResetNotification") to
                setOf(MethodRef("com.example.UserService", "buildNotificationMessage")),
            MethodRef("com.example.UserService", "sendDeactivationNotification") to
                setOf(MethodRef("com.example.UserService", "buildNotificationMessage")),
            MethodRef("com.example.UserService", "resetPassword") to
                setOf(MethodRef("com.example.UserService", "sendResetNotification")),
            MethodRef("com.example.UserService", "deactivateUser") to
                setOf(MethodRef("com.example.UserService", "sendDeactivationNotification")),
            MethodRef("com.example.UserRoute", "handleReset") to
                setOf(MethodRef("com.example.UserService", "resetPassword")),
            MethodRef("com.example.UserRoute", "handleDeactivate") to
                setOf(MethodRef("com.example.UserService", "deactivateUser")),
        )
        val sourceFiles = mapOf(
            "com.example.UserService" to "UserService.kt",
            "com.example.UserRoute" to "UserRoute.kt",
        )
        val original = CallGraph(edges, sourceFiles)

        CallGraphCache.write(cacheFile, original)
        val restored = CallGraphCache.read(cacheFile)

        // Verify transitive chain: buildNotificationMessage ← sendResetNotification ← resetPassword ← handleReset
        val directCallers = restored.callersOf("com.example.UserService", "buildNotificationMessage")
        assertEquals(2, directCallers.size, "Expected 2 direct callers")

        val resetCallers = restored.callersOf("com.example.UserService", "sendResetNotification")
        assertEquals(1, resetCallers.size)
        assertEquals("resetPassword", resetCallers.first().methodName)

        val passwordCallers = restored.callersOf("com.example.UserService", "resetPassword")
        assertEquals(1, passwordCallers.size)
        assertEquals("handleReset", passwordCallers.first().methodName)

        // Full tree from restored cache should show all levels
        val methods = listOf(MethodRef("com.example.UserService", "buildNotificationMessage"))
        val result = CallerTreeFormatter.format(restored, methods, maxDepth = 5)
        assertTrue(result.contains("resetPassword"), "Tree should show resetPassword at depth 2, got:\n$result")
        assertTrue(result.contains("handleReset"), "Tree should show handleReset at depth 3, got:\n$result")
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
