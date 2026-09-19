package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.AnnotationName
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
        classAnnotations: Map<ClassName, Set<AnnotationName>> = emptyMap(),
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
        val frameworkAdapters = AdapterDetector.detectFrameworkAdapters(projectClasses, externalDeps, signatureTypes, extraFrameworkPackages, classAnnotations, extraValuePackages)
        val detectedRoots = CompositionRootDetector.detect(base.copy(ioClasses = frameworkAdapters.keys))
        val compositionRoots = configuredCompositionRoots + detectedRoots.keys

        val findings = AdapterDetector.detect(
            projectClasses, projectDeps, externalDeps, signatureTypes, compositionRoots, extraValuePackages, extraFrameworkPackages, classAnnotations,
        )

        // Ports Spring Data/Panache generate a proxy implementor for at runtime have zero compiled
        // implementors visible to bytecode analysis — without this, such an interface's only visible
        // role is "it extends a framework type", collapsing port and adapter into one undetectable
        // class. Reassign it: the interface becomes a real port, and a synthetic proxy class (never a
        // real compiled class) stands in as the adapter that implements it.
        val proxyPorts = ProxyPortDetector.detect(base.interfaces, base.implementedBy, signatureTypes, classAnnotations)
        val syntheticProxies = proxyPorts.values.toSet()
        val detectedFindings = findings.filterKeys { it !in proxyPorts.keys }
        val adapterFindings = detectedFindings + portSiblingAdapters(base, detectedFindings.keys, compositionRoots, proxyPorts.keys, classKinds)

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

    /**
     * Every other rule classifies an adapter by evidence of touching the outside. An implementation
     * that does no I/O — an in-memory store, a no-op fallback used when a provider isn't configured —
     * trips none of them, so it lands in the service tier while the implementation it stands in for
     * sits in the adapter ring. That asymmetry is in the model, not in the code: both implement the
     * same port, and a port implementation's ring is decided by the boundary it implements, not by
     * whether it happens to open a socket.
     *
     * [InversionRingDetector.portsImplementedBy] already defines a port as an interface implemented by
     * something in the outer ring, so this reuses that definition rather than adding one. The extra
     * condition — that something outside the implementor set depends on the interface — keeps a marker
     * or utility interface nobody programs to from dragging all its implementors outward because one
     * of them happens to name a framework type.
     *
     * That guard alone is not enough, though: a *domain* interface (`Poll` implemented by `DayPoll`
     * and `TimePoll`) is programmed to by every service, so one implementor that happens to name a
     * framework type in a signature would drag its siblings out of ring 0. Value types are therefore
     * excluded outright — a data class, record or annotated model is never an adapter, whatever it
     * implements.
     */
    private fun portSiblingAdapters(
        base: RingGraph,
        adapters: Set<ClassName>,
        compositionRoots: Set<ClassName>,
        proxyPorts: Set<ClassName>,
        classKinds: Map<ClassName, ClassKind>,
    ): Map<ClassName, AdapterFinding> {
        val findings = mutableMapOf<ClassName, AdapterFinding>()
        for ((port, implementors) in base.implementedBy) {
            if (port in proxyPorts) continue
            if (implementors.none { it in adapters }) continue
            if (!isProgrammedTo(base, port, implementors)) continue

            implementors
                .filter { it !in adapters && it !in compositionRoots }
                .filterNot { classKinds[it] in VALUE_KINDS }
                .forEach { findings[it] = AdapterFinding(AdapterReason.PORT_IMPLEMENTATION, port) }
        }
        return findings
    }

    private val VALUE_KINDS = setOf(ClassKind.DATA_CLASS, ClassKind.RECORD, ClassKind.ANNOTATED_MODEL)

    /** Someone depends on the interface itself rather than on one of its implementations. */
    private fun isProgrammedTo(base: RingGraph, port: ClassName, implementors: Set<ClassName>): Boolean =
        base.dependsOn.any { (source, targets) -> source !in implementors && source != port && port in targets }
}
