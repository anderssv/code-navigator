package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName

/** Source is domain (neither adapter nor service-tier); target is service-tier. Domain must never reach into orchestration. */
data class DomainServiceViolation(
    val sourceClass: ClassName,
    val targetClass: ClassName,
)

/** Source (not itself an adapter) depends directly on a concrete adapter that implements [bypassedPort], instead of on the port. */
data class PortBypassViolation(
    val sourceClass: ClassName,
    val targetClass: ClassName,
    val bypassedPort: ClassName,
)

/**
 * Detects the application/orchestration tier structurally, one boundary further in than
 * [AdapterDetector]: whatever directly reaches a port or an adapter, regardless of ring number.
 *
 * Deliberately flat (a single direct-edge test, not a transitive closure). A fully transitive
 * definition — "anything that can reach the boundary through any chain of calls" — would make
 * the domain/service invariant unenforceable by construction: the moment a domain class depended
 * on a service, it would itself become classified as service tier too, and the very dependency we
 * want to flag would silently disappear instead of being reported. The direct-edge test keeps
 * "domain depends on service" representable as a real violation. The known cost: a class that
 * only reaches the boundary *through* another service (delegation, not direct orchestration) is
 * not itself detected as service tier, and a domain-looking caller of it won't be flagged either —
 * revisit if this proves too coarse in the field.
 */
object ServiceTierDetector {

    fun detect(graph: RingGraph): Set<ClassName> {
        val boundary = ports(graph) + graph.ioClasses
        return graph.classes
            .filter { it !in boundary }
            .filter { cls -> graph.dependsOn[cls].orEmpty().any { it in boundary } }
            .toSet()
    }

    fun domainServiceViolations(graph: RingGraph, serviceTier: Set<ClassName>): List<DomainServiceViolation> {
        val domain = graph.classes - graph.ioClasses - serviceTier
        return graph.dependsOn.entries
            .filter { (source, _) -> source in domain }
            .flatMap { (source, targets) -> targets.filter { it in serviceTier }.map { source to it } }
            .filterNot { (source, target) -> source.isSameFileFacadeOf(target) }
            .map { (source, target) -> DomainServiceViolation(source, target) }
            .sortedWith(compareBy({ it.sourceClass.value }, { it.targetClass.value }))
    }

    fun portBypassViolations(graph: RingGraph): List<PortBypassViolation> {
        val portsByImplementor = graph.implementedBy.entries
            .flatMap { (port, impls) -> impls.map { it to port } }
            .groupBy({ it.first }, { it.second })

        return graph.dependsOn.entries
            .filter { (source, _) -> source !in graph.ioClasses }
            .flatMap { (source, targets) ->
                targets.filter { it in graph.ioClasses }
                    .filterNot { target -> source.isSameFileFacadeOf(target) }
                    .mapNotNull { target ->
                        portsByImplementor[target]?.firstOrNull()?.let { port -> PortBypassViolation(source, target, port) }
                    }
            }
            .sortedWith(compareBy({ it.sourceClass.value }, { it.targetClass.value }))
    }

    private fun ports(graph: RingGraph): Set<ClassName> =
        graph.interfaces.filter { port -> graph.implementedBy[port].orEmpty().any { it in graph.ioClasses } }.toSet()
}
