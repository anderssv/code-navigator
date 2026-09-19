package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * Field report: "the confidence numbers make it look authoritative". An own=0 row rounds to a
 * confidence near 1.00 because it has no same-package edges to divide by — absence, not evidence.
 */
class MoveSuggestFormatterTest {

    private fun result(edgesToCurrent: Int) = MoveSuggestionResult(
        listOf(
            MoveSuggestion(
                className = ClassName("polls.PollsRepository"),
                currentPackage = PackageName("polls"),
                suggestedPackage = PackageName("polls.model"),
                edgesToCurrent = edgesToCurrent,
                edgesToSuggested = 4,
                confidence = 1.0,
            ),
        ),
    )

    @Test
    fun `an absence-driven suggestion is marked as weak in every format`() {
        assertContains(MoveSuggestFormatter.format(result(0)), "absence-driven")
        assertContains(MoveSuggestFormatter.formatLlm(result(0)), "evidence=absence-driven")
        assertContains(MoveSuggestFormatter.formatJson(result(0)), "\"absenceDriven\":true")
    }

    @Test
    fun `a suggestion backed by same-package edges is not marked`() {
        assertFalse(MoveSuggestFormatter.format(result(3)).contains("absence-driven"))
        assertFalse(MoveSuggestFormatter.formatLlm(result(3)).contains("evidence=absence-driven"))
        assertContains(MoveSuggestFormatter.formatJson(result(3)), "\"absenceDriven\":false")
    }

    @Test
    fun `the interpretation does not tell readers that low own-edges is a strong signal`() {
        val interpretation = MoveSuggestFormatter.MOVE_SUGGEST_INTERPRETATION

        assertFalse(interpretation.contains("low own-edges = strong signal"))
        assertContains(interpretation, "ADVISORY")
    }
}
