package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.core.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayerInitGeneratorTest {

    @Test
    fun `generates config json with detected layers from class names`() {
        val classes = listOf(
            ClassName("com.example.api.OrderController"),
            ClassName("com.example.services.OrderService"),
            ClassName("com.example.infra.OrderRepository"),
            ClassName("com.example.domain.Order"),
        )

        val json = LayerInitGenerator.generateConfigJson(classes)

        val config = LayerConfig.parse(json)
        assertTrue(config.layers.any { it.name == "http" })
        assertTrue(config.layers.any { it.name == "service" })
        assertTrue(config.layers.any { it.name == "adapter" })
        assertTrue(config.layers.any { it.name == "domain" })
    }

    @Test
    fun `generated config has domain catch-all as last layer`() {
        val classes = listOf(
            ClassName("com.example.api.OrderController"),
            ClassName("com.example.domain.Order"),
        )

        val json = LayerInitGenerator.generateConfigJson(classes)

        val config = LayerConfig.parse(json)
        assertEquals("domain", config.layers.last().name)
        assertEquals(listOf("*"), config.layers.last().patterns)
    }

    @Test
    fun `detectCommonSuffixes finds http patterns from class names`() {
        val classes = listOf(
            ClassName("com.example.api.OrderController"),
            ClassName("com.example.api.UserRoutes"),
            ClassName("com.example.domain.Order"),
        )

        val suffixes = LayerInitGenerator.detectCommonSuffixes(classes)

        assertTrue(suffixes["http"]!!.contains("*Controller"))
        assertTrue(suffixes["http"]!!.contains("*Routes"))
    }

    @Test
    fun `detectCommonSuffixes omits layers with no matching classes`() {
        val classes = listOf(
            ClassName("com.example.domain.Order"),
            ClassName("com.example.domain.Customer"),
        )

        val suffixes = LayerInitGenerator.detectCommonSuffixes(classes)

        assertTrue(suffixes.containsKey("domain"))
        assertTrue(!suffixes.containsKey("http"))
        assertTrue(!suffixes.containsKey("service"))
        assertTrue(!suffixes.containsKey("adapter"))
    }

    @Test
    fun `empty classes produces config with only domain layer`() {
        val json = LayerInitGenerator.generateConfigJson(emptyList())

        val config = LayerConfig.parse(json)
        assertEquals(1, config.layers.size)
        assertEquals("domain", config.layers[0].name)
    }

    @Test
    fun `generated config is valid and parseable`() {
        val classes = listOf(
            ClassName("com.example.api.OrderController"),
            ClassName("com.example.services.OrderService"),
            ClassName("com.example.infra.OrderRepository"),
            ClassName("com.example.infra.PaymentClient"),
            ClassName("com.example.domain.Order"),
        )

        val json = LayerInitGenerator.generateConfigJson(classes)
        val config = LayerConfig.parse(json)

        assertEquals(0, config.layerIndexOf(ClassName("com.example.api.OrderController")))
        assertEquals("http", config.layerNameOf(ClassName("com.example.api.OrderController")))
    }
}
