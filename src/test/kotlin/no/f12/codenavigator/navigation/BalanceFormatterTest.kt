package no.f12.codenavigator.navigation

import no.f12.codenavigator.navigation.core.PackageName
import no.f12.codenavigator.navigation.dsm.BalanceEntry
import no.f12.codenavigator.navigation.dsm.BalanceFormatter
import no.f12.codenavigator.navigation.dsm.BalanceResult
import no.f12.codenavigator.navigation.dsm.BalanceVerdict
import no.f12.codenavigator.navigation.dsm.IntegrationStrength
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BalanceFormatterTest {

    @Test
    fun `empty result produces no-results message`() {
        val result = BalanceResult(entries = emptyList())

        val output = BalanceFormatter.format(result)

        assertEquals("No balance findings.", output)
    }

    @Test
    fun `formats danger entry`() {
        val result = BalanceResult(
            entries = listOf(
                BalanceEntry(
                    source = PackageName("com.example.web"),
                    target = PackageName("com.example.persistence"),
                    strength = IntegrationStrength.FUNCTIONAL,
                    distance = 4,
                    sourceVolatility = 50,
                    targetVolatility = 40,
                    verdict = BalanceVerdict.DANGER,
                    suggestion = "Consider co-locating.",
                ),
            ),
        )

        val output = BalanceFormatter.format(result)

        assertEquals(
            "com.example.web → com.example.persistence  verdict=DANGER  strength=FUNCTIONAL  distance=4  volatility=50/40\n" +
                "  → Consider co-locating.",
            output,
        )
    }

    @Test
    fun `formats balanced entry without suggestion`() {
        val result = BalanceResult(
            entries = listOf(
                BalanceEntry(
                    source = PackageName("com.example.api"),
                    target = PackageName("com.example.infra"),
                    strength = IntegrationStrength.CONTRACT,
                    distance = 4,
                    sourceVolatility = 30,
                    targetVolatility = 20,
                    verdict = BalanceVerdict.BALANCED,
                    suggestion = "",
                ),
            ),
        )

        val output = BalanceFormatter.format(result)

        assertEquals(
            "com.example.api → com.example.infra  verdict=BALANCED  strength=CONTRACT  distance=4  volatility=30/20",
            output,
        )
    }

    @Test
    fun `no-results hints for single package`() {
        val hints = BalanceFormatter.noResultsHints(1)

        assertEquals(1, hints.size)
        assertEquals(
            "All classes are in a single package. Balanced coupling measures inter-package relationships, so there is nothing to display.",
            hints.first(),
        )
    }
}
