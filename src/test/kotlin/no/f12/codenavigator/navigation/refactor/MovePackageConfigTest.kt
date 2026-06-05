package no.f12.codenavigator.navigation.refactor

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MovePackageConfigTest {

    @Test
    fun `parses from-package and to-package properties`() {
        val config = MovePackageConfig.parse(mapOf(
            "from-package" to "com.example.oldpkg",
            "to-package" to "com.example.newpkg",
        ))

        assertEquals("com.example.oldpkg", config.fromPackage)
        assertEquals("com.example.newpkg", config.toPackage)
        assertEquals(false, config.preview)
        assertEquals(OutputFormat.TEXT, config.format)
    }

    @Test
    fun `parses preview flag`() {
        val config = MovePackageConfig.parse(mapOf(
            "from-package" to "com.example.oldpkg",
            "to-package" to "com.example.newpkg",
            "preview" to "true",
        ))

        assertTrue(config.preview)
    }

    @Test
    fun `parses LLM format`() {
        val config = MovePackageConfig.parse(mapOf(
            "from-package" to "com.example.oldpkg",
            "to-package" to "com.example.newpkg",
            "llm" to "true",
        ))

        assertEquals(OutputFormat.LLM, config.format)
    }

    @Test
    fun `rejects missing from-package`() {
        assertFailsWith<IllegalArgumentException> {
            MovePackageConfig.parse(mapOf(
                "to-package" to "com.example.newpkg",
            ))
        }
    }

    @Test
    fun `rejects missing to-package`() {
        assertFailsWith<IllegalArgumentException> {
            MovePackageConfig.parse(mapOf(
                "from-package" to "com.example.oldpkg",
            ))
        }
    }
}
