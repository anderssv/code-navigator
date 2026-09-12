package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName

object CompositionRootDetector {

    /** Returns composition root -> the ioClass it reaches, as evidence for why it was classified
     * as an assembler rather than the composition root simply being a bare set with no explanation. */
    fun detect(graph: RingGraph): Map<ClassName, ClassName> {
        val referenced = graph.dependsOn.values.flatten().toSet()
        return graph.classes
            .filter { it !in referenced }
            .filter { it !in graph.ioClasses }
            .mapNotNull { cls -> graph.dependsOn[cls].orEmpty().firstOrNull { it in graph.ioClasses }?.let { cls to it } }
            .toMap()
    }
}
