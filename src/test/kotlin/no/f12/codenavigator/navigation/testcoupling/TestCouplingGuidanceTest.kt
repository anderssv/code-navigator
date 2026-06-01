package no.f12.codenavigator.navigation.testcoupling

import kotlin.test.Test
import kotlin.test.assertContains

class TestCouplingGuidanceTest {

    // [TEST] Purpose explains what TTTD violations are
    @Test
    fun purposeExplainsTestThroughTheDomain() {
        val guidance = TestCouplingGuidance.GUIDANCE

        assertContains(guidance.purpose, "Testing Through the Domain")
        assertContains(guidance.purpose, "port")
    }

    // [TEST] Parameter guidance explains how to identify ports in a project
    @Test
    fun parameterGuidanceExplainsPortIdentification() {
        val guidance = TestCouplingGuidance.GUIDANCE

        assertContains(guidance.parameterGuidance, "interface")
        assertContains(guidance.parameterGuidance, "Repository")
        assertContains(guidance.parameterGuidance, "Client")
        assertContains(guidance.parameterGuidance, "--ports")
    }

    // [TEST] Interpretation explains what flagged calls mean and how to fix them
    @Test
    fun interpretationExplainsFixing() {
        val guidance = TestCouplingGuidance.GUIDANCE

        assertContains(guidance.interpretation, "service")
        assertContains(guidance.interpretation, "domain")
    }

    // [TEST] Guidance mentions hexagonal architecture concepts
    @Test
    fun mentionsHexagonalConcepts() {
        val rendered = TestCouplingGuidance.GUIDANCE.render()

        assertContains(rendered, "adapter")
    }

    // [TEST] Guidance mentions fakes as the test double mechanism
    @Test
    fun mentionsFakes() {
        val rendered = TestCouplingGuidance.GUIDANCE.render()

        assertContains(rendered, "fake", ignoreCase = true)
    }
}
