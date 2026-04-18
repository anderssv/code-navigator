package no.f12.codenavigator.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DuplicateFormatterTest {

    @Test
    fun `formats empty result`() {
        assertEquals("No duplicates found.", DuplicateFormatter.format(emptyList()))
    }

    @Test
    fun `formats single group with two locations`() {
        val groups = listOf(
            DuplicateGroup(
                tokenCount = 25,
                locations = listOf(
                    DuplicateLocation("A.kt", 10, 15),
                    DuplicateLocation("B.kt", 20, 25),
                ),
            ),
        )

        val result = DuplicateFormatter.format(groups)

        assertTrue(result.contains("1 duplicate group(s) found:"))
        assertTrue(result.contains("Group 1: 25 tokens, 2 locations"))
        assertTrue(result.contains("A.kt:10-15"))
        assertTrue(result.contains("B.kt:20-25"))
    }
}
