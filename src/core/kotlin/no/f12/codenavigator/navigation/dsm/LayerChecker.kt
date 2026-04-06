package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.core.ClassName

enum class ViolationType {
    OUTWARD,
    PEER,
}

data class LayerViolation(
    val sourceClass: ClassName,
    val targetClass: ClassName,
    val sourceLayer: String,
    val targetLayer: String,
    val type: ViolationType,
)

data class LayerCheckResult(
    val violations: List<LayerViolation>,
    val unassignedClasses: Set<ClassName>,
)

object LayerChecker {

    fun check(config: LayerConfig, dependencies: List<PackageDependency>): LayerCheckResult {
        val allClassesInDeps = dependencies
            .flatMap { listOf(it.sourceClass, it.targetClass) }
            .toSet()

        val unassigned = allClassesInDeps.filter { config.layerIndexOf(it) == null }.toSet()

        val violations = mutableListOf<LayerViolation>()
        val peerTargets = mutableMapOf<ClassName, MutableSet<ClassName>>()

        for (dep in dependencies) {
            val sourceIndex = config.layerIndexOf(dep.sourceClass) ?: continue
            val targetIndex = config.layerIndexOf(dep.targetClass) ?: continue

            if (config.arePeers(dep.sourceClass, dep.targetClass)) {
                peerTargets.getOrPut(dep.sourceClass) { mutableSetOf() }.add(dep.targetClass)
            } else if (targetIndex < sourceIndex) {
                val targetLayer = config.layers[targetIndex]
                val exemptedByTestInfrastructure = targetLayer.testInfrastructure && dep.sourceClass.isTest()
                if (!exemptedByTestInfrastructure) {
                    violations.add(
                        LayerViolation(
                            sourceClass = dep.sourceClass,
                            targetClass = dep.targetClass,
                            sourceLayer = config.layerNameOf(dep.sourceClass)!!,
                            targetLayer = config.layerNameOf(dep.targetClass)!!,
                            type = ViolationType.OUTWARD,
                        ),
                    )
                }
            }
        }

        for ((sourceClass, targets) in peerTargets) {
            val peerLimit = config.peerLimitOf(sourceClass)
            if (peerLimit >= 0 && targets.size > peerLimit) {
                for (targetClass in targets.sorted()) {
                    violations.add(
                        LayerViolation(
                            sourceClass = sourceClass,
                            targetClass = targetClass,
                            sourceLayer = config.layerNameOf(sourceClass)!!,
                            targetLayer = config.layerNameOf(targetClass)!!,
                            type = ViolationType.PEER,
                        ),
                    )
                }
            }
        }

        return LayerCheckResult(
            violations = violations.sortedWith(compareBy({ it.type }, { it.sourceClass }, { it.targetClass })),
            unassignedClasses = unassigned,
        )
    }
}
