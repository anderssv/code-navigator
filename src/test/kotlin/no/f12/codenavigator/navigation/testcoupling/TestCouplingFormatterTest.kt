package no.f12.codenavigator.navigation.testcoupling

import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class TestCouplingFormatterTest {

    // [TEST] TEXT format shows per-class verdict summary
    @Test
    fun textFormatShowsVerdictSummary() {
        val result = TestCouplingResult(
            violations = listOf(
                TestCouplingViolation(
                    testClass = ClassName("com.example.AppTest"),
                    testMethod = "testRegister",
                    portInterface = ClassName("com.example.ApplicationRepository"),
                    portMethod = MethodRef(ClassName("com.example.ApplicationRepository"), "addApplication"),
                ),
            ),
            testClassNonPortCalls = mapOf(ClassName("com.example.AppTest") to 3),
        )

        val output = TestCouplingFormatter.formatText(result)

        assertContains(output, "AppTest")
        assertContains(output, "MIXED")
        assertContains(output, "1")
    }

    // [TEST] LLM format shows per-violation detail
    @Test
    fun llmFormatShowsPerViolationDetail() {
        val result = TestCouplingResult(
            violations = listOf(
                TestCouplingViolation(
                    testClass = ClassName("com.example.AppTest"),
                    testMethod = "testRegister",
                    portInterface = ClassName("com.example.ApplicationRepository"),
                    portMethod = MethodRef(ClassName("com.example.ApplicationRepository"), "addApplication"),
                ),
            ),
            testClassNonPortCalls = mapOf(ClassName("com.example.AppTest") to 3),
        )

        val output = TestCouplingFormatter.formatLlm(result)

        assertContains(output, "testRegister")
        assertContains(output, "ApplicationRepository")
        assertContains(output, "addApplication")
    }

    // [TEST] Empty result produces no-results message
    @Test
    fun emptyResultProducesMessage() {
        val result = TestCouplingResult(violations = emptyList(), testClassNonPortCalls = emptyMap())

        val output = TestCouplingFormatter.formatText(result)

        assertContains(output, "No TTTD violations")
    }

    // [TEST] Detail text format shows per-call breakdown with verdict and confidence
    @Test
    fun detailTextFormatShowsCallBreakdown() {
        val result = TestCouplingResult(
            violations = listOf(
                TestCouplingViolation(
                    testClass = ClassName("com.example.SearchServiceTest"),
                    testMethod = "testSearch",
                    portInterface = ClassName("com.example.RARepository"),
                    portMethod = MethodRef(ClassName("com.example.RAClient"), "search"),
                ),
            ),
            testClassNonPortCalls = mapOf(ClassName("com.example.SearchServiceTest") to 3),
        )

        val output = TestCouplingFormatter.formatDetailText(result)

        assertContains(output, "SearchServiceTest")
        assertContains(output, "MIXED")
        assertContains(output, "RARepository.search")
        assertContains(output, "confidence=")
    }
}
