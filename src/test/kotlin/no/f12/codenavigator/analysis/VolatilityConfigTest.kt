package no.f12.codenavigator.analysis

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.LocalDate

class VolatilityConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "after" to "2024-06-01",
            "top" to "10",
            "no-follow" to "",
            "format" to "json",
            "min-revs" to "3",
        )

        val config = VolatilityConfig.parse(props)

        assertEquals(LocalDate.of(2024, 6, 1), config.after)
        assertEquals(10, config.top)
        assertEquals(false, config.followRenames)
        assertEquals(OutputFormat.JSON, config.format)
        assertEquals(3, config.minRevs)
    }

    @Test
    fun `defaults after to approximately one year ago`() {
        val config = VolatilityConfig.parse(emptyMap())

        val expectedApprox = LocalDate.now().minusYears(1)
        assert(config.after.isEqual(expectedApprox) || config.after.isAfter(expectedApprox.minusDays(1)))
    }

    @Test
    fun `defaults top to 50`() {
        val config = VolatilityConfig.parse(emptyMap())

        assertEquals(50, config.top)
    }

    @Test
    fun `defaults followRenames to true`() {
        val config = VolatilityConfig.parse(emptyMap())

        assertEquals(true, config.followRenames)
    }

    @Test
    fun `defaults minRevs to 1`() {
        val config = VolatilityConfig.parse(emptyMap())

        assertEquals(1, config.minRevs)
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = VolatilityConfig.parse(emptyMap())

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = VolatilityConfig.parse(mapOf("llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }
}
