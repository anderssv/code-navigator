package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.dsm.DsmConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DsmConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "root-package" to "com.example",
            "dsm-depth" to "4",
            "dsm-html" to "/tmp/dsm.html",
            "format" to "json",
            "llm" to "false",
        )

        val config = DsmConfig.parse(props)

        assertEquals(PackageName("com.example"), config.rootPackage)
        assertEquals(4, config.depth)
        assertEquals("/tmp/dsm.html", config.htmlPath)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `defaults rootPackage to empty string`() {
        val config = DsmConfig.parse(emptyMap())

        assertEquals(PackageName(""), config.rootPackage)
    }

    @Test
    fun `defaults depth to 2`() {
        val config = DsmConfig.parse(emptyMap())

        assertEquals(2, config.depth)
    }

    @Test
    fun `defaults htmlPath to null`() {
        val config = DsmConfig.parse(emptyMap())

        assertNull(config.htmlPath)
    }

    @Test
    fun `defaults to TEXT format`() {
        val config = DsmConfig.parse(emptyMap())

        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses LLM format`() {
        val config = DsmConfig.parse(mapOf("llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `parses cycles true as cyclesOnly true`() {
        val config = DsmConfig.parse(mapOf("cycles" to "true"))

        assertEquals(true, config.cyclesOnly)
    }

    @Test
    fun `defaults cyclesOnly to false`() {
        val config = DsmConfig.parse(emptyMap())

        assertEquals(false, config.cyclesOnly)
    }

    @Test
    fun `parses cycles false as cyclesOnly false`() {
        val config = DsmConfig.parse(mapOf("cycles" to "false"))

        assertEquals(false, config.cyclesOnly)
    }

    // === parseCycleFilter ===

    @Test
    fun `parseCycleFilter parses comma-separated packages`() {
        val result = DsmConfig.parseCycleFilter("api,service")

        assertEquals(PackageName("api") to PackageName("service"), result)
    }

    @Test
    fun `parseCycleFilter trims whitespace around package names`() {
        val result = DsmConfig.parseCycleFilter(" api , service ")

        assertEquals(PackageName("api") to PackageName("service"), result)
    }

    @Test
    fun `parseCycleFilter returns null for null input`() {
        val result = DsmConfig.parseCycleFilter(null)

        assertNull(result)
    }

    @Test
    fun `parseCycleFilter returns null for single package`() {
        val result = DsmConfig.parseCycleFilter("api")

        assertNull(result)
    }

    @Test
    fun `parseCycleFilter returns null for empty string`() {
        val result = DsmConfig.parseCycleFilter("")

        assertNull(result)
    }

    @Test
    fun `parse includes cycleFilter from cycle property`() {
        val config = DsmConfig.parse(mapOf("cycle" to "api,service"))

        assertEquals(PackageName("api") to PackageName("service"), config.cycleFilter)
    }

    @Test
    fun `parse defaults cycleFilter to null`() {
        val config = DsmConfig.parse(emptyMap())

        assertNull(config.cycleFilter)
    }

    // === deprecation ===

    @Test
    fun `deprecations returns warning when root-package is used without package-filter`() {
        val config = DsmConfig.parse(mapOf("root-package" to "com.example"))

        val warnings = config.deprecations()

        assertEquals(1, warnings.size)
        assertTrue(warnings[0].contains("root-package"))
        assertTrue(warnings[0].contains("package-filter"))
        assertTrue(warnings[0].contains("source sets"))
    }

    @Test
    fun `deprecations returns empty when package-filter is used`() {
        val config = DsmConfig.parse(mapOf("package-filter" to "com.example"))

        val warnings = config.deprecations()

        assertEquals(emptyList<String>(), warnings)
    }

    @Test
    fun `deprecations returns empty when neither root-package nor package-filter is used`() {
        val config = DsmConfig.parse(emptyMap())

        val warnings = config.deprecations()

        assertEquals(emptyList<String>(), warnings)
    }

    @Test
    fun `deprecations returns empty when both root-package and package-filter are used`() {
        val config = DsmConfig.parse(mapOf(
            "root-package" to "com.example",
            "package-filter" to "com.example.api",
        ))

        val warnings = config.deprecations()

        assertEquals(emptyList<String>(), warnings)
    }

    // === package-filter ===

    @Test
    fun `parses package-filter from properties`() {
        val config = DsmConfig.parse(mapOf("package-filter" to "com.example.api"))

        assertEquals(PackageName("com.example.api"), config.packageFilter)
    }

    @Test
    fun `defaults package-filter to empty`() {
        val config = DsmConfig.parse(emptyMap())

        assertEquals(PackageName(""), config.packageFilter)
    }

    @Test
    fun `root-package aliases to package-filter when package-filter is absent`() {
        val config = DsmConfig.parse(mapOf("root-package" to "com.example"))

        assertEquals(PackageName("com.example"), config.packageFilter)
    }

    @Test
    fun `package-filter takes precedence over root-package`() {
        val config = DsmConfig.parse(mapOf(
            "root-package" to "com.example",
            "package-filter" to "com.example.api",
        ))

        assertEquals(PackageName("com.example.api"), config.packageFilter)
    }

    // === include-external ===

    @Test
    fun `parses include-external true`() {
        val config = DsmConfig.parse(mapOf("include-external" to "true"))

        assertEquals(true, config.includeExternal)
    }

    @Test
    fun `defaults include-external to false`() {
        val config = DsmConfig.parse(emptyMap())

        assertEquals(false, config.includeExternal)
    }

    @Test
    fun `prodOnly defaults to false`() {
        val config = DsmConfig.parse(emptyMap())

        assertFalse(config.prodOnly)
    }

    @Test
    fun `testOnly defaults to false`() {
        val config = DsmConfig.parse(emptyMap())

        assertFalse(config.testOnly)
    }

    @Test
    fun `parses prodOnly=true`() {
        val config = DsmConfig.parse(mapOf("prod-only" to "true"))

        assertTrue(config.prodOnly)
    }

    @Test
    fun `parses testOnly=true`() {
        val config = DsmConfig.parse(mapOf("test-only" to "true"))

        assertTrue(config.testOnly)
    }
}
