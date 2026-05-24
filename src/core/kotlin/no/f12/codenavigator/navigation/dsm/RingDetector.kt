package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.PackageName

data class RingAssignment(
    val rings: Map<PackageName, Int>,
    val compositionRoots: Set<PackageName>,
    val violations: List<RingViolation>,
)

data class RingViolation(
    val sourcePackage: PackageName,
    val targetPackage: PackageName,
    val sourceRing: Int,
    val targetRing: Int,
    val type: RingViolationType,
)

enum class RingViolationType {
    OUTWARD,
    PEER,
}

object RingDetector {

    private const val COMPOSITION_ROOT_RING_THRESHOLD = 3

    fun detect(dependencies: List<PackageDependency>): RingAssignment {
        val crossPackageDeps = dependencies.filter { it.sourcePackage != it.targetPackage }

        val allPackages = crossPackageDeps
            .flatMap { listOf(it.sourcePackage, it.targetPackage) }
            .toSet()

        val packageEdges = crossPackageDeps
            .map { it.sourcePackage to it.targetPackage }
            .distinct()
            .toSet()

        val dependsOn = allPackages.associateWith { pkg ->
            packageEdges.filter { it.first == pkg }.map { it.second }.toSet()
        }

        // Step 1: Find SCCs (strongly connected components) — these are cycles
        val sccs = TarjanSCC.findSCCs(allPackages, dependsOn).toList()

        // Step 2: Collapse SCCs into single nodes, compute DAG
        val sccOf = mutableMapOf<PackageName, Int>()
        sccs.forEachIndexed { idx, scc -> scc.forEach { sccOf[it] = idx } }

        val sccDeps = sccs.indices.associateWith { idx ->
            sccs[idx].flatMap { dependsOn[it] ?: emptySet() }
                .map { sccOf[it]!! }
                .filter { it != idx }
                .toSet()
        }

        // Step 3: Assign rings on the DAG (longest path using max of targets + 1)
        val sccRings = assignRingsOnDAG(sccs.indices.toSet(), sccDeps)

        // Map back to packages
        val rings = allPackages.associateWith { sccRings[sccOf[it]!!] ?: 0 }

        // Composition roots: depend on 3+ different rings
        val compositionRoots = allPackages.filter { pkg ->
            val targetRings = (dependsOn[pkg] ?: emptySet())
                .mapNotNull { rings[it] }
                .filter { it != rings[pkg] }
                .toSet()
            targetRings.size >= COMPOSITION_ROOT_RING_THRESHOLD
        }.toSet()

        // Detect violations
        val violations = mutableListOf<RingViolation>()

        for ((source, target) in packageEdges) {
            val sourceRing = rings[source] ?: continue
            val targetRing = rings[target] ?: continue
            if (source in compositionRoots) continue

            if (targetRing > sourceRing) {
                violations.add(RingViolation(source, target, sourceRing, targetRing, RingViolationType.OUTWARD))
            } else if (targetRing == sourceRing && source != target) {
                // Peer: same ring but different packages (including cycles within an SCC)
                violations.add(RingViolation(source, target, sourceRing, targetRing, RingViolationType.PEER))
            }
        }

        val sorted = violations.sortedWith(compareBy({ it.type }, { it.sourcePackage }, { it.targetPackage }))
        return RingAssignment(rings = rings, compositionRoots = compositionRoots, violations = sorted)
    }

    private fun assignRingsOnDAG(nodes: Set<Int>, deps: Map<Int, Set<Int>>): Map<Int, Int> {
        val memo = mutableMapOf<Int, Int>()

        fun computeRing(node: Int): Int {
            memo[node]?.let { return it }
            val targets = deps[node] ?: emptySet()
            val ring = if (targets.isEmpty()) 0
            else targets.maxOf { computeRing(it) } + 1
            memo[node] = ring
            return ring
        }

        for (node in nodes) computeRing(node)
        return memo
    }

}
