package no.f12.codenavigator.analysis

import kotlin.test.Test
import kotlin.test.assertEquals

class PackageVolatilityFormatterTest {

    @Test
    fun `empty list produces no-results message`() {
        val result = PackageVolatilityFormatter.format(PackageVolatilityResult(emptyList()))

        assertEquals("No package volatility data found.", result)
    }

    @Test
    fun `single entry produces header and one row`() {
        val entries = listOf(
            PackageVolatility("com.example.foo", revisions = 10, totalChurn = 200, fileCount = 3, avgRevisionsPerFile = 3.3),
        )

        val result = PackageVolatilityFormatter.format(PackageVolatilityResult(entries))

        val lines = result.lines()
        assertEquals(2, lines.size)
        assert(lines[0].contains("Package")) { "Header should contain 'Package'" }
        assert(lines[0].contains("Revisions")) { "Header should contain 'Revisions'" }
        assert(lines[0].contains("Churn")) { "Header should contain 'Churn'" }
        assert(lines[0].contains("Files")) { "Header should contain 'Files'" }
        assert(lines[0].contains("Avg Rev/File")) { "Header should contain 'Avg Rev/File'" }
        assert(lines[1].contains("com.example.foo"))
        assert(lines[1].contains("10"))
        assert(lines[1].contains("200"))
        assert(lines[1].contains("3.3"))
    }

    @Test
    fun `multiple entries produce aligned table`() {
        val entries = listOf(
            PackageVolatility("com.example.service", revisions = 25, totalChurn = 1200, fileCount = 5, avgRevisionsPerFile = 5.0),
            PackageVolatility("com.example.model", revisions = 10, totalChurn = 200, fileCount = 8, avgRevisionsPerFile = 1.25),
        )

        val result = PackageVolatilityFormatter.format(PackageVolatilityResult(entries))

        val lines = result.lines()
        assertEquals(3, lines.size)
        assert(lines[1].contains("com.example.service"))
        assert(lines[2].contains("com.example.model"))
    }

    @Test
    fun `average is formatted to one decimal place`() {
        val entries = listOf(
            PackageVolatility("com.example.foo", revisions = 7, totalChurn = 100, fileCount = 3, avgRevisionsPerFile = 2.3333333),
        )

        val result = PackageVolatilityFormatter.format(PackageVolatilityResult(entries))

        assert(result.contains("2.3")) { "Should format to one decimal: $result" }
        assert(!result.contains("2.33")) { "Should not show more than one decimal: $result" }
    }
}
