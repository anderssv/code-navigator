package no.f12.codenavigator.navigation.testalignment

import no.f12.codenavigator.formatting.TaskGuidance

object TestAlignmentGuidance {

    val GUIDANCE = TaskGuidance(
        purpose = """
            Checks that every test sits with, and is named after, the class it tests. No dependency
            metric can answer this: a test's gravity is dominated by object mothers and fixtures,
            which live with the data they build, so a correctly-placed test and a misplaced one
            produce the same shape. Two duplicate test class names are not a dependency fact at all.
            The subject is inferred from the call graph — the production class the test drives through
            the most distinct methods, which sees same-package calls that imports cannot — and
            cross-checked against the FooTest -> Foo convention.
        """.trimIndent(),
        parameterGuidance = """
            --exclude=<regex> drops test classes by fully-qualified name. Use it for the tests that
            legitimately span many classes: endpoint tests that live with their feature slice while
            calling into services and repositories, and journey/E2E tests
            (e.g. --exclude="Journey|EndpointsTest").
            --fail-on-violation with --max-violations=<n> turns this into a CI gate; ratchet n down.
        """.trimIndent(),
        interpretation = """
            Three violation kinds, with three different fixes — reported separately on purpose:
            - package: the naming convention and the call graph agree on the subject, and it lives in a
              different package than the test. Fix with cnavMoveClass. This is the precise one.
            - name: the test is named after a class in ANOTHER package but drives something else —
              either the name is wrong or the test belongs where its name points. Fix with
              cnavRenameClass, or move it. Never raised when the named class sits in the test's own
              package: there both signals are already satisfied, and whatever the test calls most is
              fixture and assertion traffic (a test of extension functions necessarily drives the
              receiver type through more distinct methods than the facade under test).
            - duplicate: two test classes share a simple name, so test reports name them ambiguously.
            Plus one advisory list, NOT counted by --fail-on-violation: tests named after a class that
            does not exist. A behaviour-named test (TimezoneRoundTripTest) is a legitimate convention
            and a genuinely misnamed test looks identical from here, so this is shown, never gated.
            A test that calls nothing in production is never reported; neither is a test whose name
            follows no convention (no Test/Tests/IT/Spec suffix), since there is no second signal.
            Fakes, mothers, builders and fixtures are excluded from subject ranking — a test asserts
            WITH them, it is not about them, and asserting is calling.
        """.trimIndent(),
    )
}
