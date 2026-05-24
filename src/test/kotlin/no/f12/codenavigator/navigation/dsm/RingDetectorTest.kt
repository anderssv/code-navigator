package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.PackageName
import no.f12.codenavigator.navigation.types.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RingDetectorTest {

    @Test
    fun `package with no outgoing deps to other project packages is ring 0`() {
        val deps = listOf(
            PackageDependency(PackageName("com.app.web"), PackageName("com.app.domain"), ClassName("com.app.web.Controller"), ClassName("com.app.domain.Order")),
        )

        val result = RingDetector.detect(deps)

        assertEquals(0, result.rings[PackageName("com.app.domain")])
    }

    @Test
    fun `package depending only on ring 0 is ring 1`() {
        val deps = listOf(
            PackageDependency(PackageName("com.app.service"), PackageName("com.app.domain"), ClassName("com.app.service.OrderService"), ClassName("com.app.domain.Order")),
            PackageDependency(PackageName("com.app.web"), PackageName("com.app.service"), ClassName("com.app.web.Controller"), ClassName("com.app.service.OrderService")),
        )

        val result = RingDetector.detect(deps)

        assertEquals(0, result.rings[PackageName("com.app.domain")])
        assertEquals(1, result.rings[PackageName("com.app.service")])
    }

    @Test
    fun `package depending on ring 0 and ring 1 is ring 2`() {
        val deps = listOf(
            PackageDependency(PackageName("com.app.service"), PackageName("com.app.domain"), ClassName("com.app.service.OrderService"), ClassName("com.app.domain.Order")),
            PackageDependency(PackageName("com.app.web"), PackageName("com.app.service"), ClassName("com.app.web.Controller"), ClassName("com.app.service.OrderService")),
            PackageDependency(PackageName("com.app.web"), PackageName("com.app.domain"), ClassName("com.app.web.Controller"), ClassName("com.app.domain.Order")),
        )

        val result = RingDetector.detect(deps)

        assertEquals(0, result.rings[PackageName("com.app.domain")])
        assertEquals(1, result.rings[PackageName("com.app.service")])
        assertEquals(2, result.rings[PackageName("com.app.web")])
    }

    @Test
    fun `package depending on 3+ rings is composition root`() {
        val deps = listOf(
            PackageDependency(PackageName("com.app.service"), PackageName("com.app.domain"), ClassName("com.app.service.Svc"), ClassName("com.app.domain.D")),
            PackageDependency(PackageName("com.app.web"), PackageName("com.app.service"), ClassName("com.app.web.W"), ClassName("com.app.service.Svc")),
            PackageDependency(PackageName("com.app.web"), PackageName("com.app.domain"), ClassName("com.app.web.W"), ClassName("com.app.domain.D")),
            // Composition root depends on domain (ring 0), service (ring 1), and web (ring 2)
            PackageDependency(PackageName("com.app.main"), PackageName("com.app.domain"), ClassName("com.app.main.App"), ClassName("com.app.domain.D")),
            PackageDependency(PackageName("com.app.main"), PackageName("com.app.service"), ClassName("com.app.main.App"), ClassName("com.app.service.Svc")),
            PackageDependency(PackageName("com.app.main"), PackageName("com.app.web"), ClassName("com.app.main.App"), ClassName("com.app.web.W")),
        )

        val result = RingDetector.detect(deps)

        assertTrue(PackageName("com.app.main") in result.compositionRoots)
    }

    @Test
    fun `cycle between domain and infra reports peer violations`() {
        // domain↔infra is a cycle — both collapse to same ring
        // service depends on domain, web depends on service
        val deps = listOf(
            PackageDependency(PackageName("com.app.service"), PackageName("com.app.domain"), ClassName("com.app.service.Svc"), ClassName("com.app.domain.D")),
            PackageDependency(PackageName("com.app.web"), PackageName("com.app.service"), ClassName("com.app.web.W"), ClassName("com.app.service.Svc")),
            PackageDependency(PackageName("com.app.infra"), PackageName("com.app.domain"), ClassName("com.app.infra.Repo"), ClassName("com.app.domain.D")),
            PackageDependency(PackageName("com.app.domain"), PackageName("com.app.infra"), ClassName("com.app.domain.D"), ClassName("com.app.infra.Repo")),
        )

        val result = RingDetector.detect(deps)

        // Cycle collapses both to ring 0
        assertEquals(0, result.rings[PackageName("com.app.domain")], "domain ring, rings=${result.rings}")
        assertEquals(0, result.rings[PackageName("com.app.infra")], "infra ring, rings=${result.rings}")
        // Both directions reported as peer violations
        val peers = result.violations.filter { it.type == RingViolationType.PEER }
        assertTrue(peers.size >= 1, "Expected peer violations from cycle, got: ${result.violations}")
    }

    @Test
    fun `cyclic dependency between packages at same level reports peer violations`() {
        // web and service both depend on domain but also on each other = cycle = peer violations
        val deps = listOf(
            PackageDependency(PackageName("com.app.service"), PackageName("com.app.domain"), ClassName("com.app.service.Svc"), ClassName("com.app.domain.D")),
            PackageDependency(PackageName("com.app.web"), PackageName("com.app.domain"), ClassName("com.app.web.W"), ClassName("com.app.domain.D")),
            PackageDependency(PackageName("com.app.web"), PackageName("com.app.service"), ClassName("com.app.web.W"), ClassName("com.app.service.Svc")),
            PackageDependency(PackageName("com.app.service"), PackageName("com.app.web"), ClassName("com.app.service.Svc"), ClassName("com.app.web.W")),
        )

        val result = RingDetector.detect(deps)

        // Cycle between service and web: at least one violation involving them
        val cycleViolations = result.violations.filter {
            (it.sourcePackage == PackageName("com.app.service") && it.targetPackage == PackageName("com.app.web")) ||
                (it.sourcePackage == PackageName("com.app.web") && it.targetPackage == PackageName("com.app.service"))
        }
        assertTrue(cycleViolations.isNotEmpty(),
            "Expected violations between cyclic packages, rings=${result.rings}, violations=${result.violations}")
    }

    @Test
    fun `adapter depending on another adapter at same ring is a peer violation`() {
        // web and db both depend ONLY on domain — they're true peers (same ring)
        // web→db (bidirectional with db→web) creates a cycle = peer violation
        val deps = listOf(
            PackageDependency(PackageName("com.app.web"), PackageName("com.app.domain"), ClassName("com.app.web.W"), ClassName("com.app.domain.D")),
            PackageDependency(PackageName("com.app.db"), PackageName("com.app.domain"), ClassName("com.app.db.Repo"), ClassName("com.app.domain.D")),
            // Bidirectional = cycle = both at same ring
            PackageDependency(PackageName("com.app.web"), PackageName("com.app.db"), ClassName("com.app.web.W"), ClassName("com.app.db.Repo")),
            PackageDependency(PackageName("com.app.db"), PackageName("com.app.web"), ClassName("com.app.db.Repo"), ClassName("com.app.web.W")),
        )

        val result = RingDetector.detect(deps)

        val peers = result.violations.filter { it.type == RingViolationType.PEER }
        assertTrue(peers.any {
            it.sourcePackage == PackageName("com.app.web") && it.targetPackage == PackageName("com.app.db")
        }, "Expected web→db peer violation, rings=${result.rings}, violations=${result.violations}")
    }

    @Test
    fun `composition root is exempt from violations`() {
        val deps = listOf(
            PackageDependency(PackageName("com.app.service"), PackageName("com.app.domain"), ClassName("com.app.service.Svc"), ClassName("com.app.domain.D")),
            PackageDependency(PackageName("com.app.web"), PackageName("com.app.service"), ClassName("com.app.web.W"), ClassName("com.app.service.Svc")),
            PackageDependency(PackageName("com.app.web"), PackageName("com.app.domain"), ClassName("com.app.web.W"), ClassName("com.app.domain.D")),
            // Composition root depends on all rings — no violations
            PackageDependency(PackageName("com.app.main"), PackageName("com.app.domain"), ClassName("com.app.main.App"), ClassName("com.app.domain.D")),
            PackageDependency(PackageName("com.app.main"), PackageName("com.app.service"), ClassName("com.app.main.App"), ClassName("com.app.service.Svc")),
            PackageDependency(PackageName("com.app.main"), PackageName("com.app.web"), ClassName("com.app.main.App"), ClassName("com.app.web.W")),
        )

        val result = RingDetector.detect(deps)

        assertTrue(result.violations.none { it.sourcePackage == PackageName("com.app.main") })
    }

    @Test
    fun `self-dependencies within same package are ignored`() {
        val deps = listOf(
            PackageDependency(PackageName("com.app.domain"), PackageName("com.app.domain"), ClassName("com.app.domain.Order"), ClassName("com.app.domain.Product")),
            PackageDependency(PackageName("com.app.web"), PackageName("com.app.domain"), ClassName("com.app.web.W"), ClassName("com.app.domain.Order")),
        )

        val result = RingDetector.detect(deps)

        assertEquals(0, result.rings[PackageName("com.app.domain")])
        assertTrue(result.violations.isEmpty())
    }
}
