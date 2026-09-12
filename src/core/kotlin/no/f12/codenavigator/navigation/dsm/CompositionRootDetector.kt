package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName

object CompositionRootDetector {

    fun detect(graph: RingGraph): Set<ClassName> {
        val referenced = graph.dependsOn.values.flatten().toSet()
        return graph.classes
            .filter { it !in referenced }
            .filter { it !in graph.ioClasses }
            .filter { graph.dependsOn[it].orEmpty().any { target -> target in graph.ioClasses } }
            .toSet()
    }
}
