package no.f12.codenavigator.history

import no.f12.codenavigator.config.OutputFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunComparisonFormatterTest {

    private val minute = 60_000L
    private val day = 86_400_000L
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

    private fun comparison(
        ageMillis: Long,
        deltas: List<MetricDelta>,
        items: List<ItemChange> = emptyList(),
        note: String? = null,
    ) = RunComparison("previous run", 0, ageMillis, deltas, items, note)

    private fun text(c: RunComparison?, trend: RunTrend? = null, warnings: List<String> = emptyList(), window: String? = null) =
        RunComparisonFormatter.format(c, trend, warnings, window, OutputFormat.TEXT, now = 0)

    private fun header(c: RunComparison) = "Compared to previous run (${"%s"}, ${stamp.format(Instant.ofEpochMilli(c.baselineTimestamp))})"

    @Test
    fun `only changed metrics are listed with signed deltas`() {
        val c = comparison(2 * 60 * minute, listOf(MetricDelta("cycles", 3.0, 4.0), MetricDelta("violations", 14.0, 12.0), MetricDelta("packages", 7.0, 7.0)))

        assertEquals("${header(c).format("2h ago")}: cycles 3 → 4 (+1), violations 14 → 12 (-2)", text(c))
    }

    @Test
    fun `no changes at all says so`() {
        val c = comparison(10_000, listOf(MetricDelta("cycles", 3.0, 3.0)))

        assertEquals("${header(c).format("just now")}: no change", text(c))
    }

    @Test
    fun `item changes are listed under the counts, added then removed`() {
        val c = comparison(
            5 * minute,
            listOf(MetricDelta("cycles", 1.0, 1.0)),
            listOf(ItemChange("cycle", "web ↔ admin", true), ItemChange("cycle", "polls ↔ web", false)),
        )

        val lines = text(c).lines()

        assertTrue(lines[0].endsWith(": counts unchanged"), lines[0])
        assertEquals("  + cycle: web ↔ admin", lines[1])
        assertEquals("  − cycle: polls ↔ web", lines[2])
    }

    @Test
    fun `a long item list is capped with a count of the rest`() {
        val items = (1..13).map { ItemChange("violation", "A$it -> B$it", true) }
        val lines = text(comparison(minute, listOf(MetricDelta("violations", 0.0, 13.0)), items)).lines()

        assertEquals(1 + 10 + 1, lines.size)
        assertEquals("  … and 3 more", lines.last())
    }

    @Test
    fun `new and gone metrics are labelled and fractions keep two decimals`() {
        val s = text(comparison(minute, listOf(MetricDelta("added", null, 2.0), MetricDelta("old", 5.0, null), MetricDelta("avgFanIn", 2.5, 2.75))))

        assertTrue(s.contains("added new (2), old gone (was 5), avgFanIn 2.5 → 2.75 (+0.25)"), s)
    }

    @Test
    fun `ages round to a sensible unit`() {
        fun age(ms: Long) = text(comparison(ms, listOf(MetricDelta("x", 1.0, 1.0))))
        assertTrue(age(30_000).contains("just now"))
        assertTrue(age(90 * minute).contains("1h ago"))
        assertTrue(age(3 * day).contains("3d ago"))
    }

    @Test
    fun `an item note is shown when detail was dropped`() {
        val s = text(comparison(minute, listOf(MetricDelta("cycles", 1.0, 2.0)), note = "item detail for that run was dropped to keep the history small"))

        assertTrue(s.lines().last().startsWith("  (item detail"), s)
    }

    @Test
    fun `trend lays values out oldest to newest with dates, dashes for missing and the overall change`() {
        val trend = RunTrend(
            7 * day,
            listOf(
                TrendPoint(0, mapOf("cycles" to 6.0, "packages" to 12.0)),
                TrendPoint(7 * day, mapOf("cycles" to 4.0, "packages" to 12.0)),
                TrendPoint(14 * day, mapOf("cycles" to 3.0, "packages" to 13.0)),
            ),
        )

        val lines = text(null, trend, window = "7d").lines()

        assertTrue(lines[0].startsWith("Trend (every 7d): "), lines[0])
        assertTrue(lines[0].endsWith("→ now"), lines[0])
        assertEquals("  cycles 6 → 4 → 3 (-3)", lines[1])
        assertEquals("  packages 12 → 12 → 13 (+1)", lines[2])
    }

    @Test
    fun `trend shows a dash where there is no run that old`() {
        val trend = RunTrend(7 * day, listOf(TrendPoint(null, null), TrendPoint(null, null), TrendPoint(14 * day, mapOf("cycles" to 3.0))))

        val lines = text(null, trend, window = "7d").lines()

        assertTrue(lines[0].startsWith("Trend (every 7d): – → – → now"), lines[0])
        assertEquals("  cycles – → – → 3", lines[1])
    }

    @Test
    fun `warnings come first`() {
        val s = text(comparison(minute, listOf(MetricDelta("x", 1.0, 2.0))), warnings = listOf("Ignoring --trend=weekly: nope"))

        assertEquals("Ignoring --trend=weekly: nope", s.lines().first())
    }

    @Test
    fun `llm uses the same text as text`() {
        val c = comparison(minute, listOf(MetricDelta("cycles", 3.0, 4.0)))
        assertEquals(text(c), RunComparisonFormatter.format(c, null, emptyList(), null, OutputFormat.LLM, 0))
    }

    @Test
    fun `json is a separate marked block with changes, items and trend`() {
        val c = comparison(
            60_000,
            listOf(MetricDelta("cycles", 3.0, 4.0), MetricDelta("packages", 7.0, 7.0)),
            listOf(ItemChange("cycle", "a ↔ b", true)),
        )
        val trend = RunTrend(7 * day, listOf(TrendPoint(null, null), TrendPoint(1, mapOf("cycles" to 3.0)), TrendPoint(2, mapOf("cycles" to 4.0))))

        val json = RunComparisonFormatter.format(c, trend, emptyList(), "7d", OutputFormat.JSON, 0)

        assertTrue(json.startsWith("---CNAV_COMPARE_BEGIN---\n") && json.endsWith("\n---CNAV_COMPARE_END---"))
        assertTrue(json.contains("\"previousRunAgeSeconds\":60"), json)
        assertTrue(json.contains("{\"metric\":\"cycles\",\"previous\":3,\"current\":4,\"delta\":1}"), json)
        assertTrue(!json.contains("\"metric\":\"packages\""), "unchanged metrics are not listed: $json")
        assertTrue(json.contains("{\"set\":\"cycle\",\"item\":\"a ↔ b\",\"change\":\"added\"}"), json)
        assertTrue(json.contains("\"trend\":{\"windowSeconds\":604800"), json)
    }
}
