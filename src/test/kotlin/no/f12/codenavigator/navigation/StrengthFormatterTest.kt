package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.dsm.IntegrationStrength
import no.f12.codenavigator.navigation.dsm.PackageStrengthEntry
import no.f12.codenavigator.navigation.dsm.StrengthFormatter
import no.f12.codenavigator.navigation.dsm.StrengthResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrengthFormatterTest {

    @Test
    fun `empty result produces no-results message`() {
        val result = StrengthResult(emptyList())

        val output = StrengthFormatter.format(result)

        assertEquals("No inter-package dependencies found.", output)
    }

    @Test
    fun `formats single entry with strength level and counts`() {
        val result = StrengthResult(listOf(
            PackageStrengthEntry(
                source = PackageName("api"),
                target = PackageName("model"),
                strength = IntegrationStrength.MODEL,
                contractCount = 1,
                modelCount = 2,
                functionalCount = 0,
                unknownCount = 0,
                totalDeps = 3,
            ),
        ))

        val output = StrengthFormatter.format(result)

        assertTrue(output.contains("api"), "Should contain source package")
        assertTrue(output.contains("model"), "Should contain target package")
        assertTrue(output.contains("MODEL"), "Should contain strength level")
        assertTrue(output.contains("contract=1"), "Should contain contract count")
        assertTrue(output.contains("model=2"), "Should contain model count")
        assertTrue(output.contains("functional=0"), "Should contain functional count")
    }

    @Test
    fun `formats multiple entries on separate lines`() {
        val result = StrengthResult(listOf(
            PackageStrengthEntry(
                source = PackageName("api"),
                target = PackageName("service"),
                strength = IntegrationStrength.FUNCTIONAL,
                contractCount = 0,
                modelCount = 0,
                functionalCount = 3,
                unknownCount = 0,
                totalDeps = 3,
            ),
            PackageStrengthEntry(
                source = PackageName("api"),
                target = PackageName("model"),
                strength = IntegrationStrength.CONTRACT,
                contractCount = 2,
                modelCount = 0,
                functionalCount = 0,
                unknownCount = 0,
                totalDeps = 2,
            ),
        ))

        val output = StrengthFormatter.format(result)

        val lines = output.lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("service"))
        assertTrue(lines[1].contains("model"))
    }

    @Test
    fun `no-results hints when single package`() {
        val hints = StrengthFormatter.noResultsHints(1)

        assertEquals(1, hints.size)
        assertTrue(hints.first().contains("single package"))
    }

    @Test
    fun `no hints when multiple packages`() {
        val hints = StrengthFormatter.noResultsHints(3)

        assertTrue(hints.isEmpty())
    }

    @Test
    fun `shows unknown count when greater than zero`() {
        val result = StrengthResult(listOf(
            PackageStrengthEntry(
                source = PackageName("api"),
                target = PackageName("external"),
                strength = IntegrationStrength.CONTRACT,
                contractCount = 1,
                modelCount = 0,
                functionalCount = 0,
                unknownCount = 3,
                totalDeps = 4,
            ),
        ))

        val output = StrengthFormatter.format(result)

        assertTrue(output.contains("unknown=3"), "Should contain unknown count")
    }

    @Test
    fun `hides unknown count when zero`() {
        val result = StrengthResult(listOf(
            PackageStrengthEntry(
                source = PackageName("api"),
                target = PackageName("model"),
                strength = IntegrationStrength.MODEL,
                contractCount = 1,
                modelCount = 2,
                functionalCount = 0,
                unknownCount = 0,
                totalDeps = 3,
            ),
        ))

        val output = StrengthFormatter.format(result)

        assertTrue(!output.contains("unknown"), "Should not contain unknown when zero")
    }
}
