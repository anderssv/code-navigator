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
    lateinit var cacheDir: File

    private val minute = 60_000L

    @Test
    fun `first run has nothing to compare against and stores a snapshot`() {
        val comparison = RunHistory.record(cacheDir, "cycles", "fp", mapOf("cycles" to 3.0), now = 1_000)

        assertNull(comparison)
        assertTrue(File(cacheDir, "history/cycles.properties").isFile)
    }

    @Test
    fun `second run reports the delta against the first`() {
        RunHistory.record(cacheDir, "cycles", "fp", mapOf("cycles" to 3.0, "packages" to 12.0), now = 0)

        val comparison = assertNotNull(RunHistory.record(cacheDir, "cycles", "fp", mapOf("cycles" to 4.0, "packages" to 12.0), now = 5 * minute))

        assertEquals(5 * minute, comparison.ageMillis)
        assertEquals(listOf(MetricDelta("cycles", 3.0, 4.0), MetricDelta("packages", 12.0, 12.0)), comparison.deltas)
    }

    @Test
    fun `each run overwrites the snapshot so the next run compares against the latest one`() {
        RunHistory.record(cacheDir, "cycles", "fp", mapOf("cycles" to 3.0), now = 0)
        RunHistory.record(cacheDir, "cycles", "fp", mapOf("cycles" to 4.0), now = minute)

        val third = assertNotNull(RunHistory.record(cacheDir, "cycles", "fp", mapOf("cycles" to 4.0), now = 2 * minute))

        assertEquals(listOf(MetricDelta("cycles", 4.0, 4.0)), third.deltas)
    }

    @Test
    fun `a different fingerprint is incompatible so it overwrites instead of comparing`() {
        RunHistory.record(cacheDir, "dsm", "scope=prod", mapOf("packages" to 12.0), now = 0)

        assertNull(RunHistory.record(cacheDir, "dsm", "scope=all", mapOf("packages" to 400.0), now = minute))
        val next = assertNotNull(RunHistory.record(cacheDir, "dsm", "scope=all", mapOf("packages" to 401.0), now = 2 * minute))
        assertEquals(listOf(MetricDelta("packages", 400.0, 401.0)), next.deltas)
    }

    @Test
    fun `a corrupt snapshot file is overwritten without failing`() {
        File(cacheDir, "history").mkdirs()
        File(cacheDir, "history/cycles.properties").writeText("\u0000garbage not properties \\u12")

        assertNull(RunHistory.record(cacheDir, "cycles", "fp", mapOf("cycles" to 1.0), now = 0))
        assertNotNull(RunHistory.record(cacheDir, "cycles", "fp", mapOf("cycles" to 2.0), now = 1))
    }

    @Test
    fun `a snapshot from another schema version is overwritten`() {
        File(cacheDir, "history").mkdirs()
        File(cacheDir, "history/cycles.properties").writeText("schema=999\nfingerprint=fp\nsavedAt=0\nm.cycles=3.0\n")

        assertNull(RunHistory.record(cacheDir, "cycles", "fp", mapOf("cycles" to 1.0), now = 0))
    }

    @Test
    fun `a metric absent from the previous snapshot is new and one that vanished is gone`() {
        RunHistory.record(cacheDir, "metrics", "fp", mapOf("a" to 1.0, "old" to 5.0), now = 0)

        val comparison = assertNotNull(RunHistory.record(cacheDir, "metrics", "fp", mapOf("a" to 1.0, "added" to 2.0), now = 1))

        assertEquals(listOf(MetricDelta("a", 1.0, 1.0), MetricDelta("added", null, 2.0), MetricDelta("old", 5.0, null)), comparison.deltas)
    }

    @Test
    fun `tasks keep separate snapshots`() {
        RunHistory.record(cacheDir, "cycles", "fp", mapOf("n" to 1.0), now = 0)

        assertNull(RunHistory.record(cacheDir, "rings", "fp", mapOf("n" to 9.0), now = 1))
    }

    @Test
    fun `an unwritable cache location never throws`() {
        val blocker = File(cacheDir, "history").also { it.writeText("a file where the directory should be") }
        assertTrue(blocker.isFile)

        assertNull(RunHistory.record(cacheDir, "cycles", "fp", mapOf("cycles" to 1.0), now = 0))
    }

    @Test
    fun `report is null when disabled and writes nothing`() {
        assertNull(RunHistory.report(cacheDir, "cycles", "fp", mapOf("cycles" to 1.0), OutputFormat.TEXT, enabled = false))
        assertTrue(!File(cacheDir, "history").exists())
    }

    @Test
    fun `report is null on the first run`() {
        assertNull(RunHistory.report(cacheDir, "cycles", "fp", mapOf("cycles" to 1.0), OutputFormat.TEXT, enabled = true))
    }

    @Test
    fun `report is the delta line on the second run`() {
        RunHistory.record(cacheDir, "cycles", "fp", mapOf("cycles" to 3.0, "packages" to 12.0), now = 0)

        val text = RunHistory.report(cacheDir, "cycles", "fp", mapOf("cycles" to 4.0, "packages" to 12.0), OutputFormat.TEXT, enabled = true, now = 2 * 60 * minute)

        assertEquals("Compared to previous run (2h ago): cycles 3 → 4 (+1)", text)
    }

    @Test
    fun `fingerprint ignores presentation-only keys but not result-shaping ones`() {
        val base = mapOf("scope" to "prod", "format" to "text")
        assertEquals(RunHistory.fingerprint(base), RunHistory.fingerprint(base + ("format" to "llm") + ("no-compare" to "true")))
        assertTrue(RunHistory.fingerprint(base) != RunHistory.fingerprint(base + ("scope" to "all")))
    }
}
