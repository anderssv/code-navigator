package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName

enum class AdapterReason {
    CONFIGURED,
    FRAMEWORK_SIGNATURE,
    FRAMEWORK_TYPE,
    SINK_WITH_EXTERNAL_CALLS,
    UNCALLED_ENTRY_POINT,
}

data class RingGraph(
    val classes: Set<ClassName>,
    val interfaces: Set<ClassName> = emptySet(),
    val implementedBy: Map<ClassName, Set<ClassName>> = emptyMap(),
    val ioClasses: Set<ClassName> = emptySet(),
    val adapterReasons: Map<ClassName, AdapterReason> = emptyMap(),
    val adapterEvidence: Map<ClassName, ClassName> = emptyMap(),
    val compositionRoots: Set<ClassName> = emptySet(),
    val dependsOn: Map<ClassName, Set<ClassName>> = emptyMap(),
) {
    fun withoutCompositionRoots(): RingGraph {
        if (compositionRoots.isEmpty()) return this
        return copy(
            classes = classes - compositionRoots,
            interfaces = interfaces - compositionRoots,
            implementedBy = (implementedBy - compositionRoots)
                .mapValues { (_, impls) -> impls - compositionRoots },
            ioClasses = ioClasses - compositionRoots,
            dependsOn = (dependsOn - compositionRoots)
                .mapValues { (_, targets) -> targets - compositionRoots },
        )
    }
}

data class ClassRingViolation(
    val sourceClass: ClassName,
    val targetClass: ClassName,
    val sourceRing: Int,
    val targetRing: Int,
    val type: RingViolationType,
)

enum class RingDiagnosis {
    NO_CLASSES,
    NO_INVERSION_BOUNDARY,
    LAYERED,
}

data class RingLayering(
    val ringCount: Int,
    val diagnosis: RingDiagnosis,
    val rings: Map<ClassName, Int> = emptyMap(),
    val violations: List<ClassRingViolation> = emptyList(),
)

object InversionRingDetector {

    fun detect(input: RingGraph): RingLayering {
        val graph = input.withoutCompositionRoots()
        if (graph.classes.isEmpty()) {
            return RingLayering(ringCount = 0, diagnosis = RingDiagnosis.NO_CLASSES)
        }

        val layers = peelLayers(graph)
        if (layers.size == 1) {
            return RingLayering(
                ringCount = 1,
                diagnosis = RingDiagnosis.NO_INVERSION_BOUNDARY,
                rings = graph.classes.associateWith { 0 },
            )
        }

        val rings = ringPerClass(layers)
        return RingLayering(
            ringCount = layers.size,
            diagnosis = RingDiagnosis.LAYERED,
            rings = rings,
            violations = outwardViolations(graph, rings),
        )
    }

    private fun outwardViolations(graph: RingGraph, rings: Map<ClassName, Int>): List<ClassRingViolation> =
        graph.dependsOn.entries
            .flatMap { (source, targets) -> targets.map { source to it } }
            .filterNot { (source, target) -> source.isSameFileFacadeOf(target) }
            .mapNotNull { (source, target) ->
                val sourceRing = rings[source] ?: return@mapNotNull null
                val targetRing = rings[target] ?: return@mapNotNull null
                if (targetRing <= sourceRing) return@mapNotNull null
                ClassRingViolation(source, target, sourceRing, targetRing, RingViolationType.OUTWARD)
            }
            .sortedWith(compareBy({ it.sourceClass.value }, { it.targetClass.value }))

    private fun peelLayers(graph: RingGraph): List<Set<ClassName>> {
        var outer = graph.ioClasses.intersect(graph.classes)
        if (outer.isEmpty()) return listOf(graph.classes)

        val layers = mutableListOf<Set<ClassName>>()
        val assigned = mutableSetOf<ClassName>()

        while (true) {
            layers += outer
            assigned += outer

            val ports = portsImplementedBy(graph, outer) - assigned
            if (ports.isEmpty()) break

            outer = ports + usersOf(graph, ports, assigned)
        }

        val unplaced = graph.classes - assigned
        if (unplaced.isEmpty()) return layers

        return layers.dropLast(1) + listOf(layers.last() + unplaced)
    }

    private fun usersOf(graph: RingGraph, ports: Set<ClassName>, assigned: Set<ClassName>): Set<ClassName> =
        graph.classes
            .filter { it !in assigned && it !in ports }
            .filter { graph.dependsOn[it].orEmpty().any { target -> target in ports } }
            .toSet()

    private fun ringPerClass(layers: List<Set<ClassName>>): Map<ClassName, Int> =
        layers.flatMapIndexed { index, layer ->
            val ring = layers.size - 1 - index
            layer.map { it to ring }
        }.toMap()

    private fun portsImplementedBy(graph: RingGraph, outer: Set<ClassName>): Set<ClassName> =
        graph.interfaces
            .filter { it !in outer }
            .filter { port -> graph.implementedBy[port].orEmpty().any { it in outer } }
            .toSet()
}
