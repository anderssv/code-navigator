package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.ClassName
import no.f12.codenavigator.navigation.core.SourceSet
import no.f12.codenavigator.navigation.core.SourceSetResolver
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceSetResolverTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `resolves main class to MAIN source set`() {
        val mainDir = tempDir.resolve("main").also { it.mkdirs() }
        TestClassWriter.writeClassFile(mainDir, "com/example/Service", "Service.kt")

        val resolver = SourceSetResolver.from(listOf(mainDir to SourceSet.MAIN))

        assertEquals(SourceSet.MAIN, resolver.sourceSetOf(ClassName("com.example.Service")))
    }

    @Test
    fun `resolves test class to TEST source set`() {
        val testDir = tempDir.resolve("test").also { it.mkdirs() }
        TestClassWriter.writeClassFile(testDir, "com/example/ServiceTest", "ServiceTest.kt")

        val resolver = SourceSetResolver.from(listOf(testDir to SourceSet.TEST))

        assertEquals(SourceSet.TEST, resolver.sourceSetOf(ClassName("com.example.ServiceTest")))
    }

    @Test
    fun `resolves classes from both main and test directories`() {
        val mainDir = tempDir.resolve("main").also { it.mkdirs() }
        val testDir = tempDir.resolve("test").also { it.mkdirs() }
        TestClassWriter.writeClassFile(mainDir, "com/example/Service", "Service.kt")
        TestClassWriter.writeClassFile(testDir, "com/example/ServiceTest", "ServiceTest.kt")

        val resolver = SourceSetResolver.from(listOf(
            mainDir to SourceSet.MAIN,
            testDir to SourceSet.TEST,
        ))

        assertEquals(SourceSet.MAIN, resolver.sourceSetOf(ClassName("com.example.Service")))
        assertEquals(SourceSet.TEST, resolver.sourceSetOf(ClassName("com.example.ServiceTest")))
    }

    @Test
    fun `returns null for unknown class`() {
        val mainDir = tempDir.resolve("main").also { it.mkdirs() }
        TestClassWriter.writeClassFile(mainDir, "com/example/Service", "Service.kt")

        val resolver = SourceSetResolver.from(listOf(mainDir to SourceSet.MAIN))

        assertNull(resolver.sourceSetOf(ClassName("com.example.Unknown")))
    }

    @Test
    fun `handles non-existent directory gracefully`() {
        val missing = tempDir.resolve("does-not-exist")

        val resolver = SourceSetResolver.from(listOf(missing to SourceSet.MAIN))

        assertNull(resolver.sourceSetOf(ClassName("com.example.Service")))
    }

    @Test
    fun `resolves class in default package`() {
        val mainDir = tempDir.resolve("main").also { it.mkdirs() }
        TestClassWriter.writeClassFile(mainDir, "NoPackageClass", "NoPackageClass.kt")

        val resolver = SourceSetResolver.from(listOf(mainDir to SourceSet.MAIN))

        assertEquals(SourceSet.MAIN, resolver.sourceSetOf(ClassName("NoPackageClass")))
    }

    @Test
    fun `provides flat directory list`() {
        val mainDir = tempDir.resolve("main").also { it.mkdirs() }
        val testDir = tempDir.resolve("test").also { it.mkdirs() }

        val resolver = SourceSetResolver.from(listOf(
            mainDir to SourceSet.MAIN,
            testDir to SourceSet.TEST,
        ))

        assertEquals(listOf(mainDir, testDir), resolver.classDirectories)
    }
}
