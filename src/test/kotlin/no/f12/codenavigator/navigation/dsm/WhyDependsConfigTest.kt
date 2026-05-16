package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.*

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WhyDependsConfigTest {

    // [TEST] Parses from and to from properties
    // [TEST] Missing from throws
    // [TEST] Missing to throws

    @Test
    fun `parses from-package and to-package from properties`() {
        val config = WhyDependsConfig.parse(mapOf(
            "from-package" to "com.example.api",
            "to-package" to "com.example.db",
        ))

        assertEquals("com.example.api", config.fromPackage)
        assertEquals("com.example.db", config.toPackage)
    @Test
    fun `missing from-package throws`() {
        assertFailsWith<IllegalStateException> {
            WhyDependsConfig.parse(mapOf("to-package" to "com.example.db"))
        }
    }

    @Test
    fun `missing to-package throws`() {
        assertFailsWith<IllegalStateException> {
            WhyDependsConfig.parse(mapOf("from-package" to "com.example.api"))
        }
    }
}
}
