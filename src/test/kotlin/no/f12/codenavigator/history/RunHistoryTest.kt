package no.f12.codenavigator.history

import no.f12.codenavigator.config.OutputFormat
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RunHistoryTest {

    @TempDir
    lateinit var project: File

    private val minute = 60_000L
    private val day = 86_400_000L

    private fun snap(cycles: Double, vararg cycleItems: String) =
        RunSnapshot(mapOf("cycles" to cycles), if (cycleItems.isEmpty()) emptyMap() else mapOf("cycle" to cycleItems.toSet()))

    private fun record(task: String = "cycles", fp: String = "fp", snapshot: RunSnapshot, now: Long) =
        RunHistory.record(project, task, fp, snapshot, now)

    @Test
    fun `first run has no earlier runs and creates a self-ignoring history folder`() {
        assertEquals(emptyList(), record(snapshot = snap(3.0), now = 1_000))

        assertTrue(RunHistory.historyFile(project, "cycles").isFile)
        assertEquals("*\n", File(project, ".cnav/.gitignore").readText())
    }

    @Test
    fun `existing gitignore in the history folder is left alone so history can be committed`() {
        record(snapshot = snap(1.0), now = 1)
        File(project, ".cnav/.gitignore").delete()

        record(snapshot = snap(2.0), now = 2)

        assertTrue(!File(project, ".cnav/.gitignore").exists())
    }

    @Test
    fun `every run is kept and the earlier ones come back oldest first`() {
        record(snapshot = snap(3.0), now = 0)
        record(snapshot = snap(4.0), now = minute)

        val earlier = record(snapshot = snap(5.0), now = 2 * minute)

        assertEquals(listOf(3.0, 4.0), earlier.map { it.snapshot.metrics["cycles"] })
        assertEquals(listOf(0L, minute), earlier.map { it.timestamp })
    }

    @Test
    fun `compare picks the previous run by default`() {
        val runs = listOf(StoredRun(0, "fp", snap(3.0)), StoredRun(minute, "fp", snap(4.0)))

        assertEquals(minute, RunHistory.select(runs, CompareWith.Previous, 2 * minute)?.timestamp)
    }

    @Test
    fun `compare with a run count goes that many runs back`() {
        val runs = (1..5).map { StoredRun(it * minute, "fp", snap(it.toDouble())) }

        assertEquals(3.0, RunHistory.select(runs, CompareWith.RunsBack(3), 10 * minute)?.snapshot?.metrics?.get("cycles"))
        assertNull(RunHistory.select(runs, CompareWith.RunsBack(6), 10 * minute))
    }

    @Test
    fun `compare with an age picks the newest run at least that old`() {
        val runs = listOf(StoredRun(0, "fp", snap(1.0)), StoredRun(5 * day, "fp", snap(2.0)), StoredRun(9 * day, "fp", snap(3.0)))

        assertEquals(5 * day, RunHistory.select(runs, CompareWith.AtLeastOld(7 * day, "7d"), now = 14 * day)?.timestamp)
        assertNull(RunHistory.select(runs, CompareWith.AtLeastOld(30 * day, "30d"), now = 14 * day))
    }

    @Test
    fun `counts are diffed per metric and new or vanished metrics are visible`() {
        val baseline = StoredRun(0, "fp", RunSnapshot(mapOf("a" to 1.0, "old" to 5.0)))

        val comparison = RunHistory.compare(baseline, RunSnapshot(mapOf("a" to 2.0, "added" to 2.0)), "previous run", now = minute)

        assertEquals(listOf(MetricDelta("a", 1.0, 2.0), MetricDelta("added", null, 2.0), MetricDelta("old", 5.0, null)), comparison.deltas)
    }

    @Test
    fun `items expose a swap that leaves the count unchanged`() {
        val baseline = StoredRun(0, "fp", snap(1.0, "polls ↔ web"))

        val comparison = RunHistory.compare(baseline, snap(1.0, "web ↔ web.admin"), "previous run", now = minute)

        assertEquals(listOf(MetricDelta("cycles", 1.0, 1.0)), comparison.deltas)
        assertEquals(
            listOf(ItemChange("cycle", "web ↔ web.admin", added = true), ItemChange("cycle", "polls ↔ web", added = false)),
            comparison.itemChanges,
        )
    }

    @Test
    fun `item diff is skipped with a note when the older run had its items pruned`() {
        val baseline = StoredRun(0, "fp", RunSnapshot(mapOf("cycles" to 1.0)), itemsPruned = true)

        val comparison = RunHistory.compare(baseline, snap(2.0, "a ↔ b"), "previous run", now = minute)

        assertEquals(emptyList(), comparison.itemChanges)
        assertNotNull(comparison.itemsNote)
    }

    @Test
    fun `runs with different fingerprints share a file but never meet`() {
        record(fp = "scope=prod", snapshot = snap(3.0), now = 0)
        record(fp = "scope=all", snapshot = snap(30.0), now = minute)

        val prod = record(fp = "scope=prod", snapshot = snap(4.0), now = 2 * minute)

        assertEquals(listOf(3.0), prod.map { it.snapshot.metrics["cycles"] })
    }

    @Test
    fun `tasks keep separate histories`() {
        record(task = "cycles", snapshot = snap(1.0), now = 0)

        assertEquals(emptyList(), record(task = "rings", snapshot = snap(9.0), now = 1))
    }

    @Test
    fun `only the last 50 runs per fingerprint are kept`() {
        repeat(60) { record(snapshot = snap(it.toDouble()), now = it * minute) }

        val earlier = record(snapshot = snap(60.0), now = 60 * minute)

        assertEquals(50, earlier.size)
        assertEquals(10.0, earlier.first().snapshot.metrics["cycles"])
        assertEquals(59.0, earlier.last().snapshot.metrics["cycles"])
    }

    @Test
    fun `a large history loses the items of its oldest runs first and keeps their counts`() {
        val big = (1..6_000).map { "com.example.package$it ↔ com.example.other$it" }.toTypedArray()
        repeat(12) { record(snapshot = snap(it.toDouble(), *big), now = it * minute) }

        val earlier = record(snapshot = snap(99.0, *big), now = 12 * minute)

        assertTrue(RunHistory.historyFile(project, "cycles").length() <= RunHistory.MAX_BYTES + 100_000)
        assertTrue(earlier.first().itemsPruned, "oldest run should have lost its items")
        assertEquals(emptyMap(), earlier.first().snapshot.items)
        assertEquals(0.0, earlier.first().snapshot.metrics["cycles"])
        assertTrue(!earlier.last().itemsPruned, "newest run keeps its items")
    }

    @Test
    fun `items with tabs, newlines and backslashes survive a round trip`() {
        val odd = "a\tb\nc\\d"
        record(snapshot = snap(1.0, odd), now = 0)

        val earlier = record(snapshot = snap(1.0, odd), now = 1)

        assertEquals(setOf(odd), earlier.single().snapshot.items["cycle"])
    }

    @Test
    fun `a corrupt or foreign file is replaced without failing`() {
        File(project, ".cnav/history").mkdirs()
        RunHistory.historyFile(project, "cycles").writeText("this is not a history\u0000file")

        assertEquals(emptyList(), record(snapshot = snap(1.0), now = 0))
        assertEquals(1, record(snapshot = snap(2.0), now = 1).size)
    }

    @Test
    fun `a file in another format version is replaced`() {
        File(project, ".cnav/history").mkdirs()
        RunHistory.historyFile(project, "cycles").writeText("cnav-history\t1\nrun\t0\tfp\nm\tcycles\t3.0\n")

        assertEquals(emptyList(), record(snapshot = snap(1.0), now = 0))
    }

    @Test
    fun `an unwritable history location never throws`() {
        File(project, ".cnav").writeText("a file where the directory should be")

        assertEquals(emptyList(), record(snapshot = snap(1.0), now = 0))
    }

    @Test
    fun `trend finds the newest run at or before each window boundary`() {
        val runs = listOf(
            StoredRun(0, "fp", snap(6.0)),
            StoredRun(8 * day, "fp", snap(5.0)),
            StoredRun(9 * day, "fp", snap(4.0)),
            StoredRun(20 * day, "fp", snap(3.5)),
        )

        val trend = RunHistory.trend(runs, snap(3.0), windowMillis = 7 * day, now = 21 * day)

        // boundaries are day 7 (two windows ago) and day 14 (one window ago)
        assertEquals(listOf(0L, 9 * day, 21 * day), trend.points.map { it.timestamp })
        assertEquals(listOf(6.0, 4.0, 3.0), trend.points.map { it.metrics?.get("cycles") })
    }

    @Test
    fun `trend leaves a point empty when no run is old enough`() {
        val runs = listOf(StoredRun(20 * day, "fp", snap(3.5)))

        val trend = RunHistory.trend(runs, snap(3.0), windowMillis = 7 * day, now = 21 * day)

        assertEquals(listOf(null, null, 21 * day), trend.points.map { it.timestamp })
        assertNull(trend.points[0].metrics)
    }

    @Test
    fun `report is null when disabled and writes nothing`() {
        assertNull(RunHistory.report(project, "cycles", "fp", snap(1.0), OutputFormat.TEXT, HistoryOptions(enabled = false)))
        assertTrue(!File(project, ".cnav").exists())
    }

    @Test
    fun `report is null on the first run and describes the change on the next`() {
        assertNull(RunHistory.report(project, "cycles", "fp", snap(3.0, "a ↔ b"), OutputFormat.TEXT, HistoryOptions(), now = 0))

        val text = RunHistory.report(project, "cycles", "fp", snap(4.0, "a ↔ b", "c ↔ d"), OutputFormat.TEXT, HistoryOptions(), now = 2 * 60 * minute)

        assertNotNull(text)
        assertTrue(text.contains("cycles 3 → 4 (+1)"), text)
        assertTrue(text.contains("+ cycle: c ↔ d"), text)
    }

    @Test
    fun `options parse run counts, ages and a trend window, and warn instead of failing on bad values`() {
        assertEquals(CompareWith.Previous, HistoryOptions.from(emptyMap()).compareWith)
        assertEquals(CompareWith.RunsBack(3), HistoryOptions.from(mapOf("compare-with" to "3")).compareWith)
        assertEquals(CompareWith.AtLeastOld(7 * day, "7d"), HistoryOptions.from(mapOf("compare-with" to "7d")).compareWith)
        assertEquals(14 * day, HistoryOptions.from(mapOf("trend" to "2w")).trendWindowMillis)
        assertTrue(!HistoryOptions.from(mapOf("no-compare" to "true")).enabled)
        assertTrue(HistoryOptions.from(mapOf("no-compare" to "false")).enabled)

        val bad = HistoryOptions.from(mapOf("compare-with" to "soon", "trend" to "weekly"))
        assertEquals(CompareWith.Previous, bad.compareWith)
        assertNull(bad.trendWindowMillis)
        assertEquals(2, bad.warnings.size)
    }

    @Test
    fun `fingerprint ignores presentation and history options but not result-shaping ones`() {
        val base = mapOf("scope" to "prod", "format" to "text")
        assertEquals(
            RunHistory.fingerprint(base),
            RunHistory.fingerprint(base + ("format" to "llm") + ("no-compare" to "true") + ("compare-with" to "7d") + ("trend" to "7d")),
        )
        assertTrue(RunHistory.fingerprint(base) != RunHistory.fingerprint(base + ("scope" to "all")))
    }
}
