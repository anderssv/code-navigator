package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.metrics.MetricsConfig
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetricsConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "after" to "2024-06-01",
            "top" to "10",
            "no-follow" to "",
            "root-package" to "com.example",
            "format" to "json",
        )

        val config = MetricsConfig.parse(props)

        assertEquals(LocalDate.of(2024, 6, 1), config.after)
        assertEquals(10, config.top)
        assertEquals(false, config.followRenames)
        assertEquals(PackageName("com.example"), config.rootPackage)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = MetricsConfig.parse(emptyMap())

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = MetricsConfig.parse(mapOf("llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `defaults after to approximately one year ago`() {
        val config = MetricsConfig.parse(emptyMap())

        val expectedApprox = LocalDate.now().minusYears(1)
        assertTrue(config.after.isEqual(expectedApprox) || config.after.isAfter(expectedApprox.minusDays(1)))
    }

    @Test
    fun `defaults followRenames to true when no-follow absent`() {
        val config = MetricsConfig.parse(emptyMap())

        assertEquals(true, config.followRenames)
    }

    @Test
    fun `sets followRenames to false when no-follow present`() {
        val config = MetricsConfig.parse(mapOf("no-follow" to null))

        assertEquals(false, config.followRenames)
    }

    @Test
    fun `defaults top to 5`() {
        val config = MetricsConfig.parse(emptyMap())

        assertEquals(5, config.top)
    }

    @Test
    fun `defaults rootPackage to empty string`() {
        val config = MetricsConfig.parse(emptyMap())

        assertEquals(PackageName(""), config.rootPackage)
    }

    @Test
    fun `parses exclude-annotated from property map`() {
        val config = MetricsConfig.parse(mapOf(
            "exclude-annotated" to "Scheduled,EventListener",
            "exclude-framework" to "ALL",
        ))

        assertEquals(listOf("Scheduled", "EventListener"), config.excludeAnnotated)
    }

    @Test
    fun `all frameworks active by default includes annotations in excludeAnnotated`() {
        val config = MetricsConfig.parse(emptyMap())

        assertTrue(config.excludeAnnotated.contains("com.fasterxml.jackson.annotation.JsonCreator"))
        assertTrue(config.excludeAnnotated.contains("org.springframework.stereotype.Controller"))
    }

    @Test
    fun `merges explicit annotations with default framework presets`() {
        val config = MetricsConfig.parse(mapOf(
            "exclude-annotated" to "Custom",
        ))

        assertTrue(config.excludeAnnotated.contains("Custom"))
        assertTrue(config.excludeAnnotated.contains("com.fasterxml.jackson.annotation.JsonCreator"))
    }

    @Test
    fun `exclude-framework=ALL results in empty excludeAnnotated`() {
        val config = MetricsConfig.parse(mapOf("exclude-framework" to "ALL"))

        assertEquals(emptyList<String>(), config.excludeAnnotated)
    }

    // === package-filter ===

    @Test
    fun `parses package-filter from properties`() {
        val config = MetricsConfig.parse(mapOf("package-filter" to "com.example.api"))

        assertEquals(PackageName("com.example.api"), config.packageFilter)
    }

    @Test
    fun `defaults package-filter to empty`() {
        val config = MetricsConfig.parse(emptyMap())

        assertEquals(PackageName(""), config.packageFilter)
    }

    @Test
    fun `root-package aliases to package-filter when package-filter is absent`() {
        val config = MetricsConfig.parse(mapOf("root-package" to "com.example"))

        assertEquals(PackageName("com.example"), config.packageFilter)
    }

    @Test
    fun `package-filter takes precedence over root-package`() {
        val config = MetricsConfig.parse(mapOf(
            "root-package" to "com.example",
            "package-filter" to "com.example.api",
        ))

        assertEquals(PackageName("com.example.api"), config.packageFilter)
    }

    // === include-external ===

    @Test
    fun `parses include-external true`() {
        val config = MetricsConfig.parse(mapOf("include-external" to "true"))

        assertEquals(true, config.includeExternal)
    }

    @Test
    fun `defaults include-external to false`() {
        val config = MetricsConfig.parse(emptyMap())

        assertEquals(false, config.includeExternal)
    }

    // === deprecation ===

    @Test
    fun `deprecations returns warning when root-package is used without package-filter`() {
        val config = MetricsConfig.parse(mapOf("root-package" to "com.example"))

        val warnings = config.deprecations()

        assertEquals(1, warnings.size)
        assertTrue(warnings[0].contains("root-package"))
        assertTrue(warnings[0].contains("package-filter"))
        assertTrue(warnings[0].contains("source sets"))
    }

    @Test
    fun `deprecations returns empty when package-filter is used`() {
        val config = MetricsConfig.parse(mapOf("package-filter" to "com.example"))

        assertEquals(emptyList<String>(), config.deprecations())
    }

    // === prodOnly / testOnly ===

    @Test
    fun `prodOnly defaults to false`() {
        val config = MetricsConfig.parse(emptyMap())

        assertFalse(config.prodOnly)
    }

    @Test
    fun `testOnly defaults to false`() {
        val config = MetricsConfig.parse(emptyMap())

        assertFalse(config.testOnly)
    }

    @Test
    fun `parses prodOnly=true`() {
        val config = MetricsConfig.parse(mapOf("prod-only" to "true"))

        assertTrue(config.prodOnly)
    }

    @Test
    fun `parses testOnly=true`() {
        val config = MetricsConfig.parse(mapOf("test-only" to "true"))

        assertTrue(config.testOnly)
    }
}
