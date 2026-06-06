package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName

data class ClassRingAssignment(
    val classRings: Map<ClassName, Int>,
    val packageSummary: Map<PackageName, PackageRingSummary>,
    val violations: List<ClassRingViolation>,
)

data class PackageRingSummary(
    val classesByRing: Map<Int, List<ClassName>>,
) {
    val isMixedRing: Boolean get() = classesByRing.size > 1
    val minRing: Int get() = classesByRing.keys.min()
    val maxRing: Int get() = classesByRing.keys.max()
}

data class ClassRingViolation(
    val sourceClass: ClassName,
    val targetClass: ClassName,
    val sourceRing: Int,
    val targetRing: Int,
    val type: RingViolationType,
)

object EmergentRingDetector {

    fun detect(
        projectDeps: List<PackageDependency>,
        externalDeps: List<PackageDependency>,
        projectClasses: Set<ClassName>,
        hintsConfig: RingsHintsConfig? = null,
    ): ClassRingAssignment {
        // Build ClassDependencies for each class
        val classDepsMap = buildClassDependencies(projectDeps, externalDeps, projectClasses)

        // Classify each class into a ring
        val rawRings = ClassRingClassifier.classify(classDepsMap)
        val classRings = if (hintsConfig != null) {
            hintsConfig.adjustRings(rawRings, projectClasses)
        } else {
            rawRings
        }

        // Build package summary
        val packageSummary = classRings.entries
            .groupBy { it.key.packageName() }
            .mapValues { (_, entries) ->
                val byRing = entries.groupBy({ it.value }, { it.key })
                PackageRingSummary(classesByRing = byRing)
            }

        // Detect violations: class depending on a class at a higher ring (outward)
        val violations = mutableListOf<ClassRingViolation>()
        for (dep in projectDeps) {
            val sourceRing = classRings[dep.sourceClass] ?: continue
            val targetRing = classRings[dep.targetClass] ?: continue
            if (targetRing > sourceRing) {
                violations += ClassRingViolation(
                    dep.sourceClass, dep.targetClass, sourceRing, targetRing, RingViolationType.OUTWARD,
                )
            }
        }

        return ClassRingAssignment(
            classRings = classRings,
            packageSummary = packageSummary,
            violations = violations.sortedWith(compareBy({ it.type }, { it.sourceClass }, { it.targetClass })),
        )
    }

    private fun buildClassDependencies(
        projectDeps: List<PackageDependency>,
        externalDeps: List<PackageDependency>,
        projectClasses: Set<ClassName>,
    ): Map<ClassName, ClassDependencies> {
        val projectDepsBySource = projectDeps.groupBy { it.sourceClass }
        val externalDepsBySource = externalDeps.groupBy { it.sourceClass }

        return projectClasses.associateWith { cls ->
            ClassDependencies(
                projectDeps = projectDepsBySource[cls]
                    ?.map { it.targetClass }
                    ?.filter { it in projectClasses && it != cls }
                    ?.toSet() ?: emptySet(),
                externalDeps = externalDepsBySource[cls]
                    ?.map { it.targetClass }
                    ?.filter { it !in projectClasses }
                    ?.toSet() ?: emptySet(),
            )
        }
    }
}
