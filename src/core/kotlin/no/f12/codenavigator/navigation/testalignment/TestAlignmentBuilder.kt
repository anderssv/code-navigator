package no.f12.codenavigator.navigation.testalignment

import no.f12.codenavigator.navigation.relations.callgraph.CallGraph
import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.types.SourceSet

/** The production class a test appears to be about, and how strongly. */
data class CallSubject(val className: ClassName, val distinctMethods: Int)

/**
 * A test that sits in a different package than the class it tests, where the naming convention and
 * the call graph agree on which class that is. Fix with `cnavMoveClass`.
 */
data class PackageMismatch(
    val testClass: ClassName,
    val subject: ClassName,
    val testPackage: PackageName,
    val subjectPackage: PackageName,
    val distinctMethods: Int,
)

/**
 * The test is named after a class that lives in *another* package, but drives something else — so
 * either the name is wrong, or the test belongs where its name points. Fix with `cnavRenameClass`,
 * or move the assertions to the seam the name promises.
 *
 * Deliberately not raised when the named subject sits in the test's own package: there the placement
 * and the name are both already right, and whatever the test calls most is fixture and assertion
 * traffic. A test of extension functions necessarily drives the receiver type through more distinct
 * methods than the facade under test, and asserting is calling — measured on a real project, that
 * shape alone produced 11 false positives out of 15.
 */
data class NameMismatch(
    val testClass: ClassName,
    val nameSubject: ClassName,
    val callSubject: CallSubject,
)

/**
 * `FooTest` where no production class named `Foo` exists. Advisory, never a violation: a
 * behaviour-named test (`TimezoneRoundTripTest`, `PollUpdateRetryTest`) is a legitimate convention,
 * not a defect — but a test named after nothing that exists is also how a genuinely misnamed test
 * looks, so it is worth showing and never worth failing a build over.
 */
data class UnknownSubject(
    val testClass: ClassName,
    val expectedSubjectName: String,
    val callSubject: CallSubject,
)

/**
 * Two or more test classes sharing a simple name. Not a dependency fact, so no dependency metric can
 * see it — but test reports name them ambiguously.
 */
data class DuplicateTestName(val simpleName: String, val testClasses: List<ClassName>)

data class TestAlignmentResult(
    val packageMismatches: List<PackageMismatch>,
    val nameMismatches: List<NameMismatch>,
    val duplicateNames: List<DuplicateTestName>,
    val unknownSubjects: List<UnknownSubject> = emptyList(),
    val analysedTests: Int,
    val excludedTests: Int,
) {
    /** What `--fail-on-violation` counts: the kinds precise enough to gate a build on. */
    val violationCount: Int get() = packageMismatches.size + nameMismatches.size + duplicateNames.size

    val findingCount: Int get() = violationCount + unknownSubjects.size

    fun isEmpty(): Boolean = findingCount == 0
}

data class TestAlignmentAnalysisConfig(
    val exclude: Regex? = null,
    /** Suffixes stripped from a test class name to get the subject name it promises. */
    val testSuffixes: List<String> = listOf("Tests", "Test", "IT", "Spec"),
)

/**
 * Answers "does every test sit with — and is it named after — the class it tests?".
 *
 * Deliberately not a tuning pass on `cnavMoveSuggest`: a test's dependency gravity is dominated by
 * object mothers and fixtures, which live with the data they build, so no edge-counting metric can
 * tell a correctly-placed test from a misplaced one. The two signals here are the call graph (which
 * production class does this test actually exercise, counted in distinct methods — this sees
 * same-package calls, which imports cannot) and the `FooTest -> Foo` naming convention. A package
 * move is only proposed where both agree; where they disagree, that disagreement is the finding.
 */
object TestAlignmentBuilder {

    fun analyze(graph: CallGraph, config: TestAlignmentAnalysisConfig): TestAlignmentResult {
        val productionClasses = graph.projectClasses()
            .filter { graph.sourceSetOf(it) == SourceSet.MAIN }
            .toSet()

        val allTestClasses = graph.projectClasses()
            .filter { graph.sourceSetOf(it) == SourceSet.TEST }
            .filter { it == it.topLevelClass() }
            .filter { graph.hasTestAnnotations(it) || it.isTest() }
            .sorted()

        val testClasses = allTestClasses.filter { config.exclude?.containsMatchIn(it.value) != true }

        val calleesByCaller = calleesByCaller(graph)
        val productionBySimpleName = productionClasses.groupBy { normalizeSubjectName(it.simpleName()) }

        val packageMismatches = mutableListOf<PackageMismatch>()
        val nameMismatches = mutableListOf<NameMismatch>()
        val unknownSubjects = mutableListOf<UnknownSubject>()

        for (testClass in testClasses) {
            val callSubject = inferCallSubject(testClass, calleesByCaller, productionClasses)
            val expectedName = expectedSubjectName(testClass.simpleName(), config.testSuffixes)
                ?: continue // Not named by the convention; there is nothing to cross-check against.

            val nameCandidates = productionBySimpleName[normalizeSubjectName(expectedName)].orEmpty()
            // Nothing exercised in production: no second signal, so neither check has anything to
            // cross-check the name against.
            if (callSubject == null) continue

            if (nameCandidates.isEmpty()) {
                unknownSubjects.add(UnknownSubject(testClass, expectedName, callSubject))
                continue
            }

            val agreed = nameCandidates.firstOrNull { agree(it, callSubject.className) }
            if (agreed != null) {
                // Both signals agree on the subject, so its package is a trustworthy answer.
                if (agreed.packageName() != testClass.packageName()) {
                    packageMismatches.add(
                        PackageMismatch(
                            testClass = testClass,
                            subject = agreed,
                            testPackage = testClass.packageName(),
                            subjectPackage = agreed.packageName(),
                            distinctMethods = callSubject.distinctMethods,
                        ),
                    )
                }
                continue
            }

            // The signals disagree. That only means something when the named subject lives somewhere
            // else — see [NameMismatch].
            if (nameCandidates.any { it.packageName() == testClass.packageName() }) continue
            nameMismatches.add(NameMismatch(testClass, nameCandidates.first(), callSubject))
        }

        val duplicateNames = testClasses
            .groupBy { it.simpleName() }
            .filterValues { it.size > 1 }
            .map { (name, classes) -> DuplicateTestName(name, classes.sorted()) }
            .sortedBy { it.simpleName }

        return TestAlignmentResult(
            packageMismatches = packageMismatches.sortedBy { it.testClass },
            nameMismatches = nameMismatches.sortedBy { it.testClass },
            duplicateNames = duplicateNames,
            unknownSubjects = unknownSubjects.sortedBy { it.testClass },
            analysedTests = testClasses.size,
            excludedTests = allTestClasses.size - testClasses.size,
        )
    }

    /**
     * Highest number of *distinct* methods called, so one method called in fifty test cases does not
     * outrank a class the test drives through its whole surface. A tie means no clear subject.
     */
    private fun inferCallSubject(
        testClass: ClassName,
        calleesByCaller: Map<ClassName, Set<Pair<ClassName, String>>>,
        productionClasses: Set<ClassName>,
    ): CallSubject? {
        val counts = calleesByCaller[testClass].orEmpty()
            .filter { (callee, _) -> callee.topLevelClass() in productionClasses || callee in productionClasses }
            .filterNot { (callee, _) -> isFixture(callee.topLevelClass()) }
            .groupingBy { (callee, _) -> callee.topLevelClass() }
            .eachCount()
        if (counts.isEmpty()) return null

        val ranked = counts.entries.sortedWith(compareByDescending<Map.Entry<ClassName, Int>> { it.value }.thenBy { it.key })
        if (ranked.size > 1 && ranked[0].value == ranked[1].value) return null
        return CallSubject(ranked[0].key, ranked[0].value)
    }

    /**
     * A fake, mother or builder is what a test asserts *with*, never what it is about — and since
     * asserting is calling, an unfiltered ranking puts the fake on top. Matched by name because a
     * production fallback fake (an email client with no provider configured, say) lives in `src/main`
     * like any other class.
     */
    private fun isFixture(cls: ClassName): Boolean {
        val simple = cls.simpleName()
        return simple.startsWith("Fake") ||
            FIXTURE_SUFFIXES.any { simple.endsWith(it) }
    }

    private val FIXTURE_SUFFIXES = listOf("Fake", "Mother", "Builder", "Fixture", "Fixtures", "TestData", "Stub", "Mock")

    private fun calleesByCaller(graph: CallGraph): Map<ClassName, Set<Pair<ClassName, String>>> {
        val byCaller = mutableMapOf<ClassName, MutableSet<Pair<ClassName, String>>>()
        graph.forEachEdge { caller, callee ->
            byCaller.getOrPut(caller.className.topLevelClass()) { mutableSetOf() }
                .add(callee.className to callee.methodName)
        }
        return byCaller
    }

    /** `PollDomainTest` -> `PollDomain`; returns null when the name follows no test convention. */
    internal fun expectedSubjectName(testSimpleName: String, suffixes: List<String>): String? {
        val suffix = suffixes.firstOrNull { testSimpleName.endsWith(it) && testSimpleName.length > it.length }
            ?: return null
        return testSimpleName.removeSuffix(suffix)
    }

    /** A Kotlin file facade (`SlotRowKt`) is the same subject as the name `SlotRow` promises. */
    private fun normalizeSubjectName(simpleName: String): String = simpleName.removeSuffix("Kt")

    private fun agree(nameSubject: ClassName, callSubject: ClassName): Boolean =
        nameSubject == callSubject ||
            (nameSubject.packageName() == callSubject.packageName() &&
                normalizeSubjectName(nameSubject.simpleName()) == normalizeSubjectName(callSubject.simpleName()))
}
