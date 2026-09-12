package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InversionRingDetectorTest {

    @Test
    fun `no classes yields no rings`() {
        val graph = RingGraph(classes = emptySet())

        val layering = InversionRingDetector.detect(graph)

        assertEquals(0, layering.ringCount)
    }

    @Test
    fun `many classes with no inversion boundary yield a single ring`() {
        val graph = RingGraph(
            classes = setOf(
                ClassName("com.app.Order"),
                ClassName("com.app.OrderService"),
                ClassName("com.app.Money"),
            ),
        )

        val layering = InversionRingDetector.detect(graph)

        assertEquals(1, layering.ringCount)
    }

    @Test
    fun `a single ring reports that no inversion boundary was found`() {
        val graph = RingGraph(
            classes = setOf(ClassName("com.app.Order")),
        )

        val layering = InversionRingDetector.detect(graph)

        assertEquals(RingDiagnosis.NO_INVERSION_BOUNDARY, layering.diagnosis)
    }

    @Test
    fun `a port implemented by an I O class forms a boundary yielding two rings`() {
        val port = ClassName("com.app.polls.PollsRepository")
        val impl = ClassName("com.app.polls.PollsRepositoryImpl")
        val service = ClassName("com.app.polls.PollService")

        val graph = RingGraph(
            classes = setOf(port, impl, service),
            interfaces = setOf(port),
            implementedBy = mapOf(port to setOf(impl)),
            ioClasses = setOf(impl),
            dependsOn = mapOf(
                impl to setOf(port),
                service to setOf(port),
            ),
        )

        val layering = InversionRingDetector.detect(graph)

        assertEquals(2, layering.ringCount)
    }

    @Test
    fun `the I O implementor sits outside the boundary and the port and its users inside`() {
        val port = ClassName("com.app.polls.PollsRepository")
        val impl = ClassName("com.app.polls.PollsRepositoryImpl")
        val service = ClassName("com.app.polls.PollService")

        val graph = RingGraph(
            classes = setOf(port, impl, service),
            interfaces = setOf(port),
            implementedBy = mapOf(port to setOf(impl)),
            ioClasses = setOf(impl),
            dependsOn = mapOf(
                impl to setOf(port),
                service to setOf(port),
            ),
        )

        val layering = InversionRingDetector.detect(graph)

        assertEquals(1, layering.rings[impl])
        assertEquals(0, layering.rings[port])
        assertEquals(0, layering.rings[service])
    }

    @Test
    fun `an interface that itself touches I O is not a port`() {
        val leakyInterface = ClassName("com.app.infra.PollsTable")
        val impl = ClassName("com.app.infra.PollsTableImpl")

        val graph = RingGraph(
            classes = setOf(leakyInterface, impl),
            interfaces = setOf(leakyInterface),
            implementedBy = mapOf(leakyInterface to setOf(impl)),
            ioClasses = setOf(leakyInterface, impl),
            dependsOn = mapOf(impl to setOf(leakyInterface)),
        )

        val layering = InversionRingDetector.detect(graph)

        assertEquals(RingDiagnosis.NO_INVERSION_BOUNDARY, layering.diagnosis)
        assertEquals(1, layering.ringCount)
    }

    @Test
    fun `an interface with no implementors does not form a boundary`() {
        val orphanPort = ClassName("com.app.polls.UnusedPort")
        val ioClass = ClassName("com.app.infra.PollsTable")

        val graph = RingGraph(
            classes = setOf(orphanPort, ioClass),
            interfaces = setOf(orphanPort),
            implementedBy = emptyMap(),
            ioClasses = setOf(ioClass),
        )

        val layering = InversionRingDetector.detect(graph)

        assertEquals(RingDiagnosis.NO_INVERSION_BOUNDARY, layering.diagnosis)
    }

    @Test
    fun `two nested inversion boundaries yield three rings`() {
        val sqlRepo = ClassName("com.app.infra.SqlPollsRepository")
        val repoPort = ClassName("com.app.polls.PollsRepository")
        val service = ClassName("com.app.polls.PollService")
        val policyPort = ClassName("com.app.domain.PollPolicy")
        val poll = ClassName("com.app.domain.Poll")

        val graph = RingGraph(
            classes = setOf(sqlRepo, repoPort, service, policyPort, poll),
            interfaces = setOf(repoPort, policyPort),
            implementedBy = mapOf(
                repoPort to setOf(sqlRepo),
                policyPort to setOf(service),
            ),
            ioClasses = setOf(sqlRepo),
            dependsOn = mapOf(
                sqlRepo to setOf(repoPort),
                service to setOf(repoPort, policyPort),
                poll to setOf(policyPort),
            ),
        )

        val layering = InversionRingDetector.detect(graph)

        assertEquals(3, layering.ringCount)
        assertEquals(2, layering.rings[sqlRepo])
        assertEquals(1, layering.rings[service])
        assertEquals(0, layering.rings[poll])
    }

    @Test
    fun `a core class depending on an outer ring class is an outward violation`() {
        val port = ClassName("com.app.polls.PollsRepository")
        val impl = ClassName("com.app.infra.PollsRepositoryImpl")
        val service = ClassName("com.app.polls.PollService")
        val poll = ClassName("com.app.domain.Poll")

        val graph = RingGraph(
            classes = setOf(port, impl, service, poll),
            interfaces = setOf(port),
            implementedBy = mapOf(port to setOf(impl)),
            ioClasses = setOf(impl),
            dependsOn = mapOf(
                impl to setOf(port),
                service to setOf(port),
                poll to setOf(impl),
            ),
        )

        val layering = InversionRingDetector.detect(graph)

        assertEquals(
            listOf(ClassRingViolation(poll, impl, 0, 1, RingViolationType.OUTWARD)),
            layering.violations,
        )
    }

    @Test
    fun `a class and its own Kt file facade are not a violation even across rings`() {
        val port = ClassName("com.app.polls.PollsRepository")
        val impl = ClassName("com.app.infra.PollsRepositoryImpl")
        val implFacade = ClassName("com.app.infra.PollsRepositoryImplKt")

        val graph = RingGraph(
            classes = setOf(port, impl, implFacade),
            interfaces = setOf(port),
            implementedBy = mapOf(port to setOf(impl)),
            ioClasses = setOf(impl),
            dependsOn = mapOf(
                impl to setOf(port),
                implFacade to setOf(impl),
            ),
        )

        val layering = InversionRingDetector.detect(graph)

        assertEquals(emptyList(), layering.violations, "implFacade -> impl is the same Kotlin file, not a real dependency")
    }

    @Test
    fun `two classes in the same ring depending on each other is not a violation`() {
        val port = ClassName("com.app.polls.PollsRepository")
        val impl = ClassName("com.app.infra.PollsRepositoryImpl")
        val coordinator = ClassName("com.app.polls.PollCoordinator")
        val collaborator = ClassName("com.app.polls.PollCollaborator")

        val graph = RingGraph(
            classes = setOf(port, impl, coordinator, collaborator),
            interfaces = setOf(port),
            implementedBy = mapOf(port to setOf(impl)),
            ioClasses = setOf(impl),
            dependsOn = mapOf(
                impl to setOf(port),
                coordinator to setOf(port, collaborator),
                collaborator to setOf(coordinator),
            ),
        )

        val layering = InversionRingDetector.detect(graph)

        assertEquals(emptyList(), layering.violations)
    }

    @Test
    fun `a composition root is excluded from ring assignment and from violations`() {
        val port = ClassName("com.app.polls.PollsRepository")
        val impl = ClassName("com.app.infra.PollsRepositoryImpl")
        val service = ClassName("com.app.polls.PollService")
        val root = ClassName("com.app.ApplicationKt")

        val graph = RingGraph(
            classes = setOf(port, impl, service, root),
            interfaces = setOf(port),
            implementedBy = mapOf(port to setOf(impl)),
            ioClasses = setOf(impl, root),
            compositionRoots = setOf(root),
            dependsOn = mapOf(
                impl to setOf(port),
                service to setOf(port),
                root to setOf(port, impl, service),
            ),
        )

        val layering = InversionRingDetector.detect(graph)

        assertNull(layering.rings[root])
        assertEquals(emptyList(), layering.violations)
    }
}
