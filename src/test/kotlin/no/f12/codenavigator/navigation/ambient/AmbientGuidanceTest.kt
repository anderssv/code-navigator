package no.f12.codenavigator.navigation.ambient

import kotlin.test.Test
import kotlin.test.assertContains

class AmbientGuidanceTest {

    @Test
    fun `purpose explains why direction analysis cannot see this`() {
        val guidance = AmbientGuidance.GUIDANCE

        assertContains(guidance.purpose, "cnavRings")
        assertContains(guidance.purpose, "static call")
    }

    @Test
    fun `parameter guidance explains the domain fallback`() {
        assertContains(AmbientGuidance.GUIDANCE.parameterGuidance, "--domain")
        assertContains(AmbientGuidance.GUIDANCE.parameterGuidance, "--categories")
    }

    @Test
    fun `interpretation explains the zone overload and the injected clock`() {
        val interpretation = AmbientGuidance.GUIDANCE.interpretation

        assertContains(interpretation, "zone")
        assertContains(interpretation, "Clock")
    }

    @Test
    fun `interpretation explains when clock findings are advisory`() {
        assertContains(AmbientGuidance.GUIDANCE.render(), "advisory")
    }
}
