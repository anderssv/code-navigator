package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.config.OutputFormat
import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class HexRingFormatterTest {

    private val poll = ClassName("com.app.domain.Poll")
    private val port = ClassName("com.app.polls.PollsRepository")
    private val impl = ClassName("com.app.infra.SqlPollsRepository")
    private val root = ClassName("com.app.ApplicationKt")

    private fun output(
        layering: RingLayering,
        graph: RingGraph = RingGraph(classes = setOf(poll, port, impl)),
        unhonoured: List<UnhonouredDirective> = emptyList(),
        expectedRingCount: Int? = null,
        serviceTier: Set<ClassName> = emptySet(),
        domainServiceViolations: List<DomainServiceViolation> = emptyList(),
        portBypassViolations: List<PortBypassViolation> = emptyList(),
    ) = HexRingsOutput(
        layering = layering,
        graph = graph,
        unhonoured = unhonoured,
        expectedRingCount = expectedRingCount,
        testInvolvement = null,
        skippedFileWarning = null,
        serviceTier = serviceTier,
        domainServiceViolations = domainServiceViolations,
        portBypassViolations = portBypassViolations,
    )

    @Test
    fun `explains that no inversion boundary was found instead of reporting a ring ladder`() {
        val layering = RingLayering(
            ringCount = 1,
            diagnosis = RingDiagnosis.NO_INVERSION_BOUNDARY,
            rings = mapOf(poll to 0, port to 0, impl to 0),
        )

        val text = HexRingFormatter.format(output(layering), OutputFormat.TEXT)

        assertContains(text, "No dependency inversion")
        assertContains(text, "extract a port")
    }

    @Test
    fun `reports outward violations with the rings they cross`() {
        val layering = RingLayering(
            ringCount = 2,
            diagnosis = RingDiagnosis.LAYERED,
            rings = mapOf(poll to 0, port to 0, impl to 1),
            violations = listOf(ClassRingViolation(poll, impl, 0, 1, RingViolationType.OUTWARD)),
        )

        val text = HexRingFormatter.format(output(layering), OutputFormat.TEXT)

        assertContains(text, "com.app.domain.Poll")
        assertContains(text, "com.app.infra.SqlPollsRepository")
        assertContains(text, "ring 0")
        assertContains(text, "ring 1")
    }

    @Test
    fun `names composition roots as excluded rather than placing them in a ring`() {
        val layering = RingLayering(
            ringCount = 2,
            diagnosis = RingDiagnosis.LAYERED,
            rings = mapOf(poll to 0, port to 0, impl to 1),
        )
        val graph = RingGraph(classes = setOf(poll, port, impl, root), compositionRoots = setOf(root))

        val text = HexRingFormatter.format(output(layering, graph = graph), OutputFormat.TEXT)

        assertContains(text, "Composition root")
        assertContains(text, "com.app.ApplicationKt")
    }

    @Test
    fun `warns about a directive that could not be honoured`() {
        val layering = RingLayering(1, RingDiagnosis.NO_INVERSION_BOUNDARY, mapOf(poll to 0))
        val unhonoured = listOf(UnhonouredDirective(DirectiveKind.ADAPTER, "com.app.gone.*"))

        val text = HexRingFormatter.format(output(layering, unhonoured = unhonoured), OutputFormat.TEXT)

        assertContains(text, "com.app.gone.*")
        assertContains(text, "matched no class")
    }

    @Test
    fun `reports a detected ring count that disagrees with the configured pin`() {
        val layering = RingLayering(3, RingDiagnosis.LAYERED, mapOf(poll to 0))

        val text = HexRingFormatter.format(output(layering, expectedRingCount = 2), OutputFormat.TEXT)

        assertContains(text, "expected 2")
    }

    @Test
    fun `does not report a pin that matches`() {
        val layering = RingLayering(2, RingDiagnosis.LAYERED, mapOf(poll to 0))

        val text = HexRingFormatter.format(output(layering, expectedRingCount = 2), OutputFormat.TEXT)

        assertFalse(text.contains("expected 2"))
    }

    @Test
    fun `marks a service-tier class in the ring listing`() {
        val service = ClassName("com.app.polls.PollService")
        val layering = RingLayering(2, RingDiagnosis.LAYERED, mapOf(poll to 0, service to 0, impl to 1))
        val graph = RingGraph(classes = setOf(poll, service, impl))

        val text = HexRingFormatter.format(output(layering, graph = graph, serviceTier = setOf(service)), OutputFormat.TEXT)

        assertContains(text, "com.app.polls.PollService")
        assertContains(text, "service tier")
    }

    @Test
    fun `reports a domain class depending on a service-tier class as a violation`() {
        val service = ClassName("com.app.polls.PollService")
        val layering = RingLayering(2, RingDiagnosis.LAYERED, mapOf(poll to 0, service to 0, impl to 1))

        val text = HexRingFormatter.format(
            output(layering, domainServiceViolations = listOf(DomainServiceViolation(poll, service))),
            OutputFormat.TEXT,
        )

        assertContains(text, "com.app.domain.Poll")
        assertContains(text, "com.app.polls.PollService")
        assertContains(text, "Domain")
    }

    @Test
    fun `reports a port bypass violation with the port that was skipped`() {
        val service = ClassName("com.app.polls.PollService")
        val layering = RingLayering(2, RingDiagnosis.LAYERED, mapOf(poll to 0, service to 0, impl to 1))

        val text = HexRingFormatter.format(
            output(layering, portBypassViolations = listOf(PortBypassViolation(service, impl, port))),
            OutputFormat.TEXT,
        )

        assertContains(text, "com.app.polls.PollService")
        assertContains(text, "com.app.infra.SqlPollsRepository")
        assertContains(text, "com.app.polls.PollsRepository")
        assertContains(text, "bypass")
    }
}
