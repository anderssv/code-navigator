package no.f12.codenavigator.history

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunComparisonFormatterTest {

    private val minute = 60_000L

    private fun comparison(ageMillis: Long, vararg deltas: MetricDelta) = RunComparison(ageMillis, deltas.toList())

    @Test
    fun `only changed metrics are listed with signed deltas`() {
        val text = RunComparisonFormatter.format(
            comparison(2 * 60 * minute, MetricDelta("cycles", 3.0, 4.0), MetricDelta("violations", 14.0, 12.0), MetricDelta("packages", 7.0, 7.0)),
            OutputFormat.TEXT,
        )

        assertEquals("Compared to previous run (2h ago): cycles 3 → 4 (+1), violations 14 → 12 (-2)", text)
    }

    @Test
    fun `no changes says so`() {
        assertEquals(
            "Compared to previous run (just now): no change",
            RunComparisonFormatter.format(comparison(10_000, MetricDelta("cycles", 3.0, 3.0)), OutputFormat.TEXT),
        )
    }

    @Test
    fun `new and gone metrics are labelled`() {
        val text = RunComparisonFormatter.format(
            comparison(5 * minute, MetricDelta("added", null, 2.0), MetricDelta("old", 5.0, null)),
            OutputFormat.TEXT,
        )

        assertEquals("Compared to previous run (5m ago): added new (2), old gone (was 5)", text)
    }

    @Test
    fun `fractional values keep two decimals and whole ones none`() {
        val text = RunComparisonFormatter.format(comparison(minute, MetricDelta("avgFanIn", 2.5, 2.75)), OutputFormat.TEXT)

        assertEquals("Compared to previous run (1m ago): avgFanIn 2.5 → 2.75 (+0.25)", text)
    }

    @Test
    fun `ages round to a sensible unit`() {
        fun age(ms: Long) = RunComparisonFormatter.format(comparison(ms, MetricDelta("x", 1.0, 1.0)), OutputFormat.TEXT)
        assertTrue(age(30 * 1000L).contains("just now"))
        assertTrue(age(90 * minute).contains("1h ago"))
        assertTrue(age(3 * 24 * 60 * minute).contains("3d ago"))
    }

    @Test
    fun `llm uses the same single line as text`() {
        val c = comparison(minute, MetricDelta("cycles", 3.0, 4.0))
        assertEquals(RunComparisonFormatter.format(c, OutputFormat.TEXT), RunComparisonFormatter.format(c, OutputFormat.LLM))
    }

    @Test
    fun `json is a separate marked block with machine readable changes`() {
        val json = RunComparisonFormatter.format(comparison(60_000, MetricDelta("cycles", 3.0, 4.0), MetricDelta("packages", 7.0, 7.0)), OutputFormat.JSON)

        assertTrue(json.startsWith("---CNAV_COMPARE_BEGIN---\n"))
        assertTrue(json.endsWith("\n---CNAV_COMPARE_END---"))
        assertTrue(json.contains("\"previousRunAgeSeconds\":60"))
        assertTrue(json.contains("{\"metric\":\"cycles\",\"previous\":3,\"current\":4,\"delta\":1}"))
        assertTrue(!json.contains("\"packages\""), "unchanged metrics are not listed: $json")
    }
}
