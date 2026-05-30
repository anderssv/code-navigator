package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertTrue

class StructureFormatterTest {

    @Test
    fun `text format shows groups with target package and class list`() {
        val result = structureResult()

        val output = StructureFormatter.formatText(result)

        assertTrue(output.contains("com.app.domain"), "Should show target package")
        assertTrue(output.contains("UserMapper"), "Should list class names")
        assertTrue(output.contains("OrderMapper"), "Should list class names")
    }

    @Test
    fun `text format shows drift score`() {
        val result = structureResult()

        val output = StructureFormatter.formatText(result)

        assertTrue(output.contains("15%") || output.contains("15.0%"), "Should show drift percentage. Output:\n$output")
    }

    @Test
    fun `llm format includes interpretation`() {
        val result = structureResult()

        val output = StructureFormatter.formatLlm(result)

        assertTrue(output.contains("com.app.domain"), "Should show target package")
        assertTrue(output.contains("drift"), "Should mention drift. Output:\n$output")
    }

    @Test
    fun `json format is valid`() {
        val result = structureResult()

        val output = StructureFormatter.formatJson(result)

        assertTrue(output.startsWith("{"), "Should be JSON object")
        assertTrue(output.contains("\"driftScore\""), "Should have driftScore field")
        assertTrue(output.contains("\"groups\""), "Should have groups field")
    }

    private fun structureResult() = StructureResult(
        groups = listOf(
            StructureGroup(
                targetPackage = PackageName("com.app.domain"),
                classes = listOf(
                    MoveSuggestion(ClassName("com.app.web.UserMapper"), PackageName("com.app.web"), PackageName("com.app.domain"), 1, 4, 0.8),
                    MoveSuggestion(ClassName("com.app.web.OrderMapper"), PackageName("com.app.web"), PackageName("com.app.domain"), 1, 3, 0.75),
                ),
            ),
        ),
        driftScore = 0.15,
        totalClassCount = 20,
        misplacedCount = 3,
    )
}
