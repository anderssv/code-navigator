package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertTrue

class HintsConfigGeneratorTest {

    @Test
    fun `generates hints for infrastructure patterns in ring 0`() {
        val classRings = mapOf(
            ClassName("com.app.serial.FooSerializer") to 0,
            ClassName("com.app.gen.BarGenerator") to 0,
            ClassName("com.app.domain.Order") to 0,
            ClassName("com.app.service.OrderService") to 2,
        )

        val result = HintsConfigGenerator.generate(classRings)

        assertTrue(result.contains("*Serializer"))
        assertTrue(result.contains("*Generator"))
        assertTrue(result.contains("FooSerializer"))
        assertTrue(result.contains("BarGenerator"))
        assertTrue(result.contains("review and tweak"))
    }

    @Test
    fun `no hints when no infrastructure patterns in ring 0`() {
        val classRings = mapOf(
            ClassName("com.app.domain.Order") to 0,
            ClassName("com.app.domain.User") to 0,
        )

        val result = HintsConfigGenerator.generate(classRings)

        assertTrue(result.contains("\"hints\": {}"))
    }

    @Test
    fun `generated output is parseable as JSON after removing comments`() {
        val classRings = mapOf(
            ClassName("com.app.serial.FooSerializer") to 0,
        )

        val raw = HintsConfigGenerator.generate(classRings)
        val json = raw.lines()
            .filter { !it.trimStart().startsWith("//") }
            .joinToString("\n")
            .replace(Regex("""//.*"""), "")

        val config = RingsHintsConfig.fromJson(json)
        assertTrue(config.hints.containsKey("adapter"))
    }
}
