package no.f12.codenavigator.navigation.relations.callgraph

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.TestClassWriter
import no.f12.codenavigator.navigation.Call
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.GroupBy
import no.f12.codenavigator.navigation.types.Scope
import no.f12.codenavigator.navigation.types.SourceSet
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FindUsagesOrchestratorTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var classesDir: File
    private lateinit var cacheDir: File

    @BeforeEach
    fun setUp() {
        classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
        cacheDir = tempDir.resolve("cnav").toFile()
        cacheDir.mkdirs()
    }

    // [TEST] Returns empty result when no usages found
    @Test
    fun `returns empty result when no usages found`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/Target", "Target.kt")

        val config = FindUsagesConfig(
            ownerClass = "com.example.Target",
            method = "nonExistent",
            field = null,
            type = null,
            outsidePackage = null,
            filterSynthetic = false,
            scope = Scope.ALL,
            groupBy = GroupBy.NONE,
            raw = false,
            includeImpls = false,
            format = OutputFormat.TEXT,
        )
        val taggedDirs = listOf(classesDir to null as SourceSet?)

        val result = FindUsagesOrchestrator.run(config, taggedDirs, cacheDir)

        assertTrue(result.usages.isEmpty())
        assertTrue(result.implementations.isEmpty())
        assertTrue(result.collapsed.isEmpty())
    }

    @Test
    fun `finds usages for owner-class and method`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/Target", "Target.kt")
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", listOf(Call("com/example/Target", "process", "()V")),
        )

        val config = FindUsagesConfig(
            ownerClass = "com.example.Target",
            method = "process",
            field = null,
            type = null,
            outsidePackage = null,
            filterSynthetic = false,
            scope = Scope.ALL,
            groupBy = GroupBy.NONE,
            raw = false,
            includeImpls = false,
            format = OutputFormat.TEXT,
        )
        val taggedDirs = listOf(classesDir to null as SourceSet?)

        val result = FindUsagesOrchestrator.run(config, taggedDirs, cacheDir)

        assertEquals(1, result.usages.size)
        assertEquals("process", result.usages[0].targetName)
        assertEquals(1, result.collapsed.size)
    }

    @Test
    fun `returns empty collapsed list when raw is true`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/Target", "Target.kt")
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", listOf(Call("com/example/Target", "process", "()V")),
        )

        val config = FindUsagesConfig(
            ownerClass = "com.example.Target",
            method = "process",
            field = null,
            type = null,
            outsidePackage = null,
            filterSynthetic = false,
            scope = Scope.ALL,
            groupBy = GroupBy.NONE,
            raw = true,
            includeImpls = false,
            format = OutputFormat.TEXT,
        )
        val taggedDirs = listOf(classesDir to null as SourceSet?)

        val result = FindUsagesOrchestrator.run(config, taggedDirs, cacheDir)

        assertEquals(1, result.usages.size)
        assertTrue(result.collapsed.isEmpty())
    }

    @Test
    fun `filters usages by outside-package`() {
        TestClassWriter.writeClassFile(classesDir, "com/example/Target", "Target.kt")
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", listOf(Call("com/example/Target", "process", "()V")),
        )
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/other/ExternalCaller", "ExternalCaller.kt",
            "invoke", listOf(Call("com/example/Target", "process", "()V")),
        )

        val config = FindUsagesConfig(
            ownerClass = "com.example.Target",
            method = "process",
            field = null,
            type = null,
            outsidePackage = "com.example",
            filterSynthetic = false,
            scope = Scope.ALL,
            groupBy = GroupBy.NONE,
            raw = false,
            includeImpls = false,
            format = OutputFormat.TEXT,
        )
        val taggedDirs = listOf(classesDir to null as SourceSet?)

        val result = FindUsagesOrchestrator.run(config, taggedDirs, cacheDir)

        assertEquals(1, result.usages.size)
        assertEquals(ClassName("com.other.ExternalCaller"), result.usages[0].callerClass)
    }

    @Test
    fun `expands interface implementors when include-impls is true`() {
        // Write interface
        TestClassWriter.writeClassFile(
            classesDir, "com/example/Service", "Service.kt",
            access = org.objectweb.asm.Opcodes.ACC_PUBLIC or org.objectweb.asm.Opcodes.ACC_INTERFACE or org.objectweb.asm.Opcodes.ACC_ABSTRACT,
        )
        // Write implementor
        TestClassWriter.writeClassFile(
            classesDir, "com/example/ServiceImpl", "ServiceImpl.kt",
            interfaces = arrayOf("com/example/Service"),
        )
        // Write caller that calls ServiceImpl
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/Caller", "Caller.kt",
            "doWork", listOf(Call("com/example/ServiceImpl", "execute", "()V")),
        )

        val config = FindUsagesConfig(
            ownerClass = "com.example.Service",
            method = "execute",
            field = null,
            type = null,
            outsidePackage = null,
            filterSynthetic = false,
            scope = Scope.ALL,
            groupBy = GroupBy.NONE,
            raw = false,
            includeImpls = true,
            format = OutputFormat.TEXT,
        )
        val taggedDirs = listOf(classesDir to null as SourceSet?)

        val result = FindUsagesOrchestrator.run(config, taggedDirs, cacheDir)

        assertTrue(result.implementations.isNotEmpty())
        assertTrue(result.interfaceTypes.contains(ClassName("com.example.Service")))
        assertEquals(1, result.usages.size)
    }
}
