package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName

object RingGraphBuilder {

    fun build(
        projectClasses: Set<ClassName>,
        projectDeps: List<PackageDependency>,
        externalDeps: List<PackageDependency>,
        classKinds: Map<ClassName, ClassKind>,
        supertypes: List<StructuralSupertypeInfo>,
        signatureTypes: Map<ClassName, Set<ClassName>> = emptyMap(),
        configuredCompositionRoots: Set<ClassName> = emptySet(),
    ): RingGraph {
        val base = RingGraph(
            classes = projectClasses,
            interfaces = classKinds.filterValues { it == ClassKind.INTERFACE }.keys.intersect(projectClasses),
            implementedBy = supertypes
                .filter { classKinds[it.supertypeClass] == ClassKind.INTERFACE }
                .groupBy({ it.supertypeClass }, { it.sourceClass })
                .mapValues { (_, implementors) -> implementors.toSet() },
            dependsOn = projectDeps
                .filter { it.sourceClass in projectClasses && it.targetClass in projectClasses }
                .filter { it.sourceClass != it.targetClass }
                .groupBy({ it.sourceClass }, { it.targetClass })
                .mapValues { (_, targets) -> targets.toSet() },
        )

        // Staged on purpose: the topological adapter rules need to know the composition roots, and root
        // detection needs to know what an adapter is. The framework pass depends on neither, so it goes
        // first and breaks the cycle.
        val frameworkAdapters = AdapterDetector.detectFrameworkAdapters(projectClasses, externalDeps, signatureTypes)
        val compositionRoots = configuredCompositionRoots +
            CompositionRootDetector.detect(base.copy(ioClasses = frameworkAdapters.keys))

        val adapterReasons = AdapterDetector.detect(
            projectClasses, projectDeps, externalDeps, signatureTypes, compositionRoots,
        )

        return base.copy(
            ioClasses = adapterReasons.keys,
            adapterReasons = adapterReasons,
            compositionRoots = compositionRoots,
        )
    }
}
