package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName

data class WhyDependsEdge(
    val sourceClass: ClassName,
    val targetClass: ClassName,
    val count: Int,
)

data class WhyDependsResult(
    val fromPackage: PackageName,
    val toPackage: PackageName,
    val edges: List<WhyDependsEdge>,
)

object WhyDependsBuilder {

    fun build(
        dependencies: List<PackageDependency>,
        fromPackage: PackageName,
        toPackage: PackageName,
    ): WhyDependsResult {
        val matching = dependencies.filter { dep ->
            dep.sourcePackage.startsWith(fromPackage) && dep.targetPackage.startsWith(toPackage)
        }

        val collapsed = matching.map { dep ->
            dep.copy(
                sourceClass = dep.sourceClass.topLevelClass(),
                targetClass = dep.targetClass.topLevelClass(),
            )
        }

        val grouped = collapsed
            .groupBy { it.sourceClass to it.targetClass }
            .map { (key, entries) -> WhyDependsEdge(key.first, key.second, entries.size) }
            .sortedWith(compareByDescending<WhyDependsEdge> { it.count }.thenBy { it.sourceClass.value }.thenBy { it.targetClass.value })

        return WhyDependsResult(fromPackage, toPackage, grouped)
    }
}
