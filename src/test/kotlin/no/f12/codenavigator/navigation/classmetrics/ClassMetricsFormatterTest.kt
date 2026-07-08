package no.f12.codenavigator.navigation.classmetrics

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassMetricsFormatterTest {

    @Test
    fun `empty results produce a plain message`() {
        val result = ClassMetricsFormatter.format(emptyList())

        assertEquals("No matching classes found.", result)
    }

    @Test
    fun `single result renders class name and all metric columns`() {
        val entry = ClassMetricsResult(
            className = ClassName("com.example.OrderService"),
            packageName = PackageName("com.example"),
            totalMethods = 5,
            tcc = 0.12,
            lcc = 0.30,
            verdict = ClassCohesionVerdict.MONOLITH,
            wmc = 34,
            cbo = 12,
            dit = 3,
        )

        val result = ClassMetricsFormatter.format(listOf(entry))

        assertTrue(result.contains("com.example.OrderService"), "Should include class name, got: $result")
        assertTrue(result.contains("0.12"), "Should include TCC, got: $result")
        assertTrue(result.contains("0.30"), "Should include LCC, got: $result")
        assertTrue(result.contains("MONOLITH"), "Should include verdict, got: $result")
        assertTrue(result.contains("34"), "Should include WMC, got: $result")
        assertTrue(result.contains("12"), "Should include CBO, got: $result")
        assertTrue(result.contains("3"), "Should include DIT, got: $result")
    }
}
