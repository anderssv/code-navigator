package no.f12.codenavigator.formatting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import no.f12.codenavigator.config.OutputFormat

class TaskGuidanceTest {

    // [TEST] Renders all sections when all are present
    @Test
    fun rendersAllSectionsWhenPresent() {
        val guidance = TaskGuidance(
            purpose = "Detects tests that bypass domain services.",
            parameterGuidance = "Set -Pports to match your boundary interfaces (e.g. .*Repository|.*Client).",
            interpretation = "Tests calling port methods directly use data-oriented setup.",
        )

        val rendered = guidance.render()

        assertContains(rendered, "Detects tests that bypass domain services.")
        assertContains(rendered, "Set -Pports to match your boundary interfaces")
        assertContains(rendered, "Tests calling port methods directly use data-oriented setup.")
    }

    // [TEST] Omits empty sections from rendered output
    @Test
    fun omitsEmptySections() {
        val guidance = TaskGuidance(
            purpose = "Checks coupling.",
            parameterGuidance = "",
            interpretation = "High coupling is bad.",
        )

        val rendered = guidance.render()

        assertContains(rendered, "Checks coupling.")
        assertContains(rendered, "High coupling is bad.")
        assertEquals(false, rendered.contains("\n\n"))
    }

    // [TEST] Renders with section headers for clarity
    @Test
    fun rendersSectionHeaders() {
        val guidance = TaskGuidance(
            purpose = "Checks coupling.",
            parameterGuidance = "Use -Pports to specify boundaries.",
            interpretation = "High coupling is bad.",
        )

        val rendered = guidance.render()

        assertContains(rendered, "Purpose:")
        assertContains(rendered, "Parameters:")
        assertContains(rendered, "Interpretation:")
    }

    // [TEST] Empty guidance renders as empty string
    @Test
    fun emptyGuidanceRendersEmpty() {
        val guidance = TaskGuidance(
            purpose = "",
            parameterGuidance = "",
            interpretation = "",
        )

        assertEquals("", guidance.render())
    }

    // [TEST] OutputWrapper includes guidance in LLM format
    @Test
    fun outputWrapperIncludesGuidanceInLlmFormat() {
        val guidance = TaskGuidance(
            purpose = "Detects TTTD violations.",
            parameterGuidance = "Set -Pports to match port interfaces.",
            interpretation = "Direct port calls from tests bypass the domain.",
        )

        val result = OutputWrapper.wrapWithGuidance("some results", OutputFormat.LLM, guidance)

        assertContains(result, "Purpose: Detects TTTD violations.")
        assertContains(result, "some results")
        assertContains(result, "---CNAV_BEGIN---")
    }

    // [TEST] OutputWrapper omits guidance in TEXT format
    @Test
    fun outputWrapperOmitsGuidanceInTextFormat() {
        val guidance = TaskGuidance(
            purpose = "Detects TTTD violations.",
            parameterGuidance = "Set -Pports.",
            interpretation = "Bad coupling.",
        )

        val result = OutputWrapper.wrapWithGuidance("some results", OutputFormat.TEXT, guidance)

        assertEquals("some results", result)
    }
}
