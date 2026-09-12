package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals

class ServiceTierDetectorTest {

    private val raClient = ClassName("com.app.ra.RAClient")
    private val raClientImpl = ClassName("com.app.ra.RAClientImpl")
    private val resetService = ClassName("com.app.passwordreset.ResetPasswordService")
    private val sessionAndKey = ClassName("com.app.passwordreset.SessionAndPublicKey")
    private val domainValue = ClassName("com.app.domain.Money")

    @Test
    fun `a class with a direct dependency on a port is service tier`() {
        val graph = RingGraph(
            classes = setOf(raClient, raClientImpl, resetService),
            interfaces = setOf(raClient),
            implementedBy = mapOf(raClient to setOf(raClientImpl)),
            ioClasses = setOf(raClientImpl),
            dependsOn = mapOf(resetService to setOf(raClient)),
        )

        val serviceTier = ServiceTierDetector.detect(graph)

        assertEquals(setOf(resetService), serviceTier)
    }

    @Test
    fun `a class with a direct dependency on an adapter with no port is service tier`() {
        val noPortAdapter = ClassName("com.app.infra.SmtpMailer")
        val graph = RingGraph(
            classes = setOf(noPortAdapter, resetService),
            ioClasses = setOf(noPortAdapter),
            dependsOn = mapOf(resetService to setOf(noPortAdapter)),
        )

        val serviceTier = ServiceTierDetector.detect(graph)

        assertEquals(setOf(resetService), serviceTier)
    }

    @Test
    fun `a class depending only on other domain classes is not service tier`() {
        val graph = RingGraph(
            classes = setOf(raClient, raClientImpl, resetService, domainValue),
            interfaces = setOf(raClient),
            implementedBy = mapOf(raClient to setOf(raClientImpl)),
            ioClasses = setOf(raClientImpl),
            dependsOn = mapOf(
                resetService to setOf(raClient, domainValue),
            ),
        )

        val serviceTier = ServiceTierDetector.detect(graph)

        assertEquals(setOf(resetService), serviceTier, "domainValue should not become service tier just because a service also depends on it")
    }

    @Test
    fun `a class two hops from the boundary is not itself service tier — flat, not transitive`() {
        val orchestrator = ClassName("com.app.passwordreset.PasswordResetOrchestrator")
        val graph = RingGraph(
            classes = setOf(raClient, raClientImpl, resetService, orchestrator),
            interfaces = setOf(raClient),
            implementedBy = mapOf(raClient to setOf(raClientImpl)),
            ioClasses = setOf(raClientImpl),
            dependsOn = mapOf(
                resetService to setOf(raClient),
                orchestrator to setOf(resetService),
            ),
        )

        val serviceTier = ServiceTierDetector.detect(graph)

        assertEquals(setOf(resetService), serviceTier, "orchestrator only reaches the port through resetService, not directly")
    }

    @Test
    fun `domainServiceViolations flags a domain class depending on a service-tier class`() {
        val graph = RingGraph(
            classes = setOf(raClient, raClientImpl, resetService, sessionAndKey),
            interfaces = setOf(raClient),
            implementedBy = mapOf(raClient to setOf(raClientImpl)),
            ioClasses = setOf(raClientImpl),
            dependsOn = mapOf(
                resetService to setOf(raClient, sessionAndKey),
                sessionAndKey to setOf(resetService),
            ),
        )
        val serviceTier = ServiceTierDetector.detect(graph)

        val violations = ServiceTierDetector.domainServiceViolations(graph, serviceTier)

        assertEquals(listOf(DomainServiceViolation(sessionAndKey, resetService)), violations)
    }

    @Test
    fun `domainServiceViolations does not flag a service depending on domain`() {
        val graph = RingGraph(
            classes = setOf(raClient, raClientImpl, resetService, sessionAndKey),
            interfaces = setOf(raClient),
            implementedBy = mapOf(raClient to setOf(raClientImpl)),
            ioClasses = setOf(raClientImpl),
            dependsOn = mapOf(
                resetService to setOf(raClient, sessionAndKey),
            ),
        )
        val serviceTier = ServiceTierDetector.detect(graph)

        val violations = ServiceTierDetector.domainServiceViolations(graph, serviceTier)

        assertEquals(emptyList(), violations)
    }

    @Test
    fun `domainServiceViolations excludes a class and its own Kt file facade`() {
        val resetServiceFacade = ClassName("com.app.passwordreset.ResetPasswordServiceKt")
        val graph = RingGraph(
            classes = setOf(raClient, raClientImpl, resetService, resetServiceFacade),
            interfaces = setOf(raClient),
            implementedBy = mapOf(raClient to setOf(raClientImpl)),
            ioClasses = setOf(raClientImpl),
            dependsOn = mapOf(
                resetService to setOf(raClient),
                resetServiceFacade to setOf(resetService),
            ),
        )
        val serviceTier = ServiceTierDetector.detect(graph)

        val violations = ServiceTierDetector.domainServiceViolations(graph, serviceTier)

        assertEquals(emptyList(), violations, "resetServiceFacade -> resetService is the same Kotlin file, not a real dependency")
    }

    @Test
    fun `domainServiceViolations excludes composition roots`() {
        val root = ClassName("com.app.AppKt")
        val graph = RingGraph(
            classes = setOf(raClient, raClientImpl, resetService, root),
            interfaces = setOf(raClient),
            implementedBy = mapOf(raClient to setOf(raClientImpl)),
            ioClasses = setOf(raClientImpl),
            dependsOn = mapOf(root to setOf(resetService), resetService to setOf(raClient)),
            compositionRoots = setOf(root),
        )
        val strippedGraph = graph.withoutCompositionRoots()
        val serviceTier = ServiceTierDetector.detect(strippedGraph)

        val violations = ServiceTierDetector.domainServiceViolations(strippedGraph, serviceTier)

        assertEquals(emptyList(), violations, "the composition root wiring a service is expected, not a violation")
    }

    @Test
    fun `portBypassViolations flags calling a concrete adapter directly instead of its port`() {
        val graph = RingGraph(
            classes = setOf(raClient, raClientImpl, resetService),
            interfaces = setOf(raClient),
            implementedBy = mapOf(raClient to setOf(raClientImpl)),
            ioClasses = setOf(raClientImpl),
            dependsOn = mapOf(resetService to setOf(raClientImpl)),
        )

        val violations = ServiceTierDetector.portBypassViolations(graph)

        assertEquals(listOf(PortBypassViolation(resetService, raClientImpl, raClient)), violations)
    }

    @Test
    fun `portBypassViolations does not flag going through the port`() {
        val graph = RingGraph(
            classes = setOf(raClient, raClientImpl, resetService),
            interfaces = setOf(raClient),
            implementedBy = mapOf(raClient to setOf(raClientImpl)),
            ioClasses = setOf(raClientImpl),
            dependsOn = mapOf(resetService to setOf(raClient)),
        )

        val violations = ServiceTierDetector.portBypassViolations(graph)

        assertEquals(emptyList(), violations)
    }

    @Test
    fun `portBypassViolations excludes a class and its own Kt file facade`() {
        val raClientImplFacade = ClassName("com.app.ra.RAClientImplKt")
        val graph = RingGraph(
            classes = setOf(raClient, raClientImpl, raClientImplFacade),
            interfaces = setOf(raClient),
            implementedBy = mapOf(raClient to setOf(raClientImpl)),
            ioClasses = setOf(raClientImpl),
            dependsOn = mapOf(raClientImplFacade to setOf(raClientImpl)),
        )

        val violations = ServiceTierDetector.portBypassViolations(graph)

        assertEquals(emptyList(), violations, "raClientImplFacade -> raClientImpl is the same Kotlin file, not a real bypass")
    }

    @Test
    fun `portBypassViolations does not flag an adapter calling another adapter`() {
        val otherAdapter = ClassName("com.app.infra.AuditLogger")
        val graph = RingGraph(
            classes = setOf(raClient, raClientImpl, otherAdapter),
            interfaces = setOf(raClient),
            implementedBy = mapOf(raClient to setOf(raClientImpl)),
            ioClasses = setOf(raClientImpl, otherAdapter),
            dependsOn = mapOf(raClientImpl to setOf(otherAdapter)),
        )

        val violations = ServiceTierDetector.portBypassViolations(graph)

        assertEquals(emptyList(), violations)
    }

    @Test
    fun `portBypassViolations does not flag calling an adapter with no port at all`() {
        val noPortAdapter = ClassName("com.app.infra.SmtpMailer")
        val graph = RingGraph(
            classes = setOf(noPortAdapter, resetService),
            ioClasses = setOf(noPortAdapter),
            dependsOn = mapOf(resetService to setOf(noPortAdapter)),
        )

        val violations = ServiceTierDetector.portBypassViolations(graph)

        assertEquals(emptyList(), violations, "there is no port to bypass when the adapter implements nothing")
    }

    @Test
    fun `portBypassViolations excludes composition roots`() {
        val root = ClassName("com.app.AppKt")
        val graph = RingGraph(
            classes = setOf(raClient, raClientImpl, root),
            interfaces = setOf(raClient),
            implementedBy = mapOf(raClient to setOf(raClientImpl)),
            ioClasses = setOf(raClientImpl),
            dependsOn = mapOf(root to setOf(raClientImpl)),
            compositionRoots = setOf(root),
        )

        val violations = ServiceTierDetector.portBypassViolations(graph.withoutCompositionRoots())

        assertEquals(emptyList(), violations, "the composition root wiring the concrete adapter is expected, not a bypass")
    }
}
