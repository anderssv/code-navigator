package no.f12.codenavigator.navigation.report

import no.f12.codenavigator.navigation.*
import no.f12.codenavigator.navigation.refactor.parseJsonObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportOrchestratorTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var classesDir: File
    private lateinit var cacheDir: File
    private lateinit var reportFile: File
    private lateinit var projectDir: File

    @BeforeEach
    fun setUp() {
        classesDir = tempDir.resolve("classes").toFile().apply { mkdirs() }
        cacheDir = tempDir.resolve("cnav").toFile().apply { mkdirs() }
        reportFile = File(cacheDir, "skipped.txt")
        projectDir = tempDir.resolve("project").toFile().apply { mkdirs() }

        // com.example.api.Controller <-> com.example.service.Service form a package cycle
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/api/Controller", "Controller.kt",
            "handle", listOf(Call("com/example/service/Service", "process", "()V")),
        )
        TestClassWriter.writeClassWithCalls(
            classesDir, "com/example/service/Service", "Service.kt",
            "process", listOf(Call("com/example/api/Controller", "handle", "()V")),
        )
    }

    private fun run(): ReportData = ReportOrchestrator.run(
        ReportConfig.parse(emptyMap()),
        classDirectories = listOf(classesDir),
        testClassDirectories = emptyList(),
        commits = emptyList(),
        cacheDir = cacheDir,
        reportFile = reportFile,
    )

    @Test
    fun `pipeline runs end to end and populates typed data`() {
        val data = run()

        assertTrue(data.cycles.isNotEmpty(), "The api<->service cycle should be detected")
    }

    @Test
    fun `TEXT output has the expected sections joined as markdown`() {
        val text = ReportFormatter.format(run())

        assertTrue(text.contains("## Metrics"))
        assertTrue(text.contains("## Cycles"))
        assertTrue(text.contains("## Rings"))
        assertTrue(text.contains("## Move Suggestions"))
        assertTrue(text.contains("## Dead Code"))
        assertTrue(text.contains("\n\n---\n\n"), "Sections should be separated by markdown rules")
    }

    @Test
    fun `JSON output is structured with a key per sub-analysis, not the markdown blob`() {
        val json = ReportFormatter.formatJson(run())

        // Must be a real JSON object that parses, with a named key per section — not '## Metrics' markdown.
        val parsed = parseJsonObject(json)
        assertTrue(parsed.containsKey("metrics"))
        assertTrue(parsed.containsKey("cycles"))
        assertTrue(parsed.containsKey("rings"))
        assertTrue(parsed.containsKey("deadCode"))
        assertTrue(!json.contains("## Metrics"), "JSON must not embed the markdown section headers")
    }

    @Test
    fun `JSON omits moveSuggestions and cohesion when absent`() {
        // Two-class fixture yields no move suggestions and no cohesion result.
        val data = run().copy(moveSuggestions = null, cohesion = null)
        val json = ReportFormatter.formatJson(data)

        assertTrue(!json.contains("moveSuggestions"))
        assertTrue(!json.contains("cohesion"))
    }

    @Test
    fun `LLM output sections each sub-analysis under a heading`() {
        val llm = ReportFormatter.formatLlm(run())

        assertTrue(llm.contains("# metrics"))
        assertTrue(llm.contains("# cycles"))
        assertTrue(llm.contains("# rings"))
        assertTrue(llm.contains("# dead-code"))
    }
}
