package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.*

import no.f12.codenavigator.analysis.PackageVolatility
import no.f12.codenavigator.analysis.PackageVolatilityResult
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.dsm.BalanceBuilder
import no.f12.codenavigator.navigation.dsm.BalanceVerdict
import no.f12.codenavigator.navigation.dsm.IntegrationStrength
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
        val volatility = PackageVolatilityResult(entries = emptyList())

        val result = BalanceBuilder.build(strength, emptyMap(), emptySet(), volatility)

        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `danger — functional strength plus high ring separation plus high volatility`() {
        val pkgA = PackageName("com.example.web.controllers")
        val pkgB = PackageName("com.example.infra.persistence")

        val strength = StrengthResult(
            entries = listOf(
                strengthEntry(pkgA, pkgB, IntegrationStrength.FUNCTIONAL, functional = 5),
            ),
        )
        // pkgA at ring 3, pkgB at ring 0 → separation 3 (crosses the whole stack)
        val rings = mapOf(pkgA to 3, pkgB to 0)
        val volatility = PackageVolatilityResult(
            entries = listOf(
                volatility("com.example.web.controllers", revisions = 50),
                volatility("com.example.infra.persistence", revisions = 40),
            ),
        )

        val result = BalanceBuilder.build(strength, rings, emptySet(), volatility)

        assertEquals(1, result.entries.size)
        val entry = result.entries.first()
        assertEquals(BalanceVerdict.DANGER, entry.verdict)
        assertEquals(IntegrationStrength.FUNCTIONAL, entry.strength)
        assertEquals(3, entry.distance)
        assertEquals(50, entry.sourceVolatility)
        assertEquals(40, entry.targetVolatility)
    }

    @Test
    fun `deeply nested packages at the same ring are not danger`() {
        // Regression: lexical distance flagged ktor.routes.v1.bankid → services as DANGER
        // purely because of nesting depth. With ring-separation distance, co-located
        // features at the same ring score distance 0 — not danger.
        val pkgA = PackageName("com.example.ktor.routes.v1.bankid")
        val pkgB = PackageName("com.example.services")

        val strength = StrengthResult(
            entries = listOf(
                strengthEntry(pkgA, pkgB, IntegrationStrength.FUNCTIONAL, functional = 5),
            ),
        )
        // Both at ring 2 — a route calling a service in the same layer.
        val rings = mapOf(pkgA to 2, pkgB to 2)
        val volatility = PackageVolatilityResult(
            entries = listOf(
                volatility("com.example.ktor.routes.v1.bankid", revisions = 50),
                volatility("com.example.services", revisions = 40),
            ),
        )

        val result = BalanceBuilder.build(strength, rings, emptySet(), volatility)

        assertEquals(1, result.entries.size)
        val entry = result.entries.first()
        assertEquals(0, entry.distance, "Same-ring coupling should have distance 0 regardless of nesting depth")
        assertTrue(entry.verdict != BalanceVerdict.DANGER, "Same-ring coupling must not be DANGER")
    }

    @Test
    fun `composition root is never danger even with high separation and volatility`() {
        // Regression: the DI composition root wires everything together (high fan-out by
        // design) and nests deep. It must never be flagged as DANGER.
        val di = PackageName("com.example.di")
        val target = PackageName("com.example.infra.persistence")

        val strength = StrengthResult(
            entries = listOf(
                strengthEntry(di, target, IntegrationStrength.FUNCTIONAL, functional = 5),
            ),
        )
        val rings = mapOf(di to 4, target to 0)
        val volatility = PackageVolatilityResult(
            entries = listOf(
                volatility("com.example.di", revisions = 50),
                volatility("com.example.infra.persistence", revisions = 40),
            ),
        )

        val result = BalanceBuilder.build(strength, rings, setOf(di), volatility)

        assertEquals(1, result.entries.size)
        assertTrue(
            result.entries.first().verdict != BalanceVerdict.DANGER,
            "Composition root must never be DANGER",
        )
    }

    @Test
    fun `contract strength plus low separation plus high volatility is tolerable not over-engineered`() {
        val pkgA = PackageName("com.example.service")
        val pkgB = PackageName("com.example.model")

        val strength = StrengthResult(
            entries = listOf(
                strengthEntry(pkgA, pkgB, IntegrationStrength.CONTRACT, contract = 3),
            ),
        )
        val rings = mapOf(pkgA to 2, pkgB to 1)
        val volatility = PackageVolatilityResult(
            entries = listOf(
                volatility("com.example.service", revisions = 20),
                volatility("com.example.model", revisions = 15),
            ),
        )

        val result = BalanceBuilder.build(strength, rings, emptySet(), volatility)

        assertEquals(1, result.entries.size)
        assertEquals(BalanceVerdict.TOLERABLE, result.entries.first().verdict)
    }

    @Test
    fun `contract strength plus low separation plus low volatility is balanced when nearby`() {
        val pkgA = PackageName("com.example.domain.service")
        val pkgB = PackageName("com.example.domain.model")

        val strength = StrengthResult(
            entries = listOf(
                strengthEntry(pkgA, pkgB, IntegrationStrength.CONTRACT, contract = 3),
            ),
        )
        val rings = mapOf(pkgA to 1, pkgB to 0)
        val volatility = PackageVolatilityResult(
            entries = listOf(
                volatility("com.example.domain.service", revisions = 2),
                volatility("com.example.domain.model", revisions = 1),
                volatility("com.example.other.hot", revisions = 30),
                volatility("com.example.other.active", revisions = 25),
                volatility("com.example.other.busy", revisions = 20),
            ),
        )

        val result = BalanceBuilder.build(strength, rings, emptySet(), volatility)

        assertEquals(1, result.entries.size)
        assertEquals(BalanceVerdict.BALANCED, result.entries.first().verdict)
    }

    @Test
    fun `model strength plus low separation plus low volatility is balanced`() {
        val pkgA = PackageName("com.example.domain.service")
        val pkgB = PackageName("com.example.domain.repository")

        val strength = StrengthResult(
            entries = listOf(
                strengthEntry(pkgA, pkgB, IntegrationStrength.MODEL, model = 3),
            ),
        )
        val rings = mapOf(pkgA to 1, pkgB to 0)
        val volatility = PackageVolatilityResult(
            entries = listOf(
                volatility("com.example.domain.service", revisions = 2),
                volatility("com.example.domain.repository", revisions = 1),
                volatility("com.example.other.hot", revisions = 30),
                volatility("com.example.other.active", revisions = 25),
                volatility("com.example.other.busy", revisions = 20),
            ),
        )

        val result = BalanceBuilder.build(strength, rings, emptySet(), volatility)

        assertEquals(1, result.entries.size)
        assertEquals(BalanceVerdict.BALANCED, result.entries.first().verdict)
    }

    @Test
    fun `balanced — contract strength plus high separation`() {
        val pkgA = PackageName("com.example.web.api")
        val pkgB = PackageName("com.example.infra.db")

        val strength = StrengthResult(
            entries = listOf(
                strengthEntry(pkgA, pkgB, IntegrationStrength.CONTRACT, contract = 2),
            ),
        )
        val rings = mapOf(pkgA to 3, pkgB to 0)
        val volatility = PackageVolatilityResult(
            entries = listOf(
                volatility("com.example.web.api", revisions = 30),
                volatility("com.example.infra.db", revisions = 20),
            ),
        )

        val result = BalanceBuilder.build(strength, rings, emptySet(), volatility)

        assertEquals(1, result.entries.size)
        assertEquals(BalanceVerdict.BALANCED, result.entries.first().verdict)
    }

    @Test
    fun `balanced — functional strength plus low separation`() {
        val pkgA = PackageName("com.example.order")
        val pkgB = PackageName("com.example.order.model")

        val strength = StrengthResult(
            entries = listOf(
                strengthEntry(pkgA, pkgB, IntegrationStrength.FUNCTIONAL, functional = 4),
            ),
        )
        val rings = mapOf(pkgA to 1, pkgB to 0)
        val volatility = PackageVolatilityResult(
            entries = listOf(
                volatility("com.example.order", revisions = 20),
                volatility("com.example.order.model", revisions = 15),
            ),
        )

        val result = BalanceBuilder.build(strength, rings, emptySet(), volatility)

        assertEquals(1, result.entries.size)
        assertEquals(BalanceVerdict.BALANCED, result.entries.first().verdict)
    }

    @Test
    fun `tolerable — high strength high separation but low volatility`() {
        val pkgA = PackageName("com.example.web")
        val pkgB = PackageName("com.example.persistence")

        val strength = StrengthResult(
            entries = listOf(
                strengthEntry(pkgA, pkgB, IntegrationStrength.FUNCTIONAL, functional = 3),
            ),
        )
        val rings = mapOf(pkgA to 3, pkgB to 0)
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

        val result = BalanceBuilder.build(strength, rings, emptySet(), volatility)

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
        val rings = mapOf(pkgA to 3, pkgB to 0)
        val volatility = PackageVolatilityResult(entries = emptyList())

        val result = BalanceBuilder.build(strength, rings, emptySet(), volatility)

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
        val rings = mapOf(pkgA to 3, pkgB to 0, pkgC to 2, pkgD to 1)
        val volatility = PackageVolatilityResult(
            entries = listOf(
                volatility("com.example.web.controllers", revisions = 50),
                volatility("com.example.infra.persistence", revisions = 40),
                volatility("com.example.service", revisions = 3),
                volatility("com.example.model", revisions = 1),
            ),
        )

        val result = BalanceBuilder.build(strength, rings, emptySet(), volatility)

        assertEquals(2, result.entries.size)
        assertEquals(BalanceVerdict.DANGER, result.entries[0].verdict)
        assertEquals(BalanceVerdict.TOLERABLE, result.entries[1].verdict)
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
        val rings = mapOf(pkgA to 3, pkgB to 0, pkgC to 2, pkgD to 1)
        val volatility = PackageVolatilityResult(
            entries = listOf(
                volatility("com.example.web.controllers", revisions = 50),
                volatility("com.example.infra.persistence", revisions = 40),
                volatility("com.example.service", revisions = 3),
                volatility("com.example.model", revisions = 1),
            ),
        )

        val result = BalanceBuilder.build(strength, rings, emptySet(), volatility, top = 1)

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
