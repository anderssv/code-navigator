package no.f12.codenavigator.navigation.converge

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.types.Scope
import kotlin.test.Test
import kotlin.test.assertEquals

class ConvergeConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "mode" to "risk",
            "package-filter" to "com.example",
            "exclude-packages" to "\\.di\\.",
            "after" to "2024-06-01",
            "min-shared-revs" to "10",
            "min-coupling" to "50",
            "max-changeset-size" to "20",
            "no-follow" to "",
            "top" to "25",
            "scope" to "prod",
            "format" to "json",
        )

        val config = ConvergeConfig.parse(props)

        assertEquals(ConvergeMode.RISK, config.mode)
        assertEquals(PackageName("com.example"), config.packageFilter)
        assertEquals("\\.di\\.", config.exclude?.pattern)
        assertEquals(10, config.minSharedRevs)
        assertEquals(50, config.minCoupling)
        assertEquals(20, config.maxChangesetSize)
        assertEquals(false, config.followRenames)
        assertEquals(25, config.top)
        assertEquals(Scope.PROD, config.scope)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `defaults mode to intersect when absent`() {
        val config = ConvergeConfig.parse(emptyMap())

        assertEquals(ConvergeMode.INTERSECT, config.mode)
    }

    @Test
    fun `defaults mode to intersect for unrecognized value`() {
        val config = ConvergeConfig.parse(mapOf("mode" to "bogus"))

        assertEquals(ConvergeMode.INTERSECT, config.mode)
    }

    @Test
    fun `defaults packageFilter to null when absent`() {
        val config = ConvergeConfig.parse(emptyMap())

        assertEquals(null, config.packageFilter)
    }

    @Test
    fun `defaults exclude to null when absent`() {
        val config = ConvergeConfig.parse(emptyMap())

        assertEquals(null, config.exclude)
    }

    @Test
    fun `defaults scope to prod when absent`() {
        val config = ConvergeConfig.parse(emptyMap())

        assertEquals(Scope.PROD, config.scope)
    }

    @Test
    fun `respects explicit scope=all override`() {
        val config = ConvergeConfig.parse(mapOf("scope" to "all"))

        assertEquals(Scope.ALL, config.scope)
    }

    @Test
    fun `defaults top to 50 when absent`() {
        val config = ConvergeConfig.parse(emptyMap())

        assertEquals(50, config.top)
    }

    @Test
    fun `defaults followRenames to true when no-follow absent`() {
        val config = ConvergeConfig.parse(emptyMap())

        assertEquals(true, config.followRenames)
    }
}
