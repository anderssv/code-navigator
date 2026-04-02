package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.PackageName
import no.f12.codenavigator.navigation.dsm.PackageDistanceEntry
import no.f12.codenavigator.navigation.dsm.PackageDistanceFormatter
import no.f12.codenavigator.navigation.dsm.PackageDistanceResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackageDistanceFormatterTest {

    @Test
    fun `empty result produces no-results message`() {
        val result = PackageDistanceResult(emptyList())

        val output = PackageDistanceFormatter.format(result)

        assertEquals("No inter-package dependencies found.", output)
    }

    @Test
    fun `formats single entry with distance and dependency count`() {
        val result = PackageDistanceResult(listOf(
            PackageDistanceEntry(PackageName("api"), PackageName("model"), 2, 3),
        ))

        val output = PackageDistanceFormatter.format(result)

        assertTrue(output.contains("api"))
        assertTrue(output.contains("model"))
        assertTrue(output.contains("2"))
        assertTrue(output.contains("3"))
    }

    @Test
    fun `formats multiple entries sorted by descending distance`() {
        val result = PackageDistanceResult(listOf(
            PackageDistanceEntry(PackageName("api.rest"), PackageName("infra.db"), 4, 2),
            PackageDistanceEntry(PackageName("api"), PackageName("model"), 2, 5),
        ))

        val output = PackageDistanceFormatter.format(result)

        val apiRestIdx = output.indexOf("api.rest")
        val apiIdx = output.indexOf("api →")
        assertTrue(apiRestIdx < apiIdx, "Higher distance should appear first")
    }

    @Test
    fun `shows prefix header when displayPrefix is non-empty`() {
        val result = PackageDistanceResult(
            entries = listOf(
                PackageDistanceEntry(PackageName("model"), PackageName("web"), 2, 3),
            ),
            displayPrefix = PackageName("org.springframework.samples.petclinic"),
        )

        val output = PackageDistanceFormatter.format(result)

        assertTrue(output.contains("Common prefix: org.springframework.samples.petclinic"), "Should show common prefix header, got:\n$output")
        assertTrue(output.contains("model"), "Should still show package entries")
    }

    @Test
    fun `omits prefix header when displayPrefix is empty`() {
        val result = PackageDistanceResult(
            entries = listOf(
                PackageDistanceEntry(PackageName("api"), PackageName("model"), 2, 3),
            ),
            displayPrefix = PackageName(""),
        )

        val output = PackageDistanceFormatter.format(result)

        assertTrue(!output.contains("Common prefix:"), "Should not show prefix header when empty")
        assertTrue(output.contains("api"), "Should still show entries")
    }

    @Test
    fun `no-results hints when single package`() {
        val hints = PackageDistanceFormatter.noResultsHints(1)

        assertEquals(1, hints.size)
        assertTrue(hints.first().contains("single package"))
    }

    @Test
    fun `no hints when multiple packages`() {
        val hints = PackageDistanceFormatter.noResultsHints(3)

        assertTrue(hints.isEmpty())
    }
}
