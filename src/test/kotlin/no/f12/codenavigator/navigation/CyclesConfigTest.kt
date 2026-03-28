package no.f12.codenavigator.navigation

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.dsm.CyclesConfig
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
