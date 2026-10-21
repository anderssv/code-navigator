package no.f12.codenavigator.history

import no.f12.codenavigator.config.OutputFormat
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

object RunComparisonFormatter {

    private const val MAX_ITEM_LINES = 10
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())

    fun format(
        comparison: RunComparison?,
        trend: RunTrend?,
        warnings: List<String>,
        trendWindowText: String?,
        format: OutputFormat,
        now: Long,
    ): String = when (format) {
        OutputFormat.JSON -> json(comparison, trend)
        OutputFormat.TEXT, OutputFormat.LLM, OutputFormat.DIFF -> text(comparison, trend, warnings, trendWindowText)
    }

    private fun text(comparison: RunComparison?, trend: RunTrend?, warnings: List<String>, trendWindowText: String?): String {
        val lines = mutableListOf<String>()
        lines += warnings
        if (comparison != null) lines += comparisonLines(comparison)
        if (trend != null) lines += trendLines(trend, trendWindowText ?: "")
        return lines.joinToString("\n")
    }

    private fun comparisonLines(c: RunComparison): List<String> {
        val changes = c.deltas.filter { it.changed }.map { describe(it) }
        val summary = if (changes.isEmpty() && c.itemChanges.isEmpty()) "no change" else changes.joinToString(", ").ifEmpty { "counts unchanged" }
        val lines = mutableListOf("Compared to ${c.baselineLabel} (${age(c.ageMillis)}, ${STAMP.format(Instant.ofEpochMilli(c.baselineTimestamp))}): $summary")
        c.itemChanges.take(MAX_ITEM_LINES).forEach { lines += "  ${if (it.added) "+" else "−"} ${it.set}: ${it.item}" }
        if (c.itemChanges.size > MAX_ITEM_LINES) lines += "  … and ${c.itemChanges.size - MAX_ITEM_LINES} more"
        c.itemsNote?.let { lines += "  ($it)" }
        return lines
    }

    private fun trendLines(trend: RunTrend, windowText: String): List<String> {
        val header = trend.points.joinToString(" → ") { p -> p.timestamp?.let { if (it == trend.points.last().timestamp) "now" else DAY.format(Instant.ofEpochMilli(it)) } ?: "–" }
        val names = trend.points.flatMap { it.metrics?.keys.orEmpty() }.distinct().sorted()
        val lines = mutableListOf("Trend (every $windowText): $header")
        for (name in names) {
            val values = trend.points.map { it.metrics?.get(name) }
            val first = values.firstOrNull { it != null }
            val last = values.last()
            val overall = if (first != null && last != null && values.count { it != null } > 1) " (${signed(last - first)})" else ""
            lines += "  $name ${values.joinToString(" → ") { it?.let(::number) ?: "–" }}$overall"
        }
        return lines
    }

    private fun describe(delta: MetricDelta): String = when {
        delta.previous == null -> "${delta.name} new (${number(delta.current!!)})"
        delta.current == null -> "${delta.name} gone (was ${number(delta.previous)})"
        else -> "${delta.name} ${number(delta.previous)} → ${number(delta.current)} (${signed(delta.current - delta.previous)})"
    }

    private fun json(comparison: RunComparison?, trend: RunTrend?): String {
        val parts = mutableListOf<String>()
        if (comparison != null) {
            val changes = comparison.deltas.filter { it.changed }.joinToString(",") { d ->
                val previous = d.previous?.let(::number) ?: "null"
                val current = d.current?.let(::number) ?: "null"
                val delta = if (d.previous != null && d.current != null) number(d.current - d.previous) else "null"
                "{\"metric\":\"${esc(d.name)}\",\"previous\":$previous,\"current\":$current,\"delta\":$delta}"
            }
            val items = comparison.itemChanges.joinToString(",") {
                "{\"set\":\"${esc(it.set)}\",\"item\":\"${esc(it.item)}\",\"change\":\"${if (it.added) "added" else "removed"}\"}"
            }
            parts += "\"baseline\":\"${esc(comparison.baselineLabel)}\",\"baselineTimestamp\":${comparison.baselineTimestamp}," +
                "\"previousRunAgeSeconds\":${comparison.ageMillis / 1000},\"changes\":[$changes],\"itemChanges\":[$items]" +
                (comparison.itemsNote?.let { ",\"itemsNote\":\"${esc(it)}\"" } ?: "")
        }
        if (trend != null) {
            val points = trend.points.joinToString(",") { p ->
                val metrics = p.metrics?.entries?.joinToString(",") { "\"${esc(it.key)}\":${number(it.value)}" } ?: ""
                "{\"timestamp\":${p.timestamp ?: "null"},\"metrics\":${if (p.metrics == null) "null" else "{$metrics}"}}"
            }
            parts += "\"trend\":{\"windowSeconds\":${trend.windowMillis / 1000},\"points\":[$points]}"
        }
        return "---CNAV_COMPARE_BEGIN---\n{\"comparison\":{${parts.joinToString(",")}}}\n---CNAV_COMPARE_END---"
    }

    private fun esc(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t")

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
