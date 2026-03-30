package no.f12.codenavigator.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CodeNavigatorExtensionTest {

    @Test
    fun `rootPackage defaults to empty string`() {
        val extension = CodeNavigatorExtension()

        assertEquals("", extension.rootPackage)
    }

    // === resolveProperties ===

    @Test
    fun `resolveProperties passes through CLI properties unchanged`() {
        val extension = CodeNavigatorExtension()
        val cli = mapOf("package-filter" to "com.cli", "include-external" to "true")

        val result = extension.resolveProperties(cli)

        assertEquals("com.cli", result["package-filter"])
        assertEquals("true", result["include-external"])
    }

    @Test
    fun `resolveProperties adds packageFilter from extension when CLI has none`() {
        val extension = CodeNavigatorExtension()
        extension.packageFilter = "com.ext"

        val result = extension.resolveProperties(emptyMap())

        assertEquals("com.ext", result["package-filter"])
    }

    @Test
    fun `resolveProperties CLI package-filter takes precedence over extension`() {
        val extension = CodeNavigatorExtension()
        extension.packageFilter = "com.ext"
        val cli = mapOf("package-filter" to "com.cli")

        val result = extension.resolveProperties(cli)

        assertEquals("com.cli", result["package-filter"])
    }

    @Test
    fun `resolveProperties aliases rootPackage to package-filter when packageFilter is empty`() {
        val extension = CodeNavigatorExtension()
        extension.rootPackage = "com.legacy"

        val result = extension.resolveProperties(emptyMap())

        assertEquals("com.legacy", result["package-filter"])
        assertEquals("com.legacy", result["root-package"])
    }

    @Test
    fun `resolveProperties packageFilter wins over rootPackage in extension`() {
        val extension = CodeNavigatorExtension()
        extension.rootPackage = "com.legacy"
        extension.packageFilter = "com.new"

        val result = extension.resolveProperties(emptyMap())

        assertEquals("com.new", result["package-filter"])
    }

    @Test
    fun `resolveProperties adds includeExternal from extension when CLI has none`() {
        val extension = CodeNavigatorExtension()
        extension.includeExternal = true

        val result = extension.resolveProperties(emptyMap())

        assertEquals("true", result["include-external"])
    }

    @Test
    fun `resolveProperties does not add includeExternal when false`() {
        val extension = CodeNavigatorExtension()

        val result = extension.resolveProperties(emptyMap())

        assertNull(result["include-external"])
    }

    @Test
    fun `resolveProperties does not override CLI includeExternal`() {
        val extension = CodeNavigatorExtension()
        extension.includeExternal = true
        val cli = mapOf("include-external" to "false")

        val result = extension.resolveProperties(cli)

        assertEquals("false", result["include-external"])
    }

    @Test
    fun `resolveProperties returns empty map when nothing is configured`() {
        val extension = CodeNavigatorExtension()

        val result = extension.resolveProperties(emptyMap())

        assertEquals(emptyMap(), result)
    }
}
