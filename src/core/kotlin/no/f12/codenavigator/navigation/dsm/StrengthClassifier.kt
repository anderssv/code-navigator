package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.ClassName
import no.f12.codenavigator.navigation.PackageName

enum class IntegrationStrength(val level: Int) {
    CONTRACT(1),
    MODEL(2),
    FUNCTIONAL(3),
}

data class PackageStrengthEntry(
    val source: PackageName,
    val target: PackageName,
    val strength: IntegrationStrength,
    val contractCount: Int,
    val modelCount: Int,
    val functionalCount: Int,
    val unknownCount: Int,
    val totalDeps: Int,
)

data class StrengthResult(
    val entries: List<PackageStrengthEntry>,
)

object StrengthClassifier {

    fun classify(
        dependencies: List<PackageDependency>,
        classTypeRegistry: Map<ClassName, ClassKind>,
        top: Int = Int.MAX_VALUE,
        packageFilter: PackageName? = null,
    ): StrengthResult {
        val filtered = dependencies.filterByPackage(packageFilter)

        val grouped = filtered.groupBy { it.sourcePackage to it.targetPackage }

        val entries = grouped.map { (key, deps) ->
            val (source, target) = key
            var contract = 0
            var model = 0
            var functional = 0
            var unknown = 0

            for (dep in deps) {
                when (classifyTarget(dep.targetClass, classTypeRegistry)) {
                    IntegrationStrength.CONTRACT -> contract++
                    IntegrationStrength.MODEL -> model++
                    IntegrationStrength.FUNCTIONAL -> functional++
                    null -> unknown++
                }
            }

            val strongest = when {
                functional > 0 -> IntegrationStrength.FUNCTIONAL
                model > 0 -> IntegrationStrength.MODEL
                else -> IntegrationStrength.CONTRACT
            }

            PackageStrengthEntry(
                source = source,
                target = target,
                strength = strongest,
                contractCount = contract,
                modelCount = model,
                functionalCount = functional,
                unknownCount = unknown,
                totalDeps = deps.size,
            )
        }
            .sortedWith(compareByDescending<PackageStrengthEntry> { it.strength.level }
                .thenByDescending { it.totalDeps }
                .thenBy { it.source.toString() }
                .thenBy { it.target.toString() })
            .take(top)

        return StrengthResult(entries)
    }

    private fun classifyTarget(
        targetClass: ClassName,
        registry: Map<ClassName, ClassKind>,
    ): IntegrationStrength? {
        val kind = registry[targetClass] ?: return null
        return when (kind) {
            ClassKind.INTERFACE, ClassKind.ABSTRACT -> IntegrationStrength.CONTRACT
            ClassKind.DATA_CLASS, ClassKind.RECORD -> IntegrationStrength.MODEL
            ClassKind.CONCRETE -> IntegrationStrength.FUNCTIONAL
        }
    }
}
