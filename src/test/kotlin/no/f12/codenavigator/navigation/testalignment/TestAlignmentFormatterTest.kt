package no.f12.codenavigator.navigation.testalignment

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class TestAlignmentFormatterTest {

    private val result = TestAlignmentResult(
        packageMismatches = listOf(
            PackageMismatch(
                testClass = ClassName("polls.PollDomainTest"),
                subject = ClassName("polls.model.PollDomain"),
                testPackage = PackageName("polls"),
                subjectPackage = PackageName("polls.model"),
                distinctMethods = 7,
            ),
        ),
        nameMismatches = listOf(
            NameMismatch(
                testClass = ClassName("time.DateFormattingTest"),
                nameSubject = ClassName("polls.calendar.DateFormatting"),
                callSubject = CallSubject(ClassName("time.DateUtilsKt"), 4),
            ),
        ),
        duplicateNames = listOf(
            DuplicateTestName("DateFormattingTest", listOf(ClassName("polls.calendar.DateFormattingTest"), ClassName("time.DateFormattingTest"))),
        ),
        unknownSubjects = listOf(
            UnknownSubject(
                testClass = ClassName("polls.model.SlotBoardRowsTest"),
                expectedSubjectName = "SlotBoardRows",
                callSubject = CallSubject(ClassName("polls.model.SlotRowKt"), 3),
            ),
        ),
        analysedTests = 107,
        excludedTests = 2,
    )

    @Test
    fun `text output separates the package delta from the name delta`() {
        val text = TestAlignmentFormatter.formatText(result)

        assertContains(text, "test-alignment: 3 violation(s) + 1 advisory in 107 test class(es), 2 excluded")
        assertContains(text, "Wrong package (1)")
        assertContains(text, "cnavMoveClass")
        assertContains(text, "Wrong name (1)")
        assertContains(text, "cnavRenameClass")
        assertContains(text, "Duplicate test class names (1)")
        assertContains(text, "Named after nothing that exists (1) — advisory, not counted as violations:")
    }

    @Test
    fun `llm output is one line per finding, tagged by kind`() {
        val llm = TestAlignmentFormatter.formatLlm(result)

        assertContains(llm, "package polls.PollDomainTest polls -> polls.model subject=polls.model.PollDomain methods=7")
        assertContains(llm, "name time.DateFormattingTest names=polls.calendar.DateFormatting calls=time.DateUtilsKt methods=4")
        assertContains(llm, "advisory-unknown-subject polls.model.SlotBoardRowsTest expected=SlotBoardRows calls=polls.model.SlotRowKt methods=3")
        assertContains(llm, "duplicate DateFormattingTest polls.calendar.DateFormattingTest,time.DateFormattingTest")
    }

    @Test
    fun `json exposes each finding kind as its own array`() {
        val json = TestAlignmentFormatter.formatJson(result)

        assertContains(json, "\"violationCount\":3")
        assertContains(json, "\"findingCount\":4")
        assertContains(json, "\"packageMismatches\"")
        assertContains(json, "\"nameMismatches\"")
        assertContains(json, "\"duplicateNames\"")
        assertContains(json, "\"unknownSubjects\"")
        assertContains(json, "\"subjectPackage\":\"polls.model\"")
    }

    @Test
    fun `a clean project says so and reports how many tests were checked`() {
        val clean = TestAlignmentResult(emptyList(), emptyList(), emptyList(), analysedTests = 42, excludedTests = 0)


        val text = TestAlignmentFormatter.formatText(clean)

        assertTrue(text.startsWith(TestAlignmentFormatter.NO_FINDINGS))
        assertContains(text, "42 test class(es) checked")
    }
}
