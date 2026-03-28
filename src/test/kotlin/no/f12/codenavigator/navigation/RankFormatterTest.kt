package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.rank.RankFormatter
import no.f12.codenavigator.navigation.rank.RankedType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RankFormatterTest {

    @Test
    fun `empty list produces no-results message`() {
        val output = RankFormatter.format(emptyList())

        assertEquals("No ranked types found.", output)
    }

    @Test
    fun `formats ranked types as columnar table`() {
        val ranked = listOf(
            RankedType(ClassName("com.example.Core"), 0.42, inDegree = 5, outDegree = 2),
            RankedType(ClassName("com.example.Service"), 0.15, inDegree = 2, outDegree = 3),
        )

        val output = RankFormatter.format(ranked)

        assertTrue(output.contains("com.example.Core"), "Should contain class name")
        assertTrue(output.contains("com.example.Service"), "Should contain class name")
        assertTrue(output.contains("Rank"), "Should contain header")
        assertTrue(output.contains("In"), "Should contain inDegree header")
        assertTrue(output.contains("Out"), "Should contain outDegree header")
    }

    @Test
    fun `rank values are formatted to 4 decimal places`() {
        val ranked = listOf(
            RankedType(ClassName("com.example.Core"), 0.42135678, inDegree = 1, outDegree = 0),
        )

        val output = RankFormatter.format(ranked)

        assertTrue(output.contains("0.4214"), "Rank should be formatted to 4 decimal places")
    }
}
