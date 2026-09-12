package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals

class RingsConfigGeneratorTest {

    @Test
    fun `generates a rings section pinning the detected count and roots`() {
        val root = ClassName("com.app.ApplicationKt")
        val impl = ClassName("com.app.infra.PollsRepositoryImpl")

        val graph = RingGraph(
            classes = setOf(root, impl),
            compositionRoots = setOf(root),
        )
        val layering = RingLayering(ringCount = 3, diagnosis = RingDiagnosis.LAYERED)

        val json = RingsConfigGenerator.generate(graph, layering)

        assertEquals(
            """
            {
              "rings": {
                "expected": 3,
                "compositionRoots": ["com.app.ApplicationKt"],
                "adapters": [],
                "notAdapters": []
              }
            }
            """.trimIndent(),
            json,
        )
    }

    @Test
    fun `the generated config round trips back through the parser`() {
        val graph = RingGraph(
            classes = setOf(ClassName("com.app.ApplicationKt")),
            compositionRoots = setOf(ClassName("com.app.ApplicationKt")),
        )
        val layering = RingLayering(ringCount = 2, diagnosis = RingDiagnosis.LAYERED)

        val config = RingsConfig.fromJson(RingsConfigGenerator.generate(graph, layering))

        assertEquals(2, config.expectedRingCount)
        assertEquals(listOf("com.app.ApplicationKt"), config.compositionRoots)
    }
}
