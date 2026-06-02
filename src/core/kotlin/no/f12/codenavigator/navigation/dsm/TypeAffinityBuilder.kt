package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName

data class TypeAffinityResult(
    val singleOwnerTypes: List<SingleOwnerType>,
    val sharedTypes: List<SharedType>,
)

data class SingleOwnerType(
    val type: ClassName,
    val ownerPackage: PackageName,
    val usageCount: Int,
    val ringImpact: Int,
)

data class SharedType(
    val type: ClassName,
    val consumerPackages: Set<PackageName>,
)

object TypeAffinityBuilder {

    fun analyze(
        dependencies: List<PackageDependency>,
        targetPackage: PackageName,
        threshold: Int = 1,
    ): TypeAffinityResult {
        // Build a map of which packages call into each package (callers)
        val callersOf = buildCallersMap(dependencies)

        // Find all types in the target package that are referenced from outside
        val typesInTarget = dependencies
            .filter { it.targetPackage == targetPackage && it.sourcePackage != targetPackage }
            .groupBy { it.targetClass }

        val singleOwner = mutableListOf<SingleOwnerType>()
        val shared = mutableListOf<SharedType>()

        // Compute current rings for ring impact calculation
        val currentRings = RingDetector.detect(dependencies).rings

        for ((type, deps) in typesInTarget) {
            val consumerPackages = deps.map { it.sourcePackage }.toSet()
            val effectiveOwners = collapsePortConsumers(consumerPackages, callersOf, targetPackage)

            if (effectiveOwners.size <= threshold) {
                // Primary owner = the one with most usages of this type
                val ownerPackage = deps
                    .groupBy { it.sourcePackage }
                    .maxByOrNull { it.value.size }?.key ?: effectiveOwners.first()
                val ringImpact = computeRingImpact(dependencies, type, targetPackage, ownerPackage, currentRings)
                singleOwner.add(SingleOwnerType(
                    type = type,
                    ownerPackage = ownerPackage,
                    usageCount = deps.size,
                    ringImpact = ringImpact,
                ))
            } else {
                shared.add(SharedType(type = type, consumerPackages = consumerPackages))
            }
        }

        return TypeAffinityResult(
            singleOwnerTypes = singleOwner.sortedByDescending { it.ringImpact },
            sharedTypes = shared,
        )
    }

    /**
     * Collapse "port" consumers: if a consumer package is only called by one other consumer
     * in the set (plus the target package itself), it's a port for that caller — not a separate owner.
     */
    private fun collapsePortConsumers(
        consumers: Set<PackageName>,
        callersOf: Map<PackageName, Set<PackageName>>,
        targetPackage: PackageName,
    ): Set<PackageName> {
        val owners = consumers.toMutableSet()
        for (consumer in consumers) {
            // Who calls this consumer? (excluding the target package itself)
            val callers = (callersOf[consumer] ?: emptySet()) - targetPackage
            // If all callers of this consumer are within the consumer set,
            // and there's exactly one such caller, this consumer is a port
            val callersInSet = callers.intersect(consumers)
            if (callersInSet.size == 1 && (callers - consumers).isEmpty()) {
                owners.remove(consumer)
            }
        }
        return owners
    }

    private fun buildCallersMap(dependencies: List<PackageDependency>): Map<PackageName, Set<PackageName>> {
        return dependencies
            .filter { it.sourcePackage != it.targetPackage }
            .groupBy { it.targetPackage }
            .mapValues { (_, deps) -> deps.map { it.sourcePackage }.toSet() }
    }

    /**
     * Computes the ring drop if [type] were moved from [targetPackage] into [ownerPackage].
     * Simulates by removing deps from ownerPackage→targetPackage that reference this type,
     * and recomputing rings.
     */
    private fun computeRingImpact(
        dependencies: List<PackageDependency>,
        type: ClassName,
        targetPackage: PackageName,
        ownerPackage: PackageName,
        currentRings: Map<PackageName, Int>,
    ): Int {
        val currentRing = currentRings[ownerPackage] ?: return 0

        // Simulate: remove edges from ownerPackage→targetPackage for this specific type
        val simulatedDeps = dependencies.filter {
            !(it.sourcePackage == ownerPackage && it.targetPackage == targetPackage && it.targetClass == type)
        }

        val simulatedRings = RingDetector.detect(simulatedDeps).rings
        val newRing = simulatedRings[ownerPackage] ?: 0

        return currentRing - newRing
    }
}
