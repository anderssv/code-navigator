package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RingsHintsConfigTest {

    // [TEST] Empty JSON returns default config
    // [TEST] Parses hints with simple patterns
    // [TEST] Parses overrides with FQCN mappings
    // [TEST] Pattern ordering: first match wins
    // [TEST] Glob matching: exact, prefix*, *suffix, *middle*, *
    // [TEST] Override takes precedence over hint
    // [TEST] Hint sets minimum ring (promotes if raw < hint)
    // [TEST] Hint doesn't demote (raw >= hint stays)
    // [TEST] Class not matching any hint pattern keeps raw ring
    // [TEST] Malformed JSON throws

    @Test
    fun `empty JSON returns default config`() {
        val config = RingsHintsConfig.fromJson("{}")

        assertNull(config.ringNames)
        assertNotNull(config.hints)
        assertEquals(0, config.hints.size)
        assertNotNull(config.overrides)
        assertEquals(0, config.overrides.size)
    }

    @Test
    fun `parses hints with simple patterns`() {
        val json = """{"hints": {"infrastructure": ["*Serializer", "*Generator"], "port": ["*Repository"]}}"""
        val config = RingsHintsConfig.fromJson(json)

        assertEquals(2, config.hints.size)
        assertEquals(listOf("*Serializer", "*Generator"), config.hints["infrastructure"])
        assertEquals(listOf("*Repository"), config.hints["port"])
    }

    @Test
    fun `parses overrides with FQCN mappings`() {
        val json = """{"overrides": {"com.app.Foo": "infrastructure", "com.app.Bar": "domain"}}"""
        val config = RingsHintsConfig.fromJson(json)

        assertEquals(2, config.overrides.size)
        assertEquals("infrastructure", config.overrides["com.app.Foo"])
        assertEquals("domain", config.overrides["com.app.Bar"])
    }

    @Test
    fun `parses ringNames in order`() {
        val json = """{"ringNames": ["domain", "port", "application", "adapter"]}"""
        val config = RingsHintsConfig.fromJson(json)

        assertNotNull(config.ringNames)
        assertEquals(4, config.ringNames.size)
        assertEquals(listOf("domain", "port", "application", "adapter"), config.ringNames)
    }

    @Test
    fun `malformed JSON throws`() {
        assertFailsWith<IllegalArgumentException> {
            RingsHintsConfig.fromJson("{invalid")
        }
    }

    @Test
    fun `glob matches exact class name`() {
        val result = RingsHintsConfig.matchesGlob("FooSerializer", "FooSerializer")
        assertTrue(result)
    }

    @Test
    fun `glob matches prefix wildcard`() {
        val result = RingsHintsConfig.matchesGlob("FooSerializer", "*Serializer")
        assertTrue(result)
    }

    @Test
    fun `glob matches suffix wildcard`() {
        val result = RingsHintsConfig.matchesGlob("SerializerFoo", "Serializer*")
        assertTrue(result)
    }

    @Test
    fun `glob matches middle wildcard`() {
        val result = RingsHintsConfig.matchesGlob("FooSerializerBar", "*Serializer*")
        assertTrue(result)
    }

    @Test
    fun `glob matches catch-all`() {
        val result = RingsHintsConfig.matchesGlob("AnythingHere", "*")
        assertTrue(result)
    }

    @Test
    fun `glob does not match non-matching exact`() {
        assertFalse(RingsHintsConfig.matchesGlob("FooSerializer", "BarSerializer"))
    }

    @Test
    fun `glob does not match non-matching prefix`() {
        assertFalse(RingsHintsConfig.matchesGlob("FooSerializer", "*Bar"))
    }

    @Test
    fun `hint lookup returns first matching ring name`() {
        val config = RingsHintsConfig.fromJson("""{"hints": {"infrastructure": ["*Serializer", "*Generator"], "port": ["*Repository"]}}""")
        val hints = listOf(
            HintPattern("infrastructure", "*Serializer"),
            HintPattern("infrastructure", "*Generator"),
            HintPattern("port", "*Repository"),
        )

        assertEquals("infrastructure", config.findHint(hints, "FooSerializer"))
        assertEquals("port", config.findHint(hints, "MyRepository"))
        assertNull(config.findHint(hints, "Unknown"))
    }

    @Test
    fun `hint ordering respects first match`() {
        val config = RingsHintsConfig.fromJson("""{"hints": {"first": ["*Specific"], "catch-all": ["*"]}}""")
        val hints = listOf(
            HintPattern("first", "*Specific"),
            HintPattern("catch-all", "*"),
        )

        assertEquals("first", config.findHint(hints, "VerySpecific"))
        assertEquals("catch-all", config.findHint(hints, "SomethingElse"))
    }

    @Test
    fun `override takes precedence over hint`() {
        val json = """{"hints": {"infrastructure": ["*Serializer"]}, "overrides": {"com.app.MySerializer": "domain"}}"""
        val config = RingsHintsConfig.fromJson(json)
        val className = "com.app.MySerializer"
        val simpleName = "MySerializer"

        // Override matches by FQCN
        assertEquals("domain", config.overrides[className])
        // Hint would match by simple name
        assertEquals("infrastructure", config.findHint(config.toHintList(), simpleName))
    }

    @Test
    fun `hint sets minimum ring correctly`() {
        val config = RingsHintsConfig.fromJson("""{"ringNames": ["domain", "port", "application", "adapter"], "hints": {"adapter": ["*Serializer"]}}""")

        // Class at raw ring 0, hint says adapter (ring 3 based on order)
        val result = config.applyHint("FooSerializer", 0)
        assertEquals(3, result)
    }

    @Test
    fun `hint does not demote class`() {
        val config = RingsHintsConfig.fromJson("""{"ringNames": ["domain", "port", "application", "adapter"], "hints": {"port": ["*Service"]}}""")

        // Class at raw ring 3, hint says port (ring 1) — should stay at 3
        val result = config.applyHint("FooService", 3)
        assertEquals(3, result)
    }

    @Test
    fun `class not matching any hint keeps raw ring`() {
        val config = RingsHintsConfig.fromJson("""{"hints": {"infrastructure": ["*Serializer"]}}""")

        val result = config.applyHint("DomainEntity", 0)
        assertEquals(0, result)
    }

    @Test
    fun `applyHint with override uses override ring`() {
        val config = RingsHintsConfig.fromJson(
            """{"ringNames": ["domain", "port", "application", "adapter"], "overrides": {"com.app.MySerializer": "adapter"}}"""
        )

        val result = config.applyHint("MySerializer", 0, overrides = mapOf("com.app.MySerializer" to "adapter"), fqcn = "com.app.MySerializer")
        assertEquals(3, result)
    }

    @Test
    fun `applyHint override does not demote`() {
        val config = RingsHintsConfig.fromJson(
            """{"ringNames": ["domain", "port", "application", "adapter"], "overrides": {"com.app.MyService": "port"}}"""
        )

        val result = config.applyHint("MyService", 3, overrides = mapOf("com.app.MyService" to "port"), fqcn = "com.app.MyService")
        assertEquals(3, result)
    }

    @Test
    fun `adjustRings promotes matching classes`() {
        val config = RingsHintsConfig.fromJson(
            """{"ringNames": ["domain", "port", "application", "adapter"], "hints": {"adapter": ["*Serializer"]}}"""
        )
        val rawRings = mapOf(
            ClassName("com.app.domain.Order") to 0,
            ClassName("com.app.serial.FooSerializer") to 0,
            ClassName("com.app.service.OrderService") to 1,
        )

        val adjusted = config.adjustRings(rawRings, rawRings.keys)

        assertEquals(0, adjusted[ClassName("com.app.domain.Order")])
        assertEquals(3, adjusted[ClassName("com.app.serial.FooSerializer")])
        assertEquals(1, adjusted[ClassName("com.app.service.OrderService")])
    }

    @Test
    fun `adjustRings with no hints returns raw rings unchanged`() {
        val config = RingsHintsConfig.fromJson("{}")
        val rawRings = mapOf(
            ClassName("com.app.Foo") to 0,
            ClassName("com.app.Bar") to 2,
        )

        val adjusted = config.adjustRings(rawRings, rawRings.keys)

        assertEquals(rawRings, adjusted)
    }

    @Test
    fun `applyHint strips Kt suffix before matching`() {
        val config = RingsHintsConfig.fromJson(
            """{"ringNames": ["domain", "port", "application", "adapter"], "hints": {"adapter": ["*Config"]}}"""
        )

        val result = config.applyHint("ApplicationConfigKt", 0)
        assertEquals(3, result)
    }

    @Test
    fun `applyHint strips Test suffix before matching`() {
        val config = RingsHintsConfig.fromJson(
            """{"ringNames": ["domain", "port", "application", "adapter"], "hints": {"adapter": ["*Service*"]}}"""
        )

        val result = config.applyHint("UserServiceTest", 0)
        assertEquals(3, result)
    }
}
