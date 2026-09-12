package no.f12.codenavigator.navigation.ambient

import no.f12.codenavigator.navigation.types.SourceSet
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AmbientOrchestratorTest {

    private val taggedDirs = listOf(File("test-project/build/classes/kotlin/main") to SourceSet.MAIN)
    private val projectDir = File("test-project")
    private val reportFile = File.createTempFile("cnav-ambient", ".txt").also { it.deleteOnExit() }

    private fun run(domain: String, categories: Set<AmbientCategory> = AmbientCategory.entries.toSet()) =
        AmbientOrchestrator.run(
            AmbientTaskConfig(
                domain = Regex(domain),
                categories = categories,
                exclude = null,
                detail = false,
                scope = no.f12.codenavigator.navigation.types.Scope.PROD,
                format = no.f12.codenavigator.config.OutputFormat.TEXT,
                failOnViolation = false,
                maxViolations = 0,
            ),
            taggedDirs,
            reportFile,
            projectDir,
        )

    @Test
    fun `finds every ambient read in the domain pattern end to end`() {
        val result = run("\\.variants\\.ambient\\.")

        assertNotNull(result.result)
        assertEquals(SubjectSource.DOMAIN_PATTERN, result.result!!.subjectSource)
        val labels = result.result!!.all.map { it.signal.label }.toSet()
        assertEquals(setOf("Instant.now()", "LocalDate.now()", "UUID.randomUUID()", "System.getenv()"), labels)
    }

    @Test
    fun `reports the project's clock injection as the established convention`() {
        val result = run("\\.variants\\.ambient\\.")

        assertEquals(ClockConvention.INJECTED, result.result!!.clockConvention)
    }

    @Test
    fun `narrows to the requested categories`() {
        val result = run("\\.variants\\.ambient\\.", categories = setOf(AmbientCategory.RANDOM))

        assertEquals(listOf("UUID.randomUUID()"), result.result!!.all.map { it.signal.label })
    }

    @Test
    fun `renders a report naming the file and line of each finding`() {
        val result = run("\\.variants\\.ambient\\.")

        val text = AmbientFormatter.formatText(result.result!!, detail = true)

        assertContains(text, "AmbientReads.kt:")
        assertContains(text, "ExpiryPolicy.isExpired")
    }

    @Test
    fun `a pattern matching nothing yields no findings rather than an error`() {
        val result = run("\\.no\\.such\\.package\\.")

        assertTrue(result.result!!.all.isEmpty())
        assertEquals(0, result.result!!.domainClassCount)
    }
}
