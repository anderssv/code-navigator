package no.f12.codenavigator.navigation.classmetrics

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.Scope
import org.junit.jupiter.api.io.TempDir
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassMetricsOrchestratorTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var classesDir: File
    private lateinit var reportFile: File

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        classesDir = tempDir.resolve("classes").toFile()
        classesDir.mkdirs()
        reportFile = tempDir.resolve("skipped.txt").toFile()

        // A single trivial class: totalMethods=1, wmc=1, tcc=0.0, cbo=0, dit=0.
        val writer = ClassWriter(0)
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "com/example/Trivial", null, "java/lang/Object", null)
        val mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "doIt", "()V", null, null)
        mv.visitCode()
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(0, 1)
        mv.visitEnd()
        writer.visitEnd()
        File(classesDir, "com/example").mkdirs()
        File(classesDir, "com/example/Trivial.class").writeBytes(writer.toByteArray())
    }

    private fun config(minMethods: Int = 0, minTcc: Double = 0.0, maxWmc: Int = Int.MAX_VALUE, maxCbo: Int = Int.MAX_VALUE) =
        ClassMetricsConfig(
            minMethods = minMethods,
            minTcc = minTcc,
            maxWmc = maxWmc,
            maxCbo = maxCbo,
            scope = Scope.ALL,
            format = OutputFormat.TEXT,
        )

    @Test
    fun `default config includes the class`() {
        val output = ClassMetricsOrchestrator.run(config(), listOf(classesDir), reportFile)

        assertEquals(1, output.results.size)
    }

    @Test
    fun `minMethods filters out classes below the threshold`() {
        val output = ClassMetricsOrchestrator.run(config(minMethods = 2), listOf(classesDir), reportFile)

        assertTrue(output.results.isEmpty())
    }

    @Test
    fun `minTcc filters out classes below the cohesion threshold`() {
        val output = ClassMetricsOrchestrator.run(config(minTcc = 0.5), listOf(classesDir), reportFile)

        assertTrue(output.results.isEmpty())
    }

    @Test
    fun `maxWmc filters out classes above the complexity threshold`() {
        val output = ClassMetricsOrchestrator.run(config(maxWmc = 0), listOf(classesDir), reportFile)

        assertTrue(output.results.isEmpty())
    }

    @Test
    fun `maxCbo does not filter out a class at cbo zero`() {
        val output = ClassMetricsOrchestrator.run(config(maxCbo = 0), listOf(classesDir), reportFile)

        assertEquals(1, output.results.size)
    }
}
