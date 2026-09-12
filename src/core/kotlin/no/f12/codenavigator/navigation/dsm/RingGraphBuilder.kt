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
        extraValuePackages: Set<String> = emptySet(),
        extraFrameworkPackages: Set<String> = emptySet(),
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
        val frameworkAdapters = AdapterDetector.detectFrameworkAdapters(projectClasses, externalDeps, signatureTypes, extraFrameworkPackages)
        val detectedRoots = CompositionRootDetector.detect(base.copy(ioClasses = frameworkAdapters.keys))
        val compositionRoots = configuredCompositionRoots + detectedRoots.keys

        val findings = AdapterDetector.detect(
            projectClasses, projectDeps, externalDeps, signatureTypes, compositionRoots, extraValuePackages, extraFrameworkPackages,
        )

        // Ports Spring Data/Panache generate a proxy implementor for at runtime have zero compiled
        // implementors visible to bytecode analysis — without this, such an interface's only visible
        // role is "it extends a framework type", collapsing port and adapter into one undetectable
        // class. Reassign it: the interface becomes a real port, and a synthetic proxy class (never a
        // real compiled class) stands in as the adapter that implements it.
        val proxyPorts = ProxyPortDetector.detect(base.interfaces, base.implementedBy, signatureTypes)
        val syntheticProxies = proxyPorts.values.toSet()
        val adapterFindings = findings.filterKeys { it !in proxyPorts.keys }

        return base.copy(
            classes = base.classes + syntheticProxies,
            implementedBy = base.implementedBy + proxyPorts.mapValues { (_, proxy) -> setOf(proxy) },
            ioClasses = adapterFindings.keys + syntheticProxies,
            adapterReasons = adapterFindings.mapValues { (_, finding) -> finding.reason } +
                syntheticProxies.associateWith { AdapterReason.FRAMEWORK_GENERATED_PROXY },
            adapterEvidence = adapterFindings.mapNotNull { (cls, finding) -> finding.evidence?.let { cls to it } }.toMap(),
            compositionRoots = compositionRoots,
            compositionRootEvidence = detectedRoots,
        )
    }
}
