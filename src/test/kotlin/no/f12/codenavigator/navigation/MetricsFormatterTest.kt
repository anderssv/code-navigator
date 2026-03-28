package no.f12.codenavigator.navigation

import no.f12.codenavigator.analysis.Hotspot
import no.f12.codenavigator.navigation.metrics.MetricsFormatter
import no.f12.codenavigator.navigation.metrics.MetricsResult
import kotlin.test.Test
import kotlin.test.assertTrue

class MetricsFormatterTest {

    @Test
    fun `formats metrics summary with all sections`() {
        val result = MetricsResult(
            totalClasses = 42,
            packageCount = 5,
            averageFanIn = 8.5,
            averageFanOut = 3.2,
            cycleCount = 2,
            deadClassCount = 3,
            deadMethodCount = 7,
            topHotspots = listOf(
                Hotspot("src/main/Foo.kt", 15, 200),
                Hotspot("src/main/Bar.kt", 10, 100),
            ),
        )

        val output = MetricsFormatter.format(result)

        val lines = output.lines()
        assertTrue(lines.any { it.contains("Classes") && it.contains("42") }, "Should show total classes")
        assertTrue(lines.any { it.contains("Packages") && it.contains("5") }, "Should show package count")
        assertTrue(lines.any { it.contains("fan-in") && it.contains("8.5") }, "Should show avg fan-in")
        assertTrue(lines.any { it.contains("fan-out") && it.contains("3.2") }, "Should show avg fan-out")
        assertTrue(lines.any { it.contains("Cycles") && it.contains("2") }, "Should show cycle count")
        assertTrue(lines.any { it.contains("Dead classes") && it.contains("3") }, "Should show dead classes")
        assertTrue(lines.any { it.contains("Dead methods") && it.contains("7") }, "Should show dead methods")
        assertTrue(lines.any { it.contains("Foo.kt") }, "Should show hotspot files")
        assertTrue(lines.any { it.contains("Bar.kt") }, "Should show hotspot files")
    }

    @Test
    fun `formats metrics with no hotspots`() {
        val result = MetricsResult(
            totalClasses = 10,
            packageCount = 2,
            averageFanIn = 0.0,
            averageFanOut = 0.0,
            cycleCount = 0,
            deadClassCount = 0,
            deadMethodCount = 0,
            topHotspots = emptyList(),
        )

        val output = MetricsFormatter.format(result)

        assertTrue(output.contains("Classes"), "Should show classes section")
        assertTrue(!output.contains("Top Hotspots"), "Should not show hotspots section")
    }

    @Test
    fun `formats averages with one decimal place`() {
        val result = MetricsResult(
            totalClasses = 10,
            packageCount = 2,
            averageFanIn = 3.333333,
            averageFanOut = 7.666666,
            cycleCount = 0,
            deadClassCount = 0,
            deadMethodCount = 0,
            topHotspots = emptyList(),
        )

        val output = MetricsFormatter.format(result)

        assertTrue(output.contains("3.3"), "Should format fan-in to one decimal")
        assertTrue(output.contains("7.7"), "Should format fan-out to one decimal")
    }
}
