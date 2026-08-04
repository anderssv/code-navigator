package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName

sealed class PlanStep {
    data class Move(val classToMove: ClassName, val targetPackage: PackageName) : PlanStep()
}

object PlanMutator {

    /**
     * [dropSamePackageEdges] discards edges that land in the same package after mutation — correct
     * for cycle/DSM/ring analysis (same-package edges are noise there), but wrong for move-suggest/cohesion,
     * which are extracted with includeSamePackage=true and need those edges to score gravity at the new location.
     */
    fun apply(dependencies: List<PackageDependency>, plan: List<PlanStep>, dropSamePackageEdges: Boolean = true): List<PackageDependency> {
        if (plan.isEmpty()) return dependencies

        var current = dependencies
        for (step in plan) {
            current = when (step) {
                is PlanStep.Move -> applyMove(current, step.classToMove, step.targetPackage, dropSamePackageEdges)
            }
        }
        return current
    }

    fun applyToClassSet(projectClasses: Set<ClassName>, plan: List<PlanStep>): Set<ClassName> {
        if (plan.isEmpty()) return projectClasses
        var current = projectClasses
        for (step in plan) {
            current = when (step) {
                is PlanStep.Move -> {
                    current.map { className ->
                        if (className == step.classToMove) {
                            ClassName("${step.targetPackage}.${className.simpleName()}")
                        } else {
                            className
                        }
                    }.toSet()
                }
            }
        }
        return current
    }

    /** Applies class moves to metadata keyed by class name (module provenance, annotations, etc.). */
    fun <T> applyToClassMap(values: Map<ClassName, T>, plan: List<PlanStep>): Map<ClassName, T> {
        if (plan.isEmpty()) return values
        var current = values
        for (step in plan) {
            current = when (step) {
                is PlanStep.Move -> current.mapKeys { (className, _) ->
                    if (className == step.classToMove) {
                        ClassName("${step.targetPackage}.${className.simpleName()}")
                    } else {
                        className
                    }
                }
            }
        }
        return current
    }

    fun parseJson(jsonString: String): List<PlanStep> {
        val steps = mutableListOf<PlanStep>()
        val objectPattern = Regex("""\{[^}]+\}""")
        for (match in objectPattern.findAll(jsonString)) {
            val obj = match.value
            val action = extractField(obj, "action")
            when (action) {
                "move" -> {
                    val type = extractField(obj, "type") ?: error("'type' required for move action")
                    val to = extractField(obj, "to") ?: error("'to' required for move action")
                    steps.add(PlanStep.Move(ClassName(type), PackageName(to)))
                }
                else -> error("Unknown plan action: $action")
            }
        }
        return steps
    }

    fun parseFile(path: java.io.File): List<PlanStep> {
        if (!path.exists()) error("Plan file not found: $path")
        return parseJson(path.readText())
    }

    private fun extractField(json: String, field: String): String? {
        val pattern = Regex(""""$field"\s*:\s*"([^"]+)"""")
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun applyMove(
        dependencies: List<PackageDependency>,
        classToMove: ClassName,
        targetPackage: PackageName,
        dropSamePackageEdges: Boolean,
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
        }.let { mutated -> if (dropSamePackageEdges) mutated.filter { it.sourcePackage != it.targetPackage } else mutated }
    }
}
