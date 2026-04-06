package no.f12.codenavigator.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChangeCouplingFormatterTest {

    @Test
    fun `formats empty list`() {
        val result = ChangeCouplingFormatter.format(emptyList())

        assertEquals("No coupling found.", result)
    }

    @Test
    fun `formats single coupling pair as table`() {
        val pairs = listOf(
            CoupledPair("src/Foo.kt", "src/Bar.kt", 85, 10, 12),
        )

        val result = ChangeCouplingFormatter.format(pairs)

        val lines = result.lines()
        assertEquals(2, lines.size)
        assert(lines[0].contains("Entity"))
        assert(lines[0].contains("Coupled"))
        assert(lines[0].contains("Degree"))
        assert(lines[0].contains("Shared"))
        assert(lines[1].contains("src/Foo.kt"))
        assert(lines[1].contains("src/Bar.kt"))
        assert(lines[1].contains("85%"))
        assert(lines[1].contains("10"))
    }

    @Test
    fun `formats multiple coupling pairs with newlines between rows`() {
        val pairs = listOf(
            CoupledPair("src/Foo.kt", "src/Bar.kt", 85, 10, 12),
            CoupledPair("src/Alpha.kt", "src/Beta.kt", 60, 5, 8),
            CoupledPair("src/One.kt", "src/Two.kt", 100, 20, 20),
        )

        val result = ChangeCouplingFormatter.format(pairs)

        val lines = result.lines()
        assertEquals(4, lines.size, "Header + 3 data rows")
        assert(lines[1].contains("src/Foo.kt"))
        assert(lines[2].contains("src/Alpha.kt"))
        assert(lines[3].contains("src/One.kt"))
        assert(lines[3].contains("100%"))
    }

    @Test
    fun `aligns columns based on widest values`() {
        val pairs = listOf(
            CoupledPair("short", "x", 1, 1, 1),
            CoupledPair("a-much-longer-entity-name", "also-a-longer-coupled-name", 99, 999, 100),
        )

        val result = ChangeCouplingFormatter.format(pairs)

        val lines = result.lines()
        // All lines should have consistent column alignment
        // The header "Entity" column should be padded to fit "a-much-longer-entity-name"
        assert(lines[0].contains("Entity"))
        assert(lines[1].contains("short"))
        assert(lines[2].contains("a-much-longer-entity-name"))
    }

    @Test
    fun `high coupling degree across directories shows recommendation`() {
        val pairs = listOf(
            CoupledPair("src/main/kotlin/services/Foo.kt", "src/main/kotlin/domain/Bar.kt", 80, 15, 18),
        )

        val result = ChangeCouplingFormatter.format(pairs)

        assertTrue(result.contains("High coupling"), "Should flag high coupling across directories, got:\n$result")
    }

    @Test
    fun `same directory pair shows recommendation with high coupling`() {
        val pairs = listOf(
            CoupledPair("src/main/kotlin/services/FooService.kt", "src/main/kotlin/services/FooRepository.kt", 85, 15, 18),
        )

        val result = ChangeCouplingFormatter.format(pairs)

        assertTrue(result.contains("High coupling"), "Same-directory production pairs should be flagged, got:\n$result")
    }

    @Test
    fun `test and main pair shows no recommendation even with high coupling`() {
        val pairs = listOf(
            CoupledPair("src/main/kotlin/services/FooService.kt", "src/test/kotlin/services/FooServiceTest.kt", 90, 20, 22),
        )

        val result = ChangeCouplingFormatter.format(pairs)

        assertFalse(result.contains("High coupling"), "Test+main pairs should not be flagged, got:\n$result")
    }

    @Test
    fun `test and main pair suppressed regardless of path order`() {
        val pairs = listOf(
            CoupledPair("src/test/kotlin/services/FooServiceTest.kt", "src/main/kotlin/services/FooService.kt", 90, 20, 22),
        )

        val result = ChangeCouplingFormatter.format(pairs)

        assertFalse(result.contains("High coupling"), "Test+main pairs should not be flagged regardless of order, got:\n$result")
    }

    @Test
    fun `moderate coupling shows no recommendation`() {
        val pairs = listOf(
            CoupledPair("src/Foo.kt", "src/Bar.kt", 50, 5, 10),
        )

        val result = ChangeCouplingFormatter.format(pairs)

        assertFalse(result.contains("High coupling"), "Should not flag moderate coupling")
    }
}
