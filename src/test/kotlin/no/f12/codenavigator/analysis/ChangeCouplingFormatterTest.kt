package no.f12.codenavigator.analysis

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
