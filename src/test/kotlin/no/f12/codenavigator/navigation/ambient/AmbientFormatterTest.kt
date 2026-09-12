package no.f12.codenavigator.navigation.ambient

import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AmbientFormatterTest {

    private val clockSignal = AmbientBlocklist.SIGNALS.first { it.label == "Instant.now()" }
    private val randomSignal = AmbientBlocklist.SIGNALS.first { it.label == "UUID.randomUUID()" }

    private fun call(
        className: String = "com.example.domain.Poll",
        method: String = "expire",
        signal: AmbientSignal = clockSignal,
        site: AmbientSite = AmbientSite.METHOD_BODY,
        line: Int? = 226,
    ) = AmbientCall(
        callerClass = ClassName(className),
        callerMethod = method,
        signal = signal,
        descriptor = "()Ljava/time/Instant;",
        sourceFile = "PollDomain.kt",
        line = line,
        site = site,
    )

    private fun result(
        violations: List<AmbientCall> = listOf(call()),
        initializers: List<AmbientCall> = emptyList(),
        clockInjecting: Set<ClassName> = setOf(ClassName("com.example.services.PollService")),
    ) = AmbientResult(
        violations = violations,
        initializerViolations = initializers,
        domainClassCount = 57,
        clockInjectingClasses = clockInjecting,
        subjectSource = SubjectSource.RINGS,
    )

    @Test
    fun `text output reports the finding with its location`() {
        val text = AmbientFormatter.formatText(result(), detail = false)

        assertContains(text, "CLOCK (1)")
        assertContains(text, "Poll")
        assertContains(text, "Instant.now()")
        assertContains(text, "PollDomain.kt:226")
    }

    // Grouping by class must not collapse to one location: two reads in the same class are
    // usually on different lines, and a single line number would send the reader to the wrong one.
    @Test
    fun `grouped output keeps a location per call site`() {
        val text = AmbientFormatter.formatText(
            result(violations = listOf(call(method = "expire", line = 226), call(method = "close", line = 240))),
            detail = false,
        )

        assertContains(text, "Instant.now() PollDomain.kt:226, Instant.now() PollDomain.kt:240")
    }

    @Test
    fun `text header reports how much of the domain is affected`() {
        val text = AmbientFormatter.formatText(result(), detail = false)

        assertContains(text, "1 finding(s) in 1 of 57 ring-0 class(es)")
    }

    @Test
    fun `text output flags clock findings as drift when the project injects a clock`() {
        val text = AmbientFormatter.formatText(result(), detail = false)

        assertContains(text, "drift from the project's own practice")
    }

    @Test
    fun `text output marks clock findings advisory when no clock is injected anywhere`() {
        val text = AmbientFormatter.formatText(result(clockInjecting = emptySet()), detail = false)

        assertContains(text, "advisory")
    }

    @Test
    fun `initializer findings are listed in their own section`() {
        val text = AmbientFormatter.formatText(
            result(initializers = listOf(call(className = "com.example.domain.Event", method = "<init>", site = AmbientSite.INITIALIZER))),
            detail = false,
        )

        assertContains(text, "Default arguments and initializers (1)")
        assertContains(text, "Event.<init>")
    }

    @Test
    fun `detail output lists every call site`() {
        val text = AmbientFormatter.formatText(
            result(violations = listOf(call(method = "expire"), call(method = "close"))),
            detail = true,
        )

        assertContains(text, "Poll.expire")
        assertContains(text, "Poll.close")
    }

    @Test
    fun `no findings renders a clean message`() {
        val text = AmbientFormatter.formatText(result(violations = emptyList()), detail = false)

        assertEquals(AmbientFormatter.NO_FINDINGS, text)
    }

    @Test
    fun `llm output is one line per finding`() {
        val text = AmbientFormatter.formatLlm(result(violations = listOf(call(), call(signal = randomSignal, method = "newId"))))

        assertContains(text, "CLOCK Poll.expire -> Instant.now() PollDomain.kt:226")
        assertContains(text, "RANDOM Poll.newId -> UUID.randomUUID() PollDomain.kt:226")
    }

    @Test
    fun `json output exposes the structured signals`() {
        val json = AmbientFormatter.formatJson(result())

        assertContains(json, "\"subjectSource\":\"RINGS\"")
        assertContains(json, "\"clockConvention\":\"INJECTED\"")
        assertContains(json, "\"category\":\"CLOCK\"")
        assertContains(json, "\"class\":\"com.example.domain.Poll\"")
        assertContains(json, "\"line\":226")
        assertContains(json, "\"site\":\"METHOD_BODY\"")
    }

    @Test
    fun `json omits the line when bytecode carried no line numbers`() {
        val json = AmbientFormatter.formatJson(result(violations = listOf(call(line = null))))

        assertFalse(json.contains("\"line\""))
    }
}
