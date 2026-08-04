package no.f12.codenavigator.navigation.dsm

import no.f12.codenavigator.navigation.types.ClassName
import no.f12.codenavigator.navigation.types.PackageName

object ModulePackageLabels {
    fun build(
        projectClasses: Set<ClassName>,
        modulesOfClass: Map<ClassName, Set<String>>,
        displayPrefix: PackageName,
        depth: Int,
    ): Map<PackageName, Set<String>> {
        if (modulesOfClass.isEmpty()) return emptyMap()
        return projectClasses
            .groupBy { it.packageName().truncate(displayPrefix, depth) }
            .mapValues { (_, classes) -> classes.flatMap { modulesOfClass[it].orEmpty() }.toSet() }
    }
}
