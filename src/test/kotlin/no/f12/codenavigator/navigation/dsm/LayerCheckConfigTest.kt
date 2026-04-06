package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.config.OutputFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class LayerCheckConfigTest {

    @Test
    fun `parses all properties from map`() {
        val props = mapOf(
            "config" to "/custom/path.json",
            "init" to "true",
            "format" to "json",
        )

        val config = LayerCheckConfig.parse(props)

        assertEquals("/custom/path.json", config.configPath)
        assertEquals(true, config.init)
        assertEquals(OutputFormat.JSON, config.format)
    }

    @Test
    fun `uses defaults when properties are missing`() {
        val config = LayerCheckConfig.parse(emptyMap())

        assertEquals(".cnav-layers.json", config.configPath)
        assertEquals(false, config.init)
        assertEquals(OutputFormat.TEXT, config.format)
    }
}
