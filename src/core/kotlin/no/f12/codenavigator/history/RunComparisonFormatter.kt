package no.f12.codenavigator.history

import no.f12.codenavigator.config.OutputFormat
import java.math.BigDecimal
import kotlin.math.abs

object RunComparisonFormatter {

    fun format(comparison: RunComparison, format: OutputFormat): String = when (format) {
        OutputFormat.JSON -> json(comparison)
        OutputFormat.TEXT, OutputFormat.LLM, OutputFormat.DIFF -> line(comparison)
    }

    private fun line(comparison: RunComparison): String {
        val changes = comparison.deltas.filter { it.changed }.map { describe(it) }
        val summary = if (changes.isEmpty()) "no change" else changes.joinToString(", ")
        return "Compared to previous run (${age(comparison.ageMillis)}): $summary"
    }

    private fun describe(delta: MetricDelta): String = when {
        delta.previous == null -> "${delta.name} new (${number(delta.current!!)})"
        delta.current == null -> "${delta.name} gone (was ${number(delta.previous)})"
        else -> "${delta.name} ${number(delta.previous)} → ${number(delta.current)} (${signed(delta.current - delta.previous)})"
    }

    private fun json(comparison: RunComparison): String {
        val changes = comparison.deltas.filter { it.changed }.joinToString(",") { d ->
            val previous = d.previous?.let(::number) ?: "null"
            val current = d.current?.let(::number) ?: "null"
            val delta = if (d.previous != null && d.current != null) number(d.current - d.previous) else "null"
            "{\"metric\":\"${d.name}\",\"previous\":$previous,\"current\":$current,\"delta\":$delta}"
        }
        return "---CNAV_COMPARE_BEGIN---\n" +
            "{\"comparison\":{\"previousRunAgeSeconds\":${comparison.ageMillis / 1000},\"changes\":[$changes]}}" +
            "\n---CNAV_COMPARE_END---"
    }

    /** Whole numbers without a decimal point; anything else rounded to two decimals, trailing zeros dropped. */
    private fun number(value: Double): String {
        if (value == Math.rint(value) && abs(value) < 1e15) return value.toLong().toString()
        return BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    private fun signed(delta: Double): String = if (delta > 0) "+${number(delta)}" else number(delta)

    private fun age(millis: Long): String {
        val seconds = millis / 1000
        return when {
            seconds < 60 -> "just now"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86_400 -> "${seconds / 3600}h ago"
            else -> "${seconds / 86_400}d ago"
        }
    }
}
