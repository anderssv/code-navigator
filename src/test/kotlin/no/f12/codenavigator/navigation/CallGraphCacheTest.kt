package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.callgraph.CallGraph
import no.f12.codenavigator.navigation.callgraph.CallGraphCache
import no.f12.codenavigator.navigation.callgraph.CallDirection
import no.f12.codenavigator.navigation.callgraph.CallTreeFormatter
import no.f12.codenavigator.navigation.callgraph.MethodRef
import no.f12.codenavigator.navigation.core.SourceSet
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

        assertTrue(result.calleesOf(ClassName("any.Class"), "anyMethod").isEmpty())
        assertTrue(result.callersOf(ClassName("any.Class"), "anyMethod").isEmpty())
    }

    @Test
    fun `writes and reads back call graph with edges`() {
        val edges = mapOf(
            MethodRef(ClassName("com.example.Caller"), "doWork") to setOf(
                MethodRef(ClassName("com.example.Target"), "process"),
            ),
        )
        val graph = CallGraph(edges, emptyMap())

        CallGraphCache.write(cacheFile, graph)
        val result = CallGraphCache.read(cacheFile)

        val callees = result.calleesOf(ClassName("com.example.Caller"), "doWork")
        assertEquals(1, callees.size)
        assertEquals(MethodRef(ClassName("com.example.Target"), "process"), callees.first())

        val callers = result.callersOf(ClassName("com.example.Target"), "process")
        assertEquals(1, callers.size)
        assertEquals(MethodRef(ClassName("com.example.Caller"), "doWork"), callers.first())
    }

    @Test
    fun `writes and reads back source file mappings`() {
        val edges = mapOf(
            MethodRef(ClassName("com.example.Service"), "handle") to setOf(
                MethodRef(ClassName("com.example.Repo"), "save"),
            ),
        )
        val sourceFiles = mapOf(
            ClassName("com.example.Service") to "Service.kt",
            ClassName("com.example.Repo") to "Repo.kt",
        )
        val graph = CallGraph(edges, sourceFiles)

        CallGraphCache.write(cacheFile, graph)
        val result = CallGraphCache.read(cacheFile)

        assertEquals("Service.kt", result.sourceFileOf(ClassName("com.example.Service")))
        assertEquals("Repo.kt", result.sourceFileOf(ClassName("com.example.Repo")))
        assertEquals("<unknown>", result.sourceFileOf(ClassName("com.example.Missing")))
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
            MethodRef(ClassName("com.example.Orchestrator"), "run") to setOf(
                MethodRef(ClassName("com.example.StepA"), "execute"),
                MethodRef(ClassName("com.example.StepB"), "execute"),
            ),
        )
        val graph = CallGraph(edges, emptyMap())

        CallGraphCache.write(cacheFile, graph)
        val result = CallGraphCache.read(cacheFile)

        val callees = result.calleesOf(ClassName("com.example.Orchestrator"), "run")
        assertEquals(2, callees.size)
        val calleeNames = callees.map { it.className.value }.toSet()
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

        assertTrue(result.data.calleesOf(ClassName("any.Class"), "anyMethod").isEmpty())
    }

    @Test
    fun `cache round-trip preserves transitive caller chains for same-class methods`() {
        val edges = mapOf(
            MethodRef(ClassName("com.example.UserService"), "sendResetNotification") to
                setOf(MethodRef(ClassName("com.example.UserService"), "buildNotificationMessage")),
            MethodRef(ClassName("com.example.UserService"), "sendDeactivationNotification") to
                setOf(MethodRef(ClassName("com.example.UserService"), "buildNotificationMessage")),
            MethodRef(ClassName("com.example.UserService"), "resetPassword") to
                setOf(MethodRef(ClassName("com.example.UserService"), "sendResetNotification")),
            MethodRef(ClassName("com.example.UserService"), "deactivateUser") to
                setOf(MethodRef(ClassName("com.example.UserService"), "sendDeactivationNotification")),
            MethodRef(ClassName("com.example.UserRoute"), "handleReset") to
                setOf(MethodRef(ClassName("com.example.UserService"), "resetPassword")),
            MethodRef(ClassName("com.example.UserRoute"), "handleDeactivate") to
                setOf(MethodRef(ClassName("com.example.UserService"), "deactivateUser")),
        )
        val sourceFiles = mapOf(
            ClassName("com.example.UserService") to "UserService.kt",
            ClassName("com.example.UserRoute") to "UserRoute.kt",
        )
        val original = CallGraph(edges, sourceFiles)

        CallGraphCache.write(cacheFile, original)
        val restored = CallGraphCache.read(cacheFile)

        // Verify transitive chain: buildNotificationMessage ← sendResetNotification ← resetPassword ← handleReset
        val directCallers = restored.callersOf(ClassName("com.example.UserService"), "buildNotificationMessage")
        assertEquals(2, directCallers.size, "Expected 2 direct callers")

        val resetCallers = restored.callersOf(ClassName("com.example.UserService"), "sendResetNotification")
        assertEquals(1, resetCallers.size)
        assertEquals("resetPassword", resetCallers.first().methodName)

        val passwordCallers = restored.callersOf(ClassName("com.example.UserService"), "resetPassword")
        assertEquals(1, passwordCallers.size)
        assertEquals("handleReset", passwordCallers.first().methodName)

        // Full tree from restored cache should show all levels
        val methods = listOf(MethodRef(ClassName("com.example.UserService"), "buildNotificationMessage"))
        val result = CallTreeFormatter.format(restored, methods, maxDepth = 5, direction = CallDirection.CALLERS)
        assertTrue(result.contains("resetPassword"), "Tree should show resetPassword at depth 2, got:\n$result")
        assertTrue(result.contains("handleReset"), "Tree should show handleReset at depth 3, got:\n$result")
    }

    @Test
    fun `writes and reads back line number mappings`() {
        val method = MethodRef(ClassName("com.example.Service"), "handle")
        val edges = mapOf(method to emptySet<MethodRef>())
        val sourceFiles = mapOf(ClassName("com.example.Service") to "Service.kt")
        val lineNumbers = mapOf(method to 42)
        val graph = CallGraph(edges, sourceFiles, lineNumbers)

        CallGraphCache.write(cacheFile, graph)
        val result = CallGraphCache.read(cacheFile)

        assertEquals(42, result.lineNumberOf(method))
    }

    @Test
    fun `line number is null for methods not in cache`() {
        val graph = CallGraph(emptyMap(), emptyMap())

        CallGraphCache.write(cacheFile, graph)
        val result = CallGraphCache.read(cacheFile)

        assertEquals(null, result.lineNumberOf(MethodRef(ClassName("com.example.Missing"), "method")))
    }

    @Test
    fun `writes and reads back source set mappings`() {
        val sourceFiles = mapOf(
            ClassName("com.example.Service") to "Service.kt",
            ClassName("com.example.ServiceTest") to "ServiceTest.kt",
        )
        val sourceSetsMap = mapOf(
            ClassName("com.example.Service") to SourceSet.MAIN,
            ClassName("com.example.ServiceTest") to SourceSet.TEST,
        )
        val graph = CallGraph(emptyMap(), sourceFiles, emptyMap(), sourceSetsMap)

        CallGraphCache.write(cacheFile, graph)
        val result = CallGraphCache.read(cacheFile)

        assertEquals(SourceSet.MAIN, result.sourceSetOf(ClassName("com.example.Service")))
        assertEquals(SourceSet.TEST, result.sourceSetOf(ClassName("com.example.ServiceTest")))
        assertEquals(null, result.sourceSetOf(ClassName("com.example.Missing")))
    }

    @Test
    fun `reads cache without source sets section for backward compatibility`() {
        val graph = CallGraph(emptyMap(), mapOf(ClassName("com.example.Service") to "Service.kt"))

        CallGraphCache.write(cacheFile, graph)
        val result = CallGraphCache.read(cacheFile)

        assertEquals(null, result.sourceSetOf(ClassName("com.example.Service")))
    }

    @Test
    fun `writes and reads back declared methods`() {
        val sourceFiles = mapOf(ClassName("com.example.Service") to "Service.kt")
        val declaredMethods = mapOf(
            ClassName("com.example.Service") to setOf("process", "validate", "<init>"),
        )
        val graph = CallGraph(emptyMap(), sourceFiles, emptyMap(), emptyMap(), declaredMethods)

        CallGraphCache.write(cacheFile, graph)
        val result = CallGraphCache.read(cacheFile)

        val declared = result.declaredMethodsOf(ClassName("com.example.Service"))
        assertEquals(setOf("process", "validate", "<init>"), declared)
        assertTrue(result.declaredMethodsOf(ClassName("com.example.Missing")).isEmpty())
    }

    @Test
    fun `getOrBuildTagged builds graph with source set tags when cache is stale`() {
        val mainDir = tempDir.resolve("main-classes").toFile()
        mainDir.mkdirs()
        writeClassFile("com/example/Service", mainDir)
        val testDir = tempDir.resolve("test-classes").toFile()
        testDir.mkdirs()
        writeClassFile("com/example/ServiceTest", testDir)

        val taggedDirs = listOf(mainDir to SourceSet.MAIN, testDir to SourceSet.TEST)
        val result = CallGraphCache.getOrBuildTagged(cacheFile, taggedDirs)

        assertEquals(SourceSet.MAIN, result.data.sourceSetOf(ClassName("com.example.Service")))
        assertEquals(SourceSet.TEST, result.data.sourceSetOf(ClassName("com.example.ServiceTest")))
    }

    @Test
    fun `getOrBuildTagged reads source sets from cache when fresh`() {
        val mainDir = tempDir.resolve("main-classes").toFile()
        mainDir.mkdirs()
        writeClassFile("com/example/Service", mainDir)
        val testDir = tempDir.resolve("test-classes").toFile()
        testDir.mkdirs()
        writeClassFile("com/example/ServiceTest", testDir)

        val taggedDirs = listOf(mainDir to SourceSet.MAIN, testDir to SourceSet.TEST)
        CallGraphCache.getOrBuildTagged(cacheFile, taggedDirs)
        Thread.sleep(50)
        cacheFile.setLastModified(System.currentTimeMillis())
        val result = CallGraphCache.getOrBuildTagged(cacheFile, taggedDirs)

        assertEquals(SourceSet.MAIN, result.data.sourceSetOf(ClassName("com.example.Service")))
        assertEquals(SourceSet.TEST, result.data.sourceSetOf(ClassName("com.example.ServiceTest")))
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
