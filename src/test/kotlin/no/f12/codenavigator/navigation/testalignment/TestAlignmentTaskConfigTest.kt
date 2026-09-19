package no.f12.codenavigator.navigation.testalignment

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestAlignmentTaskConfigTest {

    @Test
    fun `defaults to text output with no exclusions and no CI gate`() {
        val config = TestAlignmentTaskConfig.parse(emptyMap())

        assertNull(config.exclude)
        assertEquals(OutputFormat.TEXT, config.format)
        assertEquals(false, config.failOnViolation)
        assertEquals(0, config.maxViolations)
    }

    @Test
    fun `parses the exclusion pattern and the CI gate`() {
        val config = TestAlignmentTaskConfig.parse(
            mapOf(
                "exclude" to "Journey|EndpointsTest",
                "format" to "llm",
                "fail-on-violation" to "true",
                "max-violations" to "3",
            ),
        )

        assertTrue(config.exclude!!.containsMatchIn("web.CalendarExportEndpointsTest"))
        assertEquals(OutputFormat.LLM, config.format)
        assertEquals(true, config.failOnViolation)
        assertEquals(3, config.maxViolations)
    }
}
