package no.f12.codenavigator.navigation.dsm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RingsConfigTest {

    @Test
    fun `an absent rings section yields an empty config`() {
        val config = RingsConfig.fromJson("{}")

        assertEquals(RingsConfig(), config)
    }

    @Test
    fun `reads the ring section`() {
        val json = """
            {
              "rings": {
                "expected": 3,
                "compositionRoots": ["com.app.ApplicationKt"],
                "adapters": ["com.app.web.*"],
                "notAdapters": ["com.app.domain.*"]
              }
            }
        """.trimIndent()

        val config = RingsConfig.fromJson(json)

        assertEquals(3, config.expectedRingCount)
        assertEquals(listOf("com.app.ApplicationKt"), config.compositionRoots)
        assertEquals(listOf("com.app.web.*"), config.adapters)
        assertEquals(listOf("com.app.domain.*"), config.notAdapters)
    }

    @Test
    fun `an old style ringNames ladder is rejected with an explanation`() {
        val json = """{"ringNames": ["domain", "port", "application", "infrastructure"]}"""

        val error = assertFailsWith<IllegalArgumentException> { RingsConfig.fromJson(json) }

        assertTrue(error.message!!.contains("ringNames"))
        assertTrue(error.message!!.contains("no longer"))
    }
}
