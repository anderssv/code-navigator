package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.dsm.CyclesConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CyclesConfigTest {

    @Test
    fun `defaults — root-package empty, depth 2, format TEXT`() {
        val config = CyclesConfig.parse(emptyMap())

        assertEquals(PackageName(""), config.rootPackage)
        assertEquals(2, config.depth)
        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses root-package and dsm-depth`() {
        val config = CyclesConfig.parse(
            mapOf("root-package" to "com.example", "dsm-depth" to "3"),
        )

        assertEquals(PackageName("com.example"), config.rootPackage)
        assertEquals(3, config.depth)
    }

    @Test
    fun `parses format and llm`() {
        val config = CyclesConfig.parse(mapOf("llm" to "true"))

        assertEquals(OutputFormat.LLM, config.format)
    }

    // === package-filter ===

    @Test
    fun `parses package-filter from properties`() {
        val config = CyclesConfig.parse(mapOf("package-filter" to "com.example.api"))

        assertEquals(PackageName("com.example.api"), config.packageFilter)
    }

    @Test
    fun `defaults package-filter to empty`() {
        val config = CyclesConfig.parse(emptyMap())

        assertEquals(PackageName(""), config.packageFilter)
    }

    @Test
    fun `root-package aliases to package-filter when package-filter is absent`() {
        val config = CyclesConfig.parse(mapOf("root-package" to "com.example"))

        assertEquals(PackageName("com.example"), config.packageFilter)
    }

    @Test
    fun `package-filter takes precedence over root-package`() {
        val config = CyclesConfig.parse(mapOf(
            "root-package" to "com.example",
            "package-filter" to "com.example.api",
        ))

        assertEquals(PackageName("com.example.api"), config.packageFilter)
    }

    // === include-external ===

    @Test
    fun `parses include-external true`() {
        val config = CyclesConfig.parse(mapOf("include-external" to "true"))

        assertEquals(true, config.includeExternal)
    }

    @Test
    fun `defaults include-external to false`() {
        val config = CyclesConfig.parse(emptyMap())

        assertEquals(false, config.includeExternal)
    }

    // === deprecation ===

    @Test
    fun `deprecations returns warning when root-package is used without package-filter`() {
        val config = CyclesConfig.parse(mapOf("root-package" to "com.example"))

        val warnings = config.deprecations()

        assertEquals(1, warnings.size)
        assertTrue(warnings[0].contains("root-package"))
        assertTrue(warnings[0].contains("package-filter"))
        assertTrue(warnings[0].contains("source sets"))
    }

    @Test
    fun `deprecations returns empty when package-filter is used`() {
        val config = CyclesConfig.parse(mapOf("package-filter" to "com.example"))

        assertEquals(emptyList<String>(), config.deprecations())
    }

    @Test
    fun `prodOnly defaults to false`() {
        val config = CyclesConfig.parse(emptyMap())

        assertFalse(config.prodOnly)
    }

    @Test
    fun `testOnly defaults to false`() {
        val config = CyclesConfig.parse(emptyMap())

        assertFalse(config.testOnly)
    }

    @Test
    fun `parses prodOnly=true`() {
        val config = CyclesConfig.parse(mapOf("prod-only" to "true"))

        assertTrue(config.prodOnly)
    }

    @Test
    fun `parses testOnly=true`() {
        val config = CyclesConfig.parse(mapOf("test-only" to "true"))

        assertTrue(config.testOnly)
    }
}
