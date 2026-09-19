package no.f12.codenavigator.navigation.testalignment

import no.f12.codenavigator.navigation.relations.callgraph.CallGraph
import no.f12.codenavigator.navigation.relations.callgraph.MethodRef
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.types.SourceSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The three cases come from the field report that asked for this check (greitt, v0.1.116): a test in
 * the wrong package, two test classes sharing a simple name, and a test named after nothing that
 * exists. All three were real defects that no dependency metric registered, before or after the fix.
 */
class TestAlignmentBuilderTest {

    private class GraphBuilder {
        private val edges = mutableMapOf<MethodRef, MutableSet<MethodRef>>()
        private val sourceSets = mutableMapOf<ClassName, SourceSet>()
        private val sourceFiles = mutableMapOf<ClassName, String>()
        private val testAnnotated = mutableSetOf<ClassName>()

        fun production(fqcn: String) = apply {
            val cls = ClassName(fqcn)
            sourceSets[cls] = SourceSet.MAIN
            sourceFiles[cls] = "${cls.simpleName()}.kt"
        }

        fun test(fqcn: String, calls: List<Pair<String, String>> = emptyList()) = apply {
            val cls = ClassName(fqcn)
            sourceSets[cls] = SourceSet.TEST
            sourceFiles[cls] = "${cls.simpleName()}.kt"
            testAnnotated.add(cls)
            calls.forEach { (target, method) ->
                edges.getOrPut(MethodRef(cls, "test")) { mutableSetOf() }
                    .add(MethodRef(ClassName(target), method))
            }
        }

        fun build() = CallGraph(edges, sourceFiles, emptyMap(), sourceSets, emptyMap(), testAnnotated)
    }

    private fun analyze(exclude: Regex? = null, build: GraphBuilder.() -> Unit): TestAlignmentResult =
        TestAlignmentBuilder.analyze(
            GraphBuilder().apply(build).build(),
            TestAlignmentAnalysisConfig(exclude = exclude),
        )

    @Test
    fun `reports a test whose subject lives in another package when name and calls agree`() {
        val result = analyze {
            production("polls.model.PollDomain")
            test("polls.PollDomainTest", listOf("polls.model.PollDomain" to "open", "polls.model.PollDomain" to "close"))
        }

        val mismatch = result.packageMismatches.single()
        assertEquals(ClassName("polls.PollDomainTest"), mismatch.testClass)
        assertEquals(ClassName("polls.model.PollDomain"), mismatch.subject)
        assertEquals(PackageName("polls"), mismatch.testPackage)
        assertEquals(PackageName("polls.model"), mismatch.subjectPackage)
        assertEquals(2, mismatch.distinctMethods)
        assertTrue(result.nameMismatches.isEmpty())
    }

    @Test
    fun `a test sitting with its subject is not reported`() {
        val result = analyze {
            production("polls.model.PollDomain")
            test("polls.model.PollDomainTest", listOf("polls.model.PollDomain" to "open"))
        }

        assertTrue(result.isEmpty(), "reported a correctly placed test: $result")
    }

    // The failure mode cnavMoveSuggest cannot avoid: object mothers live with the data they build,
    // so a correctly-placed test's gravity points at the model package.
    @Test
    fun `gravity toward an object mother does not move a correctly placed test`() {
        val result = analyze {
            production("polls.UserPollsService")
            production("polls.model.Poll")
            test(
                "polls.UserPollsServiceTest",
                listOf(
                    "polls.UserPollsService" to "pollsFor",
                    "polls.model.Poll" to "getId",
                    "polls.model.Poll" to "getSlots",
                    "polls.model.Poll" to "getTitle",
                ),
            )
        }

        assertTrue(result.packageMismatches.isEmpty(), "object-mother gravity moved a correctly placed test: $result")
    }

    @Test
    fun `a test named after a class that does not exist is advisory, not a violation`() {
        val result = analyze {
            production("polls.model.SlotRowKt")
            test("polls.model.SlotBoardRowsTest", listOf("polls.model.SlotRowKt" to "slotRowsByDay"))
        }

        val advisory = result.unknownSubjects.single()
        assertEquals("SlotBoardRows", advisory.expectedSubjectName)
        assertEquals(ClassName("polls.model.SlotRowKt"), advisory.callSubject.className)
        assertEquals(0, result.violationCount, "an advisory must not fail a build")
        assertEquals(1, result.findingCount)
    }

    @Test
    fun `reports a test named after a class in another package while driving something else`() {
        val result = analyze {
            production("polls.calendar.DateFormatting")
            production("time.DateUtilsKt")
            test("time.DateFormattingTest", listOf("time.DateUtilsKt" to "format", "time.DateUtilsKt" to "parse"))
        }

        val mismatch = result.nameMismatches.single()
        assertEquals(ClassName("polls.calendar.DateFormatting"), mismatch.nameSubject)
        assertEquals(ClassName("time.DateUtilsKt"), mismatch.callSubject.className)
        assertEquals(1, result.violationCount)
    }

    // Field verification (greitt, 0.1.118-SNAPSHOT): 11 of 15 name findings were this exact shape —
    // a test sitting next to a production file of the matching name, driving the domain type its
    // subject is an extension of. Asserting is calling; that is not a defect.
    @Test
    fun `a test sitting next to its named subject is not reported however its calls are distributed`() {
        val result = analyze {
            production("polls.model.PollDomainKt")
            production("polls.model.DayPoll")
            test(
                "polls.model.PollDomainTest",
                listOf(
                    "polls.model.PollDomainKt" to "isOpen",
                    "polls.model.DayPoll" to "getId",
                    "polls.model.DayPoll" to "getSlots",
                    "polls.model.DayPoll" to "getTitle",
                    "polls.model.DayPoll" to "getOwner",
                ),
            )
        }

        assertTrue(result.isEmpty(), "flagged a test that sits next to the class it names: $result")
    }

    // Same run: `EmailVerificationServiceTest -> FakeEmailClient (7 distinct methods)`. A production
    // fallback fake lives in src/main, so source set alone does not exclude it.
    @Test
    fun `a fake is not mistaken for the subject a test is about`() {
        val result = analyze {
            production("notification.EmailVerificationService")
            production("notification.FakeEmailClient")
            test(
                "polls.EmailVerificationServiceTest",
                listOf(
                    "notification.EmailVerificationService" to "verify",
                    "notification.FakeEmailClient" to "sent",
                    "notification.FakeEmailClient" to "clear",
                    "notification.FakeEmailClient" to "lastMessage",
                ),
            )
        }

        val mismatch = result.packageMismatches.single()
        assertEquals(ClassName("notification.EmailVerificationService"), mismatch.subject)
        assertTrue(result.nameMismatches.isEmpty())
    }

    @Test
    fun `reports two test classes sharing a simple name`() {
        val result = analyze {
            production("polls.calendar.DateFormatting")
            test("polls.calendar.DateFormattingTest", listOf("polls.calendar.DateFormatting" to "format"))
            test("time.DateFormattingTest", listOf("polls.calendar.DateFormatting" to "format"))
        }

        val duplicate = result.duplicateNames.single()
        assertEquals("DateFormattingTest", duplicate.simpleName)
        assertEquals(
            listOf(ClassName("polls.calendar.DateFormattingTest"), ClassName("time.DateFormattingTest")),
            duplicate.testClasses,
        )
    }

    @Test
    fun `a Kotlin file facade is the same subject as the name it promises`() {
        val result = analyze {
            production("polls.model.ResponseLookupKt")
            test("polls.model.ResponseLookupTest", listOf("polls.model.ResponseLookupKt" to "lookup"))
        }

        assertTrue(result.isEmpty(), "treated the SubjectKt facade as a different subject: $result")
    }

    @Test
    fun `a test calling nothing in production is not reported`() {
        val result = analyze {
            production("polls.model.Poll")
            test("polls.UserJourneysTest")
        }

        assertTrue(result.isEmpty())
    }

    @Test
    fun `a test driving several classes equally has no clear subject and is not moved`() {
        val result = analyze {
            production("polls.UserPollsService")
            production("web.Routes")
            test("web.UserJourneysTest", listOf("polls.UserPollsService" to "create", "web.Routes" to "handle"))
        }

        assertTrue(result.packageMismatches.isEmpty())
    }

    @Test
    fun `the exclude pattern drops tests that legitimately span many classes`() {
        val result = analyze(exclude = Regex("Journey|EndpointsTest")) {
            production("polls.model.PollDomain")
            test("web.CalendarExportEndpointsTest", listOf("polls.model.PollDomain" to "open"))
            test("polls.PollDomainTest", listOf("polls.model.PollDomain" to "open"))
        }

        assertEquals(1, result.analysedTests)
        assertEquals(1, result.excludedTests)
        assertEquals(ClassName("polls.PollDomainTest"), result.packageMismatches.single().testClass)
    }

    @Test
    fun `a class that follows no test naming convention is not cross-checked`() {
        val result = analyze {
            production("polls.model.PollDomain")
            test("polls.fixtures.PollMother", listOf("polls.model.PollDomain" to "open"))
        }

        assertTrue(result.isEmpty(), "cross-checked a class with no test suffix: $result")
    }
}
