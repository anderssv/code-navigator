package no.f12.codenavigator.navigation.ambient

import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmbientBuilderTest {

    private fun call(
        className: String,
        method: String = "doWork",
        signal: AmbientSignal = AmbientBlocklist.SIGNALS.first { it.label == "Instant.now()" },
        site: AmbientSite = AmbientSite.METHOD_BODY,
    ) = AmbientCall(
        callerClass = ClassName(className),
        callerMethod = method,
        signal = signal,
        descriptor = "()Ljava/time/Instant;",
        sourceFile = "Domain.kt",
        line = 12,
        site = site,
    )

    private val randomSignal = AmbientBlocklist.SIGNALS.first { it.label == "UUID.randomUUID()" }

    @Test
    fun `keeps calls from domain classes and drops the rest`() {
        val scan = AmbientScan(
            calls = listOf(call("com.example.domain.Poll"), call("com.example.adapters.PollDao")),
            clockInjectingClasses = setOf(ClassName("com.example.services.PollService")),
        )

        val result = AmbientBuilder.analyze(
            scan,
            AmbientAnalysisConfig(domainClasses = setOf(ClassName("com.example.domain.Poll")), categories = AmbientCategory.entries.toSet()),
        )

        assertEquals(1, result.violations.size)
        assertEquals("com.example.domain.Poll", result.violations[0].callerClass.value)
    }

    // A lambda or inner class compiles to Outer$name, but the subject set holds top-level names.
    @Test
    fun `attributes a call from a lambda to its top-level owner`() {
        val scan = AmbientScan(
            calls = listOf(call("com.example.domain.Poll\$expire\$1")),
            clockInjectingClasses = setOf(ClassName("com.example.services.PollService")),
        )

        val result = AmbientBuilder.analyze(
            scan,
            AmbientAnalysisConfig(domainClasses = setOf(ClassName("com.example.domain.Poll")), categories = AmbientCategory.entries.toSet()),
        )

        assertEquals(1, result.violations.size)
        assertEquals(setOf(ClassName("com.example.domain.Poll")), result.byClass().keys)
    }

    @Test
    fun `separates initializer sites from method bodies`() {
        val scan = AmbientScan(
            calls = listOf(
                call("com.example.domain.Poll"),
                call("com.example.domain.Event", method = "<init>", site = AmbientSite.INITIALIZER),
            ),
            clockInjectingClasses = emptySet(),
        )

        val result = AmbientBuilder.analyze(
            scan,
            AmbientAnalysisConfig(
                domainClasses = setOf(ClassName("com.example.domain.Poll"), ClassName("com.example.domain.Event")),
                categories = AmbientCategory.entries.toSet(),
            ),
        )

        assertEquals(1, result.violations.size)
        assertEquals(1, result.initializerViolations.size)
        assertEquals(2, result.all.size)
    }

    @Test
    fun `filters by requested categories`() {
        val scan = AmbientScan(
            calls = listOf(call("com.example.domain.Poll"), call("com.example.domain.Poll", signal = randomSignal)),
            clockInjectingClasses = emptySet(),
        )

        val result = AmbientBuilder.analyze(
            scan,
            AmbientAnalysisConfig(domainClasses = setOf(ClassName("com.example.domain.Poll")), categories = setOf(AmbientCategory.RANDOM)),
        )

        assertEquals(1, result.all.size)
        assertEquals(AmbientCategory.RANDOM, result.all[0].category)
    }

    @Test
    fun `honours the exclude regex`() {
        val scan = AmbientScan(calls = listOf(call("com.example.domain.Poll")), clockInjectingClasses = emptySet())

        val result = AmbientBuilder.analyze(
            scan,
            AmbientAnalysisConfig(
                domainClasses = setOf(ClassName("com.example.domain.Poll")),
                categories = AmbientCategory.entries.toSet(),
                exclude = Regex("\\.domain\\."),
            ),
        )

        assertTrue(result.all.isEmpty())
    }

    // Without clock-injection evidence anywhere, a clock read isn't drift from the project's
    // practice — it is the project's practice. Randomness stays actionable regardless.
    @Test
    fun `clock findings are advisory when the project never injects a clock`() {
        val scan = AmbientScan(
            calls = listOf(call("com.example.domain.Poll"), call("com.example.domain.Poll", signal = randomSignal)),
            clockInjectingClasses = emptySet(),
        )

        val result = AmbientBuilder.analyze(
            scan,
            AmbientAnalysisConfig(domainClasses = setOf(ClassName("com.example.domain.Poll")), categories = AmbientCategory.entries.toSet()),
        )

        assertEquals(ClockConvention.ABSENT, result.clockConvention)
        assertEquals(listOf(AmbientCategory.RANDOM), result.actionable.map { it.category })
    }

    @Test
    fun `clock findings are actionable when the project injects a clock elsewhere`() {
        val scan = AmbientScan(
            calls = listOf(call("com.example.domain.Poll")),
            clockInjectingClasses = setOf(ClassName("com.example.services.PollService")),
        )

        val result = AmbientBuilder.analyze(
            scan,
            AmbientAnalysisConfig(domainClasses = setOf(ClassName("com.example.domain.Poll")), categories = AmbientCategory.entries.toSet()),
        )

        assertEquals(ClockConvention.INJECTED, result.clockConvention)
        assertEquals(1, result.actionable.size)
    }
}
