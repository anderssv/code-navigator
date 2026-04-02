package no.f12.codenavigator.navigation

import no.f12.codenavigator.analysis.PackageVolatility
import no.f12.codenavigator.analysis.PackageVolatilityResult
import no.f12.codenavigator.navigation.core.PackageName
import no.f12.codenavigator.navigation.dsm.BalanceBuilder
import no.f12.codenavigator.navigation.dsm.BalanceVerdict
import no.f12.codenavigator.navigation.dsm.IntegrationStrength
import no.f12.codenavigator.navigation.dsm.PackageDistanceEntry
import no.f12.codenavigator.navigation.dsm.PackageDistanceResult
import no.f12.codenavigator.navigation.dsm.PackageStrengthEntry
import no.f12.codenavigator.navigation.dsm.StrengthResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BalanceBuilderTest {

    // [TEST] Empty inputs produce empty result
    @Test
    fun `empty inputs produce empty result`() {
        val strength = StrengthResult(entries = emptyList())
        val distance = PackageDistanceResult(entries = emptyList())
        val volatility = PackageVolatilityResult(entries = emptyList())

        val result = BalanceBuilder.build(strength, distance, volatility)

        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `danger — functional strength plus high distance plus high volatility`() {
        val pkgA = PackageName("com.example.web.controllers")
        val pkgB = PackageName("com.example.infra.persistence")

        val strength = StrengthResult(
            entries = listOf(
                strengthEntry(pkgA, pkgB, IntegrationStrength.FUNCTIONAL, functional = 5),
            ),
        )
        val distance = PackageDistanceResult(
            entries = listOf(distanceEntry(pkgA, pkgB, distance = 4)),
        )
        val volatility = PackageVolatilityResult(
            entries = listOf(
                volatility("com.example.web.controllers", revisions = 50),
                volatility("com.example.infra.persistence", revisions = 40),
            ),
        )

        val result = BalanceBuilder.build(strength, distance, volatility)

        assertEquals(1, result.entries.size)
        val entry = result.entries.first()
        assertEquals(BalanceVerdict.DANGER, entry.verdict)
        assertEquals(IntegrationStrength.FUNCTIONAL, entry.strength)
        assertEquals(4, entry.distance)
        assertEquals(50, entry.sourceVolatility)
        assertEquals(40, entry.targetVolatility)
    }

    @Test
    fun `over-engineering — contract strength plus low distance plus low volatility`() {
        val pkgA = PackageName("com.example.service")
        val pkgB = PackageName("com.example.model")

        val strength = StrengthResult(
            entries = listOf(
                strengthEntry(pkgA, pkgB, IntegrationStrength.CONTRACT, contract = 3),
            ),
        )
        val distance = PackageDistanceResult(
            entries = listOf(distanceEntry(pkgA, pkgB, distance = 2)),
        )
        val volatility = PackageVolatilityResult(
            entries = listOf(
                volatility("com.example.service", revisions = 2),
                volatility("com.example.model", revisions = 1),
            ),
        )

        val result = BalanceBuilder.build(strength, distance, volatility)

        assertEquals(1, result.entries.size)
        assertEquals(BalanceVerdict.OVER_ENGINEERED, result.entries.first().verdict)
    }

    @Test
    fun `balanced — contract strength plus high distance`() {
        val pkgA = PackageName("com.example.web.api")
        val pkgB = PackageName("com.example.infra.db")

        val strength = StrengthResult(
            entries = listOf(
                strengthEntry(pkgA, pkgB, IntegrationStrength.CONTRACT, contract = 2),
            ),
        )
        val distance = PackageDistanceResult(
            entries = listOf(distanceEntry(pkgA, pkgB, distance = 4)),
        )
        val volatility = PackageVolatilityResult(
            entries = listOf(
                volatility("com.example.web.api", revisions = 30),
                volatility("com.example.infra.db", revisions = 20),
            ),
        )

        val result = BalanceBuilder.build(strength, distance, volatility)

        assertEquals(1, result.entries.size)
        assertEquals(BalanceVerdict.BALANCED, result.entries.first().verdict)
    }

    @Test
    fun `balanced — functional strength plus low distance`() {
        val pkgA = PackageName("com.example.order")
        val pkgB = PackageName("com.example.order.model")

        val strength = StrengthResult(
            entries = listOf(
                strengthEntry(pkgA, pkgB, IntegrationStrength.FUNCTIONAL, functional = 4),
            ),
        )
        val distance = PackageDistanceResult(
            entries = listOf(distanceEntry(pkgA, pkgB, distance = 1)),
        )
        val volatility = PackageVolatilityResult(
            entries = listOf(
                volatility("com.example.order", revisions = 20),
                volatility("com.example.order.model", revisions = 15),
            ),
        )

        val result = BalanceBuilder.build(strength, distance, volatility)

        assertEquals(1, result.entries.size)
        assertEquals(BalanceVerdict.BALANCED, result.entries.first().verdict)
    }

    @Test
    fun `tolerable — high strength high distance but low volatility`() {
        val pkgA = PackageName("com.example.web")
        val pkgB = PackageName("com.example.persistence")

        val strength = StrengthResult(
            entries = listOf(
                strengthEntry(pkgA, pkgB, IntegrationStrength.FUNCTIONAL, functional = 3),
            ),
        )
        val distance = PackageDistanceResult(
            entries = listOf(distanceEntry(pkgA, pkgB, distance = 3)),
        )
        // Low volatility: these packages have below-median revisions
        val volatility = PackageVolatilityResult(
            entries = listOf(
                volatility("com.example.web", revisions = 1),
                volatility("com.example.persistence", revisions = 1),
                volatility("com.example.other.active", revisions = 30),
                volatility("com.example.other.busy", revisions = 25),
                volatility("com.example.other.hot", revisions = 20),
            ),
        )

        val result = BalanceBuilder.build(strength, distance, volatility)

        assertEquals(1, result.entries.size)
        assertEquals(BalanceVerdict.TOLERABLE, result.entries.first().verdict)
    }

    @Test
    fun `missing volatility for package treated as zero`() {
        val pkgA = PackageName("com.example.web")
        val pkgB = PackageName("com.example.persistence")

        val strength = StrengthResult(
            entries = listOf(
                strengthEntry(pkgA, pkgB, IntegrationStrength.FUNCTIONAL, functional = 3),
            ),
        )
        val distance = PackageDistanceResult(
            entries = listOf(distanceEntry(pkgA, pkgB, distance = 4)),
        )
        val volatility = PackageVolatilityResult(entries = emptyList())

        val result = BalanceBuilder.build(strength, distance, volatility)

        assertEquals(1, result.entries.size)
        val entry = result.entries.first()
        assertEquals(0, entry.sourceVolatility)
        assertEquals(0, entry.targetVolatility)
        // With no volatility data, volatilityHigh=false (median=0), so TOLERABLE (not DANGER)
        assertEquals(BalanceVerdict.TOLERABLE, entry.verdict)
    }

    @Test
    fun `entries sorted by verdict severity descending`() {
        val pkgA = PackageName("com.example.web.controllers")
        val pkgB = PackageName("com.example.infra.persistence")
        val pkgC = PackageName("com.example.service")
        val pkgD = PackageName("com.example.model")

        val strength = StrengthResult(
            entries = listOf(
                strengthEntry(pkgC, pkgD, IntegrationStrength.CONTRACT, contract = 2),
                strengthEntry(pkgA, pkgB, IntegrationStrength.FUNCTIONAL, functional = 5),
            ),
        )
        val distance = PackageDistanceResult(
            entries = listOf(
                distanceEntry(pkgC, pkgD, distance = 2),
                distanceEntry(pkgA, pkgB, distance = 4),
            ),
        )
        val volatility = PackageVolatilityResult(
            entries = listOf(
                volatility("com.example.web.controllers", revisions = 50),
                volatility("com.example.infra.persistence", revisions = 40),
                volatility("com.example.service", revisions = 3),
                volatility("com.example.model", revisions = 1),
            ),
        )

        val result = BalanceBuilder.build(strength, distance, volatility)

        assertEquals(2, result.entries.size)
        assertEquals(BalanceVerdict.DANGER, result.entries[0].verdict)
        assertEquals(BalanceVerdict.OVER_ENGINEERED, result.entries[1].verdict)
    }

    @Test
    fun `top parameter limits results`() {
        val pkgA = PackageName("com.example.web.controllers")
        val pkgB = PackageName("com.example.infra.persistence")
        val pkgC = PackageName("com.example.service")
        val pkgD = PackageName("com.example.model")

        val strength = StrengthResult(
            entries = listOf(
                strengthEntry(pkgA, pkgB, IntegrationStrength.FUNCTIONAL, functional = 5),
                strengthEntry(pkgC, pkgD, IntegrationStrength.CONTRACT, contract = 2),
            ),
        )
        val distance = PackageDistanceResult(
            entries = listOf(
                distanceEntry(pkgA, pkgB, distance = 4),
                distanceEntry(pkgC, pkgD, distance = 2),
            ),
        )
        val volatility = PackageVolatilityResult(
            entries = listOf(
                volatility("com.example.web.controllers", revisions = 50),
                volatility("com.example.infra.persistence", revisions = 40),
                volatility("com.example.service", revisions = 3),
                volatility("com.example.model", revisions = 1),
            ),
        )

        val result = BalanceBuilder.build(strength, distance, volatility, top = 1)

        assertEquals(1, result.entries.size)
        assertEquals(BalanceVerdict.DANGER, result.entries.first().verdict)
    }


    private fun strengthEntry(
        source: PackageName,
        target: PackageName,
        strength: IntegrationStrength,
        contract: Int = 0,
        model: Int = 0,
        functional: Int = 0,
    ) = PackageStrengthEntry(
        source = source,
        target = target,
        strength = strength,
        contractCount = contract,
        modelCount = model,
        functionalCount = functional,
        unknownCount = 0,
        totalDeps = contract + model + functional,
    )

    private fun distanceEntry(
        source: PackageName,
        target: PackageName,
        distance: Int,
    ) = PackageDistanceEntry(
        source = source,
        target = target,
        distance = distance,
        dependencyCount = 1,
    )

    private fun volatility(
        packageName: String,
        revisions: Int,
    ) = PackageVolatility(
        packageName = packageName,
        revisions = revisions,
        totalChurn = revisions * 10,
        fileCount = 5,
        avgRevisionsPerFile = revisions / 5.0,
    )
}
