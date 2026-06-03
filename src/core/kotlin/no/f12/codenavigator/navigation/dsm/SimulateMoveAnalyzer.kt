package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName

data class SimulateMoveResult(
    val classToMove: ClassName,
    val targetPackage: PackageName,
    val cyclesBefore: Int,
    val cyclesAfter: Int,
    val removedCycles: List<CycleDetail>,
    val addedCycles: List<CycleDetail>,
)

object SimulateMoveAnalyzer {

    fun analyze(
        dependencies: List<PackageDependency>,
        classToMove: ClassName,
        targetPackage: PackageName,
    ): SimulateMoveResult {
        val beforeMatrix = buildMatrix(dependencies)
        val beforeCycles = detectCycles(beforeMatrix)

        val mutated = mutateDependencies(dependencies, classToMove, targetPackage)
        val afterMatrix = buildMatrix(mutated)
        val afterCycles = detectCycles(afterMatrix)

        val removedCycles = beforeCycles.filter { it !in afterCycles }
        val addedCycles = afterCycles.filter { it !in beforeCycles }

        return SimulateMoveResult(
            classToMove = classToMove,
            targetPackage = targetPackage,
            cyclesBefore = beforeCycles.size,
            cyclesAfter = afterCycles.size,
            removedCycles = removedCycles,
            addedCycles = addedCycles,
        )
    }

    private fun mutateDependencies(
        dependencies: List<PackageDependency>,
        classToMove: ClassName,
        targetPackage: PackageName,
    ): List<PackageDependency> {
        return dependencies.map { dep ->
            var source = dep.sourceClass
            var sourcePkg = dep.sourcePackage
            var target = dep.targetClass
            var targetPkg = dep.targetPackage

            if (dep.sourceClass == classToMove) {
                sourcePkg = targetPackage
                source = ClassName("${targetPackage}.${classToMove.simpleName()}")
            }
            if (dep.targetClass == classToMove) {
                targetPkg = targetPackage
                target = ClassName("${targetPackage}.${classToMove.simpleName()}")
            }

            PackageDependency(sourcePkg, targetPkg, source, target)
        }.filter { it.sourcePackage != it.targetPackage }
    }

    private fun buildMatrix(dependencies: List<PackageDependency>): DsmMatrix {
        return DsmMatrixBuilder.build(dependencies, PackageName(""), depth = Int.MAX_VALUE)
    }

    private fun detectCycles(matrix: DsmMatrix): List<CycleDetail> {
        val adjacency = CycleDetector.adjacencyMapFrom(matrix)
        val cycles = CycleDetector.findCycles(adjacency)
        return CycleDetector.enrich(cycles, matrix)
    }
}
