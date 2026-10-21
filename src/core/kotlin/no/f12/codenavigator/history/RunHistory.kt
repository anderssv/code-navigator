package no.f12.codenavigator.history

import no.f12.codenavigator.config.OutputFormat
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

data class MetricDelta(val name: String, val previous: Double?, val current: Double?) {
    val changed: Boolean get() = previous != current
}

data class RunComparison(val ageMillis: Long, val deltas: List<MetricDelta>)

/**
 * Keeps the previous result of an analysis task as a small flat snapshot (`metric -> number`) under
 * the project's cnav cache directory, so the next run can say what moved ("cycles 3 -> 4 (+1)").
 *
 * A snapshot is only comparable to the next run when its schema version and [fingerprint] (the
 * options that shape the result, e.g. scope/package filter) both match — comparing `--scope=prod` to
 * `--scope=all` would report a meaningless "+400". Anything else (no file, unreadable file, another
 * schema or fingerprint) is simply overwritten: this must never make an analysis fail.
 */
object RunHistory {

    private const val SCHEMA = 1
    private const val METRIC_PREFIX = "m."

    // Presentation/side-effect options that don't change what is counted.
    private val NON_RESULT_KEYS = setOf("format", "llm", "no-compare", "dsm-html", "detail")

    /** `--no-compare` is a flag: present (Gradle sends "true", Maven may send an empty value) unless explicitly "false". */
    fun enabled(properties: Map<String, String?>): Boolean = properties["no-compare"]?.let { it == "false" } ?: true

    fun fingerprint(properties: Map<String, String?>): String =
        properties.filterKeys { it !in NON_RESULT_KEYS }
            .filterValues { it != null }
            .toSortedMap()
            .entries.joinToString("&") { "${it.key}=${it.value}" }

    /** Compares against and then replaces the stored snapshot. Null when there was nothing comparable. */
    fun record(
        cacheDir: File,
        task: String,
        fingerprint: String,
        current: Map<String, Double>,
        now: Long = System.currentTimeMillis(),
    ): RunComparison? {
        val file = File(cacheDir, "history/$task.properties")
        val previous = read(file, fingerprint)
        write(file, fingerprint, current, now)
        return previous?.let { (savedAt, metrics) -> RunComparison(now - savedAt, deltas(metrics, current)) }
    }

    /**
     * The text to print after a task's own output, or null (first run, incompatible snapshot, or
     * [enabled] false — in which case nothing is read or written).
     */
    fun report(
        cacheDir: File,
        task: String,
        fingerprint: String,
        current: Map<String, Double>,
        format: OutputFormat,
        enabled: Boolean,
        now: Long = System.currentTimeMillis(),
    ): String? {
        if (!enabled) return null
        return record(cacheDir, task, fingerprint, current, now)?.let { RunComparisonFormatter.format(it, format) }
    }

    private fun deltas(previous: Map<String, Double>, current: Map<String, Double>): List<MetricDelta> =
        (previous.keys + current.keys).sorted().map { MetricDelta(it, previous[it], current[it]) }

    private fun read(file: File, fingerprint: String): Pair<Long, Map<String, Double>>? = try {
        if (!file.isFile) {
            null
        } else {
            val props = file.inputStream().use { Properties().apply { load(it) } }
            if (props.getProperty("schema") != SCHEMA.toString() || props.getProperty("fingerprint") != fingerprint) {
                null
            } else {
                val savedAt = props.getProperty("savedAt").toLong()
                val metrics = props.stringPropertyNames()
                    .filter { it.startsWith(METRIC_PREFIX) }
                    .associate { it.removePrefix(METRIC_PREFIX) to props.getProperty(it).toDouble() }
                savedAt to metrics
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun write(file: File, fingerprint: String, current: Map<String, Double>, now: Long) {
        try {
            val props = Properties().apply {
                setProperty("schema", SCHEMA.toString())
                setProperty("fingerprint", fingerprint)
                setProperty("savedAt", now.toString())
                current.forEach { (name, value) -> setProperty("$METRIC_PREFIX$name", value.toString()) }
            }
            file.parentFile.mkdirs()
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.outputStream().use { props.store(it, null) }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            // A history snapshot is a convenience; failing to keep one must never fail the analysis.
        }
    }
}
