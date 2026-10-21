package no.f12.codenavigator.history

import no.f12.codenavigator.config.OutputFormat
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** What one analysis run found: headline [metrics] plus, where the task has identifiable findings, the [items] behind them. */
data class RunSnapshot(
    val metrics: Map<String, Double>,
    val items: Map<String, Set<String>> = emptyMap(),
)

/** [itemsPruned]: the size budget stripped this run's [RunSnapshot.items]; its counts and timestamp are kept. */
data class StoredRun(
    val timestamp: Long,
    val fingerprint: String,
    val snapshot: RunSnapshot,
    val itemsPruned: Boolean = false,
)

data class MetricDelta(val name: String, val previous: Double?, val current: Double?) {
    val changed: Boolean get() = previous != current
}

data class ItemChange(val set: String, val item: String, val added: Boolean)

data class RunComparison(
    val baselineLabel: String,
    val baselineTimestamp: Long,
    val ageMillis: Long,
    val deltas: List<MetricDelta>,
    val itemChanges: List<ItemChange>,
    /** Set when item-level detail can't be given for this pair of runs. */
    val itemsNote: String? = null,
)

data class TrendPoint(val timestamp: Long?, val metrics: Map<String, Double>?)

/** Oldest to newest; the last point is the current run. */
data class RunTrend(val windowMillis: Long, val points: List<TrendPoint>)

sealed interface CompareWith {
    data object Previous : CompareWith
    data class RunsBack(val n: Int) : CompareWith
    data class AtLeastOld(val millis: Long, val text: String) : CompareWith
}

data class HistoryOptions(
    val enabled: Boolean = true,
    val compareWith: CompareWith = CompareWith.Previous,
    val trendWindowMillis: Long? = null,
    val trendWindowText: String? = null,
    /** Problems with the option values; reported, not fatal — an analysis never fails over its history. */
    val warnings: List<String> = emptyList(),
) {
    companion object {
        private val DURATION = Regex("""(\d+)([smhdw])""")

        fun from(properties: Map<String, String?>): HistoryOptions {
            val warnings = mutableListOf<String>()
            val enabled = properties["no-compare"]?.let { it == "false" } ?: true

            val compareText = properties["compare-with"]
            val compareWith = when {
                compareText.isNullOrBlank() -> CompareWith.Previous
                compareText.all { it.isDigit() } && compareText.toInt() >= 1 -> CompareWith.RunsBack(compareText.toInt())
                else -> parseDuration(compareText)?.let { CompareWith.AtLeastOld(it, compareText) }
                    ?: CompareWith.Previous.also { warnings += "Ignoring --compare-with=$compareText: expected a run count (3) or an age (12h, 7d, 2w)." }
            }

            val trendText = properties["trend"]
            val trendMillis = trendText?.takeIf { it.isNotBlank() }?.let { text ->
                parseDuration(text) ?: null.also { warnings += "Ignoring --trend=$text: expected an age window like 7d, 12h or 2w." }
            }
            return HistoryOptions(enabled, compareWith, trendMillis, trendText.takeIf { trendMillis != null }, warnings)
        }

        private fun parseDuration(text: String): Long? {
            val match = DURATION.matchEntire(text.trim().lowercase()) ?: return null
            val amount = match.groupValues[1].toLong()
            if (amount < 1) return null
            val unit = when (match.groupValues[2]) {
                "s" -> 1_000L
                "m" -> 60_000L
                "h" -> 3_600_000L
                "d" -> 86_400_000L
                else -> 7 * 86_400_000L
            }
            return amount * unit
        }
    }
}

/**
 * Keeps the last [MAX_RUNS_PER_FINGERPRINT] runs of an analysis task under `<project>/.cnav/history`, so a result
 * can be compared with the previous run, with an older one, or laid out as a trend.
 *
 * Runs are only comparable when their fingerprint — the options that shape the result (scope, package
 * filter, ...) — matches, so runs with different fingerprints share a file but never meet. An unreadable file or
 * one in another format is silently replaced: history is a convenience and must never fail an analysis.
 *
 * Counts are always kept. The per-run item lists (which cycles, which violations) are what make files grow, so
 * when a task's file exceeds [MAX_BYTES] the oldest runs lose their items first, and only then are whole old
 * runs dropped.
 */
object RunHistory {

    const val MAX_RUNS_PER_FINGERPRINT = 50
    const val MAX_BYTES = 1_000_000
    private const val FORMAT = "cnav-history\t2"
    private const val PRUNED = "pruned"
    private val NON_RESULT_KEYS = setOf("format", "llm", "no-compare", "compare-with", "trend", "dsm-html", "detail")

    fun fingerprint(properties: Map<String, String?>): String =
        properties.filterKeys { it !in NON_RESULT_KEYS }
            .filterValues { it != null }
            .toSortedMap()
            .entries.joinToString("&") { "${it.key}=${it.value}" }

    fun historyFile(projectDir: File, task: String): File = File(projectDir, ".cnav/history/$task.log")

    /**
     * Stores [snapshot] as the newest run and returns the earlier runs with the same fingerprint, oldest first
     * (empty when there are none or the file wasn't usable).
     */
    fun record(
        projectDir: File,
        task: String,
        fingerprint: String,
        snapshot: RunSnapshot,
        now: Long = System.currentTimeMillis(),
    ): List<StoredRun> {
        val file = historyFile(projectDir, task)
        val existing = read(file)
        write(file, prune(existing + StoredRun(now, fingerprint, snapshot)))
        return existing.filter { it.fingerprint == fingerprint }.sortedBy { it.timestamp }
    }

    /**
     * The text to print after a task's own output: a comparison and/or a trend, per [options]. Null when disabled
     * (nothing is read or written then) or when there is nothing to say yet.
     */
    fun report(
        projectDir: File,
        task: String,
        fingerprint: String,
        snapshot: RunSnapshot,
        format: OutputFormat,
        options: HistoryOptions,
        now: Long = System.currentTimeMillis(),
    ): String? {
        if (!options.enabled) return null
        val earlier = record(projectDir, task, fingerprint, snapshot, now)

        val baseline = select(earlier, options.compareWith, now)
        val comparison = baseline?.let { compare(it, snapshot, label(options.compareWith), now) }
        val trend = options.trendWindowMillis?.let { trend(earlier, snapshot, it, now) }
        val warnings = options.warnings

        if (comparison == null && trend == null && warnings.isEmpty()) return null
        return RunComparisonFormatter.format(comparison, trend, warnings, options.trendWindowText, format, now)
    }

    fun select(runs: List<StoredRun>, compareWith: CompareWith, now: Long): StoredRun? = when (compareWith) {
        CompareWith.Previous -> runs.lastOrNull()
        is CompareWith.RunsBack -> runs.getOrNull(runs.size - compareWith.n)
        is CompareWith.AtLeastOld -> runs.lastOrNull { now - it.timestamp >= compareWith.millis }
    }

    private fun label(compareWith: CompareWith): String = when (compareWith) {
        CompareWith.Previous -> "previous run"
        is CompareWith.RunsBack -> "run ${compareWith.n} back"
        is CompareWith.AtLeastOld -> "newest run at least ${compareWith.text} old"
    }

    fun compare(baseline: StoredRun, current: RunSnapshot, label: String, now: Long): RunComparison {
        val names = (baseline.snapshot.metrics.keys + current.metrics.keys).sorted()
        val deltas = names.map { MetricDelta(it, baseline.snapshot.metrics[it], current.metrics[it]) }

        val itemsNote: String?
        val changes: List<ItemChange>
        if (baseline.itemsPruned && current.items.isNotEmpty()) {
            itemsNote = "item detail for that run was dropped to keep the history small"
            changes = emptyList()
        } else {
            itemsNote = null
            changes = (baseline.snapshot.items.keys + current.items.keys).sorted().flatMap { set ->
                val before = baseline.snapshot.items[set].orEmpty()
                val after = current.items[set].orEmpty()
                (after - before).sorted().map { ItemChange(set, it, added = true) } +
                    (before - after).sorted().map { ItemChange(set, it, added = false) }
            }
        }
        return RunComparison(label, baseline.timestamp, now - baseline.timestamp, deltas, changes, itemsNote)
    }

    /** The current run preceded by the newest run at or before each of 2 and 1 windows ago. */
    fun trend(runs: List<StoredRun>, current: RunSnapshot, windowMillis: Long, now: Long): RunTrend {
        val points = (2 downTo 1).map { steps ->
            val run = runs.lastOrNull { it.timestamp <= now - steps * windowMillis }
            TrendPoint(run?.timestamp, run?.snapshot?.metrics)
        } + TrendPoint(now, current.metrics)
        return RunTrend(windowMillis, points)
    }

    // --- storage ---------------------------------------------------------------------------------------------

    private fun prune(runs: List<StoredRun>): List<StoredRun> {
        var kept = runs.groupBy { it.fingerprint }
            .flatMap { (_, group) -> group.sortedBy { it.timestamp }.takeLast(MAX_RUNS_PER_FINGERPRINT) }
            .sortedBy { it.timestamp }
        while (kept.isNotEmpty() && serialize(kept).toByteArray().size > MAX_BYTES) {
            val withItems = kept.indexOfFirst { it.snapshot.items.isNotEmpty() }
            kept = if (withItems >= 0) {
                kept.toMutableList().also {
                    it[withItems] = it[withItems].copy(snapshot = it[withItems].snapshot.copy(items = emptyMap()), itemsPruned = true)
                }
            } else {
                kept.drop(1)
            }
        }
        return kept
    }

    private fun serialize(runs: List<StoredRun>): String = buildString {
        appendLine(FORMAT)
        for (run in runs) {
            appendLine("run\t${run.timestamp}\t${escape(run.fingerprint)}")
            run.snapshot.metrics.forEach { (name, value) -> appendLine("m\t${escape(name)}\t$value") }
            run.snapshot.items.forEach { (set, items) -> items.sorted().forEach { appendLine("i\t${escape(set)}\t${escape(it)}") } }
            if (run.itemsPruned) appendLine(PRUNED)
        }
    }

    private fun read(file: File): List<StoredRun> = try {
        if (!file.isFile) {
            emptyList()
        } else {
            parse(file.readLines())
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun parse(lines: List<String>): List<StoredRun> {
        if (lines.firstOrNull() != FORMAT) return emptyList()
        val runs = mutableListOf<StoredRun>()
        var timestamp = 0L
        var fingerprint = ""
        var metrics = linkedMapOf<String, Double>()
        var items = linkedMapOf<String, MutableSet<String>>()
        var pruned = false
        var open = false

        fun close() {
            if (open) runs += StoredRun(timestamp, fingerprint, RunSnapshot(metrics, items), pruned)
        }
        for (line in lines.drop(1)) {
            val parts = line.split('\t')
            when (parts[0]) {
                "run" -> {
                    close()
                    timestamp = parts[1].toLong()
                    fingerprint = unescape(parts[2])
                    metrics = linkedMapOf(); items = linkedMapOf(); pruned = false; open = true
                }
                "m" -> { check(open); metrics[unescape(parts[1])] = parts[2].toDouble() }
                "i" -> { check(open); items.getOrPut(unescape(parts[1])) { linkedSetOf() }.add(unescape(parts[2])) }
                PRUNED -> { check(open); pruned = true }
                else -> error("unknown line")
            }
        }
        close()
        return runs
    }

    private fun write(file: File, runs: List<StoredRun>) {
        try {
            val firstUse = file.parentFile?.parentFile?.exists() == false
            file.parentFile.mkdirs()
            if (firstUse) {
                // Keeps the history out of version control without touching the project's own .gitignore;
                // delete this file to commit the history instead.
                File(file.parentFile.parentFile, ".gitignore").writeText("*\n")
            }
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(serialize(runs))
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            // A history file is a convenience; failing to keep one must never fail the analysis.
        }
    }

    private fun escape(text: String): String =
        text.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")

    private fun unescape(text: String): String = buildString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                append(when (text[i + 1]) { 't' -> '\t'; 'n' -> '\n'; 'r' -> '\r'; else -> text[i + 1] })
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
