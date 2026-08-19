package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExecutePlanFormatterTest {

    @Test
    fun `errors from steps with zero changes are surfaced in text output`() {
        val result = ExecutePlanResult(
            steps = listOf(
                ExecutePlanStepResult(
                    from = "com.example.ValidationResult",
                    to = "com.example.moved.ValidationResult",
                    result = MoveClassResult(
                        changes = emptyList(),
                        error = "com.example.ValidationResult.kt also declares: Valid, Invalid. Use cnavMoveFile or cnavMoveClass --from-file.",
                    ),
                ),
            ),
            preview = false,
        )

        val text = ExecutePlanFormatter.format(result, OutputFormat.TEXT)

        assertTrue(text.contains("also declares: Valid, Invalid"), "Error should be surfaced in TEXT output, got: $text")
    }

    @Test
    fun `errors from steps with zero changes are surfaced in llm output`() {
        val result = ExecutePlanResult(
            steps = listOf(
                ExecutePlanStepResult(
                    from = "com.example.ValidationResult",
                    to = "com.example.moved.ValidationResult",
                    result = MoveClassResult(
                        changes = emptyList(),
                        error = "com.example.ValidationResult.kt also declares: Valid, Invalid. Use cnavMoveFile or cnavMoveClass --from-file.",
                    ),
                ),
            ),
            preview = false,
        )

        val llm = ExecutePlanFormatter.format(result, OutputFormat.LLM)

        assertTrue(llm.contains("also declares: Valid, Invalid"), "Error should be surfaced in LLM output, got: $llm")
    }

    @Test
    fun `errors from steps with zero changes are surfaced in json output`() {
        val result = ExecutePlanResult(
            steps = listOf(
                ExecutePlanStepResult(
                    from = "com.example.ValidationResult",
                    to = "com.example.moved.ValidationResult",
                    result = MoveClassResult(
                        changes = emptyList(),
                        error = "com.example.ValidationResult.kt also declares: Valid, Invalid. Use cnavMoveFile or cnavMoveClass --from-file.",
                    ),
                ),
            ),
            preview = false,
        )

        val json = ExecutePlanFormatter.format(result, OutputFormat.JSON)

        assertTrue(json.contains("also declares: Valid, Invalid"), "Error should be surfaced in JSON output, got: $json")
    }

    @Test
    fun `json output properly escapes special characters in error messages`() {
        val result = ExecutePlanResult(
            steps = listOf(
                ExecutePlanStepResult(
                    from = "com.example.A",
                    to = "com.example.moved.A",
                    result = MoveClassResult(
                        changes = emptyList(),
                        error = """path\to\file also declares: "Valid". Use cnavMoveFile.""",
                    ),
                ),
            ),
            preview = false,
        )

        val json = ExecutePlanFormatter.format(result, OutputFormat.JSON)

        assertTrue(json.contains("""path\\to\\file"""), "Backslashes should be escaped in JSON, got: $json")
        assertTrue(json.contains("""\"Valid\""""), "Double quotes should be escaped in JSON, got: $json")
    }

    @Test
    fun `allErrors collects errors across all steps`() {
        val result = ExecutePlanResult(
            steps = listOf(
                ExecutePlanStepResult(
                    from = "com.example.A",
                    to = "com.example.moved.A",
                    result = MoveClassResult(changes = emptyList(), error = "error A"),
                ),
                ExecutePlanStepResult(
                    from = "com.example.B",
                    to = "com.example.moved.B",
                    result = MoveClassResult(changes = emptyList()),
                ),
                ExecutePlanStepResult(
                    from = "com.example.C",
                    to = "com.example.moved.C",
                    result = MoveClassResult(changes = emptyList(), error = "error C"),
                ),
            ),
            preview = false,
        )

        assertEquals(listOf("error A", "error C"), result.allErrors)
    }

    @Test
    fun `allErrors is empty when no step has an error`() {
        val result = ExecutePlanResult(
            steps = listOf(
                ExecutePlanStepResult(
                    from = "com.example.A",
                    to = "com.example.moved.A",
                    result = MoveClassResult(changes = emptyList()),
                ),
            ),
            preview = false,
        )

        assertTrue(result.allErrors.isEmpty())
    }
}
