package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.core.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LayerConfigTest {

    @Test
    fun `parse simple config with pattern layers`() {
        val json = """
        {
          "layers": [
            { "name": "http", "patterns": ["*Controller", "*Routes"] },
            { "name": "service", "patterns": ["*Service"] },
            { "name": "domain", "patterns": ["*"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(3, config.layers.size)
        assertEquals("http", config.layers[0].name)
        assertEquals(listOf("*Controller", "*Routes"), config.layers[0].patterns)
        assertEquals("service", config.layers[1].name)
        assertEquals("domain", config.layers[2].name)
    }

    @Test
    fun `empty layers array returns empty config`() {
        val json = """{ "layers": [] }"""

        val config = LayerConfig.parse(json)

        assertEquals(0, config.layers.size)
    }

    @Test
    fun `layer without name throws validation error`() {
        val json = """
        {
          "layers": [
            { "patterns": ["*Controller"] }
          ]
        }
        """.trimIndent()

        val exception = assertFailsWith<IllegalArgumentException> {
            LayerConfig.parse(json)
        }

        assertEquals("Layer must have a 'name' field", exception.message)
    }

    @Test
    fun `layer without patterns throws validation error`() {
        val json = """
        {
          "layers": [
            { "name": "http" }
          ]
        }
        """.trimIndent()

        val exception = assertFailsWith<IllegalArgumentException> {
            LayerConfig.parse(json)
        }

        assertEquals("Layer 'http' must have a 'patterns' array", exception.message)
    }

    @Test
    fun `layerIndexOf matches class by suffix pattern`() {
        val json = """
        {
          "layers": [
            { "name": "http", "patterns": ["*Controller"] },
            { "name": "service", "patterns": ["*Service"] },
            { "name": "domain", "patterns": ["*"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(0, config.layerIndexOf(ClassName("com.example.api.OrderController")))
        assertEquals(1, config.layerIndexOf(ClassName("com.example.services.OrderService")))
        assertEquals(2, config.layerIndexOf(ClassName("com.example.domain.Order")))
    }

    @Test
    fun `layerIndexOf returns null when no layers defined`() {
        val config = LayerConfig.parse("""{ "layers": [] }""")

        assertNull(config.layerIndexOf(ClassName("com.example.Foo")))
    }

    @Test
    fun `first matching layer wins`() {
        val json = """
        {
          "layers": [
            { "name": "http", "patterns": ["*Controller"] },
            { "name": "catch-all", "patterns": ["*"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(0, config.layerIndexOf(ClassName("com.example.api.OrderController")))
        assertEquals(1, config.layerIndexOf(ClassName("com.example.domain.Order")))
    }

    @Test
    fun `layerNameOf returns layer name for matching class`() {
        val json = """
        {
          "layers": [
            { "name": "http", "patterns": ["*Controller"] },
            { "name": "service", "patterns": ["*Service"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals("http", config.layerNameOf(ClassName("com.example.OrderController")))
        assertEquals("service", config.layerNameOf(ClassName("com.example.OrderService")))
        assertNull(config.layerNameOf(ClassName("com.example.Order")))
    }

    @Test
    fun `pattern matches simple class name not fully qualified`() {
        val json = """
        {
          "layers": [
            { "name": "http", "patterns": ["*Controller"] },
            { "name": "domain", "patterns": ["*"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(0, config.layerIndexOf(ClassName("com.example.deep.nested.MyController")))
        assertEquals(1, config.layerIndexOf(ClassName("com.example.deep.nested.MyEntity")))
    }

    @Test
    fun `arePeers returns true for two classes in same layer`() {
        val json = """
        {
          "layers": [
            { "name": "service", "patterns": ["*Service"] },
            { "name": "domain", "patterns": ["*"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(true, config.arePeers(
            ClassName("com.example.OrderService"),
            ClassName("com.example.CustomerService"),
        ))
    }

    @Test
    fun `arePeers returns false for classes in different layers`() {
        val json = """
        {
          "layers": [
            { "name": "service", "patterns": ["*Service"] },
            { "name": "domain", "patterns": ["*"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(false, config.arePeers(
            ClassName("com.example.OrderService"),
            ClassName("com.example.Order"),
        ))
    }

    @Test
    fun `arePeers returns false when either class is unmatched`() {
        val json = """
        {
          "layers": [
            { "name": "service", "patterns": ["*Service"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(false, config.arePeers(
            ClassName("com.example.OrderService"),
            ClassName("com.example.Order"),
        ))
    }

    @Test
    fun `prefix pattern matches class name starting with prefix`() {
        val json = """
        {
          "layers": [
            { "name": "test", "patterns": ["Test*"] },
            { "name": "domain", "patterns": ["*"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(0, config.layerIndexOf(ClassName("com.example.TestOrderService")))
        assertEquals(1, config.layerIndexOf(ClassName("com.example.OrderService")))
    }

    @Test
    fun `exact pattern matches exact simple class name`() {
        val json = """
        {
          "layers": [
            { "name": "app", "patterns": ["Application"] },
            { "name": "domain", "patterns": ["*"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(0, config.layerIndexOf(ClassName("com.example.Application")))
        assertEquals(1, config.layerIndexOf(ClassName("com.example.ApplicationService")))
    }

    @Test
    fun `peerLimit defaults to zero when not specified`() {
        val json = """
        {
          "layers": [
            { "name": "service", "patterns": ["*Service"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(0, config.layers[0].peerLimit)
    }

    @Test
    fun `peerLimit is parsed from JSON`() {
        val json = """
        {
          "layers": [
            { "name": "service", "patterns": ["*Service"], "peerLimit": 3 }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(3, config.layers[0].peerLimit)
    }

    @Test
    fun `peerLimitOf returns limit for matching class`() {
        val json = """
        {
          "layers": [
            { "name": "service", "patterns": ["*Service"], "peerLimit": 5 },
            { "name": "domain", "patterns": ["*"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(5, config.peerLimitOf(ClassName("com.example.OrderService")))
        assertEquals(0, config.peerLimitOf(ClassName("com.example.Order")))
    }

    // --- Auto-Test suffix matching ---

    @Test
    fun `class ending in Test matches the layer of its base pattern`() {
        val json = """
        {
          "layers": [
            { "name": "http", "patterns": ["*Controller"] },
            { "name": "service", "patterns": ["*Service"] },
            { "name": "domain", "patterns": ["*"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(1, config.layerIndexOf(ClassName("com.example.OrderServiceTest")))
        assertEquals("service", config.layerNameOf(ClassName("com.example.OrderServiceTest")))
    }

    @Test
    fun `test class with no matching base pattern falls to catch-all`() {
        val json = """
        {
          "layers": [
            { "name": "http", "patterns": ["*Controller"] },
            { "name": "service", "patterns": ["*Service"] },
            { "name": "domain", "patterns": ["*"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(2, config.layerIndexOf(ClassName("com.example.MetricsTest")))
        assertEquals("domain", config.layerNameOf(ClassName("com.example.MetricsTest")))
    }

    @Test
    fun `test class is peer with its production counterpart`() {
        val json = """
        {
          "layers": [
            { "name": "service", "patterns": ["*Service"], "peerLimit": 5 },
            { "name": "domain", "patterns": ["*"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(true, config.arePeers(
            ClassName("com.example.OrderServiceTest"),
            ClassName("com.example.OrderService"),
        ))
        assertEquals(5, config.peerLimitOf(ClassName("com.example.OrderServiceTest")))
    }

    @Test
    fun `test suffix stripping prefers specific match over catch-all`() {
        val json = """
        {
          "layers": [
            { "name": "http", "patterns": ["*Controller"] },
            { "name": "domain", "patterns": ["*"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(0, config.layerIndexOf(ClassName("com.example.OrderControllerTest")))
        assertEquals("http", config.layerNameOf(ClassName("com.example.OrderControllerTest")))
    }

    // --- Auto-Kt suffix stripping ---

    @Test
    fun `Kt suffix class matches pattern without Kt`() {
        val json = """
        {
          "layers": [
            { "name": "http", "patterns": ["*Route", "*Setup"] },
            { "name": "domain", "patterns": ["*"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(0, config.layerIndexOf(ClassName("com.example.ktor.OrderRouteKt")))
        assertEquals(0, config.layerIndexOf(ClassName("com.example.ktor.SetupKt")))
        assertEquals("http", config.layerNameOf(ClassName("com.example.ktor.OrderRouteKt")))
    }

    @Test
    fun `Kt suffix class with no matching base pattern falls to catch-all`() {
        val json = """
        {
          "layers": [
            { "name": "http", "patterns": ["*Route"] },
            { "name": "domain", "patterns": ["*"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(1, config.layerIndexOf(ClassName("com.example.UtilsKt")))
        assertEquals("domain", config.layerNameOf(ClassName("com.example.UtilsKt")))
    }

    @Test
    fun `Kt and Test suffixes combine correctly`() {
        val json = """
        {
          "layers": [
            { "name": "http", "patterns": ["*Route"] },
            { "name": "domain", "patterns": ["*"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(0, config.layerIndexOf(ClassName("com.example.OrderRouteKt")))
        assertEquals(0, config.layerIndexOf(ClassName("com.example.OrderRouteTest")))
        assertEquals("http", config.layerNameOf(ClassName("com.example.OrderRouteKt")))
        assertEquals("http", config.layerNameOf(ClassName("com.example.OrderRouteTest")))
    }

    @Test
    fun `class named Test alone falls to catch-all`() {
        val json = """
        {
          "layers": [
            { "name": "service", "patterns": ["*Service"] },
            { "name": "domain", "patterns": ["*"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(1, config.layerIndexOf(ClassName("com.example.Test")))
        assertEquals("domain", config.layerNameOf(ClassName("com.example.Test")))
    }

    // --- peerLimit ---

    @Test
    fun `testInfrastructure defaults to false when not specified`() {
        val json = """
        {
          "layers": [
            { "name": "wiring", "patterns": ["*Dependencies"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(false, config.layers[0].testInfrastructure)
    }

    @Test
    fun `testInfrastructure is parsed from JSON`() {
        val json = """
        {
          "layers": [
            { "name": "wiring", "patterns": ["*Dependencies"], "testInfrastructure": true }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(true, config.layers[0].testInfrastructure)
    }

    @Test
    fun `peerLimitOf returns zero for unmatched class`() {
        val json = """
        {
          "layers": [
            { "name": "service", "patterns": ["*Service"] }
          ]
        }
        """.trimIndent()

        val config = LayerConfig.parse(json)

        assertEquals(0, config.peerLimitOf(ClassName("com.example.Order")))
    }
}
